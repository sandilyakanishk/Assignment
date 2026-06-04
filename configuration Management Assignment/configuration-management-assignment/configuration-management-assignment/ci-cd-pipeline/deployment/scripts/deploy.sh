#!/usr/bin/env bash
# ============================================================================
# Blue-Green Deployment Script
# ============================================================================
# Deploys a new image tag to the INACTIVE color, health-checks it, and then
# switches nginx to point at it. The previously-active color becomes the
# new standby and can be rolled back to instantly.
#
# Usage:
#     ./deploy.sh <image-tag>
#
# Example:
#     ./deploy.sh 1.2.3
# ============================================================================

set -euo pipefail

# -------------------- Config --------------------
IMAGE_NAME="${IMAGE_NAME:-bluegreen-app}"
NGINX_CONF="$(dirname "$0")/../nginx/nginx.conf"
COMPOSE_DIR="$(dirname "$0")/.."
HEALTH_RETRIES=30
HEALTH_INTERVAL=2

NEW_TAG="${1:?usage: $0 <image-tag>}"

# -------------------- Helpers --------------------
log() { printf "\033[1;34m[deploy]\033[0m %s\n" "$*"; }
err() { printf "\033[1;31m[deploy]\033[0m %s\n" "$*" >&2; }

# Read currently-active color from nginx.conf by grepping the marker line
detect_active_color() {
    grep -oE 'server app-(blue|green):8080;\s*# ACTIVE_BACKEND' "$NGINX_CONF" \
        | grep -oE '(blue|green)' \
        | head -n1
}

# Port-on-host where each color is exposed for direct probing
host_port_for() {
    case "$1" in
        blue)  echo 8081 ;;
        green) echo 8082 ;;
        *) err "unknown color: $1"; exit 1 ;;
    esac
}

# -------------------- 1. Figure out which way we're flipping --------------------
ACTIVE="$(detect_active_color)"
if [[ -z "$ACTIVE" ]]; then
    err "Could not detect active color from $NGINX_CONF — defaulting to blue."
    ACTIVE="blue"
fi
TARGET="$([[ "$ACTIVE" == "blue" ]] && echo green || echo blue)"
TARGET_PORT="$(host_port_for "$TARGET")"

log "Active color  : $ACTIVE"
log "Deploying to  : $TARGET"
log "New image tag : $IMAGE_NAME:$NEW_TAG"

# -------------------- 2. Roll the TARGET container to the new image --------------------
log "Pulling/checking image $IMAGE_NAME:$NEW_TAG ..."
docker image inspect "$IMAGE_NAME:$NEW_TAG" >/dev/null 2>&1 || \
    docker pull "$IMAGE_NAME:$NEW_TAG"

# Tag the new image as the target color's expected tag, then restart that container
TAG_ENV_VAR="$( [[ "$TARGET" == "blue" ]] && echo BLUE_TAG || echo GREEN_TAG )"
export "$TAG_ENV_VAR"="$NEW_TAG"

log "Recreating app-$TARGET with $IMAGE_NAME:$NEW_TAG ..."
( cd "$COMPOSE_DIR" && \
  IMAGE_NAME="$IMAGE_NAME" \
  BLUE_TAG="${BLUE_TAG:-1.0.0}" GREEN_TAG="${GREEN_TAG:-1.0.0}" \
  docker compose up -d --force-recreate "app-$TARGET" )

# -------------------- 3. Health-check the target --------------------
log "Health-checking app-$TARGET on port $TARGET_PORT ..."
HEALTHY=0
for ((i=1; i<=HEALTH_RETRIES; i++)); do
    if curl -fs "http://localhost:$TARGET_PORT/actuator/health" 2>/dev/null | grep -q '"UP"'; then
        HEALTHY=1
        log "  ✓ app-$TARGET is UP (attempt $i)"
        break
    fi
    printf "  ... attempt %d/%d not ready\n" "$i" "$HEALTH_RETRIES"
    sleep "$HEALTH_INTERVAL"
done

if [[ "$HEALTHY" -ne 1 ]]; then
    err "app-$TARGET FAILED to become healthy after $((HEALTH_RETRIES * HEALTH_INTERVAL))s."
    err "Live traffic is UNTOUCHED, still pointing at $ACTIVE. Investigate logs:"
    err "    docker logs app-$TARGET"
    exit 2
fi

# -------------------- 4. Smoke test --------------------
log "Running smoke tests against the inactive color ..."
"$(dirname "$0")/smoke-test.sh" "$TARGET_PORT" || {
    err "Smoke tests failed on app-$TARGET. Not switching traffic."
    exit 3
}

# -------------------- 5. Flip the switch --------------------
log "All checks passed. Switching nginx upstream → $TARGET ..."
"$(dirname "$0")/switch.sh" "$TARGET"

log "Deployment complete."
log "  → Live traffic:   $TARGET ($IMAGE_NAME:$NEW_TAG)"
log "  → Standby (rollback target): $ACTIVE"
