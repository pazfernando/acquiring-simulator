# P2a6 progress — pure NEW-request freshness validator

Status: **IMPLEMENTED AND LOCALLY VERIFIED** for the bounded freshness slice.
Evidence: `EVIDENCE-MANIFEST.md`, `BINDING.txt`, before/after hashes, git status,
targeted stdout/stderr/exit.

## Delivered

One pure `Iso8583FreshnessValidator.evaluate(sent, receivedAt, mode)`:

- `NEW_FIRST_DISPATCH`: `|receipt - sent| <= 300 s` → `FRESH`; otherwise
  `STALE` with failure label `ADMISSION_FRESHNESS`.
- `AUTHORIZED_HISTORICAL_REPLAY` / `LATE_EVIDENCE` → `NOT_APPLICABLE` without
  reading the timestamp; persisted original identity is not re-evaluated or
  re-encoded.
- `Iso8583AdmissionMode` is a trusted server-side enum, not a client field.

Exactly 300.000 s is inside; 300.001 s and 301 s are outside. The test uses the
actual `D-stale` hex (301 s before receipt), exact-300/just-outside boundaries,
full-instant cross-year cases with a 2027 server clock, and replay/late
non-application. No extra clock-skew allowance is introduced.

## Actual results

- `Iso8583FreshnessValidatorTest`: **5/5**, exit `0`, stderr empty.
- Main recompile: 121 sources; test recompile: 50 sources.
- Inputs: 177 before → 181 after; changed/removed = 0 (exit `0`); diff exit `1`
  only for the four additions.
- HEAD unchanged: `cfc456f4f469d9558a78fda8ff9b770b00817315`.

## Not covered

826 exchange budget, registry/mandate/route/fixture resolution, P4/binding
admission, response packing, C4 carriage, HTTP route, production caller and
runtime wiring. No domain, authentication or admission claim is made. P1 and G6
stay open. Local evidence only; not QA, Security or acceptance.

## Next slice

Separate 826-byte exchange admission validator (`ADMISSION_RESPONSE_CAPACITY`)
over the same approved corpus, without claim or dispatch.
