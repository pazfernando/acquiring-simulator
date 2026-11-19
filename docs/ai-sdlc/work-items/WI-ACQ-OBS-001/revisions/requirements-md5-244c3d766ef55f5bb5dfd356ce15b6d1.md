# WI-ACQ-OBS-001 — Draft requirements

Status: DRAFT / NOT APPROVED. Date: 2026-11-18. Product: Acquiring Processor Simulator.
This English specification and its companion acceptance-scenarios.md are analysis deliverables only. No construction, architecture approval, deployment or acceptance is implied. Independent domain review and triggered Security review remain required; QA has not executed these scenarios.

## Authority and source identities

References below are repository-relative:
- **O**: docs/ai-sdlc/work-items/WI-ACQ-OBS-001/revisions/owner-scope-2026-11-18.md, MD5 67f9979245fba660bc3603de43b2fe99; Owner request and decisions / Goal and boundaries. Immutable scope authority, including its literal human statements. Current WORK_ITEM.md is the evolving receipt, not this immutable source.
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

Proposed channel envelope (A01/A02, not an approved API): contract version, supported operation, client submission/idempotency reference, acquirer/merchant/terminal references, synthetic credential fixture reference, amount/currency where relevant, and allowlisted synthetic POS entry/CVM/EMV fixture indicators. Preserve existing A validation, USD and idempotency constraints rather than silently generalizing them. Caller cannot choose permissions, fault injection or downstream routing credentials. Concrete candidate schema, endpoints and error behavior: channel-contract.md C1–C2; human adoption remains pending.

## Rules and provenance

Each numbered R row is one rule for counting. SOURCED is backed by the cited scope or current product evidence, not an assertion that a feature exists. DESIGN ASSUMPTION is a simulator proposal requiring human decision. The candidate resolves field-design gaps; adoption and architecture decisions remain pending, not self-approved. A proposal never overrides a sourced business constraint.

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
| R23 | DESIGN ASSUMPTION | Adopt candidate SIM-ISO87A-v1 P1–P4, channel-contract.md C1–C2 and exact vectors/packed-vectors.json. Reject unsupported operations; no generic inquiry mapping. Candidate supplied, adoption pending. | D1; O; F §3; research structural baseline |
| R24 | DESIGN ASSUMPTION | Apply observation-contract.md O3: durable admission fail-closed; after possible dispatch no redispatch/rollback, separate journal from trace failure and preserve durable known evidence or uncertainty with explicit gaps. | D2 |
| R25 | DESIGN ASSUMPTION | Apply observation-contract.md O1–O4: common hook activation, context restricted polling/history, separately permissioned issuer diagnostics, seven-day traces independent of business references, bounded replay and measurable freshness. | D3 |

Counts: 25 rules = 10 SOURCED, 15 DESIGN ASSUMPTION, 0 UNRESOLVED rule rows. Human decisions D1–D3 remain unresolved approvals; zero field-design gaps is not approval.

## Candidate task and response vocabulary (A01/A02)

Task execution statuses: STARTED, SUCCEEDED, FAILED, SKIPPED; absent evidence remains unknown, never synthesized FAILED. Proposed stages: receive, authenticate/context-check, validate, idempotency-check, request-adapt, dispatch, network/issuer tasks where instrumented, response-adapt, business-persist and channel-response. This list is a capability-specific manifest candidate, not a mandatory deployment topology. Producers unable to report are shown as gaps; unauthorized issuer diagnostics are not exposed.

For A, retain existing public APPROVED, DECLINED, PROCESSOR_REJECTED and NOT_CONFIRMED semantics. NOT_CONFIRMED means business UNKNOWN, not decline. In-flight and not-dispatched input/adapter errors are processing dispositions, not issuer outcomes. Response envelope proposal: permitted operation/transaction reference, attempt/correlation reference, known business outcome and as-of/provenance, separate channel error and observation status. Never expose private issuer-table data through a normal processor response. New live/history service failures remain observation failures.

## Candidate disposition and source/review traceability
All simulator defaults and architecture recommendations in the four companion contracts are PROPOSED. They supersede the earlier open-ended A01–A06 candidate wording only in this work item, not existing approved business cores. Source concepts remain sourced; private field values, canonicalization, aliases, retention, transport and event/storage choices are not scheme rules.
- D1: iso8583-profile.md P1–P4 + channel-contract.md C1–C2 + vectors/ — complete private A-only candidate and channel adoption.
- D2: observation-contract.md O3 — durable trace admission fail-closed and separate durable business evidence conditions.
- D3: observation-contract.md O1/O2/O4 — common hook, polling/storage/index/access/retention/freshness architecture proposal.
- Existing A pending P01–P08 supplements remain explicitly proposed, not newly approved by incorporation. See profile P3 for equality/reference/window details and existing HD cores. AWS two-second deadline supersedes local-harness 30-second default only on AWS.

Immutable original review citations: revisions/requirements-r1-md5-2ac753ee.md (full MD5 2ac753ee2ec5f6a5e8951256c86581a5), revisions/acceptance-scenarios-md5-6b26f7204ee969bb3c74267380277c31.md, revisions/owner-scope-2026-11-18.md (67f9979245fba660bc3603de43b2fe99). Outgoing citation-corrected draft preserved at revisions/requirements-md5-5130314810dbc3a27ab6b9bb7e88d0eb.md. domain-review.md c49694b00907371de7f8fd16e3885f9f and research 7e27138d8d3016f33e55dc2b90b12d07 remain untouched. Review findings refer to those immutable historical bytes, not this newly authored revision.

## Single next owner question
After independent re-review, adopt the documented SIM-ISO87A-v1 private authorization-only profile and its HTTPS channel boundary (D1)? Recommendation: adopt D1 as the simulator-only baseline, with no scheme-conformance claim. D2 and D3 remain subsequent separate decisions; no additional question is presented now. Do not ask the owner to design fields.

## Handoff
See decision-proposal.md for F1–F6 correction evidence. Corrections are submitted, not reviewer-closed. Construction remains blocked pending renewed independent domain review, human decisions/architecture approval and required independent Security review; independent QA has not run. Configured MCP tools are incomplete; only local source/document verification succeeded. No production/test codec implementation, dependency installation, code change, commit, deployment, external send or publication.
