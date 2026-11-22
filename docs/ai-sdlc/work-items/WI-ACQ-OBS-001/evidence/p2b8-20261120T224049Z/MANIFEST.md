# P2b8 evidence manifest

Local construction evidence only. Not independent QA, Security review, human acceptance,
runtime activation or deployment.

| File | Purpose |
| --- | --- |
| `RESULTS.md` | Actual binding, commands, outcomes, coverage, trust limits and missing mapping |
| `PROGRESS.md` | Short chronological slice record |
| `BINDING.txt` | Repository, branch, HEAD, origin, model, toolchain, runtime state |
| `branch.txt`, `origin.txt`, `head-before.txt`, `head-after.txt` | Checkout identity |
| `toolchain.txt`, `mvn-version.txt`, `java-version.txt` | Toolchain |
| `git-status-before.txt`, `git-status-after.txt`, `git-status-final.txt` | Working-tree state |
| `hashes-before.sha256` | SHA-256 freeze over 271 pre-existing inputs |
| `hashes-after.sha256` | SHA-256 over 281 post-edit inputs |
| `before-paths.txt`, `after-paths.txt`, `added-files.txt` | Input-set difference; 10 added |
| `existing-files-after.sha256`, `existing-files.diff`, `existing-files.diff-exit.txt` | Proof that every pre-existing file is byte-unchanged |
| `delivered-files.sha256` | Hashes of the 10 new files |
| `compile-stdout.txt`, `compile-stderr.txt`, `compile-exit.txt` | Main compile |
| `test-compile-stdout.txt`, `test-compile-stderr.txt`, `test-compile-exit.txt` | Test compile |
| `targeted-stdout.txt`, `targeted-stderr.txt`, `targeted-exit.txt` | Targeted 5-test run |
| `regression-stdout.txt`, `regression-stderr.txt`, `regression-exit.txt` | C4 regression run |
| `test-command.txt` | Exact targeted command |
| `TEST-…HttpApiV2InternalRouteTest.xml`, `…txt` | Surefire report, 5/5 |
| `TEST-…C4CarrierParserTest.xml`, `…txt` | Surefire report, 18/18 |
| `TEST-…C4LateDeliveryTest.xml`, `…txt` | Surefire report, 23/23 |
| `evidence.sha256` | Hash list of this evidence directory |

## Bound inputs

- Branch `feat/wi-acq-auth-001`, HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`.
- Origin `https://github.com/pazfernando/acquiring-simulator.git`.
- Model `nan/deepseek-v4-flash`, configured default, no override.
- Temurin JDK 25.0.5, Maven 3.10.1, offline mode.
- `AwsRuntime` inactive; no HTTP route, deployment or AWS effect.
