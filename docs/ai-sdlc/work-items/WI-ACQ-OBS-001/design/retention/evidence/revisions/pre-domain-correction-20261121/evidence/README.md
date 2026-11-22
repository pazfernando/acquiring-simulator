# NV-05 retention draft — evidence manifest

## What this evidence establishes

Local documentary inspection and SHA-256 identity checks only. All writes are confined to the assigned design/retention/ directory. The original stub was extended into retention-delta.md; the previous source-identities.json remains byte-preserved. No approved contract, vector, code, test, Coordinator plan/status/WORK_ITEM or Implementation-owned evidence was edited. No Git/provider command, deployment, cloud API call, target probe, credentials, publication or onward delegation was performed. Read-only fetches of official public documentation are not deployed-system evidence.

Runtime: native OpenClaw subagent/Codex; observed current session metadata gpt-6.1-sol, system family GPT-6; no override. Provider-qualified identifier unavailable. Session agent:ai-sdlc-requirements:subagent:c537e5bc-0ddb-4d37-b5f5-86dbb451ab13. Do not copy Implementation's nan/deepseek-v4-flash binding as the analyst model.

Inherited MCP configuration is incomplete (6 and 2 tools omitted by policy). No MCP-backed success, QA result, Security closure, independent domain approval or human acceptance is claimed. Coordinator must commission independent acquiring-domain review of the exact output hash.

## Input identities and binding

- source-identities.json: preserved previous-attempt identities, including the six commissioned pins and Security report.
- source-identities-current.json: freshly recomputed per-file SHA-256 values, prior identities and change flags. Only owner-decisions.md changed relative to that saved inventory: efef3d2272dd54130b404fae43ec90643bc42c5dd4701cc1b6b4e49d4ea0f18c → fd040124319a946d28230aeb3994fb77bac82c00cb59e58b78df9e23531bae27, 80 →107 lines.
- additional-source-identities.json: additional product instruction/project/guide/profile identities used by the draft.
- source-close-check.json: closing rehash against identities read for this draft. No listed input changed during drafting.
- contract-source-excerpts.md: exact line-numbered current contractual texts for verifiable clause lookup; snapshots are not approved-file amendments or newly approved specifications.
- selected-vector-excerpts.json: exact selected existing documentary cases with file and JSON pointer. These are existing specification vectors, not executed tests.
- The actual supplied binding is [Implementation BINDING](../../../evidence/t1-traceability-20261120/BINDING.txt) and its [dirty-tree evidence](../../../evidence/t1-traceability-20261120/git-status-before.txt). These are historical supplied observations, not fresh Git verification.
- The independent early [Security review](../../../reviews/access-design-20261120/security-review.md) remains SHA-256 0d71ea007e56b12d9873df14126c22640c3abc68052b42db1f47b0a9688d218e and is BLOCKED for design-readiness, not a runtime vulnerability finding. Its NV-05 is the commission, not approval of this response.

The source owner overlay accepts notification-only WebSocket without mTLS (line86), selects self-managed Keycloak (line92), and keeps late evidence internal (lines105–107). Those later decisions do not authorize deleting payment identities or close independent Security findings.

## Official source references and retrieval scope

Saved official-source-N.json files contain the tool's complete returned result (including URL, fetch metadata, extraction, truncation status and external-content wrapper where supplied). These snapshots establish only the narrowly cited documentation facts, not any AWS account or Keycloak configuration.

