# P1b4a evidence manifest — atomic verified RESULT composer

Date: 2026-11-20 America/Bogota. Owner: Fernando Paz.
Repository: `/home/pazfernando/projects/acquiring-simulator`
Branch: `feat/wi-acq-auth-001`
HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged; no Git write this turn)
Origin: `https://github.com/pazfernando/acquiring-simulator.git`
Model: provider `nan`, model `deepseek-v4-flash`; no override.
Toolchain: JDK 25 `/home/pazfernando/.local/opt/jdk-25`; Maven 3.10.1
`/home/pazfernando/.local/opt/maven/bin/mvn` (`JAVA_HOME` pinned in every command).

## Scope

Smallest coherent verified-result composer plus DynamoDB Local tests only. APPROVED and DECLINED
from a valid correlated durable intent; no service wiring, no result gateway, no ambiguous-result
resolution, no physical send, no `AwsRuntime` activation, no capacity default, no specification edit.

## Delivered files (SHA-256 of the current tree)

```
ad56c59c01a4365094dab8d00c8180f5da4ea4f18d9f949a2474bb207cf47a90  src/main/java/com/acquiring/simulator/adapter/aws/ResultCriticalComposer.java
9bc49e566acce34ce150d336dedc33e6a2a2d0aedbdf4679a37f9c6c1671f29f  src/test/java/com/acquiring/simulator/aws/DynamoResultTransitionIntegrationTest.java
```

No pre-existing source or test file was modified. `hashes-before.txt` records the pre-edit inputs
(`pom.xml`, store/repository/composers/keys/submission/record/reply/service and the P1b3a send test)
and matches `hashes-run-before.txt` for those paths.

## Commands and results

| Command | Log | Exit | Result |
| --- | --- | --- | --- |
| `mvn -o -q -DskipTests compile` | `compile.log` | 0 | quiet |
| `mvn -o -Dtest=...DynamoResultTransitionIntegrationTest test` (first attempt) | not retained | 1 | 7 run, 3 errors: composed record condition referenced absent top-level `dispatchedAt` |
| same targeted command (after correction) | `result-test.log` | 0 | 7 run, 0 F/E/S |
| targeted regression (`domain.event.*`, claim, send, result, recovery) | `targeted-test.log` | 0 | 87 run, 0 F/E/S |
| `mvn -o test` | `full-test.log` | 0 | **181 tests, 0 F/E/S; BUILD SUCCESS** |

The failed first attempt was not preserved as a separate file (its redirect was overwritten by the
corrected run); only the reported result is recorded here. `result-test.log` is the final green run
of the same command. Full-suite baseline before this slice was 174 passing tests (P1b3c).

## Run binding

- `hashes-run-before.txt` / `hashes-run-after.txt`: 109 files (`src/**` 108 + `pom.xml`), `diff`
  empty (`hashes-diff.txt` 0 lines). The runs are bound to the hashes above.
- `git-status-before.txt` / `git-status-after.txt` identical; `AwsRuntime` and
  `AuthorizationService` are unchanged (hash `aa2bfe92…`).

## Verified behaviour (DynamoDB Local only)

- `APPROVED`: persisted state `APPROVED`, `decisionSource = ISSUER`, `outcomeDeterminedAt` = issuer
  evidence time, CAS 1→2, committed journal version 2→3, critical event sequence 3, HEAD 3, slot
  `usedSlots` 3 of explicit budget 8, dedupe `journalVersion` 3 / `stateAfter APPROVED`.
- `DECLINED`: identical atomic shape with `DECLINED` state and event identity `DECLINED-AUTH-001`.
- Exact replay: `EXACT_REPLAY`, sequence 3, no extra event row, no extra slot, record unchanged.
- Competing determined outcome recorded after the intent was read: the composed transaction is
  cancelled (`TransactionCanceledException`), no event/HEAD/slot row is added and the competing
  state is retained — no state override.
- No durable dispatch intent (claim only): rejected before any write; one claim event, HEAD 1, slot 1.
- Submission labelled with journal version 2 or `stateAfter DECLINED` for approved evidence:
  rejected before any write; the existing two-transition state is unchanged.
- Uncorrelated, `NO_RESPONSE`, `MALFORMED`, `NETWORK_REJECTED` or pre-dispatch evidence: rejected at
  composer construction before any write.

## Decision and limitation record

- Transition identity is fixed: journal version 3, state `APPROVED`/`DECLINED`, event identity
  `<STATE>-<authorizationId>`, producer sequence 3, `BUSINESS_PERSIST`/`SUCCEEDED`/`VALID`,
  `PROCESSOR` actor with `ISSUER` provenance in the business record.
- Precedence guard is the exact persisted CAS/committed pair `(1, 2)` written only by the
  dispatch-intent transition; the collector does not treat a payload field as storage authority.
  Any later write advances at least the CAS counter, so a stale result cannot overwrite it.
- Late evidence commits the same confirmed state with `outcomeDeterminedAt` = evidence time; the
  caller's conservative boundary outcome and the late/conflict evidence event variants remain a
  later slice. Contradictory evidence is refused, never applied.
- `NETWORK_REJECTED` results, resend/reversal/inquiry, ambiguous-result recovery and the service-level
  result gateway are explicitly out of scope.
- Slot accounting is opt-in; test budget `(5, 8)` is an example, not approved capacity.

## Non-claims

This is local implementation plus DynamoDB Local tests — not QA, Security review or acceptance.
G6 remains **incomplete**. DynamoDB service behaviour (throttling, Streams, IAM, encryption, TTL)
remains UNVERIFIED. No commit, push, PR, merge, deployment, cloud call, apply, credential flow,
deletion or approved-specification edit occurred.
