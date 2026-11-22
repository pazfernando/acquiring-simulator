# Operation A — actionable completion plan

Date: 2026-11-20. Owner: Fernando Paz. Planning owner: AI-SDLC Coordinator.
Status: actionable execution backlog; owner dispositions recorded on 2026-11-20. No completion inferred.

## Outcome and authority

Complete the already-selected Operation A demonstrator: the presenter signs in as the single demo user, submits a synthetic POS purchase through the channel/ISO boundary, sees the evidence-based result and live task timeline, and can reopen retained history for seven days. Reconnection, duplicate requests and telemetry failure must not resend a payment or fabricate a result.

No Operations B/C, live payment networks, public client hosting, multiuser administration, public financial-reconciliation endpoint or automatic financial retry/reversal is added.

Architecture and local application/Terraform/CI construction authority remain valid. Access, on-demand billing, environment and Security ownership follow [the owner decision ADR](adr-operation-a-access-and-environment.md). External execution approvals are not inferred. Publication, new credential flows, remote pipeline execution, backend initialization and deployment require their applicable explicit authorization. This plan does not grant it.

## Starting point, not a completion claim

- Application components exist for atomic claim/dispatch intent/results, ISO parsing/packing, channel request/response, signed normal/late evidence, conflict metadata and first-collection metadata. Their saved developer-test evidence is linked from implementation-progress.md.
- The internal HTTP transport primitive has five passing tests plus related C4 regressions. It does not authenticate service principals or activate the application path.
- The commissioned baseline verification completed: **959 tests / 63 classes, zero failures/errors/skips, exit 0**, with 282 frozen inputs unchanged across the run. See [baseline results](evidence/p2c4-fullsuite-20261120T224632Z/RESULTS.md). The baseline-run portion of T1 is complete; its requirement-to-evidence matrix remains pending. This is developer-test evidence, not end-to-end completion or independent acceptance.
- There is no current verified end-to-end demonstration, independent QA verdict, independent Security verdict or deployed AWS environment established by this plan.
- Existing WORK_ITEM.md and implementation-progress.md contain historical leading statuses superseded by later dated entries. Use delivery-status.md as the current summary; preserve approved/pinned source bytes.

## Owner dispositions — ready for local execution

See [the ADR and required scenario detail](adr-operation-a-access-and-environment.md). D1–D4 below are completion-plan IDs, not the historical owner-decision numbering.

| ID | Owner disposition | Team action |
| --- | --- | --- |
| D1 | API Gateway external/internal as appropriate, mTLS + OAuth 2.0 (`private_key_jwt`). | Document per-scenario API type, client assertion/token validation and trusted identity mapping; validate compatibility and obtain independent Security review. Do not substitute IAM/SigV4 for this selected ingress direction. |
| D2 | No business capacity/cost ceiling decision now; on-demand where supported; one-week TTL where applicable. | Remove proposed quotas as approval gates, map expiry safely, retain protocol/service safeguards. On-demand does not mean zero or unlimited cost. |
| D3 | GitHub variables/Secrets, Actions OIDC, `us-east-2`. | Parameterize account/role/backend/issuer inputs, separate sensitive values from variables, avoid static AWS keys. No account number or secret is needed in chat to construct locally. Demo issuer/subject configuration remains team work; a genuine unresolved product choice is escalated with a concrete recommendation. |
| D4 | Existing `ai-sdlc-security` owns independent review. | Commission the installed specialist, verify execution availability and record its verdict against exact revisions. Review remains pending, not unavailable by default. |

No further owner clarification is required to continue local planning, traceability and construction. Resolve technical details within the selected direction; escalate only actual incompatibilities or unapproved external actions.

No new decision is requested on Operation A scope, Java 25, native Terraform, one demo user, screen-sharing, seven-day history or the prohibition on automatic redispatch. CI recommendation: GitHub Actions in the existing GitHub repository; keep the SAM template as historical/reference infrastructure until Terraform parity, then mark it non-deployable/deprecated rather than deleting it. These routine proposals do not authorize remote actions.

