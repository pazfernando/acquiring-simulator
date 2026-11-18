# Work Item: WI-ACQ-AUTH-001 — Payment Authorization Request (Operation A)

- Product: Acquiring Processor Simulator (MVP) — multi-acquirer payment processor
- Flow / capability: Operation A — Payment Authorization (authorization request only)
- Status: Architecture APPROVED; domain PASS retained; local implementation commits `17ea72e` and `ccc229a` exist on `feat/wi-acq-auth-001`. Independent QA, Security review and human acceptance remain pending. Earlier progress entries below are historical, not current gates.
- Human Product Lead: Fernando Paz
- Request and approved scope reference: Human direction 2026-11-16 (chat): "vamos a ir por la Operation A como scope Payment Authorization Request". Operations B and C are explicitly out of this work item.
- Sources and revisions:
  - `../../sources/acquiring_simulator_functional_guide.md` (relative to this work-item directory) — Functional Process Guide v0.5.1, dated 2026-11-16, status Working draft (md5 703fd59f66a3d097147eee79141e2524). Supplied by the Human Product Lead on 2026-11-16 as the project functional source; historical snapshot remains in the shared source archive.
  - `ai-sdlc/sources/README.md` (AI-SDLC pack v0.1)
  - `ai-sdlc/skills/payment-behavior-review/SKILL.md`
  - Original Google Drive reference `1ARKf83xluDyWSyYDxZY2b0ZNTSkTG-Uu`: not readable from this runtime (HTTP 401, sign-in required). The attached markdown snapshot is the working copy; version identity to be confirmed by the Human Product Lead if the Drive copy differs.

## Current AWS update — 2026-11-18

Owner requested the supplied AWS architecture be applied to the existing Operation A implementation. Current delta record: [aws-architecture-update.md](aws-architecture-update.md). For the AWS path it supersedes the earlier local SQL/no-HTTP design; the earlier approval evidence, approved specification bytes and historical receipts below remain preserved. Current functional source: guide v0.5.2 in `../../sources/imports/2026-11-18/`; the old v0.5.1 and old domain PASS refer only to the original pinned bundle. New delta independent domain review, QA/Security and human acceptance remain pending. B/C/3DS are not implemented or approved by this update. No AWS deployment/publication authorized.

## Goal and boundaries
- Desired outcome: The simulator accepts a Payment Authorization Request on behalf of a represented acquirer, applies documented validation and routing rules, obtains or simulates the issuer decision, records the outcome with provenance and references, and returns an observable result that distinguishes issuer decision, processor rejection and unconfirmed outcome.
- In scope: single authorization request/response for the baseline card-present scenario; processor validation and rejection; merchant/acquirer context enforcement; duplicate-request control; timeout and ambiguous outcome handling; recorded evidence and references.
- Exclusions:
  - Operation B (Authorization Reversal) and Operation C (Transaction Status Inquiry) — separate work items
  - Capture/completion, clearing, settlement, reconciliation, payouts, refunds, disputes, chargebacks, FX/DCC
  - CNP/EMV 3DS path — conditional, only if explicitly scoped later; the authorization side that validates supplied 3DS evidence is not in v1
  - Card-present EMV/CVM evidence handling beyond synthetic metadata (guide decision D-015 staged)
  - Real card credentials, real PANs, PIN keys, live scheme connectivity, certification claims
  - Mandatory message standard selection (ISO 8583 vs ISO 20022) — guide decisions D-008/D-009 remain Open

## Requirements — ai-sdlc-requirements
- Current documents: requirements.md r3 (MD5 b0c97974197a20dff1b72d38164d0e2b); simulator-profile.md (MD5 5483a4a378c0d1101391c11a628d2ae6); acceptance-scenarios.md current corrected identity 307922ad99e7468d53e687587a670750; reviewed prior identity a8a6d6522b34c77d727ce77ac870fa23 preserved in revisions/acceptance-scenarios-r3-md5-a8a6d6522b34c77d727ce77ac870fa23.md. A19/A25 correction verified locally; independent domain closure recorded below.
- Prior requirements r1/r2 preserved in revisions/.
- Coverage: inputs, validation, acquirer/merchant/terminal isolation, issuer outcomes, processor rejection, unknown, duplicates, concurrency, timeout and late evidence. 24 v1 scenarios plus 2 deferred; none executed.
- Provenance: 20 primary rules (14 sourced and 6 approved-policy cores); eight detail groups P01–P08 owner-approved under HD-04.
- Owner decisions: P01–P08 approved; see owner-decisions-2026-11-18.md and current independent domain gate.

