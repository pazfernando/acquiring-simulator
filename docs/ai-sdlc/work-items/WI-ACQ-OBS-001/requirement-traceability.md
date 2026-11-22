# WI-ACQ-OBS-001 — requirement-to-evidence traceability (T1)

Date: 2026-11-20. Author: Implementation (OpenCode ACP, configured default model, no override).
Status: **working traceability matrix — developer evidence only.** It is not an independent QA or
Security verdict, not an integration claim and not human acceptance. Every row is
`independent verification: PENDING`.

This document is the T1 requirement-to-evidence matrix required by
[the completion plan](operation-a-completion-plan.md) task T1. It maps the 25 requirements and 28
acceptance scenarios of this work item, plus the linked business rows of
[WI-ACQ-AUTH-001](../WI-ACQ-AUTH-001/WORK_ITEM.md) that are actually evidenced here, to real
implementation paths and to test cases that were **actually executed** in one frozen run.

## 1. Binding and evidence basis

| Item | Value |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged; no commit, stage or push) |
| Origin | `https://github.com/pazfernando/acquiring-simulator.git` |
| Model | provider `nan`, model `deepseek-v4-flash`; configured default, no override |
| Executed suite reused | [evidence/p2c4-fullsuite-20261120T224632Z](evidence/p2c4-fullsuite-20261120T224632Z/RESULTS.md) — **959 tests / 63 classes / 0 failures / 0 errors / 0 skipped / exit 0** |
| Suite rerun in this slice | **No.** The instruction was to reuse it, and the frozen bytes it covered are the bytes this matrix describes. |
| Freeze correspondence | The 282 frozen inputs of that run (194 `src/main`, 68 `src/test`, `pom.xml`, 13 vectors, 6 approved contracts) were byte-identical before/after it, so the green run exercised these sources. |
| Slice evidence | [evidence/t1-traceability-20261120](evidence/t1-traceability-20261120/) |

Pinned specification sources, re-verified by SHA-256 at the start of this slice
(`evidence/t1-traceability-20261120/source-hashes.sha256`):

| Document | SHA-256 |
| --- | --- |
| `adr-operation-a-access-and-environment.md` | `d51bbf650aca74e82351dacf0e45d0f14c992044b9ff246bc7b8b281920b0b39` |
| `owner-decisions.md` | `efef3d2272dd54130b404fae43ec90643bc42c5dd4701cc1b6b4e49d4ea0f18c` |
| `requirements.md` | `49c5d412e3705c320a2f78b190555f49e7e0fa4662cff9329d259f89e2b459df` |
| `channel-contract.md` | `495e1172e76ab9ba81f8a8026e7efec25baa6e4fff0d754c0a4c92bd38ea3bca` |
| `observation-contract.md` | `ca53e384a46d24b3c1119b3e6ba804a9a9d4caac049a6b0c55a40c72842a205a` |

**Concurrent Coordinator update, disclosed:** `operation-a-completion-plan.md` matched the
commissioned pin `6e75d527253e11f332abec3787d984da328ccb5f9605a2827f144ad513603f2f` when this
slice opened and was **appended by the Coordinator at 2026-11-20T23:56:35Z** (95 lines; new
section "Execution resumed — 2026-11-20"). Its current hash is
`cc522f02fd4dafbd4fd27767a7e9d2bc503f2019f788325ed57606a7c8f55672`. Only narrative was added;
the T1–T10 table is unchanged and this matrix uses it. The file was not edited here.

The current access/environment direction is the owner ADR, not the earlier recommendation:
external/internal **API Gateway per scenario, mTLS, OAuth 2.0 `private_key_jwt`**; GitHub Actions
OIDC with variables/Secrets and region `us-east-2`; on-demand billing with **no** approved business
quota or cost ceiling; one-week TTL where applicable; existing Security specialist. `private_key_jwt`
is a token-endpoint client authentication method — it is neither an API bearer token nor C4
attestation evidence. The former IAM/SigV4 application-ingress recommendation is **not** the
selected direction.

## 2. How to read this matrix

Status tokens (aligned with [delivery-status.md](delivery-status.md)):

- **A — implemented + developer-tested**: code exists and one or more executed tests assert the
  stated behavior on the frozen tree.
- **B — partially implemented + developer-tested**: a real subset of the row is implemented and
  asserted; the named remainder is absent, and the gap column says what is missing.
- **C — not implemented**: no code path or no executed test covers the row's observable behavior.

Two independent axes are recorded per row, because they are routinely conflated:

- **Runtime vs test-only**: whether the behavior is reachable from the deployed application entry
  points, or only from tests/internal seams. Today `AwsRuntime.processor()` still constructs the
  four-argument legacy `AuthorizationService` (repository + `LambdaDownstream`); it constructs
  **none** of the atomic gateways and no event store. Everything built in P1/P2 is therefore
  *test-only construction* until T3 wires it.
- **Integrated**: whether a cross-component application journey actually ran. For this tree the
  answer is **No on every row**: the strongest evidence is adapter/service integration against
  DynamoDB Local, PostgreSQL/Testcontainers or in-process doubles. Component integration is not the
  end-to-end payment journey.

Three distinctions this matrix will not blur:

1. **Green is not acceptance.** 959 passing tests describe components on one frozen commit. They do
   not establish an executed payment journey, deployment, or independent verdict.
