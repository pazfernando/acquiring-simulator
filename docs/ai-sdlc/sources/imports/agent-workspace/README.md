# AI-SDLC Agent Pack

**Project:** Acquiring Processor Simulator (MVP)  
**Status:** Proposed configuration, v0.1

## Mission and scope
Build a **simulator of a multi-acquirer processor**, not a live acquirer or certified payment switch. Initial independent flows: **Authorization**, **Authorization Reversal**, and **Transaction Status Inquiry**. Inputs are emulated POS, gateway, or API messages. No frontend, live scheme connections, real payment credentials, or reconciliation in this phase. CNP 3DS is conditional and must be scoped explicitly.

## Ownership
- **Human Product Lead:** selects scope, prioritizes, accepts outcomes.
- **Human Solution Architect:** selects architecture, contracts, state models, patterns and deployment decisions.
- **Core agents:** coordinator, requirements, acquiring-domain, implementation, QA, devops.
- **On-demand agent:** security.
- Documentation is handled by a separate existing team; no writer agent here.

## Execution
Human direction → Requirements → **independent** Domain review → human architecture approval → Implementation / DevOps → independent QA (+ Security when triggered) → human acceptance.

Use `WORK_ITEM_TEMPLATE.md` for traceability. Each agent follows its own `agents/<name>/AGENTS.md`. The shared `skills/payment-behavior-review/SKILL.md` is used only when reviewing payment semantics or test coverage.

## Source precedence and uncertainty
Human decisions recorded for this project, approved work items and architecture decisions, then `acquiring_simulator_functional_guide.md` and applicable sourced specifications. Escalate conflicts instead of guessing. All project documents, code comments and diagram labels are in **English**; human conversation may be in Spanish.

**Not a deployment configuration:** Adapt agent registration, tool permissions and delegation to the chosen AI-SDLC runtime. Do not invent supported commands.
