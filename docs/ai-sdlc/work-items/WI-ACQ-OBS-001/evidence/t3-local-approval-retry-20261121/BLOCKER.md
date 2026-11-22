# T3 bounded slice — exact integration boundary and blocker

Date: 2026-11-21. Local construction evidence only. Not QA, Security review, acceptance or deployment.

## Requested slice

Synthetic **approval + identical retry** through the existing real **atomic claim**, **dispatch
intent** and **verified signed response receipt** components, asserting **one financial send** and a
**durable result**, starting at a normal local application entry if one exists.

## Why no single service-level signed slice is available

1. **No normal local application entry constructs the atomic path.** `AcquiringSimulatorApplication`
   is a Spring Boot shell with no HTTP controller. `AwsRuntime.processor()` is the AWS/Lambda
   composition root and still builds the legacy 4-argument `AuthorizationService`
   (`src/main/java/com/acquiring/simulator/adapter/aws/AwsRuntime.java:40`); it constructs none of the
   atomic claim/dispatch/result gateways. This is the already-recorded CA-4 / G6 activation gap, not
   a new decision.
2. **No production component connects the downstream reply to signed C4 evidence.**
   `SignedEvidenceReceiptGateway` is explicitly *not* referenced by `AuthorizationService` or
   `AwsRuntime` (`src/main/java/com/acquiring/simulator/adapter/aws/SignedEvidenceReceiptGateway.java:82`).
   `AuthorizationService` writes a confirmed result only through the `ResultGateway` port
   (`.../domain/AuthorizationService.java:682`), whose production adapter is the unsigned
   `DynamoResultGateway`. The signed receipt gateway is not a `ResultGateway`, and the downstream
   `IssuerSimulator` returns only a `DownstreamReply`, not signed bytes.
3. **A concrete currency-projection conflict blocks binding approved signed evidence to a
   service-created record.** This is the decisive gap, not just missing wiring:
   - the business record stores `USD` (`.../domain/AuthorizationService.java:54, 530, 986`);
   - the approved ISO mapper hardcodes DE49 `840`
     (`.../channel/ServerResolvedInstructionMapper.java:76, 282`);
   - the P6 binder sets the bound decision currency from the signed attestation and requires it to
     equal the persisted original's DE49 (`.../channel/C4P6SignedFieldBinder.java:124, 397`);
   - the receipt composer requires `decision.currency() == intent.currency()`
     (`.../adapter/aws/EvidenceReceiptCriticalComposer.java:255`).

   Therefore a valid approved `0100`/`0110` pair (DE49 `840`, as in the pinned vectors) can never
   satisfy the receipt composer against a service-created record (`USD`). Resolving this requires an
   approved mapping decision (numeric ISO currency vs. simulator business currency), which is not
   available in this task.
4. **Identifier and original-instruction allocation are not joined.** The service mints
   `AUTH-<24 hex>` and `CORR-<24 hex>` (`.../domain/AuthorizationService.java:522, 577`), while the
   pinned signed vectors describe `AUTH-001`/`CORR-001`. The signed gateway also requires persisted
   original `0100` bytes, but `DynamoOriginalInstructionReader` has **no production writer**
   (`.../adapter/aws/DynamoOriginalInstructionReader.java:20-23`). Both are T2 items (durable
   ID/alias/original-instruction allocation), not finished here.

Consequence: the only faithful way to exercise the real signed receipt is a record authored
consistently with the approved signed fixture (as the existing P2b5b tests do), not a record produced
by the current `AuthorizationService`. Using the service and then admitting signed evidence would
either be refused or produce a `CONTENT_CONFLICT`/quarantine, not a durable signed approval.

## Tested boundary (no fake green, no fallback)

Two genuinely independent existing integration paths are executed, each with the **real adapters**
and DynamoDB Local, and each with an **identical retry**:

- **Path A — service-level atomic approval + retry.** Real `AuthorizationService` +
  `DynamoClaimGateway` + `DynamoDispatchIntentGateway` + `DynamoResultGateway`. Asserts one financial
  send, durable `APPROVED`, and no second send/append on the identical retry. This path is the real
  atomic journey; its result commit is the unsigned verified-result adapter, not the signed receipt.
- **Path B — signed receipt + retry.** Real `DynamoClaimGateway` + `DynamoDispatchIntentGateway` +
  one simulated downstream call + real `SignedEvidenceReceiptGateway` (real C4 Ed25519 verification
  and real P6 binding) with the pinned approved fixture. Asserts one financial send, durable
  `APPROVED` with one receipt, and an identical retry that returns replay and adds nothing. Send is
  gated by the real `DispatchIntentGateway.Decision.permitsSend()`; the harness supplies the same
  ordering the service implements because the production composition root does not yet host it. The
  persisted original `0100` and the record shape are seeded from the approved fixture, which is the
  documented prerequisite above.

Neither path claims end-to-end runtime integration, `AwsRuntime` activation, independent QA,
Security review, human acceptance, or completion of the full T3 matrix (decline, network rejection,
unknown/timeout, conflicting retry, malformed response, late evidence remain open).
