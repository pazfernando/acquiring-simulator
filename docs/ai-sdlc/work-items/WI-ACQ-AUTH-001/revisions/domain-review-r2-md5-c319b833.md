# Independent acquiring domain review — WI-ACQ-AUTH-001 r2

Date: 2026-11-18. Reviewer: ai-sdlc-acquiring-domain. Fresh review of the revision, not confirmation of r1.

## Revision reviewed and sources

- Requirements: /home/pazfernando/.openclaw/workspace/shared/ai-sdlc/work-items/acquiring-simulator/WI-ACQ-AUTH-001/requirements.md
- Verified md5: **bb0807235003b939c96e90c7aa178119**, 197 lines, Draft revision 2, not independently reviewed.
- Functional Guide: /home/pazfernando/.openclaw/workspace/shared/ai-sdlc/sources/acquiring_simulator_functional_guide.md, v0.5.1, working draft, verified md5 **703fd59f66a3d097147eee79141e2524**. Especially §§1–6, 7–9, 11–12.
- WORK_ITEM.md: literal Operation A-only human scope (lines 7, 15–23); Human decision record HD-01/02/03 (34–45), owner Fernando Paz, dated 2026-11-18; historical hash reconciliation (57). Decisions are accepted as recorded human evidence, not independently authenticated chat.
- Shared README.md operating contract, sources/README.md archived product scope and WORK_ITEM_TEMPLATE.md.
- Installed /home/pazfernando/.openclaw/skills/payment-behavior-review/SKILL.md.
- Preserved r1 requirements, verified md5 **3e96eed94e6626476038e1391e25c69e**, and r1 review, verified md5 **12a8eefa7fd1a049bae62fd4db226b7d**. Full textual diff inspected. Local USER.md, SOUL.md and IDENTITY.md read.

All requirements line references below refer to these exact r2 bytes. Guide references identify sections, with lines where useful.

## Verdict: CONDITIONAL

The three human policy decisions are faithfully incorporated at their core. There is no remaining need to decide currency, named simulator routes, the basic duplicate/retry policy, or variant inclusion. However, r2 is not yet a deterministic, approval-complete construction contract. Two MAJOR findings remain: overlapping retry/replay/conflict applicability, and pending executable assumptions. A MINOR provenance qualification also needs correction. These are not new scheme-rule requests.

### Prior-finding closure

| Finding | Status | Evidence and assessment |
| :-- | :-- | :-- |
| B01 | PARTIAL | Lines 22–26, 44–46, 57, 66, 94, 161–165 faithfully implement HD-01 and its USD boundary. All named route/amount/credential/CP choices are simulator profile choices, not scheme rules. D-005 stays Proposed; D-008/009 stay Open, matching Guide §9. But lines 46 and 177 explicitly require approval for precision, fixture/risk/CP-value choices that affect acceptance. Approved baseline closed; final executable validation profile remains pending (F02). |
| B02 | PARTIAL | Lines 91–96, 130–135 now specify approved key, 24 h, conflict rejection, invalid/foreign-reference rejection, configurable 30 s default and late updates/latest retry evidence. A09 no longer freezes stale unknown after A14. Yet mixed replay/retry/conflict inputs have no precedence or definition of “valid retry” (F01). |
| B03 | CLOSED | Lines 26, 83–84, 95, 137–142 implement HD-03 literally. A10/A11 survive in a deferred/non-v1 subsection. No universal partial/stand-in rejection rule is invented. Guide §5 outcomes and §11 remain conditional semantics. |
| M01 | CLOSED | Lines 82, 84, 87, 104–116, 120, 124–142, 150–152 distinguish sourced internal provenance from proposed caller-visible fields. Every Then clause inspected: none still requires a caller-visible source/reason field as SOURCED. A02/A03 returning issuer-decline versus processor-rejection semantics is not a mandatory disclosure field. A10 explicitly qualifies source disclosure. |
| N01 | CLOSED | Lines 15 and 168 factually record completed r1 BLOCKED review and require fresh review, with no absent-reviewer installation claim. |
| N02 | CLOSED | Lines 9–12 and 168 plus WORK_ITEM line 57 reconcile historical scope identity without claiming the later edited file has the old digest. Current r2 digest matches handoff and WORK_ITEM line 26. Not embedding its own digest is acceptable traceability, not a defect. Historical scope digest reconciliation is recorded evidence, not a newly recomputed historical file. |

