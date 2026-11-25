# D-5 reconciliation — WebSocket token issuance and `svc-console-ws` disposition

Status: **PROPOSAL addendum**. Documentary only. It answers the two questions the contract-delta bundle left to DevOps; it does **not** close D-5, does not amend any contract, and does not authorize deployment, credentials or a token exchange. D-5 remains an OPEN CONFLICT until independent domain review and Security confirmation of this configuration against a deployed issuer.

Author: ai-sdlc-devops. Date: 2026-11-22 (America/Bogota). Runtime observed: native OpenClaw subagent (`ai-sdlc-devops`); session model metadata `nan/deepseek-v4-flash`; configured default; **no model override**; no delegation.

## 0. Inputs and integrity

| Input | Expected | Observed | Result |
| --- | --- | --- | --- |
| `design/contract-deltas/operation-a-contract-delta.md` | `6b1e525f9071ca7f6117386da0ac8a0bb8f30ee050f7a75963e254e22d08c822` | same | **MATCH** |
| `design/contract-deltas/evidence/clause-index.json` | `62c76ec6d8609f4120d192901a8cf83a0ce3e1c71e6a32101bbb34d0fc1428ab` | same | **MATCH** |
| `design/access-realization/design-rev4.md` | `3926423e9f42f6949647e5904271b6f6818b977301b66e81ffcf334f10c5dc56` | same | **MATCH**, untouched |
| `owner-decisions.md` | — | `c73ed0aad9dd6ba563755e3b4242f2b25563381fd3a67260608cb8d767429bd6` | read (K-1, K-2, K-3, P-1, atomic-wiring section); not edited |

Branch HEAD is reported by the commission as `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` (owner commit). This role runs no Git command; the value is recorded as supplied, not verified here.

Contract-delta D-5 already fixes the target shape: one public client (`console-demo`), a demo-subject token at the WS audience with `ws.observe`, retirement of `svc-console-ws`, rejection of every service-principal token at `$connect`, and no new token-exchange or broker endpoint. What it leaves open is **whether and how the pinned Keycloak public-client flow can issue that distinct token safely**. That is what follows.

## 1. Answers

**Q1 — Issuance.** The `console-demo` public client (authorization code + PKCE, no secret) obtains a **second, distinct access token** for the socket by a `prompt=none` authorize request carrying only `scope=ws.observe`; a scope-gated Audience mapper gives it `aud` = WS API and nothing else, lifetime ≤900 s, `azp=console-demo`, `sub` = the single demo subject. The `$connect` authorizer accepts only that class: issuer/signature/expiry, WS audience, `ws.observe`, `azp` on an allowlist of exactly `console-demo`, `sub` equal to the configured demo subject, and exact `Origin`.

**Q2 — `svc-console-ws`.** **Retired.** It has no remaining consumer; the browser obtains its own token, and callbacks use IAM `execute-api:ManageConnections`, which is a different credential system. Even if it were recreated and given the WS audience and `ws.observe`, the authorizer's **`azp` + `sub` allowlist** rejects it — the subject class, not the audience, is what prevents the bypass.

## 2. Q1 — issuance in detail

### 2.1 Client and flow

| Element | Proposal |
| --- | --- |
| Client | `console-demo` — **public**, authorization code + **PKCE (S256)**, no client secret, exact HTTPS redirect `https://console.<domain>/auth/callback` |
| HTTP token | authorize with the `console-api` optional scope → `aud` = console API, `scope` = `console.demo`, `azp` = `console-demo`, `sub` = demo subject |
| WS token | second authorize, same client, **`prompt=none`**, `scope=ws.observe` only → `aud` = WS API, `scope` = `ws.observe`, `azp` = `console-demo`, `sub` = demo subject |
| Second-flow trigger | when the user opens the live view; a top-level redirect to the issuer and back, no iframe (third-party-cookie behaviour is not relied on) |
| Silent path | Keycloak SSO session at `auth.<domain>` satisfies `prompt=none` without UI; if it returns `login_required`, the SPA falls back to an interactive authorize with the same scope |
| Lifetime | ≤900 s for both; **no refresh token for the WS token** (re-authorize instead), so the header-borne credential never renews itself |
| Renewal | re-run `prompt=none` before expiry, open a new connection and epoch; never extend the old socket |

### 2.2 The configuration that makes it real (not cosmetic)

Two **optional** client scopes on `console-demo`:

| Client scope | Contains | Effect |
| --- | --- | --- |
| `console-api` | Audience mapper → console API resource; scope value `console.demo` | only present when the HTTP authorize requests it |
| `ws-observe` | Audience mapper → WS API resource; scope value `ws.observe` | only present when the WS authorize requests it |

