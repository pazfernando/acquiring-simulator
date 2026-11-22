# P1b1 evidence — 2026-11-20

Binding: branch `feat/wi-acq-auth-001`, HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`, origin `https://github.com/pazfernando/acquiring-simulator.git`.

Model: provider `nan`, model `deepseek-v4-flash`; no override.

Commands (JDK 25.0.5 Temurin, Maven 3.10.1, offline `-o`):
- `mvn -o -DskipTests compile` -> exit 0 (`compile.log`)
- `mvn -o -Dtest='com.acquiring.simulator.domain.event.*Test' test` -> exit 0, `Tests run: 52, Failures: 0, Errors: 0, Skipped: 0` (`domain-test.log`)
- `mvn -o -Dtest='com.acquiring.simulator.aws.DynamoEventStoreIntegrationTest' test` -> exit 0, `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0` (`dynamo-event-test.log`); container `amazon/dynamodb-local:2.6.1` local image
- `mvn -o test` -> exit 0, `Tests run: 126, Failures: 0, Errors: 0, Skipped: 0` (`full-test.log`) — 106 prior + 20 new

Pinned specs re-verified before writes (SHA-256, all match owner-decisions.md):
requirements `49c5d412…e2b459df`, acceptance-scenarios `15868984…aa7733`, iso8583-profile `de02c533…0e1fe481`, channel-contract `495e1172…38ea3bca`, observation-contract `ca53e384…842a205a`, decision-proposal `becbabcd…fe219166`, activity-flow.md `7f09731a…fb3c67bd`, activity-flow.mmd `e2fbb1aa…388393ce`, domain-review `73adba20…ee0ca2`, vectors/review-manifest.json `dac6fc14…eb5082dc`.

Worktree: 34 pre-existing modified/untracked entries preserved; no revert, stage, commit, push, PR, merge, deploy or cloud call. No SAM/Terraform/IaC change in this slice.

## SHA-256 of delivered code

```
55988532ff2bdec49bf40ca41688bbbc9f2638381cddee1d46bf1f2b60cd5006  src/main/java/com/acquiring/simulator/domain/event/CriticalCapacity.java
077e1aa43334e736445ff7e7681ab95e848c380fe7e92ddfaa3bb70e0aece824  src/main/java/com/acquiring/simulator/domain/event/EventAppendResult.java
c3cbc0a5f4383af601fdbebd90a7f25cfdfeb2cf92a605cf6f7e74b7e13a2f0d  src/main/java/com/acquiring/simulator/domain/event/EventContext.java
1085b497a0fc6215dc13e119fbd707adb0fe4d4f3fddde59cd86eabcb6eb1340  src/main/java/com/acquiring/simulator/domain/event/EventDedupeKey.java
9a0d38ad72f0ddf7cbd96d8c0b67f979cdcd88e61036273a34fa1dc859e26bf7  src/main/java/com/acquiring/simulator/domain/event/EventHead.java
1e69b48515f02591e062d30e5db25211ec15ac2c1d53dd4c6a384ea96640b396  src/main/java/com/acquiring/simulator/domain/event/EventManifest.java
5a3673bbd3d72d1daafa70965f5401d743d789c16ea3990f2fab3158c97036e1  src/main/java/com/acquiring/simulator/domain/event/EventSubmission.java
4ee3fef9d2e50c859e0048e432c61bf9fe47ae213cdb649c2fae76cf8d02f480  src/main/java/com/acquiring/simulator/domain/event/EventTableKeys.java
2eb4dc02b8e3dfc91f81c02cf8114855140c9900797e61afdfdfffe38ad75c75  src/main/java/com/acquiring/simulator/domain/event/EventViolation.java
6664783f91a6f6bd005cfebc41753786a926e21b6ce789e5ddf74841270d3410  src/main/java/com/acquiring/simulator/domain/event/OperationAManifest.java
61c88ba7b2cb5246a74c569a168a8a9c10fa407b4f027493321848cd821ef36b  src/main/java/com/acquiring/simulator/domain/event/StoredEvent.java
f67b90d5b966ec546decafc9fcb05b9d51216c9cd2a41312df4013938abbe4f4  src/main/java/com/acquiring/simulator/domain/port/EventStore.java
80f7aa21df9f99e5eed779969c9dbb8b8e5a4ed9d2c052b99c58b4a22f300a80  src/main/java/com/acquiring/simulator/adapter/aws/CriticalWriteComposer.java
caab3dbb8f217df20a7c7f49be2ea63b4bc9da80ac2bef7b5381dcb831a0ba40  src/main/java/com/acquiring/simulator/adapter/aws/DynamoEventStore.java
249351cc72b7c7c9fcd508ce6c4068459598bdf11a9526adf40386f771089699  src/test/java/com/acquiring/simulator/domain/event/CanonicalEventCodecTest.java
942fcf7203f3e06bd677a988e291905cb0e408e2ec3b8ffa8480bfc7702a229a  src/test/java/com/acquiring/simulator/domain/event/EventDeduplicationTest.java
b2db44d2c4ecdcd3099aa9f03f144f2c88173ed5815e82653252764adb7e7d97  src/test/java/com/acquiring/simulator/domain/event/EventSourceWrapperTest.java
dc1a96b26c3aaede29ae9535f31265042fa9e50c139cdd2511da4de97f400b12  src/test/java/com/acquiring/simulator/domain/event/EventSubmissionTest.java
30c1f8bb6b0d18ab984c335d8f45a13e199fd5a2ad5113110057466a479c07dc  src/test/java/com/acquiring/simulator/domain/event/EventTableKeysTest.java
aebc5e9040138651e22ea66fb722f5cec514dc97dc1a02cd0b6cde9518c7b3f9  src/test/java/com/acquiring/simulator/domain/event/EventValidatorTest.java
00e8ea8cd750d908feb0fa91890d005f58fa34fa53e9d9b929a464893fe3f6cd  src/test/java/com/acquiring/simulator/domain/event/OperationAManifestTest.java
cc94a6f5f56ed7b1884fbfec7ab6c6dcd99739fc7e16fcf293accea2219143b7  src/test/java/com/acquiring/simulator/aws/DynamoEventStoreIntegrationTest.java
```

