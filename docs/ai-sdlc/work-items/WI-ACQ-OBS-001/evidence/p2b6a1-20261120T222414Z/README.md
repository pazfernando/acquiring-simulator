# P2b6a1 — late signed-evidence receipt composer (local slice)

Date: 2026-11-20
Branch: feat/wi-acq-auth-001
HEAD: cfc456f4f469d9558a78fda8ff9b770b00817315 (origin github.com/pazfernando/acquiring-simulator.git)
Work item: WI-ACQ-OBS-001

## Scope
- ONE wrapper: `LateSignedEvidenceReceiptComposer` delegates to the existing
  `LateEvidenceCriticalComposer` and appends the existing normalized P5 receipt put from
  `EvidenceReceiptCriticalComposer` in the SAME `TransactWriteItems`.
- Small refactor in `EvidenceReceiptCriticalComposer`: extracted `signedReceipt(...)`,
  `requireReceiptSubmission(submission, receipt)` and `receiptPut(journalTable, receipt)`
  so both normal and late paths derive the receipt and its guard from one place. No behavior
  change to the normal path.
- Evidence accepted only from the real verifier/binder chain (`C4VerifiedAttestation`,
  `C4EvidenceAttestation`, `C4P6BindingDecision`) — no caller-supplied verified flag.
- Not production-wired: no HTTP/runtime/conflict/replay/service wiring.

## Targeted tests (only these two)
Command:
```
JAVA_HOME=/home/pazfernando/.local/opt/jdk-25 \
  /home/pazfernando/.local/opt/maven/bin/mvn -o -q \
  -Dtest=LateSignedEvidenceReceiptAtomicIntegrationTest test
```
Result (surefire): Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
Exit: 0

1. `lateSignedEvidenceCommitsReceiptLateMetadataRecordEventHeadDedupeAndSlotAtOnce` —
   real EF:V08-late-approval (evidence 12:00:05Z > deadline 12:00:03Z), real DIRECT
   verify + real P6 bind; one append commits record (CAS 2, committed 3), late metadata,
   event rows 3, HEAD 3, slot 3, receipt row 1.
2. `failingBusinessConditionCommitsNoLateReceiptRecordEventHeadOrSlot` — competing
   determined outcome after intent read; transaction cancels and commits NO receipt row,
   no late metadata, no event, no HEAD advance, no slot.

## Evidence files
- head-edited.txt / head-after.txt (identical: cfc456f4f469d9558a78fda8ff9b770b00817315)
- hashes-edited.sha256 / hashes-after.sha256 (identical file hashes)
- git-status-edited.txt / git-status-after.txt
- targeted-stdout.txt / targeted-stderr.txt / targeted-exit.txt
- surefire report copy

## Unverified / next
- Local composition mechanics only; no AWS effect, service/HTTP caller, replay/conflict
  gateway or runtime wiring. Next explicitly: signed late replay/conflict gateway slice.
- P1G6 Security review remains open. This is admission progress, not completion.
