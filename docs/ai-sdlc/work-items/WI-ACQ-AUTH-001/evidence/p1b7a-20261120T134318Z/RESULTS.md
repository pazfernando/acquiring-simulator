# p1b7a results — recovery compile + targeted integrity tests

Date (UTC): 2026-11-20
Model: provider `nan`, model `deepseek-v4-flash` (no override)
Binding: `/home/pazfernando/projects/acquiring-simulator` @ `feat/wi-acq-auth-001`
HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315` (matched the expected HEAD; unchanged at end)

## Answer to the recovery question

The three required integrity tests **already existed** before this turn, in
`src/test/java/com/acquiring/simulator/aws/DynamoEventStoreIntegrationTest.java`:

| Requirement | Existing test |
| --- | --- |
| Persisted conflict marker | `changedPayloadForTheSameIdentityIsAConflictWithoutOverwrite` |
| Idempotent replay, no extra sequence/slot | `duplicateConflictRepeatsTheSameMarkerAndConsumesNoSequenceOrSlot` |
| No marker poisoning before auth/context | `unauthorizedContextCannotPoisonTheConflictMarker` |

No test authoring was needed, and nothing was authored. The integrity production changes
(`EventIntegrityConflict`, `StoredEvent.integrityConflict`, `DynamoEventStore` marker write/read)
were already present and were compiled and exercised as-is.

## Commands actually run (absolute toolchain paths, offline)

```
export JAVA_HOME=/home/pazfernando/.local/opt/jdk-25

# forced recompile of the existing integrity sources (mtime touch only, content unchanged)
touch src/main/java/com/acquiring/simulator/domain/event/EventIntegrityConflict.java \
      src/main/java/com/acquiring/simulator/domain/event/StoredEvent.java \
      src/main/java/com/acquiring/simulator/adapter/aws/DynamoEventStore.java \
      src/test/java/com/acquiring/simulator/aws/DynamoEventStoreIntegrationTest.java

/home/pazfernando/.local/opt/maven/bin/mvn -o -DskipTests compile
/home/pazfernando/.local/opt/maven/bin/mvn -o test-compile
/home/pazfernando/.local/opt/maven/bin/mvn -o \
  -Dtest='DynamoEventStoreIntegrationTest#changedPayloadForTheSameIdentityIsAConflictWithoutOverwrite+duplicateConflictRepeatsTheSameMarkerAndConsumesNoSequenceOrSlot+unauthorizedContextCannotPoisonTheConflictMarker' \
  -DfailIfNoSpecifiedTests=false test
```

## Actual results

| Step | Exit | Observed output |
| --- | --- | --- |
| `compile` | `0` | `Compiling 89 source files with javac [debug parameters release 25] to target/classes`; `BUILD SUCCESS` |
| `test-compile` | `0` | `Compiling 37 source files ... to target/test-classes`; `BUILD SUCCESS` |
| targeted `test` | `0` | `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS` |

Tests ran against `amazon/dynamodb-local:2.6.1` via Testcontainers (Docker 29.1.3). This is the
existing test harness only; no application runtime or AWS service was started.

## Freeze / immutability evidence

- `integrity-files-before.sha256` vs `integrity-files-after.sha256`: **byte-identical**.
- `git-status-before.txt` vs `git-status-after.txt`: **identical** (82 entries, 8 modified tracked
  files, 74 untracked; no file added, removed or modified by this turn). The single untracked entry
  `?? docs/ai-sdlc/work-items/WI-ACQ-AUTH-001/evidence/` is this evidence directory itself, created
  in this turn and excluded below for the source-tree comparison: the source tree is identical.
- `git-status-final.txt` (after also writing `RESULTS.md`/`PROGRESS.md`): identical for all
  non-evidence paths.
- HEAD and branch unchanged.
- No raw conflicting payload is copied into evidence; only SHA-256 hashes are recorded.
- No event, HEAD, sequence, slot or business outcome was written by this turn (tests use an
  ephemeral container table only).

## Limitations (not success claims)

- This is a local compile and a targeted local test run. It is not independent QA, Security review
  or human acceptance.
- `tests` exercised DynamoDB Local only: AWS throttling, Streams, IAM, encryption and TTL timing
  remain unverified.
- No full suite, no regression run, no new feature, no specification change, no Git write, no
  publication, no deployment, no cloud/credential use.
- Runtime remains inactive; G6 remains open.

## Exact next command (if further verification is authorized)

```
export JAVA_HOME=/home/pazfernando/.local/opt/jdk-25
/home/pazfernando/.local/opt/maven/bin/mvn -o \
  -Dtest='DynamoEventStoreIntegrationTest' -DfailIfNoSpecifiedTests=false test
```

(Runs the whole existing `DynamoEventStoreIntegrationTest` class, not the repository suite. Requires
explicit authorization; not run in this turn by design.)
