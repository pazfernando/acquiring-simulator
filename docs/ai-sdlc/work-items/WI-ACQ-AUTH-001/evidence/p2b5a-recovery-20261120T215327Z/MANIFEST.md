# Evidence manifest — P2b5a bounded recovery

Attempt directory: `docs/ai-sdlc/work-items/WI-ACQ-AUTH-001/evidence/p2b5a-recovery-20261120T215327Z`
Started (UTC): 2026-11-20T21:53:27Z
Test finished (UTC): 2026-11-20T21:53:53Z
Manifest written (UTC): 2026-11-20T21:54:21Z
Model: DeepSeek V4 Flash (`nan:deepseek-v4-flash`)
Binding: branch `feat/wi-acq-auth-001`, HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`,
`/home/pazfernando/projects/acquiring-simulator`, default configuration, no override.

## Status

GREEN for the single targeted test:
`EvidenceReceiptAtomicIntegrationTest` — Tests run 4, Failures 0, Errors 0, Skipped 0, exit 0,
`BUILD SUCCESS`, empty stderr, real recompilation of 173 main + 63 test sources.

## Inventory

| File | Meaning |
| --- | --- |
| `BINDING.txt` | model, binding, HEAD, scope, prior-attempt state |
| `head.txt`, `branch.txt` | exact binding identifiers |
| `toolchain.txt` | Java 25.0.5, Maven 3.10.1, Docker 29.1.3 |
| `test-command.txt` | exact command executed |
| `test-stdout.txt`, `test-stderr.txt`, `test-exit.txt` | unique next-attempt run capture (exit 0) |
| `surefire-report.xml`, `surefire-report.txt` | Surefire report for this run |
| `inputs-before.sha256` | sha256 of all 257 frozen input paths after final edits, before the run |
| `inputs-after.sha256` | same paths immediately after the run; byte-identical to before |
| `source-excerpt.java` | the changed read seam, verbatim |
| `initial-failure-reference/` | read-only copies of the preserved prior failing run (originals untouched) |
| `git-status-before.txt`, `git-status-after.txt` | working tree state; no git write performed |
| `RESULTS.md` | classification, fix, before/after, atomic items, boundary, next |
| `PROGRESS.md` | progress row with actual results |
| `evidence.sha256` | digests of the run evidence and input-hash files |

## Input integrity

- `inputs-before.sha256` = `inputs-after.sha256`
  (sha256 `2b547bd43d91eee6d88f7e1e34302732c44b141b2506b7973289c74fd24474d9`).
- No input file (sources, vectors, fixtures, pom) changed during the run.
- The prior failure under `p2b5a-20261120T214745Z` was read and copied, never modified.

## Evidence digests

See `evidence.sha256` for: inputs-before, inputs-after, surefire-report.xml, test-stdout,
test-stderr, test-exit.

## Not covered

Full suite, C4 regression, feature work, HTTP/service/runtime wiring, credentials, publication,
cloud effects, deletions, specification changes, capacity defaults.
