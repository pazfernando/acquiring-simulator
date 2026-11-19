# WI-ACQ-OBS-001 — Independent specification re-review

Date: 2026-11-18 (America/Bogota). Reviewer: Acquiring Domain Reviewer.
Method: payment-behavior-review skill; local source, contract, historical-diff and byte-offset inspection. Specification only.

## Verdict and affected gates

**CONDITIONAL — research-selection adequacy and continued specification.** The pinned public packager is an adequate structural reference for a deliberately private authorization profile, not a scheme specification. The owner is not required to design fields.

**BLOCKED — decision-ready adoption of the exact D1 bundle.** Positive vectors violate its fixed-width contract, and accepted request capacity does not guarantee a representable response. Correct these internally and re-review revised hashes before asking for adoption of this exact candidate.

**BLOCKED — construction.** In addition to correctable specification findings below, D1–D3, relevant existing pending A details, human architecture disposition and triggered independent Security review remain unapproved. Independent QA belongs to its later gate and has not executed these cases. No code, deployment or approval is authorized by this report.

**D1 alone can be the next owner question, but not yet as adoption of the current “complete” bundle.** After corrections/re-review, present only D1; leave D2 and D3 for subsequent separate decisions. No owner question is needed to fix a 16-byte alias in a 15-byte field or reserve response capacity.

Severity counts: **4 HIGH, 3 MEDIUM, 0 LOW** new findings (N1–N7). These counts exclude outstanding human decisions and external gate prerequisites.

## Exact reviewed input identities

All supplied MD5 identities independently recomputed and matched:

| Relative artifact | MD5 |
| --- | --- |
| requirements.md | 244c3d766ef55f5bb5dfd356ce15b6d1 |
| acceptance-scenarios.md | c110e897df8d9a0d73c5b40a85ef1854 |
| iso8583-profile.md | eb8e68102a32c94c839d0c0dbd1df756 |
| channel-contract.md | b9c57f527f54d2e0828f9cef31789395 |
| observation-contract.md | 7893ae8b215e9d5947b74e6e03cc9ae0 |
| decision-proposal.md | ce728b11dc5b53cfa8e50e85ecd4424f |
| vectors/README.md | ac14c2ee9741457e8295c993ea088a8d |
| vectors/packed-vectors.json | 85eed31381751761ac29843c257a9f3e |
| vectors/state-vectors.json | 448482dccf0ef1ef837a9d2b8532fdd0 |
| vectors/no-response.bin | d41d8cd98f00b204e9800998ecf8427e |
| iso8583-reference-research.md | 7e27138d8d3016f33e55dc2b90b12d07 |

Prior review: revisions/domain-review-r1-md5-c49694b00907371de7f8fd16e3885f9f.md. Scope: revisions/owner-scope-2026-11-18.md. Compared current requirements with revisions/requirements-r1-md5-2ac753ee.md; inspected current AUTH-001 requirements R15–R20, simulator-profile P01–P08 and aws-architecture-update.md, plus functional guide v0.5.2 §§1/3/13 and the local research manifest/snapshots.

All three research snapshot lengths/SHA256 matched their manifest. All 47 packed-vector lengths/SHA256 matched their hex. Those checks establish identity, not codec correctness. Independent inline documentation arithmetic walked the bitmap/field offsets; no production or test codec was installed, no repository code was changed, and no QA/test pass is claimed. Configured MCP is incomplete under runtime policy: MCP-backed corroboration is unavailable; this is a local evidence review, not independent remote provenance verification or ISO conformance certification.

## Original F1–F6 disposition

