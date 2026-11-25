# Operation A — access realization, revision 4 (relay removed, public console, disposable Keycloak)

Status: **PROPOSAL, revision 4 — architecture delta**. Documentary only. Not approved architecture, not IaC, not deployment, not a Security verdict, not acceptance. Closes no finding: SR2-01/SR2-02 remain OPEN (their rev3 answers are carried), SR2-03/SR2-04 remain ADVISORY/OPEN, NV-01..NV-04 remain PARTIAL.

Author: ai-sdlc-devops. Date: 2026-11-22 (America/Bogota). Runtime observed: native OpenClaw subagent (`ai-sdlc-devops`); session model metadata `nan/deepseek-v4-flash`; configured default; **no model override**; provider-qualified identifier not exposed and not invented. No delegation.

Earlier artifacts are untouched: `design.md`, `configuration-schema.md`, `design-rev2.md`, `design-rev3.md`, `evidence-rev2/`, `evidence-rev3/`. This revision adds `design-rev4.md` and `evidence-rev4/` only.

## 0. Inputs, integrity and the delta this pass realises

| Input | Expected | Observed | Result |
| --- | --- | --- | --- |
| `design/access-realization/design-rev3.md` | `cdd65729ec04708e9bd96626d29980d616fa32261620825987c9eb2cc7d63d2a` | same | **MATCH**, untouched |
| `design/access-realization/evidence-rev3/correction-map.json` | `66a3757ffd616314fa3e46e0dbd784538941495a572fe854e56f67050d62cb99` | same | **MATCH** |
| `reviews/access-design-rev2-20261122/security-review.md` | `b74e5a686525a1c2ceff6713e23e938f4e54f0538a9587240ce8307d1dc38120` | same | **MATCH** |
| `owner-decisions.md` (newest sections: K-1, K-2/K-3, P-1) | — | `9bf166feed3eed4c0b4d6d4e441ae1ef44d3826a8bdab0cad65277bcd8127cdb` | read; supersedes the presenter assumptions |

The owner record now states, in that order: one Keycloak task ("solo una ya que es un simulador"), no maintenance commitment and disposable infrastructure ("no vamos a entrar en eso del mantenimiento … infraestructura efimera … en algun momento sera con una nueva version de keycloak"), the relay removed entirely ("no necesitamos ningun relay"), and a publicly reachable console with the single demo login as the only gate plus attempt rate limiting ("lo primero").

### 0.1 What changed, and why

| # | Change | Before (rev3) | Now (rev4) | Why |
| --- | --- | --- | --- | --- |
| R-1 | Relay removed | Browser → loopback relay → console API; relay held demo + service tokens, client certificate and assertion key | **Browser → public console API** carrying only the demo OAuth token; no local component, no service secret in the browser | owner: "no necesitamos ningun relay"; presentation is in the cloud |
| R-2 | Service identity relocation | mTLS + OAuth on browser-adjacent hop (relay→console) | mTLS + OAuth only on **console→ACL** and **ACL→processor**; evidence admission stays internal invoke; the browser hop carries no service identity | with no relay there is no client holding a certificate next to the user; service identity belongs between services |
| R-3 | Public console | loopback only, screen-share | **publicly reachable** CloudFront + S3 SPA + HTTP API, demo login as the only gate, edge rate limiting | owner P-1 "lo primero" |
| R-4 | Keycloak operations | backups, restore rehearsal, monthly patch, runbook accountability | **rebuild-not-repair**: configuration is the source of truth; recreate rather than restore; no on-call, SLA, HA or backup recovery promise | owner K-2; K-1 single task |
| R-5 | Service clients | `svc-console-relay`, `svc-console-ws`, `svc-acl` | `svc-console`, `svc-console-ws`, `svc-acl` (rename + re-scope; the relay principal loses its consumer) | owner: the relay principal "must be removed or re-scoped" |
| R-6 | WebSocket client | relay held the socket and set `Authorization` + `X-Demo-JWT` headers | **browser connects directly**; token carried in `Sec-WebSocket-Protocol`; exact `Origin` enforced; one identity (demo) instead of two | browsers cannot set arbitrary headers on `WebSocket`; the relay that could is gone |
| R-7 | Demo session | relay session cookie; token in relay memory | token in browser memory only; refresh token in memory with rotation, or re-login; no cookie at the API | no relay to keep server-side state |
| R-8 | Migration path | staged patches | version move = recreate with a new image digest and a fresh database, then import the realm | disposable model |

