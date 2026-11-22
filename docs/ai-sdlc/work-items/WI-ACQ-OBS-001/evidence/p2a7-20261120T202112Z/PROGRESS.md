# P2a7 progress — pure NEW-exchange 826-byte admission budget

Status: **IMPLEMENTED AND LOCALLY VERIFIED** for the bounded byte-budget slice.
Evidence: `EVIDENCE-MANIFEST.md`, `BINDING.txt`,
`hashes-before`/`hashes-frozen-pre-run`/`hashes-after`, hash diffs, git status,
targeted and protocol stdout/stderr/exit, `toolchain-version.txt`.

## Delivered

One pure `Iso8583ExchangeBudgetValidator.evaluate(requestDe48Bytes, mode)`:

- `NEW_FIRST_DISPATCH`: `<= 826` → `ACCEPTED`; `827..999` → `REJECTED` with
  `ADMISSION_RESPONSE_CAPACITY`; outside structural `1..999` fails closed.
- `AUTHORIZED_HISTORICAL_REPLAY` / `LATE_EVIDENCE` → `NOT_APPLICABLE` without
  reading, validating, re-encoding or resending a fresh length.
- `Iso8583AdmissionMode` is a trusted server-side enum, not a client field.
- The reserve is derived, not magic: 44 tail-key bytes (from the approved
  response keys) + 51 worst accepted P4 metadata bytes (probed through the P4
  matrix) + 64 evidence bytes + 14 `at` digits = 173; `999 − 173 = 826`.
- Structural packer/scanner still accept 999. A 999-byte response remains
  representable without dropping echoed keys, shortening references or
  truncating evidence.

## Actual results

- `Iso8583ExchangeBudgetValidatorTest`: **7/7**, exit `0`, stderr empty.
- Protocol regression `Iso8583*Test`: **419/419**, exit `0`, stderr empty
  (412 prior + 7 new).
- 11 approved packed vectors exercised: `B-DE48-exact-999`,
  `CAP-request-826`, `CAP-request-827`, `CAP-response-3`, and
  `CAP-over-request-1..7` (each 827 bytes, 17 keys, `prior` intact).
- Main recompile: 123 sources; test recompile: 51 sources.
- Inputs: 181 before → 184 frozen pre-run → 184 after; frozen and after hashes
  identical; existing-vs-before diff empty (three additions only).
- HEAD unchanged: `cfc456f4f469d9558a78fda8ff9b770b00817315`.

## Not covered

Registry/mandate/route/fixture resolution, authentication/attestation,
freshness, P4/binding admission, duplicate/conflict resolution, response
construction beyond structural packing, C4 carriage, HTTP route, production
caller and runtime wiring. No domain, trust or financial admission claim is
made. P1 and G6 stay open. Local evidence only; not QA, Security or acceptance.

## Next slice

ONE pure codec pipeline boundary (scanner → canonical DE48 request → domain →
freshness → 826 budget as a single ordered evaluator returning the first
failure label), still byte-only and claim/dispatch-free; then the ACL boundary
slice (configured principal/role eligibility) as a separate pure seam.
