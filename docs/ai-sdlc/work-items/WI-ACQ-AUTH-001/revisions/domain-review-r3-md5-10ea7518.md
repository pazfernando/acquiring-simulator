# Independent acquiring-domain review — Operation A r3

Date: 2026-11-18. Reviewer: ai-sdlc-acquiring-domain. Fresh local-file payment-domain review, not confirmation of the previous verdict.

## Verdict: CONDITIONAL

The business draft is substantially improved and within the authorized single simulated POS purchase scope. The analyst has resolved the previously missing precedence and evidence-ordering definitions as explicit proposals. Those proposals are not human approvals. One MAJOR construction-policy finding remains (F02); one new MINOR acceptance-oracle correction is required (F04). Outstanding unique findings: **0 critical / 1 major / 1 minor**. B01/B02 are umbrella partial closures, not additional severity counts.

No architecture or construction approval is given. Draft analysis can continue; unapproved executable policy cannot be treated as a construction contract.

## Evidence identities and authority

All hashes below were computed locally from the reviewed files (MD5):

| Artifact | Verified identity |
| :-- | :-- |
| requirements.md r3 | b0c97974197a20dff1b72d38164d0e2b |
| simulator-profile.md | 5483a4a378c0d1101391c11a628d2ae6 |
| acceptance-scenarios.md | a8a6d6522b34c77d727ce77ac870fa23 |
| Functional Guide v0.5.1 working draft | 703fd59f66a3d097147eee79141e2524 |
| revisions/requirements-r1-md5-3e96eed9.md | 3e96eed94e6626476038e1391e25c69e |
| revisions/requirements-r2-md5-bb080723.md | bb0807235003b939c96e90c7aa178119 |
| revisions/domain-review-r2-md5-c319b833.md | c319b8335938289958c93f983b5dea3a |
| WORK_ITEM.md observed | **9bbcc1c8180aea65993973c125e3907a**, not commissioned 629d541e0a5b42811da5fedd6f0e6e6d |

Root: /home/pazfernando/.openclaw/workspace/shared/ai-sdlc/work-items/acquiring-simulator/WI-ACQ-AUTH-001/.
Guide: /home/pazfernando/.openclaw/workspace/shared/ai-sdlc/sources/acquiring_simulator_functional_guide.md.

The scope-record identity mismatch is explicit: its current commission entry identifies 629d541e… as the pre-progress-entry snapshot and records review in progress. The exact commissioned old bytes are not available as a verified snapshot here; do not claim that digest was verified. The current record preserves HD-01–HD-03 and literal scope, consistent with this task. The mismatch does not invalidate the three matching specification artifacts, nor establish any new human approval. Coordinator should use the observed identity in the handoff and retain historical claims as historical. The record's earlier r2 metadata is historical, not the identity of r3.

Read local USER.md, SOUL.md, IDENTITY.md, shared README.md operating contract, WORK_ITEM_TEMPLATE.md, archived sources/README.md, the installed /home/pazfernando/.openclaw/skills/payment-behavior-review/SKILL.md, current artifacts, both preserved requirements revisions and previous review. Compared r1→r2 and r2→r3 changes; inspected all A01–A26 oracles. Guide §§1,4.2,5–9,11 govern the applicable semantics; §4 supplies CNP separation. Guide references are evidence, not universal network rules.

Authority: literal “ok vamos con esa especificacion” authorizes preparation/review of one simulated POS purchase, its inputs/controls, issuer approval/decline, processor rejection, unknown, duplicates, timeout and late responses. HD-01–HD-03 are accepted as recorded human evidence, not independently authenticated chat. Reversal, inquiry and e-commerce stay excluded. USD is simulator-only: no Mexican currency/pricing, scheme, tariff or posting rule. No message family selected.

## Every prior finding

