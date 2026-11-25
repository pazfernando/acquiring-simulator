# Contract-delta bounded revision — 2026-11-22

Input: independent domain review SHA-25681c258c8a25c8d107e71621f4b3409de7a44ef6521f3edf885049ff20b5f7d22, verdict CONDITIONAL. This changed bundle remains CONDITIONAL/DRAFT and requires independent review; no condition or implementation gate is self-closed.

- G-1: distinguish payment24h generation from subscription generation/epoch and its mandatory new fixedH/R/F snapshot; never extend/transplant a prior snapshot or payment generation.
- G-2: explicit prior-first retry taxonomy, pinned equality/original-reference and C3 response oracles; REPLAY/RECOVERED are knowledge, never a resend or a new public enum.
- G-3: exact original instruction/payment-generation/proof/response-hash correlation, same-record business-version CAS and atomic receipt/result/event; no late send/reversal or invented signed-version field.
- G-4: concrete proposed console-only immutable request-mandate record; authenticated svc-console transport plus ACL strong lookup/current mandate/body-hash/correlation checks. It is candidate mechanics, not approved schema, resource or verified IAM behavior.
- A-2/G-5: audit OBS-23/24 and service/environment harness language beyond W cases; retain immutable conflict/same-K cachedACK/subscription-generation, ACKA versus fixedH/R/F, epoch, expiry/truncation and no-financial-replay oracles.
- D-5: STILL OPEN CONFLICT. Target issuer/client/authorizer matrix is explicit; exact safe WS-specific public-client issuance and retirement/re-scope of svc-console-ws still require DevOps/domain/Security disposition. No current owner question, token-exchange endpoint or broker fallback is invented.
- R-1/R-2: NV-05 and retention realization remain OPEN. Guide §14 exposure staleness is tracked for separate reviewed documentation work, not a14th delta or guide edit.

Classifications unchanged:2 SOURCED CLARIFICATION,10 PROPOSED NORMATIVE CHANGE,1 OPEN CONFLICT; total13. No code/tests, Git/provider commands, approved-file mutation, deployment, credentials, onward delegation, self-approval or model override. MCP remains incomplete; documentary checks only.

## Preserved and current identities

The complete prior eight-file bundle is under ../previous-version/pre-domain-revision-20261122/; previous-version-identities.json proves byte identity. Archived relative paths retain their original bundle-root convention.

| Artifact | Previous SHA-256 | Revised SHA-256 |
| --- | --- | --- |
| operation-a-contract-delta.md | 30808ae27e9d130664049503b93cd2d445650db6346690602ef965d8ac9bc302 | 6b1e525f9071ca7f6117386da0ac8a0bb8f30ee050f7a75963e254e22d08c822 |
| evidence/clause-index.json | 41f74a84a1090150f80ed60841ad0d00a03304fcb780ab8ac67320d9fb25b891 | 62c76ec6d8609f4120d192901a8cf83a0ce3e1c71e6a32101bbb34d0fc1428ab |
| evidence/README.md | 139e45011b6ac46777499e849af8c05d2e2df5605687c6947d9705f1de6229dd | 06baf61d0abc6b0477ece6770551bd7a9a81ae04c97f779c1912dc3b17d9216b |

Previous evidence/SHA256SUMS SHA-256: c9cfd09080768938337c26a305531865173e40c4934746fe3227acaca3c0de0f. Current manifest includes this note and the preserved prior manifest; its own digest is returned separately to avoid self-reference.
