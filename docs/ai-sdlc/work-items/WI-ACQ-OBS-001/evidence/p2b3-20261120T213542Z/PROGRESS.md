# P2b3 progress — C4 late-delivery evidence recovery

Status: **partial evidence recovered; no rerun performed.** This is local construction
evidence, not independent QA, Security review, human acceptance, business admission or a
deployment.

## Recovered

- C4 late-delivery implementation files already exist in the working tree:
  `C4LateDeliveryParser`, `C4LateDeliveryVerifier`, `C4LateDeliveryEnvelope`,
  `C4LateDeliveryCandidate`, `C4VerifiedLateDelivery`, `C4LateDeliveryViolation`,
  `C4LateDeliveryException` and `C4LateDeliveryTest`.
- Existing Surefire artifacts for a single targeted run at `2026-11-20T16:34:34`
  (`test=C4LateDeliveryTest,C4EvidenceVerifierTest,C4CarrierParserTest,C4EvidenceAttestationSchemaTest`):
  - `C4LateDeliveryTest`: 23 run / 0 failures / 0 errors / 0 skipped / 0.503 s
  - `C4EvidenceVerifierTest`: 33 / 0 / 0 / 0 / 0.173 s
  - `C4CarrierParserTest`: 18 / 0 / 0 / 0 / 0.086 s
  - `C4EvidenceAttestationSchemaTest`: 6 / 0 / 0 / 0 / 0.031 s
  - Aggregate: **80 run, 0 failures, 0 errors, 0 skipped**.
- A capture **does** exist outside the repo at `/tmp/opencode/p2b3-run/`: stdout
  (`BUILD SUCCESS`, finished 16:34:34), empty stderr and exit code `0`. It is copied
  byte-for-byte into `test-stdout.txt`, `test-stderr.txt`, `test-exit.txt`, and the
  copies' hashes equal the capture originals (`capture-origin-sha256.txt`). stdout
  counts/timings match the Surefire reports exactly.

## Coverage actually exercised by the 23 late-delivery cases

- direct real Ed25519 verification and relay verification of both real signatures;
- late-envelope size bounds enforced before parsing (oversized envelope rejected);
- unknown envelope/evidence keys, duplicate JSON keys, folded headers, non-canonical
  spacing, non-canonical base64 and compressed payloads rejected;
- response-body hash mismatch rejected before verification;
- sender permission: wrong sender without diagnostic permission fails closed; explicit
  scoped diagnostic-forward permission accepted without outcome/durability authority;
- delivery receipt id alone grants no decision or durability authority;
- approved late shape fixture parses only as an unverified candidate; approved late
  missing-evidence fixture is rejected;
- parser/verifier expose no payment or worker surface.

No private key, raw proof body, signature material or secret is copied into evidence.

## Limits

- No attested pre-run hash exists; current-byte hashes are captured during recovery.
  Correspondence between the current source bytes and the bytes compiled for the 16:34
  run is inferred from mtimes and absence of later edits, not cryptographically attested.
- The run is targeted, not a full regression; `BUILD SUCCESS`/exit `0` apply to this
  four-class invocation only, not to the full suite.
- Instruction-hash binding to a persisted original wire instruction, P6 ISO parse,
  duplicate/conflict resolution and atomic storage admission remain unimplemented.

## Hygiene

Read-only capture. No test rerun, source edit, new feature, full regression, HTTP or
credential flow, git write, publication, cloud call, deletion or spec edit. Pre-existing
tracked and untracked work preserved. Runtime inactive. P1/G6 remain open.

## Next smallest slice

P5 full signed field binding against the persisted instruction (per
`implementation-progress.md` of this work item): bind the late envelope to the persisted
instruction SHA-256 and the P5 signed schema/role/mandate, still local and without storage
admission, HTTP or runtime.