2. **C4 signing tests are not transport authentication.** `C4EvidenceVerifierTest`,
   `C4P6SignedFieldBinderTest` and `C4LateDeliveryTest` verify Ed25519 signatures with ephemeral
   in-memory keys and bind trust metadata. No TLS, mTLS, IAM/SigV4 or OAuth client authentication
   exists; `C4HttpCarriageBridge` only *accepts* a `C4AuthenticatedTransportContext` supplied by a
   platform boundary that does not exist yet.
3. **Test-only construction is not a runtime path.** See the axis above.

Acceptance scenarios are acceptance **specifications**, not tests: `acceptance-scenarios.md` is
marked DRAFT / NOT EXECUTED. A scenario marked B below means its *components* are asserted, never
that the scenario ran.

## 3. Coverage counts actually mapped

| Set | Rows | A | B | C | End-to-end integrated | Independent verification |
| --- | --- | --- | --- | --- | --- | --- |
| OBS-001 requirements R01–R25 | 25 | 13 | 9 | 3 | 0 | 0 (all PENDING) |
| OBS-001 scenarios OBS-01–OBS-28 | 28 | 3 | 19 | 6 | 0 | 0 (all PENDING) |
| Linked AUTH-001 business rows (relevant subset) | 14 | 13 | 1 | 0 | 0 | 0 (all PENDING) |

These counts are counts of **rows mapped**, not tests passed and not percent complete. The AUTH-001
set is deliberately a *subset*: no complete AUTH-001 requirement-to-evidence matrix exists yet
(its `evidence/` holds run folders only, no matrix), so nothing here should be read as AUTH-001
coverage closure.

## 4. OBS-001 requirements R01–R25

Evidence names are `Class.method` in the 959-test run unless stated otherwise. `(n)` is that
class's test count in the run.

