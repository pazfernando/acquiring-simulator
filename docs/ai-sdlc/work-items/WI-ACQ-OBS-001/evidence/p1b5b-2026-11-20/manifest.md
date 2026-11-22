# P1b5b evidence manifest — network-result gateway/adapter + tests; P1b5a citation attribution

Date: 2026-11-20 America/Bogota. Owner: Fernando Paz.
Repository: `/home/pazfernando/projects/acquiring-simulator`
Branch: `feat/wi-acq-auth-001`
HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged; no Git write this turn)
Origin: `https://github.com/pazfernando/acquiring-simulator.git`
Model: provider `nan`, model `deepseek-v4-flash`; no override.
Toolchain: JDK 25.0.5 `/home/pazfernando/.local/opt/jdk-25`; Maven 3.10.1
`/home/pazfernando/.local/opt/maven/bin/mvn` (`JAVA_HOME` set in every command).

## Binding

`binding.txt`. Working tree had 71 dirty entries at slice start; all preserved, none reverted,
staged or overwritten. `git status --porcelain` identical before and after the test runs
(`git-status-diff.exit.txt` = 0). A 118-file `src/**` + `pom.xml` hash set is identical before and
after the test runs (`hashes-diff.txt` empty, `hashes-diff.exit.txt` = 0): the runs wrote no source.

## 1. P1b5a citation review — attribution corrected, not a business conflict

Verified in existing domain code (`reason-allowlist-check.txt`):

- The legacy path `AuthorizationService` records, in two places (lines 209–210 and 577–578), state
  `NETWORK_REJECTED`, `DecisionSource.NETWORK` and the **literal** reason category
  `"NETWORK_REJECTION"`, then returns `Outcome.PROCESSOR_REJECTED` (line 580) and maps
  `NETWORK_REJECTED -> PROCESSOR_REJECTED` on replay (line 618).
- The approved event reason allowlist `EventReason` does **not** contain `NETWORK_REJECTION`, and
  neither does `RejectionReason`. There is therefore no rule that renames or extends a public code:
  the composer writes the existing internal business-record category and keeps the event reason
  `VALID`.

Conclusion: NET-01/OBS-03 not naming `NETWORK_REJECTION` is **not** a business conflict. The earlier
wording ("R05/R10/R22 and the existing domain mapping") conflated sourced rules with an
implementation constant. The class javadoc now attributes the state/source to R05/R10/R22 and the
reason string to existing internal domain-code vocabulary, explicitly outside the approved event
reason allowlist. No specification file, fixture or public code was changed. The byte-preserved
P1b5a `citations.md` is left intact; `citations-addendum.md` records the correction.

## 2. Delivered files

```
ba239ea93cbb6c198b6402e66f41b278b44a09ed9e51153f66feebe26f68b638  src/main/java/com/acquiring/simulator/domain/port/NetworkResultGateway.java            (new)
7003d37e54a972b86591cebb266746908cac41b13dab0298128915c12ee7dea3  src/main/java/com/acquiring/simulator/adapter/aws/DynamoNetworkResultGateway.java       (new)
a3160a50fb9957daab19e2a6a628daac07c0bb01109adb8c376bc12d82b79a31  src/main/java/com/acquiring/simulator/adapter/aws/NetworkRejectedCriticalComposer.java (javadoc attribution only)
cc61637570fa23da03e366da05180ce372e7cb17e5102748f534f78e442bdf2f  src/test/java/com/acquiring/simulator/aws/DynamoNetworkResultGatewayIntegrationTest.java (new)
```

`NetworkRejectedCriticalComposer` was `b781020f…` in P1b5a. This slice changed **only** javadoc
attribution; the executable code is byte-identical apart from comments, and the same composer is
exercised by the targeted suite below.

No pre-existing source or test file was modified. `AwsRuntime` stays
`99c32249f1b577dc0645ffed7e2751aa0c1edcdd1b51a22b92ebb639f0f36c7b`. `AuthorizationService` is
`eda810e9784497cf38933f769cce5e95571d45fc552ba71f7901b0ed95b187e3` (its P1b4c state; not edited
this turn) and does not reference the new port.

## 3. Contract mapping

