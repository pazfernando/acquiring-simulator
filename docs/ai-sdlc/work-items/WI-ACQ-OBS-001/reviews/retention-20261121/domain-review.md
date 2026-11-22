# Operation A retention — independent acquiring-domain review

Date: 2026-11-21, America/Bogota. Reviewer: Acquiring Domain Reviewer.

## Overall verdict: CONDITIONAL — documentation review only

The record matrix preserves the required payment guarantees as a specification, provided its explicit retained authoritative records are not replaced by a mere seen/hash tombstone. One material documentary correction remains: **U2 is resolved by existing approved simulator policy, not an immediate question for Fernando.** The draft must be revised and that changed revision reviewed before treating this condition as closed. U1 remains open only for destructive business/evidence compaction or final retirement, not for the approved non-destructive seven-day functional-history interpretation.

This is advisory to the Product Lead. It does not approve construction, certify implementation/conformance, close NV-05, accept the payment path, or extend the earlier domain PASS beyond its stated scope. No code, tests, Git/provider commands, network/AWS calls, deployment, credentials or onward delegation were performed. Only this review and its checksum sidecar are authored.

Runtime actually observed: native OpenClaw subagent / Codex; this session's sessions_list entry reports **gpt-6.1-sol**, system family GPT-6, no model override. Session: agent:ai-sdlc-acquiring-domain:subagent:2fb9a9e3-1295-462e-9f28-02de8284c985. No provider-qualified model ID is exposed. Branch feat/wi-acq-auth-001 and checkout are the supplied binding, not a fresh Git observation.

Method: payment-behavior-review skill; independent current-file documentary review, not reliance on analyst conclusions. Actors remain authorized channel/ACL, processor, simulated network, issuer, internal evidence admission and observation consumers. Operation A synthetic CP only; no B/C, refund, settlement, stand-in, partial approval, CNP/3DS or live-network rules are introduced.

## Exact revision and citation convention

Commissioned retention-delta.md SHA-256: `ff5fbba0a8a92f37af971b3b8d308230277e3cace4d29156f0acf8d9555fe6af`.

Commissioned evidence/SHA256SUMS SHA-256: `513dff49e584c633d95a686085d7b93465aace03263bb2b727da2a67f7bac089`.

Both pins independently match on disk before substantive review. All 11 manifest entries, including README.md, verify. Initial manifest invocation used evidence/ as working directory rather than design/retention/ and found no files; rerunning at the manifest-relative base verified every entry. No hash mismatch occurred.

Paths below are relative to docs/ai-sdlc/work-items/ unless stated otherwise. D = WI-ACQ-OBS-001/owner-decisions.md; A = its adr-operation-a-access-and-environment.md; O = its observation-contract.md; C = its channel-contract.md; P = its iso8583-profile.md; OBS = its requirements.md; DR = its domain-review.md. AR = WI-ACQ-AUTH-001/requirements.md; AWS = its aws-architecture-update.md. T = WI-ACQ-OBS-001/design/retention/retention-delta.md. Citations identify exact clauses and current-file line numbers; SHA-256 inventory below fixes their bytes.

Authority: D 50 approves functional history, not financial deletion; D 56–60 approves agreed architecture while preserving payment semantics. A 11/20–21 removes obsolete business quota gates but preserves atomicity/idempotency/no-resend and mandates per-record retention. Latest D 86 accepts notification-only WebSocket without mTLS; D 92/95–99 selects self-managed Keycloak with separate evidence-key plane and no provisioning; D 103–107 keeps verified late evidence internal. Historical pending/provider/IAM-ingress statements do not supersede these overlays. DR 9–15/23/74/77 is a prior bounded documentation PASS, not retention implementation approval.

## Per-question verdicts and dispositions

### 1. Record expiry, no-resend, prior reference and evidence identity

**PASS for specified business invariants — CLOSED for this documentary question; implementation unverified.**

T section 3 retains the authorization journal, CLAIMED_NOT_SENT/POSSIBLE_SEND, original instruction/correlation, committed result/version, equality data, business generation head, evidence/receipt identities, aliases and issuer exact-instruction decision identity independently of functional-event payload expiry. T R-RET-04/05 and NV05-S02/06/07/08 preserve original collection identity and forbid laundering duplicates into a new event/window. A seen flag alone is explicitly rejected by the business-journal row.

