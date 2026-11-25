# T3 atomic wiring — manifest, 2026-11-22

Self-describing inventory of this evidence directory and the exact inputs the verification run
covered. Full per-file hashes of everything except this file are in
[`verify-evidence-sha256.txt`](verify-evidence-sha256.txt) (60 entries; re-runnable with
`sha256sum -c` after renaming or with `sha256sum --check verify-evidence-sha256.txt`).

## 1. Run identity

| Item | Value |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` |
| Runtime | local JVM, OpenJDK 25.0.5, Maven 3.10.1, offline `-o` |
| Model observed | `nan/deepseek-v4-flash` (session model, no override) |
| Command | one `mvn -o -B test -Dtest=...` invocation, 23 classes |
| Outcome | **192 tests / 0 failures / 0 errors / 0 skipped / exit 0 / BUILD SUCCESS / 01:15 min** |
| Full suite | **not run — deferred by instruction** |

## 2. Frozen inputs (292 paths)

| Scope | Count |
| --- | --- |
| `src/main/java/**/*.java` | 211 |
| `src/test/java/**/*.java` | 72 |
| `src/main/resources/**` | 8 |
| `pom.xml` | 1 |
| **Total** | **292** |

- `verify-freeze-filelist.txt` — sorted path list (sha256
  `4b1ab96c8ffa2ae8c6a6a999bfcff50389cba41b2a947b12f2b0ffb2d3ae347`).
- `verify-hashes-before.sha256` / `verify-hashes-after.sha256` — identical
  (sha256 `86c4551885fbad3b8e776016dfb8b027ad150db7875518100222af7ae2ab6268`).
- `verify-freeze-result.txt` — `hashdiff_exit=0`, `sha256sum_check_exit=0`,
  `git_status_diff_exit=0`, `head_unchanged=yes`.

**Freeze limitation:** post-edit baseline. The freeze was taken after `AwsRuntime.java` was modified
and after the new test file existed; it proves the run mutated nothing, not that these are pre-edit
bytes. `verify-git-status-before.txt` records `M src/main/.../AwsRuntime.java` and untracked
`src/test/java/com/acquiring/simulator/adapter/`.

Frozen bytes of the two files in the slice:

| Path | SHA-256 |
| --- | --- |
| `src/main/java/com/acquiring/simulator/adapter/aws/AwsRuntime.java` | `23251bf4e9b1674d8b4a4f6ba80b96924a2c9eb22c39dcfcd1116d9544e2a491` |
| `src/test/java/com/acquiring/simulator/adapter/aws/AwsRuntimeAtomicWiringIntegrationTest.java` | `21587f368240ded7a0b2c8a0ec50d3608783ea6ae55c2eda9437dd492bb9171e` |
| `pom.xml` | `b47fb547ab28628b78e4241986638b9379cc4263e1762218192264bb1bc7a93c` |

## 3. Files written by this verification pass

| File | Contents |
| --- | --- |
| `RESULTS.md` | cause, single-helper change, freeze, per-class counts, wired-vs-unproven, direct integration answer |
| `PROGRESS.md` | ordered steps, preconditions, scope discipline, residual items |
| `MANIFEST.md` | this inventory |
| `verify-runtime.txt` | repository, branch, HEAD, path count, Java/Maven, model, freeze scope and limitation |
| `verify-branch.txt`, `verify-head.txt`, `verify-head-after.txt` | binding |
| `verify-freeze-filelist.txt` | 292 frozen paths |
| `verify-hashes-before.sha256`, `verify-hashes-after.sha256` | identical hash lists |
| `verify-hash-diff.txt` | **empty** (0 bytes) |
| `verify-freeze-result.txt` | freeze verdict |
| `verify-git-status-before.txt`, `verify-git-status-after.txt` | identical working-tree snapshots |
| `verify-git-status-diff.txt` | **empty** (0 bytes) |
| `verify-selected-classes.txt` | 24 selected names → 23 distinct classes |
| `verify-run-exit.txt` | `0` |
| `verify-run-stdout.txt` | full Maven output incl. per-class summary and totals |
| `verify-run-stderr.txt` | environment warnings only (Mockito self-attach, Byte Buddy agent, JDK native access) |
| `verify-evidence-sha256.txt` | hashes of every file here except `MANIFEST.md` |

## 4. Inherited fix-run artifacts (preserved, not rewritten)

| File(s) | Meaning |
| --- | --- |
| `fix-note.md` | the fix run's own narrative |
| `fix-exit.txt` = `0`, `fix-stdout.txt`, `fix-stderr.txt` | post-fix run: 10/0/0/0 on the runtime test |
| `fix-red-run1-exit.txt` = `1`, `fix-red-run1-stdout.txt` | pre-fix red run: 10 run, 1 error |
| `fix-AtomicClaimServiceWiringIntegrationTest-*` | 6 tests, exit 0 |
| `fix-DispatchIntentServiceWiringTest-*` | 9 tests, exit 0 |
| `fix-LateEvidenceServiceWiringTest-*` | 11 tests, exit 0 |
| `fix-exit-preflight-127.txt`, `fix-preflight-note.txt` | `mvn` not on PATH first attempt; rerun with explicit JDK/Maven |
| `BINDING.txt`, `branch-freeze-resume.txt`, `freeze-*.txt`, `hashes-*.sha256`, `git-status-*.txt`, `slice-*.txt`, `compile*-*.txt`, `runtime-wiring-*.txt`, `fixed-run-*.txt` | the earlier slice's binding, compile and pre-fix baseline evidence |

## 5. Verification of this manifest

```bash
cd docs/ai-sdlc/work-items/WI-ACQ-OBS-001/evidence/t3-atomic-wiring-20261122
sha256sum -c verify-evidence-sha256.txt   # all OK
sha256sum -c verify-hashes-before.sha256  # 292/292 OK, from repository root
```

`MANIFEST.md` and `verify-evidence-sha256.txt` are mutually referential, so the manifest itself is
not in its own hash list; edit either and the check above will name the file that changed.

## 6. Non-claims

No Git write, stage, commit, push, PR, merge, deploy, release, publication, AWS call, credential
flow, provider mutation, approved-specification edit or invented capacity default occurred. No
onward delegation, no model override. This is developer evidence on one frozen working tree, not
independent QA, Security review or human acceptance.