| Aspect | Value |
| --- | --- |
| Admitted evidence | explicit `NETWORK_REJECTED` reply only, correlated to the issued dispatch, available no earlier than the durable dispatch, on an `AWAITING`/`NONE` dispatched intent |
| Committed state | `NETWORK_REJECTED`, `DecisionSource.NETWORK`, existing internal category `NETWORK_REJECTION`, evidence time |
| Persistence guard | exact CAS/committed pair `(1, 2)` written only by the dispatch-intent transition; a later write (e.g. an issuer confirmation) advances it and cancels a stale rejection |
| Committed version | journal version 3, explicitly persisted; never derived from the CAS counter |
| Event | `NETWORK_REJECTED-<authorizationId>`, producer sequence 3, `PROCESSOR`/`business-persist`/`SUCCEEDED`/`VALID`, issued correlation — no issuer actor/task |
| Channel mapping | `Decision.callerOutcome()` is `PROCESSOR_REJECTED` for recorded rejection, else `NOT_CONFIRMED`; never `ISSUER_DECLINED` |
| Capacity | constructor requires explicit `CriticalCapacity`; no default; O8 numbers still PROPOSED |
| Runtime | not constructed by `AwsRuntime`; service coupling absent |

## 4. Commands and results (logs captured with stdout, stderr and exit files)

| Command | Exit | Result |
| --- | --- | --- |
| `mvn -o -q -DskipTests compile` (`compile.log`) | 0 | quiet |
| `mvn -o -q -DskipTests test-compile` (`test-compile.log`) | 0 | quiet |
| `DynamoNetworkResultGatewayIntegrationTest` attempt 1 (`network-gateway-test-attempt1.*`) | 0 | **7 run, 0 F/E/S** |
| targeted regression (`domain.event.*`, event store, claim, recovery, dispatch intent, result transition, result gateway, network transition, network result gateway, wiring doubles) (`targeted-test.*`) | 0 | **136 run, 0 F/E/S** |
| `mvn -o test` on the frozen tree (`full-test.*`) | 0 | **208 run, 0 F/E/S; BUILD SUCCESS** |

No failed attempt occurred in this slice, so no failure log exists to preserve; attempt-file naming
is distinct so a later retry cannot overwrite these.

## 5. Test coverage against the requested properties

1. `committedNetworkRejectionRequiresPersistedNetworkProvenance` — one transaction commits stored
   `NETWORK_REJECTED`/`NETWORK`/`NETWORK_REJECTION`, committed version 3 over CAS 2, sequence 3,
   HEAD 3, slot 3 of explicit budget 8, `PROCESSOR_REJECTED` caller outcome, no issuer actor/task.
2. `exactReplayAddsNoSequenceEventOrSlot` — replay keeps 3 events/HEAD 3/slot 3 and the stored
   `(version 2, committed 3)` pair.
3. `lostResponseIsRecoveredOnceWithNetworkProvenance` — ambiguous-after-commit resolves once as
   `RECOVERED` with network provenance, `(2, 3)` evidence, one event/HEAD/slot.
4. `ambiguousBeforeCommitStaysUnknownWithNoPartialWrite` and
   `issuerDecisionEvidenceIsRefusedBeforeAnyWrite` — abort/refusal leaves the intent untouched
   (2 events, HEAD 2, slot 2, no determined outcome).
5. `issuerConfirmedOutcomeIsRetainedAndNeverOverwritten` — a stored `APPROVED`/`ISSUER` outcome
   cancels the rejection (`NOT_COMMITTED`) and is retained.
6. `missingLookupStaysUnknownAndIsNeverNoRejection` — a failing strong lookup stays `UNKNOWN`; no
   "no rejection" assertion, no fallback, no retry, no resend.

## 6. Limitations and non-claims

DynamoDB Local only; AWS service behaviour (throttling, Streams, IAM, encryption, TTL) remains
UNVERIFIED. Local transaction and recovery mechanics are proven, not cloud behaviour. The
`(5, 8)` capacity is a test example, not approved capacity. `AwsRuntime` is inactive and **G6 is not
complete**: service coupling, late-evidence/conflict variants, `CONTENT_CONFLICT` quarantine,
`collectedAt` retention, principal/context authorization and named OBS-20/21/22 remain open. This is
not QA, Security review or acceptance. No commit, push, PR, merge, deployment, cloud call, apply,
credential flow, deletion or approved-specification edit occurred.

## 7. Next smallest slice

Service-level network-rejection branch consuming `NetworkResultGateway` (return the conservative
`PROCESSOR_REJECTED` channel outcome only for recorded evidence), with `AwsRuntime` still inactive —
or the named ambiguous-result-after-possible-send OBS-22 service scenario. One separately scoped
change, no runtime activation bundled in.