The critical detail: **audience must come from a scope-gated mapper, not from a client default**. If the console audience were a default client scope, every token would carry it and the split would be cosmetic. With both audiences on optional scopes, the two tokens have **disjoint** audiences and scopes, and neither can be used on the other surface.

### 2.3 What `$connect` validates

1. `Sec-WebSocket-Protocol` contains exactly two entries: the fixed protocol identifier and the token; the token parses as a JWT with `kid` present.
2. Signature verified against the issuer JWKS; `alg` in the allowlist (RSA family); unknown `kid` rejected.
3. `iss` equals the deployed realm issuer; `aud` contains the WS API resource; `scope` contains `ws.observe`.
4. `azp` equals `console-demo` (allowlist of exactly one); `sub` equals the configured single demo subject; a `client_credentials` token has a service-account `sub` and a `svc-*` `azp`, so it fails twice.
5. `exp`/`nbf`/`iat` within skew; total lifetime ≤900 s.
6. `Origin` header equals `https://console.<domain>` exactly; missing or different → 401.
7. Connection record created with `{connectionId, epoch, sub, azp, tokenClass=demo-ws, environment, registryVersion, tokenExpiry, leaseExpiry=jwtExp, createdAt}`; no service binding field is written.
8. Authorizer result caching is **0** — no allow may be reused across connections, tokens or expiry.
9. On success, echo only the **fixed protocol identifier**; never echo the token.

### 2.4 Why this keeps the single-credential property

- One subject, one login, one identity: the demo user. The second token is a **derived, narrower credential for the same subject**, not a second identity, not a service account, not a secret.
- No relay, no server-side session, no browser-held service key, no token exchange, no broker, no new endpoint.
- The split exists so that the credential that travels in a header (`Sec-WebSocket-Protocol`) is the **weaker** one: it cannot initiate a payment (no console audience, no `console.demo` scope), cannot read history, and cannot submit evidence. Conversely the console token cannot open a socket (no WS audience, no `ws.observe`).
- If a single token carrying both scopes is ever preferred, it is strictly worse: the loggable header token would then also authorize payment initiation. That is the fallback of last resort, not the recommendation.

### 2.5 Alternatives considered

| Alternative | Assessment |
| --- | --- |
| One token with both audiences/scopes | Simplest, but it makes the header-borne token payment-capable; rejected as the default |
| Refresh-token exchange with narrowed `scope` | Avoids the second redirect, but Keycloak's acceptance of scope narrowing on refresh is **unverified**; viable only after verification |
| Token exchange / broker endpoint | Explicitly excluded by D-5 and by the owner's "no relay"; also creates a minting authority |
| Cookie-based session | Needs a server-side session component — a relay by another name |

## 3. Q2 — `svc-console-ws` disposition

**Decision: retire the client.** Delete it from the realm, delete its registered public key and any secret, remove its registry record and remove its configuration variables. It is not renamed and not re-scoped, because there is no caller left: the browser obtains the WS token from the issuer directly, and the notifier does not use OAuth at all.

**Why it cannot bypass the demo-subject requirement** — the layered reason, because "we deleted it" is not a control:

| Layer | Effect |
| --- | --- |
| Client deleted | No assertion key exists, so no token with `azp=svc-console-ws` can be minted |
| `azp` allowlist = {`console-demo`} | Even a re-created or renamed confidential client is refused, regardless of its audience or scope |
| `sub` = configured demo subject | A `client_credentials` token carries the service-account subject, never the demo subject; the check fails even if `azp` were somehow allowed |
| Audience/scope checks | Necessary but **not sufficient** on their own: a re-scoped service client could legitimately hold WS audience and `ws.observe`. This is exactly why the subject/client allowlist is the primary control and why an audience-only design would have been a bypass |
| No `X-Demo-JWT`, no `Authorization`, no URL token | There is no second channel by which a service token could present itself as a demo credential |

**Separating callback capability from client-facing issuance.** The notifier's `@connections` calls are an AWS API call authenticated with **IAM SigV4** by the notifier's execution role, authorized by a resource-level permission scoped to this WebSocket API and stage. That capability:

- has no Keycloak client, no OAuth token and no browser involvement;
- cannot be exercised by any browser token, because a bearer JWT is not an AWS principal;
- is granted to exactly one role, asserted in CI (no other principal may hold `execute-api:ManageConnections` on this API).

So the two capabilities are separated by **credential system**, not by convention: client-facing issuance is OAuth subject-bound; server-side delivery is IAM role-bound. Neither can substitute for the other.

## 4. Tables and configuration entries that change

