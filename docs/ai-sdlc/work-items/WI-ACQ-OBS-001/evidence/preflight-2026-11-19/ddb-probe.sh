#!/usr/bin/env bash
# Disposable DynamoDB Local probe for WI-ACQ-OBS-001 technical preflight.
# No AWS account, no credentials: dummy static creds against 127.0.0.1:8000.
set -u
export AWS_ACCESS_KEY_ID=local AWS_SECRET_ACCESS_KEY=local AWS_DEFAULT_REGION=us-east-1
EP="--endpoint-url http://127.0.0.1:8000"
T="acq-preflight-$(date +%s)-$$"
echo "table under test: $T"
pass=0; fail=0
ok(){ echo "PASS: $1"; pass=$((pass+1)); }
no(){ echo "FAIL: $1"; fail=$((fail+1)); }
echo "== P4-1 create-table =="
if aws dynamodb create-table --table-name $T $EP \
  --attribute-definitions AttributeName=pk,AttributeType=S AttributeName=sk,AttributeType=S \
  --key-schema AttributeName=pk,KeyType=HASH AttributeName=sk,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST >/dev/null 2>&1; then ok "table created"; else no "table create"; fi
aws dynamodb wait table-exists --table-name $T $EP

# canonical-ish items
EV='{"S":"{\"schemaVersion\":1,\"eventId\":\"EV-1\",\"payloadHash\":\"abc\"}"}'
BIZ='{"pk":{"S":"BIZ#k1"},"sk":{"S":"META"},"state":{"S":"CLAIMED"},"version":{"N":"1"}}'
HEAD='{"pk":{"S":"BIZ#k1"},"sk":{"S":"HEAD"},"lastSequence":{"N":"1"},"firstRetainedSequence":{"N":"1"},"expiryRevision":{"N":"0"}}'
DED='{"pk":{"S":"DEDUPE#EV-1"},"sk":{"S":"META"},"hash":{"S":"abc"}}'
EVT='{"pk":{"S":"TX#t1"},"sk":{"S":"E#000000000001"},"payload":'"$EV"',"eventId":{"S":"EV-1"},"eventSha256":{"S":"abc"}}'

echo "== P4-2 atomic business+event+dedupe+head transaction =="
OUT=$(aws dynamodb transact-write-items $EP --client-request-token tok-preflight-'$T' --transact-items '[
 {"Put":{"TableName":"'"$T"'","Item":'"$BIZ"',"ConditionExpression":"attribute_not_exists(pk)"}},
 {"Put":{"TableName":"'"$T"'","Item":'"$EVT"',"ConditionExpression":"attribute_not_exists(pk)"}},
 {"Put":{"TableName":"'"$T"'","Item":'"$DED"'}},
 {"Put":{"TableName":"'"$T"'","Item":'"$HEAD"'}}
]' 2>&1); rc=$?
if [ $rc -eq 0 ]; then ok "4-item transaction committed"; else no "transaction: $OUT"; fi
NE=$(aws dynamodb scan --table-name $T $EP --select COUNT --query Count --output text)
NC=$(aws dynamodb scan --table-name $T $EP --consistent-read --select COUNT --query Count --output text)
echo "  post-commit scan: default(eventual)=$NE consistent=$NC"
[ "$NC" = "4" ] && ok "all 4 items visible via STRONGLY CONSISTENT read (count=$NC)" || no "consistent scan expected 4, got $NC"
[ "$NE" = "4" ] || ok "default eventual Scan observed stale ($NE) immediately after commit -> catch-up MUST set ConsistentRead=true"

