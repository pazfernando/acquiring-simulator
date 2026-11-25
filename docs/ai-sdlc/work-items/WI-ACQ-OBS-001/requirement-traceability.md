# WI-ACQ-OBS-001 — requirement-to-evidence traceability (T1)

Date: 2026-11-20. Updated 2026-11-22 for the T3 atomic wiring (see §10) and for the full offline
suite at the current bytes (see §11); updated 2026-11-23 for the continuation H1 console slice —
origin-verification gate and read-only history (see §12) — and for the continuation K1 real issuer
decoder package (see §13).
Author: Implementation (OpenCode ACP, configured default model, no override).
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

**Superseded basis (2026-11-22):** the "Executed suite reused — 959 tests" row above describes the
T1 slice only. The satisfying run for the current bytes is the 978-test full suite in §11; read the
suite and freeze rows of this table through that section.

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
  points, or only from tests/internal seams. **As of 2026-11-22 `AwsRuntime.processor()` builds the
  atomic graph** (claim, dispatch-intent, result, network-result, late-evidence gateways plus the
  event store); `ProcessorHandler`'s no-argument constructor is bound to `processor()`, and the
  legacy four-argument `AuthorizationService` is no longer constructed on the AWS path. That
  composition is verified only in-process against DynamoDB Local with a stub issuer, so runtime
  reachability is proven **by construction**, not by executing the deployed journey.