## New findings

| ID | Severity | Location | Finding and evidence | Required correction |
| :-- | :-- | :-- | :-- | :-- |
| F01 | MAJOR | R15–R17/R20 (91–96), A08/A09/A12/A13 (131–134) | Applicability overlaps. A same-key identical replay carrying an invalid prior reference satisfies A08 (return prior outcome) and A12 (reject). A same-key changed-amount request with a real prior reference satisfies A13 (conflict) but R16/A09 never say changed data makes a retry invalid. A new channel payment reference with a valid prior reference is a new payment “not a retry” under R17, but R16 says a valid linked retry must not redispatch. “Valid retry” is not defined. Also specify whether prior links remain resolvable beyond 24 h: expiry explicitly governs only unlinked requests, while record/reference retention is unspecified. These are reachable business-contract ambiguities, not missing wire design. HD-02 approves the component policies, not an ordering that resolves these collisions. | Define retry identity/data compatibility, precedence of prior-reference validation versus replay/conflict, and linked-reference handling at/after duplicate expiry. Preserve HD-02 no-redispatch and no foreign disclosure. Add combined-input scenarios proving one outcome for each example above, including a late-confirmed result. Define an evaluation instant/order for confirmation versus timeout/ambiguity/retry so confirmed evidence is not overwritten by a later uncertainty event. No particular software synchronization mechanism is required. |
| F02 | MAJOR | §3 (45–46), R17/R18 (93–94), §10 (177) | The labels are honest, but labeling is not approval: r2 itself says fixture/risk choices “require human approval.” Symbolic ranges, one-route-per-credential, precision, CP enum values/false cryptogram admissibility, default-pass risk, equality fields and window boundary/expiry behavior determine dispatch, conflict and acceptance outcomes. HD-01/02 do not spell out those details. This is executable proposed design, not fully approved policy. | Obtain owner disposition of the enumerated pending executable assumptions (or approved replacements), record it as separate owner evidence, and align the requirements/scenarios. Do not restate them as already approved by HD-01/02. Domain review cannot supply that approval. This does not reopen USD, route names or the basic policies. |
| F03 | MINOR | R17 and counts/summary (93, 98, 120, 173) | Six primary policy headings have genuine human-policy anchors, but R17 also asserts “new channel payment reference denotes a new payment, not a retry” without qualifying it as an assumption or limiting it to unlinked requests. HD-02 defines the composite key but does not approve ignoring an explicit valid original reference. Blanket “R15–R20 apply approved simulator policy” is broader than its immediately following qualifications. | Qualify the new-reference and expiry/link interaction explicitly as proposed detail pending disposition, narrow the approval summaries to the actual approved cores, and align with F01/F02. Keep mixed classifications visible; do not imply zero pending decisions because there are zero standalone UNRESOLVED rule rows. |

F01's timeout ordering is a deterministic evidence rule, not a demand for additional payment variants. A07's “immediately on ambiguous evidence” should be conditioned on absence of confirmed evidence at the evaluation point, consistently with R15/R16/R20. An already recorded issuer confirmation must not be regressed to unknown.

## Provenance integrity and over-specification audit

