# WI-ACQ-AUTH-001 — Operation A requirements

Status: **Draft — not approved or independently domain-reviewed**. Date: 2026-11-16.
Authority: Fernando Paz's human scope recorded in WORK_ITEM.md; Functional Process Guide v0.5.1 (2026-11-16, working draft), cited below as Guide §. Human scope takes precedence over broader guide examples.
Method: payment-behavior-review skill applied; this is analysis, not independent verification. Missing independent domain reviewer blocks the Domain Reviewed gate.

## 1. Scope and boundaries

**Trigger:** a cardholder starts a purchase; an authorized origination channel requests a payment authorization for its merchant.
**In scope:** one simulated authorization request/response, caller/request validation, merchant/terminal/acquirer context, simulated routing and financial decision, duplicate control, uncertainty, response propagation, references and evidence.

**Working baseline only (D-005/D-006 Proposed):** Mexican-market profile, debit purchase, simulated card-present POS, online authorization. Every POS/terminal/CP-metadata requirement below depends on confirmation of this baseline. No Mexican routing, currency, PIN, posting or scheme rule is inferred.

**Out of scope:** Operations B reversal and C status inquiry (separate work items); automatic reversal; capture/completion, clearing, settlement, reconciliation, payouts, refunds, disputes/chargebacks, fees and FX/DCC; account verification; estimated/preauthorization/incremental variants; real credentials, live connections and certification; CP EMV/CVM beyond synthetic metadata, offline approval, deferred authorization and device certification. Partial approval and stand-in are conditional variants pending the explicit v1 decision in §10, not silently included.

CNP/EMV 3DS and authorization-side validation of 3DS evidence are **not in v1**. Authentication is a separate subprocess; successful 3DS does not guarantee authorization (Guide §4). No 3DS flow is specified.

This document does not decide priority, architecture, storage, stack, interface shape, message standard, country-specific rules, external route, scheme profile or compliance interpretation. Business-state labels below are observable semantics, not a mandated state-machine component.

## 2. Actors, channel and represented acquirer context

Guide §1 and §5 Operation A define: cardholder initiates; merchant accepts under an acquiring relationship; authorized origination channel submits; represented acquirer supplies mandate and commercial/routing context; our multi-acquirer processor validates, records and routes; approved domestic infrastructure and/or international network routes where applicable; issuer decides. A network delegate may decide in a supported stand-in variant, with explicit provenance.

The processor is not the contracting acquirer, issuer or scheme. An acquirer need not be a separate synchronous hop. Internal acquirer reference, external acquiring identifier, merchant identifier and issuing BIN/IIN are distinct concepts; no issuing BIN length or acquirer ownership is assumed. Baseline channel is simulated POS only, subject to D-006.

## 3. Preconditions and prerequisites

- An identified acquirer has an active authorized mandate, permitted simulated routes and scoped credentials (Guide §1, §6).
- Merchant is recognized, eligible and permitted under that acquirer; baseline terminal is recognized and associated with that merchant (Guide §4.2, §5, §11).
- Caller/channel authentication and authorization can be checked before dispatch (Guide §6). Provisioning and authentication mechanism are not selected.
- Only synthetic credential references and test metadata exist; no real PAN, PIN keys, clear PIN, CVV or genuine cryptograms (Guide §4.2, §6; human scope).
- Simulated decisions and route permissions must be supported by an approved profile before construction; no real onboarding, KYB or connectivity process is specified.
- Risk and duplicate policies require human confirmation; their existence is sourced, their thresholds and algorithms are not.

## 4. Input contract — business-level only

“Required” below is a draft semantic requirement, not a wire-field designation. Values may be supplied by the caller or resolved from authenticated context; derivation must not trust an unauthorized caller assertion. Guide lists concepts, not a complete mandatory schema. Additional mandatory-field choices are labeled assumptions.

