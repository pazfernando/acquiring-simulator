# WI-ACQ-OBS-001 — Independent asynchronous amendment and D3-readiness review

Date: 2026-11-18, America/Bogota.
Reviewer: Acquiring Domain Reviewer; payment-behavior-review skill.
Observed execution identity: GPT-6 per system context; default execution, no override. Exact provider model ID was not exposed in the inspected results.

## Verdict and gates

**PASS — domain specification review of amended D2 and D3 decision-readiness only.**
**YES: D3 ALONE may now be presented as the next owner decision.** The proposal makes its durability, loss, capacity, recovery, access and retention trade-offs explicit enough for an informed bounded choice. No technical-field design question needs to be returned to the owner.

Current findings: **0 HIGH / 0 MEDIUM / 0 LOW** new domain specification findings. No correction blocks presentation of D3. This is not an unconditional product/construction PASS.

**BLOCKED — construction:** D3 remains unapproved; human architecture/construction disposition, ADOT/Java25 compatibility and other specified preflight checks, and triggered independent Security review are outstanding. Independent QA is unavailable/unexecuted at its downstream gate. D1 and approved asynchronous D2 direction do not approve these gates. A D3 answer must not be relabeled as executed codec/crypto/transaction/telemetry verification or deployment permission.

The prior PASS at 946dd018b12383b747478c70b57d0800 reviewed the former D2 proposal. It was not extended automatically: this report independently reviews the changed synchronous boundary and read-model semantics.

## Authority and exact identities

owner-decisions.md independently matched **20a796b210eb6c74044ab46fad46c15d**. Read its historical pending/qualification entries together with its final explicit approved D2 amendment: the later assent approves asynchronous technical export and functional projection independent of backend availability, while retaining authoritative journal and atomic critical pending notifications on the business path. It supersedes separate synchronous trace-store admission, not durable business evidence or no-redispatch policy.

Read adot-observability-research.md, independently matching **848a73f6194e4d912c9eabc26f429bfa**. Its reported AWS/Context7 findings are research evidence, not fresh MCP or runtime checks by this reviewer. Its earlier proposed/pending wording is historical research context, not an override of the later owner decision.

All commissioned MD5 identities recomputed and matched:

| Relative artifact | MD5 |
| --- | --- |
| requirements.md | 6d8e8b3a51da1c486df89e3105800224 |
| acceptance-scenarios.md | 1a42478f276f827a40c0344c43bf3a3d |
| iso8583-profile.md | 3d9984b238e17902fab106d63c28de46 |
| channel-contract.md | 05d9abc5fa4ae8cd94af1a1454b3f2dd |
| observation-contract.md | 27eb3ed660f05cb7aa84dc131dafbf36 |
| decision-proposal.md | ecfcaa2855352d346c541585eeee5be7 |
| vectors/README.md | 6ffe4046b437b57acbf3ba95f50d8cf7 |
| vectors/async-projection-vectors.json | ec9535083deed2e45af474ad337a8445 |
| vectors/response-vectors.json | cd359296edaec34f74828c377ac7bdd9 |
| vectors/state-vectors.json | 686bb72447a1a78a0c16309d9894fecc |
| vectors/documentation-validation.json | 68f884d6020eb65e81630df7bc67cf19 |

Preserved prior review revisions/domain-review-md5-946dd018b12383b747478c70b57d0800.md independently matched **946dd018b12383b747478c70b57d0800**.

Unchanged fixture identities were compared with the pinned preceding reviewed inventory; the directory has no separate vector-hash manifest beyond these reviewed identities/inventory and embedded byte hashes. No nonexistent manifest verification is claimed:

| Immutable fixture | MD5 |
| --- | --- |
| vectors/packed-vectors.json | e0a111ab43dd54377ca68a52a99b4a97 |
| vectors/carrier-vectors.json | 1609b5bee77ed4fa42c1decbaf490069 |
| vectors/evidence-fixtures.json | ff16106f14f443360501a841c1af9ae0 |
| vectors/channel-vectors.json | 9c148fec0100ab3927e8c8e73d899102 |
| vectors/event-vectors.json | 4490b56092524c2e1d0ad8def4236d59 |
| vectors/no-response.bin | d41d8cd98f00b204e9800998ecf8427e |

