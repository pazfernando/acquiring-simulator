# T4 console API boundary — manifest, 2026-11-22

Self-describing inventory of this evidence directory. Per-file hashes of everything except this
file are in `evidence-sha256.txt`; re-check from this directory with
`sha256sum -c evidence-sha256.txt`.

## 1. Run identity

| Item | Value |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` (unchanged before/after) |
| Runtime | local JVM, OpenJDK 25.0.5, Maven 3.10.1, offline `-o` |
| `JAVA_HOME` | `/home/pazfernando/.local/opt/jdk-25` |
| Maven launcher | `/home/pazfernando/.local/opt/maven/bin/mvn` |
| Docker | 29.1.3, `amazon/dynamodb-local:2.6.1` |
| Model observed | `nan/deepseek-v4-flash` (session model, no override) |

## 2. Commands and outcomes

| # | Scope | Command | Exit | Tests | F / E / S |
| --- | --- | --- | --- | --- | --- |
| 1 | Two console classes | `mvn -o -B test -Dtest=ConsoleAuthorizationRouteTest,ConsoleSubmissionAtomicIntegrationTest` | `0` | 16 | 0 / 0 / 0 |
| 2 | Directly affected atomic classes | `mvn -o -B test -Dtest=AwsRuntimeAtomicWiringIntegrationTest,AtomicClaimServiceWiringIntegrationTest,DispatchIntentServiceWiringTest,ResultServiceWiringTest,DynamoAtomicClaimRecoveryIntegrationTest,DynamoCriticalClaimIntegrationTest,DynamoDispatchIntentGatewayIntegrationTest,DynamoResultGatewayIntegrationTest,DynamoResultTransitionIntegrationTest` | `0` | 66 | 0 / 0 / 0 |

Both runs used the absolute paths in §1 with `JAVA_HOME` exported. Both produced `BUILD SUCCESS`
and 0-byte stderr files. No failed launcher attempt occurred in this run.

## 3. Frozen slice (13 files)

Hashed before and after the runs; the two lists are identical.

| Scope | Count |
| --- | --- |
| `src/main/java/com/acquiring/simulator/console/*.java` | 11 |
| `src/test/java/com/acquiring/simulator/console/*.java` | 2 |
| **Total** | **13** |

Files: `slice-hashes-before.sha256`, `slice-hashes-after.sha256`, `slice-hash-diff.txt` (empty).
Related inputs outside the slice, recorded for orientation:

| Path | SHA-256 |
| --- | --- |
| `pom.xml` | `b47fb547ab28628b78e4241986638b9379cc4263e1762218192264bb1bc7a93c` |
| `src/main/java/com/acquiring/simulator/adapter/aws/AwsRuntime.java` | `23251bf4e9b1674d8b4a4f6ba80b96924a2c9eb22c39dcfcd1116d9544e2a491` |
| `src/main/java/com/acquiring/simulator/domain/AuthorizationService.java` | `c3a11d75da4eb5c45210950cee625605adbd531ab5c4891135f07422016d0dbe` |

**Freeze limitation:** post-edit baseline. The console package and both tests are untracked at
HEAD, and the two main files above were already modified before this run. The freeze proves the
runs mutated none of the 13 slice files; it does not prove these are committed or pre-edit bytes.

## 4. Files written by this verification pass

| File | Contents |
| --- | --- |
| `RESULTS.md` | binding, commands, per-class counts, what is and is not proven, freeze limitation |
| `MANIFEST.md` | this inventory |
| `runtime.txt` | repository, branch, HEAD, toolchain paths, Docker, model |
| `branch.txt`, `head.txt`, `head-after.txt`, `head-diff.txt` | binding; HEAD unchanged |
| `git-status-before.txt`, `git-status-after.txt`, `git-status-diff.txt` | identical tree snapshots; diff empty |
| `slice-hashes-before.sha256`, `slice-hashes-after.sha256`, `slice-hash-diff.txt` | 13 slice files; diff empty |
| `console-tests-exit.txt` | `0` |
| `console-tests-stdout.txt` | full Maven output for the two console classes |
| `console-tests-stderr.txt` | **empty** (0 bytes) |
| `console-surefire-summary.txt` | `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0` |
| `atomic-tests-exit.txt` | `0` |
| `atomic-tests-stdout.txt` | full Maven output for the nine atomic classes |
| `atomic-tests-stderr.txt` | **empty** (0 bytes) |
| `atomic-surefire-summary.txt` | `Tests run: 66, Failures: 0, Errors: 0, Skipped: 0` |
| `evidence-sha256.txt` | hashes of every file here except this manifest |

There is no `console-analysis.md`: both runs were green, so no failing case needed diagnosis.

## 5. Verification of this manifest

```bash
cd docs/ai-sdlc/work-items/WI-ACQ-OBS-001/evidence/t4-console-api-20261122
sha256sum -c evidence-sha256.txt
```

## 6. Non-claims

No Git write, stage, commit, push, PR, merge, deploy, release, publication, AWS call, credential
flow, provider mutation, approved-specification edit or invented capacity default occurred. No
onward delegation, no model override. Developer evidence only: not independent QA, Security review
or human acceptance.