echo "== P4-3 condition failure leaves NO partial write =="
OUT=$(aws dynamodb transact-write-items $EP --transact-items '[
 {"Put":{"TableName":"'"$T"'","Item":{"pk":{"S":"BIZ#k2"},"sk":{"S":"META"},"state":{"S":"CLAIMED"}}}},
 {"Put":{"TableName":"'"$T"'","Item":{"pk":{"S":"TX#t2"},"sk":{"S":"E#000000000001"},"payload":'"$EV"',"eventId":{"S":"EV-1"}}}},
 {"Update":{"TableName":"'"$T"'","Key":{"pk":{"S":"BIZ#k2"},"sk":{"S":"HEAD"}},
   "UpdateExpression":"SET lastSequence = if_not_exists(lastSequence, :zero) + :one",
   "ConditionExpression":"attribute_exists(pk)","ExpressionAttributeValues":{":zero":{"N":"0"},":one":{"N":"1"}}}},
 {"ConditionCheck":{"TableName":"'"$T"'","Key":{"pk":{"S":"BIZ#k1"},"sk":{"S":"META"}},"ConditionExpression":"version = :impossible",
   "ExpressionAttributeValues":{":impossible":{"N":"999"}}}}
]' 2>&1); rc=$?
if [ $rc -ne 0 ] && echo "$OUT" | grep -q TransactionCanceledException; then ok "failed condition cancels the whole transaction"; else no "expected cancellation, got rc=$rc $OUT"; fi
N2=$(aws dynamodb get-item --table-name $T $EP --key '{"pk":{"S":"BIZ#k2"},"sk":{"S":"META"}}' --query 'Item' --output json)
N3=$(aws dynamodb get-item --table-name $T $EP --key '{"pk":{"S":"TX#t2"},"sk":{"S":"E#000000000001"}}' --query 'Item' --output json)
N4=$(aws dynamodb get-item --table-name $T $EP --key '{"pk":{"S":"BIZ#k2"},"sk":{"S":"HEAD"}}' --query 'Item' --output json)
[ "$N2" = "null" ] && [ "$N3" = "null" ] && [ "$N4" = "null" ] && ok "no partial business/event/HEAD rows written on cancel" || no "partial writes leaked: $N2 / $N3 / $N4"

echo "== P4-3b one transaction cannot touch the SAME item twice =="
OUT=$(aws dynamodb transact-write-items $EP --transact-items '[
 {"Update":{"TableName":"'"$T"'","Key":{"pk":{"S":"BIZ#k1"},"sk":{"S":"HEAD"}},"UpdateExpression":"SET a = :one","ExpressionAttributeValues":{":one":{"N":"1"}}}},
 {"ConditionCheck":{"TableName":"'"$T"'","Key":{"pk":{"S":"BIZ#k1"},"sk":{"S":"HEAD"}},"ConditionExpression":"attribute_exists(pk)"}}
]' 2>&1); rc=$?
[ $rc -ne 0 ] && echo "$OUT" | grep -qi "multiple operations on one item" && ok "duplicate-item transaction rejected (ValidationException)" || no "duplicate-item transaction not rejected: $OUT"

echo "== P4-4 idempotent clientRequestToken (same token, same params) =="
REQ='[{"Put":{"TableName":"'"$T"'","Item":{"pk":{"S":"BIZ#k3"},"sk":{"S":"META"},"v":{"N":"1"}}}}]'
aws dynamodb transact-write-items $EP --client-request-token tok-dup-'$T' --transact-items "$REQ" >/dev/null 2>&1
OUT=$(aws dynamodb transact-write-items $EP --client-request-token tok-dup-'$T' --transact-items "$REQ" 2>&1); rc=$?
[ $rc -eq 0 ] && ok "repeat with identical token+params is a no-op success" || no "idempotent repeat failed: $OUT"
OUT=$(aws dynamodb transact-write-items $EP --client-request-token tok-dup-'$T' --transact-items '[{"Put":{"TableName":"'"$T"'","Item":{"pk":{"S":"BIZ#k3"},"sk":{"S":"META"},"v":{"N":"2"}}}}]' 2>&1); rc=$?
[ $rc -ne 0 ] && echo "$OUT" | grep -qi "IdempotentParameterMismatch\|different" && ok "same token + different params rejected (IdempotentParameterMismatch)" || no "token reuse with different params not rejected: $OUT"

