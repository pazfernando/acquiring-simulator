# WI-ACQ-OBS-001 — Cross-cutting transaction console, traceability and channel ACL

- Product: Acquiring Processor Simulator
- Status: D1 profile/channel, asynchronous direction, single-demo JWT/screen-sharing exposure and seven-day history approved. Functional guide 0.5.3 and replacement sequence diagrams passed independent documentation review; the reviewed 0.5.3 and sequence exports were synchronized to Drive, then a documentation-only PNG-first local revision was produced and awaits Drive re-sync. Construction remains blocked pending architecture approval and technical preflight.
- Human Product Lead: Fernando Paz
- Date: 2026-11-18

## Current Security review and follow-up — 2026-11-20

The [current delivery status](delivery-status.md) and [completion plan](operation-a-completion-plan.md) supersede historical construction-blocked and specification-only labels below. Fernando authorized local completion of Operation A and selected the access/environment direction in the ADR; publication, credentials, remote execution and deployment remain separately gated.

Independent reviewer: ai-sdlc-security, native OpenClaw/Codex, observed model gpt-6.1-sol, no override. Early design verdict: **BLOCKED by missing mandatory design evidence**, not a demonstrated runtime vulnerability. Final implementation Security verdict: **PENDING**. Report: [access-design review](reviews/access-design-20261120/security-review.md), SHA-256 `0d71ea007e56b12d9873df14126c22640c3abc68052b42db1f47b0a9688d218e`. Its six reviewed design hashes matched. No human risk acceptance or Security PASS is implied.

Follow-up ownership:
- DevOps run `03515333-84b0-42dd-b373-0a07b2db78b6` delivered the proposal in `design/access-realization/` ([design.md](design/access-realization/design.md), SHA-256 `83e4ea14b3959dcc6d8535aee0f0dd715783764e8922a72b6c0c3454a40829f4`; [configuration-schema.md](design/access-realization/configuration-schema.md), SHA-256 `1f497daade1a8429458e2cc40047adf326a97f2fb6636d583e1bf2e930bf1bb2`). Proposal only: private REST does not support native Gateway mTLS, so it recommends a Regional custom domain with mTLS on public authenticated ingress; WebSocket has a `$connect`-only authorizer and no documented native mTLS. No silent substitution of owner-selected mTLS/OAuth; seven marked decisions remain for owner or Security disposition.
- Requirements run `819be151-6edb-4e96-812f-e7fcbc632744` drafts record-specific retention/expiry and no-resend clarification in `design/retention/`. It currently holds only a saved stub and source-identity evidence because the configured model reached a provider quota; retry is scheduled, and independent acquiring-domain review follows the exact revision.
- Independent Security re-review of the revised access design delivered by ai-sdlc-security on the owner-recorded `openai/gpt-6.1-sol`: [security-review.md](reviews/access-design-rev2-20261122/security-review.md), SHA-256 `b74e5a686525a1c2ceff6713e23e938f4e54f0538a9587240ce8307d1dc38120`, design verdict **FAIL** with two confirmed IMPORTANT defects on the internal late-evidence path and an advisory on rotation overlap. The design gate therefore stays blocked; DevOps run `21388b05-0754-4889-bc24-b1a030729257` prepares `design-rev3.md`, and a further independent review follows. The earlier review in `reviews/access-design-rev2-20261121/` ran on a different model and is retained as supplementary input only.
- Independent acquiring-domain review of the retention draft delivered: [domain-review.md](reviews/retention-20261121/domain-review.md), SHA-256 `dcdd04ac330491512b04906264760e47e7dce3c3506f78e76688ed209fc4d322`, verdict CONDITIONAL. It confirms the no-resend identity and ordering semantics, confirms R20/A07 fix the 30-second default so U2 needs documentary harmonization rather than an owner decision, and holds that event tombstones cannot replace authoritative business evidence. Requirements run `e6ee31a9-71f8-40d0-a182-4eb1c61a219d` applies those two corrections; NV-05 stays open.
- Implementation compatibility pass delivered: [compatibility-assessment.md](design/access-realization/compatibility-assessment.md), SHA-256 `4886106c0afbf9ded1736ef3c0756c2bc8c738c58c61457faa0a635ecdd836ac`, with evidence manifest. The proposal is coherent but not implementable by configuration; four material items and one stale-contract item are recorded.
- Owner resolved three follow-ups on 2026-11-20 evening: WebSocket transport assurance accepted without mTLS (notification-only), self-managed Keycloak as the `private_key_jwt` issuer, and late evidence confirmed internal only so no public route is created. See [owner-decisions.md](owner-decisions.md). DevOps run `60f419d9-b587-4724-90bb-fecf10a1a7a0` delivered the revision: [design-rev2.md](design/access-realization/design-rev2.md), SHA-256 `6b15161875e702ebae74e4f76b4b94cae9ffbbb94d6a9fcd42aee563b8f3873c`, with `evidence-rev2/`, v1 preserved byte-identical. It realizes the Keycloak hosting profile, the internal-only late path, the code-aligned role vocabulary and the corrected WebSocket lease, and states that the transport-context widening and the unwired atomic gateways remain open. No finding is claimed closed and no provisioning is authorized.
- Implementation delivered the timeout-default correction: `evidence/t3-timeout-default-20261120/`, focused 45 tests, 0 failures, exit 0, atomic runtime still inactive. Its compatibility pass on the access proposal is run `4d8eba2f-e961-490a-9a54-17f7c042df65`; no other concurrent source writer is authorized.
- Coordinator commissions independent Security design re-review after the exact missing artifacts are available. Changed contracts follow the human-approval/domain-review gates as applicable; no new architecture choice is approved by this assignment.

## Owner request and decisions

Owner requested an HTML interface to submit a simulated POS transaction through an intermediate service to the simulator, with each task recorded in a database and visible from the originating screen.

Literal follow-up:
1. "para todo, no solo para operación A, es decir es una especie de hook que debe incluirse en cualquier avance del score que vayamos implementando"
2. "de acuerdo" — live updates without page reload.
3. "de acuerdo" — persistent transaction history and details.
4. "puer es un anticorruption láser, la idea es que reciba lk qué reciba el envía iso 8583 a nuestro simulador"

Interpretation of item 4: anti-corruption layer (ACL) adapting supported channel inputs to ISO 8583 at the simulator boundary. This is an owner-selected boundary direction, not an approved wire profile. "Whatever it receives" does not imply accepting arbitrary/unvalidated payloads.

## Goal and boundaries

- HTML POS initiation plus live transaction timeline and persistent history.
- Traceability is a cross-cutting obligation for all implemented and future simulator capabilities, not just Operation A. This does not commission implementation of all future business operations now.
- Each implemented capability must report task execution through a common instrumentation contract. Requirements must address correlation, attempts, provenance, partial observations and asynchronous/late evidence without inventing payment outcomes.
- Channel ACL produces ISO 8583 for the simulator. Edition/profile, MTIs, fields, encoding, framing/transport, response mapping and supported channel contracts remain unresolved; generic inquiry MTIs must not be invented. Not every future auxiliary capability necessarily has an ISO 8583 mapping.
- Preserve synthetic-data-only boundaries and existing payment semantics. No real credentials, live network connectivity, publication or deployment authorized.
- Current AWS implementation uses HTTP JSON ingress and a synthetic internal downstream contract. This request is a new delta, not evidence that ISO 8583 is already implemented. Do not rewrite prior approval artifacts.