Consequence for the carried findings: **none of SR2-01..SR2-04 is invalidated** — they live on the evidence-admission and policy layers, which the relay never touched. Two incidental simplifications follow: the presenter-key residual (K-3) disappears with the relay, and `svc-console-relay` ceases to exist. Both are stated in §5.

## 1. Topology after the delta

```text
                         ┌───────────────────────── cloud ─────────────────────────┐
 browser (public)        │                                                          │
   │  HTTPS, demo token  │  CloudFront  console.<domain>                            │
   ├────────────────────▶│   ├── /        → S3 (private, OAC) static SPA            │
   │                     │   └── /api/*   → HTTP API (JWT authorizer)               │
   │                     │                     │                                    │
   │                     │                     ▼                                    │
   │                     │            console API (Lambda)                          │
   │                     │                     │  mTLS + OAuth (svc-console)         │
   │                     │                     ▼                                    │
   │                     │            ACL service (REST + mTLS custom domain)       │
   │                     │                     │  mTLS + OAuth (svc-acl)             │
   │                     │                     ▼                                    │
   │                     │            processor (ISO C2 boundary)                   │
   │                     │                     │  IAM only (D-G)                    │
   │                     │                     ▼                                    │
   │                     │            network-sim → issuer-sim                     │
   │                     │                                                          │
   │  WSS, demo token    │  WebSocket API  ws.<domain>   ◀── notifier (@connections)  │
   └────────────────────▶│                                                          │
                         │  Keycloak auth.<domain> (single task, disposable)         │
                         │  evidence admission: internal invoke only (S5i)           │
                         └──────────────────────────────────────────────────────────┘
```

### 1.1 Which hop carries what

| Hop | Transport identity | Application identity | Notes |
| --- | --- | --- | --- |
| Browser → CloudFront/console API | TLS only (no client certificate) | **demo OAuth token only** (`aud` = console API, scope `console.demo`) | no service secret, no client certificate, no relay |
| Browser → WebSocket | TLS only | **demo OAuth token only**, carried in `Sec-WebSocket-Protocol` | exact `Origin` checked, no IAM signing, no second identity |
| Console API → ACL | **mTLS** (client certificate, Gateway truststore) | **OAuth access token** for `svc-console` (assertion via `private_key_jwt`) | this is where the relay's service role moved |
| ACL → processor | **mTLS** | **OAuth access token** for `svc-acl` | C2 byte contract unchanged |
| Processor → network → issuer | none (no network hop) | **IAM only** (D-G), no token on the send path | unchanged |
| Evidence admission | internal invoke | **function identity + registry** (SR2-01), no OAuth | unchanged |
| Notifier → `@connections` | AWS TLS + IAM SigV4 | none | unchanged |

The honest summary: **the browser path carries a user token and nothing else**, and every service-to-service hop that has a network boundary carries mTLS plus an OAuth service identity. The one remaining single-factor surface is the browser path itself, which is exactly what P-1 accepted.

### 1.2 What disappeared with the relay

- No `127.0.0.1` listener, no loopback `Host`/`Origin` allowlist, no CSRF token for a local session, no opaque relay cookie, no in-process token vault.
- No `svc-console-relay` client, no relay certificate, no relay assertion key on the presenter machine — therefore no presenter-key residual and no "rotate after each demo" obligation.
- No local component can be trusted to hold a service secret, so none exists. That removes a class of failure (a stolen laptop key) and adds one (a stolen browser token).

## 2. Public console surface

### 2.1 Hosting

