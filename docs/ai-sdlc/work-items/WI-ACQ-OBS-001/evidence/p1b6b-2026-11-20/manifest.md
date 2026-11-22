# P1b6b evidence manifest — 2026-11-20

Documentation-only finalization of the saved P1b6b run. No test was re-run, no source edited, and
no `hashes-post.txt`/`git-status-after.txt` is invented: the saved script stopped during `full-test`
before writing them.

## Binding (from `binding.txt`)

| Field | Observed |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Git top-level | same (containment OK) |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `cfc456f4f469d9558a78fda8ff9b770b00817315` |
| Origin | `https://github.com/pazfernando/acquiring-simulator.git` |
| Model | provider `nan`, `deepseek-v4-flash` (configured default; no override) |
| JDK | OpenJDK 25.0.5, `/home/pazfernando/.local/opt/jdk-25` |
| Maven | Apache Maven 3.10.1, `/home/pazfernando/.local/opt/maven/bin/mvn` |
| Start (UTC) | `2026-11-20T18:05:28Z` (13:05:28 -05:00) |

## Saved run artifacts

| Artifact | Content |
| --- | --- |
| `binding.txt` | Binding table above |
| `git-status-before.txt` | Working tree before the run |
| `hashes-pre.txt` | 123 SHA-256 entries: 122 `src` files + `pom.xml` |
| `compile.stdout.log`, `compile.stderr.log`, `compile.exit.txt` | Quiet compile |
| `late-evidence-test.*` | `DynamoLateEvidenceTransitionIntegrationTest` |
| `targeted-test.*` | The literal selector in `run.sh` |
| `full-test.stdout.log`, `full-test.stderr.log` | Full suite; **no** `.exit.txt` |
| `hashes-finalize-2026-11-20.txt` | Finalization-time recomputation (not run-produced) |
| `hashes-finalize-vs-pre.diff.txt` | Empty (0 bytes) |
| `git-status-finalize-2026-11-20.txt` | Finalization-time status (not run-produced) |

`hashes-post.txt`, `hashes-diff.txt`, `git-status-after.txt` and `new-files.sha256.txt` were never
written because `run.sh` was interrupted inside `full-test`.

## Actual results and exits

| Step | Command | Exit | Captured result |
| --- | --- | --- | --- |
| compile | `mvn -o -q -DskipTests compile` | 0 | both streams empty |
| late-evidence | `mvn -o -Dtest=DynamoLateEvidenceTransitionIntegrationTest test` | 0 | **11 tests, 0 F/E/S**; `BUILD SUCCESS` 13:05:40 |
| targeted | `mvn -o -Dtest='com.acquiring.simulator.domain.event.*Test,DynamoLateEvidenceTransitionIntegrationTest,DynamoResultTransitionIntegrationTest,DynamoResultGatewayIntegrationTest,DynamoClaimTransitionIntegrationTest,DynamoDispatchIntentTransitionIntegrationTest,DynamoNetworkResultTransitionIntegrationTest,DeadlineDispositionTest' test` | 0 | **94 tests, 0 F/E/S, 14 classes**; `BUILD SUCCESS` 13:05:58 |
| full | `mvn -o test` | **none** | **INCOMPLETE** (see below) |

The targeted selector names two patterns that match no class in this tree
(`DynamoClaimTransitionIntegrationTest`, `DynamoDispatchIntentTransitionIntegrationTest`); the 14
classes that actually ran are listed in `targeted-test.stdout.log`. It is not the same set as the
159-test P1b6a targeted sweep.

`full-test.stdout.log` contains 9 completed class summaries totalling 78 tests, all 0 F/E/S, then
stops after `Running com.acquiring.simulator.aws.AwsTransportTest`. There is no aggregate
`Results:`, no `BUILD SUCCESS`, and no exit file. The full-suite outcome for this snapshot is
therefore **unknown**, not a pass. No suite-success figure is claimed for P1b6b.

## File-set and byte correspondence

- `hashes-pre.txt` and the finalization recomputation both contain 123 entries with the same sorted
  file set; `hashes-finalize-vs-pre.diff.txt` is empty and the two hashes files are byte-identical.
- `git-status-before.txt` and `git-status-finalize-2026-11-20.txt` are identical (diff empty).
- This proves the tree did not change between the start of the saved run and this finalization. It
  does **not** prove that the Maven JVM hashed its inputs, and it is not an in-run digest.
- Slice files under test (from `hashes-pre.txt`):
  - `src/main/java/com/acquiring/simulator/adapter/aws/LateEvidenceCriticalComposer.java`
    `569206424c5c45c753c77fed23dd1e226bb0b804fdcec928dee31b79531772d7`
  - `src/test/java/com/acquiring/simulator/aws/DynamoLateEvidenceTransitionIntegrationTest.java`
    `03d2357189f0683e5b9d20b80903bd86828a9ae9d5c03fdcf385cb98aa5181e0`

## Scope of what was exercised

DynamoDB Local 2.6.1 (Testcontainers) only. The test supplies an already-verified `DownstreamReply`
through a private internal seam; C4 signatures, sender/relay roles and attestation are **not**
exercised. The composer is not referenced by `AuthorizationService` or `AwsRuntime`, so the service
branch, runtime activation and AWS service behaviour remain unverified.