| ID | Status | Implementation | Executed evidence | Runtime vs test-only | Remaining gap | Task | IV |
| --- | --- | --- | --- | --- | --- | --- | --- |
| R01 | C | No console or history-read component exists (`src/main` has no UI/history read model); only append-side `DynamoEventStore` | `DynamoEventStoreIntegrationTest` (16): `firstCriticalAppendAllocatesSequenceOneAndStoresThePayloadOnce`, `concurrentAppendsAllocateContiguousUniqueSequences` | Test-only; no UI, no list/detail API | HTML POS form, live update without reload, persisted detail after reload | T4, T5, T6 | PENDING |
| R02 | A | `OperationAManifest`, `EventManifest`, `EventValidator`, `EventTask` | `OperationAManifestTest` (7): `declaresEveryOperationATaskFromTheObservationContract`, `onlyTheProcessorHoldsCriticalCommitAuthority`; `EventValidatorTest` (14): `unknownTaskIsRejectedAtIngress` | Contract is test-only; no producer invokes the hook at runtime | Real task producers/hook binding to the payment path; unimplemented operations remain disabled by absence, not by a runtime switch | T3, T7 | PENDING |
| R03 | A | `ChannelRequestValidator`, `ServerResolvedInstructionMapper`, `Iso8583RequestValidationPipeline`, `C4HttpCarriageBridge` | `ChannelRequestValidatorTest` (20) incl. `rejectsUnknownCallerFieldsWithoutEchoingThem`; `ServerResolvedInstructionMapperTest` (3) `refusesAuthorizedContextThatDoesNotMatchTheRequest`; `Iso8583RequestValidationPipelineTest` (53) | Test-only; `/internal/sim-iso87a/v1/authorizations` primitive is not mounted | Mounted ACL boundary and live ISO dispatch | T2, T3 | PENDING |
| R04 | A | `ProcessorHandler` HTTP JSON payload v2, `LambdaDownstream` synthetic JSON | `AwsTransportTest` (6): `lambdaInvocationIsSynchronousBoundedAndWireContractRoundTrips`, `functionErrorsAndInvalidPayloadsRemainUnconfirmed` | Legacy runtime path is the JSON baseline; ISO path is unmounted | Replace JSON ingress with the ISO ACL path; keep baseline characterization in docs | T3 | PENDING |
| R05 | A | `AuthorizationService`, `Outcome`, `DecisionSource`, `RejectionReason`, `DeadlineDisposition` | `AuthorizationCoreIntegrationTest` (24): `distinguishesIssuerDeclineFromProcessorRejection`, `timeoutAndAmbiguousReplyRemainNotConfirmed`, `approvesValidPurchaseWithIssuerProvenance`; `PostgresAuthorizationCoreIntegrationTest` (24) mirrors it | Runtime uses the legacy repository; atomic path test-only | Emit the four dispositions from the new atomic path; no fabrication on missing evidence | T3, T4 | PENDING |
| R06 | A | `ClaimGateway`, `DispatchIntentGateway`, `ResultGateway`, `AuthorizationService` | `identicalReplayReturnsOriginalReferenceWithoutRedispatch`, `concurrentIdenticalArrivalsProduceOneAuthorizationAndOneDispatch`, `retainedClaimWithoutDispatchIsNeverRedispatchedAfterRestart`; `DispatchIntentServiceWiringTest` (9) `replayedIntentDoesNotSendTwice`; `ResultServiceWiringTest` (12) `duplicateResultDoesNotResend` | Test-only adapter paths; legacy runtime still sends through `LambdaDownstream` | Runtime must not retain a legacy resend path once T3 switches wiring | T3 | PENDING |
| R07 | A | `LateEvidenceAdmissionGateway`, `LateSignedEvidenceReceiptGateway`, `LateEvidenceCriticalComposer`, `C4LateDeliveryVerifier`, `C4P6SignedFieldBinder` | `DynamoLateEvidenceTransitionIntegrationTest` (11) `lateApprovalAfterTheBoundaryCommitsOneConfirmedResult`, `aConfirmedOutcomeIsNeverOverriddenByLateEvidence`; `LateSignedEvidenceReceiptGatewayIntegrationTest` (6); `C4P6SignedFieldBinderTest` (18) `bindingDecisionClaimsNoConfirmedResultBeforeAtomicReceipt` | Test-only; no authorized runtime late-evidence seam | Runtime admission seam with real key custody; original-reference retention across restarts | T2, T3 | PENDING |
| R08 | B | `AuthorizedContext` + `CALLER#<subject>` registry (`AwsRuntime.caller`), `AuthorizationService` context checks | `rejectsUnknownAcquirerWithoutUsingAnotherMandate`, `rejectsCallerWithoutAuthorityOverTheMerchant`, `rejectsUnknownMerchantAndUnknownTerminal`; `AwsTransportTest.unauthenticatedOrUnregisteredCallerCannotSupplyTheirOwnContext` | **Context-only test seam**; not a service principal | Real service identity per ADR (mTLS + OAuth2 `private_key_jwt` at API Gateway), bound to mandate/context | T2 | PENDING |
| R09 | A | `EventSanitizer`, `SafeIdentifier`, closed event schema, `ChannelRequestValidator` | `EventSanitizerTest` (6) `panShapedDigitRunIsRejected`, `compactJwtIsRejectedWithoutEchoingTheToken`, `pinBlockWordingIsRejected`; `SafeIdentifierTest` (6); `GeneratedIdentifierSanitizerTest` (1) | Server-side only; no log/UI surface exists to inspect | Whole-path proof incl. logs and console text; Security review of the allowlist | T3, T7, T10 | PENDING |
| R10 | A | Separate `NetworkResultGateway` and `ResultGateway`; issuer/network roles in `C4PrincipalRole` | `DynamoNetworkResultGatewayIntegrationTest` (7) `issuerDecisionEvidenceIsRefusedBeforeAnyWrite`; `C4EvidenceVerifierTest` (33) `networkRoleCannotSignIssuerDecision`, `relayCannotSubstituteNetworkSignatureForIssuerSignature`; `NetworkResultServiceWiringTest` (12) `issuerReplyNeverReachesTheNetworkGateway` | Test-only; no diagnostic view exists | Wire both branches at runtime; ensure no diagnostic path can replace processor outcome | T3 | PENDING |
| R11 | A | `CanonicalEventCodec`, `EventTableKeys`, `EventSourceWrapper`, `EventDedupeKey`, `EventSubmission` | `CanonicalEventCodecTest` (7) `canonicalJsonAndHashMatchTheApprovedEv1Oracle`, `producerCannotSupplyCollectorFields`; `EventTableKeysTest` (5) `eventSortKeyIsTwelveDigitZeroPaddedCommitOrder`; `EventSubmissionTest` (4) | Test-only; allocation happens only under test drivers | Durable ID/alias allocation on the runtime path | T2, T3 | PENDING |
| R12 | B | Manifest + validator + `EventDurability` + `CriticalCapacity`; `OperationAManifest` | `EventValidatorTest.skippedTaskNeedsDeclaredReasonOrEvidence`; `OperationAManifestTest.networkAndIssuerTasksRemainConditionalBranches`; `DynamoEventStoreIntegrationTest.businessCompositionIsRejectedForBestEffortEvents`, `bestEffortEventCannotAssertJournalMetadata` | Test-only; no producer emits observations | Emit started/terminal observations from real tasks; expose explicit coverage gaps; missing terminal ≠ FAILED | T3, T5, T7 | PENDING |
| R13 | A | `EventContext`, `EventClockQuality`, `StoredEvent`, `EventSourceWrapper` | `DynamoEventStoreIntegrationTest.criticalCollectionTimeEqualsCommitTimeAndSurvivesReplay`, `legacyDedupeMetadataWithoutCollectionTimeReadsAsExplicitUnknown`; `EventValidatorTest.elapsedDurationRequiresSameClockStartAndEnd`, `nonUtcOrMalformedTimesAreRejected` | Persistence test-only; nothing displays it | Display of occurrence/collection/skew/unknown duration in the timeline | T4, T5 | PENDING |
| R14 | B | `AuthorizationState`/`Outcome`, `EventDurability`, `C3ResponseEnvelope` freshness/observation fields | `C3ResponseBuilderTest` (9) `unknownObservationForcesUnknownFreshness`, `envelopeRejectsAPaymentOutcomeOnAnUnavailableRow`, `envelopeIsExactlyTheClosedThirteenKeySet` | Envelope is unit-tested; no server emits it, no connection state exists | Server freshness + task-coverage + connection state served separately from financial state | T4, T5 | PENDING |
| R15 | C | No timeline/notifier/console component exists | None. Only contract-level assertions (`C3ResponseBuilderTest`, event contract tests) | N/A | Correlated live timeline UI with provenance and independent freshness/completeness | T5, T6 | PENDING |
| R16 | A | `EventDeduplication`, `EventIntegrityConflict`, `SignedEvidenceConflict`, gateway quarantine flags | `EventDeduplicationTest` (3) `sameHashIsExactReplayAndDifferentHashIsConflict`; `DynamoEventStoreIntegrationTest.changedPayloadForTheSameIdentityIsAConflictWithoutOverwrite`; `SignedEvidenceReceiptGatewayIntegrationTest` (15) `reusedEvidenceIdentityWithChangedSignedContentIsQuarantinedAndPreservesTheOriginal`, `duplicateQuarantineFlagIsStableAndKeepsTheFirstConflictingWitness` | Test-only; quarantine is not readable by any API | Surface observation-integrity failures in the read model; keep them out of payment evidence | T3, T4 | PENDING |
| R17 | B | Append-only event store + verified-evidence gates | `DynamoLateEvidenceTransitionIntegrationTest.exactReplayOfIdenticalLateEvidenceAddsNoVersionEventOrSlot`; `DynamoResultTransitionIntegrationTest` (7) `exactReplayOfTheResultDoesNotDuplicateEventSequenceOrSlot` | Server append only; no client derives a timeline | Client-side derivation from one source; P5-only outcome change enforced end to end | T4, T5 | PENDING |
| R18 | B | Critical composers + `DynamoEventStore` composed transaction; no projection/outbox | `DynamoCriticalClaimIntegrationTest` (8) `claimCommitsBusinessHeadEventHeadDedupeAndSlotInOneTransaction`, `exhaustedSlotBudgetAbortsTheWholeClaim`; `EvidenceReceiptAtomicIntegrationTest` (4); `DynamoEventStoreIntegrationTest.failedComposedWriteLeavesNoEventHeadOrDedupe` | Atomic write is adapter-integrated; read/subscribe absent | Subscribe + ack before fixed-snapshot catch-up; Streams replay and truncation; query path | T4, T5 | PENDING |
| R19 | B | `ProcessorHandler` C1 JWT (`requestContext.authorizer.jwt.claims.sub` → registry) only | `AwsTransportTest.authenticatedInputUsesTrustedRegistryAndReturnsOnlyOutcomeAndReference`; `DynamoEventStoreIntegrationTest.unauthorizedContextCannotPoisonTheConflictMarker` | C1 demo-JWT path is real in the legacy handler; no relay, no query scoping; service ingress unimplemented | Loopback relay, bounded query/subscription scope, service identity per ADR, no issuer-truth view | T2, T6 | PENDING |
| R20 | B | `EventSanitizer`, closed canonical schema, `SafeIdentifier` | `EventSanitizerTest` (6); `CanonicalEventCodecTest.undeclaredPayloadFieldsAreRejected`; `ChannelRequestValidatorTest.rejectsUnknownCallerFieldsWithoutEchoingThem` | Server-side only | Inert rendering of all supplied text in the console; log coverage | T3, T6 | PENDING |
| R21 | A | `ChannelRequestValidator`, `ServerResolvedInstructionMapper`, `Iso8583DomainValidator`, `Iso8583ExchangeBudgetValidator` | `mapsNewDispatchToTheExactByteOracle`, `refusesNewDispatchThatExceedsTheDerivedExchangeBudget`; `Iso8583DomainValidatorTest` (9) `rejectsEveryCurrencyOtherThanUsd`; `Iso8583ExchangeBudgetValidatorTest` (7) `acceptsEightTwentySixAndRejectsEightTwentySevenAtTheDerivedBoundary` | Test-only; no live ACL route | Runtime server-side fixture/route resolution and dispatch | T2, T3 | PENDING |
| R22 | A | `Iso8583ResponseValidationPipeline`, `Iso8583P4MatrixValidator`, `C3ResponseBuilder` | `Iso8583ResponseValidationPipelineTest` (51); `Iso8583P4MatrixValidatorTest` (62) `ninetyOneIsNeverAnIssuerDecline`, `thirtyIsNeverAnIssuerDecision`; `C3ResponseBuilderTest.denialWinsWithoutLeakingAPosteriorConfirmation` | Test-only; no channel response is emitted at runtime | Emit the validated channel result from the runtime path; preserve uncertainty | T3 | PENDING |
| R23 | B | Full `SIM-ISO87A-v1` codec/ACL package + C1–C4 contracts (`iso8583-*`, `channel/*`) | `Iso8583ScannerTest` (94), `Iso8583PackerTest` (53) `repacksEveryApprovedPositiveFixtureByteExact`, `Iso8583RequestValidationPipelineTest` (53), `C4CarrierParserTest` (18) | Component-complete; **HTTPS transport authentication absent** | C2 service principal on mTLS + OAuth2 (`private_key_jwt`); real TLS termination | T2, T3, T8 | PENDING |
| R24 | B | Atomic business+critical-event composers; no dual write | `ObsAcceptanceAtomicChainIntegrationTest` (2) `obs20OnePaymentAndCriticalEventsCommitAtomicallyAndApproveWithoutAsyncBackends` | Test-only; async side does not exist | Streams/notifier/ADOT path where async failure provably cannot gate payment | T3, T7 | PENDING |
| R25 | C | Only the append-side event store exists | None for live delivery or HTTP reads | N/A | Streams trigger, notifier, WebSocket subscribe/ack/catch-up, HTTP history reads, no polling | T4, T5 | PENDING |

