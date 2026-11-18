# WI-ACQ-AUTH-001 — Operation A requirements

Status: **Draft revision 3 — consolidated specification; fresh independent review and pending human detail decisions required**. Date: 2026-11-18.

## Revision history

| Revision | Date | md5 | Change summary | Review status |
| :-- | :-- | :-- | :-- | :-- |
| r1 | 2026-11-16 | 3e96eed94e6626476038e1391e25c69e | Original requirements; immutable copy in revisions/requirements-r1-md5-3e96eed9.md | BLOCKED, independent review 2026-11-16 |
| r2 | 2026-11-18 | bb0807235003b939c96e90c7aa178119 | HD-01–HD-03 incorporated; preserved in revisions/requirements-r2-md5-bb080723.md | CONDITIONAL, domain-review.md |
| r3 | 2026-11-18 | Computed after writing; returned in handoff | Consolidates r2; deterministic proposed exception handling and explicit pending detail register | Not independently reviewed |

Current file identity is returned in the handoff, not embedded self-referentially. Human instruction “ok vamos con esa especificacion” accepts preparation of this Operation A specification: one simulated POS purchase, stated inputs/controls, four distinguishable outcomes and duplicate/timeout/late-response handling; it does not approve new profile details. The current v0.5.1 attachment matches the archived source (md5 703fd59f66a3d097147eee79141e2524), as supplied in this commission.

Authority: Fernando Paz's human scope recorded in WORK_ITEM.md; Functional Process Guide v0.5.1 (2026-11-16, working draft), cited below as Guide §. Human scope takes precedence over broader guide examples.
Method: active payment-behavior-review skill applied; this is analysis, not independent verification. r1 was independently BLOCKED; r2 was independently CONDITIONAL (F01–F03). This r3 preserves their obligations and proposes corrections without claiming reviewer closure, PASS or new human approval. The active payment-behavior-review skill is /home/pazfernando/.openclaw/skills/payment-behavior-review/SKILL.md (the commissioned shared/skills path is absent).

## 1. Scope and boundaries

**Trigger:** a cardholder starts a purchase; an authorized origination channel requests a payment authorization for its merchant.
**In scope:** one simulated authorization request/response, caller/request validation, merchant/terminal/acquirer context, simulated routing and financial decision, duplicate control, uncertainty, response propagation, references and evidence.

**Approved simulator baseline (HD-01, WORK_ITEM.md Human decision record, 2026-11-18):** debit purchase, simulated CP POS, online authorization; USD only, amounts 1.00–9,999.99 inclusive. Two named simulated routes: **domestic route** and **international scheme route**. Test credentials/BIN ranges select approve, decline or timeout; synthetic CP evidence comprises entry mode, CVM indicator and cryptogram-present flag. These are simulator profile choices, not SOURCED scheme rules.

**USD boundary:** USD is a simulator profile default. It does not model Mexican pricing or currency, does not assert that Mexican domestic debit runs in USD, and imports no scheme, tariff or posting rule. D-005 remains Proposed as business context; D-008/D-009 remain Open. No external protocol is selected.

**Out of scope:** Operations B reversal and C status inquiry (separate work items); automatic reversal; capture/completion, clearing, settlement, reconciliation, payouts, refunds, disputes/chargebacks, fees and FX/DCC; account verification; estimated/preauthorization/incremental variants; real credentials, live connections and certification; CP EMV/CVM beyond synthetic metadata, offline approval, deferred authorization and device certification. Partial approval and stand-in are deferred from v1 by HD-03; conditional R07/R08 and A10/A11 are not v1 acceptance criteria.

CNP/EMV 3DS and authorization-side validation of 3DS evidence are **not in v1**. Authentication is a separate subprocess; successful 3DS does not guarantee authorization (Guide §4). No 3DS flow is specified.

This document does not decide priority, architecture, storage, stack, interface shape, message standard, country-specific rules, external route, scheme profile or compliance interpretation. Business-state labels below are observable semantics, not a mandated state-machine component.

## 2. Actors, channel and represented acquirer context

Guide §1 and §5 Operation A define: cardholder initiates; merchant accepts under an acquiring relationship; authorized origination channel submits; represented acquirer supplies mandate and commercial/routing context; our multi-acquirer processor validates, records and routes; approved domestic infrastructure and/or international network routes where applicable; issuer decides. A network delegate may decide in a supported stand-in variant, with explicit provenance.