## Ordered delivery backlog

Task states below describe whole deliverables, not individual classes. Dependencies prevent concurrent writers. Subtasks may be small enough to survive ACP interruptions, but task IDs and exit criteria stay stable; no endless new phase labels.

| Task | Deliverable / concrete actions | Responsible | Dependency | Exit evidence |
| --- | --- | --- | --- | --- |
| T1 | Establish the current baseline and traceability. Recover the already-commissioned suite, record exact source/build identity and failures, and map every in-scope requirement/scenario to implementation, executed tests, gaps and reviewer. Fix actual baseline failures through Implementation. | Coordinator + Implementation | Existing verification settles | Current suite result with source identity, one requirement-to-evidence matrix, no unexplained mandatory coverage gaps. |
| T2 | Finish the trusted application boundary. Bind authenticated service identity to mandate/context; configure trusted test/demo signing-key references and rotation/custody design; finish server-side fixture/route resolution and durable ID/alias/original-instruction allocation. Resolve only genuine remaining timeout/legacy-row contract ambiguity through Requirements + domain review; never infer committed versions or migrate historical rows silently. | Implementation; DevOps supports identity/key design; Requirements/domain reviewer for actual contract deltas | T1; D1; owner direction from D2/D3 | Authorized callers work; forged identities, wrong mandate and invalid references fail before writes/send. Exact persisted original bytes, hashes and IDs are reproducible. Decisions and unsupported topology are explicit. |
| T3 | Connect the complete Operation A application path. Wire the channel entry, mapper, ISO request/response validation, simulator network/issuer, actual signed evidence and atomic receipt path into the runtime. Eliminate reliance on a test-only trusted-result seam. Cover network rejection and late-evidence real-adapter paths. Retain legacy harnesses without allowing fallback on atomic failure. | Implementation | T2 | One integrated harness executes approve, decline, network rejection, unknown/timeout, identical retry, conflicting retry, malformed response and valid/invalid late evidence. Assert no second financial send and no partial business/event/receipt commit. |
| T4 | Implement functional history. Build journal-aware detail/list/navigation required by the approved UI and event queries, fixed snapshots, signed scoped cursors, gaps, expiry ranges and seven-day logical expiry based on original collectedAt. Never delete business/evidence/identity records to free history capacity. | Implementation | T3 | Approved history/source vectors execute; cursor tamper/scope/expiry return the specified errors; journal outage and expired history are honestly labeled, not presented as current financial truth. |
| T5 | Implement live delivery. Add event-only Streams processing, notifier, WebSocket connection/subscription state, acknowledgements, duplicate controls, bounded buffering, head synchronization and reconnect catch-up from the same event table. Do not create a second history payload store. | Implementation | T4; D2 | W01–W33 mapped and executed where applicable; duplicate/out-of-order/lost notifications, stale ACKs and reconnection never cause a payment POST. Notifier failure does not fail an otherwise durable payment. |
| T6 | Deliver the presenter-facing console. Implement demo-user sign-in integration and the loopback relay; bind the demo-user JWT and the selected service authentication to HTTP/WebSocket requests. Build the POS form, clear result, task timeline, history and detail. Keep tokens/keys out of browser storage/URLs and render supplied text inertly. | Implementation | T3–T5; JWT inputs from D3 | A browser journey submits one purchase, sees the result/tasks, reconnects without resubmission and reopens history. JWT expiry/wrong subject/Origin checks deny access without leaking stored IDs. Screen-sharing remains the exposure model. |
| T7 | Complete common task instrumentation and asynchronous telemetry. Wire the manifest/hooks to the real path, show missing task coverage explicitly, and prove Java 25 with the selected ADOT packaging. | Implementation; DevOps owns packaging/configuration | T3; final coverage checks with T5/T6 | Executed tasks emit permitted observations. Telemetry/export/notification outages do not change payment results. Compatibility and actual export are proven in the relevant environment or remain explicitly blocked. |
| T8 | Prepare reproducible infrastructure and CI. Author native Terraform for the final resource set, private encrypted/versioned S3 state and locking with explicit bootstrap, IAM, JWT/WebSocket, keys and observability. Author build/tests/scans/artifact identity and a human-gated delivery workflow. Preserve component-source provenance. | DevOps owns CI/IaC; Implementation alone executes Git/provider operations | Start once T2 topology is settled; finalize after T4–T7 | Local format/validate/tests/scans with pinned tools and recorded exceptions; artifacts have digests; no credentials/state committed; Security design review covers new boundaries. Authored pipeline is not a remote successful run. |
| T9 | Prepare and obtain approval for actual AWS execution. Present exact account/region, an on-demand cost estimate (no owner spending ceiling requested at this stage), reviewed infrastructure plan, artifact identity, access method and rollback/cleanup. After explicit authorization, publish/run the approved pipeline, bootstrap/deploy the demo and execute cloud-only integration checks. | DevOps; Implementation executes authorized Git/provider work | T8; D1–D4; applicable Security review; explicit external-action approvals | Actual pipeline/deployment receipts and endpoints, tested service authentication/demo JWT/Streams/WebSocket/ADOT and applicable service-limit behavior. Environment is not declared usable on a plan or deployment receipt alone. |
| T10 | Independently verify, fix defects and obtain acceptance. QA derives expected results from approved criteria, executes the browser/payment/failure/history/security-boundary suite on the exact final artifact/environment, and records verdict. Independent Security reviewer records findings/closure. Implementation fixes defects; affected checks rerun. Fernando performs and accepts the demonstrator. | QA + independent Security; Implementation fixes; Fernando accepts | Local QA may start at T6–T8; final environment evidence follows T9 | All mandatory criteria passed, no acceptance-blocking defects, independent QA and Security reports identify the artifact, and Fernando's acceptance is recorded. |