- **Integrated**: whether a cross-component application journey actually ran. For this tree the
  answer is still **No on every row**: the strongest evidence is adapter/service integration against
  DynamoDB Local, PostgreSQL/Testcontainers or in-process doubles. Component and composition
  integration is not the end-to-end payment journey.

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
| OBS-001 requirements R01–R25 | 25 | 13 | 11 | 1 | 0 | 0 (all PENDING) |
| OBS-001 scenarios OBS-01–OBS-28 | 28 | 3 | 21 | 4 | 0 | 0 (all PENDING) |
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
| R01 | B | No UI exists; an in-process console request/response boundary (`ConsoleAuthorizationRoute`, no listener, no page) and a read-only HTTP history route (`ConsoleHistoryRoute` + signed cursor codec, no listener) | `DynamoEventStoreIntegrationTest` (16): `firstCriticalAppendAllocatesSequenceOneAndStoresThePayloadOnce`, `concurrentAppendsAllocateContiguousUniqueSequences`; `ConsoleAuthorizationRouteTest` (13, T4 console run 2026-11-22, `evidence/t4-console-api-20261122/`) `authorizedSubmissionReachesTheServiceExactlyOnceWithTheTrustedContext`; `ConsoleSubmissionAtomicIntegrationTest` (3) `authorizedSubmissionCommitsOnePaymentThroughTheWiredAtomicPath`; `ConsoleHistoryRouteTest` (40, QA fixes 2026-11-23 in `evidence/continuation-h1-qa-fixes-20261123/`, D-6 C-1.3/C-2 fixes in `evidence/continuation-h1-d6-c2-20261123/`, C-3 fixes in `evidence/continuation-h1-d6-c3-20261123/`) `newSnapshotReturnsTheFixedHeadAndCompleteCoverage`, `continuationKeepsTheFrozenSnapshotWhenTheHeadAdvances`; `ConsoleHistoryRouteDynamoIntegrationTest` (4, 2026-11-23, `evidence/continuation-h1-20261123/`) `snapshotReadsTheRetainedRowsAndReportsCompleteCoverage` | Test-only; no UI, no listener; the history read runs in-process over DynamoDB Local | HTML POS form, live update without reload, a served detail/history page that survives reload | T4, T5, T6 | PENDING |
| R02 | A | `OperationAManifest`, `EventManifest`, `EventValidator`, `EventTask` | `OperationAManifestTest` (7): `declaresEveryOperationATaskFromTheObservationContract`, `onlyTheProcessorHoldsCriticalCommitAuthority`; `EventValidatorTest` (14): `unknownTaskIsRejectedAtIngress` | Contract is test-only; no producer invokes the hook at runtime | Real task producers/hook binding to the payment path; unimplemented operations remain disabled by absence, not by a runtime switch | T3, T7 | PENDING |
| R03 | A | `ChannelRequestValidator`, `ServerResolvedInstructionMapper`, `Iso8583RequestValidationPipeline`, `C4HttpCarriageBridge` | `ChannelRequestValidatorTest` (20) incl. `rejectsUnknownCallerFieldsWithoutEchoingThem`; `ServerResolvedInstructionMapperTest` (3) `refusesAuthorizedContextThatDoesNotMatchTheRequest`; `Iso8583RequestValidationPipelineTest` (53) | Test-only; `/internal/sim-iso87a/v1/authorizations` primitive is not mounted | Mounted ACL boundary and live ISO dispatch | T2, T3 | PENDING |
| R04 | A | `ProcessorHandler` HTTP JSON payload v2, `LambdaDownstream` synthetic JSON | `AwsTransportTest` (6): `lambdaInvocationIsSynchronousBoundedAndWireContractRoundTrips`, `functionErrorsAndInvalidPayloadsRemainUnconfirmed` | Legacy runtime path is the JSON baseline; ISO path is unmounted | Replace JSON ingress with the ISO ACL path; keep baseline characterization in docs | T3 | PENDING |
| R05 | A | `AuthorizationService`, `Outcome`, `DecisionSource`, `RejectionReason`, `DeadlineDisposition` | `AuthorizationCoreIntegrationTest` (24): `distinguishesIssuerDeclineFromProcessorRejection`, `timeoutAndAmbiguousReplyRemainNotConfirmed`, `approvesValidPurchaseWithIssuerProvenance`; `PostgresAuthorizationCoreIntegrationTest` (24) mirrors it | Runtime now serves APPROVED, issuer DECLINE and network rejection through the wired atomic graph; verified in-process against DynamoDB Local (2026-11-22) | Four dispositions emitted from the wired atomic path and covered by `AwsRuntimeAtomicWiringIntegrationTest` (10, green inside the 978-test full suite of §11); not exercised through the deployed boundary or a real issuer | T3, T4 | PENDING |
| R06 | A | `ClaimGateway`, `DispatchIntentGateway`, `ResultGateway`, `AuthorizationService` | `identicalReplayReturnsOriginalReferenceWithoutRedispatch`, `concurrentIdenticalArrivalsProduceOneAuthorizationAndOneDispatch`, `retainedClaimWithoutDispatchIsNeverRedispatchedAfterRestart`; `DispatchIntentServiceWiringTest` (9) `replayedIntentDoesNotSendTwice`; `ResultServiceWiringTest` (12) `duplicateResultDoesNotResend` | Atomic path is now the runtime path: `AwsRuntime.atomicService` wires claim, intent, result, network-result and late gateways; the legacy `AuthorizationService` is no longer constructed on the AWS path (2026-11-22) | No legacy resend path remains reachable from the AWS entry point; unproven against a real Lambda invocation and real issuer/network | T3 | PENDING |
| R07 | A | `LateEvidenceAdmissionGateway`, `LateSignedEvidenceReceiptGateway`, `LateEvidenceCriticalComposer`, `C4LateDeliveryVerifier`, `C4P6SignedFieldBinder` | `DynamoLateEvidenceTransitionIntegrationTest` (11) `lateApprovalAfterTheBoundaryCommitsOneConfirmedResult`, `aConfirmedOutcomeIsNeverOverriddenByLateEvidence`; `LateSignedEvidenceReceiptGatewayIntegrationTest` (6); `C4P6SignedFieldBinderTest` (18) `bindingDecisionClaimsNoConfirmedResultBeforeAtomicReceipt` | Late-evidence gateway and functional event store are in the wired runtime graph (2026-11-22); no authorized transport seam and no real key custody | Runtime admission seam with real key custody; original-reference retention across restarts | T2, T3 | PENDING |
| R08 | B | `AuthorizedContext` + `CALLER#<subject>` registry (`AwsRuntime.caller`), `AuthorizationService` context checks | `rejectsUnknownAcquirerWithoutUsingAnotherMandate`, `rejectsCallerWithoutAuthorityOverTheMerchant`, `rejectsUnknownMerchantAndUnknownTerminal`; `AwsTransportTest.unauthenticatedOrUnregisteredCallerCannotSupplyTheirOwnContext` | **Context-only test seam**; not a service principal | Real service identity per ADR (mTLS + OAuth2 `private_key_jwt` at API Gateway), bound to mandate/context | T2 | PENDING |
| R09 | A | `EventSanitizer`, `SafeIdentifier`, closed event schema, `ChannelRequestValidator` | `EventSanitizerTest` (6) `panShapedDigitRunIsRejected`, `compactJwtIsRejectedWithoutEchoingTheToken`, `pinBlockWordingIsRejected`; `SafeIdentifierTest` (6); `GeneratedIdentifierSanitizerTest` (1) | Server-side only; no log/UI surface exists to inspect | Whole-path proof incl. logs and console text; Security review of the allowlist | T3, T7, T10 | PENDING |
| R10 | A | Separate `NetworkResultGateway` and `ResultGateway`; issuer/network roles in `C4PrincipalRole` | `DynamoNetworkResultGatewayIntegrationTest` (7) `issuerDecisionEvidenceIsRefusedBeforeAnyWrite`; `C4EvidenceVerifierTest` (33) `networkRoleCannotSignIssuerDecision`, `relayCannotSubstituteNetworkSignatureForIssuerSignature`; `NetworkResultServiceWiringTest` (12) `issuerReplyNeverReachesTheNetworkGateway` | Both result gateways are in the wired runtime graph (2026-11-22); no diagnostic view exists | Real issuer/network round trip; ensure no diagnostic path can replace processor outcome | T3 | PENDING |
| R11 | A | `CanonicalEventCodec`, `EventTableKeys`, `EventSourceWrapper`, `EventDedupeKey`, `EventSubmission` | `CanonicalEventCodecTest` (7) `canonicalJsonAndHashMatchTheApprovedEv1Oracle`, `producerCannotSupplyCollectorFields`; `EventTableKeysTest` (5) `eventSortKeyIsTwelveDigitZeroPaddedCommitOrder`; `EventSubmissionTest` (4) | Authorization and event id allocation now runs in the wired runtime graph (`IdentifierSource.randomHex` produces the reference the critical events are keyed by); no durable alias beyond the event table | Durable alias/identity beyond the event table; verified only against DynamoDB Local | T2, T3 | PENDING |
| R12 | B | Manifest + validator + `EventDurability` + `CriticalCapacity`; `OperationAManifest` | `EventValidatorTest.skippedTaskNeedsDeclaredReasonOrEvidence`; `OperationAManifestTest.networkAndIssuerTasksRemainConditionalBranches`; `DynamoEventStoreIntegrationTest.businessCompositionIsRejectedForBestEffortEvents`, `bestEffortEventCannotAssertJournalMetadata` | Test-only; no producer emits observations | Emit started/terminal observations from real tasks; expose explicit coverage gaps; missing terminal ≠ FAILED | T3, T5, T7 | PENDING |
| R13 | A | `EventContext`, `EventClockQuality`, `StoredEvent`, `EventSourceWrapper` | `DynamoEventStoreIntegrationTest.criticalCollectionTimeEqualsCommitTimeAndSurvivesReplay`, `legacyDedupeMetadataWithoutCollectionTimeReadsAsExplicitUnknown`; `EventValidatorTest.elapsedDurationRequiresSameClockStartAndEnd`, `nonUtcOrMalformedTimesAreRejected` | Persistence test-only; nothing displays it | Display of occurrence/collection/skew/unknown duration in the timeline | T4, T5 | PENDING |
| R14 | B | `AuthorizationState`/`Outcome`, `EventDurability`, `C3ResponseEnvelope` freshness/observation fields | `C3ResponseBuilderTest` (9) `unknownObservationForcesUnknownFreshness`, `envelopeRejectsAPaymentOutcomeOnAnUnavailableRow`, `envelopeIsExactlyTheClosedThirteenKeySet` | Envelope is unit-tested; no server emits it, no connection state exists | Server freshness + task-coverage + connection state served separately from financial state | T4, T5 | PENDING |
| R15 | C | No timeline/notifier/console component exists | None. Only contract-level assertions (`C3ResponseBuilderTest`, event contract tests) | N/A | Correlated live timeline UI with provenance and independent freshness/completeness | T5, T6 | PENDING |
| R16 | A | `EventDeduplication`, `EventIntegrityConflict`, `SignedEvidenceConflict`, gateway quarantine flags | `EventDeduplicationTest` (3) `sameHashIsExactReplayAndDifferentHashIsConflict`; `DynamoEventStoreIntegrationTest.changedPayloadForTheSameIdentityIsAConflictWithoutOverwrite`; `SignedEvidenceReceiptGatewayIntegrationTest` (15) `reusedEvidenceIdentityWithChangedSignedContentIsQuarantinedAndPreservesTheOriginal`, `duplicateQuarantineFlagIsStableAndKeepsTheFirstConflictingWitness` | Test-only; quarantine is not readable by any API | Surface observation-integrity failures in the read model; keep them out of payment evidence | T3, T4 | PENDING |
| R17 | B | Append-only event store + verified-evidence gates | `DynamoLateEvidenceTransitionIntegrationTest.exactReplayOfIdenticalLateEvidenceAddsNoVersionEventOrSlot`; `DynamoResultTransitionIntegrationTest` (7) `exactReplayOfTheResultDoesNotDuplicateEventSequenceOrSlot` | Server append only; no client derives a timeline | Client-side derivation from one source; P5-only outcome change enforced end to end | T4, T5 | PENDING |
| R18 | B | Critical composers + `DynamoEventStore` composed transaction; no projection/outbox | `DynamoCriticalClaimIntegrationTest` (8) `claimCommitsBusinessHeadEventHeadDedupeAndSlotInOneTransaction`, `exhaustedSlotBudgetAbortsTheWholeClaim`; `EvidenceReceiptAtomicIntegrationTest` (4); `DynamoEventStoreIntegrationTest.failedComposedWriteLeavesNoEventHeadOrDedupe` | Atomic write is adapter-integrated; read/subscribe absent | Subscribe + ack before fixed-snapshot catch-up; Streams replay and truncation; query path | T4, T5 | PENDING |
| R19 | B | `ProcessorHandler` C1 JWT (`requestContext.authorizer.jwt.claims.sub` → registry) only; `DemoConsoleAuthorizer` + `ConsoleAuthorizationRoute` token-policy seam (issuer, audience, client, subject, scope, expiry); `JwtConsoleTokenDecoder` + `TrustedIssuerConfiguration` + injected `JwksSource` + `ConsoleTokenComposition` (signature, `kid`, algorithm allowlist, issuer, audience, authorized party, scope, `exp`/`nbf`/`iat`, lifetime ceiling, rotation by key-set refresh) | `AwsTransportTest.authenticatedInputUsesTrustedRegistryAndReturnsOnlyOutcomeAndReference`; `DynamoEventStoreIntegrationTest.unauthorizedContextCannotPoisonTheConflictMarker`; `ConsoleAuthorizationRouteTest` (13, T4 console run 2026-11-22) `authorizerRefusesEachCredentialsClassWithItsOwnReason`, `everyRefusedCredentialLeavesTheBusinessServiceUntouched`; `JwtConsoleTokenDecoderTest` (25) inc. `refusesASignatureMismatch`, `refusesAWrongIssuer`, `refusesAnExpiredToken`, `refusesTheRetiredOldKeyAfterRotation`; `ConsoleTokenCompositionTest` (5) `thePolicyCoreStillRefusesASubjectOtherThanTheConfiguredDemoSubject` — all locally generated keys, 2026-11-23 in `evidence/continuation-k1-20261123/` | C1 demo-JWT path is real in the legacy handler; console verification is exercised only against locally generated keys and an in-memory key source (no network JWKS fetch, no real issuer, no deployed API Gateway authorizer); the dev harness still uses the unsigned stub; no relay, no query scoping, service ingress unimplemented | Deployed HTTP API JWT authorizer with real issuer/JWKS verification, bounded query/subscription scope, service identity per ADR, no issuer-truth view | T2, T6 | PENDING |
| R20 | B | `EventSanitizer`, closed canonical schema, `SafeIdentifier` | `EventSanitizerTest` (6); `CanonicalEventCodecTest.undeclaredPayloadFieldsAreRejected`; `ChannelRequestValidatorTest.rejectsUnknownCallerFieldsWithoutEchoingThem` | Server-side only | Inert rendering of all supplied text in the console; log coverage | T3, T6 | PENDING |
| R21 | A | `ChannelRequestValidator`, `ServerResolvedInstructionMapper`, `Iso8583DomainValidator`, `Iso8583ExchangeBudgetValidator` | `mapsNewDispatchToTheExactByteOracle`, `refusesNewDispatchThatExceedsTheDerivedExchangeBudget`; `Iso8583DomainValidatorTest` (9) `rejectsEveryCurrencyOtherThanUsd`; `Iso8583ExchangeBudgetValidatorTest` (7) `acceptsEightTwentySixAndRejectsEightTwentySevenAtTheDerivedBoundary` | Test-only; no live ACL route | Runtime server-side fixture/route resolution and dispatch | T2, T3 | PENDING |
| R22 | A | `Iso8583ResponseValidationPipeline`, `Iso8583P4MatrixValidator`, `C3ResponseBuilder` | `Iso8583ResponseValidationPipelineTest` (51); `Iso8583P4MatrixValidatorTest` (62) `ninetyOneIsNeverAnIssuerDecline`, `thirtyIsNeverAnIssuerDecision`; `C3ResponseBuilderTest.denialWinsWithoutLeakingAPosteriorConfirmation` | Test-only; no channel response is emitted at runtime | Emit the validated channel result from the runtime path; preserve uncertainty | T3 | PENDING |
| R23 | B | Full `SIM-ISO87A-v1` codec/ACL package + C1–C4 contracts (`iso8583-*`, `channel/*`) | `Iso8583ScannerTest` (94), `Iso8583PackerTest` (53) `repacksEveryApprovedPositiveFixtureByteExact`, `Iso8583RequestValidationPipelineTest` (53), `C4CarrierParserTest` (18) | Component-complete; **HTTPS transport authentication absent** | C2 service principal on mTLS + OAuth2 (`private_key_jwt`); real TLS termination | T2, T3, T8 | PENDING |
| R24 | B | Atomic business+critical-event composers; no dual write | `ObsAcceptanceAtomicChainIntegrationTest` (2) `obs20OnePaymentAndCriticalEventsCommitAtomicallyAndApproveWithoutAsyncBackends` | Atomic business+critical-event composition is now built by the runtime (`AwsRuntime.atomicService`); async side does not exist | Streams/notifier/ADOT path where async failure provably cannot gate payment | T3, T7 | PENDING |
| R25 | B | Append-side event store plus an in-process HTTP history read (`ConsoleHistoryRoute`: strong HEAD, bounded snapshot, signed cursor binding issuer and target terminal, gap and expired-prefix reporting); no Streams, notifier or WebSocket | `ConsoleHistoryRouteTest` (40, QA fixes 2026-11-23 in `evidence/continuation-h1-qa-fixes-20261123/`, D-6 C-1.3/C-2 fixes in `evidence/continuation-h1-d6-c2-20261123/`, C-3 fixes in `evidence/continuation-h1-d6-c3-20261123/`) `newSnapshotReturnsTheFixedHeadAndCompleteCoverage`, `gapHaltsCoverageAndSuppressesTheNextCursor`, `cursorPayloadBindsTheTokenIssuerAndTargetTerminal`, `cursorMintedUnderAnotherIssuerIsForbidden`, `cursorBoundToAnotherTerminalIsForbidden`, `serviceUnavailableBodyExactlyMatchesTheReviewedFixture`; `ConsoleHistoryRouteDynamoIntegrationTest` (4, 2026-11-23, `evidence/continuation-h1-20261123/`) `paginationKeepsTheFrozenHeadWhenTheSourceGrows` | Test-only; the route has no listener and no deployed delivery path | Streams trigger, notifier, WebSocket subscribe/ack/catch-up, a served HTTP read boundary, no polling | T4, T5 | PENDING |

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
| OBS-18 | B | `ConsoleHistoryRouteTest` (40, 2026-11-23; QA fixes in `evidence/continuation-h1-qa-fixes-20261123/`, D-6 C-1.3/C-2 fixes in `evidence/continuation-h1-d6-c2-20261123/`, C-3 fixes in `evidence/continuation-h1-d6-c3-20261123/`): `tamperedCursorIsFourHundredInvalidCursor`, `expiredCursorIsFourTenResyncRequiredWithAction`, `changedExpiryRevisionForcesFourTen`, `truncatedFirstRetainedSequenceForcesFourTen`, `cursorBoundToAnotherSubjectIsForbidden`, `cursorMintedUnderAnotherIssuerIsForbidden`, `cursorBoundToAnotherTerminalIsForbidden`, `serviceUnavailableBodyExactlyMatchesTheReviewedFixture`, `gapHaltsCoverageAndSuppressesTheNextCursor`, `logicallyExpiredPrefixIsReportedAsARangeNotSkipped`, `afterBeyondTheDurableHeadIsInvalidRatherThanSilentlyClamped` (head 1, reader 0), `postReadContextMismatchWinsOverARetiredGeneration`, `postReadRetiredGenerationAloneStaysFourTen`, `continuationContextMismatchWinsOverARetiredGeneration`, `cursorCombinedWithAfterIsRefusedBeforeMandateResolutionAndTheStore`, `cursorCombinedWithSnapshotIsRefusedBeforeMandateResolutionAndTheStore`, `registryOutageBeforeTheStoreReadIsFiveZeroThreeWithNoHead`, `missingMandateIsFourZeroThreeBeforeTheStoreRead`, `malformedFormWinsOverARegistryOutage`, `unknownQueryParameterIsRefusedInTheRequestFormStep`, `repeatedQueryParameterIsRefusedInTheRequestFormStep`, `malformedSignedPayloadIsFourHundredBeforeRetiredAndContextChecks`, `retiredSchemaCursorIsFourTenBeforeAnyContextCheck`, `expiredCursorIsFourTenBeforeAContextMismatch`, `headUnavailableIsFiveZeroThreeBeforeAnyRead`, `unknownTargetOnAContinuationIsFourZeroFourAfterTheHeadStep`, `boundLastSequenceBeyondTheSnapshotHeadIsFourHundredAfterTheHeadRead`; `ConsoleHistoryRouteDynamoIntegrationTest` (4) `changedRetentionRevisionAgainstTheRealHeadForcesFourTen`, `physicallyMissingRetainedRowIsAnExplicitGapNotAFabricatedEvent`; `C3ResponseBuilderTest.unknownHistoryTargetIs404AndDisclosesNothing` remains adjacent | Bounded resync over a served listener, real scoped authorizer and a deployed restart; retention/TTL realization is still absent | T4 | PENDING |
| OBS-19 | B | `ConsoleHistoryRouteTest` (40, 2026-11-23; QA fixes in `evidence/continuation-h1-qa-fixes-20261123/`, D-6 C-1.3/C-2 fixes in `evidence/continuation-h1-d6-c2-20261123/`, C-3 fixes in `evidence/continuation-h1-d6-c3-20261123/`) `continuationKeepsTheFrozenSnapshotWhenTheHeadAdvances`; `ConsoleHistoryRouteDynamoIntegrationTest` (4) `paginationKeepsTheFrozenHeadWhenTheSourceGrows` — a continuation keeps the frozen head while the underlying source grows | Browser reload, restart stability of a served route, and overlap dedupe across reconnects | T4 | PENDING |
| OBS-20 | B | `ObsAcceptanceAtomicChainIntegrationTest.obs20OnePaymentAndCriticalEventsCommitAtomicallyAndApproveWithoutAsyncBackends` — real claim→intent→result gateways, APPROVED returned by the invocation, one downstream call, three critical events, HEAD 3, slots 3/8, no journal evidence event | Notifier/WS/query/ADOT do not exist, so the test proves *independence*, not recovery from a failing notifier; the atomic composition is now what `AwsRuntime.processor()` builds and the runtime test re-executes it in-process (2026-11-22) | T3, T5, T7 | PENDING |
| OBS-21 | C | None (no notifier/ADOT component exists) | Observation-only retry after notification failure, no payment retry or rollback | T5, T7 | PENDING |
| OBS-22 | B | `obs22RequiredAtomicWriteFailureBeforeSendDoesNotDispatchOrResend` (real `POSSIBLE_SEND` abort, zero downstream calls, no resend); `DynamoResultGatewayIntegrationTest.strongReadFailureStaysUnknownAndIsNeverNoResult`; `ResultServiceWiringTest.notCommittedResultFabricatesNoFinancialOutcome` | `UNAVAILABLE` disposition for an unreadable restart is a C3 envelope concern, not implemented | T3 | PENDING |
| OBS-23 | B | `rejectsCallerWithoutAuthorityOverTheMerchant`, `rejectsUnknownAcquirerWithoutUsingAnotherMandate`; `ConsoleAuthorizationRouteTest` (13, T4 console run 2026-11-22) `foreignCallerContextIsRefusedWithoutAnyBusinessWrite`, `duplicatedAuthorizationHeaderIsRefusedRatherThanFirstValueRead`; `ConsoleSubmissionAtomicIntegrationTest` (3) `refusedTokenRecordsNoClaimNoEventAndNoSend`; `ConsoleTokenCompositionTest` (5, 2026-11-23, `evidence/continuation-k1-20261123/`) `aValidTokenWiredThroughTheCompositionReachesTheServiceExactlyOnce`, `thePolicyCoreStillRefusesASubjectOtherThanTheConfiguredDemoSubject`, `aKeySourceOutageRefusesTheTokenAndNeverReachesTheService` | Deployed browser/console boundary (API Gateway JWT authorizer, CloudFront, WAF); the route exists only as an in-process request/response class, and while the composition test wires the real decoder, the dev harness and any deployed composition still need DevOps-supplied issuer/audience/JWKS/subject configuration | T2, T6 | PENDING |
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
| AUTH-001 R03 — approved route and mandate-scoped dispatch | A | `noPermittedRouteRejectsBeforeDispatch`, `approvesValidPurchaseWithIssuerProvenance` | Atomic dispatch is now the runtime path; a real issuer/network round trip is unproven | T3 | PENDING |
| AUTH-001 R04 — approved/declined/not-confirmed from evidence | A | `distinguishesIssuerDeclineFromProcessorRejection`, `timeoutAndAmbiguousReplyRemainNotConfirmed` | Atomic path emits the outcome; the handler still returns the JSON result, not the C3 envelope | T3 | PENDING |
| AUTH-001 R05 — timeout/ambiguity is never an issuer decline | A | `timeoutAndAmbiguousReplyRemainNotConfirmed`; `AwsTransportTest.ambiguousTransportFailureNeverRetriesOrFabricatesDecline` | Committed deadline transition still open | T3 | PENDING |
| AUTH-001 R06 — processor rejection vs issuer decline distinguished | A | `distinguishesIssuerDeclineFromProcessorRejection`; `C3ResponseBuilderTest.denialWinsWithoutLeakingAPosteriorConfirmation` | Internal source recorded; caller-visible disclosure policy still a design assumption | T3, T4 | PENDING |
| AUTH-001 R09 — duplicate-request control | A | `identicalReplayReturnsOriginalReferenceWithoutRedispatch`, `concurrentIdenticalArrivalsProduceOneAuthorizationAndOneDispatch` | Idempotency now runs on the wired atomic path, proven in-process against DynamoDB Local; a real Lambda invocation is unproven | T3 | PENDING |
| AUTH-001 R10 — retained transaction/response references and history | A | `lateConfirmationUpdatesLatestResultAndPreservesUncertainty`; `freshStoreInstanceReplayReturnsThePersistedFirstCollectionTime` | Human-readable retained history | T4 | PENDING |
| AUTH-001 R12 — synthetic data only, no PIN/PAN/CVV/cryptograms | A | `EventSanitizerTest` (6); closed schema tests | Path-wide proof; Security review | T7, T10 | PENDING |
| AUTH-001 R15 — identical replay returns latest outcome + original reference | A | `identicalReplayReturnsOriginalReferenceWithoutRedispatch`, `combinedRetryAfterLateConfirmationReturnsLatestButRejectsConflictAndForeignLink` | Replay is exercised on the wired runtime composition; a real Lambda invocation is unproven | T3 | PENDING |
| AUTH-001 R16 — valid retry links prior authorization, never redispatches | A | `linkedRetryWithChangedBusinessKeyRejects`, `linkedRetrySurvivesWindowExpiryAtEveryBoundary` | Retained-claim no-redispatch is exercised on the wired runtime composition; a real Lambda invocation is unproven | T3 | PENDING |
| AUTH-001 R17 — business key, conflict on materially different data | A | `conflictingDuplicateRejectsWithoutMutatingOriginal`, `unlinkedBoundaryCreatesNewPaymentAtExactWindowEnd` | Window boundary/expiry policy still a design assumption | T2, T3 | PENDING |
| AUTH-001 R18 — HD-01 profile: USD, 1.00–9,999.99, entry/CVM/cryptogram | A | `Iso8583DomainValidatorTest` (9) `rejectsEveryCurrencyOtherThanUsd`, `acceptsTheInclusiveAmountBoundaries`; `ChannelRequestValidatorTest` amount oracle vectors | Runtime profile source from the registry | T2, T3 | PENDING |
| AUTH-001 R20 — 30-second default timeout, late confirmation updates latest | B | `DeadlineDispositionTest.justBeforeTheBoundaryKeepsReadablePossibleSendInFlight`; `lateNetworkResponseReturnsUncertaintyThenCorrelatedLatestEvidenceOnRetry`; `ResponseTimeoutConfigTest` (6) `absentSettingResolvesToTheApprovedThirtySecondDefault`, `approvedDefaultMatchesTheDeclaredSpringProperty`, `validExplicitSettingIsPreserved`, `zeroOrNegativeSettingIsRejectedInsteadOfSilentlyAccepted`, `malformedSettingIsRejectedInsteadOfSilentlyAccepted`, `blankSettingIsRejectedInsteadOfFallingBackSilently` | **Default-timeout divergence corrected at source level 2026-11-20** (bounded T3 prerequisite, extended 2026-11-22 when `AwsRuntime` began building the atomic gateways): `AwsRuntime.processor()` now resolves the timeout through `ResponseTimeoutConfig` — absent setting → approved 30 s, explicit positive `RESPONSE_TIMEOUT_MS` preserved, blank/malformed/non-positive fails closed at cold start. `application.properties` `PT30S` and all approved specifications are unchanged. Evidence `evidence/t3-timeout-default-20261120/`. Still open: deadline persistence and one integrated deployed path | T2, T3 | PENDING |

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
2. **T3 — complete application path.** The atomic gateways **are now constructed** by
   `AwsRuntime.atomicService(...)` (2026-11-22) and the legacy four-argument fallback is no longer
   built on the AWS path; the composition is verified in-process against DynamoDB Local with a stub
   issuer. Still needed: mount the internal ISO route, execute the deployed journey (real
   authenticated boundary, real Lambda, real issuer/network), and run one integrated harness across
   approve, decline, network rejection, unknown/timeout, identical retry, conflicting retry,
   malformed response and late evidence with a "no second send" assertion.
