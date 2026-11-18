# Authorization core scenario coverage

Recorded 2026-11-18 on branch `feat/wi-acq-auth-001`.

This document maps the required authorization scenarios (A01-A09 and
A12-A26, 24 scenarios) to executable assertions. A10 and A11 are deferred and
are not claimed here. The same database-agnostic scenario class runs against
both engines:

- `AuthorizationCoreIntegrationTest` - local profile, SQLite.
- `PostgresAuthorizationCoreIntegrationTest` - test profile, isolated
  `postgres:17-alpine` Testcontainer. No shared or production database is used.

## Result

Command:

```bash
export JAVA_HOME=$HOME/.local/opt/jdk-25
export PATH=$JAVA_HOME/bin:$HOME/.local/opt/maven/bin:$PATH
mvn -B verify
```

| Engine | Tests run | Failures | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| SQLite (local profile) | 24 | 0 | 0 | 0 | pass |
| PostgreSQL 17 (test container) | 24 | 0 | 0 | 0 | pass |
| **Total** | **48** | **0** | **0** | **0** | **BUILD SUCCESS** |

Toolchain: Temurin 25.0.5, Apache Maven 3.10.1, Spring Boot 4.1.2, Docker
29.1.3.

## Scenario-to-assertion mapping

All method names below are in
`src/test/java/com/acquiring/simulator/AuthorizationCoreScenarios.java`.

| Scenario | Executable assertion(s) | What is asserted |
| --- | --- | --- |
| A01 | `approvesValidPurchaseWithIssuerProvenance` | Approved outcome and reference; persisted state `APPROVED`, decision source `ISSUER`, domestic route, `CONFIRMATION` evidence. |
| A02 | `distinguishesIssuerDeclineFromProcessorRejection` | `SIM-DEC-*` yields `ISSUER_DECLINED` with issuer provenance, distinct from processor rejection. |
| A03 | `rejectsCallerWithoutAuthorityOverTheMerchant` | Caller without authority over the merchant is rejected, no reference, no dispatch. |
| A04 | `rejectsEachMalformedOrOutOfProfileInputBeforeDispatch` | Null credential, unsupported currency, amount below/above/invalid scale, null CVM, null cryptogram all reject with an audit row before dispatch. |
| A05 | `rejectsUnknownMerchantAndUnknownTerminal` | Unknown merchant and unknown terminal reject with `UNKNOWN_MERCHANT` / `UNKNOWN_TERMINAL`; no dispatch. |
| A06 | `rejectsUnknownAcquirerWithoutUsingAnotherMandate` | Unknown acquirer rejects without borrowing another mandate; no dispatch. |
| A07 | `timeoutAndAmbiguousReplyRemainNotConfirmed` | Timeout, malformed reply and wrong correlation all remain `NOT_CONFIRMED`, never `APPROVED`. |
| A08 | `identicalReplayReturnsOriginalReferenceWithoutRedispatch` | Identical replay returns the original reference and result; exactly one dispatch. |
| A09 | `lateConfirmationUpdatesLatestResultAndPreservesUncertainty` | Late evidence becomes the latest result, timeout evidence is retained, no decline event is fabricated, linked retry returns the same reference with one dispatch. |
| A12 | `invalidOrForeignPriorReferenceRejectsBeforeReplayDisclosure` | Malformed and unknown prior references reject with `INVALID_PRIOR_REFERENCE`; foreign prior references reject with `FOREIGN_PRIOR_REFERENCE`; original stays approved and undisclosed. |
| A13 | `conflictingDuplicateRejectsWithoutMutatingOriginal` | Duplicate with changed data rejects `PAYMENT_DATA_CONFLICT`; original amount and state unchanged; one dispatch. |
| A14 | `lateConfirmationUpdatesLatestResultAndPreservesUncertainty` | Same test as A09: late evidence survives as the latest result rather than being discarded. |
| A15 | `noPermittedRouteRejectsBeforeDispatch` | Empty permitted-route set and a route not held by the credential's acquirer reject before dispatch. |
| A16 | `concurrentIdenticalArrivalsProduceOneAuthorizationAndOneDispatch` | Two simultaneous identical arrivals yield one reference and exactly one dispatch. |
| A17 | `inFlightReplayReturnsPendingWithoutWaitingOrSecondDispatch` | Replay while the original is in flight returns `NOT_CONFIRMED` immediately; no second dispatch. |
| A18 | `invalidOrForeignPriorReferenceRejectsBeforeReplayDisclosure` | Same test as A12: invalid or foreign links never disclose the prior decision. |
| A19 | `linkedChangedProfileValidDataIsConflict` (a), `linkedMandatoryValidationRejectsBeforeConflictOrDisclosure` (b) | (a) amount, credential, CVM, entry mode and cryptogram changes each reject `PAYMENT_DATA_CONFLICT`, original unchanged, no redispatch; omitted risk resolves to explicit `PASS` and replays. (b) malformed, unknown and cross-merchant inputs reject with their mandatory-validation reasons, never `PAYMENT_DATA_CONFLICT`, before disclosure; original unchanged. |
| A20 | `linkedRetryWithChangedBusinessKeyRejects` | Linked retry with a different channel/business key rejects `INCOMPATIBLE_LINKED_RETRY`; one dispatch. |
| A21 | `linkedRetrySurvivesWindowExpiryAtEveryBoundary` | Linked retry succeeds just before, exactly at, just after and well beyond the duplicate window; one dispatch. |
| A22 | `unlinkedBoundaryCreatesNewPaymentAtExactWindowEnd` | Unlinked arrival just before the boundary replays; exactly at/after the boundary creates generation 2 and a second dispatch; original stays approved. |
| A23 | `timeoutAndAmbiguousReplyRemainNotConfirmed`, `contradictoryLaterConfirmationIsRetainedWithoutReplacingDecision` | Ambiguity never confirms; a contradictory later confirmation is retained as `CONTRADICTORY_CONFIRMATION` without replacing the approved decision. |
| A24 | `confirmationExactlyAtDeadlineWinsOverTimeout` | A confirmation available exactly at the deadline is approved and no `TIMEOUT` event is written. |
| A25 | `combinedRetryAfterLateConfirmationReturnsLatestButRejectsConflictAndForeignLink` | After a late confirmation, linked retry returns the latest result with one dispatch; changed data rejects `PAYMENT_DATA_CONFLICT`; a foreign prior reference rejects `FOREIGN_PRIOR_REFERENCE`; timeout and late-confirmation evidence are retained. |
| A26 | `profileEdgesAndDefaultsAreEnforced` | Minimum/maximum amounts, contactless without cryptogram, risk reject, unknown credential, invalid amount scale and null entry mode are enforced. |

## Supplementary coverage

`retainedClaimWithoutDispatchIsNeverRedispatchedAfterRestart` is not one of the
required 24 scenarios. It inserts a claimed-but-undispatched record and asserts
a later identical arrival returns `NOT_CONFIRMED` for the retained record
without redispatch.

## Deferred

- A10 and A11: deferred, not implemented or claimed in this checkpoint.
- Independent QA and Security review: pending.
- No publication, deployment, or shared-documentation change is part of this
  checkpoint.
