# P1b7b evidence manifest — first-authorized collectedAt persistence/replay metadata

Work item: WI-ACQ-OBS-001
Phase: P1b7b (one small additive source-wrapper metadata slice)
Run (UTC): 2026-11-20T18:47:18Z (directory stamp); commands executed 18:47–18:50 local clock
Network: offline Maven (`-o`); Testcontainers used the pre-cached `amazon/dynamodb-local:2.6.1` image.

## Binding

- Repository: `/home/pazfernando/projects/acquiring-simulator`
- Branch: `feat/wi-acq-auth-001`
- HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged; no commit)
- Origin: `https://github.com/pazfernando/acquiring-simulator.git`
- Model: provider `nan`, model `deepseek-v4-flash` (configured default, no override)
- JDK: `/home/pazfernando/.local/opt/jdk-25` (explicit `JAVA_HOME`)
- Maven: `/home/pazfernando/.local/opt/maven/bin/mvn` (3.10.1)
- Runtime: inactive; no service start, no AWS call, no cloud/apply, no credential flow
- Git writes/publication: none

`BINDING.txt` is the machine-captured binding. `hashes-before.txt` covers **all** files under
`src/main/java`, `src/main/resources`, `src/test` plus `pom.xml` (132 entries).

## Scope and sourced rule

Observation contract O1a/O2a (`observation-contract.md:17,21,23`):

- the source wrapper (not canonical content) carries `collectedAt`;
- for critical events `collectedAt = committedAt`;
- best-effort **first authorized collection is retained across redelivery**;
- the dedupe metadata partition holds payload hash + original context/source key/sequence,
  **no second payload**, retained while business links remain.

O8 (`:77`) anchors the proposed seven-day history on the **original** `collectedAt`, not on a
callback/replay/read time. This slice persists and replays that date; it does not implement expiry,
ranges or the query API (P3).

## Delivered change (additive only)

Three untracked-from-earlier-slice files were edited; no tracked file changed.

| File | SHA-256 after edit |
| --- | --- |
| `src/main/java/com/acquiring/simulator/domain/event/StoredEvent.java` | `420d896335618f4bbee2864f4530970c074fe419135d5981b164e54dfdee4309` |
| `src/main/java/com/acquiring/simulator/adapter/aws/DynamoEventStore.java` | `7fa3a17142fbc79aeacc94efc0bdc165260ad7f2757f67b843b3f71825687c7d` |
| `src/test/java/com/acquiring/simulator/aws/DynamoEventStoreIntegrationTest.java` | `3aa8e3ea7386534e59e3b3b4cb70b5b3d6b1a9fc0da8366dad6d240df3ba26a1` |

1. `StoredEvent` gains one nullable component, `collectedAt`. `null` means the metadata predates the
   field (explicit **unknown**), never a synthesized current time.
2. `DynamoEventStore.dedupePut` writes `collectedAt` with the identity metadata on the first append.
   `find` (strongly consistent) reads it back. No payload is copied and no other attribute changes.
3. Because an exact replay/conflict returns before any write, the first value is never overwritten by
   redelivery. A new store instance reads the same persisted value. Integrity flags, original hash,
   context, sequence and authorization-before-dedupe are untouched.

## Commands, exits and results

| Command (absolute toolchain, `JAVA_HOME` pinned, offline) | Exit | Result |
| --- | --- | --- |
| `mvn -o -q -DskipTests compile` | 0 | empty stdout/stderr (`compile-p1b7b.*`) |
| `mvn -o -Dtest=DynamoEventStoreIntegrationTest test` | 0 | **16 tests, 0 F/E/S**, BUILD SUCCESS |
| `mvn -o -Dtest='DynamoAtomicClaimRecoveryIntegrationTest,DynamoLateEvidenceAdmissionGatewayIntegrationTest' test` | 0 | **11 tests, 0 F/E/S**, BUILD SUCCESS |
| `mvn -o test` | 0 | **269 tests, 0 F/E/S**, BUILD SUCCESS |

No `-DfailIfNoSpecifiedTests=false` was used, so a class-name mismatch would have failed the run.
Every command has a unique `*.stdout.log`, `*.stderr.log` and `*.exit` file. The only non-empty
stderr is the full suite's JDK/Mockito/SQLite agent warnings (`full-test-p1b7b.stderr.log`), not an
error.

The full suite is justified because `StoredEvent` is a shared record read by the claim-recovery,
dispatch-intent, result, network-result and late-evidence gateways; the change is additive but the
constructor is shared. No source file was touched again after the hash set was captured.

## Freeze checks

- `hashes-before.txt` → `hashes-after-edit.txt` diff is exactly the three files above
  (`hashes-edit-diff.txt`).
- `hashes-after-edit.txt` → `hashes-after-run.txt` diff is **empty** (`hashes-run-diff.txt`), so the
  test run did not mutate the source tree.
- `git-status-before.txt` → `git-status-after.txt` diff is **empty**: all three edited files are
  untracked entries from earlier slices, so no tracked path changed. The only added untracked path is
  this evidence directory.
- No `touch`, no forced mtime, no deletion, no move and no duplicate evidence copy.

## Behaviour proven (DynamoDB Local only)

1. Best-effort redelivery with a later delivery time returns `EXACT_REPLAY`, keeps sequence 1 and
   reports the original `2026-11-18T12:00:01Z` from `find`, the event row, the dedupe row and HEAD.
2. Critical event persists `collectedAt == committedAt`; after a replay with a later delivery time the
   strong read still returns the original pair, and only one event row exists.
3. A fresh `DynamoEventStore` instance (clock moved forward two days) replays with the persisted first
   collection time and does not reset HEAD.
4. A dedupe row with `collectedAt` removed (legacy shape) reads as `null` before and after a replay;
   the event row keeps its original value and no "now" is invented or backfilled.

## Limitations / non-claims

- Local DynamoDB Local behaviour only. AWS throttling, Streams, IAM, encryption and TTL timing remain
  UNVERIFIED.
- No expiry worker, range metadata, query API, cursor, Telegram/console or notification work — those
  are P3/P4.
- `AwsRuntime` is inactive; G6 remains open. This slice does not wire the store into the runtime.
- P1b7b is local implementation and tests, not independent QA, Security review or human acceptance.
- No specification, fixture, capacity default or financial rule was edited; no commit, push, PR,
  merge, deploy, cloud/apply call or credential flow occurred.
