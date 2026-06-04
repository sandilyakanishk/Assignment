# Configuration Management — Assignment Deliverables

This repository answers two questions:

| # | Question | Deliverable |
|---|---|---|
| **1** | Compare Ansible, Puppet, and Chef | [`reports/configuration-management-tools-comparison.md`](reports/configuration-management-tools-comparison.md) |
| **2** | Extend a CI pipeline with continuous delivery + blue-green deployment | [`ci-cd-pipeline/`](ci-cd-pipeline/) |

The two parts are connected: the Ansible playbook in [`ci-cd-pipeline/ansible/deploy.yml`](ci-cd-pipeline/ansible/deploy.yml) shows how a configuration-management tool from Q1's comparison actually slots into Q2's CI pipeline.

---

## 📁 Repository Layout

```
configuration-management-assignment/
├── README.md                                              ← You are here
├── reports/
│   └── configuration-management-tools-comparison.md       ← Q1 report
└── ci-cd-pipeline/                                        ← Q2 implementation
    ├── README.md                                          ← How to run it
    ├── ARCHITECTURE.md                                    ← Blue-green explained
    ├── Jenkinsfile                                        ← Jenkins pipeline
    ├── .gitlab-ci.yml                                     ← GitLab CI pipeline
    ├── app/                                               ← Sample Spring Boot app
    │   ├── pom.xml
    │   ├── Dockerfile
    │   └── src/main/java/com/example/app/
    ├── deployment/
    │   ├── docker-compose.yml                             ← blue + green + nginx
    │   ├── nginx/nginx.conf                               ← upstream rewritten by scripts
    │   └── scripts/
    │       ├── deploy.sh                                  ← orchestrates the whole flip
    │       ├── switch.sh                                  ← rewrites nginx + reloads
    │       ├── rollback.sh                                ← flip back to standby
    │       └── smoke-test.sh                              ← per-color verification
    └── ansible/
        ├── deploy.yml                                     ← same deploy, via Ansible
        └── inventory.ini
```

---

## 🚀 Quick Start (Q2 Demo)

To see the blue-green deployment in action on your laptop, no Jenkins/GitLab required:

```bash
cd ci-cd-pipeline

# 1. Build the app image, tag it as both colors initially
cd app
docker build -t bluegreen-app:1.0.0 .
cd ..

# 2. Start the stack (both colors live, nginx pointing at blue)
cd deployment
docker compose up -d

# 3. Confirm blue is serving
curl http://localhost/
#  → {"service":"bluegreen-app","color":"blue","version":"1.0.0", ...}

# 4. Build a new version
cd ../app
docker build -t bluegreen-app:1.1.0 .
cd ../deployment

# 5. Run the blue-green deploy
./scripts/deploy.sh 1.1.0

# 6. Confirm traffic flipped to green
curl http://localhost/
#  → {"service":"bluegreen-app","color":"green","version":"1.1.0", ...}

# 7. Rollback (instant flip back to blue, no rebuild)
./scripts/rollback.sh

curl http://localhost/
#  → {"service":"bluegreen-app","color":"blue","version":"1.0.0", ...}
```

See [`ci-cd-pipeline/README.md`](ci-cd-pipeline/README.md) for the full walkthrough including the CI integration, and [`ci-cd-pipeline/ARCHITECTURE.md`](ci-cd-pipeline/ARCHITECTURE.md) for the detailed mechanics of the switch.

---

## 📖 Reading Order

1. **`reports/configuration-management-tools-comparison.md`** — the Q1 report, ~3,500 words covering all three tools with a decision framework
2. **`ci-cd-pipeline/ARCHITECTURE.md`** — how blue-green actually works in this setup, with a diagram of the flow
3. **`ci-cd-pipeline/README.md`** — how to run everything end-to-end
4. **`ci-cd-pipeline/Jenkinsfile`** and **`ci-cd-pipeline/.gitlab-ci.yml`** — the actual pipeline definitions
5. **`ci-cd-pipeline/deployment/scripts/deploy.sh`** — the script the CI tool calls; this is where the blue-green logic lives
6. **`ci-cd-pipeline/ansible/deploy.yml`** — same deployment expressed as an Ansible playbook (ties Q1 to Q2)
