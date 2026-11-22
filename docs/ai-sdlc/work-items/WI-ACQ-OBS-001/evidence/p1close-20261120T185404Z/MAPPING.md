# P1 closure assessment — OBS-20/21/22 mapping and remaining P1 checklist

Work item: WI-ACQ-OBS-001. Slice: bounded P1 closure assessment (no runtime activation).
Written **before** any test authoring in this slice (mapping first), 2026-11-20T18:54Z.

## Binding

- Repository `/home/pazfernando/projects/acquiring-simulator`, branch `feat/wi-acq-auth-001`,
  HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`, origin
  `https://github.com/pazfernando/acquiring-simulator.git`.
- Configured model provider `nan` / `deepseek-v4-flash`, no override. Runtime inactive.
- Pre-slice freeze: `hashes-before.txt` (132 entries: `src/main/java`, `src/main/resources`,
  `src/test`, `pom.xml`), `git-status-before.txt` (83 entries).

## Prior evidence acknowledged, not recreated

- `evidence/p1b7b-20261120T184718Z/` — 16 targeted store tests, 11 targeted consumer tests and
  **269/269** full suite, exit 0, with empty after-edit/after-run hash diffs. Collection metadata
  (`collectedAt` first-collection retention) is final for that snapshot.
- AUTH integrity link verified this slice: all six SHA-256 values recorded in
  `implementation-progress.md` §28 recompute byte-identically
  (`RESULTS.md 88bace57…`, `PROGRESS.md f0aeae4e…`, `EVIDENCE-MANIFEST.md 7758a588…`,
  `BINDING.txt d1e1e75a…`, `test-stdout.txt 4a7da90a…`, `test-exit.txt 9a271f2a…`).

## Exact scenario criteria (approved draft text, `acceptance-scenarios.md:33-35`)

- **OBS-20** — Given async backends unavailable but required DynamoDB journal+event table and
  critical capacity healthy; one explicit new valid A and timely issuer proof. Then ONE payment and
  critical consultable events commit atomically; APPROVED returned **without awaiting callbacks**; no
  duplicate projection/outbox payload; live gaps visible.
  Rules: R05–06, R18, R24–25; O3/O7.
- **OBS-21** — Given confirmed/possibly-sent payment and notification or ADOT fails; notification
  repair or browser catch-up. Then source retained per proposed window; **retries observations only**,
  no payment retry/rollback, no infinite offline-browser queue, no intermediate-task durability
  promise. Rules: R05–06, R14, R18, R24; O1/O7/O8.
- **OBS-22** — Given possible send followed by evidence/business+critical event atomic transaction
  failure; form response. Then current invocation NOT_CONFIRMED unless committed confirmation is
  readable; unreadable restart UNAVAILABLE; source row/span is not independent financial evidence;
  **no resend**. Rules: R05, R17, R24; C3/C5.

Sourced operating rules: `observation-contract.md:27-28` (event-store failure on the required atomic
write is business-admission failure, not async backend failure; ambiguous commit needs a strongly
consistent lookup; inability to establish state is UNAVAILABLE, not proof of no-send),
`observation-contract.md:68-69` (notifier consumes event rows; retry never updates payment result; no
per-browser queue), `channel-contract.md:47` (Stream/notifier/WS/query outage alone never gates
payment; event **table** outage does gate unsent work or retains conservative uncertainty after
possible send), `channel-contract.md:31,34` (possible-send shape → NOT_CONFIRMED/PENDING; unreadable
restart → UNAVAILABLE), `requirements.md:60` (R24: async notification failure alone does not gate
payment; required storage failure does; no auto resend after possible send).

## Mapping: current implementation and existing evidence

