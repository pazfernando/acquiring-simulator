# WI-ACQ-OBS-001 — Independent payment-domain review

Date: 2026-11-18 America/Bogota. Reviewer: Acquiring Domain Reviewer.
Review method: payment-behavior-review skill; independent inspection of local evidence, not implementation certification.

## Verdict and affected gates

**CONDITIONAL — requirements/domain-analysis handoff and research-selection adequacy.**
The commissioned HTML POS, live persisted task history and common hook across all present/future implemented capabilities are correctly bounded. The public-reference investigation is adequate to recommend a private ISO87 ASCII baseline for further specification. The owner need not design ISO fields: the team must prepare a complete candidate for a bounded adoption decision.

**BLOCKED — construction readiness of this console/ACL delta**, pending a complete reviewed profile/channel contract and Q2/Q3 dispositions, followed by human architecture approval and triggered independent Security review. This is not a finding that all future operations must be built now. Existing A implementation approval or verification is not extended by this review. No unconditional evidence-based PASS for a construction contract is possible.

## Exact reviewed identities

MD5 identities independently recomputed and matched the task envelope:

| Artifact (relative to this directory) | MD5 |
| --- | --- |
| requirements.md | 2ac753ee2ec5f6a5e8951256c86581a5 |
| acceptance-scenarios.md | 6b26f7204ee969bb3c74267380277c31 |
| iso8583-reference-research.md | 7e27138d8d3016f33e55dc2b90b12d07 |
| revisions/owner-scope-2026-11-18.md | 67f9979245fba660bc3603de43b2fe99 |
| ../WI-ACQ-AUTH-001/aws-architecture-update.md | 6c8e1aa03ace717a3349ee74235fecaa |

Additional authority: ../../sources/imports/2026-11-18/acquiring_simulator_functional_guide.md v0.5.2, MD5 69ca7e39cc0cfd595eb19c28c5087c15, especially §§1, 3, 4.2, 5 and 13; software_components_aws.md v0.3.0; imported manifest; current WORK_ITEM.md as receipt/context, not altered scope.

Research snapshot SHA-256 and byte lengths independently matched research-sources/manifest.json:

| Snapshot | SHA-256 |
| --- | --- |
| jpos-iso87ascii.xml | d128a0bb435af8193da09539937515052019a8eb3fa5c888c5dd6e9410ecb397 |
| pyiso8583-specifications.html | 2c6163996d77a1e4afd53cc84fa1eb74bdef4d6d4421beff1a0184499fff52c1 |
| jpos-generic-packager.html | 45c52e4306217a13b52182850e18d091724b344869e3199a558263e3550c4dcd |

The manifest pins jPOS commit 5ef6c9be0fce8c4c00415b05ee443f4178f8112b. Snapshot identity establishes the reviewed bytes, not independent verification of remote provenance. I inspected the local XML and relevant official-document snapshot content. I did not retrieve normative ISO text, validate the supplementary Context7 claim, or reproduce the coordinator's web searches. Configured MCP is incomplete under the runtime policy; no MCP-backed verification succeeded or is claimed. This limits external corroboration, not the local review.

## Findings and exact corrections

### F1 — Medium: scope authority citation is no longer hash-resolvable

requirements.md Authority O names current WORK_ITEM.md with the original scope MD5. That hash now identifies revisions/owner-scope-2026-11-18.md, not the receipt-appended current record. Business scope itself remains consistent.

Correction: point O directly to the immutable revision and its verified hash; list current WORK_ITEM.md separately as the evolving receipt. Retain the research owner's follow-up as separate research authority. Do not re-label new receipts as original human scope. Re-review corrected requirements by their new hash.

### F2 — High, ACL construction gate: researched structure is not a complete payment contract

The XML corroborates field classes/widths, not mandatory fields, DE3/22 values, decision provenance or duplicate policy. The independent pyiso8583 snapshot confirms incompatible sample DE54/55 definitions; mixing defaults would silently change the wire. Proposed 0100/0110, UTC DE7, processing code 000000, USD/840 and HTTPS body transport are private simulator choices, not universal ISO or scheme rules. The inaccessible ISO catalogue/full standard cannot establish conformance.