## 5. Acceptance scenarios OBS-01–OBS-28

| ID | Status | Executed component evidence | What the scenario still needs | Task | IV |
| --- | --- | --- | --- | --- | --- |
| OBS-01 | B | ACL/ISO: `ServerResolvedInstructionMapperTest.mapsNewDispatchToTheExactByteOracle`; result: `approvesValidPurchaseWithIssuerProvenance`; storage: `obs20OnePaymentAndCriticalEventsCommitAtomicallyAndApproveWithoutAsyncBackends` | HTML submission, live update without reload, detail surviving reload | T3, T5, T6 | PENDING |
| OBS-02 | B | `distinguishesIssuerDeclineFromProcessorRejection`; `deliveredIssuerDeclineAndConfirmedStateCannotRegress`; `C4EvidenceVerifierTest.directIssuerDeclineVerifies`; `C3ResponseBuilderTest.denialWinsWithoutLeakingAPosteriorConfirmation` | Display/persist DECLINED with issuer provenance in the console | T3, T6 | PENDING |
| OBS-03 | B | `DynamoNetworkRejectionTransitionIntegrationTest` (2) `correlatedNetworkRejectionCommitsNetworkProvenanceWithoutIssuerDispatch`; `NetworkResultServiceWiringTest.networkEvidenceAbsentIsNotProcessorRejected` | Console display of PROCESSOR_REJECTED with network provenance and no fabricated issuer task | T3, T6 | PENDING |
| OBS-04 | B | `ChannelRequestValidatorTest` (20) incl. `rejectsMissingNullAndWronglyTypedFields`, `rejectsUnapprovedLiterals`; `ServerResolvedInstructionMapperTest.refusesAuthorizedContextThatDoesNotMatchTheRequest` | Safe operator-facing error surface; no raw payload echo in traces | T3, T6 | PENDING |
| OBS-05 | A | `AwsTransportTest.lambdaInvocationIsSynchronousBoundedAndWireContractRoundTrips`; `ProcessorHandler` JSON payload v2; documented baseline in `requirements.md` R04 and `delivery-status.md` | Nothing asserted for Operation B/C activation (absence is the control); keep the characterization current | T3 | PENDING |
| OBS-06 | B | `AwsAuthorizationIntegrationTest` (10) `issuerApprovalSurvivesNetworkLossButProcessorCannotSeeItAndRetryDoesNotRedispatch`; `DynamoNetworkResultGatewayIntegrationTest.lostResponseIsRecoveredOnceWithNetworkProvenance`; `ResultServiceWiringTest.recoveredPersistedResultAfterLostResponseKeepsOneDownstreamCall` | Console must not show issuer truth; no diagnostic view exists to prove exclusion | T3, T4 | PENDING |
| OBS-07 | B | `DeadlineDispositionTest` (9) `exactBoundaryIsAReadDispositionNotFinancialFinality`, `repeatedEvaluationCreatesNoTransitionNoSlotAndNoDispatch`; `DynamoResultGatewayIntegrationTest.ambiguousBeforeCommitStaysUnknownWithNoPartialResult` | Committed deadline transition (left open as a contract question) and UI UNKNOWN display | T3, T4 | PENDING |
| OBS-08 | B | `DynamoLateEvidenceTransitionIntegrationTest.lateApprovalAfterTheBoundaryCommitsOneConfirmedResult`; `LateSignedEvidenceReceiptGatewayIntegrationTest.lateEd25519EvidenceCommitsReceiptRecordEventHeadSlotAndLateMetadataAtOnce`; `lateConfirmationUpdatesLatestResultAndPreservesUncertainty` | Earlier uncertainty visible in history after the late approval; no second purchase | T4, T5 | PENDING |
| OBS-09 | B | `DynamoLateEvidenceAdmissionGatewayIntegrationTest` (7) `unauthorizedCandidatesAreRefusedBeforeAnyDedupeLookup`; `DynamoLateEvidenceTransitionIntegrationTest.nonConfirmedOrUncorrelatedEvidenceIsRefusedBeforeAdmission`; `C4P6SignedFieldBinderTest.currentEnvironmentSubstitutionIsRejected` | Safe diagnostic surfacing of rejected evidence | T3, T4 | PENDING |
| OBS-10 | B | `exactReplayOfIdenticalLateEvidenceAddsNoVersionEventOrSlot`; `aConfirmedOutcomeIsNeverOverriddenByLateEvidence`; `DynamoResultTransitionIntegrationTest.exactReplayOfTheResultDoesNotDuplicateEventSequenceOrSlot` | Timeout *observation* event that appears without replacing the known result — timeout-event vocabulary is still an open contract item | T3 | PENDING |
| OBS-11 | B | `identicalReplayReturnsOriginalReferenceWithoutRedispatch`, `concurrentIdenticalArrivalsProduceOneAuthorizationAndOneDispatch`, `inFlightReplayReturnsPendingWithoutWaitingOrSecondDispatch` | Submission attempts distinguishable from one another and linked to one operation in the UI/history | T3, T4 | PENDING |
| OBS-12 | A | `conflictingDuplicateRejectsWithoutMutatingOriginal`, `invalidOrForeignPriorReferenceRejectsBeforeReplayDisclosure`, `linkedRetryWithChangedBusinessKeyRejects`; `AwsAuthorizationIntegrationTest.issuerInstructionIsIdempotentAndConflictingInstructionIsRejected` | Nothing invented; runtime must keep the same rules | T3 | PENDING |
| OBS-13 | B | `DynamoEventStoreIntegrationTest.exactReplayKeepsTheOriginalSequenceAndInstance`; `EventDeduplicationTest.sameHashIsExactReplayAndDifferentHashIsConflict` | "One event rendered" — no renderer exists; no projection exists to duplicate | T4, T5 | PENDING |
| OBS-14 | A | `changedPayloadForTheSameIdentityIsAConflictWithoutOverwrite`; `reusedEvidenceIdentityWithChangedSignedContentIsQuarantinedAndPreservesTheOriginal`; `LateSignedEvidenceReceiptGatewayIntegrationTest.contradictoryLateSignedOutcomeIsQuarantinedAndPreservesTheFirstConfirmation` | Read model must expose the integrity gap without overwriting | T4 | PENDING |
| OBS-15 | B | `EventValidatorTest.skippedTaskNeedsDeclaredReasonOrEvidence`; `OperationAManifestTest.declaresEveryOperationATaskFromTheObservationContract` | Console showing last observed status plus explicit incomplete-observation marker | T5, T6 | PENDING |
| OBS-16 | B | `elapsedDurationRequiresSameClockStartAndEnd`; `EventValidatorTest.nonUtcOrMalformedTimesAreRejected`; `DynamoEventStoreIntegrationTest.legacyDedupeMetadataWithoutCollectionTimeReadsAsExplicitUnknown` | Timeline rendering of occurrence/collection, skew and unknown duration | T5, T6 | PENDING |
| OBS-17 | C | None | WebSocket subscribe/ack, disconnect, reconnect before catch-up, buffered merge, dedupe, no POST | T5 | PENDING |
| OBS-18 | C | `C3ResponseBuilderTest.unknownHistoryTargetIs404AndDisclosesNothing` is the only adjacent assertion | Cursor tamper 400, scope 403, expiry/revision/truncation 410, explicit gap ranges, bounded resync | T4 | PENDING |
| OBS-19 | C | None | Pagination, reload, restart stability, overlap dedupe, silent-omission resistance | T4 | PENDING |
| OBS-20 | B | `ObsAcceptanceAtomicChainIntegrationTest.obs20OnePaymentAndCriticalEventsCommitAtomicallyAndApproveWithoutAsyncBackends` — real claim→intent→result gateways, APPROVED returned by the invocation, one downstream call, three critical events, HEAD 3, slots 3/8, no journal evidence event | Notifier/WS/query/ADOT do not exist, so the test proves *independence*, not recovery from a failing notifier; runtime wiring absent | T3, T5, T7 | PENDING |
| OBS-21 | C | None (no notifier/ADOT component exists) | Observation-only retry after notification failure, no payment retry or rollback | T5, T7 | PENDING |
| OBS-22 | B | `obs22RequiredAtomicWriteFailureBeforeSendDoesNotDispatchOrResend` (real `POSSIBLE_SEND` abort, zero downstream calls, no resend); `DynamoResultGatewayIntegrationTest.strongReadFailureStaysUnknownAndIsNeverNoResult`; `ResultServiceWiringTest.notCommittedResultFabricatesNoFinancialOutcome` | `UNAVAILABLE` disposition for an unreadable restart is a C3 envelope concern, not implemented | T3 | PENDING |
| OBS-23 | B | `rejectsCallerWithoutAuthorityOverTheMerchant`, `rejectsUnknownAcquirerWithoutUsingAnotherMandate`; no issuer route exists | Browser/console tampering path; the boundary that receives the request does not exist yet | T2, T6 | PENDING |
| OBS-24 | C | None | Subscription generation, revocation and stale-generation rejection | T5 | PENDING |
| OBS-25 | B | `EventSanitizerTest` (6); `rejectsUnknownCallerFieldsWithoutEchoingThem`; `EventValidatorTest.maximallyBoundedEventStaysWithinTheCanonicalPayloadLimit` | Inert rendering of supplied text in UI and error surfaces; log inspection | T3, T6 | PENDING |
| OBS-26 | B | Codec/corpus layer is strongly covered: `Iso8583DomainCorpusTest` (14), `Iso8583PackerTest` (53), `Iso8583ScannerTest` (94), `Iso8583De48RequestParserTest` (58), `Iso8583De48ResponseParserTest` (74), `Iso8583ExchangeBudgetValidatorTest` (7), `Iso8583FreshnessValidatorTest` (5) `acceptsExactlyThreeHundredSecondsAndRejectsJustOutside` | Round-trip through the real transport/ACL boundary; unmapped decision surfaced as uncertainty at the service edge | T3, T10 | PENDING |
| OBS-27 | B | `EventValidatorTest.unknownTaskIsRejectedAtIngress`; `producerCannotEmitATaskItIsNotAssignedTo`; `OperationAManifestTest.onlyTheProcessorHoldsCriticalCommitAuthority` | No auxiliary non-payment capability exists yet, so the scenario cannot execute; the contract is enforced | T7 | PENDING |
| OBS-28 | C | None. No retention, expiry, TTL or quota code exists; the ADR withdrew the proposed numeric quotas | Seven-day logical expiry, TTL mapping per record type, truncation/gap reporting, measured catch-up — without deleting business/evidence records | T4, T7, T8 | PENDING |