| Item | Change |
| --- | --- |
| Keycloak client `svc-console-ws` | **delete** (client, credential, registry row) |
| Keycloak client scopes | **add** `console-api` (optional; console audience + `console.demo`) and `ws-observe` (optional; WS audience + `ws.observe`); assign both as optional to `console-demo` |
| Keycloak client `console-demo` | add the two optional scopes; keep PKCE, exact redirect, no secret; WS tokens issued via `prompt=none` |
| Connection table | replace `serviceBinding` with `azp` + `tokenClass` (`demo-ws`); keep `epoch`, `sub`, `environment`, `registryVersion`, `tokenExpiry`, `leaseExpiry` |
| Authorizer configuration | `WS_ALLOWED_AZP=console-demo`, `WS_REQUIRED_SUBJECT=<demo subject>`, `WS_REQUIRED_SCOPE=ws.observe`, `WS_AUDIENCE=<ws resource>`, `WS_ALLOWED_ORIGIN=https://console.<domain>`, `WS_AUTHORIZER_CACHE_TTL=0` |
| Variables removed | `OAUTH_CLIENT_ID_CONSOLE_WS`, `OAUTH_SCOPES_CONSOLE_WS`, `OAUTH_KEY_CONSOLE_WS` |
| Variables added | `WS_SCOPE`, `WS_AUDIENCE`, `WS_ALLOWED_AZP`, `WS_ALLOWED_SUBJECT`, `WS_PROMPT_MODE=none`, `WS_SUBPROTOCOL_BEARER`, `DEMO_WS_TOKEN_TTL_SECONDS=900` |
| Logging configuration | exclude `Sec-WebSocket-Protocol` (and `Authorization`) from every access/execution/WAF log format; CI asserts the field list |
| IAM | confirm only the notifier role holds `execute-api:ManageConnections` on this API/stage; CI assertion |
| Registry | demo subject mandate unchanged; revocation status now also terminates sockets (§5) |

## 5. Which rev4 Security conditions this satisfies

| Rev4 condition | How this addendum satisfies it | Residual |
| --- | --- | --- |
| Registry revocation closes an existing socket | Connection record carries `registryVersion`; every frame and the idle scheduler re-read current status; `REVOKED` closes ≤1 heartbeat; failed read fails closed. The WS token cannot outlive 900 s and cannot refresh | The ≤30 s bound is a design target, not a measurement |
| Subprotocol log hygiene, or a ticket fallback | Token is the only sensitive value in the handshake; every log format excludes `Sec-WebSocket-Protocol`; CI asserts it; if a stack cannot prove exclusion, the defined fallback is a 30 s single-use ticket minted by the console API — an authenticated endpoint, not a relay | No stack inspected; exclusion unproven |
| Exact `Origin` | Authorizer equality against `https://console.<domain>`; missing/mismatch → 401 | `Origin` is set by browsers and can be forged by non-browser clients; it is context protection, not authentication |
| Rate limits | Connect attempts ≤6/min per subject after identity, plus pre-auth source-IP and concurrency limits; token endpoint rate-limited at WAF; `prompt=none` re-auth hits the same WAF rule | No measurement under cohort expiry |
| Single credential / no service key in browser | One subject, two derived tokens with disjoint capabilities; no secret, no broker, no exchange | Two tokens exist; the claim is one *identity*, stated explicitly |

## 6. Unverified — must be proven on the pinned release before this is called reconciled

1. That the pinned Keycloak release applies an optional client scope's Audience mapper only when requested, producing **disjoint** `aud` per token.
2. That `prompt=none` with PKCE returns a code silently while the SSO session is live, and returns `login_required` cleanly when it is not.
3. That Keycloak emits `azp=console-demo` and the demo `sub` on a token obtained through that second authorize.
4. That API Gateway's WebSocket `$connect` authorizer receives `Sec-WebSocket-Protocol` intact for the selected API type.
5. That the JWT fits the subprotocol token grammar and any length limit on the deployed stack.
6. That CloudFront/API Gateway/WAF logging can be configured to exclude the subprotocol and `Authorization` fields as claimed.
7. That the refresh-with-narrowed-scope alternative behaves as expected (only relevant if the silent redirect proves unusable).
8. Browser behaviour when the SSO session expires mid-demonstration (redirect, state preservation, no token loss beyond re-login).

## 7. What this does not claim

D-5 is **not closed**: this is a proposed configuration plus the checks needed to prove it. No realm, client scope, mapper or authorizer was created or inspected; no token was minted; no socket opened. Independent acquiring-domain review of the delta bundle and Security confirmation of the issuer/client/authorizer allowlist remain required. No deployment, credential, login or cloud call is authorized here.
