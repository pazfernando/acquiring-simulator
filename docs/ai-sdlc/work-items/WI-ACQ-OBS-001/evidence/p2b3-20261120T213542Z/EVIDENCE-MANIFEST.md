# P2b3 evidence manifest (sanitized recovery)

## Binding

- Work item: `WI-ACQ-OBS-001` (P2b3 C4 late-delivery envelope + instruction-binding seam)
- Branch: `feat/wi-acq-auth-001`
- HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315` (expected and observed)
- Origin: `https://github.com/pazfernando/acquiring-simulator.git` (configured default
  preserved, no override)
- Model: `nan/deepseek-v4-flash`, no override
- Kind: recovery of an **existing** targeted run, not a rerun
- Runtime: inactive; no AWS/cloud call, HTTP, credential flow or storage change

## Result recovered

- Source: `/tmp/opencode/p2b3-run/{stdout,stderr,exit}.txt` for the invocation, plus
  `target/surefire-reports/TEST-...C4LateDeliveryTest.xml` and the three companion class
  reports from the same run.
- Command: `mvn -o -Dtest=C4LateDeliveryTest,C4EvidenceVerifierTest,C4CarrierParserTest,C4EvidenceAttestationSchemaTest test`
- Run window (local): `2026-11-20T16:34:34.212-05:00` .. `16:34:34.504-05:00`
- stdout: `BUILD SUCCESS`, finished `16:34:34-05:00`; stderr empty; exit `0`
- Aggregate: **80 tests, 0 failures, 0 errors, 0 skipped**

| class | tests | failures | errors | skipped | time |
|---|---|---|---|---|---|
| `C4LateDeliveryTest` | 23 | 0 | 0 | 0 | 0.503 s |
| `C4EvidenceVerifierTest` | 33 | 0 | 0 | 0 | 0.173 s |
| `C4CarrierParserTest` | 18 | 0 | 0 | 0 | 0.086 s |
| `C4EvidenceAttestationSchemaTest` | 6 | 0 | 0 | 0 | 0.031 s |

The 33/18/6 classes are the preserved P2b1/P2b2 regressions in the same invocation; only
`C4LateDeliveryTest` (23) is new to this phase.

## Delivered C4 late-delivery files present in the working tree

Main (untracked, local construction):

- `C4LateDeliveryEnvelope` — closed late-delivery envelope + nested P2b evidence
- `C4LateDeliveryParser` — 8192-byte pre-parse bound, compression refusal, canonical
  base64/JSON, 4096-byte evidence bound, 2048-byte response-body bound, 64-char receipt id
- `C4LateDeliveryCandidate` — unverified candidate + late-envelope hash, no trust flag
- `C4LateDeliveryVerifier` — composes the existing DIRECT/RELAY Ed25519 verifier and the
  delivery-sender rule; no payment/storage/receipt-durability inference
- `C4VerifiedLateDelivery` — verified scope + receipt label + late-envelope hash only
- `C4LateDeliveryViolation`, `C4LateDeliveryException`

Test: `C4LateDeliveryTest` (23 cases).

## The 23 executed late-delivery cases (all PASS)

Chronology from `surefire-cases.tsv`:

1. `validDirectLateEnvelopeVerifiesWithRealEd25519` — real Ed25519, direct sender
2. `validRelayLateEnvelopeVerifiesBothRealSignatures` — real inner + relay signatures
3. `relayDiagnosticForwardStillVerifiesBothSignatures`
4. `tamperedDirectSignatureFailsRealEd25519`
5. `tamperedRelaySignatureFailsRealEd25519`
6. `wrongSenderWithoutDiagnosticPermissionFailsClosed`
7. `diagnosticForwardWithExplicitScopedPermissionIsAcceptedWithoutOutcomeAuthority`
8. `diagnosticForwardOutsideConfiguredScopeIsRefused`
9. `deliveryReceiptIdAloneGrantsNoDecisionOrDurabilityAuthority`
10. `responseBodyHashMismatchIsRejectedBeforeVerification`
11. `approvedLateShapeFixtureParsesAsUnverifiedCandidate`
12. `approvedLateMissingEvidenceFixtureIsRejected`
13. `oversizedLateEnvelopeIsRejectedBeforeParsing`
14. `oversizedOriginalBodyIsRejected`
15. `unknownExtraKeyIsRejected`
16. `unknownNestedEvidenceKeyIsRejected`
17. `duplicateJsonKeyIsRejected`
18. `foldedPayloadIsRejected`
19. `nonCanonicalSpacingIsRejected`
20. `nonCanonicalResponseBodyBase64IsRejected`
21. `compressedPayloadIsRejected`
22. `malformedJsonIsRejected`
23. `parserAndVerifierExposeNoPaymentOrWorkerSurface`