| Finding | Disposition | Evidence and remaining correction |
| --- | --- | --- |
| F1 scope citation | CLOSED | O now points at immutable owner scope and separates evolving receipts; historical reviewed drafts preserved. |
| F2 complete wire/channel contract | PARTIAL | P1–P4/C1–C2 now specify concrete matrices, alphabets, transport and UTC; N1/N2 prevent a consistent exact candidate. |
| F3 identity/DE48/aliases | PARTIAL | Durable aliases, full refs, generations, prior precedence and no redispatch specified; N1/N2/N3/N5 remain. |
| F4 actor/outcome mapping | PARTIAL | P4 correctly separates actor, dispatch certainty and private DE39 mapping; N4/N7 prevent complete acceptance evidence. |
| F5 failure/access contract | PARTIAL | O1–O4 now give explicit proposed admission, journal/trace, authorization, retention and recovery policies; N3/N6 remain; D2/D3 are not approved. |
| F6 exact acceptance vectors | NOT CLOSED | Inventory supplied, but positives are malformed and negatives cannot reliably isolate their intended failure. N1/N7 must be corrected. |

CLOSED is closure of the cited specification defect only, not human approval or construction permission.

## Correctable findings

### N1 — HIGH: fixed-width alias invalidates positive packets and masks negative meanings
Affected gate: D1 decision-ready bundle and vector acceptance (F2/F3/F6).

P1 requires DE42 exactly 15 uppercase alphanumeric bytes. P3, README and logicalFields use MERCHANT00000001, which is **16** bytes. The packed authoring bytes include all 16 without rejection. All V01–V10 inherit this defect.

Concrete V01 zero-based byte offsets: DE41 starts at 74 and consumes TERM0001; DE42 starts at 82 and must consume 15 bytes, yielding MERCHANT0000000. At offset 97 the decoder sees DE48 prefix **118**, not intended **188**; DE48 starts with **8v=1**, and DE49 becomes **fix** at offset 218 with trailing bytes remaining (packet length 292). Thus it cannot decode to the advertised fields or reach an issuer approval. Correct length/SHA256 does not fix this.

Correction: provision a genuine 15-byte merchant alias consistently in P3, README, every logical/hex vector and fixture inverse mapping. Regenerate exact byte identities, then independently check bitmap, fixed widths, length prefixes, end-of-body and expected decoded fields against P1. Re-evaluate every negative case after repairing the common baseline. B-DE48-exact-999 is not currently parser-valid either. Do not truncate the business merchant reference; change the synthetic alias allocation.

### N2 — HIGH: request DE48 capacity does not reserve mandatory response capacity
Affected gate: D1 decision readiness (F2/F3).

P2 permits request extension up to 999 bytes and requires response to echo every request key plus six mandatory actor/stage/result/reason/evidence/at keys within the same 999-byte ceiling. An otherwise accepted 999-byte request cannot produce any valid response. V01→V02 adds 102 extension bytes even with EVID-001. Choosing a longer legal evidence ID or rejection reason needs additional space. Discovering this only after issuer dispatch converts avoidable mapping failure into financial uncertainty.

Correction: define and enforce before claim/dispatch a deterministic request budget reserving the maximum supported response suffix (including the full legal evidence-ID bound and every P4 outcome), or explicitly redesign the echo contract. Distinguish structural 999-byte parsing from admissible exchange size. Supply largest admissible request→response and one-byte-over admission rejection pairs for each relevant suffix boundary. Update C1, P1/P2, README and B boundary expectations together; never drop refs or truncate a reply.

### N3 — HIGH: reserved-but-unsent retry state is not fully contracted
Affected gate: D2 failure-policy specification and identity preservation (F3/F5).

O3 commits claim/dispatch intent, then trace admission, then sends. If trace admission fails, the business key remains reserved but undispatched; identical retries only read it. P3 otherwise describes UNKNOWN/in-flight replay; C1 defines 202 in-flight duplicates and 200 determined replay but not this permanently unsent reservation. A crash between intent and invocation also differs from positive evidence of no invocation. No fixed case covers a failed trace admission after successful claim followed by identical retry, restart, and window expiry.

