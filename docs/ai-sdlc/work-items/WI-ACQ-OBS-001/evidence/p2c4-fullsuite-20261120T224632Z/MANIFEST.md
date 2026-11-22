# P2c4 evidence manifest — single full offline suite verification

Local construction evidence only. Not independent QA, Security review, human acceptance,
runtime activation or deployment.

| File | Purpose |
| --- | --- |
| `RESULTS.md` | Actual binding, command, aggregate, per-package rollup, correspondence, limits, C2 open decision |
| `PROGRESS.md` | Short chronological slice record |
| `BINDING.txt` | Repository, branch, HEAD, origin, model, toolchain, offline mode, runtime state |
| `binding-head.txt`, `origin.txt`, `head-before.txt`, `head-after.txt` | Checkout identity |
| `java-version.txt`, `mvn-version.txt` | Toolchain versions |
| `started-utc.txt`, `run-start-utc.txt`, `run-end-utc.txt` | UTC chronology |
| `git-status-before.txt`, `git-status-after.txt`, `git-status.diff` | Working-tree state; diff empty |
| `git-status-final.txt` | Final snapshot; adds two later coordinator-authored untracked files outside this slice |
| `freeze-filelist.txt` | The 282 frozen inputs |
| `hashes-before.sha256`, `hashes-after.sha256`, `hashes.diff` | SHA-256 freeze and post-run correspondence; diff empty |
| `hashes-verify.txt` | `sha256sum -c` over the pre-run list, 282/282 OK |
| `test-command.txt` | Exact single command |
| `test-stdout.txt`, `test-stderr.txt`, `test-exit.txt` | Unique captured Maven output and exit `0` |
| `surefire-before-list.txt` | Pre-existing report listing (not deleted) |
| `surefire-summary.tsv` | Per-class tests/failures/errors/skipped/flakes |
| `surefire-by-package.tsv` | Per-package rollup |
| `surefire-freshness.txt` | All 63 XML inside the run window |
| `TEST-*.xml`, `*.txt` (Surefire copies) | Copied run reports, hash-identical to `target/surefire-reports` |
| `evidence.sha256` | Hash list of this evidence directory |

## Bound result

Single `mvn -o -B test` run: exit `0`, `BUILD SUCCESS`, **63 classes / 959 tests / 0 failures /
0 errors / 0 skipped**. HEAD `cfc456f4…` unchanged; 282/282 frozen inputs byte-identical;
working tree unchanged. No source edit, no rerun, no Git write, no cloud call.

## Trust limits

Local only: SQLite, Testcontainers PostgreSQL, DynamoDB Local and in-process fixtures. No AWS
service behavior, deployed IAM, TLS/mTLS or SigV4 principal binding, key custody, API Gateway
wire behavior, JWT issuer/WebSocket, ADOT or end-to-end application verification. P1 G6,
C2 principal mapping, Security review, independent QA and human acceptance remain open.
