# WI-ACQ-OBS-001 — Independent corrected-candidate re-review

Date: 2026-11-18, America/Bogota. Reviewer: Acquiring Domain Reviewer.
Execution model: GPT-6 as identified by this execution's system context; configured default used, no override requested. Exact provider model identifier was not exposed in the reviewed tool results.
Method: payment-behavior-review skill; independent local contract, historical-diff, fixture and byte arithmetic inspection.

## Verdict and precise gates

**CONDITIONAL — D1 is now decision-ready as a private profile/channel selection. YES: D1 alone may be the next owner question.** The previously blocking malformed positive packets and unbounded response expansion are corrected. The owner can meaningfully adopt or reject this proposed authorization-only baseline without designing technical fields.

This is NOT an unconditional PASS for a complete executable interface specification. Three medium construction-detail findings remain below. D1 must be described as selection of the documented private profile and HTTPS boundary, not acceptance of production readiness, interoperability, cryptographic verification, or the complete console architecture. Do not describe this report as confirming all fixtures through independent QA.

**BLOCKED — construction.** D1 is still unapproved; D2 and D3 are separate subsequent decisions, relevant inherited A supplements remain proposed, and human architecture approval and triggered independent Security review are absent. Correct C1–C3 findings before the affected interfaces are constructed. Independent QA is unavailable and unexecuted at its downstream gate. Neither this review nor a future D1 “yes” approves D2/D3 by incorporation.

Severity: **0 HIGH / 3 MEDIUM / 0 LOW** current findings. This counts residual defects, not pending human decisions or unavailable mandatory reviewers.

## Reviewed identities

Independently recomputed all supplied MD5 values; all matched:

| Artifact relative to this directory | MD5 |
| --- | --- |
| requirements.md | f10cf394de8a78b9a5e87b633f62f59b |
| acceptance-scenarios.md | 1bc27f21d66148472c889bd66dbf792f |
| iso8583-profile.md | 30137e38553cce26b16e6d26bfa8d02c |
| channel-contract.md | bf8f7fa281b4849f727bbc41466b6d64 |
| observation-contract.md | a59b938d36f57a54d55dd7a157cab527 |
| decision-proposal.md | c7cae7b0791e76706966f65812389fdc |
| vectors/README.md | febcbea54442b61d70797d4eb7aad9da |
| vectors/packed-vectors.json | e0a111ab43dd54377ca68a52a99b4a97 |
| vectors/evidence-fixtures.json | f3d94d5dbcb2fb5f4299c4ddaa6713f2 |
| vectors/state-vectors.json | a1f0fdb7ff70fe0887ba5004d7868bdc |
| vectors/channel-vectors.json | 9c148fec0100ab3927e8c8e73d899102 |
| vectors/event-vectors.json | 4490b56092524c2e1d0ad8def4236d59 |
| vectors/documentation-validation.json | eb507f3d406afdf605d34466edef4336 |
| vectors/no-response.bin | d41d8cd98f00b204e9800998ecf8427e |
| iso8583-reference-research.md | 7e27138d8d3016f33e55dc2b90b12d07 |

Preserved prior review revisions/domain-review-md5-a65b92f91c36a51484ee91f7f009f44a.md independently matched a65b92f91c36a51484ee91f7f009f44a. Immutable scope matched 67f9979245fba660bc3603de43b2fe99. Current AUTH-001/aws-architecture-update.md matched 6c8e1aa03ace717a3349ee74235fecaa.

Authority remains immutable owner scope, existing AUTH-001 HD-01–HD-03 business cores and pending P01–P08 detail status, the current AWS delta, and functional guide v0.5.2. The local research manifest's three snapshot lengths/SHA256 independently matched. Source selection remains adequate for a private structural baseline; neither public packager nor sample dictionaries establish scheme business rules. Configured MCP is incomplete under runtime policy: MCP-backed corroboration is unavailable, not successful. No fresh remote provenance, normative ISO text or cryptographic implementation was verified.

## Independent fixture assessment

I did not merely adopt documentation-validation.json's result. Separate inline arithmetic read authoritative hex, primary bitmap and ascending field widths/prefixes.

