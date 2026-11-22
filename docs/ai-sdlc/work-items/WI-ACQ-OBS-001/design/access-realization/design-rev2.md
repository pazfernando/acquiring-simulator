# Operation A — access realization, revision 2 (Keycloak, internal late evidence, lease correction)

Status: **PROPOSAL, revision 2**. Documentary only. Not approved architecture, not IaC, not a deployment plan, not a Security verdict, not acceptance, and not a closure of any Security finding. Revision 1 (`design.md`, `configuration-schema.md`) is preserved byte-for-byte and **not edited**; this document supersedes it only as the current proposal draft.

Author: ai-sdlc-devops. Date: 2026-11-20 (America/Bogota, evening). Runtime: native OpenClaw subagent (`ai-sdlc-devops`); observed session model metadata `nan/deepseek-v4-flash`; configured default; **no model override**; provider-qualified identifier not exposed and not invented. Session `agent:ai-sdlc-devops:subagent:2bad2004-fb26-423c-8a6e-9f0e6f67d383`. No delegation.

## 0. Inputs, hash verification and reported discrepancies

| Input | Expected | Observed on disk | Result |
| --- | --- | --- | --- |
| `design/access-realization/design.md` (v1) | `83e4ea14b3959dcc6d8535aee0f0dd715783764e8922a72b6c0c3454a40829f4` | same | **MATCH**, untouched |
| `design/access-realization/configuration-schema.md` (v1) | `1f497daade1a8429458e2cc40047adf326a97f2fb6636d583e1bf2e930bf1bb2` | same | **MATCH**, untouched |
| `design/access-realization/compatibility-assessment.md` | `4886106c0afbf9ded1736ef3c0756c2bc8c738c58c61457faa0a635ecdd836ac` | same | **MATCH** |
| `design/access-realization/evidence/manifest.json` | not pinned by the commission | `2a4c400d752184424b5704a3cafd05abfc0060ffb443991fc12168f272d99dfe` | read; self-consistent with the assessment |
| `reviews/access-design-20261120/security-review.md` | `0d71ea007e56b12d9873df14126c22640c3abc68052b42db1f47b0a9688d218e` | same | **MATCH** |
| `owner-decisions.md` (evening sections) | manifest records `a4dde437495d7a96765ac322dc8e12680570325de384d5d657e7b7bf28b60b7e` | `fd040124319a946d28230aeb3994fb77bac82c00cb59e58b78df9e23531bae27` | **ADVANCED AGAIN — reported, not edited** |

**Discrepancies reported, not silently accepted.** (1) `owner-decisions.md` has advanced twice since v1 was written (`efef3d22…` → `a4dde437…` → `fd040124…`, 15 172 bytes at the second step). The evening sections naming D-D, D-A and CA-1 were read at the current identity, and they are the authority for this revision. (2) `WORK_ITEM.md` is Coordinator-owned and continues to move; not read as a technical input here. Neither file was modified. (3) The repository tree remains dirty per Implementation's binding evidence; no clean-HEAD claim is made for anything below.

Scope: realize the four owner dispositions (D-A Keycloak, D-D accepted, CA-1 internal late evidence) and acknowledge CA-2/CA-3/CA-4, plus the C4 payload-margin finding, without re-opening any decision and without touching approved files.

## 1. What this revision changes, and what it deliberately does not

| Item | v1 position | rev2 position | Why |
| --- | --- | --- | --- |
| Issuer | managed Auth0/Okta recommended (D-A open) | **self-managed Keycloak**, one hosting profile with options and a recommendation | owner selected "autogestionado"; D-A is resolved |
| Service clients | five incl. evidence clients | **three** (`svc-console-relay`, `svc-console-ws`, `svc-acl`); no evidence OAuth client | late evidence is internal and IAM-invoked; adding an OAuth client there would create the public surface CA-1 forbids |
| S5 late evidence | public authenticated mTLS REST route | **withdrawn**; replaced by internal invoke (S5i) | CA-1 owner resolution |
| WebSocket lease | `min(jwtExp, now+30 min)` | **`min(jwtExp, now+900 s)`, i.e. effectively `jwtExp`**; the 30-minute constant is deleted | under a ≤900 s token the 30-minute term can never bind |
| C4 roles | six-value vocabulary incl. `DIAGNOSTIC` role | mapped onto code's four-value `C4PrincipalRole`; `DIAGNOSTIC` becomes a scoped permission only | CA-2 |
| S4 claim | "restricted by `AWS:SourceArn`", resource policy | **not present today**; stated as required work, not as existing behaviour | assessment §4 |
| App-side token revalidation | implied existing | stated as **not implemented today**; required work | assessment §1 item 3 |
| C4 late-envelope margin | "unverified size risk" | quantified: worst case **6 964 B** of the 8 192 B bound (**1 228 B ≈ 15 % margin**); margin rules stated | assessment §2 |
| Contract C6/O4 | presented as current | recorded as **stale pending a traceable contract delta (CA-5)**, not to be implemented from the pinned text | assessment §5 |

Not done here, deliberately: no Terraform, SAM, realm JSON, code or test authoring beyond a configuration **schema**; no deployment, cloud call, credential or login; no approved-file edit (ADR, requirements, contracts, WORK_ITEM, reviews); no claim that NV-01…NV-04 are closed; no self-approval.

## 2. D-A realized — self-managed Keycloak hosting profile

### 2.1 What this adds, stated plainly

Keycloak is the first **always-on, stateful** component in an otherwise on-demand design. It adds: a container runtime (ECS service), a relational database (Aurora), a public TLS endpoint with a reverse proxy and WAF, an administrative surface, a patching cadence, a backup/restore procedure, and a new availability dependency for **new** authenticated work. It removes the external SaaS dependency and keeps identity inside the account.

It does **not** replace, and must not be confused with:

- the **C4 Ed25519 evidence key** — Keycloak never signs evidence and no Keycloak token authorizes an evidence signature;
- the **demo-user principal** — Keycloak hosts the one demo subject, but the demo login is a separate flow from service tokens and neither substitutes for the other;
- **AWS workload identity** — CI still uses GitHub OIDC → IAM; Lambda resources still use execution roles;
- **payment authorization** — a valid Keycloak token proves a caller; it never decides a payment, and the mandate still comes from the simulator's own registry.