## Human decision record

Recorded from the Human Product Lead's message of 2026-11-18 ("ok, solo para B01 la moneda será USD y ese es el defecto del simulador"), which accepts the coordinator's proposed defaults with one change: currency USD. Owner: Fernando Paz. These are scope/product decisions, not technical approvals.

- **HD-01 (answers B01)** — Baseline approved as proposed, with transaction currency **USD** as the simulator default:
  - amounts 1.00–9,999.99 USD; no other currency in v1
  - two named simulated routes: domestic route and international scheme route
  - simulated issuer decisions by test credential/BIN ranges: approve, decline, timeout
  - synthetic card-present evidence: entry mode, CVM indicator, cryptogram-present flag; no real PIN, no real cryptogram, no real PAN
  - Boundary: USD is a **simulator profile default**. It does not model Mexican pricing or currency, does not assert that Mexican domestic debit runs in USD, and imports no scheme, tariff or posting rule. Guide D-005 (Mexico reference) remains Proposed as business context only; D-008/D-009 remain Open and no external protocol is selected. Route names, amount range, BIN ranges and CP metadata are simulator profile choices and must be labeled as such, never as scheme rules.
- **HD-02 (answers B02)** — R15/R16 accepted as drafted, completed as follows: duplicate business key = represented acquirer reference + merchant reference + channel payment reference; retention window 24 h simulated; identical payload under the same key returns the prior known result without redispatch; same key with materially different payment data is rejected as a conflict; a retry carrying an invalid or foreign prior reference is rejected; timeout boundary configurable with a documented simulator default; late confirmation updates the record with the late evidence while preserving the earlier uncertainty history, and a retry evaluates the latest evidence rather than freezing a stale unknown. Window and timeout values are simulator defaults, adjustable by the Human Product Lead.
- **HD-03 (answers B03)** — Partial approval and network stand-in are **deferred from v1**. A10/A11 become non-v1; R07/R08 remain as conditional rules, explicitly not v1 acceptance criteria.

## Current independent domain gate — 2026-11-18

- Reviewer: ai-sdlc-acquiring-domain; run d7375aae-d3ac-42c2-9f9c-17b9868c2668.
- Evidence: domain-review.md MD5 6da7c386050eeedaecd63e67c9becf9d, read and hash verified by Coordinator.
- Verdict: PASS for domain specification bundle only; F02/F04 and B01/B02 CLOSED; 0 critical / 0 major / 0 minor; two nonblocking editorial/coverage observations.
- Inputs independently rehashed: requirements b0c97974197a20dff1b72d38164d0e2b; profile 5483a4a378c0d1101391c11a628d2ae6; corrected scenarios 307922ad99e7468d53e687587a670750; owner decisions 75b3efd4a20fff309086e600470d758f.
- Reviewed provenance table: R01–R14 sourced; R15–R20 approved simulator policy; eight P groups owner-approved. Coverage: 24 v1 plus 2 deferred scenarios; no tests executed.
- Domain blocking questions: none. Earlier pending P01–P08/CONDITIONAL labels below are historical and superseded by HD-04 and this gate, not current blockers.
- Nonblocking follow-up: align historical labels on next controlled functional revision; A19 distinct valid risk-conflict variant is not applicable under today's pass/reject profile. Preserve omitted/explicit pass equality and reject validation coverage.
- Architecture subsequently approved below; field-level P08 Security, independent QA and human acceptance are not approved. MCP tools unavailable by policy; local supplied-file evidence only, no MCP-backed success claimed.