| Element | Proposal |
| --- | --- |
| Static application | S3 bucket, private, Block Public Access, served only through **CloudFront with Origin Access Control**; no website endpoint |
| Edge | CloudFront distribution on `console.<domain>` with ACM certificate (TLS 1.2+), security headers policy (HSTS, `X-Content-Type-Options`, `Referrer-Policy`, `frame-ancestors 'none'`), and a WAF web ACL |
| API | HTTP API with a JWT authorizer (demo token `aud`, scope `console.demo`), Lambda integration; `/api/*` routed through the same CloudFront distribution so the browser sees **one origin** |
| Same-origin consequence | With SPA and API on one hostname, CORS is not needed at all; the API's CORS configuration is empty rather than wildcard. No `Access-Control-Allow-Credentials` anywhere |
| WebSocket | Separate `ws.<domain>` custom domain; WebSocket is not subject to CORS, so a separate origin is acceptable — but the handshake `Origin` is still checked (§3) |
| Issuer | `auth.<domain>` (Keycloak), public, path-allowlisted as in rev2 §2.4 |

### 2.2 Demo login and token lifecycle

1. The SPA is a **public OIDC client** (`console-demo`) with authorization code + PKCE; no client secret is issued to it and none can be.
2. Redirect URI is the exact `https://console.<domain>/auth/callback`; no wildcard, no loopback URI any more.
3. The access token is held **in memory only** — no `localStorage`, no `sessionStorage`, no cookie, no URL fragment after the code exchange (the code is removed from the address bar immediately).
4. Renewal options, with a recommendation: (a) **refresh token in memory with rotation**, absolute session cap (proposal 8 h), revoked on logout; or (b) no refresh token, re-login every ≤900 s. Recommended: (a), because a live demonstration that redirects every 15 minutes is worse operationally, and the mitigations (CSP, memory-only storage, short absolute session, issuer revocation, rate limits) are the same ones that protect the access token. This is an implementation choice, not an owner decision.
5. On expiry or failed refresh: the SPA clears state, shows session-expired, and stops issuing API calls. It never retries a payment.
6. Keycloak's SSO session cookie at `auth.<domain>` may allow a silent re-authentication on page reload; that is a convenience, not a credential the console reads.

### 2.3 Edge abuse controls

| Layer | Control (proposal) |
| --- | --- |
| WAF managed rules | AWS managed rule groups (baseline + known bad inputs); bot control only if its cost is accepted separately |
| Rate — login and token | Rate-based rule on `/realms/<realm>/protocol/openid-connect/*` (proposal: 20 requests / 5 min per IP) plus Keycloak brute-force detection with a failure factor and temporary lockout |
| Rate — API | WAF rate rule on `/api/*` (proposal: 100 requests / 5 min per IP) and API Gateway route throttling (proposal: burst 10, rate 5 rps) as a second, independent bound |
| Rate — WebSocket | Connection attempts ≤6/min per client (§3.4) and reserved concurrency on the authorizer |
| Body and header limits | API Gateway and Lambda limits as configured; the channel contract's 4096-byte body bound is enforced in the handler, not only at the edge |
| Cost/abuse | CloudFront + WAF absorb volumetric traffic up to their capacity; Shield Standard applies; no claim of DDoS immunity |
| Detection | 4xx/5xx and 429 rates per route, login failures per IP and per subject, token endpoint latency; alarms, not automatic blocks beyond the rules above |

These are **technical abuse bounds**, not business quotas: none of them gates a legitimate authorized payment beyond its own rate, and none creates an owner-approval requirement.

### 2.4 Origin, CORS and CSP rules

- **Same-origin design**: the browser talks only to `https://console.<domain>`. The API has no cross-origin consumer, so `Access-Control-Allow-Origin` is not emitted at all. If a future need appears, the allowlist is exactly `https://console.<domain>` — never `*`, never `null`.
- **CSP** (delivered by CloudFront response headers): `default-src 'self'`; `connect-src 'self' https://auth.<domain> https://ws.<domain>`; `img-src 'self' data:`; `script-src 'self'` with no `unsafe-inline`; `frame-ancestors 'none'`; `form-action 'self' https://auth.<domain>`; `base-uri 'none'`.
- **No third-party script** is loaded, so a compromised CDN cannot read the token. If a library is added, it is bundled and served from the same origin.
- **Referrer policy** `no-referrer` so the code/token in a URL can never leak through `Referer`.

### 2.5 Enforcing "no service secret in the browser" rather than assuming it

