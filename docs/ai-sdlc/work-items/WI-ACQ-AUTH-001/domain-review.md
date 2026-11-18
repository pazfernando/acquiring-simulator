# Independent acquiring-domain gate review — Operation A

Date: 2026-11-18. Reviewer: ai-sdlc-acquiring-domain. Fresh independent local-file assessment using payment-behavior-review, not confirmation of the preserved verdict.

## Verdict: PASS — domain specification bundle only

Read together with HD-04, the pinned requirements, profile and corrected scenarios have no outstanding critical business-rule gap. F02 and F04 close for this synthetic Operation A exercise. Approval of simulator policy is not proof of payment-network correctness. Outstanding findings: **0 critical / 0 major / 0 minor**; two nonblocking editorial/coverage observations below. No further business-policy correction is required for this narrowly bounded PASS.

This does not approve architecture, implementation/construction, Security, QA, acceptance, live connectivity or compliance. P08's field-level access/masking and eventual retention/purge policy remain explicit downstream interface/security obligations; HD-04 approves its exercise boundary, not an unspecified future policy.

## Evidence and authority

All identities computed locally (MD5); all commissioned current identities match.

| Artifact | Observed MD5 |
| :-- | :-- |
| requirements.md | b0c97974197a20dff1b72d38164d0e2b |
| simulator-profile.md | 5483a4a378c0d1101391c11a628d2ae6 |
| acceptance-scenarios.md corrected | 307922ad99e7468d53e687587a670750 |
| owner-decisions-2026-11-18.md | 75b3efd4a20fff309086e600470d758f |
| WORK_ITEM.md | ba11f60abd746182ca587d2435052699 |
| revisions/domain-review-r3-md5-10ea7518.md | 10ea75183f12a41857798f4114ae329c |
| revisions/acceptance-scenarios-r3-md5-a8a6d6522b34c77d727ce77ac870fa23.md | a8a6d6522b34c77d727ce77ac870fa23 |
| Functional Guide v0.5.1 working draft | 703fd59f66a3d097147eee79141e2524 |

Work-item root: /home/pazfernando/.openclaw/workspace/shared/ai-sdlc/work-items/acquiring-simulator/WI-ACQ-AUTH-001.
Guide: /home/pazfernando/.openclaw/workspace/shared/ai-sdlc/sources/acquiring_simulator_functional_guide.md.

Read role context, local USER/SOUL/IDENTITY, shared operating contract, execution contract, work-item template, archived source README, active payment skill, exact functional bundle, prior review and preserved scenarios. No live Drive equivalence or external source verification claimed.

Authority order: supplied literal human scope and HD-01–HD-04 precede historical proposal/pending labels. Literal scope is Operation A only, one simulated POS purchase; B/C and e-commerce excluded. Latest literal “ok aprueba de p01 a p08 y usemos... java spring boot la ultima version” is recorded by HD-04 against the exact profile digest above. This is evidence of owner disposition supplied for this review, not independent authentication of chat. HD-05's stack selection and HD-06's main/local-remote direction neither alter payment semantics nor grant architecture/publication approval.

## Finding closure

| Finding | Status | Fresh assessment |
| :-- | :-- | :-- |
| B01 | CLOSED | HD-01 baseline plus HD-04 P01/P02 now supply executable synthetic fixtures, precision, CP metadata and risk choices. USD remains simulator-only, not Mexican currency or posting policy. |
| B02 | CLOSED | HD-02 core plus HD-04 P03–P07 supply equality, validation/link precedence, concurrency, expiry and evidence ordering. No automatic redispatch of linked uncertain payments. |
| B03 | CLOSED | HD-03/R19 keep partial approval and stand-in deferred; R07/R08 and A10/A11 remain conditional/non-v1. |
| M01 | CLOSED | Internal deciding-actor evidence remains sourced; P08 restricts caller output to authorized business outcome/reference. No sourced mandate for caller-visible reasons or whole history. |
| N01 | CLOSED | Current independent review exists. Earlier review-needed wording is historical, not proof of a missing reviewer or new approval. |
| N02 | CLOSED | All commissioned current hashes match, including observed WORK_ITEM. Earlier record identities in preserved review remain historical; no live source identity inferred. |
| F01 | CLOSED | P04–P07 independently provide conservative, deterministic reference/equality/expiry/in-flight/evidence behavior without selecting implementation components. HD-04 now approves those policies. |
| F02 | CLOSED for exercise | HD-04 explicitly approves all eight pinned P groups, including P08's synthetic exercise disclosure/retention boundary. It does not settle future field-level masks/access or eventual purge; those limitations are part of the approved P08, not an outstanding request to approve P01–P08 again. |
| F03 | CLOSED | Primary R counts are not used to hide mixed policy details; HD-04 supplies their authority. P details remain simulator assumptions by origin, never guide/network rules. |
| F04 | CLOSED | Actual diff changes only A19 and A25. A19 separates mandatory-invalid/non-USD/non-POS requests from otherwise valid changed-data conflicts after authorized link resolution. A25 now constrains changed amount to valid USD range/precision. Both preserve zero dispatch/no original mutation and do not bypass validation. |