## Historical independent domain review
- Reviewer: ai-sdlc-acquiring-domain, 2026-11-18; run 7cfe5d0b-f50f-4980-9487-de9e61ce0743.
- Reviewed requirements r3 MD5 b0c97974197a20dff1b72d38164d0e2b, profile MD5 5483a4a378c0d1101391c11a628d2ae6, scenarios MD5 a8a6d6522b34c77d727ce77ac870fa23.
- Evidence: domain-review.md MD5 10ea75183f12a41857798f4114ae329c. Previous reviews preserved in revisions/.
- Verdict: CONDITIONAL — 1 MAJOR F02 and 1 MINOR F04. B03/M01/N01/N02/F01/F03 CLOSED; B01/B02 PARTIAL.
- F02: owner disposition of P01–P07 and P08 exercise boundary missing. This review cannot supply human approval.
- F04: A19 must distinguish profile-valid linked-data conflict from mandatory-validation rejection for non-USD/invalid values. Analyst corrected A19/A25; corrected artifact and preserved original hashes verified locally. No claim of closure until renewed review.
- Reviewer observed WORK_ITEM MD5 9bbcc1c8180aea65993973c125e3907a. Historical commissioning digest 629d541e0a5b42811da5fedd6f0e6e6d was not independently verified; specification identities matched. This evolving record is not an immutable scope snapshot.
- PASS requires corrected scenarios, recorded owner dispositions, aligned artifacts and renewed independent review. No architecture, construction, QA or security approval.

## Human architecture decision
- Owner: Fernando Paz, 2026-11-18. APPROVED: architecture-decision.md, including PostgreSQL for test/production and SQLite only for local testing. Approval is not QA/Security or release clearance.

## Implementation
- Change reference: NOT STARTED — run 3756cfcc-f714-48ac-be51-c474832161e6 stopped during prerequisite checks; no production edits, implementation commits or tests.

## Independent verification
- Reviewer: none
- Tests actually executed: none

## Delivery and security
- Pipeline / infrastructure / scans: NOT STARTED
- Security trigger and independent verdict: NOT EVALUATED (data-protection and PAN-handling rules apply once interfaces exist)

## Human acceptance
- Decision: PENDING
- Owner, date, evidence and rationale: —

## Open blockers
1. ~~Functional source missing~~ — resolved 2026-11-16: guide v0.5.1 supplied and archived in `sources/`.
2. Reference-market, card type, channel and message-profile decisions (D-005, D-006, D-008, D-009) are Proposed/Open; requirements can be drafted against the proposal but construction cannot be approved against an unconfirmed profile.
3. No independent domain reviewer installed — RESOLVED 2026-11-16: ai-sdlc-acquiring-domain is registered and granted in the coordinator's allowAgents. Review commissioned.
4. Drive reference document still unreadable; confirm whether the attached markdown equals the Drive copy and which revision governs.

## Current commission — 2026-11-18

Human instruction: "ok vamos con esa especificacion", accepting the preceding proposal: one simulated POS purchase; inputs, controls, issuer approval/decline versus processor rejection versus unknown; duplicates, timeout and late evidence. Reversal, inquiry and e-commerce excluded. This authorizes requirements preparation and independent domain review, not construction or architecture approval. Requirements consolidation COMPLETE as draft r3; fresh independent review COMPLETE with CONDITIONAL verdict (see current gate above). Commissioned ai-sdlc-acquiring-domain run 7cfe5d0b-f50f-4980-9487-de9e61ce0743 on 2026-11-18. Exact artifacts: requirements.md md5 b0c97974197a20dff1b72d38164d0e2b; simulator-profile.md md5 5483a4a378c0d1101391c11a628d2ae6; acceptance-scenarios.md md5 a8a6d6522b34c77d727ce77ac870fa23. Previous review preserved at revisions/domain-review-r2-md5-c319b833.md (md5 c319b8335938289958c93f983b5dea3a). Scope snapshot at commissioning: md5 629d541e0a5b42811da5fedd6f0e6e6d, before this progress entry. P01–P08 remain proposed, not human-approved. Historical r2 gate above is not a review of r3. Local-file evidence only; configured MCP is incomplete and no MCP-backed verification is claimed. Preserve existing HD-01–HD-03; flag any conflicts rather than silently overriding them.

