# P2b1 evidence manifest (sanitized recovery)

## Binding

- Work item: `WI-ACQ-OBS-001` (P2b1 signed C4 carrier evidence)
- Branch: `feat/wi-acq-auth-001`
- HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315`
- Model: `nan/deepseek-v4-flash`, no override
- Kind: capture of an **existing** targeted run, not a rerun

## Result recovered

- Source: `target/surefire-reports/...C4CarrierParserTest.txt` and `.xml`
- Run id: `2026-11-20T16-10-53_008-jvmRun1` (JVM start 16:10:53.008, artifacts 16:10:54.1)
- Tests: **18**, failures **0**, errors **0**, skipped **0**, flakes **0**, time 0.377 s
- Targeted: XML `test` property = `C4CarrierParserTest`
- JDK: Temurin 25.0.5+8-LTS (`/home/pazfernando/.local/opt/jdk-25`)

### The 18 executed cases (all PASS)

1. base64WhitespaceAndPaddingAreNonCanonical
2. caseVariantDuplicateHeaderCannotCollapse
3. nonCanonicalEnvelopeBytesAreRejected
4. unknownEnvelopeKeyIsRejected
5. nonZeroPadBitsAreNonCanonical
6. unknownAttestationKeyIsRejected
7. outerKeyMustMatchSignedAttestationKey
8. directModeWithRelayObjectIsRejected
9. matchesAllApprovedCarrierShapeFixtures (12 fixture cases)
10. commaCoalescedHeaderIsRejectedAsDuplicate
11. candidateHasNoTrustFlagAndDoesNotLeakMaterial
12. foldedHeaderIsRejected
13. boundsAreEnforcedBeforeDecode
14. duplicateJsonKeyIsRejected
15. signatureMustBeExactly64Bytes
16. duplicateEvidenceHeaderCannotCollapse
17. acceptedCandidateRenderingOmitsCarrierMaterial
18. totalHeaderBudgetAndBodyBudgetAreEnforced

## 12 carrier fixture cases (category mapping only)

| id | fixture expectation | test treatment | tested parser outcome |
|---|---|---|---|
| K01-direct | SHAPE_ACCEPT_REQUIRES_REAL_SIGNATURE_VERIFICATION | shape accept | DIRECT sender matched; UNVERIFIED / UNRESOLVED |
| K02-relay | SHAPE_ACCEPT_REQUIRES_REAL_SIGNATURE_VERIFICATION | shape accept | RELAY sender matched; relay hashes bound |
| K03-late | SHAPE_ACCEPT_REQUIRES_REAL_SIGNATURE_VERIFICATION | shape accept | DIRECT sender matched; late envelope shaped |
| K04-missing-sidecar | MISSING_PROOF | reject | MISSING_PROOF |
| K05-response-hash-mismatch | REJECT_HASH_BINDING | reject | HASH_BINDING |
| K06-signature-absent | REJECT_CARRIER_SCHEMA | reject | CARRIER_SCHEMA |
| K07-key-unavailable | REJECT_UNRESOLVED_KEY (later stage) | shape accept | keyId preserved `UNKNOWN`; UNRESOLVED |
| K08-direct-wrong-sender | REJECT_AUTHENTICATED_SENDER | reject | AUTHENTICATED_SENDER |
| K09-relay-hash-mismatch | REJECT_RELAY_BINDING | reject | RELAY_BINDING |
| K10-oversized-sidecar | REJECT_CARRIER_SIZE_BEFORE_SIGNATURE | reject | CARRIER_SIZE (before decode) |
| K11-late-missing-proof | REJECT_MISSING_PROOF | reject | MISSING_PROOF |
| K12-proof-unavailable | TRANSPORT_FAILURE | reject | TRANSPORT_FAILURE |

Note: the `K07` fixture expects a resolver-stage rejection; this parser slice preserves
the unresolved key and leaves rejection to the later key-resolution stage.

## Authentication status

**Not verified.** These tests prove envelope shape, canonicalisation, exact body-hash
binding, structural sender binding and no-leak rendering. They do **not** prove an
Ed25519 signature, key ownership, P5 role/mandate or transport identity. Attestation
state is `UNVERIFIED_CANDIDATE`; key resolution is `UNRESOLVED`.

## Provenance limits

- No saved stdout/stderr/exit for the original run; pass counts come from Surefire
  artifacts only. The prepared capture script aborted before writing. See
  `run-provenance.md`.
- Hashes of current bytes were captured during recovery; run-time byte equality is
  inferred from mtimes/absence of later edits, not attested by a pre-run hash.

## Hygiene

Read-only capture. No test rerun, source edit, new feature, full regression, HTTP or
credential flow, git write, publication, cloud call, deletion or spec edit. No header,
body, raw proof/signature or secret is copied. Runtime inactive; P1/G6 open.

## Next exact slice (not implemented here)

Add one C4 attestation verifier using the JDK `Signature("Ed25519")` against a
configured **test** public key selected by `keyId`, over the already-fixed canonical
attestation bytes, then enforce the P5 signed schema/role/mandate and the transport
sender binding:

- DIRECT: authenticated sender == `attestation.signingPrincipal` == configured
  attesting principal, with permitted P4 role/mandate.
- RELAY: authenticated sender == NETWORK-SIM; inner issuer attestation/signature/keyId
  unchanged; relay signature over canonical relay object; role/key/mandate match.
- Only on success promote `UNVERIFIED_CANDIDATE` to a verified state; keep rejection
  categories distinct (bad signature, unknown key, wrong role, wrong sender, hash
  mismatch).

This remains local construction only; TLS/principal/IAM binding, key custody/rotation
and any Ed25519 implementation choice still await architecture/Security approval.