| Control | What it enforces |
| --- | --- |
| Public client by construction | Keycloak issues no secret for `console-demo`; there is nothing to embed |
| Audience separation | The console route requires `aud` = console API and scope `console.demo`; a service token for ACL or WS has a different audience and is rejected — so even a leaked service token would not work here |
| Build-time scan | CI fails if the built bundle contains `client_secret`, `PRIVATE KEY`, an AWS access key pattern, a bearer token literal, or a service audience |
| CSP `connect-src` | Limits where an injected script could send anything it managed to read |
| No server-side templating of secrets | The SPA is static; no secret is ever rendered into HTML |
| No cookies at the API | Nothing is ambient; every call carries an explicit `Authorization` header, so a cross-site request cannot ride a session |

### 2.6 Residual exposure, named

- **The demo credential is the only gate.** Anyone who obtains it can read the synthetic history of that demo context and **initiate synthetic payments** through the console, within rate limits and the mandate. That is the accepted consequence of P-1, not a defect to be argued away.
- **Public issuer surface**: the login page and token endpoint are internet-reachable and are now the primary credential-stuffing target; brute-force detection and rate limits reduce, not eliminate, that.
- **Token theft via XSS** in the SPA is the highest-impact browser risk; CSP, memory-only storage, short absolute session and revocation bound it.
- **No client certificate on the browser path**: transport assurance for the console is TLS only, and the API trusts a bearer token.
- **Single demo subject**: no per-person privacy; every viewer shares one identity and one context.
- **Availability**: one Keycloak task means new logins and new token issuance fail during a restart, and the public console has no failover.

## 3. WebSocket from the browser (new, because the relay is gone)

### 3.1 The constraint

A browser `WebSocket` cannot set `Authorization` or any custom header. The relay used to set both `Authorization` and `X-Demo-JWT`; with the relay removed that mechanism no longer exists, and the approved contract text that describes it (C6: relay IAM-signs `$connect`, `X-Demo-JWT` header) is no longer implementable as written.

### 3.2 Chosen transport and alternatives

| Option | Mechanism | Assessment |
| --- | --- | --- |
| **A. Token in `Sec-WebSocket-Protocol` (chosen)** | `new WebSocket("wss://ws.<domain>", ["bearer", "<demo-jwt>"])`; the `$connect` authorizer reads the header and answers with the accepted subprotocol | Browser-native, no new endpoint, no cookie, no URL parameter. The token is visible in handshake headers and must **never** be logged |
| B. One-time ticket in the query string | Console API mints a 30 s single-use ticket bound to subject/context; browser connects with `?ticket=` | Requires a new authenticated endpoint and a state store; the ticket still lands in access logs. Viable fallback if subprotocol logging cannot be suppressed |
| C. Cookie-based session | HttpOnly cookie at the console origin, WS same-origin | Needs a server-side session component to set the cookie — a relay under another name — and would reintroduce exactly the component the owner removed |
| D. Keep a cloud BFF holding the token | Cloud component proxies the socket | Same objection as C: a relay, relocated |

**Recommendation: A**, with (i) `Sec-WebSocket-Protocol` excluded from every access-log configuration (CloudFront standard logs and API Gateway access logs), (ii) the token short-lived (≤900 s) and scoped `ws.observe`, (iii) the authorizer validating `iss`, `aud`, `exp`, `sub` = the single demo subject, and `Origin` = `https://console.<domain>`. If log hygiene cannot be proven on the chosen stack, fall back to B and accept the new endpoint.

### 3.3 Identity count changed — stated, not hidden

Previously the socket carried **two** identities (service token + demo token). Now it carries **one** (demo). Transport assurance is unchanged (still no mTLS, accepted by D-D), but the identity count drops, so the compensating controls carry more weight: lease ≤900 s, per-frame revalidation, registry revocation, server-side close, `ws.observe` scope only, exact `Origin`, and rate limits. This is a **material delta to the D-D realization** that the next Security review must judge; it is not covered by D-D's text, which named controls but not the number of credentials.

### 3.4 Lease, frames and rate limits

