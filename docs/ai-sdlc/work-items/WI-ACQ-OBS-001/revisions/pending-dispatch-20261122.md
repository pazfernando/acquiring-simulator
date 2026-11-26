# Pending coordinator dispatches — WI-ACQ-OBS-001 (Operation A)

Updated 2026-11-22 19:42 America/Bogota.

## Critical path: atomic wiring is still RED

Last valid result: `AwsRuntimeAtomicWiringIntegrationTest` — 9 tests, 6 failures, 1 error, exit 1.
Three consecutive Implementation runs aborted on the return channel; the latest left a truncated log
with no test summary and no exit file, so its outcome is unknown, not green.

**Next dispatch must be split into two short runs:**

1. **Fix run (short).** Diagnose the precondition violation first
   ("dispatch intent requires an undispatched CLAIMED_NOT_SENT claim"), state the cause before
   editing, then fix and run ONLY `AwsRuntimeAtomicWiringIntegrationTest`, writing the exit file and
   stdout/stderr immediately. No test weakening, no deletion, no skipping.
2. **Verification run.** Freeze, run the focused suites plus the runtime-level cases, verify an empty
   hash diff afterwards, then write RESULTS / MANIFEST / PROGRESS and append to
   `implementation-progress.md`.

Evidence directory already exists: `evidence/t3-atomic-wiring-20261122/` — preserve it, add to it.

## Contract deltas: revised; D-5 remains the last open conflict

Revision delivered: generation split, retry semantics, late-evidence correlation, mandate
propagation, OBS-23/24 audit all folded in; prior bytes preserved under
`previous-version/pre-domain-revision-20261122/`. Bundle now
`6b1e525f9071ca7f6117386da0ac8a0bb8f30ee050f7a75963e254e22d08c822`.

**Next dispatch: D-5 reconciliation to DevOps** — how a demo subject obtains a WS-specific token
accepted by the `$connect` authorizer (browser cannot set headers, so the token rides in the
subprotocol), and how `svc-console-ws` is retired or re-scoped so it cannot bypass the demo-subject
requirement. Output `design/access-realization/d5-ws-issuance.md`; it must not edit the bundle.

- bundle `6b1e525f9071ca7f6117386da0ac8a0bb8f30ee050f7a75963e254e22d08c822`
- clause index `62c76ec6d8609f4120d192901a8cf83a0ce3e1c71e6a32101bbb34d0fc1428ab`
- domain review of the prior revision `81c258c8a25c8d107e71621f4b3409de7a44ef6521f3edf885049ff20b5f7d22`

## Later, still unbuilt

Console UI, WebSocket subsystem with its D-5 conditions, Terraform/CI, deployment, independent QA,
and Security re-review of the corrected retention draft. NV-05, SR2-01..04, NV-01..04 and AD-01 all
remain open. Zero end-to-end integrated payments until the wiring proves one.
