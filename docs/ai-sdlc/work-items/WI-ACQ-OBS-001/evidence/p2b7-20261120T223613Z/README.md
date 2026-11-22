# P2b7 — internal signed-evidence service facade

Date: 2026-11-20
Branch: `feat/wi-acq-auth-001`
HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315`
Origin: `https://github.com/pazfernando/acquiring-simulator.git`
Work item: WI-ACQ-OBS-001 (evidence kept beside the P2b5/P2b6 evidence).

## Scope

ONE bounded internal service facade, no HTTP and no runtime activation. It routes the
already-authenticated transport context and the exact C4 late-delivery envelope to the existing
normal or late signed receipt gateway; it owns no verification, storage, receipt, business
transition or conflict rule of its own.

New files (nothing else changed):

- `src/main/java/com/acquiring/simulator/adapter/aws/SignedEvidenceAdmissionService.java`
- `src/test/java/com/acquiring/simulator/channel/SignedEvidenceAdmissionServiceIntegrationTest.java`

## Trust model

- Inputs are `C4LateDeliveryCandidate` (parser output, explicitly unverified), the
  `C4AuthenticatedTransportContext`, the configured `C4KeyRegistry`, the deadline from the durable
  dispatch policy and the authenticated `deliveredAt`. No `verified`/`bound`/`late` flag, no
  pre-verified reply and no caller-selected route.
- The facade recomputes the exact carriage hashes from the envelope content and rejects a
  presented hash that does not describe the exact bytes. This is integrity checking only.
- The sender binding is derived from the authenticated sender and the envelope mode; a sender that
  is neither the DIRECT signer nor the RELAY principal is refused before any storage read.
  Diagnostic forwarding is not admitted here because the signed receipt gateways verify without
  the forwarding permission.
- Every gateway then re-runs the trusted-context authorization before storage, the real C4
  DIRECT/RELAY verifier and the real signed-P5/P6 binder. Existing `UNKNOWN`, `CONTENT_CONFLICT`,
  replay and persisted-version semantics are returned unchanged.

## Routing

1. Strictly late evidence (evidence availability after the deadline) → late signed gateway once.
2. On-time or delivery-delayed evidence (available at/before the deadline, delivered later) → the
   late gateway returns `NORMAL_RESULT_ADMISSION` and the facade invokes the normal signed gateway
   exactly once, so the normal path remains the single owner of the write and no late metadata is
   created.
3. Invalid transport identity, proof, binding or envelope carriage → no gateway write; the attempt
   is `REFUSED` by the gateway or `NONE` at the facade, never relabelled, retried or sent.

## Targeted test (three cases)

```
JAVA_HOME=/home/pazfernando/.local/opt/jdk-25 \
  /home/pazfernando/.local/opt/maven/bin/mvn -o -q \
  -Dtest=SignedEvidenceAdmissionServiceIntegrationTest test
```
Result: Tests run 3, Failures 0, Errors 0, Skipped 0; exit 0.

1. Strictly late `EF:V08-late-approval` → `LATE`, `COMMITTED`, one business transaction, late
   metadata present; exact redelivery → `REPLAY` with the first receipt/metadata and no second
   transaction.
2. On-time `EF:V02-approved-response` delivered late → `NORMAL`, late decision
   `NORMAL_RESULT_ADMISSION`/`DELIVERY_DELAY`, normal decision `COMMITTED`, normal journal version,
   one business transaction, no late metadata.
3. Flipped Ed25519 signature → `REFUSED`; forged body-hash label → facade `NONE`; both write
   nothing, advance no version/event/HEAD/slot, create no receipt and no quarantine.

Docker/Testcontainers were real (`amazon/dynamodb-local:2.6.1`), and the executed tests reference
the new facade, so no stale class could satisfy them.

## Evidence files

- `BINDING.txt`, `branch.txt`, `toolchain.txt`, `test-command.txt`
- `head-before.txt` / `head-after.txt` (identical)
- `hashes-before.sha256` / `hashes-after.sha256` / `hashes.diff` (empty)
- `git-status-before.txt` / `git-status-after.txt` (identical)
- `targeted-stdout.txt` / `targeted-stderr.txt` (empty) / `targeted-exit.txt`
- Surefire `.txt` and `TEST-*.xml` copy
- `wiring.txt` (facade referenced only by itself and its test)
- `RESULTS.md`, `PROGRESS.md`, `MANIFEST.md`, `evidence.sha256`

## Unverified / next

Local facade routing only. Not production-wired: no HTTP endpoint, authenticated-transport
adapter, sender authentication, key custody, capacity approval, runtime activation, AWS effect or
downstream call. P1 G6 Security remains open; independent QA, Security review and human
acceptance remain pending. Next is the HTTP/authenticated-transport integration handoff described
in `RESULTS.md`.
