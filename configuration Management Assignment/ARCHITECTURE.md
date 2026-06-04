# Architecture — Blue-Green Deployment

## The Idea

Run **two identical production environments**, color-coded `blue` and `green`. At any moment, **exactly one** is receiving live traffic; the other is a fully-warm standby running the previous version. A deployment is just:

1. Push the new version to the **idle** color
2. Health-check it
3. Flip a load balancer over to it (zero downtime)
4. The previously-live color is now the standby — and your rollback target

## Why blue-green is worth the operational cost

- **Zero-downtime deploys.** The new version is fully started, warmed up, and verified before any user traffic hits it. The switch itself is a load-balancer config change measured in milliseconds.
- **Instant rollback.** The previous version is still running, healthy, and ready. Rolling back is one nginx reload — seconds, not minutes.
- **Risk-free verification.** You can smoke-test the new color from the inside (on `:8082`) before customers see it.
- **Decouples deploy from release.** Deploying ≠ releasing. You can have the new code running on green, run database migrations, run integration tests against it, then release by flipping nginx when you're ready.

## The cost

- **2× infrastructure cost** during deployment windows (and continuously, in the always-on variant used here).
- **Stateful coupling is harder.** Both colors share the database. Schema changes have to be backward-compatible during the overlap window.
- **Session state** must live outside the application (Redis, JWT, sticky-session-free design).

---

## This Project's Topology

```
                                       Client traffic
                                              │
                                              ▼
                                ┌─────────────────────────┐
                                │       nginx :80         │
                                │  (single public port)   │
                                │                         │
                                │  upstream backend {     │
                                │    server app-XXX:8080; │  ← rewritten by
                                │  }                      │     switch.sh
                                └────────────┬────────────┘
                                             │
                              ┌──────────────┴──────────────┐
                              │                             │
                  ┌───────────▼──────────┐      ┌───────────▼──────────┐
                  │   app-blue           │      │   app-green          │
                  │   :8080 (internal)   │      │   :8080 (internal)   │
                  │   :8081 (host)       │      │   :8082 (host)       │
                  │   APP_COLOR=blue     │      │   APP_COLOR=green    │
                  │   APP_VERSION=1.0.0  │      │   APP_VERSION=1.1.0  │
                  └──────────────────────┘      └──────────────────────┘
                                                  ←── NEW VERSION (target)

                        Only ONE upstream is wired into nginx at a time.
                        Both containers run continuously.
```

### Port layout

- **Public (port 80)** — what real users hit. Always served by nginx, never by an app directly.
- **`:8081` and `:8082`** — direct access to blue and green for **health checks and smoke tests** during a deploy. These should be firewalled off from the public internet in real environments.

---

## The Deployment Flow, Step by Step

Suppose `blue` is live with `v1.0.0` and we want to deploy `v1.1.0`.

```
[Initial state]                  Live → blue (v1.0.0)
                                 Standby → green (v1.0.0)

1. CI runs                       Build, test, push image v1.1.0 to registry

2. deploy.sh starts              Detects active = blue → target = green

3. Pull + restart green          docker compose up -d --force-recreate app-green
                                 (now green is running v1.1.0)
                                 Live → blue (v1.0.0)
                                 Standby → green (v1.1.0)

4. Health check green            curl http://localhost:8082/actuator/health
                                 → wait until "UP"
                                 → 30 attempts × 2s = 60s budget

5. Smoke test green              ./smoke-test.sh 8082
                                 - health UP
                                 - root payload sane
                                 - color matches "green"
                                 - response time < 1s

   ⚠️  If any step 4-5 fails: STOP HERE. Live traffic was never touched.
       Blue is still serving 100% of users on v1.0.0.

6. Switch.sh                     sed -E 's|server app-blue:8080|server app-green:8080|' nginx.conf
                                 docker exec nginx nginx -t      (validate)
                                 docker exec nginx nginx -s reload (graceful reload)

[After step 6]                   Live → green (v1.1.0)
                                 Standby → blue (v1.0.0) ← rollback target

7. Post-switch verify            curl http://localhost/ → should show version 1.1.0

8. Optionally, after burn-in     Next deploy will redeploy blue, NOT green.
                                 So blue remains as a one-version rollback safety net.
```

### What happens on the nginx reload

`nginx -s reload` is **graceful**:
- nginx starts new worker processes with the new config
- New connections are accepted by the new workers (going to green)
- **Existing in-flight connections** continue to be handled by the old workers (still going to blue) until they complete
- Once all old connections drain, the old workers exit

Result: **no dropped requests, no 502s, no failed transactions**. Users in the middle of a request finish on the old version; the next request goes to the new version.

---

## Rollback

```
./scripts/rollback.sh
```

Reads the current active color, flips back to the other one, reloads nginx. **Same mechanism as forward deploy, no rebuild required, takes seconds.**

The only constraint: the standby color must still be healthy. In this setup it always is, because we never tear it down after a successful deploy — we only replace it on the *next* deploy cycle.

---

## Where This Differs From "Real" Blue-Green

This setup is a minimal but functional demonstration. A production-grade implementation usually also has:

- **A real load balancer** (AWS ALB, GCP LB, F5) with weighted target groups — letting you do **canary** (1% → 10% → 50% → 100%) within the blue-green frame
- **Connection draining timeouts** explicitly configured (e.g., 60s LB drain before tearing down)
- **Cross-AZ / cross-region** distribution per color
- **Database migration discipline** — every schema change has to work for both N and N-1 versions during the overlap
- **Externalized session state** — Redis, JWT, or stateless tokens
- **Observability hooks** — automatic rollback on error-rate or latency-SLO breach during the switch
- **Two-stage cutover** — switch only internal/staff traffic first, watch for 10 minutes, then flip external

The bash scripts here mirror exactly what those production systems do — they just talk to nginx instead of an AWS ALB.

---

## How It Plugs Into CI/CD

The CI tool (Jenkins or GitLab CI) is just the *trigger* — the deployment logic lives in `deployment/scripts/`. This separation matters:

- The script is the source of truth for *how to deploy*
- The CI pipeline is responsible for *when to deploy* and *what artifact to deploy*
- You can run the same deploy.sh manually from a developer's laptop in an outage
- Replacing Jenkins with GitLab (or anything else) doesn't require rewriting the deploy logic

The Ansible playbook (`ansible/deploy.yml`) is an alternative deployment driver — same logic, but expressed as a playbook so it scales to a fleet of remote hosts. The CI tool can invoke either one depending on whether you're deploying to a single Docker host or a multi-server fleet.
