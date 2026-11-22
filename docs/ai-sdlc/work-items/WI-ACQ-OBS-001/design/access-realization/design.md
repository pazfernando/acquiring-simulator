# Operation A — access realization proposal (mTLS + OAuth 2.0 private_key_jwt)

Status: **PROPOSAL**. Documentary design only. Not approved architecture, not IaC, not a deployment plan, not a Security verdict, not acceptance. Owner-selected direction (mTLS + OAuth 2.0 `private_key_jwt`, external/internal Gateway per scenario, GitHub OIDC/Variables/Secrets, `us-east-2`, on-demand billing, seven-day retention where applicable, presenter-operated screen-share) is preserved unchanged; nothing here reopens it.

Author: ai-sdlc-devops. Date: 2026-11-20 (America/Bogota). Runtime: native OpenClaw subagent (`ai-sdlc-devops`), observed session model metadata `nan/deepseek-v4-flash`, configured default, no task model override. Provider-qualified model identifier is not exposed by session metadata; none is invented. Session: `agent:ai-sdlc-devops:subagent:2bad2004-fb26-423c-8a6e-9f0e6f67d383`. No delegation.

Scope: bounded documentary design closing Security review findings NV-01..NV-04 for Operation A only. No code, no adapter/YAML/ADR/WORK_ITEM edit, no Git or provider command, no cloud API call, no probe, no credential flow, no deployment, no publication, no deletion. Output confined to `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/access-realization/`.

## 1. Inputs, binding and what this document is not

| Input | Identity used |
| --- | --- |
| Independent review `reviews/access-design-20261120/security-review.md` | SHA-256 `0d71ea007e56b12d9873df14126c22640c3abc68052b42db1f47b0a9688d218e` (recomputed, matches commission) |
| `adr-operation-a-access-and-environment.md` | SHA-256 `d51bbf650aca74e82351dacf0e45d0f14c992044b9ff246bc7b8b281920b0b39` (recomputed, matches) |
| `owner-decisions.md` | SHA-256 `efef3d2272dd54130b404fae43ec90643bc42c5dd4701cc1b6b4e49d4ea0f18c` (recomputed, matches) |
| `requirements.md` | SHA-256 `49c5d412e3705c320a2f78b190555f49e7e0fa4662cff9329d259f89e2b459df` (recomputed, matches) |
| `channel-contract.md` | SHA-256 `495e1172e76ab9ba81f8a8026e7efec25baa6e4fff0d754c0a4c92bd38ea3bca` (recomputed, matches) |
| `observation-contract.md` | SHA-256 `ca53e384a46d24b3c1119b3e6ba804a9a9d4caac049a6b0c55a40c72842a205a` (recomputed, matches) |
| `domain-review.md` | SHA-256 `73adba20fd70969bf8bb9f23ebde5c5d16aec69fd4694ac32984008b76ee0ca2` (recomputed, matches) |

Repository binding is taken only from Implementation-supplied evidence, not from Git execution by this role:

- `evidence/t1-traceability-20261120/BINDING.txt` SHA-256 `e4951cc2f2df75e61a9b9d1ce7bdb0f717d17faf3746c6fda79f5ed02bdfe8db`: branch `feat/wi-acq-auth-001`, HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`, origin `https://github.com/pazfernando/acquiring-simulator.git`, workdir `/home/pazfernando/projects/acquiring-simulator`, `model_provider=nan`, `model_id=deepseek-v4-flash`, `suite_not_rerun=true`, `utc_start=2026-11-20T23:57:19Z`, `utc_close=2026-11-20T23:59:17Z`.
- `evidence/t1-traceability-20261120/git-status-before.txt` SHA-256 `b83770538498facf44c35d2bbd558ef15d105ef3e497e10ba941dc837b097fd9`: working tree **dirty**, including pre-existing application changes and untracked files. No whole-tree freeze is claimed; this design reads documents only and does not assess application diffs, the concurrent timeout-default fix, traceability output or Coordinator plan/status files.

Not asserted here: repository visibility, remote reachability, branch protection, provider plan/quota/pricing, issuer tenancy, certificate issuance, DNS, private connectivity, runtime secret delivery, or any deployed behaviour. `NO AWS RESOURCES PROVISIONED / NOT BUILT / NOT DEPLOYED`.

Historical contracts remain overlaid by the ADR, not rewritten: C2's IAM/SigV4 service-ingress recommendation, C6/O4's IAM-signed `$connect`, and O8's numeric business limits are superseded for application ingress and business gating; protocol bounds, atomicity, idempotency, no-resend and AWS service quotas are retained.

## 2. Five identity planes that must not be conflated

The single most likely construction error is treating one credential as several. The proposal keeps five planes explicitly separate, and every hop below names which planes it uses.

