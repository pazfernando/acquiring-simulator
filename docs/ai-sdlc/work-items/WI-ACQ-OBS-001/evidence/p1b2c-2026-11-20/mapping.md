# P1b2c — committed journal version: implementation mapping (2026-11-20)

Status: **IMPLEMENTED — LOCAL ONLY, NOT PRODUCTION-WIRED.**
Nature: **implementation mapping of the approved committed-version semantics**, not a human business
decision and not a specification amendment. No approved document was edited.

## Resolution basis

Coordinator clarification (relayed 2026-11-20): the approved public contract wins over the legacy
internal implementation; the owner does not need to choose private storage fields. The approved
contract defines `journalVersion`/`businessVersion` as the committed journal version (claim = 1) and
`businessVersion` as a positive committed integer; it never names the internal storage/CAS counter.
Therefore the public committed version is stored as its own field and the internal counter is left
unchanged.

## Approved-text check (before activation)

Searched `observation-contract.md`, `channel-contract.md`, `requirements.md`,
`acceptance-scenarios.md`, `decision-proposal.md`, `domain-review.md` for text forbidding a separate
committed-version storage attribute. **No such text exists.** The closest statements are:

- `observation-contract.md` line 17: `journalVersion (critical only)` is part of the source wrapper and
  `stateAfter` is "critical-only journal state at that committed version".
- `observation-contract.md` line 23: "Critical append includes business version/head/evidence in SAME
  DynamoDB TransactWriteItems account/region."

Both require the committed version to travel with the critical transaction; neither prohibits an
additive attribute beside the CAS counter. No cited clause was overridden.

## Implemented mapping (one slice)

| Concern | Implementation |
| --- | --- |
| Public committed version at claim | `ClaimCriticalComposer` writes additive `committedJournalVersion = 1` on `AUTH#<id>/META` |
| Internal CAS counter | `version = 0` unchanged (same put, no migration, no base change) |
| Derivation rule | **Never** `journalVersion = version + 1`; the public value is stored, not computed |
| Wrapper | `ClaimCriticalComposer` requires the composed wrapper's `journalVersion` to equal the persisted committed value |
| Event store read | `DynamoEventStore` persists `journalVersion` on the dedupe metadata row and returns it in `StoredEvent` |
| Read representation | `CommittedVersionEvidence(storageVersion, committedJournalVersion)` from `DynamoAuthorizationRepository.readVersionEvidence(id)` |
| Ambiguous recovery | `AtomicClaimGateway` returns the recovered `StoredEvent`, which now carries the stored committed version and `stateAfter` |
| Missing evidence | `committedJournalVersion == null` + `committedVersionRecorded() == false`; never fabricated as CAS+1 |
| Existing rows | Untouched: no migration, no backfill, no rewrite. The legacy `claim()` path writes no committed version |
| Future transitions | Not in this slice. Requirement recorded: any later business transition must atomically advance `committedJournalVersion`; the CAS counter remains only the lost-update guard |

## Compatibility / missing-field behaviour

A row written by the legacy `claim()` path has `version = 0` and **no** `committedJournalVersion`.
`readVersionEvidence` returns `(0, null)` and `committedVersionRecorded() == false`. This is explicit
absence of evidence, not a claim that the version is 0 and not a fabricated 1.

## Actual wiring status (verified by source reference scan)

`grep -rln "AtomicClaimGateway|ClaimCriticalComposer|DynamoEventStore" src/main/java` returns only the
three adapter files. `AuthorizationService` and `AwsRuntime` do not reference them; `repository.claim()`
remains the runtime claim path. The atomic claim gateway stays **disabled in AwsRuntime**.

## Test evidence

| Command | Exit | Result |
| --- | --- | --- |
| `mvn -o -q -DskipTests compile` | 0 | `BUILD SUCCESS` |
| targeted (`CommittedVersionEvidenceTest`, `DynamoCriticalClaimIntegrationTest`, `DynamoAtomicClaimRecoveryIntegrationTest`, `DynamoEventStoreIntegrationTest`) | 0 | 25 tests, 0 failures/errors/skips |
| `mvn -o test` | 0 | 141 tests, 0 failures/errors/skips (136 prior + 5 new) |

Proven: CAS 0 and committed 1 coexist on the same committed claim record and agree with the stored
event row and dedupe metadata; an ambiguous-after-commit recovery reads the stored committed version
and state; a legacy claim row reports missing committed evidence without backfill.

## Not done in this slice (deliberate)

- No `POSSIBLE_SEND` or result-event coupling.
- No service wiring; no change to `AuthorizationService` or `AwsRuntime`.
- No migration/backfill of existing records and no change to legacy `claim()` behaviour.
- No capacity default, no payment semantics, no approved fixture/spec edit.
- No Git write, publication, cloud call or credential flow.
