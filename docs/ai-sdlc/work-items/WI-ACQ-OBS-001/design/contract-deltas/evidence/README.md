# Operation A contract-delta evidence

## Artifacts and identity convention

- ../operation-a-contract-delta.md: additive DRAFT, thirteen delta IDs, not a modified approved contract.
- clause-index.json: exact source texts with file/line ranges/SHA-256; exact owner decision texts and hashes; current versus candidate wording, classification and required review/disposition for each ID. A-2 additionally records exact W-case JSON pointers and unchanged fixture contents.
- input-identities.json: current input identities at authoring. Approved contracts, owner records, Security reviews, prior design proposals and the retention draft retain their original distinct authority/status. Hashing a proposal does not approve it.
- closing-validation.json: closing source/excerpt identity comparison, document-link checks, classification/count and coverage checks; documentary only, not code or product tests.
- SHA256SUMS: every authored file except this active manifest itself, paths relative to design/contract-deltas/. Its own digest is returned separately to avoid self-reference.

## Binding and runtime

Authorized checkout /home/pazfernando/projects/acquiring-simulator. Historical Implementation-supplied branch feat/wi-acq-auth-001, HEAD cfc456f4f469d9558a78fda8ff9b770b00817315, dirty tree; no fresh Git inspection. See the input-identity entry for evidence/t1-traceability-20261120/BINDING.txt. Scope attaches to the exact document hashes, not a claim of clean HEAD.

Observed current author session: native OpenClaw/Codex, gpt-6.1-sol reported by sessions_list, system family GPT-6, no model override. Provider-qualified identifier not exposed. Session agent:ai-sdlc-requirements:subagent:c537e5bc-0ddb-4d37-b5f5-86dbb451ab13. The reviewer's glm5.3-flash and Implementation's DeepSeek model are not this author's runtime.

## Commissioned pins verified before drafting

- Security rev4 review: c418f93a70fc41fc282ba5c2ea5c28714d14337f02c7d58ee4ecb97c8d123ed2.
- Access design rev4: 3926423e9f42f6949647e5904271b6f6818b977301b66e81ffcf334f10c5dc56.
- Owner overlay at opening: 9bf166feed3eed4c0b4d6d4e441ae1ef44d3826a8bdab0cad65277bcd8127cdb; it advanced during this task to c73ed0aad9dd6ba563755e3b4242f2b25563381fd3a67260608cb8d767429bd6. The original142 lines are preserved in owner-decisions-as-reviewed-9bf166fe.md and exact prefix-hash verified; the new lines144–155 authorize separate local atomic-runtime wiring, not the public access surface. All cited access decisions are unchanged; index clauses now carry current hash plus the initial hash.
- Corrected retention draft: d11ead64f0bd3928b4112b29c408eab1d3e0f9401b84a4580e3d4adb8b35fd76; still a draft, not normative or approved by this task.

Security's PASS with conditions is for the rev4 design delta only. It permits this authoring step, not implementation/deployment or finding closure. Its section14 explicitly requires the contract-change path including ADV-1/2/6 and ADV-8 before implementation. No previous PASS is extended.

## Coverage and citation corrections

Thirteen IDs: D-1..D-9, A-1/A-2 (the two ADV-8 omissions), R-1/R-2 (retention applicability and cursor/lease carry-forward). Classification: 2 SOURCED CLARIFICATION, 10 PROPOSED NORMATIVE CHANGE, 1 OPEN CONFLICT. Counts refer to top-level IDs, not source rows or individual requirements.

Security section10 corrections are explicit: D-6 cites O5/O6, not O7; D-8 does not attribute console.write to C1 (it is in prior relay capability/owner text), distinguishes removed scope from proposed console.dispatch on S2, and describes the payment-initiation authority honestly; D-9 supersedes rev2 section2.8 and rev3 section7, preserving rev3 section3 rotation minima. The prompt's cursor/staleness section10 reference is mapped to rev4 sections3.4/5 and carried rev3 sections5/6; rev4 section10 is decisions/escalation, not invented cursor text.

D-5 remains OPEN CONFLICT: latent svc-console-ws at WS audience versus demo-only connection, and missing explicit public-client WS token issuance. Restrictive proposed rejection/retirement wording is not a claimed accepted authorizer policy. No immediate owner decision is requested; first reconcile within DevOps/domain/Security, escalating only a genuine required behavioral change.

## Missing evidence / disposition