## 6. Linked AUTH-001 business rows actually evidenced here

Qualified IDs — `R07` exists in both work items with different meanings, so every reference below
carries the work item.

| Qualified ID | Status | Executed evidence | Gap | Task | IV |
| --- | --- | --- | --- | --- | --- |
| AUTH-001 R01 — acquirer mandate/merchant context, no credential sharing | A | `rejectsUnknownAcquirerWithoutUsingAnotherMandate`, `rejectsCallerWithoutAuthorityOverTheMerchant` | Real service principal binding | T2 | PENDING |
| AUTH-001 R02 — caller/merchant eligibility and validation before routing | A | `rejectsUnknownMerchantAndUnknownTerminal`, `rejectsEachMalformedOrOutOfProfileInputBeforeDispatch` | Runtime enforcement path | T2, T3 | PENDING |
| AUTH-001 R03 — approved route and mandate-scoped dispatch | A | `noPermittedRouteRejectsBeforeDispatch`, `approvesValidPurchaseWithIssuerProvenance` | Atomic runtime dispatch | T3 | PENDING |
| AUTH-001 R04 — approved/declined/not-confirmed from evidence | A | `distinguishesIssuerDeclineFromProcessorRejection`, `timeoutAndAmbiguousReplyRemainNotConfirmed` | C3 emission at runtime | T3 | PENDING |
| AUTH-001 R05 — timeout/ambiguity is never an issuer decline | A | `timeoutAndAmbiguousReplyRemainNotConfirmed`; `AwsTransportTest.ambiguousTransportFailureNeverRetriesOrFabricatesDecline` | Committed deadline transition still open | T3 | PENDING |
| AUTH-001 R06 — processor rejection vs issuer decline distinguished | A | `distinguishesIssuerDeclineFromProcessorRejection`; `C3ResponseBuilderTest.denialWinsWithoutLeakingAPosteriorConfirmation` | Internal source recorded; caller-visible disclosure policy still a design assumption | T3, T4 | PENDING |
| AUTH-001 R09 — duplicate-request control | A | `identicalReplayReturnsOriginalReferenceWithoutRedispatch`, `concurrentIdenticalArrivalsProduceOneAuthorizationAndOneDispatch` | Runtime idempotency on the atomic path | T3 | PENDING |
| AUTH-001 R10 — retained transaction/response references and history | A | `lateConfirmationUpdatesLatestResultAndPreservesUncertainty`; `freshStoreInstanceReplayReturnsThePersistedFirstCollectionTime` | Human-readable retained history | T4 | PENDING |
| AUTH-001 R12 — synthetic data only, no PIN/PAN/CVV/cryptograms | A | `EventSanitizerTest` (6); closed schema tests | Path-wide proof; Security review | T7, T10 | PENDING |
| AUTH-001 R15 — identical replay returns latest outcome + original reference | A | `identicalReplayReturnsOriginalReferenceWithoutRedispatch`, `combinedRetryAfterLateConfirmationReturnsLatestButRejectsConflictAndForeignLink` | Runtime path | T3 | PENDING |
| AUTH-001 R16 — valid retry links prior authorization, never redispatches | A | `linkedRetryWithChangedBusinessKeyRejects`, `linkedRetrySurvivesWindowExpiryAtEveryBoundary` | Runtime path | T3 | PENDING |
| AUTH-001 R17 — business key, conflict on materially different data | A | `conflictingDuplicateRejectsWithoutMutatingOriginal`, `unlinkedBoundaryCreatesNewPaymentAtExactWindowEnd` | Window boundary/expiry policy still a design assumption | T2, T3 | PENDING |
| AUTH-001 R18 — HD-01 profile: USD, 1.00–9,999.99, entry/CVM/cryptogram | A | `Iso8583DomainValidatorTest` (9) `rejectsEveryCurrencyOtherThanUsd`, `acceptsTheInclusiveAmountBoundaries`; `ChannelRequestValidatorTest` amount oracle vectors | Runtime profile source from the registry | T2, T3 | PENDING |
| AUTH-001 R20 — 30-second default timeout, late confirmation updates latest | B | `DeadlineDispositionTest.justBeforeTheBoundaryKeepsReadablePossibleSendInFlight`; `lateNetworkResponseReturnsUncertaintyThenCorrelatedLatestEvidenceOnRetry`; `ResponseTimeoutConfigTest` (6) `absentSettingResolvesToTheApprovedThirtySecondDefault`, `approvedDefaultMatchesTheDeclaredSpringProperty`, `validExplicitSettingIsPreserved`, `zeroOrNegativeSettingIsRejectedInsteadOfSilentlyAccepted`, `malformedSettingIsRejectedInsteadOfSilentlyAccepted`, `blankSettingIsRejectedInsteadOfFallingBackSilently` | **Default-timeout divergence corrected at source level 2026-11-20** (bounded T3 prerequisite, not runtime activation): `AwsRuntime.processor()` now resolves the timeout through `ResponseTimeoutConfig` — absent setting → approved 30 s, explicit positive `RESPONSE_TIMEOUT_MS` preserved, blank/malformed/non-positive fails closed at cold start. `application.properties` `PT30S` and all approved specifications are unchanged. Evidence `evidence/t3-timeout-default-20261120/`. Still open: building the atomic gateways in `AwsRuntime`, deadline persistence and one integrated path | T2, T3 | PENDING |