The processor is not the contracting acquirer, issuer or scheme. An acquirer need not be a separate synchronous hop. Internal acquirer reference, external acquiring identifier, merchant identifier and issuing BIN/IIN are distinct concepts; no issuing BIN length or acquirer ownership is assumed. Baseline channel is simulated POS only under HD-01; guide D-006 remains Proposed in the guide register.

## 3. Preconditions and prerequisites

- An identified acquirer has an active authorized mandate, permitted simulated routes and scoped credentials (Guide §1, §6).
- Merchant is recognized, eligible and permitted under that acquirer; baseline terminal is recognized and associated with that merchant (Guide §4.2, §5, §11).
- Caller/channel authentication and authorization can be checked before dispatch (Guide §6). Provisioning and authentication mechanism are not selected.
- Only synthetic credential references and test metadata exist; no real PAN, PIN keys, clear PIN, CVV or genuine cryptograms (Guide §4.2, §6; human scope).
- HD-01 simulator profile prerequisites: USD 1.00–9,999.99, an acquirer-permitted named domestic route or international scheme route, a registered synthetic credential and all three CP metadata indicators. No real onboarding, KYB or connectivity process is specified.
- HD-02 approves duplicate/retry policy (§5). Profile fixture details below operationalize HD-01 as SIMULATOR DESIGN ASSUMPTIONS, not scheme rules: synthetic BIN labels SIM-APP-000–SIM-APP-999 approve, SIM-DEC-000–SIM-DEC-999 decline, SIM-TMO-000–SIM-TMO-999 produce no decision by timeout. These are symbolic test ranges, never PANs or real BIN allocations. Each credential fixture has exactly one permitted named route; no permitted route means processor rejection.
- SIMULATOR DESIGN ASSUMPTIONS for executable profile checks: amount has at most two fractional digits; entry mode is simulated-contact or simulated-contactless; CVM indicator is simulated-verified or simulated-not-required; cryptogram-present is a required boolean, with either value admissible (no genuine cryptographic validation). Synthetic risk indicator is pass or reject, default pass when omitted; reject prevents dispatch. Unknown credential/range, unknown route or invalid/missing required CP indicator rejects before dispatch. These fixture/risk choices require human approval; HD-01 does not itself specify their exact values.

## 4. Input contract — business-level only

“Required” below is a draft semantic requirement, not a wire-field designation. Values may be supplied by the caller or resolved from authenticated context; derivation must not trust an unauthorized caller assertion. Guide lists concepts, not a complete mandatory schema. Additional mandatory-field choices are labeled assumptions.

| Group / name | Purpose | Required / optional | Provenance |
| :-- | :-- | :-- | :-- |
| Identifiers / represented acquirer reference | Resolve whose mandate applies | Required, supplied or resolved | SOURCED §1; §5 step 2 |
| Identifiers / merchant reference | Resolve acquiring relationship and eligibility | Required | SOURCED §5 step 2 |
| Identifiers / submitting caller identity | Validate authority for this operation/context | Required, authenticated context | SOURCED §5 step 3; §6 |
| Amount/currency / requested amount, transaction currency | Identify requested financial decision | Both required; USD only, 1.00–9,999.99 inclusive | SOURCED §5 step 2; values/limits APPROVED SIMULATOR POLICY HD-01; precision SIMULATOR DESIGN ASSUMPTION §3 |
| Merchant/terminal / terminal reference and merchant association | Validate baseline POS origin | Required for approved simulator CP baseline | SOURCED §4.2, §11 context; requiredness SIMULATOR DESIGN ASSUMPTION |
| Merchant/terminal / merchant category, terminal capabilities | Apply eligible profile controls | Conditional; may resolve from configuration; no extra MCC/capability threshold is asserted for this profile | SOURCED §11 concepts; any later threshold requires separate policy |
| Credential / synthetic payment credential reference | Identify test card and simulated issuer/product | Required | SOURCED §5 step 2; synthetic-only boundary §4.2 and human scope |
| Channel/entry mode / channel type, entry-mode indicator | Distinguish CP POS origin and supported entry capability | Required for approved simulator CP baseline | SOURCED §4.2, §5 step 3; profile choices: see §3 (SIMULATOR DESIGN ASSUMPTION) |
| Correlation / channel request reference | Correlate this exchange and response | Required | SIMULATOR DESIGN ASSUMPTION derived from §6 |
| Duplicate keys / business payment reference | Identify same business request separately from transport attempt | Required channel payment reference | APPROVED SIMULATOR POLICY HD-02; key/window/equality R17 |
| Correlation / prior attempt or processor transaction reference | Link retry to known uncertain authorization | Conditional on retry | APPROVED SIMULATOR POLICY HD-02; invalid/foreign reference rejects (R20) |
| Risk/authentication / synthetic risk indicators | Support relevant transaction-risk evaluation | Optional, defaults to pass | SOURCED control §5 step 3, §6; pass/reject profile SIMULATOR DESIGN ASSUMPTION §3 |
| Risk/authentication / synthetic EMV and CVM metadata | Represent applicable baseline CP evidence without secrets | Required entry mode, CVM indicator, cryptogram-present boolean | APPROVED SIMULATOR POLICY HD-01; exact fixture values SIMULATOR DESIGN ASSUMPTION §3; conceptual context Guide §4.2 |
| Authentication / 3DS evidence | Future CNP authentication evidence | Not applicable in v1; no flow or fields specified | SOURCED §4; excluded by human scope |

