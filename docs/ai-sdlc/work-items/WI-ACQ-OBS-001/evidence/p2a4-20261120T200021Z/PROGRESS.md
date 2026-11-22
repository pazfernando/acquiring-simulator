# P2a4 progress — bounded pure domain validator

Status: IMPLEMENTED AND LOCALLY VERIFIED (targeted + one protocol regression).
No production caller, registry, freshness, 826 admission, packer, C4, HTTP or
runtime wiring. Not QA, Security review or acceptance.

- Binding: repo `/home/pazfernando/projects/acquiring-simulator`, branch
  `feat/wi-acq-auth-001`, HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`,
  origin `github.com/pazfernando/acquiring-simulator.git`, model
  `nan/deepseek-v4-flash` with no override.
- Deliverable: one pure `Iso8583DomainValidator` (+ exception/decision) applying
  approved order: DE3/DE22/DE25 profile codes → DE4 `100..999999` → DE49 `840`
  → strict Gregorian `sent` → DE7 projection of `sent`.
- Response DE7 follows the echoed original request `sent`; response `at` is not
  validated and is never used as `sent`. No nearest-year, rounding, truncation,
  padding or normalization. Year zero rejects; leap/century rules are
  deterministic.
- Ordering: structural scanner/DE48 schema upstream; domain before instruction
  binding; amount before date before binding. The P2a3c equality validator is
  unchanged and separate.
- Results: targeted 23/23, exit 0; `Iso8583*Test` 354/354, exit 0 (331 prior +
  23 new); stderr empty. One test-helper error is preserved and was fixed; no
  production change was needed.
- Hashes: 178 before → 183 after, only the five new files added; no pre-existing
  input changed. Oracle recheck matches `review-manifest.json`. HEAD unchanged.
- Fixtures: 8 domain negatives (6 request + 2 response) and 6 positives from
  actual `hex`, plus unit boundaries/leap/year-change/invalid-time cases.

Remaining handoff: freshness and 826 admission, registry/mandate resolution,
response packer, C4 carriage, HTTP/runtime and full P5. P1 and G6 stay open.
