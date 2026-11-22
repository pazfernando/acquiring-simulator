# T3 prerequisite evidence manifest — AWS response-timeout default

Local construction evidence only. Not independent QA, Security review, human acceptance, runtime
activation or deployment.

| File | Purpose |
| --- | --- |
| `RESULTS.md` | Defect, correction, behaviour table, tests actually run, freeze, runtime state |
| `PROGRESS.md` | Short chronological slice record |
| `BINDING.txt` | Repository, expected HEAD, branch, origin, model, toolchain, runtime state |
| `head-before.txt`, `head-after.txt`, `branch.txt`, `origin.txt` | Checkout identity; HEAD unchanged |
| `git-status-before.txt`, `git-status-after.txt`, `git-status-final.txt` | Freeze-point and post-run worktree snapshots; all identical (104 entries) |
| `slice-changes.txt` | Exact source paths touched by this slice |
| `freeze-filelist.txt` | The 265 frozen build inputs (`pom.xml` + all of `src`) |
| `hashes-before.sha256`, `hashes-after.sha256`, `hashes.diff`, `hashes-diff-exit.txt` | SHA-256 freeze; diff empty |
| `hashes-verify.txt` | `sha256sum -c` over the pre-run list; 265/265 `OK` |
| `delivered-files.sha256` | Hashes of the two main-source files and the new test |
| `prefix-probe-exit.txt`, `prefix-probe-stdout.txt`, `prefix-probe-stderr.txt` | **Failed** artifact: pre-fix default 2000 ms vs approved 30000 ms, exit `1` |
| `postfix-probe-exit.txt`, `postfix-probe-stdout.txt`, `postfix-probe-stderr.txt` | **Pass** artifact: compiled seam resolves 30 s, exit `0` |
| `ResponseTimeoutDefaultProbe.java`, `PostFixDefaultProbe.java` | The two probes (no AWS call) |
| `test-command.txt` | Exact single focused command |
| `focused-stdout.txt`, `focused-stderr.txt`, `focused-exit.txt` | **Pass** artifact: unique Maven output, exit `0` |
| `focused-summary.txt` | Aggregate `45 tests / 0 failures / 0 errors / 0 skipped` |
| `TEST-*.xml`, `*.txt` (Surefire copies) | Raw reports for the four executed classes |
| `evidence.sha256` | Hash list of this evidence directory |

## Bound result

`AwsRuntime.processor()` no longer defaults to 2000 ms. Absent `RESPONSE_TIMEOUT_MS` → 30 s;
valid explicit positive override preserved; blank/malformed/non-positive fails closed at cold
start. Focused command exit `0`, 45/45 green. 265/265 frozen inputs byte-identical before and
after; HEAD `cfc456f4…` unchanged; worktree unchanged by the run.

## Trust limits

The freeze point is after the edits, so it proves tested-bytes-equal-saved-bytes, not a
pre-edit baseline. Behaviour is proven for the configuration seam only: no AWS invocation, no
Lambda cold start, no DynamoDB, no deployed IAM, no deadline persistence on the atomic path and
no end-to-end authorization. `AwsRuntime`'s atomic path remains inactive. T2, T3 remainder,
independent QA, Security review and human acceptance stay open.