| Scenario criterion | Current implementation | Existing test evidence | Meaningful gap |
| --- | --- | --- | --- |
| OBS-20 atomic one-payment commit + immediate APPROVED | `DynamoClaimGateway` + `DynamoDispatchIntentGateway` + `DynamoResultGateway` over one `DynamoEventStore` and journal; service 9-arg atomic path | Transition tests per step (`DynamoCriticalClaimIntegrationTest`, `DynamoDispatchIntentGatewayIntegrationTest`, `DynamoResultGatewayIntegrationTest`) and service-double tests (`DispatchIntentServiceWiringTest`, `ResultServiceWiringTest`); `AtomicClaimServiceWiringIntegrationTest` stops at the claim step (no intent/result gateway) | **No end-to-end run of all three real adapters through `AuthorizationService`.** Genuine gap. |
| OBS-20 no duplicate projection/outbox payload | single event table; dedupe row holds hash + identity metadata only, no second payload | `DynamoEventStoreIntegrationTest` exact replay/dedupe; `DynamoResultGatewayIntegrationTest.exactReplayAddsNoVersionEventOrSlot` | Same end-to-end shape above; assert one row per critical identity. |
| OBS-20 async backends unavailable | notifier / WS / query adapter / ADOT do **not exist** (P4, P6) — structural absence, not a simulated outage | none (nothing to exercise) | Cannot be simulated without feature expansion (forbidden this slice). The end-to-end test proves the payment path has no such collaborator; it does **not** prove recovery of a failed notifier. |
| OBS-21 notification/ADOT failure → observation-only retry, no payment retry/rollback | no notifier or ADOT component exists; no observation/notification port | `DynamoResultGatewayIntegrationTest.exactReplayAddsNoVersionEventOrSlot`, `ResultServiceWiringTest.duplicateResultDoesNotResend`, `DispatchIntentServiceWiringTest.recoveredIntentIsKnowledgeOnlyAndNotPermissionToResend`, `DynamoEventStoreIntegrationTest` retained-read/HEAD | **Not expressible now.** Any test would have to invent a notification seam = feature expansion. Remains OPEN for P4/P6. |
| OBS-22 ambiguous after possible send → NOT_CONFIRMED, no resend | `ResultGateway` UNKNOWN/NOT_COMMITTED → service `NOT_CONFIRMED`; adapter one-lookup recovery; never resend | `DynamoResultGatewayIntegrationTest.ambiguousBeforeCommitStaysUnknownWithNoPartialResult`, `.strongReadFailureStaysUnknownAndIsNeverNoResult`; `ResultServiceWiringTest.unknownResultFabricatesNoFinancialOutcomeAndNoLegacyWrite`, `.notCommittedResultFabricatesNoFinancialOutcome`; `DynamoAtomicClaimRecoveryIntegrationTest` ambiguous-before-commit → UNKNOWN | Criterion **already covered**; a named duplicate would be cosmetic. Cite, do not re-add. |
| OBS-22 storage failure **before** send prevents dispatch | `DispatchIntentGateway` NOT_COMMITTED/UNKNOWN → no downstream; slot/capacity condition aborts the same transaction | `DispatchIntentServiceWiringTest.cancelledIntentPreventsDownstream…`, `.unknownIntentRemainsUncertain…` (service doubles); `DynamoDispatchIntentGatewayIntegrationTest.cancelledIntentLeavesTheClaimUntouched` (adapter only) | No run that fails the **real** intent transaction through the service. Genuine gap. |
| OBS-22 unreadable restart → UNAVAILABLE | service returns NOT_CONFIRMED; no UNAVAILABLE envelope (C3 builder not implemented) | `DynamoResultGatewayIntegrationTest.strongReadFailure…` (UNKNOWN, conservative) | Response-envelope naming belongs to P2/G3; do not invent `UNAVAILABLE` status here. |

## Decision for this slice

Author **at most two** coherent acceptance-scenario integration tests in one new class, both using
the real adapters through `AuthorizationService`:

1. `OBS-20` full atomic success chain (claim → dispatch intent → verified result), with the explicit
   test capacity example (never a production default), asserting one payment record, three critical
   events with distinct identities, one HEAD at sequence 3, one slot row at 3, exactly one downstream
   call, APPROVED returned in the same invocation, and a later independent read that adds no row.
2. `OBS-22` real atomic failure **before** send: an explicit test-only capacity whose remaining budget
   the intent slot reservation cannot satisfy, so the composed transaction aborts — outcome
   NOT_CONFIRMED, zero downstream calls, only the claim event retained, no result event/slot abuse.

Rationale: both are missing at the real-adapter level and both assert the approved criteria, not a
renamed assertion. OBS-21 is not testable without a notification subsystem and stays open. OBS-22's
ambiguity half is already covered and is cited, not duplicated.

## Remaining P1 checklist (G5/G6/G7) after this slice

| # | Item | State |
| --- | --- | --- |
| 1 | G5 canonical contract, sanitizer, wrapper, manifest, dedupe identity | COMPLETE locally (P1a) |
| 2 | G5 `collectedAt` first-collection retention (redelivery/restart/read) | COMPLETE locally (P1b7b) |
| 3 | G7 EVENT keys `E#<12-digit>`/HEAD, sequence CAS, dedupe, integrity marker | COMPLETE locally (P1b1 + P1b7a) |
| 4 | G6 atomic claim / intent / result / network / late-evidence composers and gateways | IMPLEMENTED, each locally tested |
| 5 | G6 end-to-end atomic success through the real adapters and the service | Covered by this slice's OBS-20 test |
| 6 | G6 atomic failure before send cannot dispatch | Covered by this slice's OBS-22 test |
| 7 | G6 `AwsRuntime` activation (construct atomic gateways; retire legacy claim/send) | **NOT STARTED** — runtime still builds the 4-arg legacy service |
| 8 | Durable `CONTENT_CONFLICT` quarantine / late-evidence quarantine flag | PARTIAL — integrity/conflict marker only; late contradiction refuse-and-preserve |
| 9 | Legacy `NOT_CONFIRMED` disposition and timeout-event vocabulary | OPEN — needs an approved-text decision |
| 10 | Principal/context authorization before dedupe tied to the business principal | CONTEXT-ONLY test seam (event-partition context check); **not** P5 IAM/JWT, not P2b C4 crypto |
| 11 | OBS-20 / OBS-22 named coverage | This slice (see results appended below/§30) |
| 12 | OBS-21 notification/ADOT observation-only retry | **OPEN** — requires P4/P6; not simulated |
| 13 | P2b C4 cryptographic evidence carriage; P5 actual IAM/JWT | Out of P1; different phases |
| 14 | Expiry worker/ranges/query API (P3); Streams/WS/notifier (P4) | Not started by design |

