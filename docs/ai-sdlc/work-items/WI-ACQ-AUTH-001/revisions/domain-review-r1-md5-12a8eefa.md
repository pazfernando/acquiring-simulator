# Independent acquiring domain review — WI-ACQ-AUTH-001

Date: 2026-11-16. Reviewer: ai-sdlc-acquiring-domain. Operation A only.

## Revision reviewed and evidence

- Requirements: `/home/pazfernando/.openclaw/workspace/shared/ai-sdlc/work-items/acquiring-simulator/WI-ACQ-AUTH-001/requirements.md`; MD5 **3e96eed94e6626476038e1391e25c69e**; 169 lines; Draft, 2026-11-16. Hash verified. No other requirements revision reviewed.
- Functional source: `/home/pazfernando/.openclaw/workspace/shared/ai-sdlc/sources/acquiring_simulator_functional_guide.md`, v0.5.1, 2026-11-16, Working draft; MD5 **703fd59f66a3d097147eee79141e2524**, verified; read in full. Section references below are to this guide.
- Operating contract: `/home/pazfernando/.openclaw/workspace/shared/ai-sdlc/README.md`; work-item template: adjacent `WORK_ITEM_TEMPLATE.md`; both read in full (no version declared).
- Human scope: literal instruction supplied with this commission and `WORK_ITEM.md` in this work-item directory, read in full. Actual MD5 **9d185aa5bced1a2731b547ab80bd46d4**, not commissioned **65a03c6ec36f7f8e68f988fc774eafce**. Current file records review commissioning/resolution of reviewer installation; the literal Operation A-only instruction and exclusions agree with the commission. No assertion is made about unseen earlier file contents.
- Procedure: `/home/pazfernando/.openclaw/skills/payment-behavior-review/SKILL.md`, active installed source, read in full; no version declared. Local USER.md, SOUL.md and IDENTITY.md also read.
- No approved network, scheme, route or interface profile was supplied. Public links in guide §12 were read as reference listings, not independently validated scheme specifications. No Drive revision identity verification.

## Verdict: BLOCKED

The draft is substantially sound as business analysis, and correctly identifies critical unknowns. It is **not a complete construction-ready payment contract**: R17–R20 and baseline/profile decisions prevent an evidence-based PASS. This is not a finding that analysis should stop or that excluded Operations B/C must be added. Human/institutional decisions must be recorded by their owners; this review records none.

Counts: **3 BLOCKING, 1 MAJOR, 2 MINOR** findings. B01–B03 are existing acknowledged decision gaps, not concealed analyst errors. M01 is a provenance/acceptance-contract correction. A revised requirements document requires independent review again; no automatic approval follows edits.

## Findings