- All **85** packet lengths/SHA256 matched their bytes.
- All **31** declared positives independently walked to their complete expectedDecodedFields and exact end-of-body, with no field mismatch. This includes the revised 15-byte alias, year/leap cases and capacity responses.
- V01 now has 291 bytes: DE42 starts at zero-based offset 82 and ends at 97; DE48 prefix at 97 is 188; its value occupies 100–287; DE49 is 840 at 288–290. The previous shifted-prefix defect is gone.
- Maximum suffix is independently **173 bytes**. Request 826 plus worst PROCESSOR/BEFORE_NETWORK/PROCESSOR_REJECTED/DISALLOWED/64-byte-evidence suffix yields 999. The seven capacity-response extension lengths are 984, 994, 999, 996, 997, 996, 994. All fit. 827 and structural 999 requests are explicitly inadmissible before claim/send.
- Reviewed negative first-failure meanings against P6: forbidden DE2/55, missing DE3, bitmap/secondary/MTI, truncation/trailing/non-ASCII, canonical key/order/length errors, amount/currency/date/DE7/freshness/capacity, actor matrix, reference/instruction mismatch, missing proof and contradictory confirmation. The four-digit “1000” variant correctly no longer claims a valid four-digit LLL parser: its fixed-width interpretation fails alphabet validation. I do not claim independently executing every negative through a complete production-equivalent parser.
- All **41** evidence fixture canonical-content hashes and original-instruction SHA256 bindings independently matched. Canonical JSON in these fixtures uses ASCII keys/values and integer amounts/generations; compact sorted serialization agrees for these concrete values. This arithmetic is not an implementation of RFC8785 for arbitrary input.
- M-authorized-relay carries issuer-attested identity while its authenticated sender is NETWORK-SIM. Missing/forged proof has explicit invalid oracle. These are future authentication-seam inputs, not real signatures and not proof that Ed25519/key provisioning works.
- Exact conflicting confirmation now supplies a different signed-content fixture in the sequenced confirmed-approval context. The oracle retains approval and quarantines the opposite evidence. No-response remains zero transport bytes, not a fabricated 0110/91.
- EV1/EV2/EV5 share the checked canonical hash despite collection/delivery-instance changes. EV3 changes immutable status and conflicts; EV4 is rejected for context before dedupe. Event fixtures do not authorize business state.
- Eight channel amount cases distinguish exact decimal conversion from invalid precision/type/leading-zero/range input. Thirty-five state cases cover reservations, crash boundaries, registry changes, revocation and late admission. The 161 count is correct: 85+35+8+5+28. Supporting evidence/hash records are not additional executed cases.

These are documentation checks only: no repository code/test codec was written or executed, no dependencies installed, no production round-trip/interoperability, no QA or crypto pass claimed.

## N1–N7 disposition

| Prior finding | Status | Independent assessment |
| --- | --- | --- |
| N1 malformed fixed-width alias | CLOSED | MERCH0000000001 is actually 15 bytes; positive offsets and complete decoded fields now agree. Full merchant business reference remains intact. |
| N2 response-capacity gap | CLOSED | P2a reserves 173 and rejects requests over 826 before claim; exact capacity responses and overflow request fixtures supplied. |
| N3 reserved-but-unsent semantics | CLOSED | O3a/C3 define no-claim, CLAIMED_NOT_SENT, POSSIBLE_SEND and durable confirmation. CAS precedes transport, restart never resumes send, ambiguous CAS is not proof of no-send, linked expiry never redispatches. Minor response-precedence harmonization remains C2 below, not the original missing-state defect. |
| N4 evidence authority/seam | PARTIAL | P5 resolves actor versus relay authority, original instruction hash, atomic durable receipt/version and late-handler circularity. Physical carriage between byte response and evidence handler remains C1. |
| N5 mutable-registry replay | CLOSED | P3a checks current context first, then prior/key and pinned comparison; retired fixture/changed route/scenario cannot rerun the old decision. New work alone resolves current fixture eligibility. Explicit changed risk conflicts. |
| N6 observation canonical identity | CLOSED | O2a fixes exact immutable keys, collector exclusions, absent optional normalization, producer namespace and restart redelivery. C1/C3 and O2 separate server freshness from browser connection. |
| N7 fixed acceptance oracles | PARTIAL | Major missing matrix/proof/date/conflict/amount/event/capacity fixtures supplied, backlog/live measurement separated. C3 below identifies an old state oracle still inconsistent with new admission rules; not all negative/crypto behavior has independent executable validation. |