| Group / name | Purpose | Required / optional | Provenance |
| :-- | :-- | :-- | :-- |
| Identifiers / represented acquirer reference | Resolve whose mandate applies | Required, supplied or resolved | SOURCED §1; §5 step 2 |
| Identifiers / merchant reference | Resolve acquiring relationship and eligibility | Required | SOURCED §5 step 2 |
| Identifiers / submitting caller identity | Validate authority for this operation/context | Required, authenticated context | SOURCED §5 step 3; §6 |
| Amount/currency / requested amount, transaction currency | Identify requested financial decision | Both required | SOURCED §5 step 2; supported values, precision and limits UNRESOLVED |
| Merchant/terminal / terminal reference and merchant association | Validate baseline POS origin | Required for working CP baseline | SOURCED §4.2, §11 context; requiredness SIMULATOR DESIGN ASSUMPTION |
| Merchant/terminal / merchant category, terminal capabilities | Apply eligible profile controls | Conditional; may resolve from configuration | SOURCED §11 concepts; exact applicability UNRESOLVED |
| Credential / synthetic payment credential reference | Identify test card and simulated issuer/product | Required | SOURCED §5 step 2; synthetic-only boundary §4.2 and human scope |
| Channel/entry mode / channel type, entry-mode indicator | Distinguish CP POS origin and supported entry capability | Required for working CP baseline | SOURCED §4.2, §5 step 3; exact values UNRESOLVED |
| Correlation / channel request reference | Correlate this exchange and response | Required | SIMULATOR DESIGN ASSUMPTION derived from §6 |
| Duplicate keys / business payment reference | Identify same business request separately from transport attempt | Required in draft | SIMULATOR DESIGN ASSUMPTION; scope/window/equality UNRESOLVED |
| Correlation / prior attempt or processor transaction reference | Link retry to known uncertain authorization | Conditional on retry | SIMULATOR DESIGN ASSUMPTION; invalid-reference policy UNRESOLVED |
| Risk/authentication / synthetic risk indicators | Support relevant transaction-risk evaluation | Conditional on accepted policy | SOURCED §5 step 3, §6; exact indicators/thresholds UNRESOLVED |
| Risk/authentication / synthetic EMV and CVM metadata | Represent applicable baseline CP evidence without secrets | Conditional on selected CP profile | SOURCED §4.2; synthetic-metadata limit from human scope |
| Authentication / 3DS evidence | Future CNP authentication evidence | Not applicable in v1; no flow or fields specified | SOURCED §4; excluded by human scope |

No assumption is made that tokenization, online PIN, a particular currency, or a network identifier is universally required.

## 5. Business rules

Provenance counts apply only to this numbered rule table, not every input/scenario annotation.

| ID | Rule | Provenance | Why it matters | Decision owner if unresolved |
| :-- | :-- | :-- | :-- | :-- |
| R01 | Enforce represented acquirer mandate and merchant context; never share one acquirer's credentials with another. | SOURCED §1, §6 | Prevent cross-acquirer processing | — |
| R02 | Verify caller and merchant eligibility, validate request and assess relevant transaction risk before routing; processor controls may reject without dispatch. | SOURCED §5 steps 2–3, sequence; §6 | Reject unauthorized/invalid operations | — |
| R03 | Determine an approved route and dispatch only under the identified acquirer's context. | SOURCED §5 steps 4–5 | Correct mandate and permitted path | — |
| R04 | Return and record approved, declined, or not-confirmed business outcomes based on evidence. | SOURCED §5 business outcomes, step 6 | Observable result semantics | — |
| R05 | A downstream timeout or ambiguous reply is unconfirmed, never automatically an issuer decline. | SOURCED §5 step 7 | Preserve financial uncertainty | — |
| R06 | Distinguish processor rejection from issuer decline in observable result and history, including decision source. | SOURCED §5 sequence; §7 clarification; §6 observability | Avoid false issuer attribution | — |
| R07 | A supported partial approval must identify explicit accepted amount and appropriate channel handling. | SOURCED §5 business outcomes | Avoid full-amount interpretation | — |
| R08 | A stand-in approval must retain evidence identifying the actual decision-making party, not be labeled issuer-issued. | SOURCED §5 business outcomes; §9 D-017; §11 | Correct decision provenance | — |
| R09 | Apply duplicate-request control to avoid inconsistent outcomes for repeated business requests. | SOURCED §5 step 3; §6 | Avoid repeated financial effects | — |
| R10 | Retain transaction references, response references and evidentiary history linking request and outcome. | SOURCED §5 step 6; §6 | Enable later authorized lifecycle correlation | — |
| R11 | Make processing actions and deciding actor auditable and operationally observable. | SOURCED §6 | Explain actual processor behavior | — |
| R12 | Protect payment data; use synthetic credentials/test metadata only; do not expose/store clear PIN or use real PAN, CVV, PIN keys or genuine cryptograms. | SOURCED §4.2, §6; human scope | Safe simulator boundary | — |
| R13 | Authorization/issuer decision is not evidence of capture, clearing, settlement, reconciliation or payout. | SOURCED §4.2, §8 | Preserve lifecycle boundaries | — |
| R14 | Validate terminal association and preserve applicable entry-mode/CVM/EMV metadata for the working CP baseline only. | SOURCED §4.2, §5 step 3 | Correct baseline channel context | — |
| R15 | Draft replay policy: identical replay under the same authorized acquirer/merchant/business key returns prior known result/reference, or pending uncertainty, without new dispatch. | SIMULATOR DESIGN ASSUMPTION | Concrete duplicate test oracle | — |
| R16 | Draft retry policy: a retry linked to an unknown outcome does not cause automatic redispatch; retain uncertainty and correlation until an approved resolution policy applies. | SIMULATOR DESIGN ASSUMPTION | Avoid duplicate authorizations | — |
| R17 | Exact duplicate key scope, retention window, equality test, key-conflict response and intentional-new-payment distinction remain undecided. | UNRESOLVED | Defines duplicate identity | Human Product Lead |
| R18 | Amount/currency validation, profile-specific risk policy, allowed routes and synthetic CP evidence requirements remain undecided. | UNRESOLVED | Defines eligible dispatch | Human Product Lead, with domain input |
| R19 | Partial approval and stand-in inclusion in v1, supported test cases and channel behavior require explicit scope decision. | UNRESOLVED | Defines accepted variant behavior | Human Product Lead |
| R20 | Retry/new-attempt authorization after uncertainty, late evidence handling, response deadline and invalid prior-reference behavior remain undecided. | UNRESOLVED | Prevents unsupported finality or duplicate effects | Human Product Lead |

