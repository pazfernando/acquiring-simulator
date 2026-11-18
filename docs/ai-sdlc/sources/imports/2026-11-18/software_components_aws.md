# Acquiring Simulator — Software Components & AWS Architecture

**Version:** 0.3.0 | **Status:** Evolving design | **Functional source:** `acquiring_simulator_functional_guide.md` v0.5.2

## Scope and guardrails

Multi-acquirer processor simulation: **A** authorization, **B** authorization reversal (including recovery), **C** transaction status inquiry. POS/CP EMV-CVM and e-commerce/CNP 3DS evidence are synthetic. Initial network route: simulated Mastercard, with a **single-message profile to be specified**. No live card credentials, network certification, clearing, settlement, reconciliation, capture or refunds in this slice.

**Design rule:** Keep functional responsibilities distinct from AWS deployables. Prefer the fewest low-cost AWS services; do not create a Lambda per logical responsibility. Network and issuer simulators **must remain separate** to reproduce independent failures.

## Components and AWS mapping

| Logical component / responsibility | AWS deployable | Notes |
|---|---|---|
| POS and e-commerce test channels | Lambda test client / fixtures | Synthetic requests; no POS UI initially |
| Channel ingress and authentication | API Gateway HTTP API | HTTP contract; restrict test access |
| Transaction orchestration, validation/risk, acquirer-aware routing, Mastercard adapter | **Processor Lambda** | Internal modules, not separate Lambdas |
| Reversal/recovery and transaction inquiry | **Same Processor Lambda** | Separate operations and rules; status reflects recorded evidence |
| Acquirer/merchant/terminal registry, transaction journal, idempotency and scenario configuration | **DynamoDB on-demand** | Conditional writes; acquirer isolation; original/reversal correlation |
| Mastercard network behavior | **Mastercard Simulator Lambda** | Delay, drop/invalid response, network outage, timeout, stand-in provenance where configured |
| Issuer authorization behavior | **Issuer Simulator Lambda** | Approval, decline, unavailable, delayed decision; independent of network delivery |
| CNP authentication evidence | **Optional 3DS Simulator Lambda** | Synthetic 3DS outcomes, not a certified 3DS Server/ACS |
| Logs and metrics | **CloudWatch** | Retention limits; redact synthetic credentials where appropriate |

**Invocation:** Channel → HTTP API → Processor Lambda → Mastercard Lambda → Issuer Lambda. The Processor Lambda reads/writes DynamoDB. A test client may invoke Lambda directly when HTTP behavior is not under test.

## Essential failure behavior

| Scenario | Expected processor behavior |
|---|---|
| Issuer declines and reply arrives | Record **DECLINED**, with issuer provenance |
| Issuer approves and reply arrives | Record **APPROVED**, with issuer provenance |
| Issuer approves but Mastercard loses reply | Record **UNKNOWN**; never convert to DECLINED; evaluate recovery/reversal under defined rules |
| Mastercard unavailable or times out after dispatch | Record **UNKNOWN** unless non-delivery is provable; preserve correlation and evidence |
| Duplicate authorization / repeated reversal | Apply idempotency and original-reference rules; do not blindly send a second instruction |
| Reversal response lost | Preserve unconfirmed reversal outcome; inquiry reports latest **known** evidence |

The **functional guide §13** is authoritative for the complete scenario definitions; this document only maps them to software and AWS.

## Architecture artifacts and evolution

- **C1** — system context: actors and processor boundary; revise only when business boundaries change.
- **C2** — AWS deployables: **candidate**, evolves with implementation.
- **C3** — modules within deployables: add incrementally as contracts and code stabilize.
- Mermaid is the editable source; regenerate PNG from the matching version.

Existing diagram files in the shared Drive folder: `c1-system-context.mmd`, `c1.png`, `c2-aws-containers.mmd`, `c2.png`. Diagrams are **working proposals**, not implementation evidence.

## Open design decisions

1. Specify the **synthetic Mastercard message contract** (fields, correlation IDs, response/error semantics, timeout boundaries).
2. Define explicit **transaction/reversal transitions**, retry and recovery rules from functional scenarios.
3. Confirm test access controls and Lambda timeout/latency simulation limits; use async fault injection where a delay exceeds invocation limits.
4. Validate AWS costs and runtime boundaries during implementation; add SQS/EventBridge/Step Functions only if a demonstrated scenario requires them.

**Single source of truth:** functional behavior → functional guide; logical components and AWS mapping → **this file**; architecture drawings → versioned Mermaid + PNG. Retire `software_component_catalog.md` and `aws_component_mapping.md` after this file is accepted.