Thus the D1 financial packet/proof bytes have not changed in the asynchronous amendment. Research-sources/manifest.json concerns research snapshots, not the vector inventory.

Configured MCP is incomplete under runtime policy. No fresh MCP-backed verification, remote source corroboration, Java25/ADOT compatibility, dependency installation or runtime delivery is claimed.

## Independent review disposition

CLOSED below means the specification question has concrete, internally consistent treatment; it does not mean approval, runtime verification or implemented closure.

| Review issue | Disposition | Independent reasoning |
| --- | --- | --- |
| Prior C1 exact proof carriage | CLOSED, preserved | C4/P7 carrier/proof bytes unchanged; P8 changes subsequent atomic business commit, not signatures, original/response hash binding or actor authority. |
| Prior C2 response precedence | CLOSED, amended and rechecked | C3 remains payment response authority; C5 explicitly separates history GET LAST_PROJECTED from authoritative payment outcome. History-only outage no longer creates an admission failure. |
| Prior C3 capacity/parser/reference boundaries | CLOSED, preserved | 826/999 wire-budget distinction, full selection references and linked-retry no-send meaning remain; O5 operational capacity is a different, explicitly proposed limit. |
| Atomic critical outbox versus async history | CLOSED at specification level | O3/P5/P8 require a single business/evidence/outbox/capacity transaction. No business-write-then-notification financial dual write; projector/history not transaction participants. |
| Every-task instrumentation versus delivery loss | CLOSED as a D3 trade-off awaiting adoption | O1 requires each implemented capability's hook/manifest. Critical committed transitions are durable, intermediate/preauth tasks best effort, technical spans sampled. Completeness never inferred from missing drop reports. |
| Source recovery after Streams expires | CLOSED at specification level | O5 retains durable source independently of Streams; pending/poison rows have no TTL; bounded worker and authoritative reconciliation cover index lag and expired stream cursors. |
| Finite capacity and long-lived reservations | CLOSED as an explicit D3 cost awaiting adoption | Eight slots reserve current A's <=5 transition budget, with conditional 100000-slot cap and own-slot late evidence. Indefinitely live references can retain headroom; eventual backpressure even after projection recovery is disclosed, not hidden or called a leak-free design. |
| Projection replay/poison/expiry/privacy | CLOSED at specification level | O6 requires conditional history commit before source acknowledgement, no watermark advance across missing sequences, safe quarantine without false ack/deletion, original expiry and nonfinancial recovery roles. |
| Financial isolation and outcome truth | CLOSED, preserved | Projector/task/ADOT cannot confirm authorization, mutate journal or invoke payment. Verified issuer receipt becomes known only with the required atomic business/evidence/critical commit. |

No open prior finding is silently dismissed merely because the old review passed. The async amendment is substantive and owner-authorized; the table above applies to these new hashes.

## Payment and observation boundary challenge

### 1. Synchronous minimum is financial persistence, not export

The new path is business journal/critical pending record -> Streams/projector/history, plus separate asynchronous ADOT export. Required business transition, normalized verified evidence/receipt where applicable, version/head, critical event and capacity accounting must commit together. If that transaction fails after possible send, neither a successful span nor a signed-but-unadmitted reply establishes processor confirmation.

History/ADOT failure alone does not prevent the original invocation's CLAIMED_NOT_SENT -> POSSIBLE_SEND progression while business storage and allocated capacity are healthy. Crash/restart of an old claim never resumes send. This distinction removes the old separate trace-table gate without introducing payment redispatch.

Projection uses a different consistency rule: history event/checkpoint commits, then conditional source ack. The cross-store ack is not atomic, but crash after history commit safely replays/dedupes before ack; crash before commit retains pending source. No ack-before-write or financial decision by projection is permitted.

### 2. Instrumentation is mandatory; full task durability is not promised

