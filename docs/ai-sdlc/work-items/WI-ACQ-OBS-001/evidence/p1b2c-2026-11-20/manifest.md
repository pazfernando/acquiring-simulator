# Evidence manifest — P1b2c (2026-11-20)

Binding: repository `/home/pazfernando/projects/acquiring-simulator`, branch
`feat/wi-acq-auth-001`, HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`. No Git write, publication,
cloud call, apply or credential flow this turn. Configured model: provider `nan`,
`deepseek-v4-flash`; no override.

## Captured commands (logs copied here before analysis)

| File | Command | Exit | Result |
| --- | --- | --- | --- |
| `compile.log` | `mvn -o -q -DskipTests compile` | 0 | empty output (success) |
| `targeted-test.log` | `mvn -o -Dtest='...CommittedVersionEvidenceTest,...DynamoCriticalClaimIntegrationTest,...DynamoAtomicClaimRecoveryIntegrationTest,...DynamoEventStoreIntegrationTest' test` | 0 | 25 tests, 0 failures/errors/skips |
| `full-test.log` | `mvn -o test` | 0 | 141 tests, 0 failures/errors/skips |

## Changed/new source hashes (working tree, recomputable)

See `hashes.txt`. Summary of the mapping surface:

| File | SHA-256 |
| --- | --- |
| `domain/event/CommittedVersionEvidence.java` (new) | `a9944104…4f2dfe5` |
| `domain/event/StoredEvent.java` | `20acc09c…9a483e` |
| `adapter/aws/DynamoAuthorizationRepository.java` | `2ea39e12…8800004` |
| `adapter/aws/ClaimCriticalComposer.java` | `16a6d7a9…0e20af9f` |
| `adapter/aws/DynamoEventStore.java` | `ae511d7a…9c7bd43` |
| `adapter/aws/AtomicClaimGateway.java` | `ae338cc0…a66411` |
| `test/.../CommittedVersionEvidenceTest.java` (new) | `c270391f…e55deade` |
| `test/.../DynamoCriticalClaimIntegrationTest.java` | `bba290e0…104bd67b` |
| `test/.../DynamoAtomicClaimRecoveryIntegrationTest.java` | `e80613df…ff39c1b` |
| `test/.../DynamoEventStoreIntegrationTest.java` | `efee0bb4…50ff67` |

The full values are in `hashes.txt` as produced by `sha256sum` in this working tree.

## Scope statements (non-claims)

- Local implementation and DynamoDB Local tests only. Not QA, Security review or acceptance.
- DynamoDB service behaviour (throttling, Streams, IAM, encryption, TTL) remains UNVERIFIED.
- `AuthorizationService` and `AwsRuntime` are unchanged; atomic claim remains disabled in runtime.
- No existing record migrated, no backfill, no approved fixture or specification edited.
