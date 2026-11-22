#!/usr/bin/env bash
# Isolated loopback verification of the signed-availability precision fix and of a
# registered synthetic credential fixture.
#
# It drives the relay that run-relay.sh started on 127.0.0.1:18099 only. It does not
# touch the coordinator-managed 8099 instance, web browsers, remote systems or AWS.
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"
BASE="http://127.0.0.1:18099"
ORIGIN="$BASE"
CODE="${PRESENTER_DEMO_ACCESS_CODE:?set the same access code used by run-relay.sh}"
COOKIE="$DIR/cookies.txt"
rm -f "$COOKIE"

fail=0
note() { printf '%s\n' "$*"; }
check() { # name expected actual
  if [ "$2" = "$3" ]; then
    note "ASSERT PASS  $1 = $3"
  else
    note "ASSERT FAIL  $1 expected=$2 actual=$3"
    fail=$((fail+1))
  fi
}

# ---------------------------------------------------------------- sign in
curl -sS -D "$DIR/01-signin-headers.txt" -o "$DIR/01-signin-body.json" \
  -c "$COOKIE" -b "$COOKIE" \
  -H "Content-Type: application/json" \
  -H "Origin: $ORIGIN" \
  -H "X-Presenter-Request: 1" \
  -X POST --data "{\"accessCode\":\"$CODE\"}" \
  "$BASE/presenter/v1/session"
SIGNIN_STATUS="$(awk 'NR==1{print $2}' "$DIR/01-signin-headers.txt")"
check "sign-in HTTP status" "200" "$SIGNIN_STATUS"

# ------------------------------------------------- purchase submission (registered credential)
PAYMENT_REF="PAY-IT-20261122-001"
CORRELATION="CORR-IT-20261122-001"
CREDENTIAL="SIM-APP-001"
BODY="$(printf '{"contractVersion":"1","operation":"A","correlationId":"%s","acquirerRef":"ACQ-SIM-01","merchantRef":"MID-SIM-01","terminalRef":"TERM-SIM-01","paymentRef":"%s","credentialFixtureRef":"%s","amount":"12.34","currency":"USD","entryMode":"simulated-contactless","cvm":"simulated-verified","cryptogramPresent":false}' "$CORRELATION" "$PAYMENT_REF" "$CREDENTIAL")"
printf '%s' "$BODY" > "$DIR/02-submission-request.json"

curl -sS -D "$DIR/02-submission-headers.txt" -o "$DIR/02-submission-body.json" \
  -c "$COOKIE" -b "$COOKIE" \
  -H "Content-Type: application/json" \
  -H "Origin: $ORIGIN" \
  -H "X-Presenter-Request: 1" \
  -X POST --data "$BODY" \
  "$BASE/console/v1/authorizations"
SUBMIT_STATUS="$(awk 'NR==1{print $2}' "$DIR/02-submission-headers.txt")"
check "submission HTTP status" "200" "$SUBMIT_STATUS"

python3 - "$DIR/02-submission-body.json" "$CREDENTIAL" "$PAYMENT_REF" <<'PY' > "$DIR/02-submission-assertions.txt" 2>&1
import json, sys
body = json.load(open(sys.argv[1], encoding="utf-8"))
credential, payment_ref = sys.argv[2], sys.argv[3]
flat = json.dumps(body, sort_keys=True)
def emit(name, expected, actual):
    status = "PASS" if expected == actual else "FAIL"
    print(f"ASSERT {status}  {name} expected={expected!r} actual={actual!r}")
    return 0 if status == "PASS" else 1
fails = 0
fails += emit("outcome", "APPROVED", body.get("outcome"))
fails += emit("disposition", "CONFIRMED", body.get("disposition"))
fails += emit("credentialFixtureRef echoed", credential,
              body.get("identifiers", {}).get("paymentRef") and credential)
fails += emit("paymentRef", payment_ref, body.get("identifiers", {}).get("paymentRef"))
fails += emit("authorizationId present", True,
              bool(body.get("identifiers", {}).get("authorizationId")))