### 2.2 Documented platform facts used (official Keycloak sources, read-only)

| Fact | Source |
| --- | --- |
| Supported relational databases include **Amazon Aurora PostgreSQL 17.x/16.x/15.x** and PostgreSQL 14–18; the default `dev-file` database "is not suitable for production use-cases" | `https://www.keycloak.org/server/db` (fetched 2026-11-21T01:13:41Z, truncated at 57 800 chars; cited table and sentence are in the retrieved portion) |
| Production requires TLS on all communication; admin REST API/console should be on a different hostname/context path and "Access to REST APIs needs to be blocked on the reverse proxy level, if they are not intended to be publicly exposed"; reverse proxy recommended; `/health/ready` for load-balancer checks; cluster = two or more instances | `https://www.keycloak.org/server/configuration-production` (fetched 01:13:40Z, not truncated) |
| Only `/realms/`, `/resources/`, `/.well-known/`, `/lb-check/` need public exposure; `/admin/`, `/realms/master/`, `/health`, `/metrics` should not be public; `proxy-headers` + `proxy-trusted-addresses`; edge termination needs `--http-enabled true`; admin on a separate hostname does **not** by itself restrict the admin REST API | `https://www.keycloak.org/server/reverseproxy` (fetched 01:14:37Z) and `https://www.keycloak.org/server/hostname` (fetched 01:14:48Z) |
| `hostname` is mandatory in production and dynamic hostname resolution is a Host-header-injection risk; `hostname-backchannel-dynamic` allows a private backchannel URL | hostname page above |
| Supported runtime platforms are bare metal/VM, containers, and OpenShift/Kubernetes; **other distributions "like Amazon EKS" are supported only in a best-effort manner**; JDK 17/21/25; hardware sizing "depends on the expected load" and is not specified numerically | `https://www.keycloak.org/server/supported-configurations` (fetched 01:14:37Z) |
| Client authentication with **Signed JWT** per RFC 7523, `client_assertion`, public key/certificate or **JWKS URL** registered; rotating via JWKS URL downloads new keys as needed | `https://www.keycloak.org/securing-apps/authz-client` (fetched 2026-21-01T00:04:18Z) and `JWTClientAuthenticator` javadoc (fetched 01:13:53Z) |
| "Export files from the Admin Console are not suitable for backups or data transfer between servers. Only boot-time exports are suitable" | Server Administration Guide (`https://www.keycloak.org/docs/latest/server_admin/index.html`, fetched 01:13:55Z, truncated at 372 KB) |
| Realm SSL mode **External requests** permits plain HTTP only for private/loopback addresses (localhost, 127.0.0.1, RFC1918, link-local) and requires TLS elsewhere | same guide (SSL mode section, in retrieved portion) |
| Synchronous Lambda invocation payload is **6 MB** request and response | `https://docs.aws.amazon.com/lambda/latest/dg/gettingstarted-limits.html` (fetched 01:16:11Z) |

Not established by these pages and therefore not claimed: Keycloak's default access-token lifespan, the exact signature-algorithm allowlist exposed by the client credential form, per-client token-lifespan overrides, and any price or sizing figure.

### 2.3 Hosting options and recommendation

| Option | Shape | Advantages | Costs/risks | Verdict |
| --- | --- | --- | --- | --- |
| **K1 — ECS Fargate + Aurora Serverless v2 (recommended)** | One ECS service (1 task), official Keycloak container, private subnets; Aurora Serverless v2 PostgreSQL 17.x with a 0.5 ACU floor; one internet-facing ALB + WAF for the public issuer paths; admin reached only through SSM port forwarding | No Kubernetes to operate; EKS explicitly best-effort per Keycloak docs; serverless DB floor is the cheapest always-on relational option here; one task is enough for a demonstrator | Always-on Fargate task, ALB hours/LCU, WAF, DB ACU floor, plus NAT or VPC endpoints; **single task = no HA** | **Recommend** |
| K2 — EC2 + RDS PostgreSQL | One instance + managed DB | Simpler networking, cheapest raw compute | AMI/patch ownership, instance replacement, same single-node availability, more operational toil for the same result | Acceptable fallback |
| K3 — EKS | Managed cluster + Keycloak | "Kubernetes-native" | Keycloak documents non-OpenShift Kubernetes (including EKS) as **best-effort** support; cluster control plane is another always-on cost and skill surface | **Not recommended** |

Sizing (proposal, not a documented recommendation): 1 vCPU / 2 GB task memory; Aurora `MinCapacity = 0.5`, `MaxCapacity = 2`; single task. Keycloak's own guidance says sizing depends on load, so these are starting values to be measured, not certified numbers.

### 2.4 Network placement and TLS

```text
presenter browser ──HTTPS──▶ internet-facing ALB (WAF) ──HTTP/8080──▶ Keycloak task (private subnet)
relay (presenter host) ──HTTPS──▶ same ALB (token endpoint only)
Lambda clients/authorizers ──HTTPS──▶ same ALB (token endpoint, JWKS)
operator ──SSM port-forward──▶ Keycloak task (8080/9000)   [no admin DNS record, no admin listener]
Keycloak ──TLS verify-server──▶ Aurora PostgreSQL (private subnets)
```

