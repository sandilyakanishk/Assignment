#!/usr/bin/env bash
# ============================================================================
# Smoke tests run against the INACTIVE color BEFORE switching live traffic.
# Each check that matters in your domain should be here. Keep them fast.
# ============================================================================

set -euo pipefail

PORT="${1:?usage: $0 <port>}"
BASE_URL="http://localhost:$PORT"

pass() { printf "  \033[1;32m✓\033[0m %s\n" "$1"; }
fail() { printf "  \033[1;31m✗\033[0m %s\n" "$1"; exit 1; }

echo "Smoke tests against $BASE_URL"

# 1. Health endpoint reports UP
health="$(curl -fs "$BASE_URL/actuator/health" || true)"
echo "$health" | grep -q '"UP"' && pass "health endpoint reports UP" \
                                || fail "health endpoint not UP — got: $health"

# 2. Root endpoint returns a sane payload
root="$(curl -fs "$BASE_URL/" || true)"
echo "$root" | grep -q '"service":"bluegreen-app"' && pass "root endpoint returns service identity" \
                                                    || fail "root endpoint malformed: $root"

# 3. Color matches what we expect for this port
case "$PORT" in
    8081) expected="blue"  ;;
    8082) expected="green" ;;
    *)    expected=""      ;;
esac
if [[ -n "$expected" ]]; then
    echo "$root" | grep -q "\"color\":\"$expected\"" \
        && pass "color is $expected (as expected for port $PORT)" \
        || fail "color mismatch — expected $expected, got: $root"
fi

# 4. Response time under 1s (basic perf sanity check)
elapsed=$(curl -fs -o /dev/null -w '%{time_total}' "$BASE_URL/")
awk -v t="$elapsed" 'BEGIN { exit !(t < 1.0) }' \
    && pass "response time ${elapsed}s under 1s" \
    || fail "response time ${elapsed}s exceeds 1s budget"

echo "All smoke tests passed."