USD and CP evidence are this simulator profile only; no universal tokenization, online PIN, currency or network-identifier requirement is inferred. A permitted route and synthetic credential range are resolved from the authorized profile, not an untrusted routing assertion.

## 5. Business rules

Provenance counts apply only to this numbered rule table, not every input/scenario annotation.

| ID | Rule | Provenance | Why it matters | Decision owner if unresolved |
| :-- | :-- | :-- | :-- | :-- |
| R01 | Enforce represented acquirer mandate and merchant context; never share one acquirer's credentials with another. | SOURCED §1, §6 | Prevent cross-acquirer processing | — |
| R02 | Verify caller and merchant eligibility, validate request and assess relevant transaction risk before routing; processor controls may reject without dispatch. | SOURCED §5 steps 2–3, sequence; §6 | Reject unauthorized/invalid operations | — |
| R03 | Determine an approved route and dispatch only under the identified acquirer's context. | SOURCED §5 steps 4–5 | Correct mandate and permitted path | — |
| R04 | Return and record approved, declined, or not-confirmed business outcomes based on evidence. | SOURCED §5 business outcomes, step 6 | Observable result semantics | — |
| R05 | A downstream timeout or ambiguous reply is unconfirmed, never automatically an issuer decline. | SOURCED §5 step 7 | Preserve financial uncertainty | — |
| R06 | Distinguish processor rejection from issuer decline in observable result and history; record decision source internally (Guide §6). Caller-visible source/reason fields are a SIMULATOR DESIGN ASSUMPTION pending approved disclosure policy, not a sourced obligation. | SOURCED §5 sequence; §7 clarification; §6 observability | Avoid false issuer attribution | — |
| R07 | A supported partial approval must identify explicit accepted amount and appropriate channel handling. Conditional, deferred by HD-03; not a v1 acceptance criterion. | SOURCED §5 business outcomes | Avoid full-amount interpretation | — |
| R08 | A stand-in approval must retain internal evidence identifying the actual decision-making party, not be labeled issuer-issued (Guide §6). Conditional, deferred by HD-03; not a v1 acceptance criterion. Caller-visible source/reason disclosure is a SIMULATOR DESIGN ASSUMPTION pending approved policy. | SOURCED §5 business outcomes; §9 D-017; §11 | Correct decision provenance | — |
| R09 | Apply duplicate-request control to avoid inconsistent outcomes for repeated business requests. | SOURCED §5 step 3; §6 | Avoid repeated financial effects | — |
| R10 | Retain transaction references, response references and evidentiary history linking request and outcome. | SOURCED §5 step 6; §6 | Enable later authorized lifecycle correlation | — |
| R11 | Make processing actions and deciding actor auditable and operationally observable internally (Guide §6); this does not prescribe caller-visible source/reason fields, which remain a SIMULATOR DESIGN ASSUMPTION pending approved disclosure policy. | SOURCED §6 | Explain actual processor behavior | — |
| R12 | Protect payment data; use synthetic credentials/test metadata only; do not expose/store clear PIN or use real PAN, CVV, PIN keys or genuine cryptograms. | SOURCED §4.2, §6; human scope | Safe simulator boundary | — |
| R13 | Authorization/issuer decision is not evidence of capture, clearing, settlement, reconciliation or payout. | SOURCED §4.2, §8 | Preserve lifecycle boundaries | — |
| R14 | Validate terminal association and preserve applicable entry-mode/CVM/EMV metadata for the working CP baseline only. | SOURCED §4.2, §5 step 3 | Correct baseline channel context | — |
| R15 | Approved replay policy: within the retention window, identical business payment data under the same authorized business key returns the latest prior known outcome and original reference (or not-confirmed if no confirmation exists), without redispatch. | APPROVED SIMULATOR POLICY HD-02 | Concrete duplicate test oracle | — |
| R16 | Approved retry policy: link a valid retry to its prior authorization; never automatically redispatch. Evaluate latest evidence: return confirmed approval/decline and original reference when available, otherwise not-confirmed and original reference; preserve uncertainty history. | APPROVED SIMULATOR POLICY HD-02 | Avoid duplicate authorizations | — |
| R17 | Business key = represented acquirer reference + merchant reference + channel payment reference; retain for 24 h (simulator default, adjustable by Human Product Lead). Same key with materially different payment data rejects as conflict without dispatch or changing original outcome. Equality compares amount, currency, credential, terminal, channel, CP and risk metadata using resolved values; exchange/prior-attempt references are not payment data. Only an unlinked request with a new channel payment reference is proposed as a new payment; any explicit prior reference takes precedence under P04–P06 in simulator-profile.md. Proposed window boundary and expiry handling are P06; expiry never authorizes redispatch of an explicitly linked retry. | APPROVED SIMULATOR POLICY HD-02; equality fields/window boundary are SIMULATOR DESIGN ASSUMPTIONS | Defines duplicate identity | — |
| R18 | Use HD-01 simulator profile: USD only, inclusive 1.00–9,999.99; domestic route or international scheme route only under acquirer permissions; synthetic credential/BIN range selects approve, decline or timeout; require entry mode, CVM indicator and cryptogram-present flag. Reject failed profile validation before dispatch. USD boundary in §1 applies in full; no Mexican currency/pricing, scheme, tariff or posting rule is imported. Exact fixture/risk values are §3 assumptions, not sourced rules. | APPROVED SIMULATOR POLICY HD-01; fixture details SIMULATOR DESIGN ASSUMPTION §3 | Defines eligible dispatch | — |
| R19 | Partial approval and network stand-in are deferred from v1 (HD-03). R07/R08 remain conditional; A10/A11 are non-v1, not acceptance criteria. Supported v1 simulated issuer outcomes are approve, decline and timeout/not-confirmed; no universal scheme rejection policy is inferred for deferred variants. | APPROVED SIMULATOR POLICY HD-03 | Defines accepted variant behavior | — |
| R20 | Configurable response timeout: simulator default 30 seconds from dispatch (adjustable by Human Product Lead); absent confirmed evidence at the boundary returns not-confirmed, never decline. Ambiguous or malformed downstream evidence returns not-confirmed immediately only if no valid correlated confirmation is recorded at evaluation; P07 specifies evidence ordering. Invalid or foreign prior reference rejects without redispatch or foreign disclosure. Late confirmation updates latest recorded result and references, retains late source evidence and earlier uncertainty history; subsequent valid retry returns latest evidence under R16, not stale unknown. No automatic reversal, inquiry or redispatch is introduced. | APPROVED SIMULATOR POLICY HD-02; 30-second value SIMULATOR DEFAULT | Prevents unsupported finality or duplicate effects | — |