AUTH-001 R07/R08/R19 remain deferred/non-v1 per HD-03 and are intentionally not counted here.

## 7. Concrete gap register (no generic "done")

Ordered by the completion task that closes them. Each item is a real missing artifact, not a
status word.

1. **T2 — trusted application boundary.** No runtime service principal exists. The ADR selects
   API Gateway with mTLS and OAuth 2.0 `private_key_jwt`; `C4HttpCarriageBridge` only accepts a
   `C4AuthenticatedTransportContext`. Needed: client-assertion validation at the token endpoint,
   separate access-token validation, certificate↔client↔mandate binding, key custody/rotation, and
   the demo-user JWT kept separate from service authentication. The runtime timeout default
   divergence under AUTH-001 R20 was corrected at source level on 2026-11-20
   (`ResponseTimeoutConfig`, six focused tests); that correction is not runtime activation and
   T2 itself remains open.
2. **T3 — complete application path.** `AwsRuntime.processor()` still builds the legacy
   four-argument `AuthorizationService`. Needed: construct the atomic gateways, remove the legacy
   fallback, mount the internal route, and run one integrated harness across approve, decline,
   network rejection, unknown/timeout, identical retry, conflicting retry, malformed response and
   late evidence with a "no second send" assertion.
3. **T4 — functional history.** No list/detail/query/cursor/expiry capability exists. Needed:
   journal-aware queries, fixed snapshots, signed scoped cursors, 400/403/410 semantics, gap
   ranges, and seven-day logical expiry based on original `collectedAt` — with TTL mapped per
   record type and no deletion of business/evidence/identity records.
