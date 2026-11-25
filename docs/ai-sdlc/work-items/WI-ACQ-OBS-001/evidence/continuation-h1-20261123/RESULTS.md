# Continuation slice H1 — origin-verification gate and read-only history: results (2026-11-23)

Developer evidence only: **not** independent QA, **not** Security review, **not** human acceptance.
It records what was executed and observed in this slice, in order. Every statement below is grounded
in a file that exists in this directory.

## 1. Binding

| Field | Observed |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` (no commit, stage, push or PR in this slice) |
| Branch/HEAD check | recorded in `PROGRESS.md` §1 (`realpath`, `git rev-parse --show-toplevel`, branch, HEAD) |
| JDK | `JAVA_HOME=/home/pazfernando/.local/opt/jdk-25` (recorded in `compile-command.txt`) |
| Maven | `/home/pazfernando/.local/opt/maven/bin/mvn`, offline (`-o`) |
| Docker | Docker 29.1.3; `amazon/dynamodb-local:2.6.1` (Testcontainers 2.0.5, from the run stdout) |
| Model | provider `nan`, model `deepseek-v4-flash`; no override |

Slice files and their SHA-256 at the recorded run: `slice-files.sha256` (20 `src/main` and 4
`src/test` files under `.../console/`, plus the two `git status --short` lines showing both packages
untracked).

## 2. What was executed, in order

| # | Step | Command | Exit | Observed | Evidence file |
| --- | --- | --- | --- | --- | --- |
| 1 | Compile (main) | `mvn -o -q -DskipTests compile` | **0** | empty stdout, empty stderr | `compile-attempt1-exit.txt`, `compile-attempt1-stdout.txt`, `compile-attempt1-stderr.txt` |
| 2 | Test compile attempt 2 | `mvn -o … test-compile` | **1** | test-source compilation failure: the two named errors below | `compile-attempt2-testcompile-exit.txt`, `compile-attempt2-testcompile-stdout.txt` |
| 3 | Test compile attempt 3 | same, after the test-source correction | **0** | empty stdout, empty stderr | `compile-attempt3-testcompile-exit.txt`, `compile-attempt3-testcompile-stdout.txt`, `compile-attempt3-testcompile-stderr.txt` |
| 4 | First test run (red) | focused console classes | **1** | **33 tests, 1 failure, 4 errors, 0 skipped** | `history-and-ov-tests-exit.txt`, `history-and-ov-tests-stdout.txt` |
| 5 | Second test run (green) | same focused console classes | **0** | **33 tests, 0 failures, 0 errors, 0 skipped** | `history-and-ov-tests-attempt2-exit.txt`, `history-and-ov-tests-attempt2-stdout.txt` |
| 6 | Existing console classes | `ConsoleAuthorizationRouteTest`, `ConsoleSubmissionAtomicIntegrationTest` | **0** | **16 tests, 0 failures, 0 errors, 0 skipped** (13 + 3) | `existing-console-tests-exit.txt`, `existing-console-tests-stdout.txt` |

### Step 2 — the failing test compile, verbatim

`compile-attempt2-testcompile-stdout.txt` records exactly two test-source errors, both in
`src/test/java/com/acquiring/simulator/console/ConsoleHistoryRouteTest.java`:

```
[ERROR] /home/pazfernando/projects/acquiring-simulator/src/test/java/com/acquiring/simulator/console/ConsoleHistoryRouteTest.java:[137,15] cannot assign a value to final variable rows
[ERROR] /home/pazfernando/projects/acquiring-simulator/src/test/java/com/acquiring/simulator/console/ConsoleHistoryRouteTest.java:[293,62] cannot find symbol
  symbol:   variable EXPIRED_TOKEN
  location: class com.acquiring.simulator.console.ConsoleAuthenticationException.Reason
