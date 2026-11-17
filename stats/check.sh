#!/usr/bin/env bash
# Runs the receiver locally (no Cloudflare account needed), posts good and bad reports, and checks what was stored.
# Usage: stats/check.sh   (from anywhere; needs Node). Exits non-zero on the first wrong answer.
set -euo pipefail
cd "$(dirname "$0")"
W="npx --yes wrangler@4.156.2"
export WRANGLER_SEND_METRICS=false
state=$(mktemp -d); port=${PORT:-8797}
# The dev server runs in its own process group: killing npx alone leaves workerd serving the port to the next run.
trap 'kill -- -$dev 2>/dev/null || true; rm -rf "$state"' EXIT

$W d1 migrations apply daybook-stats --local --persist-to "$state" >/dev/null
setsid $W dev --local --persist-to "$state" --port "$port" >"$state/dev.log" 2>&1 & dev=$!
for _ in $(seq 60); do curl -s -o /dev/null "http://127.0.0.1:$port/" && break; sleep 1; done

today=$(date -u +%F); id=0b6c5e2a-1f2d-4c3b-9a8e-7d6c5b4a3f2e
good() { cat <<JSON
{"schema":1,"install":"$id","app":"0.1.212","platform":"android","sentOn":"$today","firstPlayed":"2026-09-02","sends":$1,
 "puzzles":[{"id":"mosaic","tier":"EXPERT","opened":41,"finished":29,"abandoned":9,"dailyFinished":22,"practiceFinished":7,
   "medianSeconds":214,"hintsUsed":17,"hintFallbacks":3,"walkthroughFinished":true,"extra":"dropped"}],
 "days":[{"d":"2026-11-15","p":"mosaic","t":"EXPERT","r":"abandoned","sec":380,"h":2}],"name":"dropped"}
JSON
}
post() { curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' --data-binary "$1" "http://127.0.0.1:$port/report"; }
rows() { $W d1 execute daybook-stats --local --persist-to "$state" --json --command "$1" | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["results"])'; }
expect() { [ "$1" = "$2" ] || { echo "FAIL: $3: got $1, want $2"; cat "$state/dev.log" | tail -20; exit 1; }; }

expect "$(post "$(good 1)")" 204 "good report answered"
expect "$(post "$(good 2)")" 204 "same-day resend answered"
expect "$(post '{"schema":2}')" 204 "wrong schema answered the same"
expect "$(post 'not json')" 204 "garbage answered the same"
expect "$(post "$(good 3 | sed 's/"EXPERT"/"EASY"/')")" 204 "bad tier answered the same"
expect "$(post "$(good 3 | sed "s/$today/2026-02-30/")")" 204 "impossible date answered the same"
expect "$(post "$(good 3 | sed 's/"sec":380/"sec":-1/')")" 204 "negative count answered the same"
expect "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port/report")" 405 "GET refused"
expect "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port/")" 404 "other paths 404"
expect "$(curl -s -D - -o /dev/null -X OPTIONS "http://127.0.0.1:$port/report" | tr -d '\r' | grep -i '^access-control-allow-origin:' | tr 'A-Z' 'a-z')" \
  "access-control-allow-origin: https://knowhowit.com.au" "CORS preflight"

expect "$(rows 'SELECT COUNT(*) AS n FROM reports')" "[{'n': 1}]" "one row: bad reports dropped, same-day resend replaced"
expect "$(rows "SELECT json_extract(payload, '\$.sends') AS s FROM reports")" "[{'s': 2}]" "the resend won"
expect "$(rows "SELECT instr(payload, 'dropped') AS i FROM reports")" "[{'i': 0}]" "unknown fields not stored"
echo "stats receiver: all checks passed"