## Sources

- ../WI-ACQ-AUTH-001/WORK_ITEM.md
- ../WI-ACQ-AUTH-001/aws-architecture-update.md
- ../../sources/imports/2026-11-18/acquiring_simulator_functional_guide.md, especially sections 3 and 13
- ../../sources/imports/2026-11-18/software_components_aws.md

## Requirements — ai-sdlc-requirements

- Document: [requirements.md](requirements.md) — draft delivered; citation corrected after review, renewed review pending.
- Scenarios: [acceptance-scenarios.md](acceptance-scenarios.md) — draft delivered; 28 scenarios, not executed.
- Blocking questions: ISO profile and channel/response contracts; trace-persistence failure semantics; remaining architecture choices require explicit disposition.

## Independent domain review

- Reviewer: ai-sdlc-acquiring-domain; run 01abec77-d770-4a41-b3c4-3accc7251f4a, observed default openai/gpt-6.1-sol.
- Verdict: CONDITIONAL for analysis/research selection; BLOCKED for construction. Evidence: [domain-review.md](domain-review.md), MD5 c49694b00907371de7f8fd16e3885f9f. Exact reviewed revisions are recorded in that document; this is not human approval.

## Human architecture decision

- ACL direction and live/history capability chosen by owner; detailed interfaces, event/storage/state model and failure behavior PENDING.

## Implementation and verification

- Implementation: NOT AUTHORIZED by this record; specification only.
- QA: PENDING; no tests executed.
- Security: triggered by new UI, interfaces and trace data; independent review PENDING.
- Human acceptance: PENDING.

## Requirements draft and ISO research — 2026-11-18

- Requirements agent run 9ec6d469-552c-4521-a1ed-3e975331422d; observed configured default openai/gpt-6.1-sol, no model override. Coordinator read both artifacts and verified 25 rule rows (10 SOURCED, 12 DESIGN ASSUMPTION, 3 UNRESOLVED), and 28 scenario rows. These are draft specifications, not executed tests.
- requirements.md MD5 2ac753ee2ec5f6a5e8951256c86581a5; acceptance-scenarios.md MD5 6b26f7204ee969bb3c74267380277c31. Original scope bytes preserved in revisions/owner-scope-2026-11-18.md (MD5 67f9979245fba660bc3603de43b2fe99).
- Owner then requested researching public ISO8583 reference structures. Research completed: [iso8583-reference-research.md](iso8583-reference-research.md), MD5 7e27138d8d3016f33e55dc2b90b12d07; source snapshots/hashes in research-sources/manifest.json. Candidate SIM-ISO87A-v1 uses pinned jPOS ISO87 ASCII reference. Profile recommendation is not owner-approved or construction-ready; no new architecture or code changes.
- Independent acquiring-domain review to be commissioned for this exact draft/research bundle. Profile request/response vectors, DE48, identity/code tables and transport contract remain pending specification/review. Original Q1 is narrowed by research, not falsely closed. Q2/Q3 remain pending.

Independent review completed: run 01abec77-d770-4a41-b3c4-3accc7251f4a, agent ai-sdlc-acquiring-domain, observed configured default openai/gpt-6.1-sol without override. Coordinator read the entire review and recomputed artifact hashes. Six findings: two Medium (F1/F6), four High (F2–F5). Research baseline selection is adequate for further specification, not construction approval.

F1 citation correction applied: original reviewed requirements preserved at revisions/requirements-r1-md5-2ac753ee.md; current requirements.md MD5 5130314810dbc3a27ab6b9bb7e88d0eb points O to the immutable owner-scope revision. No business rules changed. Correction is coordinator-recorded, not reviewer-closed; renewed review of the new revision remains pending.

Next specification handoff: Requirements must develop the complete candidate/profile matrices, canonical DE48, durable aliases/idempotency, actor/dispatch-aware outcomes, channel/transport contract and fixed byte vectors (F2–F4/F6), plus concrete Q2/Q3 failure/access/retention proposals (F5). Then obtain independent re-review and present bounded adoption/architecture decisions to Fernando. Do not ask the owner to design field definitions. Research-selection task is complete; normative profile specification is not.

Independent QA and Security agents are not installed under the current roster; their required downstream gates remain unavailable until an appropriate independent reviewer is supplied. Local-source review succeeded; incomplete configured MCP availability does not establish MCP-backed verification. No code, executed interoperability tests, publication or deployment.

## Specification completion authorized — 2026-11-18

Fernando: "ok continua y si tienes preguntas hazlas claras y de una en una". Authorizes completing the candidate specification and review preparation, not construction/architecture approval. Questions must be clear and presented one at a time.

Requirements continuation accepted/queued in existing child session agent:ai-sdlc-requirements:subagent:91938153-4fb4-4ebe-9fc3-db9718bc8530, run 98780fdd-4126-4115-974e-a25f33f8cbdb; no model override. This receipt does not establish execution completion or observed follow-up model. Inputs pinned: requirements 5130314810dbc3a27ab6b9bb7e88d0eb, scenarios 6b26f7204ee969bb3c74267380277c31, research 7e27138d8d3016f33e55dc2b90b12d07, review c49694b00907371de7f8fd16e3885f9f. Required outputs: revised requirements/scenarios plus complete ISO profile, channel and observation contracts, golden data vectors and a bounded decision proposal addressing F1–F6. Existing revisions preserved; no code/deployment or fabricated approval. Independent re-review follows exact returned revisions.

## Completed candidate and independent re-review — 2026-11-18

Requirements continuation 98780fdd-4126-4115-974e-a25f33f8cbdb completed; observed default openai/gpt-6.1-sol from sanitized history, no override. Candidate includes ISO profile, channel/observation contracts, decision proposal and exact data fixtures. All new choices remain PROPOSED. Coordinator read candidate contracts/proposal/inventory, recomputed all ten returned MD5 identities, and verified byte lengths/SHA256 for all 47 packed vectors. This is documentation integrity, not codec execution, QA or interoperability proof. State inventory contains 22 cases; combined claimed inventory is 97 including 28 original scenarios.

Prior independent review preserved at revisions/domain-review-r1-md5-c49694b00907371de7f8fd16e3885f9f.md before replacement. Independent re-review accepted/queued in existing acquiring-domain child, run bd144cda-1ac4-4d2a-be11-07ddfd415c68; acceptance is not completion. Review brief pins all candidate artifacts and requests per-finding F1–F6 disposition, exact correction list, source/provenance audit and decision-readiness verdict. No human question yet; D1 only will be presented after review if decision-ready. Construction remains unauthorized; downstream QA/Security availability unchanged.