Correction: explicitly distinguish durable CLAIMED_NOT_SENT, POSSIBLE_SEND and confirmed business evidence (names may differ). Define safe returned outcome/null, disposition, HTTP status, original ID and attempt link for each on initial submit, replay and restart. Do not assert UNKNOWN issuer activity for positively unsent work or NOT_DISPATCHED after a crash without proof. Declare whether unsent reservations expire into a new unlinked generation under P06 and the operator warning needed; linked retry must never become new. Preserve fail-closed admission and no automatic redispatch. Add ordered cases at each crash boundary, including journal commit succeeding while trace admission fails. These are team specification corrections before D2, not a request for the owner to design state fields.

### N4 — HIGH: response evidence authority is asserted but its seam is not fully specified
Affected gate: evidence/provenance construction specification (F4).

P4 requires evidence IDs to resolve to correlated durable processor-received evidence from an authenticated configured actor; C2 names an authenticated service boundary but defers a separately approved late-evidence seam. The exact record/validation contract linking a relaying NETWORK principal to an ISSUER decision is absent. A valid service signature alone cannot attest a decision by another actor. The separate issuer table must remain inaccessible to the processor and cannot serve as receipt evidence. Volatile reply handling under O3 also needs a clear order relative to this durable-record prerequisite.

Correction: specify the trusted response/evidence contract independently of trace events: permitted producer/relay identities, who attests issuer decisions, exact bound instruction/context/actor/stage/result/reference/time fields, immutable evidence-ID scope/conflict handling, and journal admission order. Define normal and late evidence ingestion through authorized internal handling without commissioning a public endpoint or automatic worker. Include forged actor, missing/unresolvable evidence, authorized relay of issuer evidence and issuer-truth-only cases. Name remaining IAM/mTLS realization as architecture/Security work, not an already satisfied domain evidence contract.

### N5 — MEDIUM: replay resolution order needs a registry-change oracle
Affected gate: identity/channel specification (F3).

C1 says resolve defaults/fixture permissions before equality; P3 says registry changes cannot re-evaluate an already claimed instruction. Both are reasonable only with explicit distinction between current caller/context eligibility and immutable stored instruction data. Otherwise a fixture scenario or route change could reselect an old outcome or deny a legitimate replay for a reason different from current access revocation.

Correction: define current access/mandatory validation first, then authorized key/prior lookup, then comparison against the persisted resolved instruction. State exactly which current fixture eligibility checks can reject replay without modifying its original outcome, and which values remain pinned. Add changed registry/default/route and revoked-context cases. Explicitly require NOT_CONFIRMED for an admitted in-flight replay per AUTH-001 P05, rather than leaving C1's pending outcome implicit.

### N6 — MEDIUM: observation transport vocabulary and canonical event identity conflict
Affected gate: D3 observation specification (F5).

C1 freshness permits CURRENT/STALE, whereas O2 adds DISCONNECTED; O3 says expose UNKNOWN freshness if stores fail. No discriminator explains server freshness versus browser connection state. O2 compares canonical content excluding collector fields, but O1 has both producer-supplied and collector-replaced context/collectedAt without an exact canonical field set.

Correction: publish separate server freshness and client connection vocabularies, define all-store-failure response behavior, and give an explicit dedupe canonical field list and producer namespace/restart rule. Show same producer event with changed collection time as exact replay, forged context as rejection, and changed immutable content as conflict. Preserve original producer identity; collector authorization must not silently reassign an event to another business context.

### N7 — MEDIUM: coverage inventory overstates fixed, isolated acceptance oracles
Affected gate: F4/F6 specification completeness; not a claim of failed QA.

The 97 inventory count is arithmetically correct but does not mean 97 independently usable vectors. README conflict case says “V08-shaped opposite decision” without exact bytes/extension/evidence. Valid relay, PROCESSOR/BEFORE_NETWORK/30, PROCESSOR/POSSIBLE_ISSUER/91, DISALLOWED and UNAVAILABLE uncertain variants have no fixed positive matrix cases. Missing-DE38 with asserted reference is prose rather than a separately pinned evidence fixture. S20 applies live collection→display objectives to recovery of retained events without distinguishing initial backlog age.

