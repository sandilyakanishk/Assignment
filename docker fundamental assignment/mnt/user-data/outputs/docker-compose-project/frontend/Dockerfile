# =============================================================
#  frontend/Dockerfile
#  Author : Kanishk Sandilya
#  Multi-stage build:
#    Stage 1 (builder): install deps + build React app
#    Stage 2 (runner):  serve with lightweight nginx
#  Result: production image is ~25MB instead of ~400MB
# =============================================================

# ── Stage 1: Build ────────────────────────────────────────────
FROM node:20-alpine AS builder

WORKDIR /app

# Copy package files first (cache layer)
COPY package*.json ./
RUN npm ci --silent

# Copy source and build
COPY . .
RUN npm run build

# ── Stage 2: Serve ────────────────────────────────────────────
FROM nginx:alpine AS runner

# Copy built files from Stage 1
COPY --from=builder /app/build /usr/share/nginx/html

# Simple nginx config for SPA (single-page app)
RUN echo 'server { \
  listen 3000; \
  root /usr/share/nginx/html; \
  location / { try_files $uri /index.html; } \
}' > /etc/nginx/conf.d/default.conf

EXPOSE 3000

CMD ["nginx", "-g", "daemon off;"]