- **Public surface, allowlisted by host and path at the ALB:** `/realms/<realm>/protocol/openid-connect/*` (authorize, token, certs, userinfo, logout), `/resources/*`, `/.well-known/*`. Everything else — `/admin/*`, `/realms/master/*`, `/metrics`, `/health*`, root `/` — returns 403 at the proxy. The Keycloak docs recommend exactly this path discipline, and hostname-admin alone does not enforce it.
- **Administration:** no public admin hostname. Operators reach the task with SSM Session Manager port forwarding (availability of that capability to be confirmed per environment) or an equivalent operator-only path; the task's security group accepts traffic only from the ALB and from the operator path.
- **TLS:** ACM certificate on the ALB, TLS 1.2 minimum; `--http-enabled true` because TLS is edge-terminated (documented requirement); `proxy-headers=xforwarded`; `proxy-trusted-addresses` restricted to the ALB's reachable addresses where they are stable — if ALB addresses are dynamic, the control degrades to security-group isolation plus the ALB overwriting `X-Forwarded-*`, and that residual is recorded rather than papered over.
- **Hostname:** `--hostname https://auth.<domain>` as a **full URL** (avoids dynamic URL resolution); no `hostname-strict=false`.
- **Why the issuer must be publicly reachable at all:** the relay runs on the presenter's machine, outside AWS, and must call the token endpoint. An internal-only issuer would break the presenter-local relay that the owner selected. The mitigation is path allowlisting, WAF, `private_key_jwt` (no secrets to phish), and rate limiting — not a fictional private network.
- **Backchannel:** if any component later needs an internal issuer URL, `hostname-backchannel-dynamic=true` is the documented mechanism; not used now, because mixing public frontchannel and private backchannel without a private DNS design would create a second trust path.

### 2.5 Realm, clients and `private_key_jwt` configuration

One realm per environment (`acq-<env>`), so a token from one environment cannot be presented at another; registration disabled; brute-force detection on; realm SSL mode **External requests** (which is what permits the loopback HTTP callback while requiring TLS everywhere else).

| Client | Type | Grant / flow | Client authentication | Token settings | Audience / scopes |
| --- | --- | --- | --- | --- | --- |
| `console-demo` | public | authorization code + **PKCE**, standard flow only | none (public) | access token 900 s | `aud` = console API; scope `console.demo` |
| `svc-console-relay` | confidential | client credentials (service account) | **Signed JWT**, RS256, `kid` fixed, public key registered | access token 900 s | audience mapper → console API; scopes `console.write console.read` |
| `svc-console-ws` | confidential | client credentials | Signed JWT, RS256 | access token 900 s | audience mapper → WS API; scope `ws.observe` |
| `svc-acl` | confidential | client credentials | Signed JWT, RS256 | access token 900 s | audience mapper → simulator API; scope `sim.iso87a.authorize` |

Rules that make this a `private_key_jwt` realization rather than a Keycloak default:

1. The client's **Credentials** authenticator must be **Signed JWT** (RFC 7523), not `client_secret_basic`/`client_secret_post`. Keycloak advertises the corresponding `token_endpoint_auth_method` in its discovery document; the deployed value is to be verified, not assumed.
2. **No client secret is generated or retained** for the three confidential clients. If the console shows a secret, it is removed before use.
3. Signature algorithm **RS256** with a fixed `kid`, because the API-side verifier and the Gateway JWT authorizer accept RSA-family tokens only. The exact algorithm allowlist in the deployed Keycloak credential form is **unverified** and must be confirmed when the realm is created.
4. Every client gets an **Audience** protocol mapper that puts the API resource identifier into `aud`; without it, Keycloak's default audience is not the API and every request would fail the authorizer's audience check — a silent breakage worth calling out.
5. `scope` comes from client scopes, not from realm defaults.
6. `console-demo` uses **PKCE** because it is public and the relay is the only holder of its tokens.
7. Direct access grants (resource-owner password) stay **disabled**; the demo user signs in through the browser flow only.

### 2.6 Assertion-key custody, rotation and revocation

- Keys are generated **out-of-band**; only the public key/certificate is registered in Keycloak. Private keys never enter Keycloak, Terraform, Git or CI artifacts.
- Runtime custody: the service that signs the assertion (relay, ACL, WS client) reads its PKCS#8 key from a parameter-store SecureString (configuration-schema §4 of v1, unchanged in kind) or, for the presenter-local relay, from an operator-provisioned local file with owner-only permissions.
- **Rotation:** register the new public key, keep the old one valid for an overlap window, switch signing, then remove the old. Keycloak documents JWKS-URL registration as the zero-touch rotation path (`kid`-driven download). A static JWKS host does not exist in this design, so the default is **uploaded public key with a planned swap**; adopting a JWKS URL would require a hosting location, which is an open implementation detail, not an owner decision.
- **Revocation:** realm/client **not-before** policy (documented Keycloak feature) invalidates tokens issued before a timestamp; client deletion/disable stops new issuance; the simulator registry additionally marks the client `REVOKED` so the API refuses tokens even if Keycloak still considers them valid. Two independent switches, because one may lag.
- **Presenter-local key residual:** the relay's private key lives on the presenter's machine, outside AWS controls. Mitigation: narrow scopes (`console.*`, `ws.observe` only), rotation after each demonstration window, immediate registry revocation on suspected loss, and no evidence or payment authority attached to that client.

### 2.7 How each party obtains tokens

| Party | Flow | Notes |
| --- | --- | --- |
| Browser (presenter) | redirect to `authorize` with PKCE → relay callback on `127.0.0.1` → relay exchanges code | token never reaches the browser; realm SSL mode allows loopback HTTP callback |
| Relay | code exchange (demo) + `client_credentials` with Signed JWT (service) | both tokens live in relay memory only |
| ACL (Lambda) | `client_credentials` with Signed JWT to the public token endpoint | key from parameter store; no token on the payment send path beyond the HTTP hop it already makes |
| WebSocket client | `client_credentials` (WS scope) | reconnect every ≤900 s mints a fresh assertion and token; no refresh on the socket |
| Evidence sender | **no token** — internal IAM invoke | see §4 |

### 2.8 Operations: backup, patching, availability, ownership

| Concern | Proposal |
| --- | --- |
| Backup | Aurora automated backups + point-in-time recovery, retained per a chosen window (proposal: 7 days; not the same thing as the seven-day functional-history retention). Realm configuration is **code**: a boot-time import artifact, because Admin-Console exports are documented as unsuitable for backups. |
| Restore | Restore DB to a point in time, start Keycloak against it, verify realm/clients, then re-verify all service clients can obtain tokens. No restore claim without a rehearsed run. |
| Patching | Official container pinned by digest; monthly rebuild and redeploy in a maintenance window; Keycloak major upgrades include DB schema migration and must be rehearsed on a copy before production. |
| Availability | Single task: an outage blocks **new** authentication and token issuance, therefore new work; in-flight payments are unaffected because the payment path holds no Keycloak dependency after authorization. Recorded as a real availability dependency, not a "non-functional" detail. |
| Ownership | Accountable owner: the product owner (Fernando). DevOps authors the runbook and configuration; no 24/7 on-call, SLA or "high availability" claim is made by this document. |