| Plane | What it proves | Credential | Used by | Never used for |
| --- | --- | --- | --- | --- |
| **P-DEMO** demo principal | one pre-provisioned demo subject is signed in | OIDC access token (`aud` = console API, scope `console.demo`) from issuer's interactive sign-in | relay → console HTTP/WS only | service-to-service calls; console business mandate; C4 evidence |
| **P-SVC** service client | which registered OAuth client is calling, at the token endpoint | client assertion JWT signed with the client's private key (`private_key_jwt`), exchanged for a short-lived access token | all service-to-service application hops | browser; AWS API calls; C4 evidence |
| **P-TLS** transport peer | which X.509 client certificate terminated the handshake | mTLS client certificate + private key, issued by a chosen CA | hops where API Gateway supports inbound mTLS | identity on hops without native mTLS; payment mandate |
| **P-AWS** AWS workload | which AWS principal acts on AWS resources | GitHub OIDC → IAM role; Lambda execution roles; resource policies | CI, DynamoDB, `@connections`, invocation | application client authentication |
| **P-EVID** evidence signer | the processor-known attestation of a specific instruction/response | Ed25519 key per signing principal | C4 DIRECT/RELAY envelopes and late admission | substituting for TLS/OAuth; admitting a payment result |

The **simulated issuer** (issuer-of-record for the synthetic authorization) is a sixth, payment-domain identity that is deliberately *not* the OAuth issuer. The OAuth issuer only authenticates clients and demo users; it never decides a payment.

Mandate (environment/acquirer/merchant/terminal/route/role) is **not** carried by any single credential. It is resolved from a server-side registry keyed by OAuth `client_id`, and the certificate (when present) must map to that same client. Claims are hints; the registry is authoritative.

## 3. OAuth issuer selection — PROPOSAL with official capability evidence

### 3.1 Capability evidence gathered read-only

| Provider | Documented `private_key_jwt` | Evidence (official) | Notes |
| --- | --- | --- | --- |
| Auth0 | Yes — client assertion with `client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer`, `iss`/`sub`=client_id, `aud`=tenant URL with trailing slash, `exp` max 5 min (recommends ≤1 min), `jti` recommended UUID, one-time use | `https://auth0.com/docs/get-started/authentication-and-authorization-flow/authenticate-with-private-key-jwt` (fetched 2026-11-21T00:04:02Z, 200, not truncated) | Assertion ≤2048 bytes; `alg` must match configured credential |
| Okta | Yes — `token_endpoint_auth_method=private_key_jwt`, RS256/384/512 or ES256/384/512; public key in client JWKSet; if `jti` present the token is single-use; `exp` must be ≤1 h | `https://developer.okta.com/docs/api/openapi/okta-oauth/guides/client-auth` (fetched 2026-11-21T00:04:02Z, 200) | Also documents `self_signed_tls_client_auth` with `cnf.x5t#S256`, flagged as Early Access — not to be relied on |
| Keycloak (self-hosted) | Yes — "Client authentication with Signed JWT" per RFC7523, `client_assertion`, public key/cert or JWKS URL registered | `https://www.keycloak.org/securing-apps/authz-client` (fetched 2026-11-21T00:04:18Z, 200) | Requires hosting + database; no third-party dependency |
| Amazon Cognito | **No** — token endpoint "supports `client_secret_basic` and `client_secret_post` authentication" | `https://docs.aws.amazon.com/cognito/latest/developerguide/token-endpoint.html` (fetched 2026-11-21T00:04:18Z, 200, truncated at 22 KB; sentence present in retrieved portion) | Cognito is therefore **excluded** as the service-identity issuer. Its presence in the current SAM template's JWT authorizer does not make it `private_key_jwt`-capable |

Documentation capability is not tenancy configuration, not a signed contract, not a price, and not executed interoperability.

### 3.2 Proposal and the decision it forces

**Proposal (recommended): one managed OIDC tenant (Auth0, or Okta) hosting both the single demo subject and the service clients.** Rationale: it documents both interactive sign-in for the one demo user and machine clients with `private_key_jwt`; it keeps the simulator free of an identity server to operate; it exposes JWKS for rotation.

**This is a material decision, not a routine choice**: adopting a hosted identity SaaS introduces an external dependency and a data-processing boundary that the owner has not selected. It is escalated as **D-A** (§14), with a self-hosted Keycloak fallback.

**Rejected**: implementing a bespoke token endpoint inside the simulator. It would place identity and assertion-verification crypto in the product under test and create an unaudited authority; also rejected is any `client_secret` fallback for service clients.

### 3.3 Non-negotiable provider settings (proposal)

- Service clients: `token_endpoint_auth_method=private_key_jwt`; client secret **not issued/disabled**; no `none`-authenticated confidential client; no password/implicit grants.
- Demo user: authorization code + PKCE, single pre-provisioned subject, no self-registration, no roles/users CRUD (matches O4).
- **Access-token signing algorithm must be RSA** (RS256 baseline). API Gateway's HTTP API JWT authorizer validates "only RSA-based algorithms" (`https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-jwt-authorizer.html`, fetched 2026-11-21T00:05:30Z). An ES256-signed *access token* would be rejected at the edge even if the provider supports it. Client assertion algorithms are a separate setting and may be RS256/PS256.
- Key overlap window on rotation ≥ API Gateway best-effort JWKS cache (up to 2 hours per that page) plus margin; old key published but not signing for ≥ 24 h.
- Token lifetimes (proposal, not owner-approved numbers): access token ≤ 900 s; client assertion ≤ 60 s; demo JWT ≤ 900 s (O4 already proposes ≤15 min).