| Finding | Status | Independent assessment |
| :-- | :-- | :-- |
| B01 | PARTIAL | Approved baseline preserved in requirements §§1,3–4 and R18: USD range, named simulated routes, synthetic credential decision classes and three CP indicators. D-005 remains Proposed; D-008/009 Open. P01/P02 exact fixture/validation choices remain pending F02. No need to reopen HD-01. |
| B02 | PARTIAL | HD-02 core preserved in R15–R17/R20. P04–P07 now define the missing mixed-input precedence, compatible retry, expiry and evidence order; A18–A25 support them. Draft-definition defect F01 closes, but these supplements still require owner disposition under F02. |
| B03 | CLOSED | HD-03 preserved: R07/R08 conditional; R19 and A10/A11 expressly deferred/non-v1. No blanket scheme rule rejecting partial or stand-in invented. |
| M01 | CLOSED | R06/R08/R11, §§6–8 and P08 preserve internal deciding-actor evidence without claiming caller-visible reasons/source as sourced mandatory fields. Outcome distinction is not a required disclosure field. |
| N01 | CLOSED | Opening/history records completed reviews rather than missing-reviewer installation. §9's retained “r2 requires fresh independent review” is stale historical wording; r3 header and commission clearly establish current review. It is not an approval claim. |
| N02 | CLOSED | Requirements/source/snapshot identities match. Historical hashes are not represented as current-file hashes. Current WORK_ITEM mismatch is disclosed above, not silently certified. Attachment identity is supplied by the commission; no live Drive equivalence verified. |
| F01 (MAJOR in r2) | CLOSED (draft correction) | P04 gives prior-reference validation priority after current authority/mandatory validation; valid retry requires same key and resolved data. P05 suppresses in-flight/concurrent redispatch. P06 distinguishes unlinked expiry from linked retry; unavailable links reject. P07 makes recorded correlated confirmation dominate uncertainty, prevents regression and preserves contradictory later evidence internally. R17/R20 and A07 align. A18–A25 cover the former collisions. Closure means a concrete reviewable definition, not approval of the proposed policy; approval belongs to F02. |
| F02 (MAJOR) | NOT CLOSED | P01–P08 and requirements §10 are explicitly proposed/pending; no separate human disposition supplied. Useful indexing does not approve fixture labels/mapping, precision/enums/risk/defaults/requiredness, resolved equality, window boundary, precedence/concurrency or disclosure/retention. The old approval gap remains, with its full explicit proposal set now visible. |
| F03 (MINOR in r2) | CLOSED | R17 limits new-payment language to unlinked requests and marks expiry/link supplements proposed. §7 limits approval to HD cores. §5 exposes eight proposal groups alongside the primary counts; zero standalone UNRESOLVED rows no longer hides pending policy. |

## New finding and exact correction

**F04 — MINOR, acceptance-scenarios.md A19 versus P04/P02 and requirements R18/A04.**

A19 demands conflict rejection for an independently changed currency in an otherwise valid linked retry. The only valid currency is USD; any actual currency change fails mandatory profile validation before link/equality evaluation under P04. Thus its unconditional conflict oracle disagrees with its own specified precedence. An unknown credential, invalid CP value or invalid amount used as the “changed” field can have the same problem. Zero dispatch/no original mutation remain correct; the disagreement is the business rejection category, not an invented financial outcome.

Correction: split A19 parameter cases:
- For otherwise profile-valid changed amount/credential/terminal/channel/CP/risk data, require conflict rejection after successful mandatory validation and authorized link resolution.
- For non-USD currency or other invalid/missing required profile values, require processor validation rejection before dispatch, preserving original data/outcome and withholding replay evidence.
- State that link-conflict cases assume valid alternative values and unchanged authorized access. A channel change outside POS is a profile failure, not a compatible v1 alternative.
Keep A18's invalid-reference precedence for requests that pass mandatory validation. No new API/error code or field-level disclosure is required.

This is an analyst correction, not a new human scope question.

## Per-rule provenance/count audit

