# P1b4c evidence manifest — service-level atomic RESULT integration

Date: 2026-11-20 America/Bogota. Owner: Fernando Paz.
Repository: `/home/pazfernando/projects/acquiring-simulator`
Branch: `feat/wi-acq-auth-001`
HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged; no Git write this turn)
Origin: `https://github.com/pazfernando/acquiring-simulator.git`
Model: provider `nan`, model `deepseek-v4-flash`; no override.
Toolchain: JDK 25 `/home/pazfernando/.local/opt/jdk-25`; Maven 3.10.1
`/home/pazfernando/.local/opt/maven/bin/mvn` (`JAVA_HOME` pinned in every command).

## Scope

One bounded slice: an optional service-level result branch consuming the existing `ResultGateway`
exactly once for a valid, correlated, on-time confirmed reply after a durable dispatch intent. The
reported outcome comes only from persisted evidence. `AwsRuntime` is not activated; late-evidence
variants, `NETWORK_REJECTED`, conflict/quarantine metadata, ambiguous-result policy, principal
authorization and OBS-20/21/22 named scenarios remain later slices.

## Delivered files (SHA-256 of the current tree)

```
eda810e9784497cf38933f769cce5e95571d45fc552ba71f7901b0ed95b187e3  src/main/java/com/acquiring/simulator/domain/AuthorizationService.java
43bcccb11f92ed82f5f8e53cb45286049ab40bed0a02c3f36e4628d523558a08  src/test/java/com/acquiring/simulator/aws/ResultServiceWiringTest.java
```

`AwsRuntime` is unchanged (`99c32249f1b577dc0645ffed7e2751aa0c1edcdd1b51a22b92ebb639f0f36c7b`)
and still builds the 4-arg legacy `AuthorizationService`; the atomic result branch is constructed
only by tests. No pre-existing source or test file other than `AuthorizationService` was modified.

### What changed in `AuthorizationService`

- New optional `ResultGateway` field and 8-arg constructor
  `(repository, issuerSimulator, properties, clock, claimGateway, dispatchIntentGateway, resultGateway, identifiers)`.
  Existing 4/5/6/7-arg constructors are unchanged. A `ResultGateway` requires **both** the atomic
  claim and dispatch-intent gateways (`IllegalArgumentException` otherwise).
- `sendDownstream` sends exactly once, then delegates to `evaluateAtomicResult` only when the
  result gateway is present; the legacy `evaluateDispatchReply` path is unchanged otherwise.
- `evaluateAtomicResult`:
  - consumes `ResultGateway.recordResult(intent, reply)` exactly once for an authenticated,
    correlated, on-time `APPROVED`/`DECLINED` reply;
  - `COMMITTED`/`REPLAY`/`RECOVERED` report `Outcome` from the persisted `confirmedState` and only
    when `Decision.recorded()` (committed-version evidence present);
  - `CONTENT_CONFLICT` reads the stored record and reports the **authoritative stored outcome**;
    the caller's contradictory reply never overwrites it and no legacy row is written;
  - `NOT_COMMITTED`/`UNKNOWN` return `NOT_CONFIRMED` with no fabricated financial result;
  - `NO_RESPONSE`/`MALFORMED`/`NETWORK_REJECTED`/uncorrelated/late replies fail closed to
    `NOT_CONFIRMED` with **no** `updateOutcome` and **no** legacy event append.
- `submitEvidence` (late admission) throws an explicit `IllegalStateException` on an atomic-mode
  service; `evaluateDeadline` becomes read-only there. Neither writes a legacy row.

## Commands and results

| Command | Log | Exit | Result |
| --- | --- | --- | --- |
| `mvn -o -q -DskipTests compile` | `compile.log` | 0 | quiet |
| `mvn -o -q -DskipTests test-compile` (final) | `test-compile.log` | 0 | quiet |
| `mvn -o -Dtest=...ResultServiceWiringTest test` (attempt 1) | `result-service-test-attempt1.log` | 0 | **12 tests, 0 F/E/S** |
| targeted regression (`domain.event.*`, claim, send, result, recovery, gateway, wiring doubles) | `targeted-test.log` | 0 | **151 tests, 0 F/E/S** |
| `mvn -o test` | `full-test.log` | 0 | **199 tests, 0 F/E/S; BUILD SUCCESS** |