## 4. Token-endpoint authentication vs API access — two separate validations

The ADR requires these to be documented separately, and RFC 7523 separates them. The proposal states each side's exact job.

### 4.1 Client assertion validation (issuer side; specified here as the contract the client must satisfy)

| Check | Requirement |
| --- | --- |
| `client_assertion_type` | exactly `urn:ietf:params:oauth:client-assertion-type:jwt-bearer` |
| `client_id` | present; must equal assertion `iss` and `sub` |
| Signature | verified against the public key registered for that client (`kid` selects; unknown `kid` rejects) |
| `alg` | allowlist only: `RS256` baseline (optionally `PS256`); explicitly reject `none`, `HS*`, and any key/alg confusion |
| `aud` | exact token-endpoint URL of the tenant (Auth0: trailing slash form; Okta: full URL) — never the API audience |
| `exp` | ≤ 60 s ahead; `iat`/`nbf` within ±60 s skew; reject long-lived assertions |
| `jti` | required UUIDv4 by client policy; replay handling below |
| Grant | `client_credentials`; requested `scope` ⊆ registered allowlist; `resource`/`audience` parameter selects the API resource |

**Replay protection (defence in depth).** The provider may already enforce single use (Okta documents single-use when `jti` is present; Auth0 documents one-time use). The simulator additionally records `(client_id, jti)` in a dedicated DynamoDB table with `PutItem` conditioned on attribute absence and `TTL = exp + 120 s`; a duplicate is rejected before any token is used. A failed conditional put is a rejection, never a retry with a new `jti`.

### 4.2 Access-token validation (API side)

API Gateway validates `kid`, `iss`, `aud` (or `client_id` when `aud` absent), `exp`, `nbf`, `iat`, and route `scope`. It does **not** distinguish an access token from an ID token — AWS says so explicitly and recommends requiring route scopes. The proposal therefore requires, at both the edge and the application:

| Check | Edge (authorizer) | Application (final authorization point) |
| --- | --- | --- |
| Signature/issuer/expiry/audience | yes | re-validated for the values it depends on |
| Route scope present | yes | scope re-checked against the specific route/role |
| `client_id`/`azp` equals the certificate's bound client | yes (when mTLS) | yes |
| Deployment `env` claim equals this environment | yes | yes |
| Registered mandate (acquirer/merchant/terminal/route/role) | no | **yes — registry lookup, the only authority** |
| Revocation/registry status | no | yes |
| Token is not an ID token or a client assertion | structural: scope + audience required | same |

Never accepted at an API: the client assertion itself, an ID token, a demo token at a service route, or a service token at the demo route.

**Authorizer caching.** Cache keys are identity sources only (headers/query/context/stage). Because a cached allow must never be reused across a different certificate, token, scope or expiry, this proposal sets `authorizerResultTtlInSeconds = 0` on every mTLS service route. If caching is ever introduced it must include `Authorization`, the demo header where applicable, a certificate-derived context value and `$context.routeKey`, with TTL ≤ 60 s — but the default is no cache.

## 5. Certificate ↔ client ↔ mandate binding

A truststore proves only "issued by a CA we trust". It does not prove which client, environment or mandate. The binding is a registry record, checked at the final authorization point.

**Registry record (proposal, one item per client per environment):** `client_id`, `env`, `status` (`ACTIVE`/`SUSPENDED`/`REVOKED`), `certFingerprints[]` (lowercase hex SHA-256 of the DER leaf), `allowedScopes[]`, `allowedRoutes[]`, `role` (`RELAY`/`ACL`/`PROCESSOR`/`NETWORK`/`RELAY_EVIDENCE`/`DIAGNOSTIC`), `contexts[]` (synthetic acquirer/merchant/terminal sets), `c4KeyIds[]`, `version`, `updatedAt`.

**Check order at a service route:** (1) TLS handshake done by Gateway truststore; (2) authorizer recomputes the leaf fingerprint from the presented PEM (never trusts a header), requires exactly one registry match, `status=ACTIVE`, token `client_id` equal to the registry client, scope ⊆ allowed; (3) application resolves the business context/mandate from the registry and rejects any caller-supplied context that is not in `contexts[]`; (4) only then does business logic run.

**RFC 8705 binding is optional, not assumed.** If the selected issuer supports certificate-bound tokens (`cnf.x5t#S256`), the application compares the thumbprint to the actual peer certificate. Okta documents this as an Early Access feature, so the baseline design must not depend on it: registry binding is the baseline, `cnf` is an additive check when available. Claiming that "mTLS + private_key_jwt" alone binds certificate to token would be false.

