#!/usr/bin/env bash
# ============================================================================
# Rollback: flip nginx back to the OTHER color.
# This is the single biggest selling point of blue-green deployments —
# rollback is just another switch, takes seconds, doesn't redeploy anything.
# ============================================================================

set -euo pipefail

NGINX_CONF="$(dirname "$0")/../nginx/nginx.conf"

ACTIVE="$(grep -oE 'server app-(blue|green):8080;\s*# ACTIVE_BACKEND' "$NGINX_CONF" \
          | grep -oE '(blue|green)' | head -n1)"

if [[ -z "$ACTIVE" ]]; then
    echo "Could not detect active color — refusing to roll back blindly." >&2
    exit 1
fi

ROLLBACK_TARGET="$([[ "$ACTIVE" == "blue" ]] && echo green || echo blue)"

echo "Current active : $ACTIVE"
echo "Rolling back to: $ROLLBACK_TARGET"

# Confirm the standby is actually healthy before flipping to it
STANDBY_PORT="$([[ "$ROLLBACK_TARGET" == "blue" ]] && echo 8081 || echo 8082)"
if ! curl -fs "http://localhost:$STANDBY_PORT/actuator/health" | grep -q '"UP"'; then
    echo "ERROR: standby app-$ROLLBACK_TARGET is NOT healthy. Cannot roll back." >&2
    exit 2
fi

"$(dirname "$0")/switch.sh" "$ROLLBACK_TARGET"
echo "✓ Rolled back to $ROLLBACK_TARGET"