| Rule | Human authority and limits |
| :-- | :-- |
| R15 | HD-02 accepts R15 as drafted and latest-evidence replay; genuine approved simulator policy. |
| R16 | HD-02 accepts R16, no automatic redispatch and late-result retry semantics; genuine approved core, validity/precedence needs F01. |
| R17 | Composite key, 24 h, conflict rejection supported by HD-02. Equality/window details explicitly assumptions. Unqualified new-reference sentence requires F03. Not every word is human-approved. |
| R18 | HD-01 supports USD/range, named simulated routes, credential decision classes and three synthetic CP indicators. Exact fixture/risk choices remain assumptions, not approved HD-01 content. |
| R19 | Direct HD-03 deferral; no unauthorized extension. |
| R20 | HD-02 explicitly permits a documented configurable simulator timeout default, invalid-reference rejection and late evidence updates. Selecting 30 seconds fits that delegated default, not a fabricated human selection of 30. Ambiguity-as-unknown also comes from Guide §5. |

Thus **14 SOURCED / 6 APPROVED SIMULATOR POLICY** is arithmetically correct for primary rule classifications, not proof of approval of every clause. No entire one of the six lacks an approved core. R17 has an insufficiently qualified extension; counts cannot erase mixed/pending content. R06/R08/R11's mixed disclosure assumptions are explicitly identified.

R17 equality fields and R18 symbolic fixture labels are useful operationalization, not universal payment behavior; the simulator labels and §1/§3 boundaries adequately prevent scheme-rule inference. Their approval status still matters (F02). R20's 30-second value is adequately labeled an adjustable simulator default; no SLA or scheme timing requirement is claimed. No need to seek a human decision merely to reauthorize the permission to select such a default.

Caller disclosure and audit/masking/retention remain explicitly pending. M01 is a provenance correction, now closed; this is not approval of any future disclosure contract. No caller-visible field beyond the authorized outcome/reference semantics should become mandatory through inference.

## Regression assessment r1 → r2

No weakening of R01–R14 found. R01–R05, R09–R10 and R12–R14 retain their substantive controls. R06/R08/R11 narrow external field assertions while preserving internal deciding-actor auditability; this is the requested M01 correction, not removal of provenance. R07/R08 gain the authorized HD-03 deferral while keeping conditional semantics.

Changes outside those rules: replacement of proposed Mexico baseline by the HD-01 simulator-only USD profile; executable fixture/risk assumptions; input-contract details; HD-02 duplicate/retry/conflict/late-evidence policy; explicit default deadline; variant relocation; factual review/hash history; residual questions and source limitations. These are visible, not silent deletions. The extra assumptions exceed the literal decision detail but are mostly honestly marked. R14's “working CP baseline” wording is stale terminology only: §1 and the scenario preamble clearly identify the approved baseline.

R15–R20 do not inherently conflict with the sourced financial principles: unknown remains unknown; issuer decline is not processor rejection; approval is not settlement; no cross-acquirer routing, automatic reversal or inquiry is added. Their internal applicability collisions are F01. A07 → A14 → A09 now correctly returns latest confirmed evidence in the simple sequential case.

Guide source conflicts are real and correctly acknowledged: §5's partial/stand-in pointer to §12 is wrong (functional material is §5/§11); processor-decline diagram wording does not make processor rejection an issuer decision (§6 identifies deciding actor); §11.2's proposed broader slice does not override Operation A-only human scope.

## Compact payment-behavior-review