4. **T5 — live delivery.** No Streams consumer, notifier, WebSocket connection state,
   acknowledgement, buffering or reconnect catch-up exists. Needed: all of W01–W33 applicable
   behavior against the same event table, with zero payment POSTs from notification paths.
5. **T6 — presenter console.** No demo-user sign-in, relay, POS form, result view, timeline,
   history or detail view exists. Needed: the browser journey plus inert text rendering and
   token/key handling outside browser storage and URLs.
6. **T7 — instrumentation and telemetry.** Contracts exist but no producer invokes them and no
   ADOT export exists. Needed: manifest/hook wiring on the real path, explicit missing-task
   coverage, Java 25 ADOT packaging, and proof that telemetry failure cannot change a payment
   result.
7. **T8 — infrastructure and CI.** No Terraform, no CI definition, no environment/secret
   parameterization exists. Needed: native Terraform for the final resource set, S3 state with
   locking, IAM, JWT/WebSocket, keys, observability, and GitHub Actions OIDC workflows
   (variables/Secrets, `us-east-2`) with human-gated delivery.
8. **T9 — authorized AWS execution.** Nothing deployed; no account/region receipt, cost estimate,
   rollback plan or cloud-only check exists.
9. **T10 — independent verification.** No QA verdict, no Security verdict, no human acceptance.
   All 53 + 14 mapped rows carry `PENDING`.

