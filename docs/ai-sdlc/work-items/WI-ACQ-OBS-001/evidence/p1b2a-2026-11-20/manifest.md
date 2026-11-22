# P1b2a evidence — atomic initial claim composition

Date: 2026-11-20 (America/Bogota)
Branch: feat/wi-acq-auth-001
HEAD: cfc456f4f469d9558a78fda8ff9b770b00817315 (unchanged, no commit)
Model: provider nan, model deepseek-v4-flash; no override

## Commands and actual exits

| Command | Exit | Log | Summary |
| --- | --- | --- | --- |
| `mvn -o -DskipTests compile` | 0 | `compile.log` | BUILD SUCCESS |
| `mvn -o -Dtest=com.acquiring.simulator.aws.DynamoCriticalClaimIntegrationTest test` | 0 | `claim-test.log` | Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 |
| targeted suite (domain.event.*, DynamoEventStore, DynamoCriticalClaim, AwsAuthorization, AuthorizationCore) | 0 | `targeted-test.log` | Tests run: 102, Failures: 0, Errors: 0, Skipped: 0 |
| `mvn -o test` (full) | 0 | `full-test.log` | Tests run: 132, Failures: 0, Errors: 0, Skipped: 0 |

Exits were observed from the shell after each command; the logs contain the full stdout/stderr.

## Artifact hashes (SHA-256)

d9fd51876c88ad9afb855c62f4e0227e855e0b124526d178d0d6d20a36ef9d1c  src/main/java/com/acquiring/simulator/adapter/aws/ClaimCriticalComposer.java
8fed20c5e4048263206e4337e123845307b5113b0a9982a4f4e98acd12fc3542  src/main/java/com/acquiring/simulator/adapter/aws/DynamoAuthorizationRepository.java
bfd81717d447ee38b9cf557762e4c5bc0ebc26b9205f4a1275f39c0c81e19ec5  src/main/java/com/acquiring/simulator/adapter/aws/DynamoEventStore.java
ed70d170b2fea2ac6b8fd5b5e5370f76423edc479895ce0d172430e593b6892c  src/main/java/com/acquiring/simulator/domain/event/EventTableKeys.java
9cb479d98d7db8bf4fb14c09746d68fb484232885b1c583ba3a09306b44297fb  src/test/java/com/acquiring/simulator/aws/DynamoCriticalClaimIntegrationTest.java
e8eff38297e7eef32aad22dfae932ba2757dca6383640a991ae7f40e2c958adf  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/evidence/p1b2a-2026-11-20/compile.log
43f7d9cdf231a743609895e0f077ea5aad8c4ae401d496df97658a8f374572ff  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/evidence/p1b2a-2026-11-20/claim-test.log
f34c8c1f26d071cbe5765c626146fc08437c974c7ee7579cee1a3faa73108a12  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/evidence/p1b2a-2026-11-20/targeted-test.log
b130954a67f9ab828855efe9f58f6c926b9e8c51f0074c065979febe29813c44  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/evidence/p1b2a-2026-11-20/full-test.log
