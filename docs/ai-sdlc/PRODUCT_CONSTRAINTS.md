# Product constraints — Acquiring Processor Simulator

This document owns the acquiring simulator's data-safety, prohibited external targets and simulation boundaries, moved here from the former generic role text during the product-context split. It restates existing prohibitions; it does not weaken them, grant scope or approve behavior. Approved scope still comes from the selected `WORK_ITEM.md`, its requirements, domain review and architecture decision.

## Synthetic-only data and prohibited external targets

- Use synthetic-only cards and synthetic transaction data in every environment and demonstration.
- Never connect live card networks or real issuer/network endpoints.
- Never use real PANs, PINs, CVVs, secrets, production payment credentials or production payment data.
- Never claim network certification, scheme conformance or production readiness from simulator behavior.
- Do not commit secrets, tokens or credential-bearing URLs; credentials belong to the runtime/provider credential store.

## Deterministic simulation behavior

- Keep external issuer and network behavior behind deterministic simulators for payment work.
- Cover the approved outcomes explicitly, including timeout and unknown/unconfirmed results, without inventing an issuer decision.
- Distinguish an issuer approval or decline from a processor rejection and from an unconfirmed outcome.
- Label simulator profile values (routes, amount ranges, BIN ranges, card-present metadata, currency default) as simulator profile choices, never as scheme rules or market facts.

## Scope-controlled implementation units

- Implement per-acquirer context, routing, input validation, state transitions and outputs only as approved scope defines them.
- Implement protocol adapters or profile behavior only for explicitly selected protocol profiles. No message family (ISO 8583, ISO 20022 or other) is assumed universally supported.
- Keep ambiguous, duplicate, partial or stand-in handling in scope only when the approved design scopes it.
- Prevent acquiring-identifier or routing-rule leakage across acquirers and merchants.

## Scope-controlled QA checks

Add these checks only for the approved slice, and state any unexecuted path explicitly:

- replay and duplicate-request handling;
- idempotency under retry;
- concurrency behavior;
- wrong-acquirer / cross-tenant isolation;
- reversal correlation (a reversal is not a refund; status inquiry is not network management);
- status and late-evidence behavior;
- fault, timeout and unknown-outcome handling.

Local checks do not replace independent QA, Security review or human acceptance.

## Historical provenance — WI-ACQ-AUTH-001 runtime exception (not active generic policy)

On 2026-11-18 Fernando authorized OpenCode through official ACPX as an alternative to the native Codex-only route **for WI-ACQ-AUTH-001**. The preserved wording required: follow `PROJECT_EXECUTION.md` for that exception; preserve the configured default `nan/deepseek-v4-flash` without delegation overrides; verify the actual ACP runtime/model; and preserve all repository and publication boundaries.

Source of that historical wording: workspace backup `shared/ai-sdlc/backups/product-context-20261126T022309Z/ai-sdlc-implementation/AGENTS.md` (section "Authorized runtime exception — WI-ACQ-AUTH-001 (2026-11-18)"). This is retained as product-specific provenance for that work item only. It is **not** active generic policy, does not select a runtime or model for other products, and does not broaden approved scope. Current Git routing and runtime rules remain those in the shared `PROJECT_EXECUTION.md` and the owning agent role.
