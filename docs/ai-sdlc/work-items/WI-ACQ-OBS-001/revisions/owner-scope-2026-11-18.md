# WI-ACQ-OBS-001 — Cross-cutting transaction console, traceability and channel ACL

- Product: Acquiring Processor Simulator
- Status: Draft; owner scope recorded, requirements pending
- Human Product Lead: Fernando Paz
- Date: 2026-11-18

## Owner request and decisions

Owner requested an HTML interface to submit a simulated POS transaction through an intermediate service to the simulator, with each task recorded in a database and visible from the originating screen.

Literal follow-up:
1. "para todo, no solo para operación A, es decir es una especie de hook que debe incluirse en cualquier avance del score que vayamos implementando"
2. "de acuerdo" — live updates without page reload.
3. "de acuerdo" — persistent transaction history and details.
4. "puer es un anticorruption láser, la idea es que reciba lk qué reciba el envía iso 8583 a nuestro simulador"

Interpretation of item 4: anti-corruption layer (ACL) adapting supported channel inputs to ISO 8583 at the simulator boundary. This is an owner-selected boundary direction, not an approved wire profile. "Whatever it receives" does not imply accepting arbitrary/unvalidated payloads.

## Goal and boundaries

- HTML POS initiation plus live transaction timeline and persistent history.
- Traceability is a cross-cutting obligation for all implemented and future simulator capabilities, not just Operation A. This does not commission implementation of all future business operations now.
- Each implemented capability must report task execution through a common instrumentation contract. Requirements must address correlation, attempts, provenance, partial observations and asynchronous/late evidence without inventing payment outcomes.
- Channel ACL produces ISO 8583 for the simulator. Edition/profile, MTIs, fields, encoding, framing/transport, response mapping and supported channel contracts remain unresolved; generic inquiry MTIs must not be invented. Not every future auxiliary capability necessarily has an ISO 8583 mapping.
- Preserve synthetic-data-only boundaries and existing payment semantics. No real credentials, live network connectivity, publication or deployment authorized.
- Current AWS implementation uses HTTP JSON ingress and a synthetic internal downstream contract. This request is a new delta, not evidence that ISO 8583 is already implemented. Do not rewrite prior approval artifacts.

## Sources

- ../WI-ACQ-AUTH-001/WORK_ITEM.md
- ../WI-ACQ-AUTH-001/aws-architecture-update.md
- ../../sources/imports/2026-11-18/acquiring_simulator_functional_guide.md, especially sections 3 and 13
- ../../sources/imports/2026-11-18/software_components_aws.md

## Requirements — ai-sdlc-requirements

- Document: requirements.md — PENDING
- Scenarios: acceptance-scenarios.md — PENDING
- Blocking questions: ISO profile and channel/response contracts; trace-persistence failure semantics; remaining architecture choices require explicit disposition.

## Independent domain review

- Reviewer: NOT REQUESTED
- Verdict: NOT REQUESTED

## Human architecture decision

- ACL direction and live/history capability chosen by owner; detailed interfaces, event/storage/state model and failure behavior PENDING.

## Implementation and verification

- Implementation: NOT AUTHORIZED by this record; specification only.
- QA: PENDING; no tests executed.
- Security: triggered by new UI, interfaces and trace data; independent review PENDING.
- Human acceptance: PENDING.
