# P1b4b evidence manifest — RESULT gateway/recovery port + adapter

Date: 2026-11-20 America/Bogota. Owner: Fernando Paz.
Repository: `/home/pazfernando/projects/acquiring-simulator`
Branch: `feat/wi-acq-auth-001`
HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged; no Git write this turn)
Origin: `https://github.com/pazfernando/acquiring-simulator.git`
Model: provider `nan`, model `deepseek-v4-flash`; no override.
Toolchain: JDK 25 `/home/pazfernando/.local/opt/jdk-25`; Maven 3.10.1
`/home/pazfernando/.local/opt/maven/bin/mvn` (`JAVA_HOME` pinned in every command).

## Scope

One bounded slice: narrow `ResultGateway` port plus `DynamoResultGateway` adapter and its
DynamoDB Local tests. It consumes `ResultCriticalComposer` exactly once per call. No service wiring,
no `AuthorizationService` result branch, no `AwsRuntime` activation, no late-evidence/conflict
event variants, no `NETWORK_REJECTED` result, no resend, reversal or inquiry, no capacity default,
no specification edit.

## Delivered files (SHA-256 of the current tree)

```
b1f8a34111dbe49a193fc1194dd12b8a3d1fd11d505bea3d0f67539a83efa456  src/main/java/com/acquiring/simulator/domain/port/ResultGateway.java
9fc3623f10103e52a640a9b09c0ff9f391c1ff1f540a38887b60cbeb84a2ff6a  src/main/java/com/acquiring/simulator/adapter/aws/DynamoResultGateway.java
de56835b42affcd83dc9b1e6537917664a5c167d0de94d76688ff18eb4e2ab12  src/test/java/com/acquiring/simulator/aws/DynamoResultGatewayIntegrationTest.java
```

No pre-existing source or test file was modified. `AwsRuntime` remains
`99c32249f1b577dc0645ffed7e2751aa0c1edcdd1b51a22b92ebb639f0f36c7b` and `AuthorizationService`
remains `aa2bfe92538c3db399e05023189a9b1a2d8967c01061cb821133fa6032955633`; neither references the
new port or adapter.

## Commands and results

| Command | Log | Exit | Result |
| --- | --- | --- | --- |
| `mvn -o -q -DskipTests compile` | `compile.log` | 0 | quiet |
| `mvn -o -Dtest=DynamoResultGatewayIntegrationTest test` (attempt 1) | `result-gateway-test-attempt1.log` | 0 | **6 tests, 0 F/E/S** |
| targeted regression (`domain.event.*`, claim, send, result, recovery, gateway, wiring doubles) | `targeted-test.log` | 0 | 112 tests, 0 F/E/S |
| `mvn -o test` | `full-test.log` | 0 | **187 tests, 0 F/E/S; BUILD SUCCESS** |

Full-suite baseline before this slice was 181 passing tests (P1b4a); 181 + 6 = 187.

## Failure-artifact disclosure

The P1b4a record already discloses that its initial failing attempt log (7 run / 3 errors) was
overwritten by the corrected run and cannot be reconstructed; the P1b4a manifest marks it as not
retained. That missing artifact is still missing and is not claimed here. From this slice on, each
attempt is written to a distinct log name and never overwritten. The P1b4b first attempt passed, so
there is no failed attempt log to retain; `result-gateway-test-attempt1.log` is the untouched first
run.

## Run binding

- `hashes-run-before.txt` / `hashes-run-after.txt`: 111 files (`src/**` 110 + `pom.xml`), taken
  before and after the test runs; `hashes-diff.txt` is empty (0 lines). Both include the three new
  files above.
- `git-status-before.txt` / `git-status-after.txt` identical; no pre-existing edit was reverted,
  staged or overwritten.
- Binding text in `binding.txt` (repository, branch, HEAD, origin, model).

## Verified behaviour (DynamoDB Local only)

- **Committed result** — `COMMITTED` is returned only after the stored record is read back:
  state `APPROVED`, `decisionSource = ISSUER`, `outcomeDeterminedAt` = issuer evidence time, CAS 2,
  committed journal version 3, event sequence 3, HEAD 3, slot `usedSlots` 3 of explicit budget 8,
  dedupe `journalVersion` 3 / `stateAfter APPROVED`, event identity `APPROVED-AUTH-001`.
- **Lost response after commit** — the first `TransactWriteItems` commits and then fails; one
  strongly consistent lookup of the stable dedupe identity plus the persisted record check recovers
  `RECOVERED` knowledge with evidence `(2, 3)`; exactly one result event, one HEAD advance and one
  slot exist afterwards.
- **Ambiguous before commit** — `UNKNOWN`; the dispatch intent is untouched (2 events, HEAD 2,
  slot 2, state `AWAITING`, no determined outcome, dispatch correlation preserved). No partial
  result is written and uncertainty is never converted to "no result".
- **Exact replay** — second call returns `REPLAY`, recorded with evidence `(2, 3)`; no extra event,
  no extra HEAD advance, no extra slot; CAS stays 2 and committed version stays 3.
- **Strong-read failure** — an ambiguous write followed by a failing dedupe lookup returns `UNKNOWN`
  and is never reported as no-result; the attempt wrote nothing (2 events, HEAD 2, slot 2, no
  determined outcome).
- **Determined opposing outcome** — a competing `DECLINED` recorded after the intent was read
  causes the conditioned transaction to be cancelled (`NOT_COMMITTED`); the stored `DECLINED`
  outcome, issuer provenance, determination time and dispatch are retained, with no extra event or
  slot. The adapter never overwrites it.

## Decision and limitation record

- The port is narrow: `recordResult(intent, evidence)` returns `COMMITTED`/`REPLAY`/`RECOVERED`/
  `CONTENT_CONFLICT`/`NOT_COMMITTED`/`UNKNOWN` plus the confirmed state and persisted version
  evidence. `recorded()` is true only for a committed, replayed or recovered result whose stored
  committed version was read.
- Success is grounded in persistence, not input: matching state, `ISSUER` provenance, preserved
  dispatch correlation and committed version 3 are required; a mismatch is `UNKNOWN` or a retained
  conflict, never a synthesised success.
- Ambiguous resolution uses one strong lookup of `APPROVED-<id>` / `DECLINED-<id>`: exact committed
  metadata recovers knowledge; different content is a conflict; empty or failed lookup stays
  `UNKNOWN`. No resend, no legacy fallback, no `updateOutcome`, no `markDispatched`.
- Capacity is mandatory and explicit in the constructor; the test budget `(5, 8)` is an example, not
  approved capacity.
- Late-evidence variants, boundary announcement disposition, attempted-send re-resolution,
  `NETWORK_REJECTED`, result service coupling and runtime activation remain later slices.

## Non-claims

This is local implementation plus DynamoDB Local tests — not QA, Security review or acceptance.
G6 remains **incomplete**. DynamoDB service behaviour (throttling, Streams, IAM, encryption, TTL)
remains UNVERIFIED. `AwsRuntime` is inactive for this path. No commit, push, PR, merge, deployment,
cloud call, apply, credential flow, deletion or approved-specification edit occurred.
