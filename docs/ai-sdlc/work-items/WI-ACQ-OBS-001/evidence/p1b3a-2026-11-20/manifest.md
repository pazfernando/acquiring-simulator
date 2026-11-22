# Evidence manifest — P1b3a (2026-11-20, finalization addendum)

Binding verified this turn: repository `/home/pazfernando/projects/acquiring-simulator`, branch
`feat/wi-acq-auth-001`, HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`. Working tree dirty
(application edits unstaged). Configured model: provider `nan`, `deepseek-v4-flash`; no override.
This turn performed **no** new test run, no source edit, no research, no broad inventory and no Git
write/publication/cloud/apply/credential/deletion. Existing logs are preserved byte-unchanged.

Purpose: resolve which saved run corresponds to the **current** source, without re-running the suite
just to produce a report.

## Chronology actually recorded (file mtimes, local time)

| mtime | File | Content |
| --- | --- | --- |
| 11:42:18 | `binding.txt`, `model.txt` | binding/model snapshot |
| 11:42:30 | `toolchain.txt` | JDK 25.0.5, Maven 3.10.1, Docker 29.1.3 |
| 11:43:45 | `hashes-before.txt` | composer `089d76ca…`, test `075eccc8…` |
| 11:43:50 | `compile.log` | `exit=0` (identifier only) |
| 11:44:03 | `send-intent-test.log` | `DynamoPossibleSendIntegrationTest` 7/7, BUILD SUCCESS, exit 0 |
| 11:44:32 | `targeted-regression.log` | 35/35 across 5 claim/event suites, BUILD SUCCESS, exit 0 |
| 11:45:11 | `full-test.log` | **154 tests, 0 F/E/S, BUILD SUCCESS, exit=0** (finished 11:45:11) |
| 11:45:18 | `hashes-after.txt` | composer `089d76ca…`, test `075eccc8…` |
| 11:45:18 | `hashes-diff.txt` | empty → before == after (`089d76ca` both sides) |
| 11:45:29 | *working-tree composer mtime* | `PossibleSendCriticalComposer.java` edited → `b9a2579f…` |
| 11:45:36 | `hashes-before-final.txt` | composer `b9a2579f…`, test `075eccc8…` |
| 11:45:42 | `compile-final.log` | `exit=0` (identifier only) |
| 11:45:54 | `send-intent-test-final.log` | `DynamoPossibleSendIntegrationTest` 7/7, BUILD SUCCESS, exit 0 |
| 11:46:32 | `full-test-final.log` | **INCOMPLETE**: stops at `AuthorizationCoreIntegrationTest` Spring startup; no `Results:` line, no `BUILD`, no exit marker |

## Correspondence — what matches what

There were **two composer versions**, not one attempt with an inconsistent hash:

- **Attempt A — composer `089d76ca…`.** `hashes-before` (11:43:45) and `hashes-after` (11:45:18)
  both record this hash, and `hashes-diff.txt` is empty. They bracket `compile.log` (11:43:50),
  `send-intent-test.log` (11:44:03), `targeted-regression.log` (11:44:32) and `full-test.log`
  (started ≈11:44:38, finished 11:45:11). Therefore the **complete 154-test suite belongs to
  `089d76ca…`**, not to the current source.
- **Attempt B — composer `b9a2579f…` (current source).** `hashes-before-final` (11:45:36) records
  this hash; `compile-final.log` exit 0 and `send-intent-test-final.log` 7/7 (finished 11:45:54)
  postdate it. `full-test-final.log` was **started but never completed**.

**`hashes-after.txt` must not be read as the final-attempt hash.** It predates the 11:45:29 composer
edit and describes Attempt A (`089d76ca`). The final attempt's "before" hash is
`hashes-before-final.txt` (`b9a2579f`).

## Current source snapshot (`hashes-current.txt`)

Recomputed this turn over every `src/main/java` and `src/test/java` file. Key values:

| File | Current SHA-256 |
| --- | --- |
| `adapter/aws/PossibleSendCriticalComposer.java` | `b9a2579f7fa8fa462cf0f327cf25cc18d4c608eb1bf205f43e8f68e079afeca2` |
| `test/.../DynamoPossibleSendIntegrationTest.java` | `075eccc8d0483547e65a94e915cc7a35db0d2c8cc9c8997b5a6364d6ae4fa714` |

The composer matches `hashes-before-final`; the test file hash is identical in all three hash files
and in the current tree (`075eccc8`), so the test class itself did not change across Attempt A →
Attempt B.

## Honest verification status of the current tree

| Claim | Status | Basis |
| --- | --- | --- |
| Compiles (`mvn -o -q -DskipTests compile`) | **VERIFIED for `b9a2579f`** | `compile-final.log` exit 0 (11:45:42). The log records only the exit identifier; it does not embed the source hash. |
| `DynamoPossibleSendIntegrationTest` 7/7 for `b9a2579f` | **VERIFIED (targeted)** | `send-intent-test-final.log`, finished 11:45:54, exit 0. Correspondence is chronological (hash snapshot at 11:45:36 precedes the run) and the log reports `Nothing to compile — all classes are up to date` after `compile-final`. |
| Full suite for `b9a2579f` | **UNVERIFIED / INCOMPLETE** | `full-test-final.log` aborts mid-run; it is not evidence of pass or fail. Do not claim "full suite passed" for the current source. |
| Full suite 154/154 (**for `089d76ca`**) | **Complete earlier snapshot only** | `full-test.log` (11:45:11) sits between two `089d76ca` hash snapshots. Usable only as evidence for that earlier source, not as current-source evidence. |
| Targeted regression 35/35 (**for `089d76ca`**) | **Complete earlier snapshot only** | `targeted-regression.log` (11:44:32) inside the `089d76ca` bracket. |
| Difference between `089d76ca` and `b9a2579f` | **Not reconstructible** | The pre-edit composer content was not saved (`hashes-after.txt` records only the digest). The behavioral delta between the two attempts cannot be diffed from evidence. |
| DynamoDB service behavior (capacity, streams, TTL, IAM, concurrency) | **UNVERIFIED** | DynamoDB Local emulator only. |
| Runtime wiring / activation | **NOT ACTIVE** | Composer is not referenced by `AuthorizationService` or `AwsRuntime`; no production bean constructs it. |

No hash is embedded in any saved log. Correspondence above rests on recorded hashes around the runs
plus chronology, **not** on immutable build provenance. A stricter guarantee would require an
authorized `mvn -o test` on the frozen tree; that was deliberately not done this turn.

## New `PossibleSendCriticalComposer` behavior (current `b9a2579f`)

Single `TransactWriteItems` composes, all-or-nothing:

- **Business transition** (conditioned `Put`): requires a claim committed at journal version 1 /
  `CLAIMED_NOT_SENT`, undispatched, `DecisionSource.NONE`, no outcome; commits journal version 2 /
  `POSSIBLE_SEND` with the dispatch correlation and dispatch time. Guarded by the recorded CAS base
  (`version = 0`) and `committedJournalVersion = 1`; a legacy row without committed-version evidence,
  a missing claim or a concurrent write aborts the whole transaction. The CAS counter advances to 1,
  and the **public** committed version advances explicitly to 2 — never derived from the CAS counter.
- **Immutable critical event**: stable identity `POSSIBLE_SEND-<authorizationId>`, producer
  sequence 2, event version `"1"`, `journalVersion` 2 and `stateAfter = POSSIBLE_SEND`.
- **Event HEAD + dedupe metadata**: in the same transaction; one sequence advance, replay yields
  `NEW`→second attempt dedupes rather than duplicating.
- **Slot accounting (opt-in)**: `withoutSlotAccounting(...)` writes no capacity; only
  `withSlotAccounting(..., CriticalCapacity)` adds the reservation. The update admits the first write
  only when the explicit budget is positive, and otherwise requires `usedSlots < slotBudget`
  (`attribute_not_exists(usedSlots) AND :budget > 0 OR usedSlots < :budget`). No implicit capacity:
  O8 numbers remain **PROPOSED**.

Limits and wiring: records that a send is now *possible* only. It does not invoke downstream, does
not return a payment result, does not recover an ambiguous commit and never authorizes a resend.
Ambiguous-commit resolution is a separate strongly-consistent lookup of the stable event identity;
absence is not proof that nothing was sent. The transition is fixed by the type, not caller-selected.
The class is **not production-wired**: `AuthorizationService`/`AwsRuntime` do not reference it; result
coupling and service activation remain later slices. G6 (full atomic durability) is **incomplete**.

### Next smallest slice

Add a service-level `POSSIBLE_SEND` dispatch-intent branch (still not `AwsRuntime`-activated) that
consumes this composer, plus the pending result-coupling/ambiguous-commit resolver — each as its own
scoped change, with `mvn -o test` on a frozen tree when authorized. Do not combine it with runtime
activation.

## Non-claims

This manifest is documentation and evidence indexing only. It is not QA, Security review, acceptance
or a completion declaration. Admission of new input does not imply completion. `full-test-final.log`
and `full-test.log` are preserved exactly as saved; no rerun was performed.