Re-review bd144cda-1ac4-4d2a-be11-07ddfd415c68 completed. Coordinator read domain-review.md (reported MD5 a65b92f91c36a51484ee91f7f009f44a). Verdict: CONDITIONAL for continued specification; BLOCKED for exact D1 adoption and construction. Seven correctable findings: four HIGH, three MEDIUM (N1–N7); F1 CLOSED, F2–F5 PARTIAL, F6 NOT CLOSED. Byte identity checks did not establish valid field boundaries. Requirements correction continuation a75edbfd-740b-4f8a-be41-c265c836293f accepted/queued in existing child without model override; completion and exact revisions require verification. Scope includes fixed-width aliases, response capacity, unsent/restart states, trusted evidence, replay precedence, observation identity/freshness and isolated acceptance fixtures. No owner decision requested before correction and renewed independent review.

## N1–N7 correction receipt and renewed review — 2026-11-18

Requirements correction run a75edbfd-740b-4f8a-be41-c265c836293f returned. Coordinator recomputed all fourteen returned artifact MD5 identities; they match the correction receipt. Exact current revisions are pinned in independent re-review brief run 3609afea-0cea-4a70-a444-b69cd19bfd10, accepted/queued in the existing acquiring-domain child without model override. Acceptance is not review completion. Prior review preserved at revisions/domain-review-md5-a65b92f91c36a51484ee91f7f009f44a.md before canonical replacement.

Candidate requirements MD5 f10cf394de8a78b9a5e87b633f62f59b; profile 30137e38553cce26b16e6d26bfa8d02c; decision proposal c7cae7b0791e76706966f65812389fdc. Correction inventory reports 161 acceptance cases and 41 evidence fixtures; documentation-validation.json records 85 packet walks and 31 positive decoded comparisons. These are specification fixtures and documentary arithmetic, not executed codec/QA/cryptographic verification. Reviewer must independently assess N1–N7 and renewed D1 readiness; previous blocked verdict is not self-closed. No owner question yet, no architecture approval or construction authority. Configured MCP remains incomplete; no MCP-backed verification claimed.

## Corrected-candidate independent verdict — 2026-11-18

Review run 3609afea-0cea-4a70-a444-b69cd19bfd10 completed. Coordinator read the complete domain-review.md and independently verified MD5 355fb90c0d48266c53e41b23ef7526af. Reviewer reports configured default GPT-6 with no override; exact provider identifier unavailable. CONDITIONAL: D1 alone is decision-ready for private profile/channel selection. Construction remains BLOCKED. Findings: 0 HIGH, 3 MEDIUM; N1–N3/N5–N6 CLOSED, N4/N7 PARTIAL. Independent documentary inspection matched 85 packet lengths/hashes and 31 complete positive decodings; this is not codec/QA/cryptographic execution.

D1 presented to owner as selection of SIM-ISO87A-v1 authorization-only private baseline and HTTPS boundary, explicitly including the reviewed profile/channel supplements (P2a/P3a/P5/P6 and C3); D2/D3-dependent policies remain proposed. Cross-cutting instrumentation still applies to every implemented/future capability; future operation profiles require their own specification. No scheme-conformance or construction approval is implied. Owner answer PENDING.

C1 evidence carriage, C2 response precedence and C3 stale capacity/adoption references remain mandatory team corrections before affected interface construction; no owner field-design decision is needed. D2/D3 will be presented separately, one at a time. Architecture approval and independent Security/QA gates remain pending/unavailable as previously recorded. Incomplete MCP remains a verification limit, not a successful MCP-backed check.

## D1 owner selection — 2026-11-18

Fernando answered "ok de acuerdo, continua" to the single D1 selection question. D1 APPROVED for private SIM-ISO87A-v1 and HTTPS ACL/simulator boundary only; exact reviewed bundle and exclusions recorded in owner-decisions.md. Earlier D1 PENDING labels are historical. D2/D3 and architecture/construction approval remain pending. C1–C3 corrections continue within authorized specification scope; questions remain one at a time.

C1–C3 specification correction accepted/queued in existing Requirements child, run 373362d8-99e5-4269-86f2-6c98e5560adb, no model override. Completion/model not yet observed. Owner decision record MD5 28dd67ac104bf38d7ccc018226b37f9c; review input MD5 355fb90c0d48266c53e41b23ef7526af. Independent re-review required before claiming correction closure or D2 decision-readiness. No code/construction commissioned.

## D2 decision-readiness independent verdict — 2026-11-18

Independent review run 4e46f3d7-715a-4db8-855c-37c0149e5810 completed. Coordinator read the full domain-review.md and independently recomputed MD5 946dd018b12383b747478c70b57d0800. Verdict: PASS for D2 policy decision-readiness only; C1–C3 CLOSED, zero new HIGH/MEDIUM/LOW findings. Reviewer reports default GPT-6 without override; exact provider/runtime ID unavailable. Review pins the sixteen candidate revisions. Documentary checks are not runtime codec, HTTP, cryptographic or QA execution.

D2 alone may now be presented to Fernando: block new sends without durable business claim and trace admission; after any possible send do not automatically resend or reverse, preserving known durable outcome or explicit uncertainty and trace gaps. Practical consequence: trace outage can block new work or strand an unsent reservation; retry/restart does not silently resume it. D2 owner answer PENDING. D1 remains selection-only approval. D3, architecture, Security/QA gates and construction remain unapproved/unexecuted. Configured MCP verification remains unavailable; no MCP-backed success claimed.

## C1–C3 corrected candidate submitted for independent re-review

Coordinator recomputed all sixteen supplied artifact MD5 hashes and matched the Requirements receipt. Current requirements: b8f0e2970fe38ce0f2e84913d789932f; decision proposal: 8f1d9679c9e0bf9948bbdb24c64a3d7f. Prior domain review preserved at revisions/domain-review-md5-355fb90c0d48266c53e41b23ef7526af.md. Independent acquiring-domain followup run 4e46f3d7-715a-4db8-855c-37c0149e5810 accepted/queued, not yet a verdict. Requested C1–C3 closure and D2 readiness against exact pinned inputs. D1 remains approved only as recorded in owner-decisions.md. D2/D3 and construction remain unapproved. Document fixtures are not executed codec, HTTP, cryptographic or QA tests.

## ADOT/asynchronous observability investigation

Owner qualified D2 assent with asynchronous trace export and requested best-practice/Context7 research. Reviewed AWS ADOT, Lambda Telemetry API, CloudWatch subscriptions, transactional-outbox guidance and Context7 AWS Lambda documentation. Findings and proposed amendment: adot-observability-research.md. Separate technical telemetry, authoritative journal and console read model; recommend asynchronous projection rather than payment-path writes to a separate trace store. No current contract is silently superseded; explicit amendment adoption and independent revised-contract review required. No runtime/config/code changes.

## Owner-approved asynchronous observability amendment