Full-suite baseline before this slice was 187 passing tests (P1b4b); 187 + 12 = 199.

## Failure-artifact disclosure

- The first `test-compile` attempt failed inside the new test file (four `cannot find symbol: calls`
  errors from a locally typed fake result gateway). That log was **overwritten** by the successful
  recompile and is not retained; the correction was test-only typing, no production source change.
- `result-service-test-attempt1.log` is the untouched first run and passed; there is no failed test
  log for this slice.
- The P1b4a missing failed-attempt log remains missing and is not claimed here.

## Run binding

- `hashes-run-before.txt` (112 lines) / `hashes-run-after.txt` (113 lines): every file under `src`
  plus `pom.xml`, taken before and after the runs. `hashes-diff.txt` contains exactly two changes:
  `AuthorizationService` (`aa2bfe9…` → `eda810e…`) and the new `ResultServiceWiringTest`
  (`43bcccb…`). `pom.xml` is unchanged.
- `git-status-before.txt` / `git-status-after.txt`: the only status change is the new untracked
  test file (`64a65`). No pre-existing edit was reverted, staged or overwritten.
- Binding text in `binding.txt` (repository, branch, HEAD, origin, model).

## Verified behaviour (service doubles; no container)

- **Approved/declined** — exactly one downstream call, exactly one gateway call, outcome read from
  the persisted `APPROVED`/`DECLINED` state; zero legacy `updateOutcome`/`appendEvent`/`markDispatched`
  calls.
- **Commit with lost response** — `RECOVERED` with persisted evidence returns the stored outcome and
  keeps one downstream call.
- **Duplicate** — `REPLAY` returns the stored outcome and never sends a second time.
- **Unknown / not committed** — `NOT_CONFIRMED`; no financial result, no legacy write, no
  no-send assertion.
- **Recorded status without persisted evidence** — `NOT_CONFIRMED`, never a synthesised success.
- **Competing result** — `CONTENT_CONFLICT` reports the authoritative stored `DECLINED` even though
  the caller's reply was `APPROVED`; no legacy row is written.
- **Late reply, network rejection, late evidence admission** — fail closed with no gateway call, no
  legacy outcome/event row; `submitEvidence` fails explicitly.
- **Invalid port combination** — a result gateway without the atomic claim and dispatch-intent
  gateways is rejected at construction.

## Decision and limitation record

- Success is grounded in persistence (`Decision.recorded()` requires a confirmed read-back state and
  recorded committed version), not in the caller's reply.
- `REPLAY`/`RECOVERED` are knowledge, never send permission; this slice adds no resend, reversal or
  inquiry.
- The current `AuthorizationResult` carries only `(outcome, reference)`; there is no conflict flag in
  the approved response envelope yet. The conflict is retained by the gateway and the caller sees the
  authoritative stored outcome. The C3 envelope disclosure remains a later slice.
- `NETWORK_REJECTED` has no atomic composer; the service does not route it to legacy writes and
  reports the conservative `NOT_CONFIRMED`.
- Late evidence is intentionally not admitted; only the boundary `NOT_CONFIRMED` is returned.
- Capacity is not involved at the service level; the adapter still requires an explicit
  `CriticalCapacity`. The O8 numbers remain PROPOSED.

## Non-claims

Local implementation plus service-double tests and DynamoDB Local adapter tests — not QA, Security
review or acceptance. G6 remains **incomplete**. DynamoDB service behaviour (throttling, Streams,
IAM, encryption, TTL) remains UNVERIFIED. `AwsRuntime` is inactive for this path. No commit, push,
PR, merge, deployment, cloud call, apply, credential flow, deletion or approved-specification edit
occurred.
