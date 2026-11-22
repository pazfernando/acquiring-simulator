# P2b2a evidence manifest — DIRECT Ed25519 verification

## Binding

- Work item: `WI-ACQ-OBS-001` (continues the P2b1 carrier sequence; code is in the shared
  `channel` package)
- Branch: `feat/wi-acq-auth-001`
- HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315`
- Origin: `https://github.com/pazfernando/acquiring-simulator.git`
- Model: `nan/deepseek-v4-flash`, no override
- JDK/Maven: Temurin 25.0.5+8-LTS / Apache Maven 3.10.1
- Runtime: inactive

## Delivered files

Main:

- `C4EvidenceVerifier.java` — real JDK Ed25519 verification and binding order
- `C4VerifiedAttestation.java` — checked identity/type/scope result, no payment/receipt flag
- `C4AttestationType.java` — specific issuer/processor/network/uncertainty classification
- `C4PrincipalRole.java` — P5 role set
- `C4TrustedPrincipalKey.java` — public key + role + acquirer mandate registry value
- `C4KeyRegistry.java` — `(environment, principal, keyId)` lookup seam
- `C4AuthenticatedTransportContext.java` — internal authenticated transport context seam
- `C4VerificationViolation.java` / `C4VerificationException.java` — sanitized categories

Changed:

- `C4EvidenceAttestation.java` — profile-aligned RRN/ID/amount/DE38 bounds
- `C4CarrierSchema.java` — uppercase-alphanumeric exact-width and integer-range helpers

Tests:

- `C4EvidenceVerifierTest.java` — 18 real-signature and fail-closed cases
- `C4EvidenceAttestationSchemaTest.java` — 6 bound cases
- `C4EvidenceFixtures.java` — ephemeral in-memory key/signing/transport fixtures
- `C4CarrierParserTest.java` — unchanged, rerun as carrier regression

## Executed result

- Command:
  `mvn -o -Dtest=C4EvidenceVerifierTest,C4EvidenceAttestationSchemaTest,C4CarrierParserTest -DfailIfNoTests=false test`
- `Tests run: 42, Failures: 0, Errors: 0, Skipped: 0`
- Exit `0`; stderr empty; `BUILD SUCCESS`.
- Breakdown: verifier 18, schema 6, carrier regression 18.
- Compile: `test-compile` exit `0`.
- No failing iteration; first targeted run passed.

## Case coverage

Verifier:

1. DIRECT issuer approval with a real ephemeral Ed25519 key pair, valid signature.
2. DIRECT issuer deliberate decline.
3. DIRECT processor rejection before network.
4. DIRECT network possible-issuer uncertainty.
5. RELAY rejected as `RELAY_UNSUPPORTED`.
6. Unknown key rejected.
7. Signature from a different private key rejected.
8. Tampered signed canonical field (amount) rejected.
9. Outer `keyId` tamper rejected at envelope construction.
10. Registry returning a different `keyId` rejected.
11. Network role signing an issuer decision rejected.
12. `ACL_AUTH` signing rejected.
13. Wrong authenticated sender rejected.
14. Wrong authenticated environment rejected.
15. Acquirer mandate mismatch rejected.
16. Candidate body-hash tamper rejected.
17. Candidate canonical-attestation hash tamper rejected.
18. Result has no payment/processor-receipt flag and does not render signature/body/key.

Schema:

1. Uppercase/alphanumeric 12-character RRN accepted.
2. Digit-only RRN still accepted.
3. Lowercase, punctuated, 11- and 13-character RRN rejected.
4. aid/tid/att exactly 64 accepted; 65 rejected per field.
5. `amountMinor` 100 and 999999 accepted; 99 and 1000000 rejected.
6. `authorizationReference` null and `ABC123` accepted; lowercase, 5-char, 7-char and
   punctuated values rejected.

## Cryptographic trust limits

- The Ed25519 signature is verified with the JDK provider over the exact canonical
  attestation bytes. Test keys are ephemeral and in-memory only; no private key is logged,
  written, exported or included here.
- No key custody, rotation, revocation, TLS/mTLS identity mapping, IAM binding, external
  credentials or production provisioning exists.
- No original-wire `instructionSha256` comparison, P6 ISO/business binding, duplicate or
  conflict resolution, or atomic evidence+receipt+version+event admission exists.
- RELAY is not implemented by this slice; it fails explicitly and is not promoted.
- `C4VerifiedAttestation` is not a payment confirmation and not a processor receipt.

## Integrity and hygiene

- `source-before.sha256` / `source-after.sha256`: 212 → 224 inputs.
- `source-diff.txt`: only 12 new files and the two intended schema edits; no other
  pre-existing source, vector or contract byte changed.
- `git-status-before.txt` / `git-status-after.txt` / `git-status-final.txt`.
- `test-*.txt`, `test-compile-*.txt`, `test-summary.txt`, toolchain versions,
  `delivered-files.sha256`, `evidence.sha256`.
- No full suite, HTTP, runtime, cloud/credential flow, Git write, publication, deletion,
  specification/vector edit or new dependency. P1 and G6 remain open; independent
  Security review and human acceptance remain pending.