```

The first is a `final` local reassigned in the test; the second references an
`EXPIRED_TOKEN` reason that the `ConsoleAuthenticationException.Reason` enum does not declare.
Both are test-source errors. Exit was 1 and no test executed.

### Step 4 — the first test run, red

Per class, from `history-and-ov-tests-stdout.txt`: 4 tests in
`ConsoleHistoryRouteDynamoIntegrationTest` (0 failures, **4 errors**), 9 in
`ConsoleOriginVerificationGateTest` (**1 failure**, 0 errors), 20 in `ConsoleHistoryRouteTest`
(0 failures, 0 errors) — total 33, with 1 failure and 4 errors, exit 1, `BUILD FAILURE`.

The failing assertion was
`ConsoleOriginVerificationGateTest.verifiedOriginIsNotAuthenticationAndTheCredentialSeamStillRefuses`
at `ConsoleOriginVerificationGateTest.java:272`:

```
org.opentest4j.AssertionFailedError: 

expected: 401
 but was: 405
```

The four errors were identical
`com.acquiring.simulator.domain.event.EventValidationException: CONTEXT_MISMATCH at context: event
context does not match the collector partition` raised from the test's own append helper
(`ConsoleHistoryRouteDynamoIntegrationTest.java:248`) in
`paginationKeepsTheFrozenHeadWhenTheSourceGrows`,
`changedRetentionRevisionAgainstTheRealHeadForcesFourTen`,
`snapshotReadsTheRetainedRowsAndReportsCompleteCoverage`, and
`physicallyMissingRetainedRowIsAnExplicitGapNotAFabricatedEvent`.

### Step 5 — the second test run, green

`history-and-ov-tests-attempt2-stdout.txt`:

```
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 7.077 s -- in com.acquiring.simulator.console.ConsoleHistoryRouteDynamoIntegrationTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.042 s -- in com.acquiring.simulator.console.ConsoleOriginVerificationGateTest
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.046 s -- in com.acquiring.simulator.console.ConsoleHistoryRouteTest
[INFO] Tests run: 33, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Both red runs are preserved, and no case was deleted, disabled or weakened

- The red compile is preserved in `compile-attempt2-testcompile-stdout.txt` (exit in
  `compile-attempt2-testcompile-exit.txt`); the red run is preserved in
  `history-and-ov-tests-stdout.txt` (exit in `history-and-ov-tests-exit.txt`). Neither was
  overwritten by the green run, which wrote its own `*-attempt2-*` files.
- No case was deleted or disabled: the red run and the green run report **the same 33 cases**
  (4 + 9 + 20) across **the same three classes**, and **`Skipped: 0` in both**. A deleted or
  `@Disabled` case would have changed one of those numbers.
- No case was weakened: the six observed failure/error sites are all test-harness defects, not
  assertions removed — the gate case sent a request the route rejects with `405` before the
  credential seam is reached (`submissionRequestWithoutToken` now builds the `POST` request the
  route accepts), and the four DynamoDB integration cases appended through a helper whose
  `EventContext` did not match the collector partition (the helper now uses the matching
  `CONTEXT`). The step-2 errors were likewise test-source defects (a `final` local, a non-existent
  enum constant).
- Chronology supports that the corrections were confined to test sources: every file under
  `src/main/java/com/acquiring/simulator/console/` has a modification time at or before
  `22:38:33`, while the red run finished at `22:40:59` and the green run started at `22:41:31`;
  the only files written between them are `ConsoleHistoryRouteTest.java` (`22:40:34`),
  `ConsoleHistoryRouteDynamoIntegrationTest.java` (`22:41:24`) and
  `ConsoleOriginVerificationGateTest.java` (`22:41:21`).
- Disclosed limitation: the red-revision **sources** are not preserved. The console package and
  its tests are untracked, so no `git diff` of the red revision exists. The proof of
  non-deletion/non-disablement is the identical per-class case counts with zero skips; the proof
  that no assertion was relaxed rests on the failure signatures above, not on a byte-level diff.

## 3. What the green run proves — and what it does not

### Within these seams it proves

Against DynamoDB Local, with a test-supplied cursor key and test doubles for the decoder and the
context registry:

