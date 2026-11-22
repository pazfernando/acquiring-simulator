# P2a3c progress — complete original-instruction echo binding

Status: IMPLEMENTED AND LOCALLY VERIFIED (targeted + protocol regression). No
production caller, packer, admission or runtime wiring. This is not QA,
Security review or acceptance.

- Binding: repo `/home/pazfernando/projects/acquiring-simulator`, branch
  `feat/wi-acq-auth-001`, HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`,
  origin `github.com/pazfernando/acquiring-simulator.git`, model
  `nan/deepseek-v4-flash` with no override.
- Completed echo: DE48 `cid/aid/tid/att/acq/mid/term/pay/gen` (prior slice) plus
  `fixture/cvm/crypto/risk/sent/prior` and physical
  DE3/4/7/11/22/25/32/37/41/42/49. Trusted original-wire hash and DE38 binding
  are unchanged.
- First-failure order: P4 upstream → trusted hash → every instruction echo →
  DE38. `N-mismatch-amount` (DE4) precedes an invalid DE38 in the explicit test.
- Byte oracle: five approved binding negatives and two positives
  (`V10-actual-DE38`, `V02-approved-response`) run through scanner → DE48
  schema → P4 → binding using the exact approved octets and the explicit
  evidence fixture for the trusted reference. No auth flag exists.
- Results: targeted 15/15, exit 0; `Iso8583*Test` 331/331, exit 0; empty stderr.
  One compile failure (malformed test helper calls) is preserved and was fixed.
  Hashes before/after tests identical (178 inputs); git status identical.
- Deferred: full P5 environment/principal/evidence identity/attestation
  signature/`responseSha256`; domain amount/currency/date/DE7; registry/mandate;
  freshness/826; packer; C4; HTTP; runtime. P1 and G6 remain open.

Next domain-validation slice: Gregorian `sent`/DE7 consistency, amount/currency
profile, then registry/calendar failures (`N-response-DE7`,
`N-response-invalid-date`) — still equality/format-first, no admission.
