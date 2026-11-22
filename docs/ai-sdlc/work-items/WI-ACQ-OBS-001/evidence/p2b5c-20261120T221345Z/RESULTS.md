# P2b5c results — durable signed financial-evidence conflict/quarantine

## Binding

Branch `feat/wi-acq-auth-001`, HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`, origin
`https://github.com/pazfernando/acquiring-simulator.git`, configured default configuration and
model `nan/deepseek-v4-flash`, no override. Temurin JDK 25.0.5 / Maven 3.10.1 offline, absolute
paths. Gateway remains unwired; runtime inactive; P1 and G6 Security pending.

## Delivered change

One durable, sanitized quarantine marker for a signed financial-evidence conflict, written only
after trusted-context authorization, real C4 verification and real signed-P5/P6 binding.

| File | SHA-256 |
| --- | --- |
| `domain/event/SignedEvidenceConflict.java` (new) | `627473835843acf15fbe790a8cc0b4c70aaae2a41b862d15864f3e577d949c95` |
| `adapter/aws/SignedEvidenceReceiptGateway.java` | `8e5dfd0f3abe76b68a9b872942defd36988024d37913bf2cfd214f57e156d034` |
| `adapter/aws/DynamoAuthorizationRepository.java` | `83aab24d06bec6e20a2cce14737914ba442035c9dd07627c4416639edcd15a37` |
| `adapter/aws/EvidenceReceiptCriticalComposer.java` (doc only) | `db021deedfd6667c64bb66d5951afce173fb3647b5a9cb7f81d9e2555b87b242` |
| `channel/SignedEvidenceReceiptGatewayIntegrationTest.java` | `68ef2c23147eebec1e5ca7c85672f0f23db42ad34b30e40bc91706ebe85b9881` |

- Identity is exactly the immutable `(environmentId, attestingPrincipal, evidenceId)` triple, the
  same triple as the receipt; the marker is a separate `EVIDQ#…#QUARANTINE` row, so it can never be
  mistaken for, or overwrite, the `EVID#…#RECEIPT` row.
- Two approved conflict classes only: `REUSED_IDENTITY_DIFFERENT_CONTENT` (same id, different
  canonical content hash) and `CONTRADICTORY_DETERMINED_OUTCOME` (different signed id contradicting
  a determined record).
- Metadata only: identity triple, candidate canonical attestation SHA-256, optional established
  canonical hash, established authorization/acquirer reference and a safe reason. No raw proof,
  signature, second payload, raw ISO octets, log body or secret.
- The conditional `attribute_not_exists(pk)` put keeps the first conflicting witness. A repeated
  conflict is a silent no-op: no business version, source sequence, critical slot or original
  receipt changes.
- A quarantine-persistence failure is swallowed after the conflict is classified, so it cannot
  delete or change the established outcome; only the marker is absent.

## Run — targeted affected class only

Command: `mvn -o -Dtest=SignedEvidenceReceiptGatewayIntegrationTest test`
Result: `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`, exit `0`, stderr empty,
`BUILD SUCCESS`. The five prior P2b5b cases are unchanged and still pass; ten new/expanded cases
cover both conflict classes, the stable duplicate marker, unauthorized no-write and persistence
failure.

New assertions prove:

1. Real signed, verified changed identity content: `EF:V02-approved-response` commits EVID-001;
   `EF:V10-actual-DE38` reuses EVID-001 with a different canonical hash → `CONTENT_CONFLICT` with a
   durable `REUSED_IDENTITY_DIFFERENT_CONTENT` marker; original receipt, state `APPROVED`, version
   `2/3`, three events, HEAD 3 and three slots are preserved. The marker row has exactly the
   allowlisted attributes and 64-character lowercase hashes.
2. Separately different id with a contradictory outcome: `EF:N-conflicting-confirmation`
   (EVID-CONFLICT, DECLINED) against the established `AUTH-001` approval → `CONTENT_CONFLICT` with
   a durable `CONTRADICTORY_DETERMINED_OUTCOME` marker; no DECLINED receipt or event, original
   confirmation and version untouched.
3. Duplicate flag stable: a second, different conflicting candidate under the same identity leaves
   one marker carrying the first conflicting witness.
4. Unauthorized context: refused before any read/write; no marker row, no receipt/event/slot change.
5. Quarantine-persistence failure: still `CONTENT_CONFLICT`, original receipt/state/version/events
   preserved, no marker; no financial resolution, resend or reversal occurs.

## Preservation

`hashes-frozen-pre-run.sha256` and `hashes-after-run.sha256` over 242 `src/main/java`,
`src/test/java` and `pom.xml` inputs are identical (diff exit `0`). `git-status-before.txt` and
`git-status-after.txt` are identical (diff exit `0`). HEAD unchanged.

## Not covered

No full suite or regression sweep was run (affected class only, as scoped). No late-admission,
HTTP, service caller, runtime activation, key custody, financial resolution, resend or reversal is
implemented or exercised. The C4 verifier/binder are the real ones, but the test keys are ephemeral
and in-memory. Local construction evidence only — not independent QA, Security review, human
acceptance, business admission, payment confirmation or deployment.