Non-claims: this assessment invents no business rule and marks no phase complete. `AwsRuntime`
remains inactive; full P1/G6 remains open. No commit, push, PR, merge, deploy, cloud/apply call,
credential flow or destructive change occurred.

## Execution results (appended after the targeted run)

New file only: `src/test/java/com/acquiring/simulator/aws/ObsAcceptanceAtomicChainIntegrationTest.java`
SHA-256 `48be479e627f1827e2d2fa5d3013ac03b5e697dc3fe321f2b2c7117882eb5269`. No production file was
edited; `hashes-before.txt` → `hashes-after-run.txt` differs by exactly that one added line and
`git-status-before.txt` → `git-status-after.txt` by exactly that one untracked path.

| Attempt | Command (absolute JDK 25.0.5 / Maven 3.10.1, offline) | Exit | Result |
| --- | --- | --- | --- |
| compile | `mvn -o -q -DskipTests compile` | 0 | empty stdout/stderr |
| 1 | `mvn -o -Dtest=ObsAcceptanceAtomicChainIntegrationTest test` | 1 | **testCompile only**: wrong constructor arity (`DynamoResultGateway` passed where `IdentifierSource` expected). Log preserved as `obs-chain.*`; production code untouched. |
| 2 | same selector after the test-only fix (8-arg constructor + `IdentifierSource.randomHex()`) | 0 | **2 tests, 0 failures, 0 errors, 0 skipped, 5.943 s, BUILD SUCCESS**; stderr empty |

Evidence filenames are unique per attempt (nothing overwritten): `compile.{stdout,stderr}.log`,
`compile.exit`, `obs-chain.{stdout,stderr}.log`, `obs-chain.exit`,
`obs-chain-attempt2.{stdout,stderr}.log`, `obs-chain-attempt2.exit`, surefire XML/TXT.

### What the two tests actually assert

1. `obs20OnePaymentAndCriticalEventsCommitAtomicallyAndApproveWithoutAsyncBackends` — real
   `DynamoClaimGateway` → `DynamoDispatchIntentGateway` → `DynamoResultGateway` through the 8-arg
   atomic service: `APPROVED` in the same invocation, one downstream call, no legacy claim or
   `markDispatched` write, one business record (`APPROVED`/`ISSUER`/dispatched), exactly three event
   rows `E#000000000001..3` with distinct hashes, HEAD 3, slots 3/8, exactly three dedupe rows with
   **no payload attribute**, no journal-table evidence event, and a repeat observation read that adds
   no row and no dispatch.
2. `obs22RequiredAtomicWriteFailureBeforeSendDoesNotDispatchOrResend` — explicit test budget `(1,1)`
   so the claim consumes the only slot and the real `POSSIBLE_SEND` transaction cannot commit:
   `NOT_CONFIRMED`, zero downstream calls, zero legacy dispatch writes, exactly the claim event
   (sequence 1, HEAD 1, slots 1/1, one dedupe row), record still `AWAITING` with null
   `dispatchedAt`/`dispatchCorrelationId`/`outcomeDeterminedAt`, and a repeat read that dispatches
   nothing.

### Coverage honesty

- The full 269-test suite was **not** re-run; its pass remains evidence for the pre-existing tree and
  does **not** include these two tests. No full-suite claim is made for this tree.
- OBS-20's async backends (notifier, WebSocket, query adapter, ADOT) do not exist in this phase, so
  the test proves the payment path has no dependency on them; it does **not** prove recovery from a
  failing notifier. That half of OBS-20 and all of OBS-21 remain open for P4/P6.
- OBS-22 ambiguity/UNKNOWN and no-resend were already covered (cited above) and were not duplicated;
  this slice adds only the real pre-send atomic-abort path.
- DynamoDB Local only. No runtime activation, no C4 cryptography, no P5 IAM/JWT, no cloud call.
