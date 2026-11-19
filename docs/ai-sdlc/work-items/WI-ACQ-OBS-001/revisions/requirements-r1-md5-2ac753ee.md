# WI-ACQ-OBS-001 — Draft requirements

Status: DRAFT / NOT APPROVED. Date: 2026-11-18. Product: Acquiring Processor Simulator.
This English specification and its companion acceptance-scenarios.md are analysis deliverables only. No construction, architecture approval, deployment or acceptance is implied. Independent domain review and triggered Security review remain required; QA has not executed these scenarios.

## Authority and source identities

References below are repository-relative:
- **O**: docs/ai-sdlc/work-items/WI-ACQ-OBS-001/WORK_ITEM.md, MD5 67f9979245fba660bc3603de43b2fe99; Owner request and decisions / Goal and boundaries. Exact scope authority, including its literal human statements.
- **F**: docs/ai-sdlc/sources/imports/2026-11-18/acquiring_simulator_functional_guide.md v0.5.2, MD5 69ca7e39cc0cfd595eb19c28c5087c15; working draft, cited sections below are functional evidence, not universal scheme rules.
- **W**: docs/ai-sdlc/sources/imports/2026-11-18/software_components_aws.md v0.3.0, MD5 8e9f4041221835e6c54eb75651ccbbba; evolving design.
- **I**: docs/ai-sdlc/work-items/WI-ACQ-AUTH-001/aws-architecture-update.md; current implementation delta, not approval of this work item.

Precedence: O defines commissioned scope; I describes current implementation; F defines referenced business semantics; W is design context. O supersedes W's “no POS UI initially” for this new draft only. No previous artifact is amended.

## User story, actors and trigger

As an authorized simulation operator acting for an allowed acquirer/merchant/terminal context, I initiate an implemented synthetic POS operation in HTML and follow its tasks, evidence and latest known outcome live and through persisted history.

Trigger: operator selects an implemented capability and submits validated synthetic input. Actors: operator/browser, channel ACL, simulator processor, authorized task producers, separately simulated network and issuer, observation/history reader. Processor knowledge and simulator-internal truth are separate evidence domains, even when an authorized diagnostic view can display both.

Current implemented business scope is Operation A authorization. The hook obligation covers every present/future implemented capability, including non-payment auxiliary work where applicable; it does not authorize implementing reversal, transaction inquiry, CNP/3DS or all future operations. Reading console history is not Operation C. No real credentials, live scheme connectivity, country rules, capture, settlement, refund, automatic recovery or new retry behavior is commissioned.

## Preconditions and proposed input contract

Authorized subject-to-context registration and active synthetic acquirer, merchant, terminal and credential fixtures are prerequisites (I). Browser-supplied identity is not authority. An implemented capability must have a declared channel contract, validation rules, event producers and supported adaptation profile before submission is enabled.

Proposed channel envelope (A01/A02, not an approved API): contract version, supported operation, client submission/idempotency reference, acquirer/merchant/terminal references, synthetic credential fixture reference, amount/currency where relevant, and allowlisted synthetic POS entry/CVM/EMV fixture indicators. Preserve existing A validation, USD and idempotency constraints rather than silently generalizing them. Caller cannot choose permissions, fault injection or downstream routing credentials. Transport endpoint, exact schema and errors remain Q1/Q3.

## Rules and provenance

Each numbered R row is one rule for counting. SOURCED is backed by the cited scope or current product evidence, not an assertion that a feature exists. DESIGN ASSUMPTION is a simulator proposal requiring human decision. UNRESOLVED rows identify blocked contracts. A proposal never overrides a sourced business constraint.