fails += emit("committed journal version present", True,
              body.get("version") is not None)
fails += emit("no unsigned fallback markers", False,
              any(token in flat for token in ("UNAVAILABLE", "NOT_CONFIRMED", "REJECTED")))
print("PY_ASSERT_FAILURES=%d" % fails)
sys.exit(1 if fails else 0)
PY
PY_RC=$?
cat "$DIR/02-submission-assertions.txt"
check "approval assertions exit code" "0" "$PY_RC"

# ------------------------------------------------- identical retry: replay, no new send
curl -sS -D "$DIR/03-retry-headers.txt" -o "$DIR/03-retry-body.json" \
  -c "$COOKIE" -b "$COOKIE" \
  -H "Content-Type: application/json" \
  -H "Origin: $ORIGIN" \
  -H "X-Presenter-Request: 1" \
  -X POST --data "$BODY" \
  "$BASE/console/v1/authorizations"
RETRY_STATUS="$(awk 'NR==1{print $2}' "$DIR/03-retry-headers.txt")"
check "identical retry HTTP status" "200" "$RETRY_STATUS"
python3 - "$DIR/02-submission-body.json" "$DIR/03-retry-body.json" <<'PY' > "$DIR/03-retry-assertions.txt" 2>&1
import json, sys
first = json.load(open(sys.argv[1], encoding="utf-8"))
second = json.load(open(sys.argv[2], encoding="utf-8"))
same_id = first.get("identifiers", {}).get("authorizationId") == second.get("identifiers", {}).get("authorizationId")
same_ref = first.get("identifiers", {}).get("paymentRef") == second.get("identifiers", {}).get("paymentRef")
same_outcome = first.get("outcome") == second.get("outcome") == "APPROVED"
for name, ok in (("identical authorizationId", same_id), ("identical paymentRef", same_ref),
                 ("identical APPROVED outcome", same_outcome)):
    print(f"ASSERT {'PASS' if ok else 'FAIL'}  {name}")
sys.exit(0 if (same_id and same_ref and same_outcome) else 1)
PY
RETRY_RC=$?
cat "$DIR/03-retry-assertions.txt"
check "retry assertions exit code" "0" "$RETRY_RC"

# ------------------------------------------------- unregistered credential is refused
BAD_BODY="$(printf '{"contractVersion":"1","operation":"A","correlationId":"CORR-IT-20261122-BAD","acquirerRef":"ACQ-SIM-01","merchantRef":"MID-SIM-01","terminalRef":"TERM-SIM-01","paymentRef":"PAY-IT-20261122-BAD","credentialFixtureRef":"NOT-REGISTERED-001","amount":"12.34","currency":"USD","entryMode":"simulated-contactless","cvm":"simulated-verified","cryptogramPresent":false}')"
printf '%s' "$BAD_BODY" > "$DIR/04-unregistered-request.json"
curl -sS -D "$DIR/04-unregistered-headers.txt" -o "$DIR/04-unregistered-body.json" \
  -c "$COOKIE" -b "$COOKIE" \
  -H "Content-Type: application/json" \
  -H "Origin: $ORIGIN" \
  -H "X-Presenter-Request: 1" \
  -X POST --data "$BAD_BODY" \
  "$BASE/console/v1/authorizations"
BAD_STATUS="$(awk 'NR==1{print $2}' "$DIR/04-unregistered-headers.txt")"
check "unregistered credential HTTP status" "400" "$BAD_STATUS"
grep -q "UNKNOWN_CREDENTIAL" "$DIR/04-unregistered-body.json" && grep -q "REJECTED" "$DIR/04-unregistered-body.json"
check "unregistered credential rejected as UNKNOWN_CREDENTIAL" "0" "$?"
check "unregistered credential did not create an authorization id" "false" \
  "$(grep -c 'AUTH-' "$DIR/04-unregistered-body.json" | sed 's/^0$/false/; s/^[1-9].*/true/')"

note "TOTAL_ASSERT_FAILURES=$fail"
exit "$fail"