## Captured results

- DynamoDB Local: first append allocates `E#000000000001`, HEAD `lastSequence=1/firstRetainedSequence=1/expiryRevision=0`, payload stored once; exact replay keeps sequence 1 and original `BOOT-001`; changed payload is `CONTENT_CONFLICT` with stored bytes unchanged; 8 concurrent appends produce contiguous 1..8 with HEAD 8; EV4 context mismatch (`ACQ-B` payload in `ACQ-A` partition) throws `CONTEXT_MISMATCH` before any dedupe/HEAD/event write; best-effort journal metadata rejected; composed business write commits atomically; failing composed condition leaves no event, HEAD or dedupe row; business composition rejected for best-effort events.
- Earlier first run failed on `IdempotentParameterMismatchException` because the commit token omitted the table namespace; fixed by including the table in the token material. The failing run is not preserved as evidence of a defect; the final run above is the recorded result.

## Limitations

- Local emulator behavior only. DynamoDB service throttling, Streams, IAM, encryption, TTL and cross-region behavior remain UNVERIFIED.
- No business transaction is wired: `CriticalWriteComposer.NONE` is the only default; claim/POSSIBLE_SEND/result coupling, slot accounting and ambiguous-commit resolution on the send path are P1b2.
- Capacity numbers are test-only examples in test code; production capacity remains an owner decision and no default is active.
- CONTENT_CONFLICT is returned without quarantine marking (store metadata write is P1b2).
- Not independent QA, Security review or acceptance.

## Recovery re-execution — 2026-11-20 11:08 America/Bogota

Recovery turn re-verified that the delivered files are byte-identical to the hashes above (all 21 manifest hashes recomputed and matched) and re-executed the four commands. Logs in this directory were overwritten by this re-run and now carry an explicit `EXIT=` line each; stdout/stderr were captured before any analysis.

| Command | Exit | Result |
| --- | --- | --- |
| `mvn -o -DskipTests compile` | 0 | `BUILD SUCCESS` |
| `mvn -o -Dtest='com.acquiring.simulator.domain.event.*Test' test` | 0 | `Tests run: 52, Failures: 0, Errors: 0, Skipped: 0` |
| `mvn -o -Dtest='com.acquiring.simulator.aws.DynamoEventStoreIntegrationTest' test` | 0 | `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0` |
| `mvn -o test` | 0 | `Tests run: 126, Failures: 0, Errors: 0, Skipped: 0` |

Log SHA-256 (recovery re-run):

```
c62ebbd8e01b4c9e354c49f000a9136aab51d7a6066262a20351b244ce956613  compile.log
4f4a4b07b21c2038a8af541932b7b362ff98f36e565a32623a13140dda8e9da8  domain-test.log
6baa9447484bd2798e474031c5e25178b7885780df150f67d8ac2cafa2f40f89  dynamo-event-test.log
0098b8c567994f18c8d338bf7a4c90408fff59f54235360f0f3f07472f78991b  full-test.log
```

No source file was edited in the recovery turn; no failure required a fix.
