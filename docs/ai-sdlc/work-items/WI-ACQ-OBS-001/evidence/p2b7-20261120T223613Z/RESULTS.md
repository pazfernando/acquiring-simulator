# P2b7 — internal signed-evidence service facade: actual results

UTC: 2026-11-20T22:36:13Z start; 22:36:29Z targeted run; 22:36:35Z finish.
Model: DeepSeek V4 Flash (provider `nan`), configured default, no override.
Binding: `/home/pazfernando/projects/acquiring-simulator`, branch `feat/wi-acq-auth-001`,
HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`
(origin `github.com/pazfernando/acquiring-simulator.git`), default configuration.
`head-before.txt` == `head-after.txt`; `git-status-before.txt` == `git-status-after.txt`;
`hashes-before.sha256` == `hashes-after.sha256` (`hashes.diff` empty). No file was edited by the
run.

## Command and outcome

```
JAVA_HOME=/home/pazfernando/.local/opt/jdk-25 \
  /home/pazfernando/.local/opt/maven/bin/mvn -o -q \
  -Dtest=SignedEvidenceAdmissionServiceIntegrationTest test
```
- exit **0** (`targeted-exit.txt`), stderr empty.
- Surefire: **Tests run: 3, Failures: 0, Errors: 0, Skipped: 0**, 6.015 s.
- `tests="3" errors="0" skipped="0" failures="0" flakes="0"` in the XML copy.

Docker/Testcontainers were real (`amazon/dynamodb-local:2.6.1` started, see
`targeted-stdout.txt`). Compilation was real: the executed tests reference the new facade class.

## Exact coverage (three tests)

1. `strictlyLateEvidenceRoutesTheLateGatewayOnceAndExactReplayWritesNothingMore`
   - Fixture `EF:V08-late-approval` (evidence 12:00:05Z > deadline 12:00:03Z, delivery 12:00:20Z),
     ephemeral Ed25519 key, real DIRECT verify + real P6 bind, exact late envelope.
   - `LATE`, late `COMMITTED`, timing `LATE`, `recorded()` true, one business transaction, receipt
     and late metadata present, event rows 3, HEAD 3, slot 3.
   - Exact redelivery 30 s later: `REPLAY`, first receipt and first late metadata preserved, record
     and dedupe rows byte-equal, transaction count unchanged, one receipt.
2. `onTimeEvidenceDeliveredLateIsHandedToTheNormalSignedAdmissionExactlyOnce`
   - Fixture `EF:V02-approved-response` (evidence 12:00:01Z <= deadline 12:00:03Z, delivered
     12:00:20Z).
   - `NORMAL`; late decision `NORMAL_RESULT_ADMISSION`, timing `DELIVERY_DELAY`; normal decision
     `COMMITTED`, normal journal version 3, version pair `2/3`.
   - Exactly one business transaction for the whole attempt; no late metadata on the record; no
     late receipt row; 3 event rows, HEAD 3, slot 3, one normal receipt.
3. `invalidProofOrCarriageWritesNothingAndRoutesNowhere`
   - Flipped Ed25519 signature with otherwise valid carriage → routed to the late gateway,
     `REFUSED`, `recorded()` false.
   - Presented response-body hash that does not describe the exact envelope body → facade `NONE`
     before any gateway call, detail
     `delivery response body does not match the signed response hash`.
   - Neither refusal wrote a receipt or quarantine, advanced version (stays `1/2`), events
     (stays 2), HEAD (stays 2) or slot (stays 2), or changed `AWAITING`/`NONE`.

## Ordering and trust actually exercised

The facade performs only carriage integrity checks (canonical envelope hash, canonical attestation
hash, exact response-body hash, authenticated sender vs proof principal) before any gateway call
and no storage access. Each gateway then enforces trusted-context authorization before any storage
read, real C4 verification before the persisted operation is named, the strong original read, the
real P6 binder over exact bytes, receipt dedupe after classification, and one atomic receipt
transaction. No test passes a verified/bound/late/already-admitted flag, and no caller-selected
route is accepted.

## Trust limits (not covered)

- No HTTP endpoint, no authenticated-transport adapter and no sender authentication: the
  `C4AuthenticatedTransportContext` is still an internal caller-supplied seam.
- No key custody/rotation/revocation, no TLS/IAM principal mapping.
- No runtime activation, `AwsRuntime`/`AuthorizationService` wiring, AWS call, capacity approval
  (test capacity only), downstream call, send, resend or reversal.
- Diagnostic forwarding is explicitly refused by this facade (the signed receipt gateways verify
  without the separate forwarding permission).
- No full-suite, C4 regression, feature run, Git write, provider publication, credential flow,
  deletion or specification/vector edit.

## Next handoff — HTTP / authenticated transport integration

One bounded next slice: an internal HTTP/authenticated-transport adapter or endpoint that
constructs the trusted context only after real transport authentication, parses the exact C4
late-delivery envelope, supplies the owner-approved deadline and authenticated delivery time, and
calls this facade. That slice still needs separate authorization for runtime activation, key
custody, capacity approval and P1 G6 Security review before any deployment or AWS effect.

P2b7 is local construction and local test evidence only; it is not independent QA, Security
review, human acceptance, business admission or deployment.