Fernando approved asynchronous telemetry and history projection (reply "ok" to explicit separation question). Exact authority and supersession recorded in owner-decisions.md, D2 amendment. Mandatory business journal/critical atomic notification admission remains; separate history/telemetry availability is not a payment prerequisite. Requirements must align O3/O3a, channel response semantics, failure fixtures and D3 proposal; prior domain PASS only covered earlier D2 decision-readiness, not this new delta. No construction authorized.

## Async amendment independent review handoff — 2026-11-18

Coordinator verified all 17 returned artifact MD5s against the correction receipt. Owner decisions MD5 20a796b210eb6c74044ab46fad46c15d and ADOT research MD5 848a73f6194e4d912c9eabc26f429bfa remain unchanged. Candidate observation-contract.md MD5 27eb3ed660f05cb7aa84dc131dafbf36 and decision-proposal.md MD5 ecfcaa2855352d346c541585eeee5be7 distinguish required atomic business/critical outbox persistence from asynchronous history and telemetry. D3 retention, replay, finite capacity and access mechanics remain PROPOSED. Candidate inventory claims 222 cases, not executed tests.

Prior review preserved at revisions/domain-review-md5-946dd018b12383b747478c70b57d0800.md; its PASS does not cover this amendment. New independent review requested via existing domain specialist, run d3ea1a1c-38bb-428a-a707-df44a985015d; accepted/queued, not yet completed. No model override; actual execution model pending. Review must challenge retention beyond Streams, payment independence, task coverage gaps, capacity, replay/expiry and evidence precedence, and explicitly assess D3-only decision readiness. No construction, runtime/codec/crypto/QA success or approvals inferred.

## Async amendment review completed — 2026-11-18

Independent review d3ea1a1c-38bb-428a-a707-df44a985015d completed. Coordinator read the full report and recomputed domain-review.md MD5 81a59a5a59d9e90cfb2c3746738064e8. PASS for amended D2 domain specification and D3 decision-readiness only; zero HIGH/MEDIUM/LOW findings. Reviewer reports default GPT-6, no override; exact provider model ID unavailable. Reviewed identities and 222 documentary cases are recorded in the report, not executed tests. D3 alone may now be presented with explicit seven-day restricted history, durable critical events versus potentially missing intermediate observations, recovery and finite-capacity trade-offs. Owner answer remains PENDING. D1 and async D2 are not reopened. Construction remains BLOCKED pending owner architecture disposition and required preflight/Security/QA gates; Java25/ADOT compatibility remains unproven. No runtime, cryptographic, QA or deployment success claimed.

## WebSocket and single event-store delta — 2026-11-19

Owner requested activity diagram and continued work after rejecting console users/access restrictions and polling. Direction recorded in owner-decisions.md; activity-flow.md and activity-flow.mmd show proposed business/notification/reconnect/ADOT branches. Actual AWS architecture uses DynamoDB business persistence per WI-ACQ-AUTH-001/aws-architecture-update.md, superseding local SQL for AWS. Previous PASS and D3 decision-readiness do not cover this delta. Requirements alignment and renewed independent review required. No construction, resource deployment or changes to existing service authentication authorized.

## Latest owner steering and Drive publication — 2026-11-19

Fernando asked for the diagram in Drive and suggested retaining JWT with a single user. Supersedes earlier no-login direction; recorded in owner-decisions.md. Requirements followup 7604bff8-1530-4b1a-9526-3ba8a0d12157 accepted/queued in existing child, not completion. Earlier alignment run ab6bd6fb-73ab-4ee3-b24a-e4328dada8e5 may also complete; reconcile both outputs, never accept stale no-login specs. No runtime model override.

Activity diagram regenerated using cached Mermaid CLI, PNG visually inspected. Published under existing Acquiring Simulator/architecture-diagrams folder with explicit owner authority, without changing permissions or other source documents:
- PNG: https://drive.google.com/file/d/1nb1gbNagGAvSsfCTi7_JEsmceb0GbsUP/view
- Editable Mermaid: https://drive.google.com/file/d/17e8yzxLZjfEAPX851i9bOrad2kOHm05o/view
- Explanatory Markdown: https://drive.google.com/file/d/1Tkk0ni2n2OB65WmWolRV7fJxoBVZc-4Z/view

ISO authentication clarification researched using official jPOS TLS socket API plus Context7 /websites/jpos: payload format does not imply unauthenticated transport; source-IP filtering is not cryptographic peer authentication. D1 HTTPS is unchanged. Browser JWT and service identity are distinct. IP-only or raw TCP transport NOT selected by owner's question.

## Single-demo JWT and WebSocket candidate re-review
Requirements candidate verified by MD5: requirements `7c1b19505694b1cfd38658b7f4e6f161`, observation contract `1468ee3ca259184ed979dfad7bc74a1d`. Owner record `0dbe1d3a1ee8e34ee416fc8ea4f15918` retains one demo JWT user and separate service IAM. Diagram corrected for actual transaction subscription, ACK-before-catch-up, atomic predispatch states and no financial replay: activity-flow.md `2abcab7e3d7d11af43cf9127db21f184`, source `1629e92740f7ec9a49f7e47e406cba86`. Published Drive rendering predates these diagram corrections; synchronization pending current connector availability. New independent review accepted/queued as run `5bc0c5c7-3c26-418f-8c80-c4123feb32af`; acceptance is not completion. Previous PASS does not cover this candidate. Construction remains unapproved.


## Latest independent review and corrective handoff

Review `domain-review.md` MD5 `1bfa8629a5321434515b51a4050f90d5` is CONDITIONAL: 0 HIGH, 2 MEDIUM, 1 LOW. M1 subscription retry/conflict identity, M2 optional WebSocket and observed/snapshot head distinction, and L1 JWT NumericDate fixtures require specification corrections. Full receipt comparison was partial; no runtime/QA verification claimed.

Requirements follow-up `3ca1747f-e710-4f1f-82ef-836a987e9dea` accepted/queued to correct contracts, vectors and both activity-diagram sources, preserving outgoing versions. Acceptance is not completion. Renew independent review after correction. Drive connector unavailable in this turn; published diagram is older and must not be represented as synchronized. Exposure/retention remain separate owner decisions. No construction authorization.

## M1/M2/L1 correction verification and re-review handoff

- Coordinator verified all 20 MD5/SHA-256 manifest entries; manifest MD5 `71f3d3247a7847cbc673c0657a7c4059`. Inventory: 255 specification cases, not executed tests.
- Prior review `1bfa8629a5321434515b51a4050f90d5` preserved under revisions. Independent follow-up accepted/queued: `a3454f3d-6bc0-429d-bc5e-e0e27ca04ad1`; acceptance is not completion. Configured default requested without model override.
- Corrections address immutable subscription retry/conflict, optional live transport, ACK head versus fixed snapshot, and JWT NumericDate. No closure claimed before review.
- Drive connector unavailable in current tool catalog; published PNG remains older than corrected Markdown/Mermaid. In-place synchronization pending connector availability. Exposure and retention remain unapproved; construction blocked.