Rule totals: **14 SOURCED / 2 SIMULATOR DESIGN ASSUMPTION / 4 UNRESOLVED**. Assumptions require approval, not inferred scheme authority.

## 6. Observable state changes

These are business descriptions; exact persistent state model is not selected.

| Before / event | Internal record after event | Externally observable | Basis |
| :-- | :-- | :-- | :-- |
| No known operation / request received | Received, context and validation evidence | Correlated exchange; no financial decision yet | R02, R10; label is design assumption |
| Received / processor checks fail | Rejected before dispatch, processor source and reason | Processor rejection, not issuer decline | R02, R06 |
| Validated / dispatched | Awaiting decision, represented acquirer, route, correlation and dispatch evidence | No asserted approval/decline before evidence | R03, R10, R11 |
| Awaiting / issuer approves | Approved with issuer source and response references | Approved and correlated reference; not settlement | R04, R10, R13 |
| Awaiting / issuer declines | Declined with issuer source and response references | Issuer decline distinct from processor rejection | R04, R06 |
| Awaiting / timeout or ambiguity | Not confirmed; preserve known dispatch evidence | Outcome unknown/not confirmed | R05 |
| Awaiting / supported stand-in approval | Approved with network/delegate source and evidence | Approved with actual decision provenance | R08, R19; conditional |
| Awaiting / supported partial approval | Accepted amount plus source/evidence | Explicit partial amount and approved channel handling | R07, R19; conditional |
| Recorded / duplicate or retry | Preserve history; link repeat attempt | Prior result/reference or continued uncertainty under draft policy | R09–R11, R15–R17, R20 |

Internal history is not wholesale caller-visible. Return authorized references, outcome, source and appropriate reason/amount; exact response vocabulary and disclosure are unresolved. Preserve provenance as issuer, network stand-in/delegate, processor rejection, or no confirmed decision; a transport response alone is not issuer evidence. Late confirmation must not silently erase the earlier uncertainty history; exact transition/notification policy remains R20. This work item exposes no status-inquiry endpoint or reversal action.

## 7. Given/When/Then scenarios

Unless stated otherwise, given conditions use the **unconfirmed Mexican debit / simulated CP POS baseline**, synthetic credentials, eligible acquirer/merchant/terminal and an authenticated authorized caller. Assertions using R15/R16 are proposed simulator behavior, not approved scheme rules.