No PARTIAL or NOT CLOSED finding remains in this domain gate. P08 downstream obligations must not be misreported as completed security work.

## Rule-by-rule provenance challenge

“SOURCED” means this supplied working guide supports the business concept, not certification or universal scheme applicability. Approved simulator policy is a simulator assumption with recorded owner authority, not SOURCED scheme policy.

| Rule | Classification and independent check |
| :-- | :-- |
| R01 | SOURCED Guide §§1,6: merchant/acquirer mandate isolation and no credential sharing. No issuer-BIN/acquirer-ID conflation. |
| R02 | SOURCED §5 steps 2–3, §6: caller/merchant eligibility, validation and risk before dispatch. Exact synthetic thresholds/defaults come from HD-04 P02, not guide. |
| R03 | SOURCED §5 steps 4–5: permitted route under represented acquirer. Exact one-route fixture mapping is approved simulator P01, not real routing law. |
| R04 | SOURCED §5 step 6/outcomes: retain/return evidence-based approval, decline or unconfirmed. Processor rejection stays separate. |
| R05 | SOURCED §5 step 7: ambiguity/timeout is not issuer decline. |
| R06 | SOURCED §§5–7: distinguish processor rejection from issuer decision; §6 internal source. P08 controls disclosure; no mandatory external reason/source field inferred. |
| R07 | SOURCED §5 variant: explicit partial amount/channel handling only if supported; HD-03 defers from v1. |
| R08 | SOURCED §§5,6,11: preserve actual delegate source rather than issuer attribution; HD-03 defers. P08 does not introduce stand-in acceptance. |
| R09 | SOURCED §§5–6 duplicate control concept only. Exact algorithm is HD-02/HD-04, not guide. |
| R10 | SOURCED §§5–6 correlation, response references and evidence. Future lifecycle correlation does not add reversal/inquiry endpoints. |
| R11 | SOURCED §6 internal audit/deciding-actor evidence; P08 operator-only restricted history, not wholesale caller disclosure. |
| R12 | SOURCED §§4.2,6 plus human synthetic-only scope: no real PAN/CVV/PIN keys/genuine cryptograms or clear PIN. Synthetic flag is not EMV validation. |
| R13 | SOURCED §§4.2,8: approval does not establish capture, settlement or payout. No debit posting model selected. |
| R14 | SOURCED §§4.2,5 CP/terminal context; exact required synthetic enums/flags are HD-01/HD-04, not universal EMV/CVM rules. |
| R15 | APPROVED SIMULATOR POLICY HD-02 plus HD-04 P04/P05: identical retained authorized replay returns latest result/reference, no dispatch. Current validation/access still applies. |
| R16 | APPROVED SIMULATOR POLICY HD-02 plus HD-04 P04–P07: valid linked retry returns latest evidence, never automatically redispatches or invokes B/C. |
| R17 | APPROVED SIMULATOR POLICY HD-02 composite key/24 h/conflict, HD-04 P03/P04/P06 resolved equality and expiry. New unlinked purchase after expiry is not cancellation or a safe retry of the original. |
| R18 | APPROVED SIMULATOR POLICY HD-01 baseline plus HD-04 P01/P02 exact fixture/precision/risk/CP controls. USD/named routes are synthetic-only; unknown or foreign-only route rejects locally. |
| R19 | APPROVED SIMULATOR POLICY HD-03 deferral. No universal network rejection rule for partial approval/stand-in. |
| R20 | APPROVED SIMULATOR POLICY HD-02 with HD-04 P07 evidence order. 30 s is documented adjustable default, not guide SLA. Correlated confirmation at evaluation dominates timeout; later incompatible confirmations are retained/flagged, not silently substituted. |