| ID | Severity | Location | Finding | Evidence | Required correction |
| :-- | :-- | :-- | :-- | :-- | :-- |
| B01 | BLOCKING | R18; §§1,3,4,10 Q1; A01,A04,A05 | Baseline is proposed and eligible-dispatch oracle remains absent: supported amounts/currencies, risk rules, allowed simulated routes and synthetic CP evidence are not defined. | Guide §§2,4.2,5 steps 2–4; §9 D-005/006 Proposed, D-008/009 Open, D-015 staged. | Obtain human baseline decision and documented approved simulator/profile constraints with institutional input where applicable. Complete R18 and affected scenarios. Distinguish business/synthetic profile approval from later external wire mapping: no need to implement or certify an external protocol for this analysis. |
| B02 | BLOCKING | R15–R17,R20; A07–A09,A12–A14 | Duplicate/retry and ambiguity tests have no approved deterministic completion policy. A13 is explicitly a decision placeholder, not executable acceptance criteria; A12/A14 also leave final behavior open. | Guide §§5–7 mandate controls, evidence and uncertainty, but supply no keys/windows/equality, retry permission, deadline, invalid-reference response or late-update policy. | Obtain human approval or replacement of R15/R16; define key scope/window/equality, new-payment/conflict rules, invalid-reference response, timeout boundary, retry permissions and late evidence/update semantics. Update all affected Then clauses, including what a retry returns when confirmation has arrived since the original timeout. |
| B03 | BLOCKING | R19; A10,A11; §10 Q3 | Variant applicability to v1 is undecided. Their conditional semantics are correct, but neither construction nor acceptance can guess whether to implement them. | Guide §5 outcomes, §9 D-017 staged, §11 variants and STIP; human instruction does not settle these variants. | Human explicitly includes or defers each variant. If included, approve accepted-amount/channel behavior and source-evidence criteria; if deferred, make A10/A11 non-v1 and define the selected simulator profile's supported outcomes without inventing universal scheme rejection rules. |
| M01 | MAJOR | R06,R08,R11; §6 final paragraph; A01,A03,A10; §8 | Internal decision-source observability is sourced, but blanket caller-visible source/reason disclosure is not fully specified by the cited guide. “Return ... source and appropriate reason” coexists with unresolved disclosure policy. The unqualified SOURCED scenario annotations overstate that external contract. | Guide §6 explicitly says “Explain what the processor did and which actor decided”; §11 requires retaining provenance. Guide §5 returns outcome/references; it does not prescribe a source/reason field or full disclosure. Current WORK_ITEM goal supports distinguishing outcomes, not a particular returned field set. | Separate sourced internal provenance and outcome distinction from the proposed caller-visible representation. Label additional source/reason disclosure as simulator assumption pending approved business disclosure policy, or cite a genuine approved contract. Align §6 and scenario assertions; do not expose internal history merely to satisfy provenance. |
| N01 | MINOR | Requirements opening method; §9 | “Missing independent domain reviewer” / “No independent domain reviewer is installed” is stale. Draft not-yet-reviewed status was legitimate at authoring; installation claim is now false. | Current commissioned review and current WORK_ITEM independent-review section; operating contract requires actual evidence. | On next revision remove obsolete installation blocker; reference this review factually without claiming PASS or human approval. |
| N02 | MINOR | Commissioned WORK_ITEM identity | Actual WORK_ITEM hash differs from requested source hash; scope text agrees, but exact source identity cannot be claimed. | Hashes listed above. | Coordinator reconcile the recorded source revision/hash or provide the intended snapshot. Do not silently rewrite scope or approval history. Requirements and guide hashes match exactly. |

## Independent rule-provenance audit (all SOURCED rules)

| Rule | Assessment |
| :-- | :-- |
| R01 | Supported directly by §1 identifier/routing precision and §6 mandate controls. No sharing of acquiring credentials. |
| R02 | Supported by §5 steps 2–3 and processor-rejection branch plus §6. Fraud/risk evaluation is not merchant KYB. |
| R03 | Supported directly by §5 steps 4–5 and §1. A permitted route is not an arbitrary network selection. |
| R04 | Supported by §5 outcome paragraph, steps 6–7. “Declined” alone must not erase decision-source distinction; surrounding rules maintain it. |
| R05 | Explicitly supported by §5 step 7 and §6 uncertainty capability. |
| R06 | Substantively supported: §5 actually distinguishes the rejecting processor branch from the issuer branch, and §6 explicitly includes deciding actor. §7 is an open clarification question, not independently an approved answer. Internal distinction is sourced; exact caller representation falls under M01. |
| R07 | Direct, almost verbatim support in §5 outcomes: explicit accepted amount AND channel handling. Not a disguised design assumption; conditional applicability is correctly R19. |
| R08 | Direct support in §5 outcomes and §11 STIP/provenance entries: actual deciding party, not every approval issuer-issued. D-017 establishes staging, not a detailed rule. Caller-visible format still M01. |
| R09 | Directly supported by §5 step 3 and §6. Does not source the particular R15/R16 algorithm. |
| R10 | Supported by §5 step 6 and §6 references/evidentiary history; no retention duration supplied. |
| R11 | Explicitly supported by §6 auditability row, including deciding actor. The analyst is right about this citation, not merely inferring generic logging. |
| R12 | Supported by §4.2, §6 and human synthetic-only boundary; “prefer synthetic” in §6 alone would be weaker than the complete combined authority. |
| R13 | Supported by §§4.2,8; authorization is not downstream lifecycle proof. |
| R14 | Supported by §4.2 processor role and §5 step 3; terminal association/configuration also §11. No genuine cryptographic/PIN work is imported because §§1,4 limit this to conditional synthetic metadata. Add §11 for the most precise association reference; terminal-reference mandatory-field choice remains honestly labeled assumption in §4. |

