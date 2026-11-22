# P2b6b — signed late admission gateway: actual results

UTC: 2026-11-20T22:30:19Z start; 22:30:41Z targeted run start; 22:31Z finish.
Model: DeepSeek V4 Flash (provider `nan`).
Binding: `/home/pazfernando/projects/acquiring-simulator`, branch `feat/wi-acq-auth-001`,
HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`
(origin `github.com/pazfernando/acquiring-simulator.git`), default configuration, no override.
`head-before.txt` == `head-after.txt`; `hashes-before.sha256` == `hashes-after.sha256`
(`hashes.diff` empty). No file was edited by the run.

## Commands and outcomes

```
JAVA_HOME=/home/pazfernando/.local/opt/jdk-25 \
  /home/pazfernando/.local/opt/maven/bin/mvn -o -q \
  -Dtest=LateSignedEvidenceReceiptGatewayIntegrationTest test
```
- exit **0** (`targeted-exit.txt`), stderr empty.
- Surefire: **Tests run: 6, Failures: 0, Errors: 0, Skipped: 0**, 6.644 s.
- `tests="6" errors="0" skipped="0" failures="0" flakes="0"` in the XML copy.

```
... -Dtest=EvidenceReceiptAtomicIntegrationTest test
```
- exit **0** (`regression-exit.txt`), stderr empty.
- Surefire: **Tests run: 4, Failures: 0, Errors: 0, Skipped: 0**, 5.827 s.
- This is the justified regression for the P2b6a1 refactor of the shared receipt writer
  (`signedReceipt` / `requireReceiptSubmission` / `receiptPut`) that the normal path also uses.

Docker/Testcontainers were real (`amazon/dynamodb-local:2.6.1` started, see
`targeted-stdout.txt`). Compilation was real: the new sources are referenced by the executed
tests; no stale class could satisfy the assertions.

## Exact coverage (six tests)

1. `lateEd25519EvidenceCommitsReceiptRecordEventHeadSlotAndLateMetadataAtOnce`
   - Fixture `EF:V08-late-approval` (evidence 12:00:05Z > deadline 12:00:03Z, delivery 12:00:20Z),
     ephemeral Ed25519 key, real DIRECT verify + real P6 bind.
   - `COMMITTED`, timing `LATE`, `recorded()` true.
   - One transaction: record `APPROVED`/`ISSUER`/outcome 12:00:05Z, CAS `version` 2,
     `committedJournalVersion` 3, `lateEvidence = true`,
     `LateEvidenceReceipt(LATE-RECEIPT-001, 12:00:05Z, 12:00:20Z, 12:00:03Z, true)`.
   - 3 event rows, HEAD 3, dedupe 3, slot 3, one receipt `EVID#SPEC-ENV#ISSUER-SIM#EVID-001`
     with signed hashes, `eventId = APPROVED-AUTH-001`, version 3, state `APPROVED`.
2. `freshGatewayExactP5ReplayAfterTerminalStatePreservesFirstReceiptCollectedAtAndSlots`
   - Fresh gateway instance, later delivery (+30 s) and a different `deliveryReceiptId`.
   - `REPLAY`, `recorded()` true; receipt equals the first receipt.
   - Late metadata still the **first** one (original id and times).
   - Dedupe rows, event rows and receipt rows are map-equal before/after; HEAD unchanged; slot 3.
3. `contradictoryLateSignedOutcomeIsQuarantinedAndPreservesTheFirstConfirmation`
   - First late `APPROVED` (EVID-001), then late `EF:N-conflicting-confirmation`
     (EVID-CONFLICT, DECLINED).
   - `CONTENT_CONFLICT`, `recorded()` false, quarantine
     `CONTRADICTORY_DETERMINED_OUTCOME` with candidate hash and established authorization id.
   - Marker keys are metadata only (no payload/signature/proof/original/response/secret).
   - Established receipt, `APPROVED` state, version (2,3), 3 events, HEAD 3 and slot 3 survive;
     no DECLINED event.
4. `lostCommitResponseIsRecoveredOnceWithoutASecondLateResult`
   - Fault proxy commits then throws `SdkClientException`; lookups then use the clean client.
   - `RECOVERED`, `recorded()` true, receipt and late metadata read back.
   - Exactly one result: 3 events, HEAD 3, slot 3, one receipt, one `APPROVED-AUTH-001` event.
5. `ambiguousCommitWithUnreadableLookupStaysUnknownAndNeverNoSend`
   - Fault proxy commits, then fails `getItem`.
   - `UNKNOWN`, `recorded()` false, no state claimed.
   - Clean read proves the write did commit (3 events, HEAD 3, one receipt, `APPROVED`):
     `UNKNOWN` is "not established", never "no result/no send".
6. `onTimeEvidenceDeliveredLateIsHandedToNormalAdmissionWithoutWrites`
   - Fixture `EF:V02-approved-response` (evidence 12:00:01Z <= deadline 12:00:03Z,
     delivered 12:00:20Z).
   - `NORMAL_RESULT_ADMISSION`, timing `DELIVERY_DELAY`, no writes, no receipt, no quarantine,
     no late metadata; record stays `AWAITING`/`NONE`, version (1,2), 2 events, HEAD 2, slot 2.

## Ordering and trust actually exercised

Trusted transport context is authorized before any storage read. The C4 verifier runs before the
persisted operation is named. The strongly consistent original record and persisted `0100` bytes are
read after verification. The real P6 binder runs over those exact bytes and the exact received
`0110`. Envelope facts (deadline, delivery time, receipt id) are authorized only after binding. The
P5 receipt lookup happens only after timing classification, so on-time evidence cannot be used as a
receipt oracle and is never relabelled. No test passes a "verified", "bound" or "already admitted"
flag.

## Boundary

Local admission mechanics + real C4 verification/P6 binding only, against DynamoDB Local.
Not production-wired (`wiring.txt` shows references only in the gateway and its test).
P1 G6 Security remains open. No full-suite, C4 regression, feature, HTTP, runtime, AWS, key-custody,
git-write, publication, credential, deletion or specification action was performed.

## Next (unsigned)

- Real service admission: an internal caller that supplies the authenticated transport context and
  the delivery envelope, with owner approval for runtime activation.
- Owned late fixture for a reused evidence identity with changed signed content (the second
  quarantine class) to complement the contradictory-outcome case.
- P1 G6 Security review.