## Final M1/M2/L1 independent correction review — 2026-11-19

- Review run a3454f3d-6bc0-429d-bc5e-e0e27ca04ad1 completed; coordinator read full domain-review.md and verified MD5 295c1a3601dd099f172a40d27b73e4b3. PASS for specification corrections only; M1/M2/L1 CLOSED, zero new findings. Reviewer verified all 20 manifest entries. Default GPT-6 reported, no model override; exact provider ID not exposed.
- Exposure and retention are separately decision-ready, not approved. Next owner question concerns direct client browser access versus presenter screen-sharing; no inferred local-only restriction. Construction and runtime/security/QA gates remain pending.
- Current Drive connector is unavailable in the tool catalog. Published Drive PNG/source copies remain older than corrected local activity-flow.md/mmd; no synchronization or current rendered-delivery claim. Requires restored Drive capability to update existing file IDs in place.

## Owner exposure/retention decisions — 2026-11-19

Fernando will operate and screen-share the demo and separately approved seven days of functional history. Exact responses recorded in owner-decisions.md. No automatic payment replay implied. Reviewed contracts unchanged; approval overlay supersedes their historical exposure/retention PENDING labels only. Full architecture/construction decision and technical preflight remain pending; Security and QA specialist availability unchanged. Drive synchronization is being recovered now that connector is available.

## Drive diagram synchronized — 2026-11-19

Connector recovered. Latest reviewed Mermaid rendered successfully via cached Mermaid CLI and PNG visually inspected. Replaced PNG, Mermaid and Markdown in place preserving existing Drive IDs and permissions. All three API updates succeeded. PNG ID 1nb1gbNagGAvSsfCTi7_JEsmceb0GbsUP; Mermaid 17e8yzxLZjfEAPX851i9bOrad2kOHm05o; Markdown 1Tkk0ni2n2OB65WmWolRV7fJxoBVZc-4Z. Previous Drive synchronization blocker resolved. This is documentation publication, not a runtime integration test.
- activity-flow.md: MD5 2f8f752b6cad11c77f6f2136cc6c0914
- activity-flow.mmd: MD5 8e83226e66f6ce401fe6526ed60b14b0
- activity-flow.png: MD5 8637726a45cafe55a2a62f9725b935e1

## Documentation consolidation — 2026-11-19 (in progress)
Fernando requested cleanup of unused app Drive documents, sequence rather than activity diagrams, and functional-guide coverage separating Channel Test Interface from cross-cutting observability. Requirements followup 80c3efa3-1698-44f7-a393-6e76a7780fc2 admitted; completion and independent review pending. Drive guide 0.5.2 has additional section 13 absent from local 0.5.1; both baselines must be preserved and merged without losing the newer scenarios. Existing C1/C2 diagrams remain useful; activity exports will be superseded in place only after verification. No construction or new Git publication authorized by this documentation request.


## Functional guide and sequence documentation handoff

- Merged local guide and Drive 0.5.2 into candidate 0.5.3, retaining section 13 and adding section 14 Channel Test Interface and section 15 Cross-cutting Observability.
- Produced sequence-flow and sequence-recovery Markdown/Mermaid/PNG. Mermaid CLI rendering succeeded after replacing note semicolons with commas; this is documentation rendering, not runtime QA.
- Independent domain review requested: run f403e07c-531f-4652-8d34-1fe6eed8f475; format correction steered to that run. Acceptance is not review completion.
- Independent review completed: PASS — documentation only, 0 findings. Review MD5 0ce3d9fc2f2eea5dcc2dc1cba9bcdb56. Coordinator read the report and verified exact guide and four sequence-source hashes against its post-format manifest. This is not construction approval or runtime QA.
- Current-turn tool inventory exposes no Google Drive connector. Drive publication, in-place replacement and cleanup cannot be claimed complete. Preserve useful context/component diagrams; do not permanently delete files based only on age. Existing Drive activity exports are not yet replaced by sequence diagrams; no files were deleted.

## Drive synchronization completed — 2026-11-19
Explicit owner continuation: “hazlo”. Published exact independently reviewed guide (MD5 db00cd4ef3c57166b18f188b3db9fa8c) in place to Drive ID 1ARKf83xluDyWSyYDxZY2b0ZNTSkTG-Uu. Replaced and renamed activity exports in place as console-payment-sequence (.md/.mmd/.png), retaining IDs 1Tkk0ni2n2OB65WmWolRV7fJxoBVZc-4Z / 17e8yzxLZjfEAPX851i9bOrad2kOHm05o / 1nb1gbNagGAvSsfCTi7_JEsmceb0GbsUP. Added recovery sequence exports IDs 1w_biAuaVwJwRAva2-vjQu_br1VPqQbp2 / 1PaYQXZq6HO2FSMSTvclyJKvPov486N2K / 1b2zB6jw9JpRg7QUt1I2BViQzVaRUib-a. All provider writes succeeded; post-write inventory has no activity exports. C1/C2 retained as useful. No permanent deletions, Git operations, code or deployment. Local historical activity sources remain for provenance. Independent domain PASS remains documentation-only. Publication does not imply construction approval.

## PNG-first documentation pass — 2026-11-19

Documentation-only presentation change on `feat/wi-acq-auth-001`; no commit, push, PR, code or deployment. All current product Markdown now presents diagrams as a rendered PNG with the exact editable Mermaid source linked beside it. Historical evidence is exempt and unchanged: the 2026-11-18 import and the pre-existing tracked revision baselines still contain their original Mermaid blocks byte-for-byte.

### Behavior preserved
- Guide 0.5.3: after normalizing Mermaid blocks/image lines/blank-line runs, the body is text-identical to the pre-change 0.5.3 draft. Drive 0.5.2 sections 1–13 are unchanged; sections 14–15 remain only the previously reviewed additions.
- The seven guide diagrams were extracted from the Drive 0.5.2 baseline into `docs/architecture/*.mmd`; each file equals its baseline Mermaid block plus one trailing newline. No diagram statement was edited.
- `sequence-flow`, `sequence-recovery` and `activity-flow` Markdown keep their existing prose; only the embedded Mermaid block was replaced by the PNG and source link. Each `.mmd` is byte-equal to the previously reviewed block.
- Local links and heading anchors resolve; no current product Markdown embeds a Mermaid block.

### Render provenance (cached Mermaid CLI 11.12.0, headless Chrome)
Each PNG was re-rendered from the exact `.mmd` and compared pixel-by-pixel with white background: all match exactly except `activity-flow.png`, which differs only in 223 anti-aliasing pixels (max channel delta 7/255) inside one label region; its source and tracked PNG hash are unchanged, so the tracked artifact was not rewritten.
- `docs/architecture/*.png`: `mmdc -b white -w 2000`.
- `docs/architecture/operation-a-aws.png`: `mmdc -b white -w 1800`.
- `sequence-flow.png`, `sequence-recovery.png`: `mmdc -b white -w 2200`.
- `activity-flow.png`: existing tracked render from earlier in the same day, verified against the unchanged `.mmd`.