Cross-cutting gap that affects several rows: the timeout **observation event** vocabulary and the
disposition of legacy `NOT_CONFIRMED` rows are still open contract items (recorded in
implementation-progress.md §30), so rows OBS-07 and OBS-10 cannot close without a Requirements
decision.

## 8. Limits of this document

- It maps to one commit and one frozen run; it is invalid for any later source bytes until the
  affected evidence is revalidated.
- It contains no AWS service behavior, deployment, IAM, capacity/throttling, key custody, TLS or
  API Gateway wire evidence.
- Component integration against DynamoDB Local, PostgreSQL/Testcontainers and in-process doubles is
  not an integrated application journey.
- It is not a QA report and not a Security report. Security review of the concrete access/key
  design is commissioned separately and remains pending.
- No test, source, specification or vector was created or modified by this slice; only this
  document, its evidence directory and the append-only journal entry were written.

## 9. Resumen en español

**Qué funciona hoy, de verdad:** existen y están probados con 959 pruebas verdes los componentes de
validación de canal, el códec ISO 8583 `SIM-ISO87A-v1`, la traducción de peticiones y respuestas, la
verificación criptográfica de evidencia C4 (DIRECT y RELAY), la vinculación P6, y las escrituras
atómicas de negocio más evento crítico en DynamoDB. Nada de eso está todavía en el camino de
ejecución real: `AwsRuntime` sigue construyendo el servicio antiguo y ninguno de los gateways
atómicos está cableado.

**Qué NO está implementado:** la consola HTML, el usuario demo con su relay, la entrega en vivo por
Streams y WebSocket, las lecturas de historial con cursores y expiración de siete días, la
telemetría ADOT, la autenticación de servicio con mTLS y `private_key_jwt`, y toda la infraestructura
Terraform y CI.

**Tarea activa:** T1. Esta matriz es su entregable: 25 requisitos, 28 escenarios y 14 filas
empresariales de AUTH-001 mapeados a código y a pruebas realmente ejecutadas. Los recuentos miden
filas mapeadas, no porcentaje de producto terminado.

**Qué impide el siguiente hito:** T2 no puede cerrarse sin la identidad de servicio real que el ADR
exige; T3 no puede cerrarse mientras el runtime siga en el camino antiguo. Además hay una
divergencia concreta que requiere decisión de configuración: el runtime usa 2000 ms por defecto
mientras la propiedad local aprobada es `PT30S`.

**Qué falta para terminar el producto:** T2 a T10 del plan de completitud, con verificación
independiente de QA y Security y tu aceptación explícita. Ninguno de estos resultados equivale a
aceptación, despliegue o revisión independiente.