Exact basis: O O1a/O2a 16–24 (context-validated producer/event identity, immutable hash/source and no recreate after expiry); O O3 27–31 (original invocation only, mandatory POSSIBLE_SEND before single downstream invocation; retry/restart cannot resume send); O O8 77–79 (journal/evidence/heads/aliases/original links survive); P P3 61–64 and P3a 84–87 (prior-first authorization/equality, retained instruction versus current fixture, no alias reuse); P P5 91–94 (evidence canonical identity and trusted atomic receipt); AR R10/R15–R17/R20, lines 87/92–97; AWS 25–26 (no linked-reference/head TTL and separate issuer dedupe).

Thus an old exact instruction cannot acquire send authority merely because its history disappears. This is not a claim that every later unlinked request with the same business key is forever prohibited: an eligible fresh generation after 24 hours is distinct, as question 6 explains. Current access checks remain mandatory even when replay data survives; no cross-acquirer disclosure is permitted (C C3 26–29, P P3a 84–86).

### 2. Delayed physical deletion and Streams resurrection

**PASS for specified safety order — CLOSED for documentary semantics; runtime closure OPEN.**

T R-RET-03/06/06a/06b, lines 68–70 and NV05-S03–05, explicitly separate logical ineligibility from asynchronous purge: every read/fanout filters expiry, durable range/revision/EXPIRED identity precedes or accompanies removal, and native TTL cannot outrun that barrier. Only source event INSERT is a notification candidate; source is reread, REMOVE is not replayed, old pointers cannot recreate payloads or erase newer metadata. Missing unexplained source becomes SOURCE_INTEGRITY_GAP, not complete history. No financial permission is delegated to expiry/notifier/repair.

Exact basis: A 21; O O6 60–63 (revision/snapshot invalidation and recorded-event authority); O O7 68–72 (source reread, INSERT only, observation-only retry/repair, no copied backlog); O O8 78–79 (durable expiry boundary and no redelivery renewal). T R-RET-02 properly proposes exact boundary precision rather than deriving a guaranteed physical deletion instant. AWS TTL delay is not a logical grace period; the O8 <=48h physical target remains a proposal, not an AWS guarantee.

Required realization evidence remains absent: actual keys/TTL attributes, crash-safe ordering, source/dedupe conditional transactions and all read/send paths. In particular expiry must be evaluated for actual disclosure, not merely when a retry was originally queued. This states the existing logical-expiry invariant, not a new payload or schema field.

### 3. Requirement/scenario provenance, fields and business scope

**CONDITIONAL — OPEN documentary correction C-U2.**

R-RET-01/03/04/06a/06c and NV05-S02–11 correctly trace preservation obligations to O/C/P and the owner overlay. Existing contract vocabulary is preserved: NOT_CONFIRMED remains public business uncertainty, while PENDING/COMPLETED/REPLAY/UNAVAILABLE remain distinct dispositions (OBS 69; C C3 21/29–36; T section 6). EXPIRED, expiryRevision, firstRetainedSequence, SOURCE_INTEGRITY_GAP and RESYNC_REQUIRED already exist in O 20–21/60/78–79; they are not invented payment enums. R-RET-02/05/06b/07/08 explicitly identify proposed precision/origin enforcement/race and technical cleanup/replay-cache rules. These are not already approved field additions. R-RET-08 is conditional, not a claim that Keycloak already enforces a chosen jti policy.

Qualification: R-RET-06's guarantee is sourced, but O8 78 itself calls the worker mechanics proposed. T sections 1/2/4 preserve this distinction; do not promote concrete transaction layout, range-merge scheme, 15-minute cursor, purge deadline or removed numeric budgets by copying a SOURCED row alone. Existing scenarios are documentary cross-references, not executed QA; no independent vector execution is inferred.

Correction C-U2: T NV05-S14 (line 94), section 6 U2, section 7 U2 and evidence/README missing-evidence item 9 misclassify deadline policy as an unresolved owner choice. Replace that interpretation with the 30-second policy and an explicit stale-document harmonization task, preserving historical bytes as appropriate. The analyst/Coordinator owns that correction; this review edits no draft or approved source. Re-review the corrected revision. See question 5 for controlling citations.

### 4. U1: can a compact non-payload tombstone preserve business evidence?

**CONDITIONAL — CLOSED against automatic seven-day business deletion; OPEN for destructive equivalence/final horizon.**