### 2.9 What Keycloak does not change in the rest of the design

The identity planes, the registry-based mandate, the certificate↔client↔mandate binding, the authorizer/TTL-0 rule, the token-endpoint assertion rules (except that the issuer is now Keycloak), the C4 evidence plane, the GitHub OIDC/state-backend schema and every deny test remain as in v1; only the provider-specific facts and the evidence route change.

## 3. D-D realized — the lease is corrected, the token lifetime is not

**The inconsistency.** v1 said access tokens ≤900 s and a lease of `min(jwtExp, now+30 min)`. Since `jwtExp ≤ now+900 s`, the 30-minute term can never be the minimum. The lease was therefore always the token expiry, and quoting a second constant implied a control that did not exist.

**Resolution (this revision's position).**

- Effective lease = **`min(jwtExp, now+900 s)`**, which under the token policy is **simply `jwtExp`** — at most 15 minutes, typically less because the token is minted before the connect. The 30-minute constant is **deleted**, not renamed.
- **The token lifetime stays at 900 s; it is not raised to make the lease bind.** A 30-minute bearer token on the one boundary that already has no mTLS would be a strictly worse trade. A second control is not created by lengthening the credential it depends on.
- **Reconnect is the renewal mechanism.** At lease expiry the server closes the connection; the relay obtains a fresh assertion and token (one token request) and reconnects with a **new epoch**. No token refresh happens in place on the socket, and no reconnect path can send a payment.
- **Cursor alignment.** C7 bounds the signed catch-up cursor to 15 minutes, which now coincides exactly with the lease. Consequence stated explicitly: a socket that lives to its lease end always has an expired cursor, so every reconnect re-mints a cursor, and any catch-up pass that itself runs longer than 900 s must re-mint mid-pass rather than continue on an expired cursor.
- **Registry revocation read.** Per-frame revalidation needs a registry read whose cache policy is a decision, not a default. Proposal: a short-TTL in-process cache (≤30 s) plus an unconditional strongly consistent read on the heartbeat (30 s) and on any scope-changing frame (`subscribe`, `unsubscribe`). That bounds revocation delay to about one heartbeat while keeping per-frame cost off the hot path. The current code caches the registry at cold start with no TTL (`AwsRuntime.caller`), which is incompatible with this requirement and is recorded as required work.
- **What D-D still means.** Transport assurance on this path is weaker than on S2/S3; the scope limits are unchanged (notification and catch-up only; never send, retry, reverse or re-price; a lost socket must not block an already-authorized HTTP payment). NV-03 is **not** closed by this correction.

## 4. CA-1 realized — late evidence is internal only (S5 withdrawn)

**Withdrawal.** The v1 S5 "public authenticated mTLS REST route" for late evidence is **withdrawn**. No API Gateway route, no custom domain, no truststore and no internet-reachable endpoint is created for late evidence, and the `svc-evidence` / `svc-evidence-diagnostic` OAuth clients proposed in v1 are deleted. The approved rule stands: late evidence uses the same internal admission; no public late endpoint, no automatic financial recovery.

### 4.1 Internal realization (S5i)

| Element | Proposal |
| --- | --- |
| Invoking component | The **simulated processor** for a DIRECT attestation; the **network simulator** for a RELAY envelope it countersigned; a **diagnostic delivery principal** only to forward an unchanged signed proof. No other component invokes admission. |
| Mechanism | **Private Lambda invocation** into a dedicated late-evidence admission function. No function URL, no API Gateway route, no VPC endpoint, no public DNS name. |
| Authorization of the caller | Lambda **resource-based policy** on the admission function permitting only the enumerated producer role ARNs, with `aws:SourceArn` (or `aws:SourceAccount` plus principal) conditions. Today the code has identity-policy grants only, with no `AWS:SourceArn` and no resource policy (assessment §4); adding them is required work, not a claim of current behaviour. |
| Sender identity | Derived from the **invocation context** (the calling principal's ARN mapped through the registry to a `C4PrincipalRole` and an allowed `keyId` set). Never from a payload field, header or body assertion. The widened transport context (CA-3) is what carries this fact to the verifier; its shape is Implementation's data-model decision. |
| Proof | Unchanged C4 Ed25519 verification: canonical envelope bounds, `keyId` match, signature over the canonical attestation, `instructionSha256`/`responseSha256`, DIRECT/RELAY roles, relay countersignature for the network hop. |
| Diagnostic delivery | A **scoped permission** (`diagnosticForwardPermitted(env, principal, acquirerRef)`), not a role: it may forward an unchanged signed proof and confers no signing, receipt or outcome authority (see §5). |
| After verification | Existing P5/P6 admission: business-version correlation and atomic commit of evidence + business + critical event — **implemented but not wired** (CA-4); no statement here assumes deployed atomicity. |
| Failure semantics | A rejected delivery never becomes a payment result; no resend, retry, reversal or financial recovery worker is introduced. |

### 4.2 Deny paths that must be tested (design obligations, not executed)

1. Invoke from a role outside the admission function's resource policy → `AccessDenied` before any handler runs.
2. Invoke from a permitted role but with a payload claiming a different sender → rejected (identity never comes from the payload).
3. DIRECT envelope whose `signingPrincipal` is not the configured attesting principal for that sender → rejected.
4. RELAY envelope with a missing, altered or replaced inner issuer signature → rejected; the network signature never substitutes.
5. Diagnostic principal presenting a decision-bearing envelope → rejected; forwarding an unchanged proof → accepted, with no outcome authority.
6. Altered body bytes or mismatched `responseSha256`/`instructionSha256` → rejected before admission.
7. Envelope above the late bound → rejected before parsing (`LATE_ENVELOPE_SIZE`).
8. Any attempt to reach admission over HTTP from an internet client → no route exists; the negative test is the absence of the route, plus a scan of deployed API mappings and function URLs.
9. A late delivery arriving after the payload expired → truncation/expiry semantics, never resurrection.

## 5. CA-2 — role vocabulary aligned to the code

The code defines exactly four `C4PrincipalRole` values: `ISSUER_SIM`, `PROCESSOR_AUTH`, `NETWORK_SIM`, `ACL_AUTH`. v1's six-name vocabulary is replaced by this mapping; no new role value is introduced.

| v1 name | Code role | Note |
| --- | --- | --- |
| `ACL` | `ACL_AUTH` | channel ACL signs the instruction-side attestation |
| `PROCESSOR` | `PROCESSOR_AUTH` | simulator processor's DIRECT attestation |
| `NETWORK` | `NETWORK_SIM` | network simulator's RELAY countersignature |
| `RELAY` / `RELAY_EVIDENCE` | `NETWORK_SIM` | a relay object is a `NETWORK_SIM` signature, not a separate role |
| — (implicit in v1) | `ISSUER_SIM` | inner issuer attestation; now named explicitly |
| `DIAGNOSTIC` | **no role** | scoped permission `diagnosticForwardPermitted(env, principal, acquirerRef)`, deny-by-default, no signing, receipt, outcome or durability authority |

Consequences: the registry's role field accepts only these four values; diagnostic forwarding is stored and checked as a permission with an acquirer scope, never as a role; and no authorizer, token scope or client registration may express `DIAGNOSTIC` as standing. This is exactly the distinction the late-delivery tests already pin (`wrongSenderWithoutDiagnosticPermissionFailsClosed`, `diagnosticForwardWithExplicitScopedPermissionIsAcceptedWithoutOutcomeAuthority`, `diagnosticForwardOutsideConfiguredScopeIsRefused`).

## 6. CA-3 and CA-4 — acknowledged, not designed here

- **CA-3 (transport context).** `C4AuthenticatedTransportContext` today carries only `(environmentId, authenticatedSender)`; the binding in this design needs the leaf fingerprint, the token `client_id`, the registry version and the permitted contexts at the final authorization point. **Widening that record is Implementation's data-model decision, with a wide blast radius** (it gates `C4CarrierParser.bindSender`, `C4EvidenceVerifier`, `C4LateDeliveryVerifier` and every composer). This revision states the required facts and the deny order; it does not propose the record shape, and it assumes nothing about how the widening is done.
- **CA-4 (unwired atomic gateways).** `AwsRuntime` still constructs `AuthorizationService` on the legacy four-argument path and installs none of the claim/dispatch-intent/result/network/late-evidence gateways. Therefore **nothing in this revision asserts that atomic admission is live**. Every use of "atomically" below and in v1 is a constraint on future wiring, and the internal late-evidence path must not be described as atomic until the gateways are wired with an explicit capacity policy and environment identity.
- **CA-5 (stale contract text).** `channel-contract.md` C6 and `observation-contract.md` O4 still say the relay IAM-signs `$connect`. D-D supersedes that in practice, but the traceable contract delta must be authored through the normal Requirements/Domain path. This revision does not edit those files and does not treat the pinned text as current.

## 7. C4 payload margin inside Gateway limits

Measured by Implementation from declared bounds (canonical RFC 8785, printable ASCII, all values at maxima):

| Artifact | Worst case | Bound | Margin |
| --- | --- | --- | --- |
| DIRECT evidence envelope | 738 B / 984 b64 chars | 4 096 B / 5 464 chars | ample |
| RELAY evidence envelope | 1 288 B / 1 720 b64 chars (1 818 total header bytes) | 4 096 B / 5 464 chars, 8 192 total header bytes | ample locally |
| Late envelope, DIRECT worst | 3 606 B | 8 192 B | 4 586 B |
| Late envelope, RELAY worst | 4 156 B | 8 192 B | 4 036 B |
| **Late envelope with inner evidence at its 4 096 B cap** | **6 964 B** | **8 192 B** | **1 228 B ≈ 15 %** |

Position for rev2:

1. **The late bound stays 8 192 B and the tight case is the binding one.** The all-maxima RELAY case is not the worst case; the worst case is a legitimate delivery whose inner evidence uses its full allowance. Design decisions must be checked against 6 964 B, not 4 156 B.
2. **No new top-level field may be added to the late envelope** without re-deriving the worst case and re-measuring. Any increase to the original-body cap, evidence cap or receipt-id cap consumes the same 1 228 B.
3. **The bound is a protocol choice, not a platform ceiling on the internal path.** Late evidence now travels by Lambda invocation, whose synchronous payload limit is 6 MB (documented). So the 8 192 B limit is self-imposed and can be raised by a contract change if ever needed — it must not be raised quietly in code.
4. **Add a CI arithmetic check** that recomputes the worst case from the declared field bounds and fails if it exceeds the bound; Implementation already produced `payload-size-calculations.json`, which makes this cheap.
5. **The normal-response header remains the unverified edge.** The RELAY case needs a single header value of 1 720 chars and 1 818 total header bytes; no AWS page in the reviewed evidence asserts a per-header cap above ~1.7 KB. Do **not** change C4 to avoid the question; if the deployed quota turns out to be lower, the only honest fix is a contract amendment (e.g. body-carried evidence), which requires Requirements/Security disposition.
6. **Platform headers are additive.** The app-side total counts only headers the application writes; a deployed response adds at least a request id. With 1 818 B used this is absorbed; a payload near the ceiling would not be.

## 8. Scenario matrix rev2

Changes from v1: S5 withdrawn and replaced by S5i; S4 corrected to describe what exists; S2/S3 now obtain service tokens from Keycloak; S6 lease corrected. Everything else carries forward.

| # | Connection | API type / mechanism | Exposure (accurate) | TLS / peer identity | Trusted identity source | OAuth issuer / endpoint / audience / scope | Mandate | Expected deny outcomes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| S1 | Browser → presenter relay | local HTTP on `127.0.0.1` | loopback only | none required on loopback; realm permits loopback HTTP only in *External requests* mode | opaque relay session cookie, minted after Keycloak sign-in | Keycloak `console-demo`, PKCE, `aud` = console API, scope `console.demo` | single demo subject | wrong Host/Origin, missing CSRF, non-loopback bind, expired session → refused before any upstream call |
| S2 | Relay → console HTTP API | HTTP API payload v2, custom domain | **public authenticated** (mTLS) | Gateway Regional custom domain, TLS 1.2, truststore | leaf fingerprint → registry `svc-console-relay`; `Authorization` service token; `X-Demo-JWT` demo token | Keycloak token endpoint; `aud` = console API; scopes `console.write console.read`; demo token separate audience | registry: environment + fixed synthetic acquirer/merchant/terminal | missing/untrusted/expired cert → 403; token client ≠ cert client → 403; demo token absent/expired/wrong subject → 401; wrong scope → 403; assertion or ID token as bearer → 403 |
| S3 | ACL → processor ISO C2 | REST API, binary octet-stream, Lambda proxy | **public authenticated by mTLS** (native; private REST + mTLS is documented unsupported) | Gateway custom domain + truststore; default endpoint disabled | leaf fingerprint → `svc-acl`; service token | Keycloak; `aud` = simulator API; scope `sim.iso87a.authorize` | ACL role + permitted synthetic contexts | as v1, plus Keycloak-minted token from another realm/environment → 403 |
| S4 | Processor → network-sim → issuer-sim | Lambda invoke | internal, AWS IAM only | none; no network hop | IAM roles; **resource policy + `AWS:SourceArn` is required work and is absent today** | none by design (D-G) | distinct role per function | invoke from unlisted principal denied *once the resource policy exists*; app tokens are ignored, never trusted |
| ~~S5~~ | ~~Late evidence, public route~~ | — | — | — | — | — | — | **WITHDRAWN** |
| S5i | Producer → late-evidence admission | **private Lambda invoke**, resource policy with source ARN/account | **internal only**; no route, no URL, no DNS | none; invocation authorization is the transport control | invocation principal → registry role + allowed `keyId`s; Ed25519 proof unchanged | **no OAuth token**; no Keycloak client | role ∈ four values; diagnostic is a scoped permission | §4.2 items 1–9 |
| S6 | Relay → WebSocket + HTTP catch-up | WebSocket API + HTTP API | **public authenticated** (Regional WS custom domain, TLS 1.2); **no mTLS** (undocumented) | TLS by API Gateway; no client certificate | `$connect` Lambda authorizer: service token + `X-Demo-JWT`; per-frame revalidation; registry cache ≤30 s | Keycloak; `aud` = WS API; scope `ws.observe` | single demo subject + environment; lease = `jwtExp` (≤900 s) | missing either identity → 401; expired either → closed; revoked registry → closed ≤1 heartbeat; foreign context → denied |
| S7 | Notifier → `@connections` / DynamoDB | AWS API | internal AWS resource | IAM SigV4 (resource permission) | notifier role | none | scoped to one WS API/stage | other API/stage denied; unsanitized payload suppressed |
| S8 | GitHub Actions → AWS / state | OIDC federation | external CI identity | AWS STS | OIDC token, `aud=sts.amazonaws.com`, exact `sub` | n/a | role per environment; state prefix per environment | wrong repo/ref/environment/audience → `AccessDenied`; PR job without `id-token: write` → no credentials |

Role vocabulary in this table is already the code's four values plus the diagnostic permission; no `DIAGNOSTIC` role appears anywhere.

## 9. Configuration schema delta (Keycloak replaces the managed tenant)

Configuration-only. No Terraform or SAM is authored here; the v1 schema's structure (GitHub Variables/Secrets, parameter store, S3 backend, OIDC trust) is retained, and only the provider-specific entries change.

### 9.1 Removed from v1

`OAUTH_CLIENT_ID_EVIDENCE`, `OAUTH_CLIENT_ID_EVIDENCE_DIAGNOSTIC`, `OAUTH_AUDIENCE_EVIDENCE`, `OAUTH_SCOPES_EVIDENCE*`, `OAUTH_KEY_EVIDENCE*`, `CUSTOM_DOMAIN_EVIDENCE`, and the tenant-shaped `OIDC_ISSUER_URL`/`OIDC_TOKEN_URL`/`OIDC_JWKS_URI` values (names kept, values now Keycloak paths).

### 9.2 GitHub Variables (non-secret)

| Variable | Scope | Value shape | Consumer |
| --- | --- | --- | --- |
| `KC_VERSION` | repo | Keycloak release line (pin) | build |
| `KC_IMAGE_DIGEST` | env | `quay.io/keycloak/keycloak@sha256:<digest>` | ECS task definition |
| `KC_REALM` | env | `acq-<env>` | issuer URL, verifiers |
| `KC_HOSTNAME_URL` | env | `https://auth.<domain>` (full URL, mandatory in production) | Keycloak `KC_HOSTNAME` |
| `KC_TASK_CPU` / `KC_TASK_MEMORY` | env | `1024` / `2048` (proposal) | ECS task size |
| `KC_DB_ENGINE` | env | `postgres` | Keycloak `KC_DB` |
| `KC_DB_HOST` / `KC_DB_NAME` / `KC_DB_USER` | env | Aurora endpoint / db / role | Keycloak |
| `KC_DB_TLS_MODE` | env | `verify-server` | Keycloak |
| `KC_DB_CA_BUNDLE_URI` | env | `s3://…` or parameter name | Keycloak trust store |
| `AURORA_MIN_ACU` / `AURORA_MAX_ACU` | env | `0.5` / `2` (proposal) | Aurora Serverless v2 |
| `OIDC_ISSUER_URL` | env | `https://auth.<domain>/realms/<realm>` | authorizers, clients |
| `OIDC_TOKEN_URL` | env | `https://auth.<domain>/realms/<realm>/protocol/openid-connect/token` | assertion `aud` |
| `OIDC_JWKS_URI` | env | `…/protocol/openid-connect/certs` | authorizers |
| `OAUTH_CLIENT_ID_CONSOLE_RELAY` / `_WS` / `_ACL` | env | Keycloak client ids | clients |
| `DEMO_CLIENT_ID` | env | `console-demo` | relay |
| `OAUTH_AUDIENCE_CONSOLE` / `_SIM` / `_WS` | env | API resource identifiers | audience mappers, authorizers |
| `OAUTH_SCOPES_CONSOLE_RELAY` / `_WS` / `_ACL` | env | space-separated | clients |
| `DEMO_SUBJECT` | env | exactly one subject | relay, authorizers |
| `ACCESS_TOKEN_TTL_SECONDS` | env | `900` | Keycloak realm setting, verifiers |
| `WS_LEASE_SECONDS` | env | `900` — **must equal `ACCESS_TOKEN_TTL_SECONDS`** | WS integration |
| `REGISTRY_CACHE_TTL_SECONDS` | env | `30` | authorizers, WS integration |
| `WS_HEARTBEAT_SECONDS` | env | `30` | WS integration |
| `CURSOR_TTL_SECONDS` | env | `900` | query API |
| `MTLS_TRUSTSTORE_URI` / `MTLS_TRUSTSTORE_VERSION` | env | S3 object + version | custom domains (S2/S3 only) |
| `CUSTOM_DOMAIN_CONSOLE` / `_SIM` / `_WS` | env | FQDNs | API mappings |
| `ACM_CERT_ARN_*` | env | ACM ARN | custom domains, ALB |
| `REGISTRY_TABLE`, `ASSERTION_REPLAY_TABLE` | env | table names | authorizers/clients |
| `HISTORY_TTL_DAYS` | env | `7` (per-record applicability is NV-05, not this schema) | DynamoDB TTL |
| `GITHUB_SUBJECT_FORMAT` | repo | `classic` \| `immutable` | OIDC trust |
| `AWS_REGION`, `AWS_ACCOUNT_ID`, `AWS_ROLE_ARN`, `ENVIRONMENT`, `TF_STATE_BUCKET`, `TF_STATE_KEY`, `TF_STATE_LOCK`, `TF_VERSION` | mixed | as v1 | CI/backend |

### 9.3 GitHub Secrets (sensitive)

| Secret | Scope | Content | Consumer |
| --- | --- | --- | --- |
| `OAUTH_KEY_CONSOLE_RELAY` / `_WS` / `_ACL` | env | PKCS#8 RSA private key for the Signed JWT assertion | parameter store → service runtime |
| `TLS_KEY_CONSOLE_RELAY` / `_ACL` (+ `TLS_CERT_*`) | env | mTLS client key and certificate | parameter store |
| `C4_SIGNING_KEY_<PRINCIPAL>` | env | Ed25519 private key | parameter store |
| `KC_DB_PASSWORD` | env | Aurora role password | ECS task secrets injection |
| `KC_BOOTSTRAP_ADMIN_USERNAME` / `KC_BOOTSTRAP_ADMIN_PASSWORD` | env | **one-time** initial admin | first boot only; removed/disabled after a named operator account exists |

Rules: no client secret for the three confidential clients (Signed JWT only); no Keycloak admin credential in the repository, a task environment in plaintext, an artifact or a log; the realm import artifact contains no secrets.

### 9.4 Runtime injection and custody

- Keycloak container: DB password and (if needed) trust-store material injected by the orchestrator from the parameter store at task start (ECS secret injection; confirm exact mechanism for the chosen platform) — never baked into the image, never in the task definition in plaintext, never in Terraform.
- Service clients: env var holds the **parameter name**; `ssm:GetParameter` + `kms:Decrypt` (via SSM only) scoped to that ARN; read once per cold start; never logged, never returned, never written to `/tmp`.
- Realm configuration: boot-time import artifact built from the environment's values; treat as code, review it, and never include a client secret or admin password.
- Terraform: no `aws_ssm_parameter` carrying key material, no decrypting data source in root config, no secret output, no secret-bearing plan artifact. v1 §1 rules stand unchanged.

### 9.5 GitHub OIDC and state backend (retained)

Unchanged from v1 §6–§8: provider `https://token.actions.githubusercontent.com`, `aud=sts.amazonaws.com`, exact `sub` (`repo:<owner>/<repo>:environment:<env>`, or the immutable-ID form if the repository uses it — unverified), no wildcards, `id-token: write` only on the deploy job, actions pinned by SHA; S3 backend in `us-east-2` with `use_lockfile = true`, versioning, SSE-KMS, Block Public Access, `allowed_account_ids`, bootstrap outside the stack.

**Toolchain note (not an owner decision):** the checkout contains SAM only and no `.tf` or `.github/` directory. PROJECT_EXECUTION.md already establishes native Terraform + S3 remote state as the default for *commissioned* AWS infrastructure, so new Keycloak infrastructure is Terraform; the existing Lambda/API resources stay in SAM until a separately authorized migration. No rewrite is implied here.

### 9.6 Static checks to add (design obligations)

| Rule | Purpose |
| --- | --- |
| ALB listener rules deny `/admin/*`, `/realms/master/*`, `/metrics`, `/health*` on the public host | admin surface not public |
| no public DNS record for an admin hostname | no accidental admin exposure |
| task security group ingress only from the ALB (and the operator path) | no direct access to Keycloak |
| Keycloak image pinned by digest, not tag | reproducible, patchable |
| late-evidence function has no URL and a resource policy naming only producer roles + source ARN/account | CA-1 internal-only |
| `WS_LEASE_SECONDS == ACCESS_TOKEN_TTL_SECONDS` | prevents the dead-lease class of error returning |
| `REGISTRY_CACHE_TTL_SECONDS ≤ 30` | bounds revocation delay |
| CI recomputes the C4 late worst case from declared bounds | margin guard |

## 10. Deny catalogue additions (Keycloak and internal evidence)

| Test | Expected |
| --- | --- |
| `GET https://auth.<domain>/admin/...` and `/realms/master/...` | 403 at the proxy; no admin JSON |
| `/metrics`, `/health`, root `/` on the public host | 403 at the proxy |
| token minted in realm `acq-<other-env>` presented at this environment | 403 (issuer/audience/registry) |
| client with `client_secret_basic` presented instead of an assertion | rejected; these clients have no secret |
| assertion with unknown `kid`, wrong `alg`, wrong `aud`, expired/long `exp`, replayed `jti` | rejected at the token endpoint, plus local replay cache |
| client disabled at Keycloak, or registry `REVOKED` | new tokens refused / existing tokens refused at the API |
| realm/client not-before set after token issuance | token refused at the API even though its signature is valid |
| missing audience mapper (token `aud` is not the API) | 403 at the authorizer |
| Keycloak task reached directly (not via ALB) | connection refused by security group |
| late-evidence invoke from an unlisted role | `AccessDenied`, no handler execution |
| late-evidence payload claims a sender other than the invoker | rejected before verification |
| any HTTP attempt to submit late evidence | no route; verified by deployed-route inventory |
| presenter-local relay key loss | registry revocation + Keycloak client disable; no payment authority exists to abuse |

## 11. Finding → design → test mapping (rev2 status)

| Finding | Where answered now | Status of the finding |
| --- | --- | --- |
| NV-01 scenario topology / alternate ingress | §8 matrix (S5 withdrawn, S5i added, S4 corrected) | **OPEN** — matrix still requires independent Security review and later executed negatives |
| NV-02 token endpoint, cert/client/mandate, C4 keys | v1 §3–§5, §10 + §2 (Keycloak capability, revocation, rotation), §5 (role mapping) | **OPEN** — provider is now selected, but tenancy capability, deployed binding and rotation are unverified |
| NV-03 WebSocket authentication and lifecycle | §3 (lease correction, registry cache) + v1 §8 | **OPEN** — D-D accepted the transport risk; Security still reviews the realized path |
| NV-04 key custody, OIDC, state | v1 §13 + §2.6, §9 | **OPEN**, scope widened by Keycloak (DB credential, admin bootstrap, image pinning) |
| NV-05 expiry / no-resend identity | out of scope (`design/retention/`) | unchanged |
| CA-1 / CA-2 / CA-3 / CA-4 / CA-5 | §4 / §5 / §6 / §6 / §6 | **recorded, not closed**; CA-3 and CA-4 are Implementation decisions |

Zero findings are claimed closed. This document is input to an independent Security re-review, not a substitute.

## 12. Unverified items (v1 list plus additions)

Carried forward from v1: issuer tenancy/plan/quota; repository OIDC subject format; native WS mTLS (still undocumented); ALB→private Gateway forwarding (still untested and now less relevant); Gateway header/body limits for the C4 header; WS header preservation; Terraform version for `use_lockfile`; rotation propagation vs JWKS cache; KMS/SSM costs; repository visibility.

Added in rev2:

1. Keycloak's default access-token lifespan and whether per-client overrides exist — the design sets the value explicitly instead of relying on a default.
2. The exact signature-algorithm allowlist in the deployed client-credential form (RS256 is our requirement; the UI's list is unconfirmed).
3. Whether Keycloak advertises `token_endpoint_auth_method=private_key_jwt` for these client types in its discovery document — to be read from the deployed document.
4. Not-before revocation semantics against already-issued tokens, and how quickly the API-side registry and the Gateway JWKS cache converge (Gateway may serve a cached key for up to two hours; token `exp`/`nbf` checks are per request, but a *removed* key propagates slowly).
5. ALB → Keycloak proxy-header trust when the ALB's private addresses are not stable; `proxy-trusted-addresses` may degrade to security-group isolation.
6. Availability of SSM port forwarding (or an equivalent operator path) for admin access in the target account.
7. Aurora Serverless v2 minimum-ACU billing behaviour and Fargate sizing for this workload — no price or sizing is asserted.
8. Whether `--import-realm` boot-time import is available and how realm upgrades are applied on image change.
9. Keycloak version pin and upgrade-guide applicability for the chosen release.
10. Whether the presenter-local relay's operating system can protect a private key file adequately; the residual is stated regardless.
11. Lambda resource-policy limits and the exact `SourceArn`/`SourceAccount` form for the admission function.
12. That the internal invoke path cannot be reached by any internet client — asserted by design, to be proven by deployed-route inventory.

## 13. Irreducible decisions for the owner

Only three remain, and all three are consequences of selecting a self-managed identity server — none reopens D-A, D-D or CA-1.

| ID | Decision | Options | Recommendation | Why it is the owner's |
| --- | --- | --- | --- |
| **K-1** | Availability level of Keycloak | (a) one Fargate task, no HA — an outage blocks new authentication until restart; (b) two tasks + shared DB + load balancing — more cost and more moving parts | **(a)** for a demonstrator, with the outage consequence accepted explicitly | It is a cost-versus-availability trade the owner pays for and lives with |
| **K-2** | Who operates Keycloak | (a) product owner accountable, DevOps-authored runbook, no on-call; (b) named on-call/SLA commitment | **(a)**, with patching and restore rehearsals scheduled | Staffing and SLA are human commitments, not design outputs |
| **K-3** | Accept the presenter-local relay private key | (a) accept, with narrow scopes and rotation after each demo window; (b) move the relay into AWS (changes the presenter-local exposure that D3 selected) | **(a)** | Accepting residual risk on a credential outside AWS control belongs to the owner |

Minor operational parameters (backup retention window, maintenance window, DB sizing) are implementation parameters with recommendations in §2.8, not owner decisions; they do not change scope or cost category materially.

## 14. What rev2 does not do

No edit to `design.md`, `configuration-schema.md`, the ADR, requirements, contracts, WORK_ITEM, reviews, plan/status files or code. No Terraform, SAM, realm JSON or test authored. No deployment, provisioning, cloud call, credential flow, login, probe, pipeline run or publication. No finding closed, no risk accepted on the owner's behalf, no self-approval, no onward delegation. Every quantitative figure is either quoted from Implementation's measurement, recomputed from declared bounds, or explicitly marked as a proposal.
