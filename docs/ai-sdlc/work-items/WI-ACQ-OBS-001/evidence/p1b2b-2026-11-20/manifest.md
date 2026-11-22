# P1b2b evidence manifest — 2026-11-20

Slice: atomic INITIAL CLAIM adapter seam + ambiguous-commit recovery + no-duplicate-reservation.
Status: **safe, tested adapter work; production wiring DISABLED** because the
`journalVersion`/internal `version` relation is an unresolved contract conflict
(`version-conflict.md`).

## Binding

| Field | Observed |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `cfc456f4f469d9558a78fda8ff9b770b00817315` (no commit this turn) |
| Origin | `https://github.com/pazfernando/acquiring-simulator.git` |
| Model | provider `nan`, model `deepseek-v4-flash`; no override |
| Java / Maven | JDK 25.0.5, Maven 3.10.1 (offline `-o`) |

## Commands, exits and results

| # | Command | Exit | Result | Log |
| --- | --- | --- | --- | --- |
| 1 | `mvn -o -q -DskipTests compile` | 0 | `BUILD SUCCESS` | `compile.log` |
| 2 | `mvn -o -Dtest='com.acquiring.simulator.aws.DynamoAtomicClaimRecoveryIntegrationTest' test` | 0 | `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0` | `claim-recovery-test.log` |
| 3 | targeted suite (`domain.event.*`, `DynamoEventStore`, `DynamoCriticalClaim`, new recovery test, `AuthorizationCore`) | 0 | `Tests run: 96, Failures: 0, Errors: 0, Skipped: 0` | `targeted-test.log` |
| 4 | `mvn -o test` | 0 | `Tests run: 136, Failures: 0, Errors: 0, Skipped: 0` | `full-test.log` |

136 = 132 pre-existing P1b2a total + 4 new. No test was skipped.

## Code and specification hashes (SHA-256)

| File | SHA-256 |
| --- | --- |
| `src/main/java/com/acquiring/simulator/adapter/aws/AtomicClaimGateway.java` (new) | `0a2b9a1327fc37b58792693604c30208bfb32ead3672c650cb03a2e588e88bbf` |
| `src/test/java/com/acquiring/simulator/aws/DynamoAtomicClaimRecoveryIntegrationTest.java` (new) | `255f99ed5c4f074e5f2f1e2864295ba7178018bb47437f45bd1ae985825c516e` |
| `src/main/java/com/acquiring/simulator/adapter/aws/DynamoAuthorizationRepository.java` (unchanged this turn) | `8fed20c5e4048263206e4337e123845307b5113b0a9982a4f4e98acd12fc3542` |
| `evidence/p1b2b-2026-11-20/version-conflict.md` | `aa9a838041e7dc11a21b832ba9c894400a701dd3949e42f409424253ca87a4f9` |
| `observation-contract.md` (approved, untouched) | `ca53e384a46d24b3c1119b3e6ba804a9a9d4caac049a6b0c55a40c72842a205a` |
| `channel-contract.md` (approved, untouched) | `495e1172e76ab9ba81f8a8026e7efec25baa6e4fff0d754c0a4c92bd38ea3bca` |
| `vectors/async-projection-vectors.json` (approved, untouched) | `7dc800557ae99b0f67f1249dd4138774612a847c432a0d2c73d48c0350188bdd` |
| `vectors/response-vectors.json` (approved, untouched) | `38acdad4c10259e9194f4c0f2544e4337c8b3d236583fe784dd446dd8b169ca4` |

## Wiring state (verified by search)

`grep -rn "AtomicClaimGateway" src/main/java` returns only its own definition. `AuthorizationService`
and `AwsRuntime` do not reference it; `repository.claim()` is unchanged and remains the only
production claim path. The new gateway is **not runtime-wired**.

## Scope guardrails observed

- One `TransactWriteItems` per claim attempt through `DynamoEventStore.append(submission, composer)`.
- No `claim()`-then-append path.
- Capacity is mandatory/explicit in `AtomicClaimGateway.forClaim`; no production default.
- Ambiguous outcome → strongly consistent dedupe lookup only; no resend, no dispatch.
- No `POSSIBLE_SEND`, result, quarantine or retention work added.
- No approved fixture/spec mutation, no Git write, no cloud call, no credential flow.