All thirteen IDs require independent acquiring-domain disposition as an integrated exact-hash bundle before normative incorporation. No onward delegation occurred. Security/DevOps must still resolve or verify D-5 accepted class/issuer mapping, D-8 scope and trusted demo-context propagation, public-client PKCE/state/nonce, refresh rotation/reuse/logout, WS log hygiene, per-frame/idle-scheduler revocation, public edge routing/CORS/CSP/rate enforcement and Keycloak rebuild/import behavior. All carried SR2 executed negatives, effective-invoke deny evidence, key/rotation/custody evidence, and independent QA/Security implementation review remain absent. These are listed concretely in document section5; no code, tests, scans, browser exercise or deployed check was performed here.

Retention R-1/R-2 do not settle future business-evidence destruction, compact equivalence, concrete TTL mapping or cursor continuation. Event tombstones alone are not business evidence. Non-destructive seven-day payload expiry needs no new owner choice; a requested future material loss/final horizon would need business trade-off disposition after equivalence/loss assessment. No optional same-snapshot continuation, ticket endpoint, refresh cap, numerical technical rate value or scope migration is promoted by authoring.

Inherited MCP remains incomplete (6 and 2 tools omitted by policy); no MCP-backed success is claimed. Official-source compatibility research belongs to the pinned design/review chain; this task made no new public web request and does not independently certify those facts. No Git/provider commands, approved-file mutation, code/tests, deployment/cloud API/probes, credentials, login, deletion, publication, onward delegation or self-approval. All authored files remain under design/contract-deltas/.

NV-05/AD-01 and all carried Security conditions stay open. No implementation, deployment, acceptance or risk acceptance is created by these artifacts.

Concurrent source drift is acknowledged, not described as all-inputs-unchanged: see owner-record-drift.json and document section7. Opening input-identities.json retains its original observations.

## Bounded domain-review revision — 2026-11-22

Read reviews/contract-delta-20261122/domain-review.md before edits; SHA-25681c258c8a25c8d107e71621f4b3409de7a44ef6521f3edf885049ff20b5f7d22. Overall bundle verdict remains CONDITIONAL; classifications are confirmed, not changed, and this revised exact hash still needs independent review. No condition/finding is self-closed.

G-1 now distinguishes payment-key generation/24h admission from subscription epoch/generation/new snapshot, original7d history, payment30s response and Streams24h transport. G-2 makes prior-first retries, C3 response knowledge and REPLAY/RECOVERED-never-send explicit. G-3 binds late proof and business-version CAS/receipt to the original instruction/payment generation, not current key head, without adding an invented version field to the signed envelope. G-4 proposes console-only immutable authorization-context records plus authenticated svc-console call and ACL strong read/current-mandate/body-hash checks; it is candidate technical mechanics under existing D-8, not a new selected architecture/resource or proof of actual IAM controls. A-2 extends the audit to OBS-23/24 and all dependent service/environment harness semantics, preserving conflict, ACK, cachedA versus fixedH/R/F, retired-control and no-financial-replay invariants. R-1/R-2 expressly leave NV-05 and retention realization OPEN.

D-5 remains OPEN CONFLICT. Its target tables restrict browserWS to console-demo subject/authorized-party with WS-specific audience/ws.observe and reject all service classes. They propose retiring latent svc-console-ws at this surface, but do not invent a verified public-client grant or silently choose token exchange/broker. DevOps must specify permitted issuer configuration and consistent client/grant/authorizer disposition; domain/Security must review it. No immediate owner business question is identified; escalate only a concrete necessary scope deviation.

The complete previous bundle (all8 files including its manifest) remains byte-preserved in ../previous-version/pre-domain-revision-20261122/. previous-version-identities.json verifies each original value. Archived Markdown bytes keep their original relative-link convention from the original bundle root; relocation is archival, not a rewrite of those source links. revision-note-20261122.md provides old/new main/index/README identities; current manifest includes archives and new evidence but excludes only its own active path. revision-input-identities-20261122.json pins newly read review/guardrail sources; closing-validation.json is refreshed for this revision. Opening input-identities.json and original closing validation are still readable in the archived bundle.

guide-exposure-follow-up.json tracks the guide §14 stale exposure paragraph for separate reviewed documentation work; no guide change or14th delta is commissioned. None of the additions is code/test execution, approval, deployment, credential flow, Git/provider action or onward delegation. All writes stay in this bundle. MCP remains incomplete and no MCP-backed work is claimed.
