# P1b5c evidence manifest — service-level network-result branch (2026-11-20)

Documentation-only record written after the fact from the files in this directory. No source file
was edited, no test was re-run and no file in this directory was modified or recreated to produce
this manifest. Timestamps are the observed filesystem mtimes (America/Bogota, UTC-05:00).

## Binding

| Field | Observed |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `cfc456f4f469d9558a78fda8ff9b770b00817315` (no commit in this slice) |
| Origin | `https://github.com/pazfernando/acquiring-simulator.git` |
| Model | provider `nan`, model `deepseek-v4-flash`; no override |
| Source snapshot after the run | 119-line `sha256sum` set in `hashes-post-test.txt`; byte-identical to `hashes-pre-final-test.txt` and to the current working tree for every sampled file |
| Working tree | `git-status-after.txt` byte-identical to the current `git status --porcelain` (re-checked while writing this manifest) |

## Per-attempt results (actual, as recorded)

| Attempt | Window (mtime evidence) | Exit | Recorded output | Claim made |
| --- | --- | --- | --- | --- |
| `mvn -o -q -DskipTests compile` | 12:43:04–12:43:08 | 0 (`compile.exit.txt`) | `compile.stdout.log` 0 B, `compile.stderr.log` 0 B | compile succeeded; no counts |
| `NetworkResultServiceWiringTest` attempt 1 | 12:43:12–12:43:19 | 1 (`network-service-test.exit.txt`) | `network-service-test.stdout.log` 20 784 B | **FAILED: 12 tests, 1 failure, 0 errors, 0 skipped** — `networkGatewayWithoutAtomicPredecessorsIsRejected:272` |
| `NetworkResultServiceWiringTest` attempt 2 | 12:43:36–12:43:43 | 0 (`network-service-test-attempt2.exit.txt`) | both streams 0 B | **exit 0 only. No test count is available and none is asserted** (quiet run of a Mockito-only test class that emits no log output) |
| Targeted regression set | 12:44:02–12:44:43 | 0 (`targeted-test.exit.txt`) | `targeted-test.stdout.log` 6 596 B (Testcontainers/DynamoDB Local startup only) | **exit 0 only. No `Tests run:` line and no BUILD line exist, so no count is asserted** |
| Full suite, snapshot `5803cdf6` | 12:45:02–12:45:52 | 0 (`full-test.exit.txt`) | `full-test.stdout.log` 24 355 B | **220 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS** |
| Full suite, post-manifest re-run | 12:46:17–12:46:25 | **no exit file** | `full-test-final.stdout.log` 9 150 B, stderr 902 B | **INCOMPLETE — no result claimed** |

### Why attempt 1 failed (saved evidence, not inference)

The failure text shows an AssertJ message mismatch, not a production defect:

- expected the thrown message to contain `network-result gateway requires`;
- actual `IllegalArgumentException: a result gateway requires the atomic claim and dispatch-intent gateways` (`AuthorizationService.java:178`).

The constructor guard order is real product behaviour: a result gateway without claim/dispatch intent
is rejected before the network gateway is examined. The test at hash `05c3ecb3…` expected the network
message for a call that also passed a result gateway, so the expectation was wrong.

### What changed between attempt 1 and attempt 2

Only the test file, at 12:43:32.75 (between the two runs):

| File | Pre-test hash (12:43:00) | Post-test hash (12:45:56) = current |
| --- | --- | --- |
| `src/test/java/com/acquiring/simulator/aws/NetworkResultServiceWiringTest.java` | `05c3ecb3…` | `5803cdf6…` |

Current lines 267–273 assert the correct message (`atomic claim`) for the no-claim case. The edit
that produced `5803cdf6` was not diffed or copied to this directory, so the exact textual delta is
**not preserved**; the recovered behaviour is limited to the failure text above. `hashes-diff.txt`
(exit 1) records this one-file difference and nothing else.

## Which snapshot the 220 pass covers

Determination from saved artifacts, in this order:

1. The pre-test manifest was captured at 12:43:00.95, **32 seconds before** the test source edit
   (12:43:32.75), so it describes the pre-fix `05c3ecb3` file, not the file the suite ran.
2. Attempt 2 began at 12:43:36.88, after that edit. `target/test-classes/…/NetworkResultServiceWiringTest.class`
   has mtime 12:43:42.38, so the test class was compiled from the 12:43:32 source.
3. The full-suite log itself states `Nothing to compile - all classes are up to date` for both
   `default-compile` and `default-testCompile` at 12:45:02, and reports
   `NetworkResultServiceWiringTest` **12 tests, 0 failures** in that same run.
4. `hashes-post-test.txt` (12:45:56, four seconds after BUILD SUCCESS) already contains
   `5803cdf6…` and is byte-identical to the current tree hashes. No `src/**` file has an mtime after
   12:43:32.