## Implementation request follow-up — 2026-11-18

Fernando requested implementation of requirements.md and subsequently instructed all delegations to use the target agent default without model overrides. This authorizes progressing implementation preparation; it does not supply absent repository facts or explicit disposition of P01–P08/architecture.

- Concrete review proposal: implementation-proposal.md (PROPOSED, not approved).
- Product execution record: ../../PROJECT.md (relative to this work-item directory); onboarding status is recorded in that document. Historical onboarding statements below describe earlier stages.
- Requirements/profile/corrected scenario/review hashes reverified; unchanged from current record.
- External project root inspected and empty; no selected checkout exists there.
- Coding NOT STARTED. Next: Fernando dispositions of P01–P08, architecture and repository facts; renewed independent review of aligned exact artifacts; then implementation handoff if gates pass. Independent QA/Security remain unavailable in installed roster.

## Owner disposition and stack update — 2026-11-18

Fernando explicitly approved P01–P08 (HD-04), selected Java/latest stable Spring Boot (HD-05), and selected main with remote setup deferred to him (HD-06). Evidence: owner-decisions-2026-11-18.md. Functional artifacts unchanged; earlier pending owner labels are superseded by this record, not self-declared reviewer closure. Updated implementation-proposal.md to Java/Spring Boot 4.1.2 with application.properties; Python proposal preserved. Fresh domain review to be commissioned; architecture beyond stack remains proposed. No coding or Git initialization performed.

## Architecture approved and local checkout prepared — 2026-11-18

Human approved Java 25 LTS, latest stable Spring Boot and remainder of presented design with correction: SQLite only for local testing; PostgreSQL for test/prod environments. Evidence and precise approved design: architecture-decision.md. Domain business semantics unchanged; prior domain PASS retained. Git identity now available in current configuration, reused per repository. Local repository initialized with empty base commit aa5f3ccb998bedf6f8f7f4910d972ace40ad7963 on main; clean task branch feat/wi-acq-auth-001. No remote, product code or publication created. Implementation delegation must verify native runtime compatibility using configured default, without override.

## Implementation delegation — 2026-11-18

Delegated to ai-sdlc-implementation run 3756cfcc-f714-48ac-be51-c474832161e6, child agent:ai-sdlc-implementation:subagent:ca4b3812-abc4-49af-b37c-c124f88d1202, explicit cwd /home/pazfernando/projects/acquiring-simulator and owned branch feat/wi-acq-auth-001. Spawn resolved configured default nan/deepseek-v4-flash, provider nan, without model override. Admission is not native-harness proof or implementation completion. Child must verify native harness compatibility before code and return blocker without substitution if unavailable. Approved architecture pinned MD5 50d92da8f36bd1e82912418cf9511161. Coordinator appended this receipt after commissioning; earlier WORK_ITEM hash in brief is the pre-receipt snapshot. No publication authorized.

## Implementation prerequisite result — 2026-11-18

- Child run 3756cfcc-f714-48ac-be51-c474832161e6 returned a prerequisite blocker, not an implementation success. Configured default nan/deepseek-v4-flash was honored with no model override; observed session runtime was openclaw, not the required official native Codex harness. No runtime/model substitution authorized or performed.
- Evidence: sanitized child history agent:ai-sdlc-implementation:subagent:ca4b3812-abc4-49af-b37c-c124f88d1202; final report and checkout verification. No MCP-backed execution claimed; inherited MCP tools unavailable by policy.
- Coordinator reconciliation: WORK_ITEM changed from commissioned MD5 1a0e86b68558c045884c07c9a62105ba to 64a6d1be2b72d9a0c3a613633f095a44 when the delegation receipt was appended. This was coordinator record drift, not a change to approved scope. Stale header and current approval labels corrected here. Pre-correction record preserved in revisions.
- Approved architecture remains MD5 50d92da8f36bd1e82912418cf9511161. Repository remains /home/pazfernando/projects/acquiring-simulator, feat/wi-acq-auth-001, base main, HEAD aa5f3ccb998bedf6f8f7f4910d972ace40ad7963; no remote/publication authorization.
- Resume condition: human/operator resolves required native harness versus configured default incompatibility. Do not change default model or execution policy silently. On re-dispatch pin an immutable work-item snapshot after reconciliation, and keep later receipts in the evolving record rather than mutating the commissioned snapshot.
- QA/Security and acceptance remain pending; no build or tests executed.

