# P2b5a bounded recovery — actual results

UTC: 2026-11-20T21:53:27Z start, 21:53:53Z test finish
Model: DeepSeek V4 Flash (provider `nan`)
Binding: `/home/pazfernando/projects/acquiring-simulator`, branch `feat/wi-acq-auth-001`,
HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`, default configuration, no override.

## Classification

**Reader type-mismatch defect in the persistence read seam — not a wrong test attribute and not
missing composer metadata.**

- The atomic composer persists the receipt row attribute `journalVersion` as a DynamoDB **number**
  (`EvidenceReceiptCriticalComposer#receiptPut`, `AttributeValue.n(...)`), in the same transaction
  as the business record. The business record separately persists the CAS counter `version` (number)
  and the additive public `committedJournalVersion` (number).
- `DynamoAuthorizationRepository#readEvidenceReceipt` read `journalVersion` through
  `requiredString(...)`, which requires `AttributeValue.s()`. A numeric attribute has `s() == null`,
  so the read threw `IllegalStateException: journal row is missing journalVersion` even though the
  field was present and correct.
- The field **name** was right and the test assertion (`receipt.journalVersion() == 3L`) was right:
  3 is the committed journal version written with the confirmed `APPROVED` state. No alias, no
  `version + 1` derivation, no fabricated version was introduced.

## Fix (one scoped change, production read seam only)

`src/main/java/com/acquiring/simulator/adapter/aws/DynamoAuthorizationRepository.java`

- Added `requiredNumber(item, field)` mirroring `requiredString`: reads `AttributeValue.n()` and is
  explicit when the numeric attribute is absent.
- `readEvidenceReceipt` now calls `requiredNumber(item, "journalVersion")`.
- Nothing else changed: no composer schema change, no test/assertion change, no spec change, no
  capacity default, no alias.

## Before (initial failure, preserved untouched)

Source of truth: `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/evidence/p2b5a-20261120T214745Z`
(copied read-only into `initial-failure-reference/`; original files untouched).

- `targeted-run1`: Tests run 4, Failures 0, **Errors 2**, Skipped 0; exit 1.
- Both errors: `IllegalStateException: journal row is missing journalVersion` at
  `DynamoAuthorizationRepository.requiredString` via `readEvidenceReceipt` (test lines 183, 253).
- The later `targeted-stdout.txt`/`targeted-stderr.txt` were empty with no exit file:
  incomplete run, never green.

## After (this attempt, one targeted run)

Command: `mvn -Dtest=EvidenceReceiptAtomicIntegrationTest test`

- Exit: **0** (`test-exit.txt`)
- Tests run: **4, Failures: 0, Errors: 0, Skipped: 0** — `BUILD SUCCESS`
- Compilation was real, not stale: `Compiling 173 source files` then `Compiling 63 source files`.
- `test-stderr.txt` is empty.
- Test methods that passed: `verifiedApprovedResultCommitsReceiptRecordEventHeadDedupeAndSlotInOneTransaction`,
  `tamperedOrMismatchedEvidenceIsRefusedBeforeAnyWrite`,
  `exactReceiptReplayPreservesReceiptVersionEventHeadAndSlot`,
  `failingBusinessConditionCommitsNoReceiptRecordEventHeadOrSlot`.
- Inputs unchanged across the run: `inputs-before.sha256` and `inputs-after.sha256` are byte-identical
  (sha256 `2b547bd4…`).

## Atomic items proven by the passing test (assertions untouched)

Commit path, one `TransactWriteItems`:

1. Business record: `APPROVED`, `DecisionSource.ISSUER`, evidence time, dispatch correlation;
   CAS `version = 2`, `committedJournalVersion = 3` (separate facts).
2. Critical result event: 3 event rows.
3. EVENT HEAD: `lastSequence = 3`.
4. Dedupe: redelivery classified `EXACT_REPLAY`, still 3 events, version unchanged, receipt identical.
5. Slot: `usedSlots = 3`.
6. Normalized receipt: 1 row, identity `EVID#SPEC-ENV#ISSUER-SIM#EVID-001`, canonical/instruction/
   response hashes match the signed evidence, `journalVersion = 3`, `stateAfter = APPROVED`.

Abort path: competing determined outcome cancels the transaction — no receipt, 2 events, head 2,
slot 2, state `DECLINED`, `version = 1` / `committedJournalVersion = 2`.
Refusal path: mismatched decision/evidence rejected before any write; dispatch intact.

Wiring of the evidence itself is real: the test builds the persisted `0100` and received `0110`
octets, signs the exact attestation with an ephemeral in-memory Ed25519 key, and runs the real
DIRECT verifier plus the real P6 binder. No service/HTTP/runtime path is exercised.

## Boundary

Local transaction mechanics + real C4 verification/P6 binding only, against DynamoDB Local.
Still NOT production-wired (no `AuthorizationService`/`AwsRuntime` reference). P1 G6 Security
remains open. No full-suite, C4 regression or feature run was performed. No git writes,
publication, cloud call, credential use, deletion or specification change.

## Next (unsigned)

- Wire a signed receipt gateway (`SignedReceiptGateway`) that requires the C4 verifier + P6 binder
  output before composing, and expose `readEvidenceReceipt` through an authorized seam; keep it
  outside `AwsRuntime` until owner approval.
- Recovery/ambiguous-commit handling for the receipt identity (conflict = different canonical hash
  for an existing identity) and a durable quarantine decision remain open.
- Owner approval of O8 capacity numbers still pending; `CAPACITY` in the test stays test-only.