### Current local hashes (MD5)
- Guide: b2587503824327ed39e1bbba16375a17 (the independently reviewed/published hash db00cd4ef3c57166b18f188b3db9fa8c predates this presentation-only pass, so Drive must be re-synced).
- `sequence-flow.md` a216ce8b59b204b2fa852d404d63ebae; `sequence-flow.mmd` 689d6bad9a50af41246b4550c4e92d03; `sequence-flow.png` 6f08144e4f07d8e193ba911f2de54dba.
- `sequence-recovery.md` cb3ece21a27cd59d05ca1f8792bf783f; `sequence-recovery.mmd` a64edb1e04653de3fce707a865b17fd3; `sequence-recovery.png` 6bab20c5a17e4e610c2babfcdec990c5.
- `activity-flow.md` 3a9f505393f731ae014cdf56e2d7e97d; `activity-flow.mmd` 8e83226e66f6ce401fe6526ed60b14b0; `activity-flow.png` 8637726a45cafe55a2a62f9725b935e1.
- `docs/aws-operation-a.md` 84bea5c34e00aabb5f89c08d3af28209.

Observed runtime model for this pass: provider `nan`, model `deepseek-v4-flash` (DeepSeek V4 Flash); no override was applied. Rendering verification is documentation QA only, not architecture, Security, independent review, construction or runtime evidence.


### PNG-first Drive synchronization — 2026-11-19

- OpenCode completed presentation-only PNG-first verification across 31 current product Markdown files; historical evidence excluded and preserved. No commit or push.
- Updated existing Drive guide and both sequence Markdown IDs in place. Uploaded seven guide PNG/source pairs to the existing architecture-diagrams folder. Asset IDs: `png-first-drive-assets.json`.
- Drive-specific Markdown uses hosted image URLs and editable source links; local Markdown retains repository-relative links. Readback confirmed seven guide images and one image in each sequence, with no embedded Mermaid blocks. Other current root app Markdown files contain no Mermaid blocks.
- Drive viewer image rendering not independently observed; content/links verified by connector readback. No domain semantics, approval or deployment changed.

## Human architecture approval and preflight — 2026-11-19

Fernando explicitly approved the architecture with agreed decisions and commissioned technical checks before implementation. Exact authority, bounded supersession and pinned candidate SHA-256 identities: owner-decisions.md, "Architecture approval and technical-preflight commission". Current phase: Architecture Approved / technical preflight partial — see `technical-preflight.md`. No implementation, deployment or new publication authorized by this instruction. Security remains unavailable (not a callable agent); QA is installed and available for its downstream independent-verification gate, which does not replace implementation-owned preflight.

## Expanded local implementation authority and P1a delivery — 2026-11-20

Owner authority preserved and expanded, not replaced: the 2026-11-19 architecture approval (Operation A, private SIM-ISO87A-v1 over HTTPS, single demo JWT principal + separate service IAM, presenter screen-share, one queryable DynamoDB event source with atomic business/critical-event persistence, asynchronous Streams/WebSocket notification + HTTP catch-up, asynchronous ADOT, seven-day functional history) still governs. Fernando subsequently authorized continuing the pending implementation locally ("ok continua con lo pendiente"), recorded in `implementation-progress.md` and relayed by the coordinator. Authorization covers local application code, Terraform/IaC and CI definitions on `feat/wi-acq-auth-001`; it does not cover push, PR, merge, deploy, cloud apply, new credentials or destructive changes.

Current phase: architecture approved; Phase 0 preflight recovery complete; **P1 split and P1a delivered locally**. P1a implements the smallest coherent G5 subset from O1a/O2a — closed canonical payload, strict ingress, bounds/enums, sanitizer, RFC 8785 canonical hash, source wrapper, dedupe identity and versioned `A-1` manifest — with 42 new unit tests. The full local suite is 106/106. P1b (atomic EVENT store, `HEAD`, dedupe partition, slot accounting, OBS-20/21/22 core) remains open, as do G6/G7, Streams, WebSocket, query API, expiry, console, ADOT, Terraform and CI.

Correction carried into this status: the DE42 historical root cause is **unproven**. Only the corrected probes are reproducible; the pre-correction probe source was not preserved. No approved document or fixture was changed. This entry records local implementation evidence only, not independent QA, Security review or human acceptance.

## Owner continuation — 2026-11-23

Real authentication, server history, independent reviews and deployment commissioned by Fernando. See [bounded commission](handoffs/continuation-20261123/commission.md) and exact input pins. Work is not verified or deployed; review conditions remain gates.

## Normative adoption and N-02 disposition — 2026-11-23

Owner decision recorded in [owner-decisions.md](owner-decisions.md) under "N-02 closure (Option B) and normative adoption — 2026-11-23": exact instruction "adelante con lo recomendado".

- **N-02 (edge rate-bound bypass):** closed by the CloudFront origin-verification header under four binding conditions; REST+WAF and documented risk acceptance were not selected. Status remains OPEN until the contract delta is authored and independently re-reviewed; the Security verdict therefore stays FAIL in the interim.
- **Contracts:** `design/contract-deltas/continuation-rev3-20261123/` (manifest `2000bb00…ab467`) as amended by `continuation-rev3-dr301-20261123/` (`1d3f1406…d5f5`) is the normative revision. Domain evidence: `154b6346…c2e4` CONDITIONAL plus DR3-01 re-review `df5ad1a5…c5d0` PASS with DR3-01 CLOSED.
- **Not closed by adoption:** Security FAIL (`f5696ed9…ba50`), SR2-01/02, NV-05/AD-01, D-5 issuer evidence, public-surface evidence, deployment inputs.
- **Status:** Architecture Approved; construction of H1/K1 permitted once the N-02 contract delta passes independent re-review. Independent QA, Security implementation review, acceptance and deployment remain pending.

## N-02 re-review — 2026-11-23

Independent bounded re-review: `reviews/n02-origin-verification-20261123/security-review.md`, SHA-256 `e5937fb890a5723a1e1af853961659d6e8929bf9a69cc932a8aa6422d08eb2e1`, reviewer ai-sdlc-security, observed model `nan/glm5.3-flash`, no override. Verdict: bounded PASS for design/authoring scope; **N-02 PARTIAL**, standing implementation-level Security verdict remains FAIL.

- Contract delta `design/contract-deltas/n02-origin-verification-20261123/contract-delta.md` (`15959975…f3ad`) implements all four owner conditions; no defect found.
- Infrastructure verified 10/10 against `CHANGED-FILES.sha256`, policy suite 18/18 with 15/15 negative mutations firing.
- Custody widening ruled within the original condition's intent: the runtime copies are inherent to the selected option, no runtime principal can read them, and the deploy role's read surface is governed by the standing C-01 gate. No new owner decision required.
- Closure path: the boundary gate exists in no application bytes yet, so N-02 closes only after the H1/K1 construction, its implementation re-review, and the deployment-gate retests. Construction is not blocked by this review.
- Follow-through: denial metric filter and alarm (FT-2) await the frozen log shape and an owner-set threshold.

