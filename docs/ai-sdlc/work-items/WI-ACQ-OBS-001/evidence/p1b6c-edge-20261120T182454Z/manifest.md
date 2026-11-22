# P1b6c gateway edge tests + single full suite — 2026-11-20

Bounded test completion only. No product source edit was required; no feature was added.

## Binding

| Field | Value |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `cfc456f4f469d9558a78fda8ff9b770b00817315` |
| Origin | `https://github.com/pazfernando/acquiring-simulator.git` (configured default, no override) |
| Model | provider `nan`, `deepseek-v4-flash`, no override |
| JDK | 25.0.5 Temurin `/home/pazfernando/.local/opt/jdk-25` |
| Maven | 3.10.1 `/home/pazfernando/.local/opt/maven/bin/mvn` (offline `-o`) |

See `binding.txt` and `run-env.txt`.

## Edit

One test file modified:
`src/test/java/com/acquiring/simulator/aws/DynamoLateEvidenceAdmissionGatewayIntegrationTest.java`
sha256 `d9efe45e09bf3bb6b9ce263188009db9f1d8c01b803fd1f4e8ea0c014ef465a3`.

Added two tests (existing 5 preserved, class now 7):

1. `confirmedRecordWithoutALateReceiptIsAConflictThatPreservesTheOutcome` — the outcome is
   confirmed through the normal `ResultCriticalComposer` admission, so the record has **no late
   receipt** (asserted empty) and is a terminal outcome from a different evidence origin. Late
   admission returns `CONTENT_CONFLICT`, and the original `APPROVED` state, issuer provenance,
   `outcomeDeterminedAt`, committed version pair `2/3`, 3 events, HEAD 3 and 3 slots are preserved
   with no invented receipt or second result. No data is deleted to create the precondition.
2. `confirmedLateRecordWithMissingRetainedResultEventIsUnknownAndPreserved` — after one real late
   commit, only the retained-result dedupe identity row (`ENV#SIM#DEDUPE` /
   `P#processor#E#APPROVED-AUTH-001`) is removed in the isolated per-test ephemeral event table
   (test-seam manipulation; no pre-existing environment is touched). `store.find` proves nothing
   retained, admission returns `UNKNOWN` with detail "confirmed record has no retained result
   event", and the confirmed state, receipt `LATE-RECEIPT-001`, version pair `2/3`, event row,
   HEAD 3 and 3 slots are preserved with no new write.

## Execution

Single command on the frozen tree:

`mvn -o test` → exit **0**, BUILD SUCCESS, `Tests run: 247, Failures: 0, Errors: 0, Skipped: 0`.

- stdout: `full-test.stdout.log`
- stderr: `full-test.stderr.log` (only Mockito/JDK agent and SQLite native-access warnings)
- exit: `full-test.exit`
- surefire: `surefire-reports/` (target reports copied)
- class result: 7/7 green, 4.213 s.

## Preservation

`hashes-pre.txt` / `hashes-post.txt` over `src/test`, `src/main/resources`, `pom.xml` (41 files)
are byte-identical (`hashes.diff` empty, exit 0). `git-status-before.txt` and
`git-status-after.txt` are byte-identical (exit 0). No approved specification file was edited, no
tracked file was staged/reverted, and no Git write occurred.

## Non-claims and remaining gaps

- `DownstreamReply` remains the explicit internal non-cryptographic test seam; this is not C4
  sender/relay/role/key/signature or P6 ISO verification and no public endpoint exists.
- The gateway is still unwired; `AwsRuntime` remains inactive; runtime activation is still blocked
  by remaining gaps. Full G6 remains open.
- No commit, push, PR, merge, deploy, cloud call, apply, credential flow or destructive change to
  existing data occurred.
- A local green suite is not independent QA, Security review or human acceptance.

Trust: full suite green on the exact hashed bytes above; targeted class 7/7.

Next concrete implementation slice: wire `LateEvidenceAdmissionGateway` behind an internal
service-level branch (test-only, no public endpoint, no C4 claims) and resolve the remaining legacy
`NOT_CONFIRMED` disposition only if the approved text states the rule; otherwise keep it UNKNOWN
and cite the missing business rule.