1. **Origin gate.** Initialization fails closed on an absent/empty/short/bad-alphabet expected
   value; the gate is the first application step, before dispatch, `404`/`405`, body read,
   credential seam, rate limit and any service or registry call; a refusal returns the single
   fixed body `{"version":1,"error":"EDGE_ORIGIN_NOT_VERIFIED","action":null}` without echoing the
   supplied header; each refusal emits exactly one closed denial record and a failing sink cannot
   escape; caller-controlled fields on that record are bounded and never echoed; the entry point
   cannot be constructed without a gate; a verified value reaches the application unchanged and
   is **not** authentication (the credential seam still answers `401 UNAUTHENTICATED`).
2. **History route.** `GET /console/v1/transactions/{id}/events` fails to construct without its
   collaborators; a new snapshot pins a frozen `snapshotHead`; a continuation keeps that frozen
   snapshot when the source grows; a tampered cursor is `400 INVALID_CURSOR`; an expired cursor,
   a changed `expiryRevision`, a truncated `firstRetainedSequence` and a retired mandate
   generation are `410 RESYNC_REQUIRED` with an action; a cursor bound to another subject or
   another transaction is `403`; a gap halts coverage, reports its range and suppresses the next
   cursor; a logically expired prefix is reported as a range rather than silently skipped; token
   expiry or a registry outage between the two re-authentications and a store failure are `503`
   with no payload; an unknown authorized target is `404` and discloses nothing; missing or
   duplicated tokens are refused before the store read; a foreign method or path is rejected with
   no read; malformed pagination uses the provisional mapping recorded below; `after` beyond the
   durable HEAD is invalid rather than silently clamped.

### It does not prove

- **No real issuer and no real token verification.** The console decoder is a double; no JWKS,
  signature, issuer, audience or expiry truth is exercised. That is slice K1's subject.
- **No CloudFront, no WAF, no deployed edge, no Keycloak.** The `X-Origin-Verify` value is
  injected in-process; nothing shows that an edge actually sets it, strips caller-supplied values
  or that a denial is reached only from the edge. **N-02 is therefore not closed by these bytes
  alone.**
- **No cursor key sourcing.** The codec takes an injected key; the tests use the synthetic
  `cursor-key-1` and a literal signing string. Where a deployed key comes from, how it rotates and
  how it is held is unresolved (ambiguity 3 below).
- **No server-side read-pass store, no TTL or compaction, no projection.** Expiry is logical
  (`expiryRevision` plus a reported range), not a deletion or TTL mechanism.
- **No API Gateway authorizer, no Lambda invocation, no mounted internal route, no WebSocket, no
  browser, no UI, no reload across a deployed restart.** Everything green is in-process or against
  DynamoDB Local.
- **No payment-path change.** These bytes add a gate and a read route; they do not alter,
  strengthen or weaken the atomic payment composition.

## 4. Open ambiguities (unchanged by this run)

1. **Malformed pagination input has no named D-6 error code.** D-6 names `INVALID_CURSOR` only for
   an unknown/tampered cursor. The route validates `snapshot`/`after` defensively and uses a
   provisional mapping, asserted by
   `ConsoleHistoryRouteTest.malformedPaginationInputUsesTheRecordedProvisionalMapping`. It is
   flagged for reviewer confirmation, not presented as contract text.
2. **Server-side read-pass store not implemented.** Generation continuity and retirement are
   derived from the signed cursor plus the re-resolved mandate fingerprint and `expiryRevision`.
   No table or projection was added.
3. **Cursor signing key source not fixed.** No environment-variable name is fixed in the delta;
   runtime sourcing is left to the later deployed composition.

## 5. Non-claims

- This summarising step changed no code, added no test, ran no suite, opened no scope, and
  performed no Git write, stage, commit, push, PR, merge, deployment, release or publication.
- No AWS call, no credential flow, no Keycloak interaction, no provider mutation and no edit to an
  approved specification, vector or contract.
- Local green tests are not an integrated journey, not a deployed edge, not independent QA, not
  Security review and not human acceptance. Every traceability row stays
  `independent verification: PENDING`.