| ID | Provenance | Requirement / expected observable behavior | Reference |
| --- | --- | --- | --- |
| R01 | SOURCED | Provide HTML synthetic POS submission, live updates without reload, and persistent transaction history/details. | O owner request 1–3 |
| R02 | SOURCED | Require a common task instrumentation contract for all implemented capabilities and future additions; do not enable unimplemented operations. | O boundaries |
| R03 | SOURCED | Place a channel ACL between supported channel input and ISO 8583 at the simulator boundary; validate supported contracts rather than accept arbitrary payloads. | O request 4 / boundaries |
| R04 | SOURCED | Treat current HTTP JSON ingress and synthetic downstream JSON as the baseline needing adaptation, not evidence of ISO 8583 implementation. | I Implemented AWS boundary / Synthetic contract |
| R05 | SOURCED | Preserve separate processor rejection, issuer approval, issuer decline and unconfirmed outcome with actual provenance; missing/dropped/invalid response is not an issuer decline. | F §5 A, §13 NET-01–05 / ISS-01–05; I |
| R06 | SOURCED | Preserve existing A idempotency and no automatic redispatch; a retry must not create an unintended second authorization. | I authority / PROC-01; F §13 |
| R07 | SOURCED | Accept only verified correlated late evidence as payment confirmation, retain original references, apply it idempotently and do not regress confirmed state to uncertainty. Issuer-only truth is not proof of processor receipt. | F §13 NET-04/05, ISS-04/05; I |
| R08 | SOURCED | Enforce authorized acquirer/merchant/terminal context, synthetic-only inputs and existing registered access; never cross-use acquiring credentials. | O; F §1, §4.2; I |
| R09 | SOURCED | Do not store prohibited sensitive authentication data or genuine payment secrets; no live PAN/CVV/PIN/cryptograms, OTPs or challenge answers. | O; F §4.1/4.2, §11 |
| R10 | SOURCED | Preserve logical network/issuer separation and actual decision provenance; diagnostic issuer evidence cannot replace processor-known outcome. | F §13; W essential failure behavior; I |
| R11 | DESIGN ASSUMPTION | Use stable transaction ID, unique submission/processing attempt ID and immutable event ID. Link operation ID/original transaction when applicable, task execution ID, parent task/event and retry-of attempt. Repeated submissions remain distinct attempts on the same operation where existing idempotency says so; never use timestamps as identity. | A01 |
| R12 | DESIGN ASSUMPTION | Every executed task emits started and terminal evidence where observable, with task name/version, capability, producer component/instance, actor role, context, correlation, safe outcome/reason and provenance (observed versus inferred). Missing terminal evidence means incomplete observation, not fabricated task failure. Nonexecuted tasks are explicitly skipped/not applicable only with evidence. | A01 |
| R13 | DESIGN ASSUMPTION | Persist event occurrence and collection times, producer sequence when available and causal links; record start/end and same-clock elapsed duration when measurable. Mark missing/estimated timing and clock skew; collection order does not establish causal or cross-host latency truth. | A01 |
| R14 | DESIGN ASSUMPTION | Maintain business state independently from observation completeness and connection state. Expose observation complete/partial/unknown against a versioned expected-task manifest and ingestion watermark, with missing producers/tasks; “complete” is as-of evidence and can change if a later gap is detected. | A01 |
| R15 | DESIGN ASSUMPTION | Show a correlated live timeline with task statuses, provenance, timings, latest business result and independent freshness/completeness indicators. A task success is not payment approval; socket closure or green UI is not transaction finality. | A01 |
| R16 | DESIGN ASSUMPTION | Deduplicate by producer/event identity; exact replay has no additional business or display side effects. A conflicting payload for an existing event ID is quarantined/reported as observation integrity failure, not silently overwritten or accepted as payment evidence. | A01 |
| R17 | DESIGN ASSUMPTION | Append late/out-of-order evidence with both times; rebuild authorized projections by causality/version rules without deleting earlier uncertainty or creating a second purchase. Task events alone cannot authorize payment state changes outside verified business evidence handling. | A01 |
| R18 | DESIGN ASSUMPTION | Persist sanitized events and links durably and serve history/details after reload/restart. Reconnect from an authorized opaque cursor, merge replay/live by event ID, and resync history on expired/gapped cursors; display gaps until reconciled. Pagination must not silently omit events under concurrent appends. | A01 |
| R19 | DESIGN ASSUMPTION | Apply server-side context authorization to submit, list, detail, live subscription, replay/cursors and evidence views; prevent identifier guessing, context switching and stale subscriptions from leaking records. Recheck revoked access and stop subsequent delivery. No automatic global issuer-truth access. | A01/A05; derives from R08 |
| R20 | DESIGN ASSUMPTION | Store/display only allowlisted trace metadata and synthetic fixture IDs, not raw channel/ISO payloads, JWTs, secrets, full credential values, PIN blocks or authentication blobs even if synthetic. Sanitize errors and task labels; render all supplied text as inert text. | A05; stronger trace policy than R09 |
| R21 | DESIGN ASSUMPTION | ACL request translation preserves supported operation meaning, context, amount/currency, idempotency and reference semantics; validate lengths/encoding/required fields against the selected versioned profile before dispatch. Unsupported/lossy mapping produces an explicit adapter rejection and safe trace, with no dispatch. | A02/A03 |
| R22 | DESIGN ASSUMPTION | ACL response translation validates profile, correlation and trusted provenance before yielding the allowed channel result; distinguish channel/adapter failure, explicit processor/network rejection and issuer decision. Invalid/unmapped replies after possible dispatch preserve uncertainty, not fabricated issuer decline. | A02/A03 |
| R23 | UNRESOLVED | Exact supported input schema, response/error schema and ISO profile (edition, MTIs, fields, encoding, framing, transport and mapping) need explicit selection and request/response test vectors. No generic transaction inquiry MTI is defined; future auxiliary capabilities need not have ISO mapping. | Q1; O; F §3 |
| R24 | UNRESOLVED | Select trace persistence failure behavior before/after payment dispatch, durability guarantee and retry/buffering limits; inability to trace must never falsify outcome or silently redispatch. | Q2 |
| R25 | UNRESOLVED | Select access matrix, visibility of internal actor evidence, retention/deletion, live freshness objective and recovery limits; detailed interface/event/storage choices require human architecture disposition. | Q3 |

