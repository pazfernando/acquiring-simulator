# P1b6c evidence — late-evidence admission gateway (RECOVERY, PARTIAL)

Status: **PARTIAL RECOVERY. Compiles. NO gateway test exists and none was authored in this
recovery. No test was run. G6 remains open; runtime inactive.**

## Binding

- Repository: `/home/pazfernando/projects/acquiring-simulator`
- Branch: `feat/wi-acq-auth-001`
- HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315`
- Model: provider `nan`, `deepseek-v4-flash` (configured default, no override)
- JDK: `/home/pazfernando/.local/opt/jdk-25`, OpenJDK 25.0.5 LTS
- Maven: `/home/pazfernando/.local/opt/maven/bin/mvn`, Apache Maven 3.10.1
- Captured: `2026-11-20T18:18:08Z`

`binding.txt` was written before the build; its `mvn_version=` line is empty because `mvn -v` was
invoked without `JAVA_HOME` at capture time. The authoritative Maven version above and in
`hashes.txt`-covered runs is 3.10.1, captured after with `JAVA_HOME` set.

## Slice and what actually existed on disk

This recovery inspected (did not recreate or research) the three files left by the aborted slice:

| File | Status | SHA-256 |
| --- | --- | --- |
| `src/main/java/com/acquiring/simulator/adapter/aws/LateEvidenceAdmissionGateway.java` | new, untracked | `29e2d366…` |
| `src/main/java/com/acquiring/simulator/domain/event/LateEvidenceReceipt.java` | new, untracked | `f7cd1cce…` |
| `src/main/java/com/acquiring/simulator/adapter/aws/DynamoAuthorizationRepository.java` | tracked, modified | `710c34c9…` |

Gateways accessors referenced by the new class (`readVersionEvidence`, `readLateEvidenceReceipt`)
and the composer constants it uses (`LATE_FLAG_ATTRIBUTE`, receipt/deadline attributes,
`MAX_RECEIPT_ID_LENGTH`, `PRIOR_INTENT_JOURNAL_VERSION`, `LATE_EVIDENCE_JOURNAL_VERSION`,
`classify`, `submission`, `withSlotAccounting`) are all present. No source edit was needed: the
tree already compiled.

**Gateway test: ABSENT.** `find src/test -iname '*LateEvidenceAdmission*'` returns nothing. The
only related test file is the earlier `aws/DynamoLateEvidenceTransitionIntegrationTest.java`
(P1b6b scope, composer only). Writing a new test is outside this recovery's allowed scope, so the
gateway remains **uncovered by any test** and the targeted run is recorded as pending.

## Actual run

| Command | Exit | Streams | Result |
| --- | --- | --- | --- |
| `mvn -o -q test-compile` | **0** | both empty | main + test sources compile; no test executed |

No full suite, no regression sweep, no DynamoDB Local, no service/runtime wiring was run. Earlier
P1b6b full-suite non-completion and P1b6a's 229/229 belong to their own snapshots and are not
claimed here.

## Tree stability

- `hashes-pre.txt` and `hashes-post.txt`: 120 entries (`find src -name '*.java'` + `pom.xml`),
  byte-identical; `hashes-diff.txt` empty.
- `git-status-before.txt` and `git-status-after.txt` byte-identical; `git-status-diff.txt` empty.

This is a tree-level equality check around the compile, not an in-run Maven digest.

## Pending exact command

See `pending-command.txt`:

```
export JAVA_HOME=/home/pazfernando/.local/opt/jdk-25
export PATH="$JAVA_HOME/bin:/home/pazfernando/.local/opt/maven/bin:$PATH"
cd /home/pazfernando/projects/acquiring-simulator
mvn -o -Dtest=DynamoLateEvidenceAdmissionGatewayIntegrationTest test
```

Expected (absent) test path:
`src/test/java/com/acquiring/simulator/aws/DynamoLateEvidenceAdmissionGatewayIntegrationTest.java`

## Non-claims

This is a local compile check on one partial gateway slice — not QA, Security review or human
acceptance. The admission gateway is **untested** and therefore unverified for authorization order,
fresh-read replay, contradiction preservation, ambiguous-commit recovery, or unknown-is-not-no-send
behaviour. The `DownstreamReply` seam remains non-cryptographic test seam only. AWS service
behaviour and C4 authentication were not exercised. `AwsRuntime` is unchanged and inactive; G6 is
open. No commit, push, PR, merge, deploy, cloud call, credential flow, deletion or approved-spec
edit occurred.
