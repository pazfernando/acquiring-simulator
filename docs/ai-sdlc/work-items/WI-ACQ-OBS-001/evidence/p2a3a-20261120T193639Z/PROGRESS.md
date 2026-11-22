# P2a3a progress — P4 matrix verified; finalization record

Date: 2026-11-20 America/Bogota. Work item `WI-ACQ-OBS-001`, protocol P2 slice.
Owner: Fernando Paz. Actor: implementation session on the bound checkout.

## State

**IMPLEMENTED + LOCALLY VERIFIED + DOCUMENTED.** This turn is
documentation-only: no source edit, no test rerun, no research, no feature.

- Branch `feat/wi-acq-auth-001`, HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`,
  configured model `nan/deepseek-v4-flash` (no override). Working tree preserved.
- Added `Iso8583P4MatrixValidator` / `…Decision` / `…Exception` and
  `Iso8583P4MatrixValidatorTest`.
- Final recorded result: matrix class **62/62** and protocol regression
  **316/316**, 0 failures/errors/skips, exit 0; hash diff exit 0; git-status
  diff exit 0; oracle recheck exit 0.
- Evidence: `evidence/p2a3a-20261120T193639Z/` (`EVIDENCE-MANIFEST.md`,
  `BINDING.txt`, `source.sha256`, `oracle.sha256`, `hashes-*`, `git-status-*`,
  attempt and final stdout/stderr/exit files).

## What is accepted, and what is not

The accepted scope is **syntactic P4 matrix eligibility** for one claimed
`(actor, stage, DE39, result, reason)` against the eleven approved rows. That is
**not** authentication, issuer truth, payment admission, evidence trust,
DE38/instruction binding or durable receipt.

Five P4-negative fixtures and exact first-failure rules:
`N-00-network`/`N-05-processor` → `ACTOR_STAGE_NOT_PERMITTED` (`stage`);
`N-30-issuer`/`N-91-issuer` → `DE39_NOT_PERMITTED_FOR_STAGE` (`de39`);
`N-unknown-code` → `DE39_UNSUPPORTED` (`de39`).

## Snapshot ↔ result

`hashes-before-final` = `hashes-after` (169 files, empty diff). The earlier
`hashes-before` differs only at the test file (`04dc119e…` → `849f21be…`), so
62/316 are the `849f21be` snapshot; 29/283 are the superseded `04dc119e`
snapshot. 62 covers the matrix class only; 316 covers six protocol classes.
No full-suite or full-codec claim.

No failing attempt is recorded in this slice; the only "failure" was the
coordinator return channel, not the build/tests.

## Remaining layers

Instruction/context + DE38 authorization-reference binding over a trusted-record
seam; C4 attestation/carriage; domain Gregorian/DE7/amount/currency; conflict
quarantine; response packer; 826 admission; HTTP/runtime wiring.

## Trust limitation

Local implementation and JUnit evidence only. Not independent QA, Security
review or human acceptance. No invented authentication or financial rule.