## N-02 implementation review — 2026-11-23

Independent bounded implementation review: `reviews/n02-implementation-20261123/security-review.md`, reviewer ai-sdlc-security, observed model `nan/glm5.3-flash`, no override. Verdict: PASS (bounded); **N-02 CLOSED at authoring**, per the closure path the prior review projected.

- Slice manifest verified 25/25 against the current tree, so the green-run bytes are the reviewed bytes.
- The gate implements the delta faithfully: exhaustive closed classification, constant-time comparison with length fast-fail, fail-closed initialization, mandatory first application step before dispatch, 404/405, body read, credential seam, rate limit and service; exact 403 body with no echo; explicitly not authentication; one closed-field denial record per refusal.
- Red/green integrity accepted: no deleted, disabled or weakened cases; all six red failures were test-harness defects. The red-revision sources were not preserved, disclosed, and the reviewer judged the hash-pinned green assertions adequate with the residual stated.
- Advisories: A-1 the fixed OV identifiers were not carried into test names; **A-2 no composition instantiates the entry point yet, so nothing deployable refuses the direct path today — the console handler wiring via `fromEnvironment()` is mandatory and must be reviewed**; A-3 provisional `INVALID_CURSOR` mapping needs Requirements confirmation; A-4 cursor key sourcing remains open.
- Overall implementation-level Security gate: no longer FAIL-on-defect; recorded **BLOCKED (evidence-gated)** for the outstanding SR2-01/02 IAM negatives, C-02/C-03/C-04, scans and K1 real authentication.

## H1 independent QA — 2026-11-23

Independent QA report: `reviews/h1-qa-20261123/qa-report.md`, reviewer ai-sdlc-qa, observed model `nan/glm5.3-flash`, no override. **Verdict: FAIL for the scoped H1 criteria** — two executed checks violated contract criteria; all other executed checks passed.

- QA re-attested the binding read-only (branch `feat/wi-acq-auth-001`, HEAD `5bbd4eb5…`, dirty by design) and re-verified the normative packet 21/21, the N-02 delta, the DR3-01 fixtures and the implementer slice manifest 25/25.
- Executed by QA itself: 49 tests, 0 failures, 0 errors, 0 skipped across the focused console suites with DynamoDB Local; plus a QA-owned driver with 63 checks, 61 passed and 2 confirmed defects.
- Passed: the full origin-gate oracle set runnable without a deployed edge, and the complete read-protocol taxonomy including frozen snapshot, bounded pagination, cursor binding and TTL, tampered/expired/generation handling, 401 before and after the store read, 503 without payload, honest expired ranges and gaps. No payment side effect of any kind; no integrated end-to-end payment exists.
- Defects: **QA-DEF-1 (LOW)** the signed cursor payload does not bind `issuer`, which D-6 and the reviewed fixtures bind, mitigated by per-page re-authentication; **QA-DEF-2 (MEDIUM)** the 503 body emits `UNAVAILABLE` instead of the pinned `AUTHORIZATION_UNAVAILABLE`; **QA-DEF-3 (LOW)** the `terminalRef` context binding is absent although the fixture includes it.
- Observations to route, not silently accept: mandate fingerprint versus `registryMandateId`/`registryVersion`, the omitted epoch key, and `cursor` plus `after` where `after` is silently ignored.
- Not verifiable here: deployed-edge behaviour, platform 401 body, real JWKS/issuer verification (K1), server-side read-pass store, cursor-key sourcing, log and metric read-back.

## H1 composition slice — 2026-11-23

Implementation slice: the console entry point is now instantiated in a deployable composition (`adapter/aws/ConsoleHandler` + `ConsoleRuntime`), so the origin gate runs in the composed path rather than only in unit tests.

- Compile attempt 1 failed with an ambiguous constructor reference in `ConsoleHandler.java`; preserved. One-line fix, attempt 2 exit 0.
- Focused suites, each in its own invocation, all exit 0: ConsoleHandlerCompositionTest 5, ConsoleOriginVerificationGateTest 9, ConsoleHistoryRouteTest 20, ConsoleHistoryRouteDynamoIntegrationTest 4, ConsoleDevServerTest 9 — **47 tests, 0 failures, 0 errors, 0 skipped**. No case weakened, skipped or removed.
- Stated limit: the deployed composition answers `503 UNAVAILABLE` for the existing routes because it deliberately does not fabricate authorizer, registry or cursor-key collaborators. The gate is invoked; the business paths are not functional. This is the same defect class QA reported as QA-DEF-2.
- Evidence: `evidence/continuation-h1-composition-20261123/` (attempt-numbered exits and logs, RESULTS.md). Developer evidence only, not independent QA, Security review or acceptance.

## H1 QA re-verification — 2026-11-23

Independent re-check: `reviews/h1-qa-recheck-20261123/qa-report.md`, reviewer ai-sdlc-qa, observed model `nan/glm5.3-flash`, no override. **Verdict: PASS for the scoped criteria.**

- QA re-ran everything itself: compile exit 0, focused suite 53/53 green including the four DynamoDB Local integration tests, and its own driver 70/70 PASS. No new defects.
- QA-DEF-2 PASS: all four reachable 503 paths emit the byte-exact fixture body with `AUTHORIZATION_UNAVAILABLE`; the earlier assertion change is judged justified by the contract, and the new test compares the exact fixture bytes.
- QA-DEF-1 PASS: the cursor always writes and requires `issuer`; a cursor minted under another issuer is refused with 403 before any store read, while both per-page authentications remain and an expired token still returns 401 before disclosure.
- QA-DEF-3 PASS: `terminalRef` binds when target-specific and a different terminal is refused on continuation and post-read re-authentication.
- The red-to-green transition is accepted as a stale test fixture, not a relaxed assertion; the 410 assertion is unchanged and the original twenty test names remain.
- Confirmed that the three open items were left unimplemented pending the contract clarification now under independent domain review.
- No integrated end-to-end payment exists; N-02 remains open at the edge, CloudFront, K1 and platform layers.

## D-6 clarification application in H1 code — 2026-11-23

Two code changes from the C-1/C-2 domain dispositions, verified by the Coordinator in the source bytes:

- **C-1.3 ordering:** `reauthenticateAfterRead` now checks the context triple (`403 CONTEXT_DENIED`) before the mandate fingerprint (`410 RESYNC_REQUIRED`), matching the clause; the pre-read continuation was already context-first. Both authentication calls and the 401-before-disclosure rule are unchanged. Focused tests pin the order: a request that is both context-mismatched and generation-retired returns 403, and one only generation-retired still returns 410.
- **C-2 combinations:** a non-empty `cursor` combined with `after` or with `snapshot` is now refused as `400 INVALID_CURSOR` with a null action, after authentication and before mandate resolution or any store call, replacing the silent cursor-wins behaviour. Two tests assert zero registry, head and reader calls.

