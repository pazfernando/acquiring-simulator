# Evidence manifest — P2b5c

Directory: `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/evidence/p2b5c-20261120T221345Z/`

| Artifact | Purpose |
| --- | --- |
| `BINDING.txt` | model, repository, branch, HEAD, origin, toolchain, scope |
| `head.txt`, `origin.txt` | captured Git binding |
| `git-status-before.txt`, `git-status-after.txt`, `git-status-diff.txt` | unchanged working-tree shape (diff exit 0) |
| `hashes-frozen-pre-run.sha256` | SHA-256 of 242 `src/main/java`, `src/test/java` and `pom.xml` inputs before the targeted run |
| `hashes-after-run.sha256` | the same 242 inputs after the run |
| `hashes-diff.txt` | empty diff (exit 0) |
| `delivered-files.sha256` | the five delivered source/test files, recomputable |
| `test-stdout.txt`, `test-stderr.txt`, `test-exit.txt` | actual targeted invocation streams and exit `0` |
| `surefire-report.txt`, `surefire-report.xml` | class report, 15/15 |
| `RESULTS.md`, `PROGRESS.md` | actual results and slice status |
| `evidence.sha256` | self-verification of this directory |

Not captured and not claimed: a pre-edit baseline of the two pre-existing untracked source files,
a full-suite run, any AWS/cloud effect, late/HTTP/runtime behavior.
