# T1 traceability slice — evidence manifest

Slice: `t1-traceability-20261120`. Deliverable:
[`requirement-traceability.md`](../../requirement-traceability.md).

Scope: one bounded documentation/evidence slice. No test was created or run. No source, test,
resource, `pom.xml`, specification, vector or contract was edited. No commit, stage, push, provider
mutation, deployment, credential flow or deletion.

## Binding at slice open and close

| Field | Value |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged) |
| Origin | `https://github.com/pazfernando/acquiring-simulator.git` |
| Model | provider `nan`, `deepseek-v4-flash`, configured default, no override |

## Executed evidence this slice reuses (not rerun)

[`../p2c4-fullsuite-20261120T224632Z/RESULTS.md`](../p2c4-fullsuite-20261120T224632Z/RESULTS.md)

- 959 tests / 63 classes / 0 failures / 0 errors / 0 skipped / exit 0.
- 282 frozen inputs byte-identical before and after the run, so the green run exercised exactly the
  sources this matrix describes.
- Per-class counts come from `surefire-summary.tsv`; test method names come from the 63 copied
  Surefire XML files in that directory.

## Files in this directory

| File | Content |
| --- | --- |
| `BINDING.txt` | Binding, model, suite reuse and the concurrent completion-plan update note. |
| `source-hashes.sha256` | Pinned specification, work-item and evidence hashes recomputed at slice open. |
| `git-status-before.txt` | Working-tree state at slice open; all pre-existing modified/untracked work preserved. |
| `git-status-after.txt` | Working-tree state at slice close. Relative to the open snapshot it adds exactly two untracked paths: this slice's deliverable `requirement-traceability.md`, and `reviews/`, which was created concurrently by another session and was **not** written by this slice. Everything else is unchanged. |
| `coverage-counts.txt` | Row counts per status set, recomputed from the deliverable. |
| `runtime-vs-test-only.md` | The concrete evidence for the "runtime path vs test-only construction" split. |
| `delivered-files.sha256` | Hashes of the files this slice wrote. |

## Source-hash verification result

All five commissioned pins matched at slice open:

- ADR `d51bbf650aca74e82351dacf0e45d0f14c992044b9ff246bc7b8b281920b0b39`
- owner-decisions `efef3d2272dd54130b404fae43ec90643bc42c5dd4701cc1b6b4e49d4ea0f18c`
- requirements `49c5d412e3705c320a2f78b190555f49e7e0fa4662cff9329d259f89e2b459df`
- channel-contract `495e1172e76ab9ba81f8a8026e7efec25baa6e4fff0d754c0a4c92bd38ea3bca`
- observation-contract `ca53e384a46d24b3c1119b3e6ba804a9a9d4caac049a6b0c55a40c72842a205a`

The completion plan matched its pin `6e75d527…` at slice open and was appended by the
AI-SDLC Coordinator at `2026-11-20T23:56:35Z` (current `cc522f02…`, 95 lines). Only a narrative
section was added; the T1–T10 table is unchanged. The file was not edited by this slice, and the
change is disclosed in the deliverable rather than silently absorbed.

## Handoff readiness

Usable for an early Security handoff: the matrix states which behaviors are reachable at runtime
versus test-only, and separates C4 signing verification from transport authentication. It does not
contain secret values, account identifiers or credentials.
