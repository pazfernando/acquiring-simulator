# Construction handoff — H1 history and K1 authentication (prepared 2026-11-23)

Coordinator-owned dispatch record. Prepared while the N-02 correction is under independent re-review; Implementation starts only after that review closes the finding.

## Owner authority
- Continuation of 2026-11-23: complete real authentication, server history, independent reviews and deployment.
- Owner decision "N-02 closure (Option B) and normative adoption — 2026-11-23" in `owner-decisions.md`: origin-verification header selected; reviewed contracts adopted normatively.
- Adoption does not close the Security verdict, does not close SR2-01/02, NV-05/AD-01, D-5 issuer evidence or the public-surface evidence list, and does not authorize deployment.

## Normative inputs (do not substitute)
| Input | SHA-256 |
| --- | --- |
| `design/contract-deltas/continuation-rev3-20261123/OUTPUTS.SHA256SUMS` (packet manifest) | `2000bb004e68e6d2974bef3067baeae6774f1922a21dca4ea18fecf6944ab467` |
| `design/contract-deltas/continuation-rev3-dr301-20261123/old-prior-fixtures.json` | `1d3f1406b05d95f87e18f37b33eea3bb855ad5c80488f92891dcc132b5f3d5f5` |
| `design/contract-deltas/n02-origin-verification-20261123/contract-delta.md` | `159599755766d54942c5ae7a95bef35a7d611b7dff4e2a66b9dc83ef9fa0f3ad` |
| Domain review (rev3) | `154b6346544bfb760bb6bf1c5cefd63dcc6f09ab064151a7165972007b67c2e4` |
| Domain re-review (DR3-01) | `df5ad1a5979888387ea4b6b25813ee8cd5c2c9643664d505d29894199b70c5d0` |
| Security review (rev3) | `f5696ed917f009c7569cccbba5f42b431170d15a53ef06bdd38c10e61d53ba50` |
| `owner-decisions.md` at adoption | `d5208f2b6bd28047ce7394656b0d5de2fff717d68b004a6d204feeaa2443338e` |
| `WORK_ITEM.md` at adoption | `4134924093c077d206d0cdd755018cfd0add8767bc0ce84e440dc8ec713d0aff` |

## Slice H1 — read-only history (smallest, start here)
Route `GET /console/v1/transactions/{id}/events` over the existing event store. Requirements from the adopted contracts: strong base HEAD then bounded Query, fixed snapshot H/R/F, opaque cursor bound to current principal, trusted environment, mandate and generation, re-authentication before and after the store read, logical expired ranges and truthful gaps, HTTP-only read with no WebSocket dependency, and no payment capability of any kind. No destructive TTL or compaction, no new projection, no transaction creation, no financial gateway.

Boundary gate per the N-02 delta: application-first check of `X-Origin-Verify`, fail closed on absent, empty, duplicate or mismatched, constant-time, before dispatch, 404/405, body read, credential seam, rate limit and any service call; error `403` with `{"version":1,"error":"EDGE_ORIGIN_NOT_VERIFIED","action":null}`; initialization fails if the expected value is missing.

## Slice K1 — authentication seams
Replace the stub decoder with real JWKS/issuer validation and the adopted claim-class rules; keep the existing authorizer policy core; no combined token, no persisted bearer, no stub fallback, no hidden broker. The auxiliary-window flow (`browser-flow.md`) governs the browser lifecycle and its COOP/opener feasibility gate.

## Gates that remain
- Security re-review of the N-02 delta and infrastructure must close the finding before this handoff is executed.
- SR2-01/02 executed IAM evidence, D-5 issuer evidence on a pinned release, NV-05 retention realization and the public-surface evidence list remain open and are not satisfied by code or local tests.
- Deployment needs the concrete destination inputs listed in `devops-report.md` and passed independent QA/Security.