3. **T4 — functional history.** An in-process read-only history route now exists (bounded snapshot
   read, signed scoped cursor, 400/403/404/405/410/503 semantics, gap and expired-prefix ranges,
   re-authentication around the store read), but there is no listener, list/detail page, TTL or
   expiry worker. Needed: a served boundary, seven-day logical expiry based on original
   `collectedAt` — with TTL mapped per record type and no deletion of
   business/evidence/identity records.4. **T5 — live delivery.** No Streams consumer, notifier, WebSocket connection state,
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

**Qué funciona hoy, de verdad:** existen y están probados los componentes de validación de canal, el
códec ISO 8583 `SIM-ISO87A-v1`, la traducción de peticiones y respuestas, la verificación
criptográfica de evidencia C4 (DIRECT y RELAY), la vinculación P6, y las escrituras atómicas de
negocio más evento crítico en DynamoDB. Desde el 2026-11-22 `AwsRuntime.processor()` construye ese
grafo atómico y ya no construye el servicio antiguo de cuatro argumentos, pero la composición sólo
se ha ejecutado en proceso contra DynamoDB Local con un emisor simulado. **No existe todavía un pago
integral real**: falta el límite autenticado desplegado, la invocación Lambda real y el ciclo real
con emisor/red.

## 10. T3 atomic wiring update — 2026-11-22