Rule totals (primary classification): **14 SOURCED / 6 APPROVED SIMULATOR POLICY / 0 standalone SIMULATOR DESIGN ASSUMPTION / 0 UNRESOLVED**. Mixed qualifications: R06/R08/R11 contain pending disclosure assumptions; R17/R18 contain pending fixture/equality details; R20 contains a documented adjustable simulator default. These counts do not imply approval of those qualifications or the whole document.

Proposed deterministic exception handling, precedence, concurrent repeats and evidence evaluation are specified in [simulator-profile.md](simulator-profile.md), P04–P07. These supplement rather than override HD-02 and remain pending owner disposition. Rule totals above count primary R headings only: **8 additional proposed detail groups P01–P08**, including pending disclosure/retention policy, must not disappear behind “0 standalone UNRESOLVED” R rows.

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
| Awaiting / supported stand-in approval | Approved with network/delegate source and evidence | Approved outcome; source disclosure only under pending assumption | R08, R19; deferred/non-v1 |
| Awaiting / supported partial approval | Accepted amount plus source/evidence | Explicit partial amount and approved channel handling | R07, R19; deferred/non-v1 |
| Recorded / duplicate or retry | Preserve history; link repeat attempt | Prior result/reference or continued uncertainty based on latest evidence under HD-02 | R09–R11, R15–R17, R20 |