Correction: after N1, supply exact trusted evidence fixtures per case (reset/scoped IDs versus shared sequence), fixed conflicting confirmation bytes and state steps, required positive/negative actor/stage combinations, and mismatch amount/DE7/date/freshness/DE38 vectors. Include leap/invalid-date, out-of-order/empty canonical keys, exact amount JSON conversion and response-capacity cases. Negative expected layer must be the first violated rule after a valid common baseline; do not call every downstream failure a parser rejection. Define live freshness measurement separately from reconnect recovery/backlog age. These remain future executable QA inputs, not tests performed now.

## Rule provenance and non-weakening assessment

Recounted all numbered rows: **R01–R10 SOURCED; R11–R25 DESIGN ASSUMPTION: 10 + 15 = 25**. Rule-by-rule source fit:

- R01–R03: literal owner console/live/history/common-hook/ACL scope; no future-operation implementation mandate.
- R04: current AWS update explicitly establishes JSON baseline.
- R05: guide §5/§13 and AWS outcome/uncertainty distinction.
- R06: existing AUTH-001 HD-02 no redispatch and duplicate key policy; guide PROC-01 supports business concept.
- R07: guide NET-04/05 and ISS-04/05 plus AWS late-evidence constraints; issuer truth alone never confirms receipt.
- R08: guide §1/§4.2 plus AWS active context registry.
- R09: guide data boundaries plus synthetic-only scope.
- R10: guide §13 and current AWS separate network/issuer/evidence domains.
- R11–R18: proposed identity/event/timing/completeness/dedupe/history mechanics, not prescribed by public ISO material.
- R19–R20: proposed concrete access/trace policy derived from sourced isolation/data boundaries, not falsely labeled scheme rules.
- R21–R22: proposed ACL validation/translation design constrained by R05–R10.
- R23: private profile/channel proposal, not adopted external standard.
- R24: proposed D2 durability policy.
- R25: proposed D3 architecture/operational choices.

SOURCED counts are primary row classifications, not proof every subclause was explicitly dictated by a source. No rule row is now labeled UNRESOLVED, but that does **not** establish “zero field-design gaps”; N1–N7 demonstrate residual specification defects. Correct that claim in requirements and decision-proposal.

Compared with archived requirements, R01–R22 preserve the substantive constraints. R23–R25 replace open design obligations with proposals, not human approvals. Short polling can satisfy no-reload live updates if measured; it does not weaken scope merely by avoiding sockets. Seven-day trace expiry is proposed and remains separate from retained business links. Existing A approved USD/range, key/window/no-redispatch, four outcomes and deferred stand-in/partial approval are not superseded. Existing P01–P08 proposed supplements must not acquire approval by incorporation.

Research's earlier space-padding recommendation differs from P1's exact aliases/no implicit padding; treat P1 as the explicit stricter candidate and clarify precedence in D1. Neither default runtime padding nor mixed pyiso8583 catalogs may override it. Private DE39 00/05/30/91 values remain assumptions; no universal ISO, Mastercard or domestic-network semantics are certified.

## Handoff and bounded decision order

1. Team corrects N1–N7 and returns exact revised contract/vector hashes for independent re-review. Do not ask the owner to repair packet fields.
2. If corrected D1 is decision-ready, ask **only**: “Adopt the revised private authorization-only SIM-ISO87A-v1 profile and documented HTTPS channel boundary?” Identify the revised bundle. Answer still unapproved today.
3. D2 admission/durable-evidence policy and D3 access/retention/observation architecture follow one at a time. No construction follows D1 alone.

Reversal is not refund or guaranteed hold release; inquiry/history is not network management or issuer truth. No B/C, CNP/3DS, stand-in or partial approval implementation is required by this review. Common hooks apply to all present/future implemented capabilities, including auxiliary work without forced financial ISO mapping.

Only domain-review.md authored. No requirements/scenarios/research/scope/code edits, commits, approvals, external sends, publication or deployment. Security observations are advisory and do not replace independent Security review.