- Effective lease = `jwtExp` (service-token reasoning is unchanged but now applies to the demo token): `min(jwtExp, connectTime + 900 s)`.
- `$connect` only; every frame revalidated against a server-side connection record (epoch, subject, expiry, registry version).
- Cursor and mid-pass rules carry from rev3 §5 unchanged (stop at page boundary, new snapshot by default; same-snapshot continuation only if Requirements authors it).
- Limits: reconnect jitter 1/2/5/10/30 s; connection attempts ≤6/min per subject; subscribe/unsubscribe ≤10/min per connection; heartbeat ≤1 per 30 s; bounded buffers; reserved concurrency; cohort-close jitter.
- A refused or closed socket never changes a payment outcome and never triggers a retry.

## 4. Disposable Keycloak — rebuild, do not repair

### 4.1 Operating model

| Aspect | Model |
| --- | --- |
| Source of truth | **Configuration**: image digest, environment variables, realm import artifact, client/key registrations. The running task and its database are not authoritative for anything |
| Recovery | **Recreate**: destroy the service and database, apply configuration, import the realm, register public keys, verify that each client can obtain a token. No restore-from-backup step |
| Change / upgrade | Change the pinned image digest, recreate with a **fresh database**, import the realm, re-verify. A release that would require an in-place schema migration is handled the same way — recreate rather than migrate in place |
| Data loss accepted | Keycloak sessions, offline tokens and realm-local state. Never accepted: loss of the simulator's journal, evidence or event store — those live in their own tables and are not part of the disposable unit |
| Blast radius | During a rebuild, new logins and new token issuance fail; existing access tokens remain valid to their 900 s expiry; the console shows issuer-unavailable; in-flight payments are unaffected |

### 4.2 Explicitly not promised

- No on-call, no SLA, no availability target, no HA pair, no multi-AZ failover.
- No backup-based recovery commitment and no restore rehearsal. If the platform takes automated database backups by default, they are **not** the recovery path and must not be described as one.
- No patching SLA. There is no "we will patch within N days": there is "we rebuild with a newer release when we choose to".
- No durability promise for realm sessions or offline tokens.

This replaces rev2 §2.8's backup/PITR and restore-rehearsal language, which K-2 superseded. It does **not** relax anything about secret handling, revocation or the evidence plane.

### 4.3 What still must never leak

Client assertion private keys; the database password; bootstrap admin credentials (used once, then discarded with the container generation); the demo user's password beyond the provisioned secret; any access, refresh or ID token; any non-public registry value. Concretely: no secret in Terraform state, plan output, CI artifacts, container images, plaintext task definitions, application logs, access logs (including `Sec-WebSocket-Protocol` and `Authorization`), or the static bundle. A rebuild is driven from configuration, so a secret that leaked into configuration would be re-created forever.

## 5. Carried forward from rev3, with an invalidation check

| Rev3 answer | Carried? | What the relay removal changed |
| --- | --- | --- |
| **SR2-01** one admission function per delivering principal; function identity → server-side short sender id (`SND-PROC-01`, `SND-NET-01`, `SND-DIAG-01`); no payload identity | **Yes, intact** (rev3 §1) | Nothing: the relay never participated in evidence admission. The diagnostic forwarder remains the only forwarding path |
| **SR2-02** explicit `Deny` with `ArnNotEquals aws:PrincipalArn` / `StringNotEquals aws:PrincipalAccount`; `lambda:SourceFunctionArn` condition on the identity grant; no-alias/qualified-ARN rules; boundaries; inventory; "no public HTTP route" ≠ "no externally possible SDK invoke" | **Yes, intact** (rev3 §2) | Nothing on the evidence path. The same deny discipline now also applies to the console API's downstream invoke permissions |
| **SR2-03** rotation minima: assertion ≥24 h (two-client replacement if dual-key unsupported), issuer signing ≥24 h / 48 h, TLS truststore ≥24 h + 30-day renewal margin, C4 evidence ≥7-day verify window | **Yes, with the client set reduced** from three service clients to `svc-console`, `svc-console-ws`, `svc-acl` | The relay key class disappears (no presenter key). Minima unchanged; the two-client replacement now applies to the three service clients |
| **SR2-04** analytical corrections: `ACL_AUTH` is transport-only; per-request strongly consistent registry read; payload 6 964 guard with achievable 5 684 / 5 940; diagnostic reachable only through `verifyForwarded`; cursor/lease equality withdrawn; O5 lease staleness in the overlay list | **Yes, intact** (rev3 §6) | Nothing; payload and cursor items are unaffected by where the browser sits |
| Rev3 §4 relay capability wording | **Superseded** | The relay no longer exists. The capability moves: the **demo token** can initiate an authorized synthetic payment; the **`svc-console` service identity** authorizes the console→ACL hop; neither signs C4 evidence nor decides at the issuer |
| Rev3 §5 WS obligations (mid-pass, reconnect rates) | **Yes**, restated in §3.4 for a browser client | The obligations now apply to the browser rather than a relay, and the identity count dropped from two to one (§3.3) |
| Rev2 §2.8 Keycloak backup/restore/patching | **Superseded** | Replaced by §4 (rebuild-not-repair) |
| K-3 presenter-local key | **Resolved by removal** | No presenter key, no residual, no per-demo rotation obligation |