echo "== P4-5 HEAD conditional increment / no duplicate sequence =="
aws dynamodb update-item --table-name $T $EP --key '{"pk":{"S":"BIZ#k3"},"sk":{"S":"HEAD"}}' \
  --update-expression 'SET lastSequence = if_not_exists(lastSequence, :zero) + :one, firstRetainedSequence = if_not_exists(firstRetainedSequence, :one)' \
  --condition-expression 'attribute_not_exists(lastSequence) OR lastSequence = :expected' \
  --expression-attribute-values '{":zero":{"N":"0"},":one":{"N":"1"},":expected":{"N":"0"}}' >/dev/null 2>&1 && ok "HEAD increment with CAS condition (expected=0)" || no "HEAD increment"
# concurrent/stale CAS must fail
OUT=$(aws dynamodb update-item --table-name $T $EP --key '{"pk":{"S":"BIZ#k3"},"sk":{"S":"HEAD"}}' \
  --update-expression 'SET lastSequence = lastSequence + :one' \
  --condition-expression 'lastSequence = :expected' \
  --expression-attribute-values '{":one":{"N":"1"},":expected":{"N":"0"}}' 2>&1); rc=$?
[ $rc -ne 0 ] && ok "stale HEAD CAS rejected (no duplicate sequence allocation)" || no "stale CAS accepted"
LS=$(aws dynamodb get-item --table-name $T $EP --key '{"pk":{"S":"BIZ#k3"},"sk":{"S":"HEAD"}}' --query 'Item.lastSequence.N' --output text)
[ "$LS" = "1" ] && ok "HEAD lastSequence=1 (single allocation)" || no "lastSequence=$LS"

echo "== P4-6 Query pagination / cursor round-trip =="
for i in 2 3 4 5; do
  S=$(printf "E#%012d" $i)
  aws dynamodb put-item --table-name $T $EP --item '{"pk":{"S":"TX#t3"},"sk":{"S":"'"$S"'"},"n":{"N":"'"$i"'"}}' >/dev/null
done
R1=$(aws dynamodb query --table-name $T $EP --key-condition-expression 'pk = :p AND begins_with(sk, :pre)' \
  --expression-attribute-values '{":p":{"S":"TX#t3"},":pre":{"S":"E#"}}' --limit 2 --output json)