CLOSED closes the specific prior specification defect, not implementation or approval.

## Original F1–F6 disposition

| Finding | Status | Remaining boundary |
| --- | --- | --- |
| F1 immutable authority citation | CLOSED | Correct immutable O and preserved reviewed revisions remain. |
| F2 exact wire/channel specification | PARTIAL | Concrete wire candidate and arithmetic adequate for D1 selection; C1/C2 remain before construction. |
| F3 identity/DE48/aliases | CLOSED | Original refs, aliases, equality/prior/window/generation and no-redispatch preserved; capacity now bounded. Closure is specification-domain scope only. |
| F4 actor/outcome evidence | PARTIAL | P4/P5 domain trust proposal adequate for bounded selection; evidence carriage realization C1 and independent Security/QA remain. |
| F5 failure/access contract | PARTIAL | Concrete proposed O2a/O3a and access/retention contracts now supplied; C2 harmonization and D2/D3/architecture/Security decisions outstanding. |
| F6 executable acceptance inputs | PARTIAL | Concrete revised fixtures now supplied, C3 oracle correction remains; no executable independent acceptance result exists. |

## Remaining corrections

### C1 — MEDIUM: attestation carriage is not bound to the exact byte boundary

Affected gate: interface/evidence construction, not selection of the private ISO/HTTPS family.

P5 specifies what must be signed and admitted, but C2 HTTP 200 carries exactly one octet-stream 0110. Neither that 0110 nor C2 names how the signed attestation, key/signature, sender/relay receipt and response bytes arrive together at the processor's P5 handler. DE48 evidence ID alone is expressly not proof; an absent preexisting processor record cannot be fetched circularly as if receipt had already occurred. Adding P5 does not itself define an HTTP carrier.

Correction: team supplies one concrete bounded internal response/evidence carrier, its response-to-attestation binding, size limits, unavailable/missing-attestation behavior, and normal versus late delivery examples. It may preserve the exact octet-stream body using an explicitly specified authenticated sidecar/internal envelope; do not add undeclared body bytes or expose it through browser traces. Carry actual signature material in the future interface, not a VALID test-oracle flag. Keep key custody/rotation and deployed principal verification under architecture/Security review. No owner field-design question is needed.

### C2 — MEDIUM: supplemental response rules need a single precedence contract

Affected gate: channel/failure-path construction.

C1 says HTTP 202 “only in-flight duplicate”; C3/O3a also use it for active evaluation. C1's disposition enum omits UNAVAILABLE, added only in C3. O3 says failure of either durable admission returns NOT_DISPATCHED, whereas O3a correctly forbids this assertion when a commit outcome is unreadable. O3's after-send journal-failure NOT_CONFIRMED response and C3's unreadable-record null/UNAVAILABLE response need an explicit distinction based on what the current invocation independently knows.

Correction: make C1's response schema and HTTP table comprehensive and explicitly subordinate O3 prose to O3a/C3 for crash/storage ambiguity. Specify observed-current-invocation possible-send versus unreadable restart/history failure, outcome/null, disposition and IDs in each case. Preserve a known durable confirmation when readable; when unreadable, show the client's prior snapshot only with asOf/stale labeling, not a newly asserted server confirmation. Keep no retry advice and no automatic send. Add exact JSON response fixtures for these branches. This is harmonization of already proposed choices, not a new human business-policy question.

### C3 — MEDIUM: retained long-reference oracle and decision references lag the new contract

Affected gate: executable specification/handoff precision.

S04 still says “adapt with aggregate <=999” then preserve all refs, without distinguishing the new 826-byte exchange admission bound. For a structurally legal 827–999 request, preserving parsed references is possible but admitting a new exchange is prohibited. It should not be read as a positive dispatch oracle. Capacity fixtures also include prior links: an authorized linked retry reads the original instruction; acceptance of its structural bytes cannot be treated as permission to encode or dispatch a second instruction.

The D1/R23 references still name P1–P4/C1–C2, despite correctness now depending on P2a/P3a/P5/P6 and C3. Supplemental sections are present, but an adoption record must not omit the corrections that make the candidate meaningful.

