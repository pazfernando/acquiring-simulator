# P2b2b evidence manifest — RELAY Ed25519 verification extension

## Binding

- Work item: `WI-ACQ-OBS-001` (continues the P2b1/P2b2a carrier sequence; code is in the
  shared `channel` package)
- Branch: `feat/wi-acq-auth-001`
- HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315`
- Origin: `https://github.com/pazfernando/acquiring-simulator.git`
- Model: `nan/deepseek-v4-flash`, no override
- JDK/Maven: Temurin 25.0.5+8-LTS / Apache Maven 3.10.1
- Runtime: inactive

## Prior phase recorded, not rerun

- P2b2a evidence: `../p2b2a-20261120T211750Z/`
- P2b2a saved result: 42/42 targeted green (18 verifier + 6 schema + 18 carrier), exit 0.
- The P2b2a completion progress was absent from `implementation-progress.md`; §43 records
  it without rerunning, recreating or editing that evidence. Its before-edit hash list is
  not treated as this phase's frozen run baseline.

## Delivered files

Main:

- `C4EvidenceVerifier.java` — DIRECT/RELAY dispatch; real JDK Ed25519 verification of the
  inner issuer signature and the network relay countersignature; relay key/role/mandate/
  environment/sender/hash binding; inner `ISSUER_SIM`/issuer-row constraint.
- `C4VerifiedAttestation.java` — explicit DIRECT or RELAY scope plus a nullable
  `RelayBinding` (principal, keyId, receiptId, deliveredAt); no boolean verified flag.
- `C4VerificationViolation.java` — removed obsolete `RELAY_UNSUPPORTED`; added
  `RELAY_BINDING`.
- `C4KeyRegistry.java` — DIRECT/RELAY key registry documentation.
- `C4PrincipalRole.java` — `NETWORK_SIM` may countersign relayed issuer evidence.
- `C4RelayAttestation.java` — verifier boundary documentation.
- `C4CarrierCandidate.java` — verifier boundary documentation.

Tests:

- `C4EvidenceVerifierTest.java` — 16 new RELAY cases; the existing 17 DIRECT cases are
  preserved and the obsolete RELAY-unsupported case is replaced.
- `C4EvidenceFixtures.java` — real ephemeral issuer+network signing helpers for the relay
  chain; no private key is persisted or rendered.

Hashes: `delivered-files.sha256` (9 files).

## Executed result

- Command: `mvn -o -Dtest=C4EvidenceVerifierTest,C4CarrierParserTest test`
- No `-DfailIfNoTests=false`; the selectors matched real test classes.
- `Tests run: 51, Failures: 0, Errors: 0, Skipped: 0`
- Breakdown: verifier 33 (17 DIRECT + 16 RELAY), carrier regression 18.
- Exit `0`; stderr empty; `BUILD SUCCESS`.
- No failing iteration in this phase; the single targeted run passed. A pre-freeze
  `test-compile` also exited `0`; the run start reported classes up to date, and the frozen
  source hash was unchanged before and after the run.

## Case coverage

RELAY:

1. Valid issuer-approval chain with real ephemeral issuer and network Ed25519 key pairs.
2. Valid issuer-decline chain.
3. Inner issuer-signature alteration → `BAD_SIGNATURE` / `issuerSignature`.
4. Relay countersignature alteration → `BAD_SIGNATURE` / `relaySignature`.
5. Network signature substituted for the issuer signature → `BAD_SIGNATURE` /
   `issuerSignature`.
6. Inner signer configured as `NETWORK_SIM` → `ROLE_NOT_PERMITTED` / `issuerRole`.
7. Relay signer configured as `ISSUER_SIM` → `ROLE_NOT_PERMITTED` / `relayRole`.
8. Relay `responseSha256` mismatch → `RELAY_BINDING` / `relayResponseSha256`.
9. Relay `attestationSha256` mismatch → `RELAY_BINDING` / `relayAttestationSha256`.
10. Wrong authenticated sender → `AUTHENTICATED_SENDER` / `relayPrincipal`.
11. Unknown relay key → `UNKNOWN_KEY` / `relayKeyId`.
12. Registry returns a different relay key → `KEY_BINDING` / `relayKeyId`.
13. Wrong authenticated environment → `ENVIRONMENT_BINDING` / `environmentId`.
14. Relay acquirer mandate mismatch → `MANDATE_BINDING` / `relayMandate`.
15. Canonical relay signed object excludes `signature` and binds receipt, delivery time,
    response hash and inner-attestation hash.
16. Inner attestation, inner signature and relay signature remain byte-identical after
    verification; the result renders only the RELAY scope and no signature material.

DIRECT: the 17 pre-existing cases (issuer approval/decline, processor rejection, network
uncertainty, unknown key, wrong private key, tampered canonical field, outer/registry key
binding, role restrictions, sender, environment, mandate, candidate body/attestation hash
tampering, and no payment/receipt flag) remain in the same class.

## Cryptographic trust limits

- Both Ed25519 signatures are verified with the JDK provider over the exact canonical bytes:
  the issuer attestation bytes and the relay object bytes excluding `signature`.
- Test keys are ephemeral in-memory `KeyPairGenerator` output; no private key is logged,
  written, exported or included here.
- No key custody, rotation, revocation, TLS/mTLS identity mapping, IAM binding, external
  credentials or production provisioning exists.
- No original-wire `instructionSha256` comparison, P6 ISO/business binding, duplicate or
  conflict resolution, atomic evidence+receipt+version+event admission, late-delivery
  envelope, HTTP adapter or runtime wiring exists.
- `C4VerifiedAttestation` is not a payment confirmation and not a processor receipt. It has
  no boolean `valid`/`verified`/`trusted` flag.
- The authenticated transport context remains an internal test seam; no actual IAM, TLS or
  HTTP caller is claimed.

## Integrity and hygiene

- `source-frozen-pre-run.sha256` / `source-after-run.sha256`: **229/229 byte-identical**
  over all `src/main` and `src/test` files (including `src/main/resources`), `pom.xml` and
  the three C4 oracles (`carrier-vectors.json`, `channel-contract.md`,
  `iso8583-profile.md`).
- `source-diff.txt` is empty; `source-diff-exit.txt` is `0`; `source-stability.txt` records
  the identical freeze.
- `git-status-before.txt`, `git-status-after.txt`, `git-status-final.txt`,
  `head-before.txt`, `head-after.txt`, `head-final.txt`.
- `test-command.txt`, `test-stdout.txt`, `test-stderr.txt`, `test-exit.txt`,
  `test-summary.txt`, `run-start-utc.txt`, `run-end-utc.txt`.
- `java-version.txt`, `javac-version.txt`, `mvn-version.txt`.
- `delivered-files.sha256`, `evidence.sha256`.
- No full suite, HTTP, runtime, cloud/credential flow, Git write, publication, deletion,
  specification/vector edit or new dependency. P1 and G6 remain open; independent Security
  review, QA and human acceptance remain pending. This manifest is not self-approval.

## Next

P6 original-instruction binding plus the C4 late-delivery envelope and instruction-binding
seam. Storage admission, HTTP/service caller, key custody and runtime remain separate.