| ID / case | Given | When | Then | Rules / provenance |
| :-- | :-- | :-- | :-- | :-- |
| A01 Issuer approval | Valid baseline purchase and permitted simulated route | Issuer approval evidence is returned | Record/return approved with issuer provenance and references; no settlement assertion | R01–R04, R10–R14; SOURCED §1, §4.2, §5, §6, §8 |
| A02 Issuer decline | Valid request dispatched | Issuer declines | Record/return issuer decline and references, not processor rejection | R04, R06, R10; SOURCED §5–§7 |
| A03 Processor rejection | Merchant is ineligible, or caller lacks authority | Request is checked | Do not dispatch; record/return processor rejection, not issuer decline | R01–R03, R06; SOURCED §1, §5–§6 |
| A04 Malformed request | Amount/currency or required credential reference is absent or structurally invalid | Validation runs | Reject before dispatch with processor source; exact profile validation remains unresolved | R02, R06, R18; SOURCED §5; UNRESOLVED exact constraints |
| A05 Missing/unknown context | Merchant or baseline terminal cannot be resolved | Request arrives | No dispatch and no issuer decision; report processor validation rejection | R01–R03, R14; SOURCED §1, §4.2, §5; baseline conditional |
| A06 Wrong acquirer | Acquirer is unknown, or merchant belongs to a different acquirer | Context is checked | Reject without using another acquirer's mandate, route or credentials | R01–R03, R06; SOURCED §1, §5 |
| A07 Unknown downstream outcome | Request was dispatched | Downstream timeout or ambiguous evidence occurs | Record/return not confirmed; do not claim decline or approval | R04–R05, R10–R11; SOURCED §5–§6; deadline UNRESOLVED R20 |
| A08 Identical replay | Same authorized context and business key/payload already recorded | Identical request is replayed | Under draft policy, no new dispatch; return known outcome/reference or retained uncertainty | R09–R10, R15, R17; SOURCED control §6; DESIGN ASSUMPTION replay; key policy UNRESOLVED |
| A09 Retry after unknown | A07 exists with supplied prior reference | Channel retries | Under draft policy link attempt, retain unknown and do not automatically redispatch; no reversal/inquiry flow is invoked | R05, R09–R10, R16, R20; SOURCED uncertainty §5–§6; DESIGN ASSUMPTION retry |
| A10 Approval via stand-in (variant) | Stand-in variant is explicitly admitted and delegated source evidence exists | Approval arrives from network acting for issuer | Return approved but source is stand-in/delegate, not “issuer-issued”; retain evidence | R04, R08, R19; SOURCED §5, §11, D-017; inclusion UNRESOLVED |
| A11 Partial approval (variant) | Partial approval is explicitly admitted with agreed channel handling | Evidence accepts less than requested | Record/return explicit accepted amount and partial result; do not claim full approval | R07, R19; SOURCED §5; v1 inclusion UNRESOLVED |
| A12 Invalid retry reference | Supplied prior reference is missing from the authorized context or belongs to another acquirer | Retry is evaluated | No cross-acquirer disclosure or automatic dispatch; exact rejection/unknown-reference response awaits decision | R01, R16, R20; SOURCED context §1; DESIGN ASSUMPTION no redispatch; UNRESOLVED response |
| A13 Conflicting duplicate | Same proposed business key has materially different payment details | Repeat is evaluated | Do not treat it as proven identical replay; conflict/new-payment behavior must be decided before construction | R09, R17; SOURCED control §6; UNRESOLVED oracle |
| A14 Late evidence | Request was reported not confirmed | Later confirmed issuer evidence arrives | Preserve original uncertainty and late source evidence; do not manufacture an earlier decline; exact update/notification behavior awaits decision | R05, R10–R11, R20; SOURCED evidence §5–§6; UNRESOLVED late handling |

A10 resolves the phrase “issuer approval with stand-in provenance”: financial approval may be on behalf of the issuer, but cannot be attributed to the issuer itself when a network delegate made the decision.
Original-authorization eligibility for reversal and query-after-ambiguity scenarios are inapplicable here because Operations B/C are excluded; their future correlation is preserved. Authentication challenge success/failure is inapplicable because CNP/3DS is excluded.