Internal history is not wholesale caller-visible. Guide §§5–6 sources returning outcome/references and internally recording what the processor did and which actor decided. The external column describes outcome semantics, not a mandated source/reason field. Any caller-visible source/reason fields are **SIMULATOR DESIGN ASSUMPTIONS pending approved disclosure policy**; they are not SOURCED and internal history is not disclosed by default. Preserve internal provenance as issuer, network delegate, processor rejection or no confirmed decision; transport alone is not issuer evidence. Late confirmation updates the record under R20 without erasing earlier uncertainty. No status-inquiry endpoint, reversal action or unsolicited late notification is specified.

## 7. Given/When/Then scenarios

Unless stated otherwise, given conditions use the **HD-01 USD debit / simulated CP POS baseline**, synthetic credentials, eligible acquirer/merchant/terminal and an authenticated authorized caller. Only the HD-01–HD-03 cores of R15–R20 are approved simulator policy, never scheme rules; proposed supplements P01–P08 are not approved. Profile fixture details in §3 and equality details in R17 remain SIMULATOR DESIGN ASSUMPTIONS. In all scenarios, decision source is recorded internally (SOURCED Guide §6); caller-visible source/reason fields are only the pending disclosure assumption, including A01/A03/A10.

| ID / case | Given | When | Then | Rules / provenance |
| :-- | :-- | :-- | :-- | :-- |
| A01 Issuer approval | Valid baseline purchase and permitted simulated route | Issuer approval evidence is returned | Record approved with internal issuer provenance; return approved and references; no settlement assertion | R01–R04, R10–R14; SOURCED §1, §4.2, §5, §6, §8 |
| A02 Issuer decline | Valid request dispatched | Issuer declines | Record/return issuer decline and references, not processor rejection | R04, R06, R10; SOURCED §5–§7 |
| A03 Processor rejection | Merchant is ineligible, or caller lacks authority | Request is checked | Do not dispatch; record/return processor rejection, not issuer decline | R01–R03, R06; SOURCED §1, §5–§6 |
| A04 Malformed request | Amount/currency or required credential reference is absent or structurally invalid | Validation runs | Reject before dispatch; record processor source internally; return processor rejection. Test missing credential, non-USD currency, 0.99, 10,000.00, or invalid CP metadata independently; each rejects | R02, R06, R18; SOURCED validation §5; APPROVED SIMULATOR POLICY HD-01; fixture details SIMULATOR DESIGN ASSUMPTION §3 |
| A05 Missing/unknown context | Merchant or baseline terminal cannot be resolved | Request arrives | No dispatch and no issuer decision; report processor validation rejection | R01–R03, R14; SOURCED §1, §4.2, §5; HD-01 baseline |
| A06 Wrong acquirer | Acquirer is unknown, or merchant belongs to a different acquirer | Context is checked | Reject without using another acquirer's mandate, route or credentials | R01–R03, R06; SOURCED §1, §5 |
| A07 Unknown downstream outcome | Request was dispatched | Downstream timeout or ambiguous evidence occurs | At 30 seconds from dispatch without confirmation (default), or immediately on ambiguous/malformed evidence while no valid confirmation exists, record/return not-confirmed and original reference; no approval/decline and no redispatch | R04–R05, R10–R11; SOURCED §5–§6; APPROVED SIMULATOR POLICY HD-02; default R20 |
| A08 Identical replay | Same authorized context and business key/payload already recorded | Identical request is replayed | For replay within 24 h, no new dispatch; return latest known approval/decline with original reference, or not-confirmed with original reference if confirmation is still absent | R09–R10, R15, R17; SOURCED control §6; APPROVED SIMULATOR POLICY HD-02; equality qualification R17 |
| A09 Retry after unknown | A07 exists with supplied prior reference | Channel retries | Link attempt; no redispatch, reversal or inquiry. If no confirmation exists at retry evaluation return not-confirmed and original reference; if late approval/decline now exists return that latest outcome and original reference; retain earlier uncertainty history | R05, R09–R10, R16, R20; SOURCED uncertainty §5–§6; APPROVED SIMULATOR POLICY HD-02 |
| A12 Invalid retry reference | Supplied prior reference is missing from the authorized context or belongs to another acquirer | Retry is evaluated | Reject invalid/foreign prior reference as processor rejection; no dispatch, no foreign outcome/reference disclosure and no change to original authorization | R01, R16, R20; SOURCED context §1; APPROVED SIMULATOR POLICY HD-02 |
| A13 Conflicting duplicate | Same retained business key has a different amount (e.g. 10.00 then 11.00 USD) | Repeat is evaluated | Return conflict rejection without dispatch; preserve original amount, reference and outcome; do not treat as identical replay or a new payment | R09, R17; SOURCED control §6; APPROVED SIMULATOR POLICY HD-02 |
| A14 Late evidence | Request was reported not confirmed | Later confirmed issuer evidence arrives | Update current result to the confirmed issuer approval/decline and record late evidence; preserve earlier not-confirmed history; subsequent valid retry returns this latest confirmed result and original reference without redispatch; no unsolicited notification is required | R05, R10–R11, R20; SOURCED evidence §5–§6; APPROVED SIMULATOR POLICY HD-02 |