**Revocation.** API Gateway does not check certificate revocation (documented for both REST and HTTP mTLS pages). Proposal: short-lived client certificates, registry `status` enforced per request, and a CRL/OCSP check in the authorizer only if an accessible CRL distribution point exists. Truststore updates are a versioned S3 object with version pinning (documented behaviour), and a stale truststore is treated as a configuration defect, not a silent allow.

## 6. Scenario realization matrix (NV-01)

Every row states API type, honest exposure, TLS termination, trusted identity source, OAuth parameters, mandate and expected deny outcome. "Public authenticated" means reachable from the internet but rejecting unauthenticated callers — it is **not** a private-network claim.

| # | Connection | API type | Exposure (accurate) | TLS termination / peer identity | Trusted identity source | Issuer / token endpoint / audience / scope | Mandate | Expected deny outcomes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| S1 | Browser → presenter relay | local HTTP server on `127.0.0.1` (not Gateway) | loopback only; no routable interface | loopback; optional local TLS not required | opaque relay session cookie (`HttpOnly`, `SameSite=Strict`) minted after issuer sign-in | relay performs authorization-code+PKCE; demo token `aud`=console API, scope `console.demo` | fixed demo subject only | wrong `Host`/`Origin`, missing CSRF token, non-loopback bind, expired session → refused before any upstream call |
| S2 | Relay → console HTTP API (POST authorize, GET detail/events) | HTTP API, payload v2 | **public authenticated** (custom domain, mTLS) | Gateway Regional custom domain, TLS 1.2, truststore; peer = client cert | leaf fingerprint → registry client `svc-console-relay`; `Authorization: Bearer <service token>`; demo context in `X-Demo-JWT` | tenant token endpoint; `aud`=console API; scopes `console.write`,`console.read`; demo token separate `aud` | registry: demo environment + fixed synthetic acquirer/merchant/terminal | missing cert → TLS failure; untrusted/expired cert → 403; service token client ≠ cert client → 403; demo token absent/expired/wrong subject → 401; wrong scope → 403; ID token/assertion presented → 403 |
| S3 | ACL → processor, ISO C2 (`POST /internal/sim-iso87a/v1/authorizations`) | **REST API** (Regional), binary `application/octet-stream`, Lambda proxy | **public authenticated by mTLS** — see §7; not network-private | Gateway custom domain + truststore; default `execute-api` endpoint disabled | leaf fingerprint → `svc-acl`; service token `Authorization` | `aud` = simulator resource; scope `sim.iso87a.authorize` | registry: ACL role + permitted synthetic context; response suffix/DE48 rules unchanged | missing/untrusted/expired cert → 403; token client ≠ cert → 403; scope missing → 403; body > 2048 or wrong media type → 400/415 with no dispatch; default hostname → rejected |
| S4 | Processor → network-sim → issuer-sim | Lambda invoke (no HTTP API) | **internal, AWS IAM only** — no TLS/OAuth app identity at this seam | none (in-account invocation, no network hop exposed) | IAM execution roles; `lambda:InvokeFunction` restricted by `AWS:SourceArn` | none (deliberate; see §12 and decision D-G) | distinct role per function; no cross-acquirer context | invoke from an unlisted principal → denied by resource policy; any app-level token presented is ignored, not trusted |
| S5 | DIRECT/RELAY attestation and late evidence admission | REST API, late-evidence route (JSON ≤ 8192 B) | **public authenticated** (mTLS) under Option A; **network-private + OAuth only** under Option B (§7.3) | Gateway custom domain + truststore (A); VPC endpoint, no mTLS (B) | leaf fingerprint → `svc-evidence` or `svc-evidence-diagnostic`; service token | `aud` = evidence resource; scopes `evidence.late` (delivery) / `evidence.diagnostic` (forward-only) | registry role + permitted `c4KeyIds`; diagnostic principal cannot admit a decision | unsigned/altered/mismatched hash → reject before admission; wrong role → 403; diagnostic principal attempting decision authority → 403; oversized → 413 |
| S6 | Relay → WebSocket + HTTP catch-up | WebSocket API + HTTP API | **public authenticated** (Regional WS custom domain, TLS 1.2). Native WS mTLS: **undocumented** — see §8 | TLS by API Gateway; **no client certificate** | `$connect` Lambda authorizer: service token (`Authorization`) + demo token (`X-Demo-JWT`); per-connection binding record | `aud` = WS resource; scope `ws.observe`; demo token separate | registry: single demo subject + environment; subscription tuple scope | missing either identity → 401 at connect; expired either → connection closed; foreign transaction/context → 403/close; stale epoch → ignored; callback for dead connection → 410 cleanup |
| S7 | Notifier → `@connections` callback / DynamoDB | AWS API (no public API) | internal AWS resource | AWS TLS, IAM SigV4 (resource permission, not app identity) | notifier execution role | none | scoped to the one WS API/stage ARN; no payment authority | SigV4 for another API/stage → denied; payload without source pointer → suppressed |
| S8 | GitHub Actions → AWS / Terraform state | OIDC federation | external CI identity | AWS STS | OIDC token (`aud=sts.amazonaws.com`, `sub` restricted) | n/a | role per environment; state prefix per environment | wrong repo/ref/environment/audience → `AccessDenied`; PR job without `id-token: write` → no credentials |