## OpenCode ACP route authorized — 2026-11-18
Fernando authorized OpenCode + official ACPX installation/configuration. Existing OpenCode 2.3.2 at /home/pazfernando/.opencode/bin/opencode reused. Direct ACP initialize/session/new/session/set_config_option(model=nan/deepseek-v4-flash)/session/prompt passed with ACP_OK; no project tools or edits. OpenClaw implementation alias configured for ACP/opencode with unchanged model default. Official ACPX saved enabled; live activation needs reload after current Codex turn. No inference through OpenClaw ACPX yet; no code/build/tests. No credential values copied or changed.

## OpenCode ACP preflight passed — 2026-11-18

Existing ACP child agent:ai-sdlc-coordinator:acp:d9d2bd5a-3e3e-4548-9744-84a54bdc8c1e reported OpenCode 2.3.2 through ACP, effective nan/deepseek-v4-flash, cwd /home/pazfernando/projects/acquiring-simulator, branch feat/wi-acq-auth-001, clean tree, no edits/commits. Human-authorized OpenCode alternative resolves the native-harness blocker for this item. Model selection is remembered state, not a persistent pin: reverify at implementation execution and stop on mismatch, never substitute. QA/Security remain pending. Immutable commissioning snapshot below is authoritative for the next handoff; later receipts do not alter it.

## Implementation resumed through authorized ACP route

Follow-up accepted/queued as run 9881f7d4-768a-4c24-b6ef-77d4d0624543 in existing ACP session agent:ai-sdlc-coordinator:acp:d9d2bd5a-3e3e-4548-9744-84a54bdc8c1e, explicitly assigned implementation specialist role. Immutable input snapshot revisions/work-item-opencode-commission-2026-11-18.md MD5 316eee870d56b0702a654ce0972aabb4. No model override. Admission is not completion. No code/test result yet. Local edits/tests/task-branch commits authorized; publication/deployment prohibited.

### Routing correction
The reused coordinator-alias session returned model mismatch openai/gpt-6.2-sol and made no edits; earlier matching preflight did not carry to that session. Stopped run9881f7d4 is not implementation success. Dispatched through configured ai-sdlc-implementation ACP alias (no model override), child agent:ai-sdlc-implementation:acp:a352a4c5-dee2-415f-81ba-be12d6bd9e33, run d12c7cc7-a591-4935-9ebe-170d2fde45d1; accepted with completion expected. Same immutable snapshot, mandatory effective-model check before code.

## ACP implementation interrupted; bounded continuation — 2026-11-18

Run d12c7cc7-a591-4935-9ebe-170d2fde45d1 terminated after 6m2s without completed result; cause not verified. Partial report confirmed OpenCode ACP and nan/deepseek-v4-flash, checked all commissioned hashes and approved checkout before planning toolchain setup. Coordinator independently verified clean working tree and unchanged HEAD aa5f3cc. No implementation/test completion claimed. Continuing same ACP session in bounded stages, first toolchain and project skeleton; original immutable inputs and publication restrictions unchanged. QA/Security remain pending.

## Product-repository migration — 2026-11-18

The full work-item directory and immutable revisions were moved without rewriting approved content from the shared agent workspace into this repository at `docs/ai-sdlc/work-items/WI-ACQ-AUTH-001/`. Historical absolute paths and snapshots are mapped in `../../README.md`; they do not point to live documents. The functional guide is copied to `../../sources/acquiring_simulator_functional_guide.md`. The approval hashes of requirements, profile, acceptance scenarios, domain review and architecture were preserved by this move. Project-local implementation commits `17ea72e` and `ccc229a` now exist; their existence is not independent QA, Security approval or human acceptance. Future handoffs must pin the checked-out repository revision and consult current product-local documents, not the former agent-workspace paths.