Additional combined-input and boundary scenarios A15–A26 are in [acceptance-scenarios.md](acceptance-scenarios.md). Total: **24 v1 scenarios (A01–A09, A12–A26), 2 deferred (A10–A11)**. Proposed-detail tests are conditional acceptance oracles, not approved policy or executed QA.

### Deferred / non-v1 scenarios — not v1 acceptance criteria

| ID / case | Given | When | Then | Rules / provenance |
| :-- | :-- | :-- | :-- | :-- |
| A10 Approval via stand-in (variant) | Stand-in variant is explicitly admitted and delegated source evidence exists | Approval arrives from network acting for issuer | Record approval with internal stand-in/delegate source, not issuer-issued; return approval; source disclosure remains pending assumption | R04, R08, R19; SOURCED §5, §11, D-017; deferred/non-v1 HD-03; disclosure SIMULATOR DESIGN ASSUMPTION |
| A11 Partial approval (variant) | Partial approval is explicitly admitted with agreed channel handling | Evidence accepts less than requested | Record/return explicit accepted amount and partial result; do not claim full approval | R07, R19; SOURCED §5; deferred/non-v1 HD-03 |

A10 resolves the phrase “issuer approval with stand-in provenance”: financial approval may be on behalf of the issuer, but cannot be attributed to the issuer itself when a network delegate made the decision.
Original-authorization eligibility for reversal and query-after-ambiguity scenarios are inapplicable here because Operations B/C are excluded; their future correlation is preserved. Authentication challenge success/failure is inapplicable because CNP/3DS is excluded.

## 8. Non-functional constraints

- Synthetic/test data only; no real PAN/PIN/CVV, PIN keys, live credentials or genuine cryptograms. Protect credentials and avoid unsafe exposure/retention (Guide §4.2, §6).
- Retain auditable request/outcome correlations and decision provenance; operational evidence must explain actions and deciding actor (Guide §6).
- Authentication, acquirer scope and merchant eligibility controls apply before routing (Guide §1, §6).
- Exact audit retention, masking/disclosure policy and sensitive-field inventory are **UNRESOLVED**; Human Product Lead owns required business policy with independent security/domain input. No storage/encryption implementation is selected.
- Authorization response deadline is the adjustable simulator default in R20 (HD-02), not a guide target. Availability, scale and general latency targets remain unspecified; no production SLA is inferred.

## 9. Open decisions affecting this item

Guide §9 status is reproduced, not advanced by this draft.

| Decision | Current status | What remains blocked |
| :-- | :-- | :-- |
| D-005 Mexico reference, portable model | Proposed | Business context only; not authority for USD simulator currency or Mexican rules |
| D-006 Debit / simulated POS first | Proposed | Guide proposal unchanged; HD-01 separately approves this work-item simulator profile |
| D-007 Business-level standard neutrality | Proposed | Formal endorsement of neutrality; this draft remains neutral as instructed, selecting no standard |
| D-008 Network/scheme, domestic route, single-/dual-message profile | Open | Real scheme/profile and posting semantics remain open; HD-01 simulator routes are not real route selection |
| D-009 External interface after checking real network requirements | Open | External message mapping and conformance; not business drafting |
| D-017 Authorization variants, stand-in, account verification | Gap confirmed; staged | HD-03 defers partial approval/stand-in from v1; account verification remains excluded |