| Snapshot | Official source / relevant clause | Supported fact and limit |
| --- | --- | --- |
| official-source-1.json | https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/TTL.html — TTL introductory behavior and usage considerations | Per-item numeric epoch-seconds TTL; automatic deletion typically within a few days; expired pending rows may still be read/updated; service deletes enter Streams. Not exact deletion SLA, deletion-order proof or access-control enforcement. |
| official-source-2.json | https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/ttl-expired-items.html — Filter expired items from read operations; Conditionally write to expired items; TTL stream deletion discussion | Pending-expiry items must be excluded by application read semantics; they still cost storage/read units until deleted. Conditions can guard expired-row updates; presence is not retention permission. Example TTL renewal code in vendor docs does not override this product's no-renewal rule. |
| official-source-3.json | https://www.rfc-editor.org/rfc/rfc7523.txt — §2.2 and §3 items4/7 | Assertion client authentication is token-endpoint authentication; exp limits validity; jti and replay cache are MAY, cache described for the assertion's valid interval. Does not establish configured Keycloak max age, skew, mandatory replay rejection or a seven-day cache. |

RFC §3 item7 and the selected assertion/profile policy must not be confused: this draft labels stronger replay prevention PROPOSED NEW RULE. No token or proof bytes were fetched or created.

## Explicit missing-evidence list

1. Independent acquiring-domain review of this exact draft and its source overlay; no review verdict/closure is supplied here. Appropriate approval for proposed precision/clock, compaction, race-oracle and replay-cache rules remains pending.
2. Record-to-table/key/TTL-attribute mapping proving the source-range/dedupe durability barrier precedes native TTL eligibility for each applicable payload; transactional conditions, worker race handling, and crash recovery are not inspected or verified here.
3. Proof that all query/detail/notification/repair paths filter logical expiry and retain source-integrity gaps rather than claim complete coverage, including delayed TTL/service-deletion events; no code inspection or tests were commissioned.
4. Locally executed clock-driven QA for the matrix scenarios (7-day boundary, delayed deletion, restart, stream duplicate, concurrent expiry, late C4, wrong prior reference and 24-hour new generation). Documentary vectors are not execution evidence; the Implementation binding's reused suite is not analyst verification.
5. An approved finite business/evidence destruction horizon and proof of sufficient compact replacement, including original instruction equality/result/version, receipt/conflict identity, aliases, allocation and issuer dedupe. No such purge authority is derived from seven-day history approval. Full raw proof versus compact normalized evidence remains a material policy only if destructive compaction is requested.
6. Current per-record physical purge timing/alerting evidence; AWS TTL is asynchronous. O8's <=48h target is still proposed and cannot be certified by local clocks. No deployed observations were made.
7. Realized Keycloak assertion max validity, allowed skew, jti requirement, cluster-wide replay-cache behavior, cache-failure semantics and actual deletion horizon, with independent Security review. No invented assertion/cache duration, issuer provisioning or credentials.
8. Realized WebSocket lease/epoch/control-tombstone invalidation and cursor scope/revision checks under the latest accepted D-D boundary. O6's max15min cursor remains an existing proposal, not a newly approved value.
9. Deadline scope reconciliation: AWS update line34/C2 line17/P4 line77 specify AWS2s versus local30s, while latest owner-decisions.md line103 says approved30s. The separate Implementation timeout fix's documentary RESULTS/MANIFEST/PROGRESS were read and pinned: they report AWS fallback30s based on AUTH R20 and an inactive atomic path. Its code/tests were not inspected or executed here; the developer report is not a new owner scope disposition or independent acceptance. No source supplies an explicit owner scope reconciliation of AWS34 in this task. Existing NOT_CONFIRMED mapping is explicit, but any new enum/migration requirement is absent and must not be invented.
10. Fresh repository branch/HEAD/dirty-tree verification through Implementation if needed at the next gate; this role used only supplied evidence, as instructed.
11. Configured MCP tools omitted by policy (6 and 2) remain unavailable. This does not block local documentary drafting but prevents unsupported claims of MCP-backed source/configuration validation.
12. Independent final Security, QA, deployment compatibility and human acceptance evidence. None is replaced by the draft or hash validation.

## Artifact hashing and handoff

SHA256SUMS records per-file SHA-256 for retention-delta.md and every evidence file other than SHA256SUMS itself; its own digest is returned separately with the handoff. This avoids a circular self-hash. Manifest paths are relative to design/retention/. Hashes prove byte identity, not correctness or acceptance.