## Milestones that the human can see

1. **Payment connected:** T1–T3 demonstrate the whole synthetic payment path, not only component tests.
2. **Console usable locally:** T4–T7 demonstrate submission, live tasks, history and reconnect in the browser.
3. **Demo environment usable:** T8–T9 produce reviewed infrastructure and an authorized, actually exercised AWS environment.
4. **Operation A accepted:** T10 closes independent verification and human acceptance.

No completion percentage or delivery date is inferred from test counts. After T1, Implementation/DevOps must estimate remaining work from the bounded tasks and current runtime throughput; report ranges and assumptions, not an invented deadline.

## Traceability and execution rules

- delivery-status.md is the human entry point; this plan is the stable backlog. implementation-progress.md and evidence directories are the detailed journal, not the status dashboard.
- Coordinator maintains requirement ID → story/deliverable → approved criterion/vector → implementation paths → test command/result/source identity → independent verdict → current status/blocker.
- Reuse AUTH-001 business requirements and OBS-001 console/traceability requirements with qualified IDs; R07 in the two work items is not the same identifier.
- Preserve original specification/approval hashes. New derived stories or clarifications are explicitly labeled; they are not retroactively approved rules. Requirements owns substantive specification changes and domain review examines payment-rule changes.
- Implementation uses the existing bound OpenCode ACP route and its configured default model. Git/provider operations, including inspection, remain with that agent. DevOps and QA receive scoped ownership; no concurrent checkout writers.
- On interruption, recover saved files/results first. Preserve failed attempts; save distinct output/error/exit artifacts and pre/post source hashes. Retry only incomplete or invalidated work, not already-covered tests without cause.
- Do not ask Fernando to authorize routine coding/tests already in scope. Escalate genuine business conflicts, risky architecture changes and unapproved external effects with a concrete proposal.
- Reports use complete Spanish sentences: what now works, which task is active, what prevents the next milestone, and what remains to finish the product. Never present a queued task as completed.