## Checklist assessment and source-conflict adjudication

- **Responsibilities and tenant context:** Correct separation of processor, contracting acquirer, merchant/channel, network and issuer. Acquirer is not a required synchronous hop; issuing BIN is not acquiring identity. Wrong-acquirer processing and disclosure are prohibited. No security certification follows this business assessment.
- **Outcome semantics:** §§5–7 consistently keep not-confirmed distinct from approval and decline. Unknown is not a final issuer decision; receiving transport alone is not issuer evidence. A10 correctly attributes stand-in to network/delegate rather than issuer-issued approval. Partial amount is explicit, not full approval. Preserving earlier uncertainty history in A14 does not mean the current outcome must remain unknown forever; final transitions remain B02.
- **Duplicates/retries:** R15/R16 and A08/A09/A12/A13 are mutually consistent as drafts: replay known evidence or uncertainty without redispatch; unknown retries remain linked; conflicting details are not proven identical. Labels are honest, not purported scheme mandates. “No automatic dispatch” for an invalid reference is a conservative proposed extension, not a universal network rule. Exact oracles await B02. A09 should explicitly condition continuing uncertainty on there still being no confirmed evidence at retry time.
- **Scenario assertions:** No direct outcome contradiction found. A01–A07 follow R01–R14, subject to baseline validation choices. A08/A09 use identified assumptions. A10/A11 are truly conditional, not mandatory implementations. A12–A14 expose unresolved response/conflict/update decisions and cannot yet be accepted as complete tests. Caller-visible provenance has the M01 qualification. A14 history preservation is a reasonable consequence of R10/R11; exact transition and notification policy is not sourced. An unauthorized caller in A03 does not acquire a right to payment details merely because a rejection is returned.
- **UNRESOLVED adjudication:** R17 is genuinely open; §7 asks duplicate/new-payment identity but does not answer it. R18 is partly already answered at principle level (validate request, correct acquirer/route, applicable synthetic CP evidence); only the enumerated exact constraints remain open. R19 genuinely requires a scope decision, with no presumption that variants are mandatory. R20 genuinely lacks exact policy; uncertainty and retained evidence themselves are already answered and must not be reopened as “may unknown mean decline?” These are construction blockers, not reasons to block analysis. Human Product Lead owns scope/business approval; applicable acquirer/network evidence and architecture/security specialists must inform their respective details. The Human Product Lead cannot make an unsupported scheme rule authoritative by choosing it for the simulator.
- **Processor-decline wording conflict:** Analyst correctly quotes §5 “Record processor decline” / “Payment Authorization Declined,” and §7 really asks whether to distinguish processor and issuer decline. This is an underspecified common outcome vocabulary, not an instruction to attribute a processor rejection to the issuer. §6 already requires identifying the deciding actor. R06's distinction is defensible; the exact external wording/field contract remains open (M01).
- **Bad cross-reference:** Analyst is correct: §5 refers to §12 for follow-up partial/stand-in scenarios, but §12 only lists references. Actual functional text is §5 itself, §9 D-017 and §11. The cross-reference error does NOT remove the explicit accepted-amount/deciding-party requirements already present in §5. Neither §12 links nor D-017 supply detailed variant policy.
- **3DS/CP boundary:** Correct: CNP and authorization-side 3DS validation excluded; no claim 3DS is required for POS. Synthetic CP metadata is conditional, not PIN translation, cryptogram verification, terminal certification, offline/deferred approval or real EMV implementation. §4.2 role distinction is preserved.
- **Standard neutrality:** No implicit ISO 8583/ISO 20022 choice, MTI, message family or wire-field structure found. Semantic business input groups are not wire fields. §3 supports neutrality. D-009 wire specification remains downstream of confirmed requirements; it must not be conflated with an obligation to add live connectivity to this simulator.
- **Coverage and exclusions:** All procedure cases have an entry below. Missing-original reversal eligibility and status-query after ambiguity are legitimately out of Operation A. Missing prior retry reference remains applicable as A12, so excluding B/C does not excuse it. No unsupported partial/stand-in case is made mandatory.

## Compact payment-behavior-review cases