**Conclusion:** the 220/220 BUILD SUCCESS run executed the current test bytes (`5803cdf6`) and the
current production bytes (`AuthorizationService` `ac2ceaeeca…`, unchanged before and after the run). The pre/post
hash diff is explained by the mid-sequence test edit, **not** by a tree mutation during the run.

**Residual uncertainty:** the Maven log does not embed a source hash, so run-time tree identity rests
on the three artifacts above (class mtime, "nothing to compile", post-run hash equality). It is
strongly supported but not independently re-proved. The pre-test manifest must not be described as the
manifest of that run.

## The incomplete final run

`full-test-final.{stdout,stderr}.log` has no `.exit.txt` and no further files. It started at 12:46:17
against `hashes-pre-final-test.txt` (12:46:08, already `5803cdf6`), completed
`PostgresAuthorizationCoreIntegrationTest` 24/24 and
`DynamoNetworkRejectionTransitionIntegrationTest` 2/2, then stopped inside
`DynamoResultGatewayIntegrationTest` at 12:46:25 with an unfinished Testcontainers start line.

- Exit status: **not recorded** → the run cannot be reported as passed or failed.
- Coverage: **2 classes of an unknown total; at least 26 tests, no final total.**
- Disposition: **INCOMPLETE**, retained for audit only. It adds nothing to the 220-test claim and
  does not weaken it (same source snapshot, same hashes).

## Evidence inventory

| File | Bytes | mtime | Role |
| --- | --- | --- | --- |
| `binding.txt` | 188 | 12:43:00.94 | repo/branch/HEAD/origin |
| `hashes-pre-test.txt` | 16 523 | 12:43:00.95 | 119-file hash set, captured pre-fix (stale for the run) |
| `git-status-before-test.txt` | 5 187 | 12:43:00.94 | dirty-tree snapshot |
| `compile.stdout.log` / `compile.stderr.log` | 0 / 0 | 12:43:04.73 | quiet compile |
| `compile.exit.txt` | 2 | 12:43:08.82 | `0` |
| `network-service-test.stderr.log` | 0 | 12:43:12.43 | empty |
| `network-service-test.stdout.log` | 20 784 | 12:43:19.13 | attempt 1 failure detail |
| `network-service-test.exit.txt` | 2 | 12:43:19.17 | `1` |
| `network-service-test-attempt2.stdout.log` / `.stderr.log` | 0 / 0 | 12:43:36.88 | quiet attempt 2 |
| `network-service-test-attempt2.exit.txt` | 2 | 12:43:43.82 | `0` |
| `targeted-test.stderr.log` | 0 | 12:44:02.33 | empty |
| `targeted-test.stdout.log` | 6 596 | 12:44:42.18 | container startup only, no counts |
| `targeted-test.exit.txt` | 2 | 12:44:43.51 | `0` |
| `full-test.stderr.log` | 1 355 | 12:45:50.53 | toolchain warnings only |
| `full-test.stdout.log` | 24 355 | 12:45:52.33 | **220/220 BUILD SUCCESS** |
| `full-test.exit.txt` | 2 | 12:45:52.37 | `0` |
| `hashes-post-test.txt` | 16 523 | 12:45:56.69 | 119-file hash set, run-time snapshot |
| `git-status-after.txt` | 5 187 | 12:45:56.69 | identical to current status |
| `hashes-diff.txt` / `hashes-diff.exit.txt` | 1 445 / 2 | 12:45:56.70 | one-file diff; exit `1` |
| `git-status-diff.txt` / `git-status-diff.exit.txt` | 0 / 2 | 12:45:56.70 | empty; exit `0` |
| `hashes-pre-final-test.txt` | 16 523 | 12:46:08.94 | identical to post-test set |
| `git-status-before-final-test.txt` | 5 187 | 12:46:08.94 | identical |
| `full-test-final.stderr.log` | 902 | 12:46:17.62 | warnings only |
| `full-test-final.stdout.log` | 9 150 | 12:46:25.13 | truncated run; no exit |

## Non-claims

- No source edit, no test re-run, no new suite execution and no feature work occurred while producing
  this manifest; it only classifies existing files.
- 220/220 is a local DynamoDB Local / SQLite / Testcontainers result. It is not QA, Security review or
  human acceptance, and it says nothing about AWS service behaviour (throttling, Streams, IAM,
  encryption, TTL).
- The targeted and attempt-2 runs are exit-0 evidence **without counts**; no number is supplied for
  them anywhere.
- `AwsRuntime` is unchanged (`99c32249…`) and still constructs the legacy 4-argument
  `AuthorizationService`; runtime activation remains undone and G6 remains open.
- No commit, push, PR, merge, deploy, cloud call, credential flow or deletion is claimed.