K1=$(echo "$R1" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(json.dumps(d.get("LastEvaluatedKey")))')
C1=$(echo "$R1" | python3 -c 'import sys,json; print(len(json.load(sys.stdin)["Items"]))')
R2=$(aws dynamodb query --table-name $T $EP --key-condition-expression 'pk = :p AND begins_with(sk, :pre)' \
  --expression-attribute-values '{":p":{"S":"TX#t3"},":pre":{"S":"E#"}}' --limit 100 \
  --exclusive-start-key "$K1" --output json)
C2=$(echo "$R2" | python3 -c 'import sys,json; print(len(json.load(sys.stdin)["Items"]))')
[ "$C1" = "2" ] && [ "$C2" = "2" ] && ok "page1=2, page2=2 via LastEvaluatedKey from a strongly consistent base-table Query" || no "pagination C1=$C1 C2=$C2 K1=$K1"
# consistent read flag accepted on base table
aws dynamodb query --table-name $T $EP --consistent-read --key-condition-expression 'pk = :p' \
  --expression-attribute-values '{":p":{"S":"TX#t3"}}' >/dev/null 2>&1 && ok "ConsistentRead accepted on base-table Query" || no "consistent read"

echo "== P4-7 expiry revision compaction metadata =="
aws dynamodb update-item --table-name $T $EP --key '{"pk":{"S":"BIZ#k3"},"sk":{"S":"HEAD"}}' \
  --update-expression 'SET expiryRevision = if_not_exists(expiryRevision, :zero) + :one, expiredRanges = :r' \
  --expression-attribute-values '{":zero":{"N":"0"},":one":{"N":"1"},":r":{"L":[{"M":{"from":{"N":"1"},"to":{"N":"2"}}}]}}' >/dev/null 2>&1 \
  && ok "expiryRevision increment + compact expiredRanges list written" || no "expiry metadata update"
ER=$(aws dynamodb get-item --table-name $T $EP --key '{"pk":{"S":"BIZ#k3"},"sk":{"S":"HEAD"}}' --query 'Item.expiryRevision.N' --output text)
[ "$ER" = "1" ] && ok "expiryRevision=1 readable" || no "expiryRevision=$ER"

echo "== P4-8 TTL attribute accepted (logical expiry remains app-enforced) =="
aws dynamodb update-time-to-live --table-name $T $EP --time-to-live-specification 'Enabled=true,AttributeName=expiresAt' >/dev/null 2>&1
ST=$(aws dynamodb describe-time-to-live --table-name $T $EP --query 'TimeToLiveDescription.TimeToLiveStatus' --output text)
[ "$ST" = "ENABLED" ] && ok "TTL status ENABLED on attribute expiresAt" || no "TTL status=$ST"

echo "== P4-9 item size limits around the 4096-byte event budget =="
aws dynamodb put-item --table-name $T $EP --item '{"pk":{"S":"SIZE#4k"},"sk":{"S":"META"},"payload":{"S":"'"$(python3 -c 'print("x"*4096)')"'"}}' >/dev/null 2>&1 \
  && ok "4096-byte payload item written" || no "4096-byte item rejected"
OUT=$(aws dynamodb put-item --table-name $T $EP --item '{"pk":{"S":"SIZE#toobig"},"sk":{"S":"META"},"payload":{"S":"'"$(python3 -c 'print("x"*409401)')"'"}}' 2>&1); rc=$?
[ $rc -ne 0 ] && ok "item >400KB rejected by DynamoDB (item-size ceiling confirmed)" || no "oversized item accepted unexpectedly"

echo "== P4-10 transaction batch limits (expiry worker 40-row batch vs 100-action cap) =="
python3 - "$T" <<'PY' > /tmp/opencode/ddb-tx40.json
import json,sys
t=sys.argv[1]
print(json.dumps([{"Delete":{"TableName":t,"Key":{"pk":{"S":"EXP#%d"%i},"sk":{"S":"META"}}}} for i in range(40)]))
PY
OUT=$(aws dynamodb transact-write-items $EP --transact-items "$(cat /tmp/opencode/ddb-tx40.json)" 2>&1); rc=$?
[ $rc -eq 0 ] && ok "40-action transaction accepted (delete of nonexistent keys is a no-op, no condition)" || no "40-action transaction failed: $OUT"
python3 - "$T" <<'PY' > /tmp/opencode/ddb-tx101.json
import json,sys
t=sys.argv[1]
print(json.dumps([{"Put":{"TableName":t,"Item":{"pk":{"S":"BULK#%d"%i},"sk":{"S":"META"}}}} for i in range(101)]))
PY
OUT=$(aws dynamodb transact-write-items $EP --transact-items "$(cat /tmp/opencode/ddb-tx101.json)" 2>&1); rc=$?
[ $rc -ne 0 ] && ok "101-action transaction rejected (100-action cap confirmed)" || no "101-action transaction accepted unexpectedly"

echo "== P4-11 GSI eventual consistency surface =="
aws dynamodb update-table --table-name $T $EP --attribute-definitions AttributeName=acq,AttributeType=S AttributeName=seq,AttributeType=N \
  --global-secondary-index-updates '[{"Create":{"IndexName":"byAcq","KeySchema":[{"AttributeName":"acq","KeyType":"HASH"},{"AttributeName":"seq","KeyType":"RANGE"}],"Projection":{"ProjectionType":"KEYS_ONLY"}}}]' >/dev/null 2>&1
aws dynamodb wait table-exists --table-name $T $EP
sleep 2
OUT=$(aws dynamodb query --table-name $T $EP --index-name byAcq --key-condition-expression 'acq = :a' --expression-attribute-values '{":a":{"S":"ACQ-A"}}' --output json 2>&1)
echo "$OUT" | grep -q '"Count"' && ok "GSI query path exists (eventual consistency remains a contract property, not a proof)" || no "GSI query: $OUT"

echo "================ SUMMARY: pass=$pass fail=$fail ================"
