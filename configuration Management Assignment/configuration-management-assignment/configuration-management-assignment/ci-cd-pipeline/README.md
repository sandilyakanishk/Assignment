# CI/CD Pipeline with Blue-Green Deployment

A complete, working continuous-delivery pipeline that deploys a sample Spring Boot app to a Docker-Compose-managed environment using the **blue-green deployment strategy**, with **both Jenkins and GitLab CI** configurations provided.

## What's in here

| File / Folder | Purpose |
|---|---|
| `app/` | Sample Spring Boot app — tiny, but real. Exposes `/api/version` so you can see which color is responding. |
| `deployment/docker-compose.yml` | Runs **both** colors plus nginx as a single stack |
| `deployment/nginx/nginx.conf` | Marked upstream block that the deploy script rewrites |
| `deployment/scripts/deploy.sh` | Orchestrates the whole blue→green flip |
| `deployment/scripts/switch.sh` | Atomic upstream change + graceful nginx reload |
| `deployment/scripts/rollback.sh` | One-command rollback |
| `deployment/scripts/smoke-test.sh` | Per-color verification before traffic switch |
| `Jenkinsfile` | Declarative Jenkins pipeline using the scripts above |
| `.gitlab-ci.yml` | Equivalent GitLab CI pipeline |
| `ansible/deploy.yml` | Same deploy, expressed as an Ansible playbook |
| `ARCHITECTURE.md` | How blue-green works in detail, with diagrams |

## Prerequisites

- **Docker** + **Docker Compose v2**
- **Java 17** and **Maven 3.8+** (only if rebuilding the app outside Docker)
- **`curl`** (used by health checks and smoke tests)
- For the Ansible variant: Ansible 2.14+ with the `community.docker` collection

## Local demo (no CI tool needed)

This is the fastest way to *see* a blue-green deployment happen.

### 1. Build version 1.0.0 of the app

```bash
cd app
docker build -t bluegreen-app:1.0.0 .
cd ..
```

### 2. Start the stack

```bash
cd deployment
docker compose up -d
```

You now have:
- `app-blue` running v1.0.0 on host port 8081 (direct access)
- `app-green` running v1.0.0 on host port 8082 (direct access)
- `nginx` on host port 80, currently routing to `app-blue`

### 3. Check who's live

```bash
curl http://localhost/
# {"service":"bluegreen-app","color":"blue","version":"1.0.0", ... }
```

### 4. Build a new version

Bump the app version (change something visible in `VersionController.java`, or just rebuild):

```bash
cd ../app
docker build -t bluegreen-app:1.1.0 .
cd ../deployment
```

### 5. Deploy with the blue-green script

```bash
./scripts/deploy.sh 1.1.0
```

Watch the output. The script:
1. Detects `blue` is active
2. Recreates `app-green` with the new image
3. Polls `:8082/actuator/health` until UP
4. Runs `smoke-test.sh 8082`
5. Rewrites the `nginx.conf` upstream line
6. Sends nginx a graceful reload

### 6. Confirm the flip

```bash
curl http://localhost/
# {"service":"bluegreen-app","color":"green","version":"1.1.0", ... }
```

### 7. Roll back

```bash
./scripts/rollback.sh
```

Traffic returns to `blue` (v1.0.0) in seconds. No rebuild, no container restart — just an nginx reload.

---

## In a real CI pipeline

### Jenkins

The `Jenkinsfile` defines a declarative pipeline:

1. **Checkout** the repo
2. **Build** with Maven
3. **Run unit tests**, publish JUnit reports
4. **Build and push** the Docker image to a registry
5. **Deploy to inactive color** — calls `scripts/deploy.sh` with the new tag
6. **Post-switch verification** — confirms `curl http://localhost/` returns the new version
7. **(Optional) Manual confirmation** — operator chooses Keep or Rollback
8. **On any failure**, the `post.failure` block automatically calls `rollback.sh`

Set up:
- Add Jenkins agents that have Docker + Maven installed
- Configure a Jenkins credential called `docker-registry` with your registry username + password
- Change `REGISTRY = "registry.example.com"` to your actual registry
- Point your Multibranch Pipeline / Pipeline job at this repo

### GitLab CI

The `.gitlab-ci.yml` does the equivalent thing using GitLab's stages:

- `build` → `test` → `package` → `deploy` → `verify`
- The `deploy` job runs ONLY on `main` (so feature-branch pipelines still build and test, but don't deploy)
- A manual `rollback` job lets operators trigger a flip-back from the GitLab UI
- GitLab's "Environments" feature picks up the `environment:` keyword and renders a deployment timeline

### Ansible variant

For deploying to multiple remote hosts:

```bash
ansible-playbook -i ansible/inventory.ini ansible/deploy.yml -e "new_tag=1.1.0"
```

The playbook does the same steps as `deploy.sh` but:
- Runs against an inventory of remote hosts (one playbook, many servers)
- Uses Ansible's built-in idempotency (re-running doesn't double-deploy)
- Reports detailed change information per host

This is the natural choice when you have a fleet of app servers rather than a single Docker host.

---

## Verifying the switch in slow motion

If you want to see the switch happen live, run this in another terminal while you deploy:

```bash
while true; do
  curl -s http://localhost/ | grep -oE '"color":"[^"]+"' || echo "(no response)"
  sleep 0.2
done
```

You'll see `"color":"blue"` for a while, then `"color":"green"` from the moment the reload completes — with no errors or gaps. That's the zero-downtime part working.

---

## Common gotchas

| Symptom | Likely cause | Fix |
|---|---|---|
| `deploy.sh` says active color is empty | nginx.conf was hand-edited, `# ACTIVE_BACKEND` marker lost | Restore the marker on the active upstream line |
| Health check times out | App takes >60s to start | Increase `HEALTH_RETRIES` in deploy.sh, or shrink app startup |
| `nginx -s reload` fails inside the container | Invalid config — usually a syntax error from sed | The script runs `nginx -t` first; check that output |
| Both colors show same `color` value in `/api/version` | Forgot to set `APP_COLOR` env on container restart | Ensure `docker compose up` is using the compose file in `deployment/`, not a stale one |
| Rollback fails | Standby color is unhealthy | Investigate `docker logs app-<color>`; the standby may have been compromised by a prior failed deploy |

---

## Where to go from here

- **Add canary**: instead of flipping 100% of traffic at once, use nginx's `split_clients` to route 10% to green for 5 minutes before going full
- **Plug in monitoring**: add an automated rollback trigger that fires if the post-switch error rate exceeds a threshold (Prometheus alert → webhook → rollback.sh)
- **Handle database migrations**: enforce backward-compatible schema changes so both colors can run against the same DB during the overlap window
- **Move to a real LB**: AWS ALB, GCP LB, F5, or Envoy can do the same flip with proper draining and HTTP/2 support; the script structure stays the same, only the "flip" implementation changes