| Rules | Verified basis and qualification |
| :-- | :-- |
| R01 | Guide §§1,6: mandate/merchant isolation; no cross-acquirer credentials. |
| R02 | Guide §5 steps 2–3 / §6: authority, eligibility, validation and risk before dispatch; thresholds not supplied by guide. |
| R03 | Guide §5 steps 4–5: authorized acquirer route; P01's exact mapping proposed. |
| R04 | Guide §5 outcomes/step 6: evidence-based decision and recording; processor rejection separately R06. |
| R05 | Guide §5 step 7: ambiguity/timeout not issuer decline. |
| R06 | Guide §§5–7 plus §6 deciding-actor evidence: distinguish processor from issuer; external source/reason fields pending. |
| R07 | Guide §5 partial accepted amount/channel handling; HD-03 defers application. |
| R08 | Guide §§5,6,11: actual delegate evidence, not issuer attribution; HD-03 defers, external disclosure pending. |
| R09 | Guide §§5–6 duplicate controls; not a sourced exact algorithm. |
| R10 | Guide §§5–6 references/evidence history. |
| R11 | Guide §6 internal auditability; not wholesale caller history disclosure. |
| R12 | Guide §§4.2,6 and human synthetic-only scope; no real secrets/credentials. |
| R13 | Guide §§4.2,8: authorization is not settlement or payout. |
| R14 | Guide §§4.2,5: terminal/CP context preserved; exact synthetic values proposed. |
| R15 | HD-02 approved replay/latest evidence/no redispatch core; P04/P05 applicability proposed. |
| R16 | HD-02 linked retry/latest evidence/no automatic redispatch core; compatible retry/ordering proposed. |
| R17 | HD-02 composite key, 24 h, conflict core; P03 equality and P06 boundary/expiry semantics not approved. |
| R18 | HD-01 USD/range, named routes, decision classes and CP indicators; P01/P02 exact fixtures/validation/risk not approved. |
| R19 | HD-03 deferral; no universal scheme behavior. |
| R20 | HD-02 adjustable deadline, invalid/foreign links and late evidence core; Guide §5 uncertainty. 30 s is a documented delegated simulator default, not a falsely human-selected duration. P07 evaluation/conflict details proposed. |

Arithmetic is correct: 20 R rows = 14 primarily SOURCED + 6 primarily APPROVED SIMULATOR POLICY; zero standalone assumption/unresolved rows is a table-classification statement only. Eight P groups contain many individual proposed choices, not eight approved rules. R07/R08 remain among the 14 although deferred. Mixed disclosure, equality, fixture and evidence-order qualifications preclude interpreting 14/6 as universal approval.

## All-scenario inspection

| Cases | Review result / gap |
| :-- | :-- |
| A01–A02 | Correct issuer approval/decline with references and internal source; not settlement or processor rejection. Fixture selection conditional F02. |
| A03–A06 | Correct pre-dispatch rejection and acquirer/merchant/terminal controls. Exact profile checks conditional F02; no source/reason-field mandate introduced. |
| A07 | Correct unknown absent valid confirmation; P07 now protects already confirmed evidence. |
| A08–A09 | Latest-evidence replay/retry with original reference and no redispatch, read under P04 precedence and P06 window. |
| A10–A11 | Correctly deferred, preserve delegate provenance/accepted partial amount if later scoped. Not v1 tests. |
| A12–A14 | Invalid/foreign link rejection, conflicting duplicate preservation and late evidence/latest retry consistent with HD-02. |
| A15 | No permitted route is processor rejection, never issuer decline/unknown; no foreign fallback. |
| A16–A17 | Concurrent/in-flight suppression concrete and conservative; pending P05 disposition. |
| A18 | Invalid-link-before-replay disclosure closes old overlap for mandatory-valid requests. |
| A19 | F04 correction required: separate invalid profile from valid-data conflict. |
| A20 | Changed key plus valid link rejects as incompatible retry, no new dispatch; proposed P04/P06. |
| A21 | Linked retry never becomes new through window expiry; unavailable link rejects, no disclosure; P06/P08 pending. |
| A22 | Unlinked before/exact/after boundary is explicit and conditional; post-expiry dispatch is a proposed new purchase, not evidence that prior payment ceased. Preserve separate history. |
| A23 | Malformed/wrong-correlation is not issuer decision. Retain evidence without attaching it to foreign transaction; read with P07/A24 confirmation precedence. |
| A24 | Confirmation before evaluation, including deadline, prevents regression. |
| A25 | Late-confirmed replay, changed amount and foreign link outcomes align with precedence when changed amount remains profile-valid. |
| A26 | Boundary amounts, unknown credential, precision/CP/risk checks and false synthetic flag admissibility concretely testable but proposed, not EMV conformance. |

