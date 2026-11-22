# Evidence manifest — P1b3a current-tree verification (2026-11-20)

Verification-only slice. **No source edit, no feature, no Git write, no publication, no cloud
call, no apply, no credential use, no deletion.** `AwsRuntime` was not activated. One authorized
`mvn -o test` executed against the current tree.

## 1. Binding (verified this turn)

| Item | Value |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `cfc456f4f469d9558a78fda8ff9b770b00817315` |
| Working tree | dirty (application edits unstaged; new untracked sources) |
| Model | provider `nan`, `deepseek-v4-flash`, configured default, no override |
| JDK | Temurin 25.0.5 (`/home/pazfernando/.local/opt/jdk-25`) |
| Maven | Apache Maven 3.10.1 (`/home/pazfernando/.local/opt/maven`) |

Recorded in `binding.txt`. `git status --porcelain` is byte-identical before and after the run
(55 entries) — see `git-status-after.txt`.

## 2. Pre-run freeze

`hashes-before.txt` — 101 files: all of `src/**` (98) plus `pom.xml`, `infra/template.yaml`,
`scripts/render_aws_fixtures.py`. Split into `hashes-src-before.txt` and `hashes-build-before.txt`.

Reuse of `p1b3a-2026-11-20/hashes-current.txt` was **permitted only after verification**, which
passed (`hashes-current-verified.txt`, `hashes-current-diff.txt`):

- 93/93 files covered by `hashes-current.txt` (it covered `src/main/java` + `src/test/java` only)
  are **byte-identical** in the current tree — zero changed, zero missing.
- 5 files were added to the frozen set that the earlier snapshot never hashed (the four
  `application*.properties` plus `db/schema.sql`). They are additions to coverage, not changes.

Pinned digests of the files this work item turns on:

| File | SHA-256 |
| --- | --- |
| `adapter/aws/PossibleSendCriticalComposer.java` | `b9a2579f7fa8fa462cf0f327cf25cc18d4c608eb1bf205f43e8f68e079afeca2` |
| `test/.../DynamoPossibleSendIntegrationTest.java` | `075eccc8d0483547e65a94e915cc7a35db0d2c8cc9c8997b5a6364d6ae4fa714` |
| `pom.xml` | `b47fb547ab28628b78e4241986638b9379cc4263e1762218192264bb1bc7a93c` |

## 3. Run

`mvn-test.log` — command, environment, stdout+stderr, `EXIT_CODE`, and timestamps captured
immediately around the invocation. Total time 33.253 s.

**Environment note (disclosed, not hidden):** two earlier invocations in this turn were attempted
before the toolchain path was resolved and **executed no build or test at all**:

| Attempt | Log | Failure | Build executed |
| --- | --- | --- | --- |
| ENV-01 | `env-01-mvn-not-on-path.log` | exit 127, `mvn: command not found` | no |
| ENV-02 | `env-02-java-home-unset.log` | exit 1, `JAVA_HOME` not defined | no |
| **Run** | `mvn-test.log` | — | **yes, once** |

Both aborted inside the Maven launcher before any compilation. Exactly **one** suite ran against
the frozen tree. Notes: `env-01-note.txt`, `env-02-note.txt`.

## 4. Result — FAILED

| Metric | Value |
| --- | --- |
| Tests run | **154** |
| Failures | 0 |
| Errors | **1** |
| Skipped | 0 |
| Test classes | 18 |
| Exit code | **1** (`BUILD FAILURE`) |

Per-class results: `per-class-results.txt`. Failing test:

`AtomicClaimServiceWiringIntegrationTest.atomicClaimPathCommitsOnceDispatchesOnceAndReportsPersistedCommittedVersion`

`EventValidationException: PROHIBITED_CONTENT at correlationId` — the sanitizer rejected a
generated `CORR-<24 hex>` identifier as a PAN shape. Measured collision rate for that generator:
**1.55%** (3,104 / 200,000). Attempt A passed the same 154 tests with the same failing-path code,
and this test runs with `CriticalWriteComposer.NONE`, so the composer under review is not on the
failing path. Full reasoning and the smallest next fix are in `failure.md`.

**Not claimed:** "full suite passed", "154/154 green", G6 completion, production wiring, or
DynamoDB service-behaviour validation (emulator only).

## 5. Post-run freeze

`hashes-after.txt` — recomputed over the same 101-file set. `hashes-diff.txt` is **empty**:
identical file set, identical bytes, no source drift across the run. Only `target/` and this
evidence directory changed; no tracked or source file did.

## 6. Correlation of the run to the tree

The freeze brackets the run on both sides with an empty diff, so the 154/1-error result belongs to
source digests `b9a2579f…` (composer) / `075eccc8…` (send-intent test) / `b47fb547…` (`pom.xml`).
This is stronger than the chronological correspondence used in `p1b3a-2026-11-20`, where the full
suite for `b9a2579f` was left unverified.

## 7. Preservation

Everything under `evidence/p1b3a-2026-11-20/` is untouched; this turn wrote only into
`evidence/p1b3a-current-verification-2026-11-20/`. The incomplete `full-test-final.log` remains
preserved as incomplete evidence and is now superseded for the current tree by this complete run.

## 8. Files in this directory

`binding.txt`, `hashes-before.txt`, `hashes-src-before.txt`, `hashes-build-before.txt`,
`hashes-current-verified.txt`, `hashes-current-diff.txt`, `mvn-test.log`, `per-class-results.txt`,
`failure.md`, `hashes-after.txt`, `hashes-src-after.txt`, `hashes-build-after.txt`,
`hashes-diff.txt`, `git-status-after.txt`, `env-01-mvn-not-on-path.log`, `env-01-note.txt`,
`env-02-java-home-unset.log`, `env-02-note.txt`, `manifest.md`.