## Security design follow-up — 2026-11-20

The [independent review](reviews/access-design-20261120/security-review.md) is complete and blocks design readiness of the changed access/expiry boundaries for missing mandatory evidence, not demonstrated vulnerabilities. Implementation Security remains pending. This does not halt unrelated local work or revoke Fernando's selected direction.

1. DevOps authors the scenario-specific access proposal and identity/key/OIDC/state schema in `design/access-realization/` (NV-01–04). Native PRIVATE REST + Gateway mTLS is incompatible; distinguish authenticated public ingress from genuinely private networking, and verify WebSocket separately. Material architecture alternatives remain proposals.
2. Requirements authors a sourced record-expiry/no-resend delta in `design/retention/` (NV-05 and superseded-contract advisory). Coordinator routes the exact revision to acquiring-domain; retain approved historical sources.
3. Implementation assessed the proposal: [compatibility-assessment.md](design/access-realization/compatibility-assessment.md), SHA-256 `4886106c0afbf9ded1736ef3c0756c2bc8c738c58c61457faa0a635ecdd836ac`. It is not implementable by configuration. CA-1 (public late-evidence route versus the approved internal-only rule), CA-2 (role vocabulary, including a `DIAGNOSTIC` role the registry withholds) and CA-3 (transport context too narrow) need disposition before source work; CA-4 restates that the atomic gateways stay unwired; CA-5 needs a contract delta.
4. Security re-reviews exact resulting artifacts. Only then close the affected design gate; final implementation checks, QA and human acceptance remain separate.

These are team-owned details. Escalate to Fernando only a concrete irreducible business/architecture incompatibility, with a reviewable recommendation and practical consequence.

## Definition of complete

Operation A is complete only when the approved synthetic purchase, rejection, uncertainty, duplicate and late-evidence behaviors work through the actual application; the authenticated presenter console displays live tasks and retained history honestly; reconnect never resends a payment; the reviewed Terraform/CI deliver the selected authorized environment; and independent QA, Security and Fernando's acceptance are recorded for the final artifact. Until then report the achieved milestone and the remaining tasks explicitly.

## Source links

- [Business work item](../WI-ACQ-AUTH-001/WORK_ITEM.md)
- [Console/traceability work item](WORK_ITEM.md)
- [Requirements and existing user story](requirements.md)
- [Acceptance scenarios and vector inventory](acceptance-scenarios.md)
- [Owner decisions and architecture approval overlay](owner-decisions.md)
- [Channel contract](channel-contract.md)
- [Observation contract](observation-contract.md)
- [Implementation evidence journal](implementation-progress.md), especially sections 30 and 47–53.

## 2026-11-20 owner update

The previous IAM/SigV4 application-ingress recommendation, numerical business-capacity approvals, proposed budget ceiling and unavailable-Security assertion are superseded by the linked ADR. Review affected contracts and tests before changing behavior; preserve pinned historical sources and approval evidence.

## Execution resumed — 2026-11-20

Fernando: "ok continua y me vas avisando". T1 traceability has been assigned to Implementation on the existing ACP session. T2 early access-design review has been commissioned independently to ai-sdlc-security, with read-only product source and a separate review output directory. Admission receipts do not establish completion. Affected specification changes still follow Requirements/domain review before dependent implementation. Existing local construction authority remains in force; no blanket owner clarification gate remains.

## T1 deliverable received — 2026-11-20

The [requirement-to-evidence matrix](requirement-traceability.md) maps 67 rows: 25 OBS requirements, 28 OBS acceptance scenarios and 14 linked AUTH requirements. It reuses the frozen 959-test evidence without rerunning. No row is end-to-end integrated; every independent verdict is pending. This completes the T1 documentation deliverable only. Security access-design review is running. While that review proceeds, Implementation has a bounded T3 prerequisite task to fix the effective AWS timeout default (2000 ms versus approved 30 seconds) with focused tests; atomic runtime activation is not included. No new owner clarification is required for that correction.