Nothing in SR2-01/02/03/04 is silently dropped. The only substantive change is that the WebSocket boundary now has one credential instead of two, which is flagged in §3.3 as a delta for Security rather than folded into the carried text.

## 6. Scenario matrix rev4

| # | Connection | API / mechanism | Exposure | TLS / peer identity | Application identity | Issuer / audience / scope | Mandate | Expected deny outcomes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| S1 | Browser → console (SPA + API) | CloudFront + S3 (OAC) + HTTP API with JWT authorizer | **public internet** | TLS 1.2+ at CloudFront; **no client certificate** | **demo token only** | Keycloak `console-demo` (PKCE); `aud` = console API; scope `console.demo` | single demo subject; synthetic context fixed server-side | no token → 401; expired/wrong audience/wrong subject → 401; service token presented → 403; cross-origin call → no CORS allowance; rate limited → 429 |
| S2 | Console API → ACL | REST API, mTLS custom domain | public authenticated by mTLS | Gateway truststore; leaf fingerprint → `svc-console` | OAuth access token `svc-console` | `aud` = ACL API; scope `console.dispatch` | registry: console role + fixed synthetic contexts | missing/untrusted cert → 403; token client ≠ cert client → 403; missing scope → 403; demo token replayed here → 403 |
| S3 | ACL → processor (ISO C2) | REST API, `application/octet-stream`, mTLS | public authenticated by mTLS | truststore; fingerprint → `svc-acl` | OAuth access token `svc-acl` | `aud` = simulator API; scope `sim.iso87a.authorize` | ACL role + permitted contexts | wrong media/size → 400/415 with no dispatch; default hostname rejected; token client ≠ cert client → 403 |
| S4 | Processor → network-sim → issuer-sim | Lambda invoke | internal | none | **IAM only** (rev3 §2.2 conditioned grants) | none (D-G) | role per function | invoke outside a function context or from another role → denied once the rev3 controls exist |
| S5i | Producer → evidence admission | private invoke, one function per sender | internal only | none | function identity → registry sender id | no OAuth | role + allowed key ids + mode | rev3 §1.5 cases 1–12 unchanged |
| S6 | Browser → WebSocket | WebSocket API custom domain; token in `Sec-WebSocket-Protocol` | **public internet**; no mTLS (accepted) | TLS at API Gateway; no client certificate | **demo token only** + exact `Origin` | `aud` = WS API; scope `ws.observe`; `sub` = single demo subject | lease = `jwtExp` ≤900 s; per-frame revalidation | missing token → 401; wrong `Origin` → 401; expired → closed; revoked → closed ≤1 heartbeat; foreign context → denied |
| S7 | Notifier → `@connections` / DynamoDB | AWS API | internal | IAM SigV4 | none | n/a | scoped to one API/stage | other stage/API denied; unsanitised payload suppressed |
| S8 | GitHub Actions → AWS / state | OIDC | external CI | AWS STS | OIDC token, exact `sub` | n/a | role per environment | wrong repo/ref/environment/audience → `AccessDenied` |

## 7. Configuration schema delta

Configuration-only. No Terraform or SAM authored here.

### 7.1 Removed