Count independently checked: A01–A09 = 9, A12–A26 = 15; **24 v1 + 2 deferred = 26 scenario rows**. Canonical file has 12 v1 and 2 deferred; supplement adds 12 v1. Parameter variants are not counted tests and none were executed. An explicit concurrent-conflict variant and incompatible-confirmation variant would improve later QA derivation from P05/P07, but their rules are already stated; no extra business scope or architecture is demanded.

## Regression and source conflicts

r1→r2 preserves R01–R14 substantive mandate/routing, eligibility, credential protection, uncertainty, correlation, provenance and lifecycle controls. External disclosure assertions were narrowed while internal deciding actor remained; this repairs provenance rather than weakening auditability. HD-01 replaces an unapproved Mexican-market assumption with simulator-only USD; HD-02 completes duplicate/late policy; HD-03 defers variants without deleting their conditional semantics.

r2→r3 leaves R01–R16, R18/R19 and substantive original scenarios intact, refining R17/R20 and A07 rather than removing controls. P04–P08 add no-disclosure/no-redispatch/evidence safeguards. No reversal, inquiry, CNP/3DS, actual scheme integration, ISO family, storage architecture or production SLA is smuggled into acceptance. The guide's §12 variant pointer is erroneous; §5/§11 are relevant. Its processor-“decline” diagram does not erase deciding-actor distinction. Its broader proposed slice cannot override literal Operation A scope.

## Exact route to PASS and blocking human questions

1. Analyst corrects F04's A19 oracle as above; align any affected cross-references. No human approval needed merely to repair inconsistent draft wording.
2. Fernando records explicit disposition of **P01–P07**, or concrete replacements: fixture ranges/route mapping, precision/no rounding, CP enums and false flag, risk/default, exchange-reference requiredness, resolved equality, current validation/link precedence, concurrency/in-flight behavior, exact expiry handling and confirmation ordering/conflict preservation. Do not reauthorize HD-01–HD-03 cores or misrepresent 30 s as a new mandatory human selection.
3. Fernando records disposition of **P08** for this synthetic exercise: authorized outcome/reference only versus additional disclosure, operator restriction/masking and evidence/link retention duration or exercise-end rule. The 24 h duplicate window is not audit retention. Exact field-level masks/access/purge policy remains an interface/security gate; no production retention regime is required merely to finish this draft.
4. Align requirements/profile/scenarios to those decisions and submit exact identities plus owner evidence for renewed independent review. No automatic PASS of future edits.

Blocking human questions are exactly the two dispositions above: approve/replace P01–P07; approve/replace P08's exercise boundary. Explicit proposals and the broad “ok vamos…” scope statement do not answer them. No real market/scheme/protocol choice, reversal/inquiry support, production performance target or construction authorization is required to resolve this payment-domain draft. Even a future domain PASS does not satisfy the separate human architecture, independent QA/security and acceptance gates.

## Limitations

Local-file review only. Configured MCP is incomplete; no MCP-backed verification performed. No executed QA, security review, scheme/network/ISO/EMV/PCI conformance, live Drive verification, implementation, architecture or construction approval claimed. Only domain-review.md was written; requirements, profile, scenarios, WORK_ITEM and immutable snapshots were not modified.
