# Evidence manifest — P1b2d (2026-11-20)

Binding verified this turn: repository `/home/pazfernando/projects/acquiring-simulator`, branch
`feat/wi-acq-auth-001`, HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`. Working tree dirty
(application edits unstaged). Configured model: provider `nan`, `deepseek-v4-flash`; no override.
No Git write, test rerun, publication, cloud call or credential flow this turn.

## Saved runs (already present; not rerun)

| File | mtime (local) | Exit | Observed result |
| --- | --- | --- | --- |
| `compile.log` | 11:29 | 0 | `EXIT=0` (identifier only, 7 bytes) |
| `service-claim-test.log` | 11:30 | 0 | `BUILD SUCCESS` (service claim slice) |
| `full-test.log` | 11:31 | — | **ABORTED mid-run** (ends inside `DynamoCriticalClaimIntegrationTest`; no BUILD line). Preserved, not deleted, not evidence of failure or success. |
| `wiring-attempt2.log` | 11:35 | 0 | `AtomicClaimServiceWiringIntegrationTest`: 6 tests, 0 F/E/S; `BUILD SUCCESS` |
| `claim-tests-attempt2.log` | 11:35 | 0 | `BUILD SUCCESS` (targeted claim tests) |
| `compile-attempt2.log` | 11:38 | 0 | `mvn -o -q test-compile`, quiet, no compiler output; header records branch/HEAD/toolchain |
| `full-test-attempt2.log` | 11:37 | 0 | **147 tests, 0 failures, 0 errors, 0 skipped**; `BUILD SUCCESS`; finished 11:37:45 |

XML reports: `TEST-attempt2-AtomicClaimServiceWiringIntegrationTest.xml` (6),
`TEST-attempt2-DynamoCriticalClaimIntegrationTest.xml`, `TEST-attempt2-DynamoAtomicClaimRecoveryIntegrationTest.xml`.

## Current-code correspondence (explicit)

- Last application-source write: `DynamoAuthorizationRepository.java` 11:30:00,
  `AuthorizationService.java` 11:31:12. `claim-tests-attempt2` (11:35), `wiring-attempt2` (11:35),
  `compile-attempt2` (11:38) and `full-test-attempt2` (11:37) all postdate the last source write.
- `find src/main -newermt "2026-11-20 11:38:15"` → 0 files. No source changed after the saved run.
- No hash is embedded in the logs. Correspondence is therefore supported by chronology and by the
  absence of later source writes, **not** proven by immutable build provenance. If a stricter
  guarantee is required later, re-run `mvn -o test` on the frozen tree.
- The saved run is recorded as **147 passed**, not as a re-verified execution in this turn.

## Hashes (recomputable, working tree as of this manifest)

Full values in `hashes.txt`. Key surface:

| File | SHA-256 |
| --- | --- |
| `domain/port/ClaimGateway.java` (new) | `3151a44f…06bcdb` |
| `adapter/aws/DynamoClaimGateway.java` (new) | `c223f15a…d50ff5` |
| `adapter/aws/AtomicClaimGateway.java` | `ae338cc0…669611` (unchanged from P1b2c) |
| `adapter/aws/ClaimCriticalComposer.java` | `16a6d7a9…0e20af9f` (unchanged) |
| `adapter/aws/DynamoEventStore.java` | `ae511d7a…9c7bd43` (unchanged) |
| `adapter/aws/DynamoAuthorizationRepository.java` | `ce22adff…df0376` |
| `domain/AuthorizationService.java` | `5a91c578…3a058b3` |
| `adapter/aws/AwsRuntime.java` | `99c32249…0f36c7b` (unchanged) |
| `test/.../AtomicClaimServiceWiringIntegrationTest.java` (new) | `0b2a4f0d…71d3fa` |

## Precise runtime / service wiring status

- `AuthorizationService` now has an **optional** `ClaimGateway` (5-arg constructor); the existing
  4-arg constructor delegates with `null`, so Spring and the local harness keep the legacy
  `repository.claim()` path.
- When a gateway **is** injected, `authorizeAtomic` performs exactly one `gateway.claim(...)` and
  **never** falls back to `repository.claim()`, never retries and never resends.
- `AwsRuntime.processor()` still constructs the 4-arg service (no gateway). The atomic path is
  therefore **inactive in `AwsRuntime`**. `DynamoClaimGateway` exists and implements the port but is
  constructed only by tests; no production bean/factory references it.
- Remaining atomic transitions (claim + `POSSIBLE_SEND`/result coupling + later journal-version
  advance) are **not** implemented. Runtime activation waits on them.

## Behavioural dispositions (from the saved 6-test wiring suite)

- `UNKNOWN` → `NOT_CONFIRMED`; **no dispatch**, no legacy claim, no write (verified).
- `CONTENT_CONFLICT` → `PAYMENT_DATA_CONFLICT` rejection; no dispatch, no overwrite (verified).
- `NOT_COMMITTED` without a competing winner → no write, no reclaim, no dispatch (verified).
- `CLAIMED` without persisted committed evidence → `NOT_CONFIRMED`, no dispatch; the version is
  never synthesized from the CAS counter (verified).

## Metadata-loss fix disposition (explicit)

`committedJournalVersion` is additive and is written beside the internal CAS `version`; it is never
derived from it. Legacy rows are **not backfilled**. A missing committed version is reported as
explicit absence (`(0, null)`, `committedVersionRecorded() == false`), never fabricated. The
repository replacement path **copies forward** the recorded value so a rewrite cannot silently drop
it; advancing it is deliberately out of scope until the next business transition commits atomically
with it. No `UNKNOWN` outcome is converted into "not sent".

## Non-claims

G6 is not complete. Local implementation plus DynamoDB Local tests only — not QA, Security review or
acceptance. DynamoDB service behaviour (throttling, Streams, IAM, encryption, TTL) remains UNVERIFIED.
`full-test.log` remains an interrupted log and is preserved. No commit, push, PR, merge, deployment,
cloud call or credential flow occurred.