Correction: produce a versioned normative SIM-ISO87A-v1 candidate with separate 0100/0110 required/conditional/forbidden matrices, character repertoire, byte-count and padding rules, allowed values, malformed/unsupported handling, exact channel schema and boundary/transport identity contract. Define request/response correlation and trusted evidence validation, including which references are echoed or generated. Pin the actual codec configuration; demonstrate lengths/packing with vectors rather than treating XML class names alone as proof of codec behavior. Explicitly forbid undeclared secondary-bitmap fields in initial A. Define DE7 year rollover interpretation without making DE7 a transaction identity. Maintain domain semantics separately from codec and transport. The team should supply this candidate, not return an open-ended field-design question to the owner.

### F3 — High, identity/idempotency construction gate: aliases and DE48 remain underspecified

The proposal correctly avoids putting SIM-APP-001 in numeric PAN/DE2, omits genuine PAN, prohibits truncation and warns against STAN/RRN-only duplicate detection. A 999-character DE48 capacity does not by itself guarantee preservation of the original business key. No accepted canonical extension or durable alias namespace yet demonstrates this.

Correction: specify DE48 version, keys/subfields, requiredness, exact maximum byte lengths, encoding, escaping, duplicate/unknown-key handling and aggregate overflow rejection. Carry fixture references only; resolve credentials/decision fixtures against authorized server-side registries, not caller-selected issuer outcomes. Reject DE2 and other undeclared sensitive fields, not merely ignore them. Preserve existing authorization ID, correlation ID, business key, 24-hour window/generation, payload conflict rules and original links across ACL, replay and restart. Define durable acquirer-scoped DE32/41/42 aliases, uniqueness/collision checks and reverse resolution; never derive aliases by truncation, trust them as authority, or reassign them while linked records remain. STAN wrap and RRN collisions cannot merge operations. A submission attempt/event identity must never replace the existing payment duplicate key. Define whether a channel retry reuses an existing wire instruction or merely reads its known result; it cannot redispatch automatically.

### F4 — High, outcome-mapping construction gate: DE39 must not erase actor or dispatch certainty

Research explicitly labels 00/05/30/91 as proposals and requires private provenance; that is appropriate. A generic 05 fixture cannot represent every issuer-decline reason. A 91 response is not inherently an issuer decline or proof of non-delivery. A 30 rejection can be generated at different boundaries: “pre-dispatch” needs to identify dispatch to which actor.

Correction: supply a table combining producing actor, verified dispatch stage, DE39, mandatory private result/provenance, channel disposition and allowed business transition. Define no-response separately from generated error responses. Permit APPROVED/DECLINED only with verified correlated issuer evidence in this slice; classify explicit pre-issuer rejection as PROCESSOR_REJECTED with actual provenance; preserve NOT_CONFIRMED/UNKNOWN after possible dispatch without definitive evidence. Reject inconsistent combinations (for example 00 plus non-issuer/unknown provenance), missing result extension, unmapped codes and wrong references as confirmation evidence. Preserve already confirmed state when a later invalid reply arrives. A DE38 value is neither a payment ID nor independent approval proof; define its source, stability on replay and absence on nonapproval. Do not invent an issuer authorization reference if the baseline lacks one.

### F5 — High, failure-path/access construction gate: Q2/Q3 cannot be treated as settled defaults

R14–R18 and OBS-06/15/17/21/22 correctly separate payment, observation and connection state. A04(a), seven-day trace retention and proposed live/recovery targets remain unaccepted assumptions. Existing CloudWatch retention does not govern new trace data. Verified late evidence, not issuer-table reads or generic task success, controls reconciliation.

Correction: select and document trace admission behavior, durable business-evidence conditions, dispatch ambiguity boundaries, bounded recovery/loss behavior and distinct business-journal versus trace-store failures. Never redispatch because trace admission/replay/reconnect fails. Specify authorized issuer-diagnostic visibility separately from processor history, revocation/cursor enforcement, retention and preservation of idempotency/original-reference records. Resolve what “known result” means when a journal write fails without silently converting trace data into business authority. Security findings here are advisory; new UI, subscriptions and evidence interfaces require independent Security review.

