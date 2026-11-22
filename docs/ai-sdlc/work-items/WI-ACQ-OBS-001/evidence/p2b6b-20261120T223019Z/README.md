# P2b6b — one signed late admission gateway

Date: 2026-11-20
Branch: `feat/wi-acq-auth-001`
HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315`
Origin: `github.com/pazfernando/acquiring-simulator.git`
Work item: WI-ACQ-OBS-001 (evidence kept beside the P2b6a1 composer evidence).

## Scope

ONE signed late-evidence admission gateway plus its tests. It reuses, rather than duplicates, the
normal signed path's trust model:

- `SignedEvidenceReceiptGateway.TrustedContextAuthorizer` and `.ConflictRecorder` seams;
- real `C4EvidenceVerifier.verify` (Ed25519 DIRECT/RELAY);
- real `C4P6SignedFieldBinder.bind` over the persisted original `0100` and exact received `0110`;
- `ProcessorEvidenceReceipt` P5 identity `(environmentId, attestingPrincipal, evidenceId)` and
  canonical attestation hash;
- `SignedEvidenceConflict` durable quarantine marker;
- `LateSignedEvidenceReceiptComposer` (P2b6a1) for the one atomic late receipt/result transaction.

No caller-supplied verified/bound/already-admitted flag and no pre-verified reply DTO.

## New files

- `src/main/java/com/acquiring/simulator/adapter/aws/LateSignedEvidenceReceiptGateway.java`
- `src/test/java/com/acquiring/simulator/channel/LateSignedEvidenceReceiptGatewayIntegrationTest.java`

Nothing else was modified. No spec, pom, resource, fixture/pin, existing composer or existing test
file changed; no dependency added.

## Targeted tests (six)

```
JAVA_HOME=/home/pazfernando/.local/opt/jdk-25 \
  /home/pazfernando/.local/opt/maven/bin/mvn -o -q \
  -Dtest=LateSignedEvidenceReceiptGatewayIntegrationTest test
```
Result: Tests run 6, Failures 0, Errors 0, Skipped 0; exit 0.

Justified regression:
```
... -Dtest=EvidenceReceiptAtomicIntegrationTest test
```
Result: Tests run 4, Failures 0, Errors 0, Skipped 0; exit 0.

## Behaviour summary

1. Trusted transport context authorized before any storage read.
2. Real C4 verification; only authenticated evidence may name the operation.
3. Strong read of the persisted original record and persisted `0100`; real P6 bind.
4. Envelope (deadline, delivery time, receipt id) authorized against the durable dispatch.
5. Evidence-time classification: `ON_TIME` / `DELIVERY_DELAY` → `NORMAL_RESULT_ADMISSION`, no write,
   no receipt lookup, no relabel; only `LATE` proceeds.
6. P5 receipt lookup after classification: exact identity+hash → `REPLAY` preserving first receipt,
   first `collectedAt`, first late metadata, slot and sequence, even after terminal state; changed
   content or contradictory determined outcome → sanitized quarantine + preserved state.
7. First admission → one `TransactWriteItems` through the P2b6a1 composer.
8. Ambiguous SDK outcome → one strong receipt+event+record lookup; grounded `RECOVERED`, else
   `UNKNOWN`; no resend/retry/fallback.

## Evidence files

- `head-before.txt` / `head-after.txt` (identical)
- `hashes-before.sha256` / `hashes-after.sha256` / `hashes.diff` (no input change)
- `git-status-before.txt` / `git-status-after.txt`
- `targeted-stdout.txt` / `targeted-stderr.txt` / `targeted-exit.txt`
- `regression-stdout.txt` / `regression-stderr.txt` / `regression-exit.txt`
- Surefire `.txt` and `TEST-*.xml` copies for both runs
- `wiring.txt`, `toolchain.txt`, `BINDING.txt`, `branch.txt`, `test-command.txt`
- `RESULTS.md`, `PROGRESS.md`, `MANIFEST.md`

## Unverified / next

Local admission mechanics only. Not production-wired: no `AuthorizationService`/`AwsRuntime`
reference, no HTTP/service route, no runtime activation, no key custody, no downstream call.
P1 G6 Security remains open. Next: real service admission and an owned late reused-identity fixture.