## 8. Non-functional constraints

- Synthetic/test data only; no real PAN/PIN/CVV, PIN keys, live credentials or genuine cryptograms. Protect credentials and avoid unsafe exposure/retention (Guide §4.2, §6).
- Retain auditable request/outcome correlations and decision provenance; operational evidence must explain actions and deciding actor (Guide §6).
- Authentication, acquirer scope and merchant eligibility controls apply before routing (Guide §1, §6).
- Exact audit retention, masking/disclosure policy and sensitive-field inventory are **UNRESOLVED**; Human Product Lead owns required business policy with independent security/domain input. No storage/encryption implementation is selected.
- Latency/deadlines, availability and scale targets are **UNRESOLVED — Human Product Lead**. The guide supplies no quantitative targets. Do not invent them.

## 9. Open decisions affecting this item

Guide §9 status is reproduced, not advanced by this draft.

| Decision | Current status | What remains blocked |
| :-- | :-- | :-- |
| D-005 Mexico reference, portable model | Proposed | Approval of baseline market-dependent examples/routing |
| D-006 Debit / simulated POS first | Proposed | Approval of baseline channel/product and applicable CP requirements |
| D-007 Business-level standard neutrality | Proposed | Formal endorsement of neutrality; this draft remains neutral as instructed, selecting no standard |
| D-008 Network/scheme, domestic route, single-/dual-message profile | Open | Exact eligible routes, amount/credential/variant semantics and debit posting interpretation |
| D-009 External interface after checking real network requirements | Open | External message mapping and conformance; not business drafting |
| D-017 Authorization variants, stand-in, account verification | Gap confirmed; staged | Whether partial approval/stand-in are v1 tests or deferred; account verification remains excluded by current scope |

Additional construction gaps: R17–R20; audit/observability detail and quantitative targets (§8). No independent domain reviewer is installed; requirements cannot self-pass review. Architecture and implementation remain downstream gates.

## 10. Blocking questions

1. **Do you confirm Mexico, debit purchase and simulated card-present POS as the v1 baseline (D-005/D-006), and which network/scheme and permitted route/profile should govern it (D-008/D-009), while retaining business-level message neutrality (D-007)?**
   Why it blocks: eligible dispatch and profile-specific validation cannot be approved without a reference profile. Meanwhile: use the proposed baseline for analysis only; select no country rule, route, protocol or posting semantics.

2. **Do you accept the draft duplicate/retry policy in R15–R16, and what business-key scope/window, conflicting-key response, invalid-prior-reference response, timeout boundary and late-confirmation behavior should complete R17/R20?**
   Why it blocks: replay and uncertain retries otherwise have no approved deterministic oracle and may create duplicate authorizations. Meanwhile: proposed identical replay reuses known evidence; unknown retry never automatically redispatches; exact error/timing/update behavior stays unresolved.

3. **Are partial approval and network stand-in in v1 or deferred under D-017; if included, what accepted-amount/channel handling and decision-provenance evidence are required?**
   Why it blocks: variant scope and observable acceptance criteria are not decided. Meanwhile: A10/A11 are conditional specifications only, not mandatory v1 implementation; neither is silently rejected as a universal scheme rule.

### Source limitations and conflicts for handoff

- Guide §5 says partial approval/stand-in follow-ups are in §12, but §12 is public references; relevant staged material is §9 D-017 and §11. No variant policy can be derived from that erroneous cross-reference.
- Guide §5 diagram calls processor rejection “Payment Authorization Declined” and records “processor decline”; §7 asks about distinguishing processor versus issuer decline. R06 preserves that distinction using §6 decision-source observability; exact external vocabulary needs review.
- Guide §11.2 suggests a broader first slice including reversals and inquiry; it expressly is proposed prioritization. Human Operation A-only scope governs.
- Guide is silent on complete input requiredness, duplicate keys/windows/equality, exact replay/retry rules, late evidence, timeout duration, exact validation/risk thresholds, partial channel behavior, proof format for stand-in, response disclosure and quantitative service targets. Those remain labeled assumptions/unresolved, not sourced scheme rules.
- The operating contract's statement that the guide was missing is historical; the supplied v0.5.1 snapshot exists and governs this draft. The Drive revision cannot be verified here; coordinator records that source-identity question separately.
