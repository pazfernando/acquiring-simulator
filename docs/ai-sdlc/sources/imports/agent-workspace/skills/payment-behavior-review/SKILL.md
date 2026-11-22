---
name: payment-behavior-review
description: Cross-agent checklist for drafting or independently reviewing Authorization, Authorization Reversal and Transaction Status Inquiry behavior in the Acquiring Processor Simulator. Use when creating payment scenarios, reviewing business semantics, or deriving QA coverage.
---

# Payment Behavior Review

**Use by:** Requirements Analyst, Acquiring Domain Reviewer, QA. Each keeps its independent judgment.  
**Source:** `acquiring_simulator_functional_guide.md`, approved work item, applicable documented interface profile.

## Procedure
1. Identify operation, origination channel, represented acquirer, merchant context and selected simulator/network profile.
2. Trace request, observable response, references/correlation and state before/after. Keep issuer decisions separate from processor rejections and unconfirmed network outcomes.
3. Inspect relevant cases: success, decline, malformed input, duplicate, idempotent replay, timeout, retries, wrong acquirer, missing original transaction, late response and query after ambiguity.
4. Apply operation-specific boundaries:
   - **Authorization:** issuer response is not reconciliation/settlement; unknown is not a decline.
   - **Reversal:** original authorization linkage, duplicates and ambiguous outcomes; not a refund.
   - **Status Inquiry:** identify what internal/external evidence supports the status; do not mislabel ISO 8583 network-management messages as transaction inquiry.
   - **CNP/3DS:** authentication outcome is separate from authorization, included only if explicitly scoped.
5. Label every nontrivial rule **SOURCED**, **SIMULATOR ASSUMPTION** or **UNRESOLVED**; provide references. Never infer universal scheme behavior from an example protocol.

## Compact output
`Flow | scenario | expected behavior | rule status/reference | gap | verdict (PASS / CONDITIONAL / BLOCKED)`

Stop and request human decision for unresolved critical business rules. QA must independently execute tests; checklist completion is not proof of test execution.