Counts: **20 primary R rules = 14 SOURCED + 6 APPROVED SIMULATOR POLICY + 0 standalone SIMULATOR ASSUMPTION + 0 standalone UNRESOLVED**. Two sourced rows (R07/R08) are deferred. Mixed policy clauses retain simulator provenance. **8 P groups = 8 owner-approved simulator assumption groups / 0 awaiting owner disposition**; these are groups, not eight atomic rules. No count asserts that downstream field-level/security policy is resolved.

P01–P08 challenge: symbolic fixture labels/one-route mapping (P01), non-rounding precision/enums/risk/defaults/correlation (P02), resolved equality excluding transport references (P03), mandatory-validation then authorized-link precedence (P04), at-most-one dispatch/pending replay (P05), half-open unlinked window versus retained explicit links (P06), monotonic correlated evidence/conflict retention (P07), and restricted exercise disclosure/evidence retention (P08) are all explicit and consistent with scope. None is falsely attributed to a card brand. No protocol family, real network certification, country tariff or posting rule is selected.

## Scenario and boundary assessment

A01–A06 preserve issuer-versus-processor distinctions, malformed-input and wrong-acquirer rejection. A07–A09 and A12–A14 preserve unknown, latest replay/retry evidence, invalid-link rejection and original data on conflict. A15 forbids foreign-route fallback. A16–A18 suppress concurrent/in-flight redispatch and replay disclosure through invalid links. Corrected A19/A25 respect mandatory validation first. A20–A22 distinguish explicit retry identity/expiry from new unlinked purchase, retaining prior history. A23/A24 reject malformed/wrong-correlation evidence as confirmation and prevent confirmed-to-unknown regression. A26 tests approved synthetic profile boundaries without asserting cryptographic validation. A10/A11 remain deferred.

Independent count: **24 v1 + 2 deferred = 26 scenario rows** (12 v1 plus 2 deferred in requirements, 12 supplementary v1). No tests executed; parameter variants are not executed-test counts.

### Nonblocking observations and exact editorial alignment

1. Historical status labels are stale when artifacts are read alone. In a future controlled revision, add a current authority banner linking HD-04 and its pinned profile hash to requirements §3/§5/§7/§8/§10, profile header/authority column, scenario header/provenance and WORK_ITEM status/provenance/pending findings. Replace current-tense “pending/proposed/unapproved P01–P08” with “approved simulator policy by HD-04; originally proposed,” without rewriting the historical decision trail. Update WORK_ITEM to reference this review rather than claiming F02/F04 remain open. Keep future field-level masks/access/retention and all other gate limitations explicit. None of these files was edited here; HD-04 already explicitly governs their historical labels, so this is not a prerequisite for the bundle-level PASS. Any changed functional policy requires renewed review.
2. A19's changed valid risk alternative has no reachable distinct value in today's pass/reject-only profile: omitted resolves pass; reject fails controls. Mark that conflict subvariant “not applicable under current P02”; test omitted versus explicit pass as equal, and reject as validation/risk rejection. Do not invent a third risk value or broaden scope to make the case executable. A19 already conditions conflict on passing controls and explicitly excludes reject, so this is coverage clarification, not an incorrect required outcome. Later independent QA should derive concurrent-conflict and incompatible-confirmation variants directly from P05/P07.

## Blocking questions and downstream handoff

**Domain blocking questions: none.** No need to ask Fernando to reapprove P01–P08. No exact remaining payment-rule correction is required for this PASS.

Before interface construction, carry P08's explicit owner/security obligation for exact output/internal field inventory, masks, operator access and eventual retention/purge into the relevant architecture/security gate; do not infer completion from this verdict. Human architecture approval remains separate. QA and triggered Security require independent evidence and are not supplied here. Only domain-review.md was written.

Runtime limitation: configured MCP tools are omitted by policy in this run. No MCP-backed verification succeeded or is claimed. Local supplied-file review was sufficient for this domain gate; unavailable MCP-backed independent checks, live Drive identity, external provider/version and other external evidence remain unverified.
