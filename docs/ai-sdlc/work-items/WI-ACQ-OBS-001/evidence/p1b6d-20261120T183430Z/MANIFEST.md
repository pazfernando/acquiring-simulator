# p1b6d — recovery compile + two saved service test classes

**Status:** run complete. Both classes green. No code edits were required.

## Binding (unchanged, preserved)
- Repo: `/home/pazfernando/projects/acquiring-simulator`
- Branch: `feat/wi-acq-auth-001`
- HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315`
- Model: default configured, no override
- Edits: none. `sha256-delta.txt` is empty (`delta_exit=0`) between preflight and postrun hashes of all `src/main/java`, `src/test`, `src/main/resources`, `src/test/resources`, `pom.xml` (131 files).
- Toolchain: JDK 25.0.5 Temurin `/home/pazfernando/.local/opt/jdk-25`; Maven 3.10.1 `/home/pazfernando/.local/opt/maven/bin/mvn`; `JAVA_HOME` pinned to the JDK.

## What ran
1. `mvn -B -q test-compile` — exit 0, first attempt, no fix needed.
2. `mvn -B test -Dtest=LateEvidenceServiceWiringTest,DynamoLateEvidenceServiceIntegrationTest -DfailIfNoSpecifiedTests=false` — exit 0.

## Results (actual)
| Class | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| `aws.LateEvidenceServiceWiringTest` | 11 | 0 | 0 | 0 |
| `aws.DynamoLateEvidenceServiceIntegrationTest` | 5 | 0 | 0 | 0 |
| **Total** | **16** | **0** | **0** | **0** |

Dynamo test used `amazon/dynamodb-local:2.6.1` via Testcontainers 2.0.5 on Docker 29.1.3 (`DynamoDbClient` local endpoint, per-test tables).

## Files
- `binding.txt`, `preflight-sha256.txt`, `postrun-sha256.txt`, `sha256-delta.txt`
- `mvn-test-p1b6d-20261120T183441Z.stdout.log`, `...stderr.log` (0 bytes), `...exit` (`0`)
- `surefire-reports/` (both class reports)

## Scope / non-claims
- Test-only. Compilation and execution of the two existing classes; no new features, no other classes run, no regression suite.
- The `DownstreamReply` is an internal non-cryptographic seam. Green is **not** a C4 sender/relay/role/key/signature or P6 ISO-structure verification claim, not a public-endpoint claim, and not a runtime admission claim.
- No Git writes, no publication, no cloud/credentials, no deletion, no spec edits, no capacity defaults. O8/`CriticalCapacity(5, 8)` remains an explicit test example (PROPOSED).
- Runtime remains inactive; G6 stays open.
- `saved partials` state from the recovery receipt is confirmed present; `evidence/p1b6d` was absent and is now created by this run only.

## Exact next slice (suggested, not started)
1. Run the third saved class `aws.DynamoLateEvidenceTransitionIntegrationTest` (510 lines, present) as the immediate next slice.
2. Then the admission-adapter class `aws.DynamoLateEvidenceAdmissionGatewayIntegrationTest` (its `p1b6c-edge` surefire report exists; re-run to confirm on this HEAD).
3. Only after those: decide with the owner whether the C4/P6 upstream verification gap and `AwsRuntime` activation (G6) are in or out of this work item.