`OAUTH_CLIENT_ID_CONSOLE_RELAY`, `OAUTH_SCOPES_CONSOLE_RELAY`, `OAUTH_KEY_CONSOLE_RELAY`, `TLS_KEY_CONSOLE_RELAY`, `TLS_CERT_CONSOLE_RELAY`, every loopback/relay parameter (bind address, relay port, relay session TTL, relay CSRF secret), and `CUSTOM_DOMAIN_CONSOLE` in its relay sense.

### 7.2 Added — console hosting and edge

| Variable | Value shape | Consumer |
| --- | --- | --- |
| `CONSOLE_DOMAIN` | FQDN for the public console | CloudFront alias, ACM, CSP, `Origin` allowlist |
| `CONSOLE_BUCKET` | S3 bucket name (private, OAC) | static hosting |
| `CLOUDFRONT_DISTRIBUTION_ID` | distribution id | deployment |
| `CONSOLE_CERT_ARN` | ACM ARN (us-east-2 for CloudFront) | CloudFront |
| `WAF_WEB_ACL_ARN` | WAF web ACL ARN | CloudFront / API |
| `WAF_RATE_LOGIN` / `WAF_RATE_API` | requests per 5 min per IP (proposal 20 / 100) | WAF rules |
| `APIGW_THROTTLE_BURST` / `APIGW_THROTTLE_RATE` | 10 / 5 | HTTP API stage |
| `DEMO_CLIENT_ID` | `console-demo` | SPA configuration |
| `DEMO_REDIRECT_URI` | `https://console.<domain>/auth/callback` | Keycloak client |
| `DEMO_ACCESS_TTL_SECONDS` | 900 | Keycloak realm, verifiers |
| `DEMO_REFRESH_ENABLED` / `DEMO_SESSION_MAX_SECONDS` | `true` / 28800 (proposal) | Keycloak client |
| `WS_SUBPROTOCOL_BEARER` | subprotocol name carrying the token | SPA, `$connect` authorizer |
| `WS_ALLOWED_ORIGIN` | `https://console.<domain>` | `$connect` authorizer |
| `CSP_POLICY_VERSION` | identifier of the header set | CloudFront response headers |
| `CONSOLE_CORS_ORIGIN` | empty, or exactly `https://console.<domain>` if ever needed | API CORS |

### 7.3 Service clients after the rename

| Client | Scope | Key location |
| --- | --- | --- |
| `svc-console` | `console.dispatch` (ACL audience) | console API Lambda: assertion key + client certificate from parameter store |
| `svc-console-ws` | `ws.observe` (WS audience) | console backend if it ever mints observation tokens; never the browser |
| `svc-acl` | `sim.iso87a.authorize` (simulator audience) | ACL runtime |
| `console-demo` | `console.demo` (public client, PKCE) | none — no secret exists |

### 7.4 Carried unchanged

Keycloak hosting shape (single task, Aurora Serverless v2, private placement, ALB path allowlist, SSM admin path), realm-per-environment, Signed JWT/RS256 with no client secrets, audience mappers, registry and assertion-replay tables, mTLS truststore and custom domains for S2/S3, C4 key ids, `HISTORY_TTL_DAYS`, and the GitHub OIDC trust plus S3 state backend (`use_lockfile`, versioning, SSE-KMS, Block Public Access, `allowed_account_ids`, explicit bootstrap) exactly as in rev2 §9.5.

## 8. Contradictions this delta creates — deltas Requirements must author

No approved file is edited here. Each row is a statement that now conflicts with a pinned document and must be resolved through the contract-change path with independent review.