**Alternate-path inventory (must be closed for S2/S3/S5):** default `execute-api` hostname; any second stage/alias; Lambda function URLs; direct `lambda:InvokeFunction` from outside the intended principal; any `$default` route without an authorizer; CloudFront distribution in front of an API (would bypass mTLS semantics); API keys as a substitute (explicitly not used).

## 7. The PRIVATE REST conflict and compatible termination

### 7.1 Documented facts

- REST mTLS requires a **Regional custom domain**, an ACM certificate and an S3 truststore, and the default `execute-api` endpoint must be disabled to force the mTLS path; "**Mutual TLS isn't supported for private APIs**"; API Gateway does not verify revocation (`rest-api-mutual-tls.html`, fetched 2026-11-21T00:00Z).
- The HTTP API page repeats the same limitation (`http-api-mutual-tls.html`).
- Private REST APIs are callable only through an interface VPC endpoint, with `aws:SourceVpc`/`aws:SourceVpce` resource-policy conditions (`apigateway-private-apis.html`). Network privacy and Gateway-terminated mTLS are **different properties**, and the combination is not offered.

### 7.2 Option A — native, recommended

Regional custom domain + truststore mTLS, default endpoint disabled, no private API. Honest label: **public TLS endpoint, authenticated by mTLS, refusing all unauthenticated clients**. This is the only native combination; it introduces no architecture delta and keeps one consistent policy surface.

### 7.3 Option B — network-private, material delta, unverified

If genuine network-private ingress is mandatory for C2 or evidence admission: terminate mTLS at an **internal Application Load Balancer** in private subnets. ALB documents mutual TLS with either *passthrough* (chain forwarded, target verifies) or *verify* mode (ALB validates against a trust store, optionally with revocation checks), and forwards peer facts in `X-Amzn-Mtls-Clientcert*` headers (`https://docs.aws.amazon.com/elasticloadbalancing/latest/application/mutual-authentication.html`, fetched 2026-11-21T00:03:37Z). The ALB would then forward to the private API's VPC endpoint (IP target group) or directly to the Lambda.

This is a **material architecture delta** and must not be presented as a configuration detail:

- Feasibility of ALB → `execute-api` interface endpoint (Host/SNI preservation, private DNS, target health) is **UNVERIFIED**; it must be proven by Implementation against authoritative docs before adoption.
- Header trust: any `X-Amzn-Mtls-*` sent by a client must be stripped, and the target security group must accept traffic only from the ALB. Otherwise transport identity is forgeable.
- ALB is an always-on, billed resource; that conflicts with the owner's on-demand preference and needs explicit disposition.

**Recommendation:** Option A for S2/S3/S5. Reserve Option B only if the owner or Security requires network privacy as a control rather than a convenience.

## 8. WebSocket service authentication (NV-03)

**Documented position.** WebSocket APIs support a Lambda authorizer **only on `$connect`**, and "changing authorizer in `$connect` doesn't affect the already connected client" (`apigateway-websocket-api-lambda-auth.html`, fetched 2026-11-21T00:00Z). The WebSocket custom-domain page lists only Regional domains and TLS 1.2, and mentions no mTLS option. The shared v2 `DomainNames` schema does expose `mutualTlsAuthentication` on the resource, but no AWS page documents mTLS behaviour for WebSocket APIs. **Native WebSocket mTLS is therefore treated as undocumented/unverified and is not relied on.** It is not silently replaced by IAM: IAM remains only an AWS resource permission for callbacks.

**Proposal.**

1. Relay is the WebSocket client (server-side), so it can set headers: `Authorization: Bearer <service token>` and `X-Demo-JWT: <demo token>`. No token in URL, query string or message body.
2. `$connect` Lambda authorizer validates, in this order: both headers present; service token signature/issuer/audience/scope `ws.observe`; service token `client_id` = `svc-console-ws` and registry `ACTIVE`; demo token signature/issuer/audience/`sub` = the one configured demo subject and not expired; returns context `{clientId, demoSubject, env, jwtExp}`. Any failure → 401, connection refused.
3. The integration persists a technical connection record: `connectionId`, `epoch` (opaque, new on every connect), `clientId`, `demoSubject`, `env`, `jwtExp`, `leaseExpiry = min(jwtExp, now+30 min)`, `registryVersion`.
4. Because Gateway authenticates only `$connect`, **every** inbound frame (`subscribe`, `unsubscribe`, sync/heartbeat) is re-validated by the integration against the connection record, registry status and expiry; the server closes the connection at lease/expiry or on revocation. This is the application-level control the review requires; Gateway is not claimed to re-authenticate per message.
5. Only `$connect`, `$disconnect` and `$default` routes exist; all client frames land on `$default`, whose integration performs (4). No route trusts `connectionId` or demo identity supplied in a message body.
6. Callbacks use IAM `execute-api:ManageConnections` scoped to this API ARN and stage only. A `GoneException` deletes routing state; callback acceptance is not browser receipt (O5/O7 unchanged).
7. Bypass note: WebSocket APIs have no documented "disable default endpoint" switch, so the default `execute-api` hostname stays invocable. This is acceptable **only because** the same `$connect` authorizer guards every connection; the matrix records it as a residual, not a closure claim.