An event tombstone can preserve event duplicate/expiry knowledge, but is **not by itself sufficient business evidence**. An ID/hash/EXPIRED marker can reject a known duplicate; it cannot reconstruct current result/version, original-reference resolution, equality fields, authenticated deciding actor, earlier uncertainty provenance or processor receipt. T's matrix correctly retains those authoritative records separately. Do not call retained results/evidence redundant payload merely because they are not console history.

P P5 91 explicitly allows restricted journal storage of normalized evidence/identity hashes and references, and O O8 79 allows non-payload expiry/dedupe metadata. Therefore compact normalized business records are possible in principle; indefinite raw wire/proof retention is not automatically required. However no reviewed replacement representation or equivalence proof is supplied. The event-level tombstone does not establish that P5 original-instruction binding, C3 latest-result replay, AR R10/R11/R20 provenance and earlier uncertainty remain explainable after destroying business evidence. This review cannot certify such substitution.

Exact policy boundary: D 50; A 21; O O8 77–79; AR section 8 lines 155–158 and residual P08 question line 184. No finite destruction horizon is authorized. Retain required authoritative records while the team specifies and independently validates a compact equivalent; no new owner choice is necessary merely to expire functional history. If final destruction or loss of audit/replay/late-admission capability is desired, Fernando must decide the explicit business-evidence trade-off and retirement horizon. He need not design keys or tombstone fields.

### 5. U2: 30-second R20/A07 versus descriptive two-second AWS note

**PASS — Coordinator's interpretation CONFIRMED; CLOSED as a business-policy question. Documentary harmonization remains OPEN under C-U2.**

AR R20 line 97 says: “Configurable response timeout: simulator default 30 seconds from dispatch (adjustable by Human Product Lead)” and classifies it “APPROVED SIMULATOR POLICY HD-02; 30-second value SIMULATOR DEFAULT.” A07 line 133 repeats “At 30 seconds from dispatch without confirmation (default)”; section 8 line 158 expressly identifies R20/HD-02 as authority; section 10 line 178 records HD-02's adjustable timeout default. This is not merely legacy implementation behavior.

AWS line 34 does describe AWS2s/local30s explicitly, and C C2 17/P P4 77 repeat2s. Those are real documentary inconsistencies, not nonexistent text. But AWS bounded supersession line 9 names SQL storage/no-HTTP/in-process choices, not an owner decision adjusting timeout policy. Its line 3 identifies an implementation update, not independent acceptance. D 58 preserves existing payment semantics, and current D 103 describes the **approved 30-second deadline** when recording internal late evidence. No specific Human Product Lead adjustment to2s is documented in these inputs. Broad architecture approval of the candidate does not silently repeal its expressly retained approved payment policy.

Accordingly30s is the adjustable simulator default for this review, including AWS; specific explicitly authorized configuration may differ, but this draft identifies none. Preserve immediate uncertainty for ambiguous/malformed evidence absent valid confirmation and monotonic known confirmation (AR R20; C C3 29–36). Do not rename NOT_CONFIRMED, invent a persisted timeout result, or infer that late response is automatically harvested. A documentary development report is unnecessary to establish this precedence and is not implementation verification here.

### 6. Seven-day history versus 24-hour generation, retry and late evidence

**PASS — CLOSED for business separation; execution evidence OPEN. No additional missed business conflict found beyond C-U2 and U1's destructive-retirement limit.**

The clocks have different purposes: firstReceipt+24h governs generation admission; original collectedAt+7d governs each observation payload; dispatch+30s governs default unconfirmed response timing. Streams24h governs notification transport only. None is a substitute for another.

P P3 61/P3a 84–87 permits an eligible unlinked generation at the exact 24h boundary but never turns a valid linked retry into a new payment. AWS 9/25 preserves generation policy and durable heads. T matrix generation row and NV05-S07 preserve this distinction without indefinitely locking the business key. Supplied unknown/foreign/conflicting prior reference rejects before fallback to a matching local key; expiry is not permission to route/send again. A historical exact instruction remains distinct even if the same key later names a genuinely new generation.

After7d, old history is explicitly truncated, not “no transaction” or financial finality. A genuinely new verified late result for the retained original instruction is admitted only internally through P5/C4, atomically with its own new business version and critical event; that event has its own collection origin. It neither renews the old uncertainty event nor erases the required internal uncertainty/evidence linkage (T R-RET-04/06c and NV05-S08; O O3 29/O8 77–79; P P5 91–94; C C4 42–43; D 105–107; AR R16/R20). Identical repeated evidence creates no fresh transition/window; conflicting confirmed evidence quarantines without overwrite. Evidence arrival time, history lifetime and issuer decision provenance remain separate.

