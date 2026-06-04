# Docker & Containerization — Theoretical Overview
**Assignment Submission | Kanishk Sandilya**

---

## 1. What is Containerization?

Containerization is a lightweight form of **OS-level virtualization** that allows you to package
an application and all its dependencies (libraries, config files, runtime) into a single,
isolated unit called a **container**.

Unlike traditional Virtual Machines (VMs), containers share the host OS kernel — they don't
need a full OS per app. This makes them:
- Faster to start (milliseconds vs minutes)
- Lighter in size (MBs vs GBs)
- More portable ("works on my machine" problem solved)

---

## 2. What is Docker?

Docker is the most popular **containerization platform**. It provides:
- A runtime to build and run containers
- A standard image format (Docker Image)
- Docker Hub — a public registry to share images
- Docker Compose — multi-container orchestration

> "Docker = shipping container for software"
> Just as a physical container holds goods regardless of what ship carries it,
> a Docker container holds software regardless of which OS runs it.

---

## 3. Core Docker Concepts

| Concept        | Description                                                              |
|----------------|--------------------------------------------------------------------------|
| **Image**      | Read-only blueprint/template. Built from a Dockerfile.                   |
| **Container**  | A running instance of an image. Isolated process on your machine.        |
| **Dockerfile** | A text script with instructions to build a Docker image layer by layer.  |
| **Registry**   | Storage for Docker images. Default: Docker Hub (hub.docker.com)          |
| **Volume**     | Persistent storage that survives container restarts                       |
| **Network**    | Virtual network allowing containers to communicate with each other        |
| **Layer**      | Each Dockerfile instruction creates a cached, reusable image layer        |

---

## 4. Docker Architecture

Docker uses a **client–server architecture**:

```
Docker CLI (client)
       |
       |  REST API
       v
Docker Daemon (dockerd) ← runs on host OS
       |
       |── Images (stored locally)
       |── Containers (running instances)
       |── Networks
       └── Volumes
```

- The **Docker CLI** is what you type commands into
- The **Docker Daemon** does the actual work (building, running, stopping)
- They communicate via a REST API (usually a Unix socket)

---

## 5. Docker vs Virtual Machines

```
Virtual Machine                    Docker Container
─────────────────────────          ──────────────────────────
App A     | App B                  App A    | App B
─────────────────────────          ──────────────────────────
Guest OS  | Guest OS               Libs     | Libs
─────────────────────────          ──────────────────────────
Hypervisor (VMware, VirtualBox)    Docker Engine (runtime)
─────────────────────────          ──────────────────────────
Host Operating System              Host Operating System
─────────────────────────          ──────────────────────────
Physical Hardware                  Physical Hardware
```

VMs virtualize hardware. Containers virtualize the OS.
Result: Containers are much faster and use far fewer resources.

---

## 6. Dockerfile Instructions Reference

| Instruction  | Purpose                                         | Example                            |
|--------------|-------------------------------------------------|------------------------------------|
| `FROM`       | Base image to build on top of                   | `FROM python:3.11-slim`            |
| `WORKDIR`    | Set working directory inside container          | `WORKDIR /app`                     |
| `COPY`       | Copy files from host into container             | `COPY . .`                         |
| `RUN`        | Execute command during image build              | `RUN pip install -r requirements.txt` |
| `ENV`        | Set environment variable                        | `ENV PORT=5000`                    |
| `EXPOSE`     | Document which port app listens on              | `EXPOSE 5000`                      |
| `CMD`        | Default command when container starts           | `CMD ["python", "app.py"]`         |
| `ENTRYPOINT` | Fixed command (CMD args appended to this)       | `ENTRYPOINT ["gunicorn"]`          |
| `VOLUME`     | Declare a mount point for persistent data       | `VOLUME ["/data"]`                 |
| `ARG`        | Build-time variable                             | `ARG VERSION=1.0`                  |

---

## 7. How Docker Builds an Image (Layer Caching)

Each instruction in a Dockerfile creates an immutable **layer**:

```
Layer 5: CMD ["python", "app.py"]       ← most likely to change
Layer 4: COPY . .                        ← changes when code changes
Layer 3: RUN pip install -r requirements.txt
Layer 2: COPY requirements.txt .         ← only re-run if requirements change
Layer 1: FROM python:3.11-slim           ← rarely changes (base image)
```

**Key insight**: Docker caches layers. If layer 2 hasn't changed, Docker reuses
the cached result for layers 2 and 3 — making builds much faster.
This is why we copy `requirements.txt` BEFORE copying the full source code.

---

## 8. Docker Lifecycle Commands

```bash
# Build an image from a Dockerfile
docker build -t myapp:v1 .

# List all images
docker images

# Run a container from an image
docker run -d -p 8080:5000 --name myapp myapp:v1

# List running containers
docker ps

# See logs
docker logs myapp

# Stop a container
docker stop myapp

# Remove a container
docker rm myapp

# Remove an image
docker rmi myapp:v1

# Execute a command inside running container
docker exec -it myapp bash

# Pull an image from Docker Hub
docker pull nginx:latest

# Push image to Docker Hub
docker push yourusername/myapp:v1
```

---

## 9. Docker Networking

Docker creates virtual networks so containers can talk to each other:

- `bridge` — default network; containers on same bridge can communicate by container name
- `host` — container shares host's network stack (no isolation)
- `none` — completely isolated, no network access

```bash
# Create a custom network
docker network create mynetwork

# Run containers on the same network
docker run -d --network mynetwork --name db postgres
docker run -d --network mynetwork --name app myapp:v1
# Now 'app' can reach 'db' at hostname 'db'
```

---

## 10. Docker Volumes (Persistent Storage)

Containers are ephemeral — data inside them is lost when removed.
Volumes solve this:

```bash
# Named volume (managed by Docker)
docker run -v mydata:/var/lib/postgresql/data postgres

# Bind mount (map host folder to container)
docker run -v /home/kanishk/data:/app/data myapp

# Inspect volume
docker volume inspect mydata
```

---

## 11. Docker Compose (Multi-Container Apps)

Real apps often need multiple services: web server + database + cache.
Docker Compose manages all of them with a single `docker-compose.yml` file.

```bash
docker compose up -d       # Start all services
docker compose down        # Stop and remove all containers
docker compose logs        # View all service logs
docker compose ps          # List running services
```

---

## 12. Best Practices

1. Use **slim/alpine base images** — smaller, faster, fewer vulnerabilities
2. **Copy requirements first**, then source code — maximize layer cache hits
3. Use **`.dockerignore`** to exclude node_modules, .git, etc.
4. **Never store secrets** in Dockerfiles — use environment variables or secrets managers
5. Run containers as **non-root users** for security
6. One process per container — follow the Unix philosophy
7. Always **tag your images** with meaningful versions, not just `latest`

---

*Assignment by: Kanishk Sandilya*