### F6 — Medium, executable acceptance gate: broad scenarios do not replace concrete vectors

OBS-01–28 provide good semantic coverage but OBS-26 is an obligation, not supplied golden bytes. No executable interoperability acceptance follows yet.

Correction: add fixed input/packed-byte/decoded-output/result vectors for:
- approved and deliberate issuer-decline 0100/0110; explicit network rejection; no response; private uncertain response; correlated late approval and late decline;
- 00/05/30/91 provenance combinations, absent/inconsistent DE48 result metadata, unknown DE39, missing/unsolicited DE38, wrong operation/correlation/context and conflicting confirmed evidence (reject/quarantine unless separately authorized resolution exists);
- DE32/41/42 alias collisions and long references; same STAN/RRN across tenants and after wrap; replay/restart within the existing window and new generation after expiry; amount/currency or fixture changes under the same business key;
- smallest/largest currently valid A amounts, invalid amounts/non-USD; exact and overflow field/DE48 limits, ASCII repertoire, multibyte rejection, padding, leading zeros and LL/LLL prefixes;
- missing required/extra forbidden fields, DE2 injection, DE55 injection, malformed bitmap, unexpected secondary bitmap/MTI, truncated body and trailing bytes;
- trace outage before/after dispatch, business-journal outage, issuer truth without processor receipt, late/reordered/conflicting observation events, and retention/reconnect without a second dispatch.

These are specification additions and future QA obligations, not tests executed by this review. No reversal/inquiry wire vectors are required until those operations are separately commissioned.

## Flow-level assessment

| Flow/scenario | Expected behavior | Rule status/reference | Gap | Verdict |
| --- | --- | --- | --- | --- |
| HTML CP authorization and common hook | Instrument every implemented task; do not activate unimplemented operations | SOURCED: owner scope; R01–04 | Detailed channel/task contract | CONDITIONAL |
| Issuer approves but network drops reply | Processor UNKNOWN; issuer truth remains separate | SOURCED: F §13 NET-03/ISS-04; I | Concrete profile/evidence vectors | CONDITIONAL |
| Verified late decision / duplicate request | Reconcile same original idempotently; no automatic redispatch or state regression | SOURCED: F §13, I 24-hour policy; R06–07 | Alias/DE48 contract and vectors | CONDITIONAL |
| Live/task success or disconnected UI | Cannot establish approval/decline; completeness is separate | SIMULATOR ASSUMPTION: R12–18; consistent with sourced payment semantics | Q2/Q3 dispositions | CONDITIONAL |
| Private ISO87 reference selection | Use pinned baseline; do not mix sample dictionaries or assert scheme compliance | SIMULATOR ASSUMPTION: research; SOURCED: inspected packager definitions | Not normative business profile | CONDITIONAL |
| Reversal / transaction inquiry / auxiliary work | Separate future capability activation; reversal is not refund; no generic 0800/0810 inquiry | SOURCED: owner scope, F §§3/5/13 | Future profiles only when commissioned | CONDITIONAL |

## Bounded decision questions and handoff

1. After the team supplies the complete candidate and vectors above, does the owner adopt SIM-ISO87A-v1 as a private authorization-only profile with the proposed HTTPS simulator boundary? This is an adoption decision, not a request to design fields.
2. Which concretely documented A04 failure policy and durable business-evidence contract is accepted? The team must resolve operational details before presenting this decision.
3. Which proposed context/issuer-diagnostic access, trace retention and freshness/recovery contract is accepted through the architecture/Security gates?

Continue specification and independent re-review; do not mark construction authorized. Preserve B/C boundaries: reversal eligibility, original linkage, duplicate/uncertain acknowledgments and request-versus-advice choices belong to a future reviewed operation profile; reversal is not refund or proof of hold release. Console history is not external inquiry, issuer confirmation or network management. Neither optional 3DS nor stand-in/partial approval is added.

Only this domain-review.md was authored. No requirements/scenarios/research/scope/code edits, tests, human approvals, external sends, publication or deployment are claimed.