The new seven-day event does not promise a newly complete seven-day transaction narrative: the UI must still expose old expired ranges. Required future-evidence persistence cannot be discarded to free history occupancy; obsolete budget numbers are not resurrected (A 11/20; O O8 77). Neither notification retry nor a user retry is new dispatch authority (D 86/107; C C3 36/C5 48; O O7 69–71).

## Residual risks — not missing approvals invented by this review

- Final business/evidence destruction can remove audit, equality, original-reference and late-admission capability even if event duplicate hashes survive. U1 is not solved by naming a tombstone.
- Durable business links/identity metadata and unresolved-operation headroom can outlive seven-day payloads; lifetime cost/storage is not bounded by payload retention. On-demand billing does not remove this cost.
- Asynchronous physical deletion can leave logically expired data stored; filtering failure on any detail/query/notification/repair path would violate the approved window. Local clock tests cannot certify an external purge SLA.
- Conflicting approved-policy/descriptive-default text risks inconsistent timeout tests and operator expectations until C-U2 documentary harmonization is reviewed.
- Current access/revocation and generation scope must remain independent of historical identity retention; a valid old ID is not access authorization or decision authority.

## Missing evidence — separate from residual business risk

1. Revised analyst draft resolving C-U2 and independent review of its new hash. This exact conditional review does not mutate or approve that future revision.
2. Concrete record/table/key/TTL mapping and compaction-equivalence demonstration for every retained business/evidence/reference role; no event-only tombstone sufficiency proof supplied.
3. Crash/concurrency proof and local clock-driven independent QA for logical boundary, delayed TTL, stream duplicate/REMOVE, range/revision races, source absence, restart/retry, prior-reference scope,24h generation and late evidence after7d. No code or test execution reviewed here.
4. Actual query/detail/fanout/repair expiry checks and separation from financial invocation privileges, including stale metadata/physical deletes and safe gap/truncation response.
5. Selected Keycloak replay/validity/skew/cache-failure behavior and realized WebSocket lease/revocation/epoch safeguards, with Security review. Tokens and C4 signatures remain separate planes (D 86/97/107); this domain report is not Security closure.
6. Fresh binding through Implementation if required at a later gate; no Git branch/HEAD inspection by this reviewer. Independent QA/Security and human acceptance remain outstanding. Security NV-05 lines 111–119 explicitly requires reviewed mapping plus clock-driven retest; this document supplies only the domain review portion.

## Only irreducible decisions for Fernando

**None immediately for the non-destructive history-expiry interpretation.** Do not ask him to choose2s versus30s again: HD-02/R20 already supplies the adjustable30s default. U2 is a team documentary correction.

**Conditional future U1 decision only if destructive retirement is requested:** select the business evidence that may be surrendered, the resulting limit on audit/replay/original-reference/late-evidence capability, and the retirement horizon/conditions. First obtain a concrete compact-equivalence assessment; an equivalent normalized representation without material evidence loss is a technical design/review task, not a request for Fernando to invent a schema. No final financial/evidence purge is authorized by seven-day history approval.

## Input SHA-256 inventory

Computed independently from current disk. Product paths are repository-relative. The sealed evidence inventory is identity verification only; uninspected snapshots/vectors are not execution or substantive conformance evidence. Source identities are hashes, not correctness proofs. Administrative instruction files are listed separately. The final review digest is kept in domain-review.md.sha256 and returned with the handoff, avoiding an impossible self-referential content hash.