| ID | Conflicting text | Nature of the conflict | Required delta |
| --- | --- | --- | --- |
| **D-1** | `observation-contract.md` O4 ("presenter-operated loopback HTML/relay on 127.0.0.1 … no direct public Internet endpoint"), O5 ("Trusted IAM `$connect` context plus VALIDATED X-Demo-JWT"), C6 (relay "IAM-signs `$connect`") | Relay removed; console public; socket browser-direct with no IAM signing and no custom header | Rewrite O4/O5/C6 for browser-direct public access, one credential, `Sec-WebSocket-Protocol` carriage, exact `Origin`, rate limits |
| **D-2** | `channel-contract.md` C1 ("Proposed local relay holds short-lived demo-user JWT"), C6 ("relay memory stores short-lived JWT … opaque HttpOnly SameSite=Strict local session") | No relay, no local session, token in browser memory | Restate C1/C6 around a public SPA and memory-only token; delete the loopback session model |
| **D-3** | Owner record D3 (2026-11-19): "presenter-operated demonstration with screen-sharing … no direct client access or remote-public hosting commissioned" | Superseded by K-3 and P-1 | Coordinator records the supersession; Requirements updates exposure assumptions and acceptance scenarios |
| **D-4** | `requirements.md` R19 (fixed synthetic loopback demo relay), R25 (single demo principal with WS) | R19's premise is gone; R25's WS clause must name the browser-direct transport | Revise R19; amend R25's WS realization language |
| **D-5** | `channel-contract.md` C6 / `observation-contract.md` O5: two identities at `$connect` (service + demo) | Now one (demo only) | Security and Requirements decide whether a single-identity socket is acceptable under D-D; if not, a cloud broker returns and "no relay" must be re-examined |
| **D-6** | `observation-contract.md` O5/O7: "service/environment scope" at connect | The browser has no service identity | Replace with subject + deployment-environment binding |
| **D-7** | No contract defines console rate limiting, CORS/CSP or public-exposure controls | New surface with no approved text | Author edge controls as requirements with measurable limits |
| **D-8** | `channel-contract.md` C1: `console.write` / service identity language for the console path | The console hop now carries only a user token; the service identity moved to S2 | Restate which identity authorizes which hop |
| **D-9** | Rev2 §2.8 / rev3 §3 Keycloak backup, restore and patching obligations | Superseded by rebuild-not-repair | Requirements/DevOps restate operational expectations; no restore claims remain |

`AD-01` (historical contracts must be overlaid) applies to all nine: the pinned documents remain historical evidence and must not be implemented literally.

## 9. Finding → section map

| Item | Section | Status |
| --- | --- | --- |
| SR2-01 sender provenance | carried §5 (rev3 §1 intact) | **OPEN**; unaffected by the delta |
| SR2-02 effective restriction | carried §5 (rev3 §2 intact) | **OPEN**; unaffected |
| SR2-03 rotation minima | carried §5, client set reduced | **OPEN**; minima unchanged |
| SR2-04 analytical corrections | carried §5 (rev3 §6 intact) | **OPEN**; corrections retained |
| Relay capability wording | replaced §1.2, §2.6 | **OPEN**; the relay is gone, capability now sits with the demo token and `svc-console` |
| WebSocket obligations | §3.3–§3.4 | **OPEN**; identity count reduced, flagged as a delta |
| NV-01 topology / alternate ingress | §1, §6, §7 | **PARTIAL**; matrix rebuilt, IaC absent |
| NV-02 issuer / binding | §4, §7.3, carried rev2 §2.5 | **PARTIAL** |
| NV-03 WebSocket | §3 | **PARTIAL**; one credential instead of two |
| NV-04 custody / OIDC / state | §4.3, §7.4 | **PARTIAL**; rebuild model changes custody expectations, not controls |
| NV-05 / AD-01 | outside this commission | unchanged |

No finding is closed; no risk is accepted on the owner's behalf; no gate exception is requested.

## 10. Decisions

**No new decision is requested from Fernando.** K-1 (single task), K-2 (disposable, no maintenance) and K-3 (relay removed) are resolved in the owner record, and P-1 fixes the public exposure. Two items are implementation choices with recommendations, not owner questions: refresh-token-in-memory versus re-login every 15 minutes (§2.2), and `Sec-WebSocket-Protocol` versus a one-time ticket (§3.2).

One item may return to the owner **only if** the next Security review judges the single-credential socket unacceptable (D-5). The alternatives are a cloud broker — which would require revisiting "no relay" — or dropping live observation in favour of HTTP catch-up only. That is a consequence to escalate, not a decision to pre-empt here.

## 11. Boundaries observed in this pass

Read-only inspection of rev3, its correction map, the FAIL review, owner decisions and the contracts named in §8. No Git/provider command, no AWS or Keycloak API call, no probe, no credential flow, no login, no deployment, no pipeline run, no publication, no test execution, no Terraform or SAM authored. No approved file, review, assessment or earlier proposal modified. No onward delegation. Only `design-rev4.md` and `evidence-rev4/` were written.
