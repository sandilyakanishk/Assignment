# Docker Compose — Multi-Container Applications
**Assignment Submission | Kanishk Sandilya**

---

## What is Docker Compose?

Docker Compose is a tool for **defining and running multi-container Docker applications**.
Instead of manually running `docker run` commands for each service, you describe the entire
application stack in a single YAML file (`docker-compose.yml`) and start everything with
one command.

```bash
docker compose up -d        # Start all services in background
docker compose down         # Stop and remove all containers
```

---

## Why Multi-Container?

Real applications have multiple moving parts:

| Component      | Purpose                                  | Example Image      |
|----------------|------------------------------------------|--------------------|
| **Frontend**   | UI served to browser                     | node, nginx        |
| **Backend**    | Business logic, REST API                 | python/flask       |
| **Database**   | Persistent data storage                  | postgres, mysql    |
| **Cache**      | Fast in-memory data store                | redis              |
| **Proxy**      | Routes traffic, load balancing, SSL      | nginx, traefik     |

Each runs in its own container — isolated, individually scalable, replaceable.

---

## Core `docker-compose.yml` Concepts

### Services
Each service = one container. Minimum definition:
```yaml
services:
  myapp:
    image: nginx:alpine
    ports:
      - "80:80"
```

### Build vs Image
```yaml
# Use a pre-built image from Docker Hub
db:
  image: postgres:15-alpine

# Build from a local Dockerfile
backend:
  build:
    context: ./backend
    dockerfile: Dockerfile
```

### Ports
```yaml
ports:
  - "HOST_PORT:CONTAINER_PORT"
  - "8080:5000"    # localhost:8080 → container:5000
```

### Environment Variables
```yaml
environment:
  - DATABASE_URL=postgresql://user:pass@db:5432/mydb
  - FLASK_ENV=production
```

### Volumes — Persistent Data
```yaml
# Named volume (managed by Docker, survives docker compose down)
volumes:
  - pg_data:/var/lib/postgresql/data

# Bind mount (maps host folder into container — great for dev)
  - ./backend:/app

# Declare at the bottom of the file:
volumes:
  pg_data:
```

### Networks — Service Discovery
```yaml
networks:
  - frontend-net
  - backend-net

networks:
  frontend-net:
    driver: bridge
  backend-net:
    driver: bridge
```

Services on the same network can reach each other by **service name** as the hostname.
So `backend` can connect to `db` at hostname `db` — no IP addresses needed.

### depends_on — Startup Order
```yaml
backend:
  depends_on:
    db:
      condition: service_healthy   # Wait for healthcheck to pass
```

### Healthchecks
```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U user -d mydb"]
  interval: 10s
  timeout: 5s
  retries: 5
  start_period: 10s
```

---

## This Assignment's Stack

```
Browser
  │
  ▼ port 80
┌─────────────────────────────┐
│  Nginx (Reverse Proxy)      │  ← single entry point
│  /api/* → Flask backend     │
│  /*     → React frontend    │
└──────────┬──────────────────┘
           │
     frontend-net
           │
  ┌────────┴────────┐
  │                 │
  ▼                 ▼
┌───────────┐  ┌────────────┐
│  Frontend │  │  Backend   │  ← also on backend-net
│  (React)  │  │  (Flask)   │
└───────────┘  └──────┬─────┘
                      │
                backend-net
                      │
           ┌──────────┴──────────┐
           │                     │
           ▼                     ▼
    ┌─────────────┐      ┌─────────────┐
    │  PostgreSQL │      │    Redis    │
    │  (Database) │      │   (Cache)   │
    └─────────────┘      └─────────────┘
```

---

## Key Commands Reference

```bash
# Start all services (detached / background)
docker compose up -d

# Start and rebuild images
docker compose up -d --build

# Stop all services (keeps volumes)
docker compose down

# Stop and delete all volumes (wipes DB data!)
docker compose down -v

# View running services
docker compose ps

# Follow logs of all services
docker compose logs -f

# Follow logs of one service
docker compose logs -f backend

# Run a command inside a running container
docker compose exec backend bash
docker compose exec db psql -U kanishk -d appdb

# Scale a service (run 3 backend instances)
docker compose up -d --scale backend=3

# Rebuild just one service without restarting others
docker compose up -d --build backend

# See resource usage
docker compose stats
```

---

## Development vs Production

This project uses **two Compose files**:

| File                          | Used for    | Loaded automatically? |
|-------------------------------|-------------|----------------------|
| `docker-compose.yml`          | Production  | Yes (always)         |
| `docker-compose.override.yml` | Development | Yes (on local only)  |

The override file adds: direct port exposure, hot-reload mounts, pgAdmin.

To run **production only** (skipping the override):
```bash
docker compose -f docker-compose.yml up -d
```

---

## Network Isolation (Security)

Two separate networks prevent the frontend from directly accessing the database:

- `frontend-net`: Nginx + Frontend + Backend
- `backend-net`: Backend + DB + Redis

The DB container is **not** on `frontend-net` — it literally cannot be reached
by Nginx or the React app. Only the Backend service bridges both networks.

---

## The Cache-Aside Pattern (Redis + Postgres)

The backend uses Redis as a cache in front of Postgres:

```
Request → Check Redis → HIT  → Return cached data (fast!)
                     → MISS → Query Postgres → Store in Redis → Return data
```

This reduces database load and speeds up repeated reads.

---

*Assignment by: Kanishk Sandilya*
