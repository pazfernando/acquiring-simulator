# T3 prerequisite result — AWS response-timeout default (2026-11-20)

Local source-correction evidence only. **Not** runtime activation, not integration, not independent
QA, Security review or human acceptance.

## Defect

`AwsRuntime.processor()` resolved its authorization response timeout with an inline default of
`2000` ms (`RESPONSE_TIMEOUT_MS` absent), while approved **AUTH-001 R20** states the simulator
default is **30 seconds from dispatch**, and
`src/main/resources/application.properties` already declared
`simulator.authorization.response-timeout=PT30S`. Same code, two different deadlines depending on
which entry path was used.

Pre-fix probe (`ResponseTimeoutDefaultProbe.java`, reproduces the exact pre-fix expression, no AWS
call): exit `1`, `pre-fix AwsRuntime effective default = 2000 ms`, `approved AUTH-001 R20 default =
30000 ms`, `application.properties = PT30S`, `DIVERGENCE`. See `prefix-probe-exit.txt`.

## Correction

New seam `com.acquiring.simulator.config.ResponseTimeoutConfig`; `AwsRuntime.processor()` now calls
`ResponseTimeoutConfig.resolve(System.getenv(ResponseTimeoutConfig.RESPONSE_TIMEOUT_MILLIS_VARIABLE))`
instead of the inline literal.

| Input | Resolved result |
| --- | --- |
| absent (`null`) | `Duration.ofSeconds(30)` — approved R20 default |
| `45000` (valid positive) | `Duration.ofMillis(45000)` — explicit override preserved |
| `1`, ` 45000 ` | preserved (trimmed) |
| `abc`, `30s`, `30.5`, `1e4`, `99999999999999999999` | `IllegalStateException`, message names the variable |
| `0`, `-1`, `-30000` | `IllegalStateException` |
| `""`, `"   "`, `"\t"` | `IllegalStateException` — no silent fallback |

**Changed invalid-input behaviour.** Malformed and blank values previously threw a bare
`NumberFormatException`; non-positive values were rejected later by `SimulatorProperties`. All now
fail closed at cold start with one clear `IllegalStateException`, before any authorization is
evaluated, so no configuration error can become a decline. No evidence ordering, deadline-equality,
business-state or transport-timeout-to-outcome rule was touched. Approved specifications and
`application.properties` were not edited.

Post-fix probe (`PostFixDefaultProbe.java`, loads `target/classes`, variable absent): exit `0`,
`post-fix resolved default = 30000 ms`, `MATCH = true`. See `postfix-probe-exit.txt`.

## Tests actually run

Command (`test-command.txt`):

```
mvn -o -B test -Dtest='ResponseTimeoutConfigTest,DeadlineDispositionTest,AwsTransportTest,AuthorizationCoreIntegrationTest'
```

Result: exit `0`, `BUILD SUCCESS`, **45 tests / 0 failures / 0 errors / 0 skipped**.

| Class | Tests | Result |
| --- | --- | --- |
| `com.acquiring.simulator.config.ResponseTimeoutConfigTest` (new) | 6 | pass |
| `com.acquiring.simulator.domain.DeadlineDispositionTest` (directly affected boundary) | 9 | pass |
| `com.acquiring.simulator.aws.AwsTransportTest` (directly affected transport) | 6 | pass |
| `com.acquiring.simulator.AuthorizationCoreIntegrationTest` (R20 scenario harness) | 24 | pass |

Raw output: `focused-stdout.txt`, `focused-stderr.txt`, `focused-exit.txt`. No broad suite was run:
the edit changes one value resolution in one cold-start factory, and the four classes above are the
focused plus directly affected set.

## Freeze

Frozen **after** the edits and **before** the tests: 265 build inputs (`pom.xml` + all of `src`) in
`freeze-filelist.txt`. `hashes-before.sha256` and `hashes-after.sha256` are identical
(`hashes.diff` empty, `diff_exit=0`); `sha256sum -c hashes-before.sha256` → `verify_exit=0`,
265/265 `OK`. Working tree at freeze point equals the tree after the run
(`git-status-before.txt` = `git-status-after.txt` = `git-status-final.txt`, 104 entries). HEAD
`cfc456f4f469d9558a78fda8ff9b770b00817315` unchanged (`head-before.txt` = `head-after.txt`).

**Freeze limitation.** The freeze point was captured after this slice's edits, so it proves the
tested bytes are the saved bytes and that the run mutated nothing; it does not reconstruct a
pre-edit byte baseline. The pre-edit state of the touched files is recoverable from HEAD
(`AwsRuntime.java`) and from absence (the two new files).

## Files changed by this slice

- modified: `src/main/java/com/acquiring/simulator/adapter/aws/AwsRuntime.java`
- new: `src/main/java/com/acquiring/simulator/config/ResponseTimeoutConfig.java`
- new: `src/test/java/com/acquiring/simulator/config/ResponseTimeoutConfigTest.java`
- new: this evidence directory
- documentation: `requirement-traceability.md` (AUTH-001 R20 row + T2 gap clause),
  `implementation-progress.md` (§56)

Nothing else was modified, staged, reverted or deleted. The Coordinator's plan, delivery status,
ADR and the Security `reviews/access-design-20261120` directory were not written to.

## Runtime state — explicit

`AwsRuntime` atomic path remains **inactive**: `processor()` still constructs the legacy
four-argument `AuthorizationService`; no atomic claim, dispatch-intent, result, network-result,
late-evidence or event-store gateway is referenced from it; no internal route is mounted. Only the
timeout value changed. Activation, deadline persistence and an integrated harness remain open
(T3), together with T2's trusted application boundary.

## Concurrent external change, disclosed

Between the post-run snapshot and final capture, the untracked directory
`docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/retention/` appeared (worktree entries 104 → 105).
It was **not** created, read or modified by this slice. `external-concurrent-change.txt` records
the observation; `git-status-final.txt` is the refreshed snapshot that includes it. Freeze hashes
cover `pom.xml` and `src` only and were unaffected.
