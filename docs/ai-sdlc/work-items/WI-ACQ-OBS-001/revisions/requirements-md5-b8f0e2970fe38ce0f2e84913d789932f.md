# WI-ACQ-OBS-001 — Draft requirements

Status: D1 family/boundary SELECTED; revised construction detail DRAFT, D2/D3 PENDING. Date: 2026-11-18. Product: Acquiring Processor Simulator.
This English specification and its companion acceptance-scenarios.md are analysis deliverables only. No construction, architecture approval, deployment or acceptance is implied. Independent domain review and triggered Security review remain required; QA has not executed these scenarios.

## Authority and source identities

References below are repository-relative:
- **O**: docs/ai-sdlc/work-items/WI-ACQ-OBS-001/revisions/owner-scope-2026-11-18.md, MD5 67f9979245fba660bc3603de43b2fe99; Owner request and decisions / Goal and boundaries. Immutable scope authority, including its literal human statements. Current WORK_ITEM.md is the evolving receipt, not this immutable source.
- **F**: docs/ai-sdlc/sources/imports/2026-11-18/acquiring_simulator_functional_guide.md v0.5.2, MD5 69ca7e39cc0cfd595eb19c28c5087c15; working draft, cited sections below are functional evidence, not universal scheme rules.
- **W**: docs/ai-sdlc/sources/imports/2026-11-18/software_components_aws.md v0.3.0, MD5 8e9f4041221835e6c54eb75651ccbbba; evolving design.
- **HD**: docs/ai-sdlc/work-items/WI-ACQ-OBS-001/owner-decisions.md, MD5 28dd67ac104bf38d7ccc018226b37f9c; D1 selection approved only, D2/D3 pending.
- **I**: docs/ai-sdlc/work-items/WI-ACQ-AUTH-001/aws-architecture-update.md; current implementation delta, not approval of this work item.

Precedence: O defines commissioned scope; I describes current implementation; F defines referenced business semantics; W is design context. O supersedes W's “no POS UI initially” for this new draft only. No previous artifact is amended.

## User story, actors and trigger

As an authorized simulation operator acting for an allowed acquirer/merchant/terminal context, I initiate an implemented synthetic POS operation in HTML and follow its tasks, evidence and latest known outcome live and through persisted history.

Trigger: operator selects an implemented capability and submits validated synthetic input. Actors: operator/browser, channel ACL, simulator processor, authorized task producers, separately simulated network and issuer, observation/history reader. Processor knowledge and simulator-internal truth are separate evidence domains, even when an authorized diagnostic view can display both.

Current implemented business scope is Operation A authorization. The hook obligation covers every present/future implemented capability, including non-payment auxiliary work where applicable; it does not authorize implementing reversal, transaction inquiry, CNP/3DS or all future operations. Reading console history is not Operation C. No real credentials, live scheme connectivity, country rules, capture, settlement, refund, automatic recovery or new retry behavior is commissioned.

## Preconditions and proposed input contract

Authorized subject-to-context registration and active synthetic acquirer, merchant, terminal and credential fixtures are prerequisites (I). Browser-supplied identity is not authority. An implemented capability must have a declared channel contract, validation rules, event producers and supported adaptation profile before submission is enabled.

Proposed channel envelope (A01/A02, not an approved API): contract version, supported operation, client submission/idempotency reference, acquirer/merchant/terminal references, synthetic credential fixture reference, amount/currency where relevant, and allowlisted synthetic POS entry/CVM/EMV fixture indicators. Preserve existing A validation, USD and idempotency constraints rather than silently generalizing them. Caller cannot choose permissions, fault injection or downstream routing credentials. Concrete candidate schema, endpoints and error behavior: channel-contract.md C1–C4; D1 family/boundary selection approved; detailed harmonization and D2/D3 remain pending.

## Rules and provenance

