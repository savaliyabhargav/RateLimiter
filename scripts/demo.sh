#!/usr/bin/env bash
# Fires N requests at the protected endpoint as one client and prints the status of each,
# so you can watch 201s turn into 429s.
#
# usage: ./scripts/demo.sh [client-id] [requests] [base-url]

set -u

CLIENT="${1:-alice}"
COUNT="${2:-12}"
BASE_URL="${3:-http://localhost:8080}"

echo "Active configuration:"
curl -sS "${BASE_URL}/api/admin/rate-limit"
echo -e "\n"

echo "Sending ${COUNT} requests as '${CLIENT}' ..."
for i in $(seq 1 "${COUNT}"); do
  response=$(curl -sS -o /dev/null -w "%{http_code} algo=%header{X-RateLimit-Algorithm} remaining=%header{X-RateLimit-Remaining} retry_after=%header{Retry-After}" \
    -X POST "${BASE_URL}/api/messages" \
    -H "Content-Type: application/json" \
    -H "X-Client-Id: ${CLIENT}" \
    -d "{\"message\":\"request ${i}\"}")
  printf "  %2d -> %s\n" "${i}" "${response}"
done

echo
echo "Reset this client with:"
echo "  curl -X DELETE \"${BASE_URL}/api/admin/rate-limit/state?clientId=${CLIENT}\""