```text
fd040124319a946d28230aeb3994fb77bac82c00cb59e58b78df9e23531bae27  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/owner-decisions.md
d51bbf650aca74e82351dacf0e45d0f14c992044b9ff246bc7b8b281920b0b39  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/adr-operation-a-access-and-environment.md
49c5d412e3705c320a2f78b190555f49e7e0fa4662cff9329d259f89e2b459df  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/requirements.md
495e1172e76ab9ba81f8a8026e7efec25baa6e4fff0d754c0a4c92bd38ea3bca  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/channel-contract.md
ca53e384a46d24b3c1119b3e6ba804a9a9d4caac049a6b0c55a40c72842a205a  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/observation-contract.md
73adba20fd70969bf8bb9f23ebde5c5d16aec69fd4694ac32984008b76ee0ca2  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/domain-review.md
de02c533536dcb6e87e4c6e93357efb9efa60c085a59f6f63bbfbb8c0e1fe481  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/iso8583-profile.md
595ab10f95ba59ebcad6c235162c32f1a35eca519a2d341da8df5e675fe5e840  docs/ai-sdlc/work-items/WI-ACQ-AUTH-001/requirements.md
d53b759ee7caf14965bbb6d8655107cd6e086e43f957191549a2b5668c643935  docs/ai-sdlc/work-items/WI-ACQ-AUTH-001/aws-architecture-update.md
90a798c7e2a1de362993cb4974d306497d7de3567c163a3511c07b194a5ae094  docs/ai-sdlc/sources/acquiring_simulator_functional_guide.md
0d71ea007e56b12d9873df14126c22640c3abc68052b42db1f47b0a9688d218e  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/reviews/access-design-20261120/security-review.md
80a7092c0bcf7cd2258c6fde89710f1d952e6c2984b0e2728f077ddc694133b8  AGENTS.md
8584a2ed39a3ac548f303bf41bceb8e5e9df3fd3ec678289bdb128560643710e  docs/ai-sdlc/PROJECT.md
ff5fbba0a8a92f37af971b3b8d308230277e3cace4d29156f0acf8d9555fe6af  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/retention/retention-delta.md
71030eb27c6f76ee0c4a5e2b15fb8d47f6b561ed4338b6bd3e64ba0ef595af2c  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/retention/evidence/additional-source-identities.json
5f750263bea643457bba0912f7da75a6b81ce0c976a9ed7f03df786434309b34  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/retention/evidence/contract-source-excerpts.md
7deb420103927e65d1cf8f12baa76939878666952aae416caa573afbe67d076a  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/retention/evidence/official-source-1.json
dc59285467c55191eea84226e3dda921f3db2acb1af223787e2567074c27e1d0  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/retention/evidence/official-source-2.json
b60180f2d131afab9b4c275704d7ec187fe9a78d7accdd6fbf6130602e11b02a  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/retention/evidence/official-source-3.json
bd74d096ad9829ddbbf39f2bcf3bff78bbbc55077bbef66d782259c4e79ab3f6  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/retention/evidence/README.md
78bbddc8f99ebdcd96773ce79c94b0c5232f8a89499de1856c94550989f730b7  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/retention/evidence/selected-vector-excerpts.json
513dff49e584c633d95a686085d7b93465aace03263bb2b727da2a67f7bac089  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/retention/evidence/SHA256SUMS
e2ec3af3b1e0c50bcd90dae249a213637b7136fecd35d290a965e95f79340dbe  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/retention/evidence/source-close-check.json
50be8288b48d7f2ec0f7bfacac25a7c1ef379b676f2c22aeecead20bc9a5f31e  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/retention/evidence/source-identities-current.json
9b16d60d27b080baa9260596c32a8ffd9b0924cda695ed2a289e5cb32711ee93  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/retention/evidence/source-identities.json
fcbd985ddc5823a4ba3a68275f3180a5ca7a1cf28a8556b99cd23f189a45843b  /home/pazfernando/.openclaw/workspace/ai-sdlc-acquiring-domain/USER.md
a9494010cd75cff002ae1a33c90f4386776ba238f1a193dfde839983327a7ee3  /home/pazfernando/.openclaw/workspace/ai-sdlc-acquiring-domain/SOUL.md
ffc5220b41def89f4098a7d6e9c20486048265871b9d49fcdcbde74226cdd180  /home/pazfernando/.openclaw/workspace/ai-sdlc-acquiring-domain/IDENTITY.md
197a3ff08e59a72f96c6a8dcc1850b2a2348eee0463b3f5c7037b63ac4bba4b2  /home/pazfernando/.openclaw/workspace/ai-sdlc-acquiring-domain/AGENTS.md
99efa2250f92cb29ffa9b0e3f9c74161b65e1a90ac061db89c4961ea9b5ee9b5  /home/pazfernando/.openclaw/workspace/shared/ai-sdlc/README.md
8de7896fd94f1fd84a9164fc3e0366bdc4fd9536c2c47324aa2154dcd900746b  /home/pazfernando/.openclaw/workspace/shared/ai-sdlc/WORK_ITEM_TEMPLATE.md
9996056ba0d01dcd3392484dc43bf82c262c3effb201275da99753fbf9ac04f0  /home/pazfernando/.openclaw/skills/payment-behavior-review/SKILL.md
```