R12/R18 are deliberately changed proposals, not unchanged historical guarantees: mandatory hook invocation does not guarantee every intermediate observation survives invocation crash, queue saturation or export loss. The <=100-event/200KiB local queue drops rather than blocks at its bound. OTel sampling is separate and cannot supply missing functional-task evidence.

This loss allowance is not treated as already approved by D2's async direction alone. It is part of the D3 trade-off still awaiting owner adoption. When presenting D3, explicitly say that intermediate/preauth task observations may be missing while committed business transitions retain durable notifications. Never offer “every task durably recorded” as an unconditional claim.

Versioned manifests and independent critical completeness/task coverage make this honest: required missing producers/start/terminal remain gaps; a span's absence is not FAILED, and a received span's success is not financial approval. Unobservable preadmission requests cannot be reconstructed or disclosed through a global correlation lookup.

### 3. Retention and capacity cannot be ignored

DynamoDB Streams' 24-hour window is not the recovery source of record. Pending/unacknowledged and poison source rows survive without TTL; acknowledged rows require both the minimum original-time retention and confirmed projection receipt before compaction. Replay and 24-hour authoritative reconciliation do not mistake absence from an eventually consistent pending index for completion.

Reserved future slots are counted with retained rows. At 99992+8, admission fits exactly; at 99993+8, it fails before a positively unsent new claim. Existing operations retain evidence headroom; a late verified confirmation consumes its own slot despite new-work saturation. The 409600000-byte figure is a logical reserved envelope, not a DynamoDB physical/cost estimate.

Long-lived UNKNOWN, unsent and first-integrity-flag eligibility can retain allocations indefinitely under no-auto-purge business retention. This can ultimately stop NEW work even with healthy history. The proposal explicitly chooses monitoring/escalation/separately authorized disposition instead of deleting evidence reserves or old links. Present this practical limitation with D3. Do not promise autonomous cleanup or unlimited admission.

### 4. Replay, poison and read authority are bounded

At-least-once source delivery requires immutable identity/hash, not transport exactly-once assumptions. N+1 may be retained pending, but a contiguous checkpoint cannot skip N. Projection conflicts retry observations only. Poison cannot masquerade as projected success or disappear because quarantine/alert delivery fails; original source remains discoverable.

Native failure destinations may contain full records. O6 explicitly requires sanitization/IAM/retention validation or the safe pointer path; this remains a Security/architecture construction preflight, not a claim that native destinations are automatically safe.

Expired events are not reintroduced as fresh seven-day history. Expiry acknowledgement/watermark is separate from missing or poison and cannot erase business links. Metadata retained beyond UI expiry must not reveal old payload.

C5/O2 now make LAST_PROJECTED explicit when journal current state is unreadable. Projection APPROVED is displayed only as historical projected knowledge; authoritative current outcome is null. A newly evaluated payment response cannot promote that projection or an old browser snapshot into a confirmed journal result.

## Independent documentation checks and limits

I inspected the fixtures rather than adopting the validation record as execution proof:

- Three exact critical source examples independently reproduce their O2a-normalized event hashes. Payload sizes are **565/575/565 bytes**, row sizes **903/910/895 bytes**, all within 2048/4096. Their collectedAt equals original committedAt. Repeated source rows embedded in reset-scoped cases equal those examples exactly.
- All **26** response JSON bodies match their listed exact serialization, fit 4096 bytes and set automaticAuthorizationRetry=false. H04/H19/H20/H23/H24/H25 express backend failure/lag without fabricated payment failure; H06/H07/H21/H22/H26 express required-storage/capacity/current-send/unreadable distinctions.
- All **85** immutable packet lengths/SHA256 again matched hex. All **41** evidence canonical-content, original-instruction and exact response-hash bindings matched. This preserves reviewed identity, not cryptographic validity.
- A01–A23 give reset-scoped semantic expectations for atomicity, crashes, duplicate/out-of-order projection, 25-hour recovery, eight-day expiry, poison/quarantine loss, reservation saturation, best-effort loss and journal-versus-projection reads. They are not production DynamoDB/Streams event fixtures or executed transactions.
- State S13/S14/S23/S25/S28/S35 and OBS-20/21/22 now use atomic business/outbox admission and async projection, not the superseded separate trace-store gate. S20/OBS-28 separate already-projected reconnect from source-backlog drain.
- Count independently agrees: **28+85+35+8+5+12+26+23 =222** acceptance cases; evidence/source/validation records are supporting inputs, not extra cases or passed tests.