**Residual risk (must be stated to Security):** with no mTLS on this path, a stolen service access token plus the demo token would allow an observer connection. Mitigations: ≤15 min tokens, single demo subject, scope `ws.observe` only, no payment capability, revocation via registry, connection closure on expiry. This is a real reduction in transport assurance versus S2/S3 and is decision **D-D**.

**If mTLS is made mandatory on this path**, the only coherent compatible termination is to stop using API Gateway WebSocket for observation: terminate mTLS at a private Application Load Balancer and proxy the WebSocket upgrade to a container-hosted endpoint, re-implementing connection registry, fan-out and callback authorization that Gateway currently supplies, and replacing the `@connections` notifier contract. That is a major architecture change (new compute, new connection store, new notifier path, always-on cost) and is recorded only as the fallback shape — not recommended, not designed here, and not to be inferred as approved.

## 9. Browser / loopback relay (single demo principal)

- Bind `127.0.0.1` only; refuse non-loopback bind addresses at startup; no `0.0.0.0`.
- Exact `Host` allowlist (`127.0.0.1:<port>`, `localhost:<port>`) and exact `Origin` allowlist; reject `Origin: null`, cross-site and unknown hosts (DNS-rebinding defence).
- Same-origin only: no wildcard CORS, no `Access-Control-Allow-Credentials` with `*`; JSON content type enforced; CSRF token bound to the relay session and required on every state-changing call.
- Session: opaque random value in `HttpOnly; SameSite=Strict; Path=/` cookie, short TTL, rotated on sign-in; never a JWT in browser storage, URL, HTML, IndexedDB or console output.
- Tokens live in relay process memory only; no refresh token in the browser; on expiry the relay stops upstream writes/queries and closes sockets, then requires re-authentication (no payment retry).
- The relay holds the only client certificate and private key for S2/S3/S6; the browser holds none.
- Relay logs: method, route, status, correlation id, durations. Never Authorization, cookie, demo token, assertion, certificate PEM, ISO bytes or C4 envelope.

## 10. Attestation (C4) is a separate control — DIRECTION and late admission

C4 is unchanged in semantics; this section only fixes how its keys and transport interact with the identity planes.