Correction: split S04 structural-preservation and admissible-exchange cases at 826/827, explicitly no claim/send above the threshold; state that linked capacity fixtures are parser/response-budget inputs rather than a retry-dispatch lifecycle. Update D1/R23 exact revision references to the full corrected profile/channel sections; identify D2/D3-dependent portions as still proposed. Pin revised hashes after these editorial/fixture corrections. Do not weaken the no-redispatch core to make capacity fixtures operational.

## Provenance and preserved constraints

Recount: **25 rule rows = R01–R10 SOURCED, R11–R25 DESIGN ASSUMPTION**. Zero UNRESOLVED row labels does not settle human decisions or prove construction completeness.

| Rule | Independent source/design assessment |
| --- | --- |
| R01 | Owner HTML/live/no-reload/persisted history scope. |
| R02 | Owner common hook for all implemented/future capabilities, not build-everything mandate. |
| R03 | Owner ACL to simulator ISO boundary; supported/validated input remains a necessary bounded interpretation. |
| R04 | AWS delta establishes existing JSON, not ISO implementation. |
| R05 | Guide §5/§13 and AWS distinguish rejection, issuer decision and unknown. |
| R06 | Existing HD-02 duplicate/no-redispatch core plus PROC-01. |
| R07 | NET-04/05, ISS-04/05 and AWS late-evidence/receipt constraints. |
| R08 | Owner synthetic scope, guide acquiring mandate/context and current registry. |
| R09 | Synthetic-only/sensitive-data constraints, not ISO catalog policy. |
| R10 | Guide §13/current AWS separate issuer truth from processor knowledge. |
| R11 | Proposed transaction/attempt/event identity mechanics. |
| R12 | Proposed task evidence/manifest mechanics; no missing-event failure invention. |
| R13 | Proposed time/causality measurements; no cross-clock certainty. |
| R14 | Proposed independent business/completeness/connection dimensions. |
| R15 | Proposed timeline presentation, constrained by sourced payment truth. |
| R16 | Proposed dedupe/quarantine policy. |
| R17 | Proposed late projection mechanics, not authority from task success. |
| R18 | Proposed durable history/replay/pagination. |
| R19 | Proposed concrete access surfaces derived from sourced tenant boundaries. |
| R20 | Proposed stricter allowlisted trace policy, including synthetic raw payload exclusion. |
| R21 | Proposed exact request adaptation preserving sourced semantics. |
| R22 | Proposed response validation preserving actor/uncertainty. |
| R23 | Private profile selection, not scheme mandate or existing approval. |
| R24 | D2 proposed durable admission/journal failure choice. |
| R25 | D3 proposed hook/storage/polling/access/retention/freshness choices. |

Compared full current contracts with preserved prior revisions and original reviewed constraints. No removal of R01–R22 core safeguards was found. The meaningful refinements are explicit: replay may outlive fixture retirement but not current context revocation; no implicit alias padding; trace retention does not expire business links; ambiguity after CAS is intentionally conservative; unlinked expiry may create a new authorization only with warning and normal checks. These are private proposed mechanics, not universal scheme rules. Existing A pending supplements do not become approved through cross-reference.

P5 introduces signed attestations as a simulator design proposal, not something sourced from ISO or already present in AWS. It does not merge 3DS authentication with financial authorization. Positive issuer proof still becomes processor-known outcome only at durable receipt/version commit. A valid issuer signature unseen by the processor is not confirmation in processor history.

No stand-in, partial approval, reversal, inquiry, capture, settlement or live connectivity is added. Reversal remains a separate future operation, not a refund or guaranteed hold release; history is not external status inquiry or 0800 network management. Auxiliary future hooks need not fabricate payment states or financial MTIs.

## Single-question handoff

The coordinator may now ask only:

**“Adopt the proposed SIM-ISO87A-v1 private authorization-only profile and documented HTTPS channel boundary as the simulator baseline, without scheme-conformance or construction approval?”**

Identify this reviewed bundle and the remaining construction corrections; do not call it a fully verified codec/interface. This report supplies independent decision-readiness advice, not the owner's answer. D2 durable trace admission and D3 observation/access/architecture must follow separately, one at a time. Technical corrections C1–C3 remain team work; no request for the owner to design fields is necessary.

Only domain-review.md authored. No contracts, fixtures, work-item receipt, source, code, configuration or other files modified. No commits, tests, human approval, external sends, publication or deployment. Advisory boundary findings do not substitute for independent Security review.