Counts: 25 rules = 10 SOURCED, 12 DESIGN ASSUMPTION, 3 UNRESOLVED.

## Candidate task and response vocabulary (A01/A02)

Task execution statuses: STARTED, SUCCEEDED, FAILED, SKIPPED; absent evidence remains unknown, never synthesized FAILED. Proposed stages: receive, authenticate/context-check, validate, idempotency-check, request-adapt, dispatch, network/issuer tasks where instrumented, response-adapt, business-persist and channel-response. This list is a capability-specific manifest candidate, not a mandatory deployment topology. Producers unable to report are shown as gaps; unauthorized issuer diagnostics are not exposed.

For A, retain existing public APPROVED, DECLINED, PROCESSOR_REJECTED and NOT_CONFIRMED semantics. NOT_CONFIRMED means business UNKNOWN, not decline. In-flight and not-dispatched input/adapter errors are processing dispositions, not issuer outcomes. Response envelope proposal: permitted operation/transaction reference, attempt/correlation reference, known business outcome and as-of/provenance, separate channel error and observation status. Never expose private issuer-table data through a normal processor response. New live/history service failures remain observation failures.

## Proposed human decisions — not defaults in force

- **A01 — Observation contract:** adopt R11–R18 with append-only sanitized evidence and versioned manifests. Define task versus attempt explicitly; redelivery of an event is not a processing attempt. No mandatory broker, database, tracing vendor or new AWS service is selected.
- **A02 — First supported channel:** HTML synthetic CP authorization only, versioned structured input/response; instrument every currently executed A task plus ACL. Future capabilities must supply manifests and supported adapters before activation.
- **A03 — Simulator-only wire candidate for Q1:** choose a named, versioned, private ISO 8583 authorization profile, using 0100/0110 only if explicitly accepted as that profile's MTIs (F §3 common examples, not scheme mandates). Candidate logical data: processing intent, amount/currency, synthetic credential identifier, acquirer/MID/terminal, request correlation and response decision/reference. Exact DE assignments, widths, character sets, bitmap/packing, frame/transport and synthetic credential representation remain to be specified and accepted with golden vectors. Reject unknown message types; no 0800/0810 transaction inquiry mapping, scheme certification, stand-in or single-message debit posting claim. Existing synthetic JSON is not this wire profile.
- **A04 — Persistence failure preference for Q2:** compare (a) fail closed before dispatch unless durable trace admission succeeds; after possible dispatch return/preserve known result or uncertainty and flag degraded trace, never roll back external effects; versus (b) allow processing with bounded durable buffering and explicit observation degradation. Recommend (a) for the simulator. A buffer's outage, recovery, capacity and loss guarantees still require architecture decision; do not promise recovered events when no durable copy exists. Business journal persistence failure is distinct from trace-store failure: after possible dispatch report uncertainty unless verified durable business evidence supports a result.
- **A05 — Access/data policy for Q3:** propose subject-bound context visibility, separate diagnostic permission for issuer evidence, metadata-only traces and a seven-day trace-retention starting proposal (not inherited from CloudWatch log retention). No selected retention, deletion or export policy exists yet; deleting traces must not break business idempotency/original references.
- **A06 — Service objectives for Q3:** propose healthy live visibility within two seconds of durable event collection and replay recovery within ten seconds for a bounded test dataset of 100 transactions / 1,000 events. These are simulator acceptance targets awaiting approval, not existing guarantees or payment response deadlines. I's two-second payment timeout is separate.

## Prioritized blocking questions (maximum three)

1. **Q1 — Interface/profile:** Will the owner adopt A02/A03, and which exact versioned channel request/response and ISO wire profile, fields, MTIs, packing/framing/transport and evidence validation vectors are approved? This blocks ACL construction; a candidate label is not a completed profile.
2. **Q2 — Durability/failure:** Which A04 option is accepted, including pre-dispatch admission, post-dispatch trace loss, business-journal failure, buffering/recovery guarantees and operator-visible error contract? This blocks failure-path construction.
3. **Q3 — Access and operational contract:** Which context/diagnostic access matrix, retention/deletion limits and freshness/reconnect bounds (A05/A06) apply, and what detailed architecture is approved? This blocks interface/storage/access implementation and measurable nonfunctional acceptance.

## Handoff and limitations

Companion scenarios link every R rule and identify conditional proposals. Independent domain review must inspect this exact revision and payment distinctions before construction; human architecture disposition and triggered Security review remain pending. No source/profile approval is inferred. Configured MCP availability is incomplete per runtime instructions; this draft uses local product evidence only and makes no MCP-backed success claim. No code, Git commit, external send or test execution is part of this deliverable.