## Coverage actually demonstrated

- **Direct**: an approved direct late envelope verifies with a real ephemeral Ed25519
  key pair over the exact canonical attestation bytes.
- **Relay**: an approved relay late envelope verifies **both** the inner issuer
  signature and the network countersignature; the diagnostic-forward relay path also
  verifies both.
- **Late-envelope bounds**: the 8192-byte raw limit rejects before parsing; original
  response body and evidence sizes are bounded; gzip/zlib-style containers rejected.
- **Sender permission**: a wrong sender is admitted only through the explicit scoped
  `diagnosticForwardPermitted` seam, gains no outcome authority, and otherwise fails
  closed; receipt id alone grants no decision or durability authority.
- **Unknown/failure paths**: unknown keys (flat and nested), duplicate keys, folded
  payload, non-canonical spacing, non-canonical base64, compression, malformed JSON and
  response-hash mismatch all fail closed with distinct violations.
- **No surface leakage**: parser/verifier expose no payment, send, storage or worker
  API, and the result has no `valid`/`paymentConfirmed`/`receiptDurable` flag.

No private key, raw proof body, signature bytes or secret is copied into evidence; the
test uses ephemeral in-memory key pairs only.

## Limits

- No persisted-instruction `instructionSha256` comparison and no P6 ISO parse/business
  binding; the late envelope binds the response hash, canonical attestation hash and
  inner proof, not a stored original wire instruction.
- No duplicate/conflict resolution, no atomic evidence+receipt+version+critical-event
  admission, no HTTP adapter, runtime wiring, key custody/rotation or TLS/IAM binding.
- No attested pre-run hash; current-byte hashes are recovery-time. See
  `run-provenance.md`.
- No full regression in this phase.

## Integrity artifacts

- `hashes-current.sha256` — 233 input files under `src/main`+`src/test`, `pom.xml`, and
  the three C4 oracles (`carrier-vectors.json`, `channel-contract.md`,
  `iso8583-profile.md`); 237 hashed paths in total
- `chronology.tsv` — input, compiled-class and Surefire mtimes
- `surefire-summary.txt`, `surefire-testsuite-attrs.tsv`, `surefire-cases.tsv`,
  `surefire-origin-sha256.txt`, `surefire-origin-stat.txt`
- `test-command.txt`, `test-stdout.txt`, `test-stderr.txt`, `test-exit.txt`,
  `test-summary.txt`
- `git-status-before.txt`, `git-status-after.txt`
- `java-version.txt`, `javac-version.txt`, `mvn-version.txt`
- `capture-origin-sha256.txt`, `capture-origin-stat.txt` — hashes of the source capture in
  `/tmp/opencode/p2b3-run`, proving the copies are byte-identical
- `delivered-files.sha256`, `delivered-files-verify.txt`
- `evidence.sha256`, `evidence-verify.txt`

## Progress-log gap

The work-item `implementation-progress.md` still ends at P2b2b; this recovery did not edit
it (scope limited to this evidence folder). A later authorized phase should record the
recovered P2b3 result there.

## Next exact slice (not implemented here)

P5 full signed field binding to the persisted instruction (per this work item's
`implementation-progress.md`): bind the late envelope to the persisted instruction
SHA-256 plus the P5 signed schema/role/mandate and sender binding, still local — **not**
storage admission, HTTP or runtime. This remains local construction only; independent
Security review, QA and human acceptance are pending, and P1/G6 remain open.
