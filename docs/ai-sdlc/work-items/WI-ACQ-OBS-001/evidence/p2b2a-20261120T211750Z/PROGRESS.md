# P2b2a progress — one real DIRECT Ed25519 verifier

Status: **implemented and locally verified**. This is local construction evidence, not
independent QA, Security review, human acceptance, business admission or a deployment.

## Delivered

- `C4EvidenceVerifier` verifies one DIRECT candidate with the JDK
  `Signature.getInstance("Ed25519")` over the exact canonical attestation bytes.
- The public key is resolved by `(environmentId, signingPrincipal, keyId)` from
  `C4KeyRegistry`; the registry entry also supplies the P5 role and acquirer mandate.
- `C4AuthenticatedTransportContext` is the internal transport-context seam. It must be
  built by the trusted adapter from the authenticated peer and configured environment,
  never from an `X-*` header, body assertion, query parameter or client flag.
- DIRECT binding is exact: authenticated sender == `attestation.signingPrincipal`; outer
  `keyId` == signed `keyId` == registry key; registry environment/principal/keyId are
  rechecked; candidate body and canonical-attestation hashes must equal the signed values.
- P5 role → P4 row matrix:
  - `ISSUER_SIM`: `ISSUER` / `ISSUER_REPLIED` / `00` / `APPROVED` / `APPROVED`, or
    `05` / `DECLINED` / `DELIBERATE_DECLINE`.
  - `PROCESSOR_AUTH`: `PROCESSOR` / `BEFORE_NETWORK` / `30` / `PROCESSOR_REJECTED` /
    `FORMAT|DISALLOWED`, or `PROCESSOR` / `POSSIBLE_ISSUER` / `91` /
    `NOT_CONFIRMED` / `UNCERTAIN|UNAVAILABLE`.
  - `NETWORK_SIM`: `NETWORK` / `BEFORE_ISSUER` / `30` / `PROCESSOR_REJECTED` /
    `FORMAT|DISALLOWED`, or `NETWORK` / `BEFORE_ISSUER` / `91` /
    `PROCESSOR_REJECTED` / `UNAVAILABLE`, or `NETWORK` / `POSSIBLE_ISSUER` / `91` /
    `NOT_CONFIRMED` / `UNCERTAIN|UNAVAILABLE`.
  - `ACL_AUTH`: no evidence-signing authority.
- Acquirer mandate must contain the signed `acquirerRef`.
- `C4VerifiedAttestation` returns the checked identity, role, specific attestation type,
  hashes and a scope marker. It has no payment-confirmed, processor-receipt, valid or
  trusted flag.
- RELAY is explicitly not supported in this slice and is rejected with
  `RELAY_UNSUPPORTED`; it is not accepted, not left merely unverified, and not promoted.

## Schema correction (no specification edit)

The approved profile says DE37 is 12 uppercase letters/digits and P2 says aid/tid/att are
1–64. The existing C4 shape contradicted both, so the implementation was corrected before
signing anything:

- `RRN`: exact 12 uppercase letters/digits (previously exact 12 digits).
- `authorizationId`, `transactionId`, `instructionAttemptId`: 1–64 (previously 1–128).
- `amountMinor`: 100..999999, matching P1's `000000000100`–`000000999999`.
- `authorizationReference`: null or exactly 6 uppercase letters/digits, matching DE38
  (previously 1–6 printable ASCII).

No specification, vector or owner decision was edited.

## Actual result

Targeted command:

```
mvn -o -Dtest=C4EvidenceVerifierTest,C4EvidenceAttestationSchemaTest,C4CarrierParserTest \
  -DfailIfNoTests=false test
```

- `Tests run: 42, Failures: 0, Errors: 0, Skipped: 0`
- `C4EvidenceVerifierTest`: 18
- `C4EvidenceAttestationSchemaTest`: 6
- `C4CarrierParserTest`: 18 (carrier regression because shared schema changed)
- Build success, exit `0`, stderr empty.
- `test-compile` exit `0`.
- No failing iteration; the first targeted run passed.

## Crypto and trust limits

- Only the JDK Ed25519 signature over the canonical attestation bytes is checked.
- Test keys are ephemeral in-memory `KeyPairGenerator` output. No private key is written,
  logged, serialized, exported or included in evidence. No external key login, credential,
  cloud call or custody configuration was used.
- Key custody/rotation, TLS/mTLS principal binding, IAM mapping, production provisioning
  and revocation are **not** implemented.
- This does not compare `instructionSha256` with a persisted original wire instruction,
  parse P6 ISO, bind the full business context, or atomically admit evidence with a
  processor receipt, business version and critical event.
- The result is evidence of cryptographic and trust binding only, not payment
  confirmation and not a processor receipt.

## Preserved / not performed

No full suite, HTTP endpoint, runtime start, AWS/cloud call, credential flow, Git write,
publication, deletion, specification/vector edit or new dependency. Pre-existing tracked
and untracked work was preserved; `source-diff.txt` shows only the intended additions and
the two intended C4 schema edits. P1 and G6 remain open. Independent Security review,
QA and human acceptance remain pending.