- Signing principals: `PROCESSOR-DIRECT` (the simulator processor signs the 0110 it produced) and `NETWORK-SIM` (relay signer over the issuer's inner attestation), plus `ISSUER-SIM` for the inner attestation. Each has its **own Ed25519 key id**; keys are never shared, and a relay signature never substitutes for an issuer signature.
- Envelope bytes, canonical JSON (RFC 8785), exact `responseSha256` over the received decoded octet stream, `instructionSha256`, `mode` DIRECT/RELAY and the relay object are as specified in C4 — not restated or modified here.
- Verification order at admission: transport identity (§5) → envelope/body bounds and canonical encodings → outer `keyId` matches signed `keyId` and the registry's permitted key for that principal/role → Ed25519 signature → both hashes → P6 ISO/binding checks → atomic business+evidence+critical-event commit. A failure at any earlier step rejects the candidate; it never becomes a payment result.
- `TLS` and `OAuth` prove *who delivered*; only `P-EVID` proves *what the processor observed*. A signed sidecar with a valid OAuth token but a bad signature is rejected.
- Private Ed25519 keys follow §13 custody; public keys are ordinary configuration (not secrets).

## 11. Token/certificate/assertion swap defences (summary of the negative catalogue)

| Attack | Control | Where |
| --- | --- | --- |
| Certificate of client A + token of client B | `client_id` equality + registry fingerprint match | authorizer + application |
| Replay a captured client assertion | short `exp`, provider single-use `jti`, local `(client_id,jti)` conditional put | client policy + issuer + DynamoDB |
| Present the assertion as an API bearer | API requires access token with API audience and route scope; assertion `aud` is the token endpoint | edge + application |
| Present an ID token | route scope + API audience required; no route accepts unscoped tokens | edge |
| Demo token used for a service call | separate audience and scope; service routes reject `console.demo` | edge |
| Service token used for demo reads | demo routes require the demo audience/subject | edge + application |
| Cross-acquirer access with a valid token | registry `contexts[]` check at the final authorization point | application |
| Forged identity headers (`X-Client-Id`, `X-Amzn-Mtls-*`) | never trusted; identity only from authorizer context/anonymised peer facts | authorizer + application |
| Allow cached across certificates | authorizer caching disabled (TTL 0) | configuration |
| Alternate ingress (default host, function URL, extra stage) | default endpoint disabled, no function URLs, single stage, static assertion in CI | IaC + CI |
| Reconnect/expiry keeps access | lease = min(jwt exp, 30 min), revalidation per frame, server close | WS integration |
| Stale cursor/epoch | existing O5a/O6 generation and cursor binding (unchanged) | query/WS |

## 12. Internal hops, honestly labelled

S4 (processor → network → issuer) is Lambda-to-Lambda invocation. AWS SDK invocation carries **no application TLS or OAuth identity**; claiming otherwise would be false. Proposal: distinct execution role per function, `lambda:InvokeFunction` granted only to the specific caller principal with `AWS:SourceArn`, no public invoke path, and no token minted on the payment path (an added issuer round-trip before send would create a new failure mode on the send path and would gate payment on an external service — contrary to D2's separation). Decision **D-G** records this.

Where an HTTP hop exists between services (S2/S3/S5/S6), the full P-SVC + P-TLS model applies.

## 13. Key roles, custody, rotation, revocation

| Key | Type | Held by | Storage | Rotation | Revocation | Blast radius if lost |
| --- | --- | --- | --- | --- | --- | --- |
| OAuth client assertion key (per client) | RSA-2048+ PKCS#8 | each service process | secure parameter store (SecureString, CMK) | new key + JWKS/credential overlap ≥24 h; old removed after overlap | delete credential at issuer + registry `SUSPENDED` | one client's token issuance |
| TLS client certificate + key (per client) | RSA/ECDSA leaf from chosen CA | ACL, relay, evidence sender | key in secure parameter store; cert alongside | ≤ 90 days; new thumbprint added to registry before use | registry `REVOKED` + truststore/CRL action | one client's transport identity |
| Tenant/API TLS server cert | ACM-managed | Gateway | ACM | automatic | n/a | availability only |
| Truststore bundle | public certs | Gateway config | S3 object, versioned | on CA change | version pin rollback | admission of all mTLS clients |
| C4 Ed25519 signing keys | Ed25519 (64-byte sig) | processor / network / issuer principals | secure parameter store | new `keyId` published in registry; sign with new, verify both | registry removes `keyId` | forged/denied evidence for that principal |
| Demo session material | opaque random | relay memory | never persisted | per sign-in | process restart | one demo session |
| AWS deploy role | IAM role | GitHub Actions job | STS session (OIDC) | n/a | trust policy edit | CI blast radius per environment |

No key is generated by Terraform, printed by Terraform, or placed in Terraform state/outputs/plan artifacts. Public keys, JWKS documents, truststore bundles and certificate bodies are **not** secrets; private keys and demo/service tokens are.

## 14. Irreducible material decisions (owner or Security disposition required)

| ID | Decision | Options | Recommendation | Why it cannot be settled by DevOps |
| --- | --- | --- | --- | --- |
| **D-A** | OAuth/OIDC issuer | (1) Managed tenant (Auth0/Okta) — documented `private_key_jwt`; (2) self-hosted Keycloak (compute + DB); (3) defer | **(1)**, subject to explicit owner approval of the external SaaS dependency | Introduces a third-party processor and a new recurring dependency; owner has not selected it |
| **D-B** | C2 ingress exposure | (1) Regional custom domain + mTLS, public authenticated (native); (2) private REST + no native mTLS → ALB mTLS termination (material delta, unverified, always-on cost) | **(1)** | Owner selected mTLS; network privacy is a separate property with a cost and an unproven topology |
| **D-C** | Evidence/late-admission exposure | (1) same as D-B option 1; (2) network-private + OAuth only (no mTLS) | **(1)** for consistency; (2) only if Security requires no public surface for evidence | Changes which control proves the sender |
| **D-D** | WebSocket transport assurance | (1) accept no mTLS; OAuth token + $connect + per-frame revalidation; (2) re-architect the WS path behind a proxy that can do mTLS (unverified, not supported natively) | **(1)** with the residual risk in §8 stated explicitly | Accepting a weaker transport control on one boundary is a risk decision |
| **D-E** | Runtime secret injection | (1) SSM Parameter Store SecureString with CMK; (2) Secrets Manager; (3) GitHub Secrets only (not viable for runtime reads) | **(1)**; confirm current pricing rather than asserting it | Introduces a service and a cost line |
| **D-F** | Terraform state locking | (1) S3 native `use_lockfile=true` (DynamoDB locking documented as deprecated); (2) DynamoDB lock table (legacy Terraform) | **(1)** with Terraform ≥ 1.10 | Toolchain version is a deployment input |
| **D-G** | In-VPC Lambda hops | (1) IAM-only, documented separation; (2) OAuth service tokens on every internal invoke | **(1)** | Adding a token-minting hop to the send path changes payment failure modes |

## 15. Unverified items (explicit, not closures)

1. Issuer tenancy, plan, quotas and whether `private_key_jwt` is enabled for the chosen client type in that tenant.
2. Actual repository OIDC subject format (classic vs immutable-ID form). GitHub documents the immutable form for repositories created after 2026-07-15; this repository's creation date is not established here.
3. Whether a WebSocket custom domain accepts `mutualTlsAuthentication` and, if accepted, whether it enforces it. Not documented; must be tested only under a separately authorized environment.
4. ALB → private API Gateway endpoint forwarding behaviour (Option B), including Host/SNI and target health.
5. API Gateway request/response size, header-count and header-size limits for the C4 envelope (`X-Simulator-Evidence`) on the chosen API type — read the current quotas page before construction; not asserted here.
6. Actual behaviour of `X-Demo-JWT` and `Authorization` header preservation through the WS handshake and integration.
7. Terraform/OpenTofu version available in the environment (needed for `use_lockfile`).
8. Provider key-rotation propagation latency versus the Gateway JWKS cache.
9. KMS/SSM costs and quotas.
10. Whether the repository is private (affects OIDC subject evaluation only indirectly) — `PROJECT.md` records visibility UNVERIFIED.

## 16. Finding → design → test mapping

Every Security review finding that falls inside this commission is mapped to the section that answers it, the negative tests a later implementation/QA pass must run, and what would actually close it. **Nothing here is closed by writing this document**; closure requires an independent Security re-review of this artifact plus separately authorized executed tests.

| Finding | Answered in | Required design-level tests (later, authorized environment only) | Closure evidence | Residual |
| --- | --- | --- | --- | --- |
| **NV-01** scenario topology and alternate ingress | §6 (eight-row matrix with exposure labels), §7 (private-REST conflict and termination options), §8 (WS path), §11 (swap/bypass catalogue), configuration-schema §9 (static rules) | Walk each matrix row and confirm API type/exposure/TLS/identity/audience/scope/mandate/deny; request the default `execute-api` hostname on S2/S3/S5 → rejected; confirm no function URL, no second stage/alias, no unauthenticated `$default` route; confirm a second API cannot accept the first API's token | Reviewed matrix + passing static assertions in CI + a later authorized handshake-negative run | DNS, certificate issuance and private connectivity remain unexecuted deployment checks |
| **NV-02** token endpoint, certificate/client/mandate binding, C4 keys | §3 (issuer proposal and settings), §4 (assertion vs access-token validation, replay), §5 (registry binding, `cnf` option, revocation), §10 (C4 plane), §11 | Assertion replay with the same `jti` → second exchange fails; wrong `aud`, wrong `alg`, unknown `kid`, expired `exp`, over-long `exp` → reject; certificate of client A with token of client B → 403; missing scope → 403; cross-acquirer context → 403; ID token, demo token or raw assertion presented as service bearer → 403; C4 sender/signing-role swap, altered ISO bytes, forged or absent sidecar → rejected before confirmation; no rejection after possible-send triggers resend | Provider capability evidence + local negative vectors + registry binding review | Live provider issuance, key rotation and deployed context propagation unverified |
| **NV-03** WebSocket service authentication and lifecycle | §8 (connect, per-frame revalidation, lease, callbacks, bypass note), §6 S6, §11 | Connect with missing service token → 401; missing demo token → 401; expired either → closed; registry revocation → closed at next frame/heartbeat; foreign transaction or context subscription → denied; stale epoch/cursor → ignored/410; callback to a dead connection → 410 cleanup; WS outage does not block an otherwise authorized explicit HTTP payment; reconnect/heartbeat never sends a payment | Official feature mapping (this document) + executed handshake/lifecycle tests in an authorized environment | Header propagation through the WS handshake and callback behaviour unexecuted; native WS mTLS remains undocumented (decision D-D) |
| **NV-04** key custody, GitHub OIDC and Terraform state | §13 (key table), configuration-schema §2–§8 | Static trust-policy review with wrong repository / wrong ref / wrong environment / wrong audience negative cases; secret-to-runtime inventory proving no private bytes in Git, artifacts, state, plan or logs; confirm parameterized `us-east-2` backend with `use_lockfile`; confirm no `aws_ssm_parameter` value and no decrypting data source | Reviewed trust policy + inventory + parameterized backend configuration | Repository subject format, role IDs, runtime secret delivery and backend provisioning unverified |
| **NV-05** one-week expiry and no-resend identity | **Outside this commission** — see the concurrent `design/retention/` delta by Requirements. This design only consumes `HISTORY_TTL_DAYS`; it does not define per-record expiry applicability | Not proposed here | Requirements + Domain resolution | Deliberately not claimed |

Mapping completeness: four of four in-scope findings have a design answer and a test list; zero findings are claimed closed; zero acceptance claims are made.

## 17. What this proposal does not do

No ADR, requirements, contract, WORK_ITEM, plan, status, review or code file was modified. No approval is claimed. This document is input to an Implementation compatibility pass and an independent Security re-review; it is not their substitute.
