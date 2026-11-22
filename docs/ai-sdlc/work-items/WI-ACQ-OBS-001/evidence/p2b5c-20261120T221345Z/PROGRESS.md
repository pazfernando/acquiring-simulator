# Progress — P2b5c

| Slice | Status | Actual result | Evidence |
| --- | --- | --- | --- |
| P2b5c durable signed-evidence conflict/quarantine metadata | **TARGETED TEST GREEN** | 15 tests, 0 failures, 0 errors, 0 skipped, exit 0, BUILD SUCCESS | this directory |
| P2b5b signed receipt gateway | preserved | 10/10 green (unchanged assertions) | `../p2b5b-20261120T220425Z/` |
| P2b5a atomic normal receipt | preserved | 4/4 green | `../../../WI-ACQ-AUTH-001/evidence/p2b5a-recovery-20261120T215327Z/` |

## What changed

- New `domain/event/SignedEvidenceConflict` record: the two approved conflict classes, identity
  triple, candidate/established canonical hashes, established reference and a safe reason.
- `DynamoAuthorizationRepository`: `recordEvidenceConflict` (conditional first-witness put on a
  distinct `EVIDQ#…#QUARANTINE` row) and `readEvidenceConflict`.
- `SignedEvidenceReceiptGateway`: a `ConflictRecorder` seam, used only on the two receipt-level
  conflict paths, after authorization/C4/P6. A persistence failure is swallowed; status and
  established facts are decided before the write.
- `EvidenceReceiptCriticalComposer`: documentation now points quarantine at the gateway; behavior
  unchanged.
- Test class: fixture `chain(...)` helper extracted without changing the existing ten cases, and
  five conflict cases added.

## What the green run proves

The reused-identity and contradictory-outcome conflicts are flagged durably with sanitized
metadata only, the first conflicting witness is stable across repeats, unauthorized material
writes nothing, and a marker persistence failure neither deletes nor changes the established
outcome. No business version, event sequence, critical slot, receipt, resend or reversal moves.

## Still open

- Not production-wired; no `AuthorizationService`/`AwsRuntime` reference, no HTTP route.
- Late-evidence contradiction remains refuse-and-preserve in `LateEvidenceAdmissionGateway`; its
  durable quarantine is a separate slice.
- The event-store integrity marker (already durable) is unchanged and not duplicated.
- O8 capacity numbers remain PROPOSED; test capacity is test-only.
- P1 G6 Security review remains open.
- Full suite / broader regression was not run in this slice.

## Next

Late SIGNED atomic admission: bind the already-verified late-delivery envelope/instruction seam to
the same atomic receipt/version/event admission, still local, with no public endpoint or runtime.