Runs: test-compile exit 0; focused console suites exit 0 with **79 tests, 0 failures, 0 errors, 0 skipped** (ConsoleHistoryRouteTest 29). Evidence: `evidence/continuation-h1-d6-c2-20261123/`. C-3's taxonomy and its two failure-order corrections remain unimplemented pending the corrected text now being authored. Developer evidence only; independent verification stays PENDING.

## D-6 clarification adoption — 2026-11-23

Bounded re-check of the correction: `reviews/d6-clarifications-rev2-20261123/domain-review.md`, reviewer ai-sdlc-acquiring-domain, observed model `nan/deepseek-v4-flash`, no override. **Verdict: PASS for adoption of C-1, C-2, C-3 and C-4.** All seven corrections applied exactly as asked; nothing remains CONDITIONAL or BLOCKED; no new owner decision required.

- Corrected packet `design/contract-deltas/d6-clarifications-rev2-20261123/d6-clarifications.md` `3aaac88a…43a3`; the reviewed rev-1 packet stayed byte-identical and unedited.
- C-3 now orders failures explicitly: request-form 400, then resolution 503/403, malformed-payload 400, retired-schema 410 after signature verification and before context 403, expired 410, context 403, representation 410, head 503/404, `after` beyond the frozen head 400 only after the head is read, then `R`/`F` 410, bounded `lastSequence` 400, 200.
- Reviewer confirmed the code already checks context 403 before fingerprint 410 at both re-authentication points, and declared the fixture's missing `audience` a documentary omission rather than a contract gap.
- One handoff label error was recorded as non-gating: the hash quoted for rev-2 `OUTPUTS.SHA256SUMS` was actually `PROGRESS.md`; the real manifest is `1cb58943…`.
- Authority: adopted under the owner's existing normative-adoption direction; the reviewer confirms no new business decision is required, and formal human acceptance of the corrected text remains part of the acceptance gate, not a construction blocker.

## C-3 taxonomy independent QA — 2026-11-23

Independent verification: `reviews/h1-qa-c3-20261123/qa-report.md`, reviewer ai-sdlc-qa, observed model `nan/glm5.3-flash`, no override. **Verdict: PASS for every commissioned C-3 criterion; no product defect found.**

- QA derived the expected order from C-3.1–C-3.4 and section 7 before reading any assertion, then executed its own 40-check driver and its own run of the eight focused suites: 90 tests, 0 failures, 0 errors, 0 skipped.
- Verified: the request-form 400 fires only for syntactic `after` errors and unknown or repeated parameters, before any registry or store call, and a 12-digit `after` is correctly not treated as a form refusal; resolution 503/403 follows; malformed-payload 400 precedes retired-schema 410; retired and expired 410 both precede context 403; context 403 precedes representation 410 in both the pre-read and post-read paths; head 503/404 precedes the `R`/`F` and bound-`lastSequence` steps; and `after` beyond the frozen head returns its exact 400 only after exactly one head call with zero reader calls.
- Bodies were byte-compared against the reviewed `historyErrors` fixtures at runtime, including `action: null` everywhere except 410.
- The 74→79→87→90 count chain is documented and arithmetically consistent; every test name cited in prior artifacts still exists. Limitation recorded: no exit artifact of the 87-run was preserved, so "both executions green" rests on the implementer's disclosure while the 90-run is QA-reproduced.
- Restated: no integrated end-to-end payment exists, and N-02 remains open at the edge. This PASS covers C-3 order and taxonomy for the verified working-tree bytes only.

## K1 authentication Security review — 2026-11-23

Implementation-level review: `reviews/k1-security-20261123/security-review.md`, reviewer ai-sdlc-security, observed model `nan/glm5.3-flash`, no override. **Verdict: PASS for the K1 slice, bounded**; the overall implementation-level Security gate stays BLOCKED (evidence-gated). No BLOCKER or IMPORTANT defect in the reviewed bytes.

- Evidence manifest recomputed 13/13 against the working tree, so the reviewed bytes are the green-run bytes.
- Verified in source: fail-closed order parse → algorithm → crit → kid → key → signature → claims; rotation through one refresh with no pinned key; `none`, HMAC and absent algorithm refused before any key is consulted; missing kid, signature mismatch, tampered payload, wrong issuer, wrong audience, dual-resource audience, expired, `nbf`, future `iat`, missing scope and wrong subject all refused; claims read only after signature verification, and the cursor's issuer binding compares against the verified principal's issuer.
- Token exists only within a single decode call; no cookie, storage or URL path; duplicate Authorization refused; a single token class with the socket audience and scope forbidden, so no combined payment-capable socket token exists; the dev stub is referenced only under `console/dev/`, reachable only from a loopback `main`, and not selectable by any production configuration.
- Decisions on the three open points: JWKS-unavailable surfacing as 401 accepted for this threat model, with a 503 refinement routed as non-gating follow-up; retained dev stub judged safe as wired, conditioned at deployment on pinning the handler entry point and on no `CONSOLE_DEV_*` variables in deployed configuration; RSA-only allowlist judged adequate, with non-RSA configuration failing construction.
- The untested `HttpJwksSource`/`JwksParser` gap does not block this slice because no deployed composition wires the decoder yet, but becomes mandatory at the deployment gate with named parser, HTTP and live-issuer retests (F-3). Advisories F-1, F-2 and the deployment-gate list D-G1–D-G7 are recorded in the report.

## K1 independent QA — 2026-11-24

Independent verification: `reviews/k1-qa-20261123/qa-report.md`, reviewer ai-sdlc-qa, observed model `nan/glm5.3-flash`, no override. **Verdict: PASS for slice K1 at this revision.**

- QA re-attested binding read-only (branch `feat/wi-acq-auth-001`, HEAD `5bbd4eb5…`), re-hashed the K1 evidence manifest to the commissioned digest and re-hashed all thirteen package files; every digest matched, so QA executed byte-identical code.
- QA's own executions: offline compile exit 0, focused suites exit 0 with 127 tests and no failures, errors or skips, plus its own driver of 94 checks, all passing, with locally generated RSA keys and its own JWS assembly.
- Verified: valid token accepted with the claim shape the authorizer consumes; unsupported, absent, `none` and genuine HMAC algorithms refused before any key consultation; missing kid, signature mismatch, tampered payload, wrong or missing issuer, single, dual-resource, mixed and duplicated audiences, expired, future `nbf`, future `iat`, missing scope and non-demo subject all refused with zero business-service calls; rotation accepted only after exactly one refresh and the retired key refused with no key pinned; no adversarial input produced claims without verification; the cursor's issuer provably originates post-verification; and the dev stub is isolated both at runtime and statically.
- QA independently narrowed the untested-key-source gap by exercising parser and loopback HTTP behaviour locally, including redirect refusal, status refusal, size bound and cache refresh. Remaining items are named deployment retests: DEP-K1-A deployed issuer fetch and TLS, DEP-K1-B the 503 refinement for unavailable keys, DEP-K1-C real Keycloak PKCE, rotation, revocation and disjoint-scope interop.
- Re-confirmed: no integrated end-to-end payment exists.