The 2026-11-16 independent review of r1 was BLOCKED. B01–B03 have human decisions and corrections are incorporated; r2 requires fresh independent review. WORK_ITEM.md records N02 source-identity reconciliation (reviewed scope record md5 7d9363bcb132b7661df96705214f1a1a); the current record includes later HD-01–HD-03 and is not claimed to have that historical digest. Architecture and implementation remain downstream gates.

## 10. Decisions taken

- B01 answered by **HD-01**, WORK_ITEM.md Human decision record, 2026-11-18: USD simulator-only baseline, amount range, named simulated routes, synthetic credential decision classes and CP evidence. Guide D-005 remains Proposed and D-008/D-009 Open.
- B02 answered by **HD-02**, same record/date: approved key, replay/conflict/retry, 24 h window, adjustable timeout default and late-evidence semantics, completed in R15–R17/R20.
- B03 answered by **HD-03**, same record/date: partial approval and stand-in deferred from v1; R07/R08 conditional, A10/A11 non-v1.

### Remaining human decisions (construction blockers, not analysis blockers)

1. **Approve the proposed executable simulator details P01–P07 in simulator-profile.md, or provide replacements?** Concrete defaults retain r2's symbolic credential ranges, precision and CP/risk values, resolved equality fields and 24 h boundary; add prior-reference-first validation, same-key/same-data linked retry, no redispatch for in-flight repeats or linked references after expiry, and confirmation-before-uncertainty evaluation. These details are proposed, not accepted by the literal “ok vamos con esa especificacion”. USD, route names and HD-01–HD-03 cores are not reopened.
2. **Approve P08's minimum disclosure/retention boundary?** Proposed: authorized outcome and transaction reference only; internal reasons/provenance and masked synthetic evidence restricted to authorized operators; retain evidence and resolvable prior links for the simulator exercise, no automatic purge or wholesale history disclosure. The approved 24 h duplicate window remains separate. Exact future retention/purge and field-level masking policy require owner/security disposition before interface construction.

No market, real scheme, protocol, architecture, production SLA or variant decision is needed to finish this business draft. Real integration and production policy are later gates, not hidden additions to v1.

## Revision 3 changes and review handoff

- Preserved exact r2 before editing; did not restart from r1. Retained R01–R14 controls, conditional variant obligations and HD-01–HD-03.
- F01: proposed explicit reference-first precedence, compatible retry identity, linked expiry handling, in-flight suppression and monotonic evidence evaluation; A15–A26 cover mixed inputs.
- F02: retained concrete r2 detail choices but indexed all pending executable groups P01–P08 separately; human disposition is still required.
- F03: narrowed new-payment and approval language; rule-heading counts do not imply approval of mixed clauses.
- Added no-route and malformed-downstream cases without introducing an API, code, protocol, storage design, reversal, inquiry or e-commerce.
- domain-review.md remains the r2 review; r3 requires fresh independent review. No reviewer finding is self-declared closed.

### Source limitations and conflicts for handoff

- Guide §5 says partial approval/stand-in follow-ups are in §12, but §12 is public references; relevant staged material is §9 D-017 and §11. No variant policy can be derived from that erroneous cross-reference.
- Guide §5 diagram calls processor rejection “Payment Authorization Declined” and records “processor decline”; §7 asks about distinguishing processor versus issuer decline. R06 preserves that distinction using §6 decision-source observability; exact external vocabulary needs review.
- Guide §11.2 suggests a broader first slice including reversals and inquiry; it expressly is proposed prioritization. Human Operation A-only scope governs.
- Guide is silent on complete input requiredness, duplicate keys/windows/equality, exact replay/retry rules, late evidence, timeout duration, exact validation/risk thresholds, partial channel behavior, proof format for stand-in, response disclosure and quantitative service targets. HD-01–HD-03 now govern the simulator policies; remaining fixture/disclosure details are explicitly assumptions or residual gaps, never sourced scheme rules.
- The operating contract's statement that the guide was missing is historical; the supplied v0.5.1 snapshot exists and governs this draft. This commission confirms attachment identity to the working v0.5.1 snapshot; no live Drive access or different revision verification is claimed.