Bounded verification pass recorded in
[evidence/t3-atomic-wiring-20261122](evidence/t3-atomic-wiring-20261122/RESULTS.md). Binding:
HEAD `5bbd4eb`, branch `feat/wi-acq-auth-001`, JDK 25.0.5, Maven 3.10.1 offline, model
`nan/deepseek-v4-flash` with no override.

- **Production change:** only `AwsRuntime.java`. `processor()` now delegates to `atomicService(...)`,
  which builds the event store and the claim, dispatch-intent, result, network-result and
  late-evidence gateways; every required environment value fails closed and no capacity default is
  invented.
- **Test change:** one helper (`storeHead()` treats DynamoDB Local's empty item map as an absent
  HEAD). No assertion weakened, removed or skipped; the pre-fix red run is preserved.
- **Executed:** one Maven invocation, 23 classes, **192 tests / 0 failures / 0 errors / 0 skipped /
  exit 0 / BUILD SUCCESS**. The three fix-carrying classes re-verified at 6, 9 and 11 tests. Full
  suite **deferred at that time**; superseded by the 978-test full run in §11 (the 959-test
  baseline belongs to `cfc456f` and never transferred).
- **Freeze:** 292 inputs unchanged before/after, `sha256sum -c` 292/292 OK, `git status` identical.
  Limitation: post-edit baseline, not pre-edit bytes.
