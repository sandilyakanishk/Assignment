#!/usr/bin/env bash
# ============================================================================
# Atomically switches the nginx upstream to a given color and reloads nginx.
# Used by deploy.sh after the new color is verified healthy. Can also be
# invoked directly for manual rollback.
#
# Usage:
#     ./switch.sh blue
#     ./switch.sh green
# ============================================================================

set -euo pipefail

TARGET_COLOR="${1:?usage: $0 <blue|green>}"

if [[ "$TARGET_COLOR" != "blue" && "$TARGET_COLOR" != "green" ]]; then
    echo "Color must be 'blue' or 'green'." >&2
    exit 1
fi

NGINX_CONF="$(dirname "$0")/../nginx/nginx.conf"
NGINX_CONTAINER="bluegreen-nginx"

# --- 1. Rewrite the upstream line ---
# We only touch the line that has the ACTIVE_BACKEND marker, so the rest of
# the config is left exactly as-is.
#
# NOTE on the sed delimiter: we use `@` instead of `/` or `|`. `|` would clash
# with the `(blue|green)` alternation in the regex; `/` is fine but `@` reads
# more clearly given the patterns contain `;`, `:`, and `#`.
sed -i.bak -E \
    "s@server app-(blue|green):8080;[[:space:]]*# ACTIVE_BACKEND@server app-${TARGET_COLOR}:8080;  # ACTIVE_BACKEND@" \
    "$NGINX_CONF"

# --- 2. Validate the new config inside the running container ---
docker exec "$NGINX_CONTAINER" nginx -t

# --- 3. Graceful reload — existing connections finish on the old upstream,
#         new ones go to the new upstream. Zero downtime. ---
docker exec "$NGINX_CONTAINER" nginx -s reload

echo "✓ nginx upstream now points to app-${TARGET_COLOR}"