Flow | Case | Expected behavior | Rule status/reference | Gap | Verdict
:-- | :-- | :-- | :-- | :-- | :--
Authorization | Success A01 | Approval, references, internal issuer evidence; not settlement | SOURCED Guide §§5–6,8; HD-01 profile | F02 fixture acceptance | CONDITIONAL
Authorization | Decline A02 | Issuer decline distinct from processor rejection | SOURCED §§5–6 | None in basic outcome | PASS
Authorization | Malformed/risk A03–A05 | Processor rejection before dispatch | SOURCED §5 controls; HD-01 plus proposed details | F02 | CONDITIONAL
Authorization | Wrong acquirer A06 | No foreign mandate/credentials/disclosure | SOURCED §§1,6 | No basic semantic gap; not security certification | PASS
Authorization | Duplicate/conflict A13 | Same key, changed data rejects; original preserved | HD-02; R17 detail assumptions | F01/F02 | CONDITIONAL
Authorization | Identical replay A08 | Latest result/reference; no redispatch | HD-02 R15 | F01/F02 | CONDITIONAL
Authorization | Timeout/ambiguity A07 | Unknown, never invented decline | SOURCED §5; HD-02 default permission | F01 event ordering | CONDITIONAL
Authorization | Retry A09 | Link, latest evidence, no redispatch | HD-02 R16/R20 | F01 validity/precedence/expiry | CONDITIONAL
Authorization | Missing/foreign prior A12 | Reject without foreign evidence or dispatch | HD-02; SOURCED tenant context §1 | F01 combined-input priority | CONDITIONAL
Authorization | Late confirmation A14 | Update current result, retain uncertainty, next valid retry sees confirmation | HD-02; SOURCED evidence §§5–6 | F01 ordering/combined inputs | CONDITIONAL
Authorization | Stand-in A10 | Actual delegate provenance, not issuer-issued | SOURCED §§5,11; HD-03 deferred | No v1 acceptance obligation | PASS (deferred)
Authorization | Partial A11 | Explicit amount and channel handling if later admitted | SOURCED §5; HD-03 deferred | No v1 acceptance obligation | PASS (deferred)
Reversal | Missing original | Separate Operation B, not refund | Human scope; Guide §5 B | Inapplicable; retry reference still A12 | PASS (inapplicable)
Status inquiry | Query after ambiguity | Separate Operation C; no network-management mapping inferred | Human scope; Guide §§3,5 C | Inapplicable | PASS (inapplicable)
CNP/3DS | Authentication outcomes | Separate from authorization; never required for this POS profile | Human scope; Guide §4 | Inapplicable | PASS (inapplicable)

## Exact conditions to change CONDITIONAL to PASS

1. Resolve F01 in requirements and combined-input acceptance scenarios: retry validity/payment-data compatibility; prior-reference versus key/replay/conflict precedence; changed-key linked requests; linked-reference behavior at/after 24 h; latest-evidence evaluation/timeout/ambiguity ordering. Preserve the already approved policies.
2. Resolve F02 through recorded Human Product Lead disposition of the specifically pending executable profile/equality/window assumptions or replacements; update affected rules and acceptance scenarios without fabricating approval.
3. Correct F03 mixed provenance/approval summaries, particularly the new-reference clause, consistently with those dispositions.
4. Submit the resulting exact revision and owner evidence for fresh independent review verifying these corrections. This review does not automatically PASS later edits.

No new scope decision on B01/B02/B03's approved cores is requested. No implementation, external protocol selection, scheme certification, reversal or inquiry endpoint is required to close these corrections.

## Residual human questions

- Approve or replace the concrete pending executable assumptions in F02? This is the remaining genuine business-policy disposition, not a repetition of HD-01/02.
- What caller-visible source/reason and audit/masking/retention policy should later govern the interface? Already identified as pending; retain internal provenance and avoid making proposed external fields mandatory until decided. This review does not make a new disclosure-field requirement.

F01 can be drafted by the analyst using conservative no-redispatch/no-disclosure rules; any material policy choice beyond HD-02 must be marked proposed and taken to its owner, not presented as existing approval.

## Limitations and instruction assessment

Review of local source snapshots and business semantics only. Drive identity is not verified; the working guide is available and its bytes match. No scheme/network/ISO/EMV/PCI conformance, architecture approval, security assessment, QA execution, implementation, release or human approval is claimed. Quantitative production targets remain outside this simulator review. Requirements, WORK_ITEM and revisions were not modified.

No instruction in the brief was judged wrong. Its suggested count of six approved policy rules is a primary-classification hypothesis to audit, not authority to approve all mixed clauses. This review uses the installed skill as requested; no new learned skill was created because the findings are codebase/work-item knowledge.