- **Rows updated above:** only those whose runtime reachability actually changed. All remain
  `independent verification: PENDING`; end-to-end integrated counts remain **0**.

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

## 11. Full offline suite at the current bytes — 2026-11-22

Evidence [evidence/t3-fullsuite-20261122](evidence/t3-fullsuite-20261122/RESULTS.md). Binding:
branch `feat/wi-acq-auth-001`, HEAD `5bbd4eb` unchanged, OpenJDK 25.0.5, Maven 3.10.1 offline,
model `nan/deepseek-v4-flash` with no override.

- **Suite basis replaced.** `mvn -o -B test` ran once: **978 tests / 67 classes / 0 failures / 0
  errors / 0 skipped / exit 0 / BUILD SUCCESS / 01:48 min**, aggregated from both the Surefire
  console line and the 67 `TEST-*.xml` suites. The T1 basis recorded in §1 ("959 tests, `cfc456f`,
  reused, suite rerun: No") no longer describes these bytes and must be read through this section.
- **Cited counts re-verified.** All 56 `Class (n)` counts cited in §4–§6 match this run exactly; no
  cited test class is missing or has a different size. The 19-test growth from 959 to 978 is in
  classes added after `cfc456f`, not in a changed cited class.
- **Row statements that genuinely change:** only R05's evidence note, where
  `AwsRuntimeAtomicWiringIntegrationTest` (10, green) is now confirmed inside a full-suite run at the
  current bytes rather than a bounded per-class run; and §10's "full suite deferred", now closed.
  No requirement or scenario status moves: A/B/C stay as mapped, integrated stays **No** on every
  row, and every row keeps `independent verification: PENDING`.
- **Comment fix.** Javadoc-only correction in `AuthorizationService`: the late-evidence constructor
  is the one `AwsRuntime.atomicService` binds for the Lambda runtime, and the `@Autowired`
  constructor is only the Spring/JDBC harness binding. `comment-fix.diff` changes three comment
  lines; no executable change, and the suite is green afterwards.
- **Freeze.** 292 inputs identical before and after (same SHA-256, empty diff), HEAD unchanged.
  Limitation: slice-start working tree, which includes pre-existing uncommitted owner/coordinator
  edits, so this proves the run mutated nothing — not that these are the `5bbd4eb` committed bytes.

**Still open:** T3 requires the deployed journey — mounted internal ISO route, real authenticated
boundary, real Lambda invocation and a real issuer/network round trip. This section changes no
verdict and adds no acceptance.

## 12. Continuation H1 — origin-verification gate + read-only history (2026-11-23)

Evidence [evidence/continuation-h1-20261123](evidence/continuation-h1-20261123/RESULTS.md). Binding:
branch `feat/wi-acq-auth-001`, HEAD `5bbd4eb` unchanged, OpenJDK 25.0.5, Maven 3.10.1 offline,
Docker 29.1.3 with `amazon/dynamodb-local:2.6.1`, model `nan/deepseek-v4-flash` with no override.

- **Delivered:** the application-side N-02 gate (`ConsoleOriginVerificationGate`,
  `ConsoleIntegrationEntryPoint`, denial record/sink: `EDGE_ORIGIN_VERIFY`, constant-time
  byte-exact comparison, ABSENT/EMPTY/DUPLICATE/MISMATCH, first application step, fixed opaque
  `403`) and the HTTP-only read route `GET /console/v1/transactions/{id}/events`
  (`ConsoleHistoryRoute`, `ConsoleHistoryQuery`, `ConsoleHistoryCursorCodec`).
- **Executed:** compile exit 0; one red test compile (two test-source errors: a `final` local and
  the undeclared `EXPIRED_TOKEN` reason); red focused run **33 tests / 1 failure / 4 errors /
  exit 1**; green focused run **33 tests / 0 failures / 0 errors / 0 skipped / exit 0** (4 + 9 +
  20); existing console classes **16 tests / 0 failures / 0 errors / exit 0**. Both red artifacts
  are preserved; counts and zero skips are identical red-to-green, so no case was deleted or
  disabled, and the six red sites were test-harness defects (fixture request method; append
  helper's `EventContext`), with corrections confined to test sources by modification time.
- **Rows whose status genuinely changed:** R01, R25, OBS-18 and OBS-19 move **C → B**, and the
  coverage counts in §3 follow (requirements 13/11/1; scenarios 3/21/4). No other A/B/C token,
  requirement or scenario statement changed. Gap register item 3 (T4) was corrected because the
  capability it said did not exist now partially does.
- **Why these move and others do not:** an executed, DynamoDB-Local-backed path now asserts the
  row's observable behavior in-process. Rows whose observable behavior still needs a deployed edge,
  a real issuer or a browser (R19, OBS-23, OBS-28 and the rest) keep their token; the origin gate is
  **not** authentication and changes no authentication row.
- **What green does not establish:** no real issuer/JWKS, no CloudFront, WAF, deployed edge or
  Keycloak, no cursor key sourcing, no server-side read-pass store, no TTL/compaction, no
  listener, no Lambda invocation, no browser or UI. **N-02 is not closed by these bytes alone.**
- **Freeze/limitation:** transcript, exits and per-file SHA-256 are in the evidence directory
  (`compile-*-exit.txt`, `history-and-ov-tests-*-exit.txt`, `slice-files.sha256`). The console
  package is untracked at HEAD, so the hashes describe the tested working tree, not committed
  bytes; the red-revision sources are not preserved.
- **Post-QA corrections (2026-11-23):** independent QA returned FAIL with QA-DEF-1/2/3. The
  history `503` body now emits the pinned `AUTHORIZATION_UNAVAILABLE`; the signed cursor binds and
  checks the verified token `issuer` and the optional trusted `terminalRef`; both per-page
  authentications remain. Mandate identity representation (`registryMandateId`/`registryVersion`)
  and `?cursor`+`?after` are open questions for Requirements, not behaviour chosen here; `epoch`
  is settled for this HTTP-only read by D-6's "when live". Focused console suites: **74 tests, 0
  failures, 0 errors, 0 skipped, exit 0** (one preserved red run, 74/1, from a test fixture that did
  not carry the new terminal). Evidence
  [evidence/continuation-h1-qa-fixes-20261123](evidence/continuation-h1-qa-fixes-20261123/RESULTS.md).
- **D-6 clarifications follow-up (2026-11-23):** the domain review of C-1..C-4
  (`reviews/d6-clarifications-20261123/domain-review.md`, SHA-256 `d6a57e0c…1464b9`) is advisory,
  not adoption. Its C-1.3 and C-2 directions are implemented: the post-read re-authentication now
  returns context `403` before a retired-generation `410` (matching the pre-read continuation), and
  a `cursor` combined with `after` or `snapshot` is refused `400 INVALID_CURSOR` with a null action
  after authentication and before mandate resolution or any store read — the `?cursor`+`?after`
  question is therefore settled as a refusal, not left open. Focused console suites, single run:
  **79 tests, 0 failures, 0 errors, 0 skipped, exit 0**. Evidence
  [evidence/continuation-h1-d6-c2-20261123](evidence/continuation-h1-d6-c2-20261123/RESULTS.md). No
  requirement or scenario status moves.
- **D-6 C-3 follow-up (2026-11-23):** C-3 of the clarified text
  (`design/contract-deltas/d6-clarifications-rev2-20261123/d6-clarifications.md`, SHA-256
  `3aaac88a…9643a3`) is approved for adoption by the coordinator and is implemented; the independent
  domain re-check (`reviews/d6-clarifications-rev2-20261123/domain-review.md`, SHA-256
  `fde09a1f…d3d1ad7`) is advisory, not human acceptance. The history route refuses in the fixed C-3.3
  order and nothing after the `200`: `401` → syntactic request-form `400 INVALID_CURSOR` → resolution
  `503`/`403` → malformed-payload `400` → retired-schema `410` (after signature verification and
  before the context check) → expired `410` → context `403` → representation `410` → head
  `503`/`404` → `after` beyond the frozen head `400` (only after the head read) → `R`/`F` `410` →
  bound-`lastSequence` `400` → `200`. Unknown and repeated parameters are refused in the form step.
  `ConsoleHistoryCursorCodec` now distinguishes a retired schema from tampered input. Focused console
  suites, recorded run: **90 tests, 0 failures, 0 errors, 0 skipped, exit 0**; an earlier green run
  (87 tests) preceded three added resolution/head-stop cases. Evidence
  [evidence/continuation-h1-d6-c3-20261123](evidence/continuation-h1-d6-c3-20261123/RESULTS.md). No
  requirement or scenario status moves.
- Every row remains `independent verification: PENDING`, and the integrated count stays **0**.

## 13. Continuation K1 — real issuer decoder package (2026-11-23)

Evidence [evidence/continuation-k1-20261123](evidence/continuation-k1-20261123/RESULTS.md). Binding:
branch `feat/wi-acq-auth-001`, HEAD `5bbd4eb` unchanged, Temurin 25.0.5, Apache Maven 3.10.1
offline, model `nan/deepseek-v4-flash` with no override. No dependency added; `pom.xml` unmodified.

- **Delivered:** `TrustedIssuerConfiguration` (every value required, no default issuer/audience/
  subject/key, RSA-only allowlist, 900-second ceiling, disjoint resource scopes and audiences),
  the key-source seam (`JwksSource`, `JsonWebKeySet`, `JwksParser`, `HttpJwksSource`),
  `CompactJws`, the real verifier `JwtConsoleTokenDecoder` (shape → algorithm allowlist → `kid` →
  key lookup with one rotation refresh → signature → issuer → audience/authorized party → scope →
  `exp`/`nbf`/`iat` → lifetime ceiling, failing closed at each step) and
  `ConsoleTokenComposition`, which wires that decoder behind the unchanged
  `DemoConsoleAuthorizer` policy core. No claim-order or mandate-intersection logic changed.
- **Executed:** compile exit **0** (no stdout/stderr); K1 decoder group exit **0** — **32 tests, 0
  failures, 0 errors, 0 skipped**; K1 composition exit **0** — **5 tests, 0 failures, 0 errors, 0
  skipped**; focused console suites exit **0** — **90 tests, 0 failures, 0 errors, 0 skipped**. Every
  run was a separate invocation with its own exit/stdout/stderr file. No red run occurred, so no
  preserved-red attempt is disclosed.
- **Refusals actually exercised:** bad algorithm (and absent algorithm), missing `kid`, signature
  mismatch (including a tampered payload under a valid header and key), wrong issuer, wrong
  audience (including both resource audiences), expired, not-yet-valid (`nbf` and future `iat`),
  missing scope, wrong subject (through the unchanged policy core), and the rotation case where a
  token signed by the retired key is refused with `UNKNOWN_KEY` after exactly one key-set refresh.
  Adjacent refusals also exercised: key-source unavailable, unsupported `crit` header, missing
  `exp`, lifetime above the ceiling, malformed compact-JWS shapes, WS-scope-on-HTTP-token.
- **Rows whose status genuinely changed:** R19 and OBS-23, whose evidence and limitation text now
  describe the real verifier package. **No A/B/C token moves** — the observable behaviour in both
  rows still needs a real issuer and a deployed boundary, so both stay `B` and the §3 counts are
  unchanged.
- **What green does not establish:** keys were generated locally in the test JVM; there was **no
  network JWKS fetch, no Keycloak round trip, no browser flow and no deployed composition**. The
  bundled `DevStubTokenIssuer` remains an unsigned local double (its handles are opaque
  `devstub_…` strings, not JWTs) and is **not authentication**. `HttpJwksSource` and `JwksParser`
  compiled but are not covered by any test in this pass. Activation still needs DevOps-supplied
  issuer, audience, JWKS URI and subject values, which were not invented.
- **Open points carried, not resolved:** a JWKS-unavailable condition surfaces as `401`
  `UNAUTHENTICATED` on the submission route rather than a truthful `503` (a `503` needs a new route
  branch); the dev stub is retained for the loopback harness; and the algorithm allowlist is
  RSA-only (`RS256`/`RS384`/`RS512`).
- **Freeze/limitation:** the console and `console/jwt` packages are untracked at HEAD, so the
  recorded SHA-256 hashes describe the executed working tree, not committed bytes.
