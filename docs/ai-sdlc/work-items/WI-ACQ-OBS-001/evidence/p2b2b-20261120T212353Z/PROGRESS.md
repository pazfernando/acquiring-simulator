# P2b2b progress — RELAY Ed25519 verification extension

Status: **implemented and locally verified**. This is local construction evidence, not
independent QA, Security review, human acceptance, business admission or a deployment.

## Delivered

- `C4EvidenceVerifier` now dispatches DIRECT or RELAY and verifies **both** signatures with
  the JDK `Signature.getInstance("Ed25519")` provider:
  - the inner issuer signature over the exact canonical attestation bytes;
  - the network countersignature over the canonical relay object excluding `signature`.
- RELAY keeps the inner issuer attestation, issuer key and issuer signature unchanged. The
  network cannot mint an issuer outcome or replace the issuer signature.
- The authenticated transport sender must equal the configured relay principal
  `NETWORK-SIM`. The relay key is resolved by
  `(environmentId, NETWORK-SIM, relay.keyId)`; configured environment, principal, keyId,
  `NETWORK_SIM` role and acquirer mandate are all rechecked.
- The relay object binds `principal`, `receiptId`, `deliveredAt`, `responseSha256` and the
  canonical inner `attestationSha256`. The verifier rechecks the relay response hash
  against both the candidate body hash and the signed response hash, and the relay
  attestation hash against both the candidate canonical-attestation hash and the canonical
  inner attestation hash.
- The inner issuer key is resolved by
  `(environmentId, signingPrincipal, issuer keyId)`, constrained to `ISSUER_SIM` and an
  issuer P4 row (approval or deliberate decline), and its acquirer mandate is rechecked.
- `C4VerifiedAttestation` adds the explicit
  `VerificationScope.RELAY_ED25519_AND_TRUST_BINDINGS_VERIFIED` and a `RelayBinding`
  (principal, keyId, receiptId, deliveredAt) for RELAY. DIRECT remains
  `DIRECT_ED25519_AND_TRUST_BINDINGS_VERIFIED` with a null relay binding. There is no
  boolean `valid`/`verified`/payment/receipt flag and no signature, key or body in the
  result.
- `C4VerificationViolation` removes the obsolete `RELAY_UNSUPPORTED` and adds
  `RELAY_BINDING`; signature failures remain `BAD_SIGNATURE` with distinct sanitized
  fields `issuerSignature` and `relaySignature`.
- `C4KeyRegistry`, `C4PrincipalRole`, `C4CarrierCandidate` and `C4RelayAttestation`
  documentation now names the DIRECT/RELAY verifier boundary without claiming IAM, key
  custody or business admission.

## Test coverage

New RELAY cases (16):

1. Valid issuer-approval relay chain with real ephemeral issuer and network key pairs.
2. Valid issuer-decline relay chain.
3. Inner issuer-signature alteration rejected as `BAD_SIGNATURE` / `issuerSignature`.
4. Relay countersignature alteration rejected as `BAD_SIGNATURE` / `relaySignature`.
5. Network signature substituted for the issuer signature rejected as
   `BAD_SIGNATURE` / `issuerSignature`.
6. Inner signer configured as `NETWORK_SIM` rejected as `ROLE_NOT_PERMITTED` /
   `issuerRole` (network cannot mint an issuer outcome).
7. Relay signer configured as `ISSUER_SIM` rejected as `ROLE_NOT_PERMITTED` /
   `relayRole`.
8. Relay response-hash mismatch rejected as `RELAY_BINDING` /
   `relayResponseSha256`.
9. Relay attestation-hash mismatch rejected as `RELAY_BINDING` /
   `relayAttestationSha256`.
10. Wrong authenticated sender rejected as `AUTHENTICATED_SENDER` /
    `relayPrincipal`.
11. Unknown relay key rejected as `UNKNOWN_KEY` / `relayKeyId`.
12. Registry returning a different relay key rejected as `KEY_BINDING` /
    `relayKeyId`.
13. Wrong authenticated environment rejected as `ENVIRONMENT_BINDING` /
    `environmentId`.
14. Relay acquirer-mandate mismatch rejected as `MANDATE_BINDING` /
    `relayMandate`.
15. Canonical relay signed object excludes `signature` and binds receipt, delivery time,
    response hash and inner-attestation hash.
16. Verification leaves the inner attestation, inner signature and relay signature
    byte-identical; the result renders the RELAY scope without signature material.

The existing 17 DIRECT cases are preserved. The obsolete RELAY-unsupported case was
replaced by this RELAY suite.

## Actual result

Targeted command (no `failIfNoTests=false`):

```
mvn -o -Dtest=C4EvidenceVerifierTest,C4CarrierParserTest test
```

- `Tests run: 51, Failures: 0, Errors: 0, Skipped: 0`
- `C4EvidenceVerifierTest`: 33 (17 DIRECT preserved + 16 RELAY)
- `C4CarrierParserTest`: 18 (carrier regression)
- Build success, exit `0`, stderr empty.
- No failing iteration in this phase; the single targeted run passed. A pre-freeze
  `test-compile` also exited `0`.

## Evidence freeze

`source-frozen-pre-run.sha256` and `source-after-run.sha256` are byte-identical over
**229/229** inputs:

- every file under `src/main` and `src/test`, including `src/main/resources`;
- `pom.xml`;
- the three C4 oracles: `vectors/carrier-vectors.json`, `channel-contract.md`,
  `iso8583-profile.md`.

The P2b2a saved folder is retained as evidence, but its before-edit hash list is not used
as this phase's frozen run baseline.

## Crypto and trust limits

- Both Ed25519 signatures are checked with the JDK provider over exact canonical bytes.
- Test keys are ephemeral in-memory `KeyPairGenerator` output. No private key is written,
  logged, serialized, exported or included in evidence. No external key login, credential,
  cloud call or custody configuration was used.
- Key custody/rotation/revocation, TLS/mTLS principal binding, IAM mapping, production
  provisioning and external credentials are **not** implemented.
- No `instructionSha256` comparison against a persisted original wire instruction, no P6
  ISO parse or business binding, no duplicate/conflict resolution, no atomic
  evidence+receipt+version+critical-event admission, no late-delivery envelope or
  instruction binding, and no HTTP/service caller or runtime wiring.
- The result is evidence of the cryptographic and trust chain only; it is not payment
  confirmation and not a processor receipt.

## Preserved / not performed

No full suite, HTTP endpoint, runtime start, AWS/cloud call, credential flow, Git write,
publication, deletion, specification/vector edit or new dependency. Pre-existing tracked
and untracked work was preserved. P1 and G6 remain open. Independent Security review, QA
and human acceptance remain pending; this progress record is not self-approval.

## Next

P6 original-instruction binding plus the C4 late-delivery envelope and instruction-binding
seam. Storage admission, HTTP/service caller, key custody and runtime remain separate.