Flow | scenario | expected behavior | rule status/reference | gap | verdict
:-- | :-- | :-- | :-- | :-- | :--
Authorization | success A01 | issuer approval with evidence; not settlement | SOURCED R01–R04,R10–R14; §§1,4.2–6,8 | B01; external representation M01 | BLOCKED
Authorization | issuer decline A02 | retain issuer decision, not processor rejection | SOURCED R04,R06,R10; §§5–7 | external representation M01; baseline B01 | BLOCKED
Authorization | processor rejection A03 | no dispatch, no false issuer attribution | SOURCED R02,R06; §§5–6 | disclosure M01 | CONDITIONAL
Authorization | malformed A04 | reject before dispatch | SOURCED R02/R06; exact R18 UNRESOLVED; §5 | B01 exact valid input | BLOCKED
Authorization | duplicate/conflict A13 | distinguish repeat from intentional new payment | SOURCED control R09; R17 UNRESOLVED; §§6–7 | B02 conflict oracle | BLOCKED
Authorization | idempotent replay A08 | no redispatch, reuse known evidence or uncertainty | ASSUMPTION R15; R17 UNRESOLVED | B02 key/window/equality/approval | BLOCKED
Authorization | timeout A07 | not confirmed, not approval/decline | SOURCED R05; §5 step 7 | B02 deadline | BLOCKED
Authorization | retries A09 | link and do not automatically redispatch | ASSUMPTION R16; R20 UNRESOLVED | B02 permissions/current evidence | BLOCKED
Authorization | wrong acquirer A06 | no cross-mandate route or credentials | SOURCED R01–R03; §§1,5–6 | no substantive semantic gap | PASS
Authorization | missing prior retry reference A12 | no cross-context disclosure; draft no redispatch | SOURCED R01; ASSUMPTION R16 extension; R20 UNRESOLVED | B02 exact authorized response | BLOCKED
Authorization | late response A14 | retain prior uncertainty plus later evidence | SOURCED R05,R10/R11 principles; R20 UNRESOLVED | B02 transition/notification | BLOCKED
Authorization | stand-in A10 | attribute actual network/delegate, not issuer-issued | SOURCED R08; §§5,11; R19 UNRESOLVED | B03 applicability/evidence; M01 | BLOCKED
Authorization | partial approval A11 | accepted amount explicit; channel handling agreed | SOURCED R07; §5; R19 UNRESOLVED | B03 applicability/handling | BLOCKED
Reversal | missing original authorization | original eligibility/linkage belongs to Operation B | excluded human scope; §5 Operation B | not required in A; A12 remains required | PASS (inapplicable)
Status inquiry | query after ambiguity | no inquiry endpoint/action in this work item | excluded human scope; §5 Operation C | future operation, not a missing A endpoint | PASS (inapplicable)
CNP/3DS | authentication outcomes/evidence | no authentication or authorization-side evidence validation in v1 | excluded human scope; §4 conditional | not required; never infer POS 3DS | PASS (inapplicable)

## Questions requiring human decision and release of this block

1. Confirm or replace the proposed Mexico/debit/simulated CP baseline. Approve a documented simulator business profile: permitted routes, amounts/currencies and applicable risk/synthetic CP constraints (B01). Institutional rules require supporting institutional sources; a deliberately invented simulator policy must be labeled accordingly.
2. Approve or replace R15/R16, then complete the duplicate identity/conflict/new-payment policy and uncertain retry/deadline/invalid-reference/late-evidence policies (B02). A valid retry must use latest available evidence, not blindly freeze a stale unknown.
3. Include or explicitly defer each of partial approval and stand-in; complete amount/channel/evidence criteria only for included variants (B03).
4. Approve the business-level caller disclosure boundary or keep it explicitly proposed, and reconcile assertions accordingly (M01). Independent Security review must evaluate the eventual boundary; this reviewer does not approve it.

To obtain a domain PASS for construction-ready requirements: resolve B01–B03 with recorded owner evidence, correct M01, reconcile N01/N02 traceability, and submit the revised requirements/profile for fresh independent review. External protocol certification, adding B/C or implementing live payment cryptography are not required corrections. No approval is inferred from this list or this review.

## Review limitations

No scheme/network/ISO/EMV/PCI conformance certification, no security review, no architecture approval, no production implementation, no QA execution or test-pass claim, no external sends and no human approval recorded. Findings assess business semantics and source traceability only. Requirements and WORK_ITEM were not modified.