No runtime codec/HTTP/transaction/stream/replay/ADOT/cryptographic implementation was executed. Signature shape fixtures remain noncryptographic public data, not accepted proof.

Java25 Lambda architecture/runtime, exact ADOT layer/agent/collector version, lifecycle flush/drop policy, memory/CPU overhead and exporter queues remain **preflight NOT PROVEN**. No layer ARN/version is selected by this review. Research's CloudWatch subscription latency/delivery findings cannot justify the proposed seconds-level UI objectives. Directly measure combined commit-to-display percentiles; adding two p95 or p99 values is not proof of the combined percentile. Reconnect <=10s applies only to an already-projected bounded snapshot, not a prolonged source backlog.

## Provenance and non-weakening comparison

Independently recounted **25** rows: **10 SOURCED, 13 DESIGN ASSUMPTION, 1 APPROVED SELECTION (R23), 1 APPROVED DIRECTION (R24)**. Counts are not approval or completeness evidence.

- R01–R03: immutable owner HTML/live/history/common-hook/supported ACL scope.
- R04: current AWS JSON baseline, not existing ISO implementation.
- R05–R07: guide/current A outcome, duplicate, uncertainty and verified late-receipt semantics.
- R08–R10: acquiring context, synthetic-data boundary and issuer/processor truth separation.
- R11–R22: proposed event/manifest/observation/access/privacy/translation mechanics. R12 and R18 now explicitly qualify task loss versus durable critical notifications. They are not relabeled sourced guarantees.
- R23: D1 private family/boundary selection only; carrier/runtime/crypto realization still proposed.
- R24: approved asynchronous D2 direction, not blanket approval of O5/O6 numerical or operational choices.
- R25: D3 operational/access/retention proposal, still unapproved.

Compared preserved requirements/channel/profile revisions and the prior review. Removal of the synchronous trace-store gate follows the explicit later owner amendment. No weakening of the approved A amount/currency scope, original-key/prior/generation semantics, context authorization, no-redispatch, actor proof, uncertainty or confirmation durability was found. The revised task-durability trade-off is clearly identified and must remain in D3's explanation, not be hidden as a minor editorial change.

Future implemented capabilities still require the common hook and an explicit transition budget/manifest before activation; no future operation is commissioned. No universal ISO/scheme rules, stand-in, partial approval, CNP/3DS, settlement or reversal behavior is invented. Reversal is not a refund/guaranteed hold release; console history is not network management or issuer status inquiry.

## Single next decision and remaining blockers

The coordinator may now present **D3 alone**, identifying this reviewed bundle and explaining:

- committed critical notifications are durable and replayable; intermediate task observations and sampled technical telemetry can have visible losses;
- history/telemetry outages do not themselves gate payments, but required business storage failure or eventual finite outbox saturation can;
- the proposed restricted polling/history, seven-day original-time retention and recovery limits are simulator choices, not measured guarantees;
- Java25/ADOT and full architecture/Security/QA/construction remain unapproved or unverified.

No further technical correction is required before that bounded owner decision. Do not reopen D1 or the approved D2 async direction, or bundle another question. This report asks the owner no question and records no answer.

Remaining downstream blockers: D3 owner disposition; human architecture/construction authorization; exact runtime/layer/key/IAM/transport/storage/failure-destination/cost preflight; triggered independent Security review; independent QA execution and owner acceptance at their respective gates. No unavailable reviewer is impersonated.

Only domain-review.md authored. No other artifact, owner decision, research, fixture, work-item record, source, code or configuration changed. No tests, approvals, commits, external sends, publication or deployment claimed.