Each numbered R row is one rule for counting. SOURCED is backed by the cited scope or current product evidence, not an assertion that a feature exists. DESIGN ASSUMPTION is a simulator proposal requiring human decision. D1 selection is human-approved; revised C1–C3 correction details and D2/D3 architecture remain proposed, not self-approved. A proposal never overrides a sourced business constraint.

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
| R23 | APPROVED SELECTION | Use private authorization-only SIM-ISO87A-v1 and HTTPS ACL/simulator boundary per owner-decisions.md D1, including reviewed P1–P6 (P2a/P3a/P5/P6) and channel C1–C3. Revised P7/C4 carriage and C3 response harmonization remain PROPOSED architecture detail; D2/D3 not approved. Reject unsupported operations; no generic inquiry mapping. | HD D1; O; F §3; structural research |
| R24 | DESIGN ASSUMPTION | Apply observation-contract.md O3: durable admission fail-closed; after possible dispatch no redispatch/rollback, separate journal from trace failure and preserve durable known evidence or uncertainty with explicit gaps. | D2 |
| R25 | DESIGN ASSUMPTION | Apply observation-contract.md O1–O4: common hook activation, context restricted polling/history, separately permissioned issuer diagnostics, seven-day traces independent of business references, bounded replay and measurable freshness. | D3 |

Counts: 25 rules = 10 SOURCED, 14 DESIGN ASSUMPTION, 1 APPROVED SELECTION, 0 UNRESOLVED row labels. R23 includes explicitly pending correction-detail qualifications. Human decisions D2/D3 remain pending; rule counts do not establish specification completeness or approval.

## Candidate task and response vocabulary (A01/A02)

Task execution statuses: STARTED, SUCCEEDED, FAILED, SKIPPED; absent evidence remains unknown, never synthesized FAILED. Proposed stages: receive, authenticate/context-check, validate, idempotency-check, request-adapt, dispatch, network/issuer tasks where instrumented, response-adapt, business-persist and channel-response. This list is a capability-specific manifest candidate, not a mandatory deployment topology. Producers unable to report are shown as gaps; unauthorized issuer diagnostics are not exposed.

For A, retain existing public APPROVED, DECLINED, PROCESSOR_REJECTED and NOT_CONFIRMED semantics. NOT_CONFIRMED means business UNKNOWN, not decline. In-flight and not-dispatched input/adapter errors are processing dispositions, not issuer outcomes. Response envelope proposal: permitted operation/transaction reference, attempt/correlation reference, known business outcome and as-of/provenance, separate channel error and observation status. Never expose private issuer-table data through a normal processor response. New live/history service failures remain observation failures.

## Current decision scope and correction handoff
D1 APPROVED only as recorded by owner-decisions.md: full private authorization wire family P1–P6 including P2a/P3a/P5/P6 and HTTPS C1–C3, not execution, production compatibility, D2/D3 or architecture approval. The latest domain review 355fb90c0d48266c53e41b23ef7526af made D1 selection decision-ready and identified C1–C3 construction corrections. Revised P7/C4 signed-evidence carrier and consolidated C3 are PROPOSED technical harmonization requiring renewed independent review. No approval inherited by crypto or storage realization. Existing AUTH-001 pending P01–P08 details remain proposed where not expressly selected under this D1; existing approved business cores remain unchanged.
D2 pending: durable trace admission fail-closed, send-state/confirmation/failure policy O3/O3a with C3 precedence. D3 pending: instrumentation implementation, polling/storage/index/access/retention/freshness O1/O2/O4/O2a. Common hook obligation itself remains sourced owner scope, not optional. No future operation implementation is commissioned.
Historical reviewed scopes/drafts are under revisions/ by content hash. The latest original N-review is revisions/domain-review-md5-a65b92f91c36a51484ee91f7f009f44a.md; current domain-review.md remains 355fb90c0d48266c53e41b23ef7526af. Current corrected files have fresh hashes returned in handoff and require independent re-review. Do not cite a current file with a historical hash.

## Single next decision — D2, after correction re-review
Recommend fail-closed durable trace admission before send; after possible send no automatic redispatch/rollback. Preserve readable committed evidence, otherwise distinguish current known possible-send NOT_CONFIRMED from unreadable restart UNAVAILABLE, and expose trace gaps. Trace admission outage blocks new authorizations and may leave a reserved unsent operation; no automatic resend. D2 question is in decision-proposal.md; D3 follows separately. Do not ask owner to design technical fields.

## Handoff and limitations
C1 correction: P7/C4 and carrier/evidence fixtures bind exact response bytes and signature material to trusted actor/relay with bounded normal/late carriage. C2: one comprehensive C3 schema/status/precedence with exact response-vectors.json, O3/O3a subordinate. C3: S04/README distinguish 826 admission vs999 packing, capacity priors never redispatch, full selected sections referenced. These are submitted corrections, not reviewer-closed findings. Construction remains blocked by D2/D3 and architecture/Security gates; independent QA not executed. Local documentation checks only; incomplete configured MCP means no MCP-backed verification. No code/config/commits/deployment/publication/external sends.
