# Operation A — additive contract delta for access revision 4

Status: **DRAFT / NOT NORMATIVE**. English documentary authoring only. No approved source, historical record, vector, design, review, Coordinator status or implementation file is changed. This delta must be read alongside the pinned originals; replacement text is a candidate, not an edit applied to them.

## 1. Scope and authority

Operation A acquiring simulator only; synthetic CP data, no B/C, real payment network, new inquiry/reversal, automatic financial recovery or multiuser administration. New public reachability does not change idempotency, prior-reference-first checks, at-most-one original financial invocation, atomic business/critical-event persistence, uncertainty/confirmation precedence, protected issuer truth or no-resend identities. Existing 30s default under AUTH R20/A07 remains; this task does not reopen it.

Security [rev4 review](../../reviews/access-design-rev4-20261122/security-review.md) SHA-256 c418f93a70fc41fc282ba5c2ea5c28714d14337f02c7d58ee4ecb97c8d123ed2 grants **PASS with conditions for design-delta authoring only**. It is not implementation, deployment, credential, acceptance or finding-closure authority. [Design rev4](../access-realization/design-rev4.md) SHA-256 3926423e9f42f6949647e5904271b6f6818b977301b66e81ffcf334f10c5dc56 remains a proposal; its numerical/configuration details are not owner-approved merely because the design gate passed. ADV-1/2/6 authorization details and ADV-8 additions are included below. SR2-01/02 OPEN, SR2-03/04 OPEN/ADVISORY, NV-01..04 PARTIAL, NV-05/AD-01 OPEN remain unchanged.

Owner authority: [owner-decisions.md](../../owner-decisions.md) SHA-256 c73ed0aad9dd6ba563755e3b4242f2b25563381fd3a67260608cb8d767429bd6: D-A self-managed Keycloak (92–99), D-D non-mTLS notification socket controls (86), CA-1 internal-only late evidence (103–107), K-1 one task/900s expiry (119), K-2 disposable/no maintenance promise (127), K-3 no relay/cloud presentation (129–136), P-1 public login-gated/rate-limited reachability (140). These are recorded decisions, not analyst risk acceptances. The historical line142 pending narrative is preserved as history, not current pending authority. Exact owner quotations and per-clause hashes are in the [machine-readable index](evidence/clause-index.json).

Classifications count delta IDs, not bullets/paragraphs: **2 SOURCED CLARIFICATION; 10 PROPOSED NORMATIVE CHANGE; 1 OPEN CONFLICT; total13**. D-1..D-9 map one-to-one to rev4 §8; A-1/A-2 are the two ADV-8 additions; R-1/R-2 explicitly carry pending retention applicability and lease/cursor staleness. Overlapping rows share replacement clauses, not competing definitions; D-5 is the authoritative proposed socket candidate, D-6 its scope, R-2 its lifecycle. All13 require independent acquiring-domain disposition as one exact-hash integrated bundle before normative incorporation; **no row is promoted by this author**. Sourced rows restate existing authority only; proposed detail remains proposed; D-5 remains an open design-table/issuance conflict.

## 2. Clause deltas

Each source citation below includes its SHA-256 and line span. Full exact original text is preserved in clause-index.json; design documents and the retention draft are explicitly proposals, not disguised approved clauses. The “today” text is a faithful summary, not a quotation substituted for the original. Line numbers bind to those hashes.

### D-1 — Browser-direct topology; O4/O5/C6 relay removal

**PROPOSED NORMATIVE CHANGE — DRAFT.**

**Exact affected files/clauses:**

- observation-contract.md, lines 35–39; SHA-256 ca53e384a46d24b3c1119b3e6ba804a9a9d4caac049a6b0c55a40c72842a205a.
- observation-contract.md, lines 42–43; SHA-256 ca53e384a46d24b3c1119b3e6ba804a9a9d4caac049a6b0c55a40c72842a205a.
- channel-contract.md, lines 51–53; SHA-256 495e1172e76ab9ba81f8a8026e7efec25baa6e4fff0d754c0a4c92bd38ea3bca.

**Today:** Loopback HTML/relay and screen-share, server-only JWT, IAM-signed $connect plus X-Demo-JWT, service-bound technical connection, 30-minute heartbeat-refreshed lease and a pending public exposure decision.

**Owner-approved direction / authority:** D-A (owner-decisions.md lines 90–99); D-D (owner-decisions.md lines 82–86); K-3 (owner-decisions.md lines 129–136); P-1 (owner-decisions.md lines 138–142). Owner file hash is pinned in section1 and in every index entry.

**What the recorded direction now requires:** K-3/P-1 require relay-free cloud/public presentation and direct single-demo-user authentication; D-A preserves Keycloak and service-hop identities. The exact protocol replacement is proposed, not fully owner-selected.

**Minimal replacement/addition (candidate):**

The presentation shall be a publicly reachable browser SPA and console API, with no presenter-local relay, loopback listener, local session, relay principal, client certificate or service assertion key in the browser. Anonymous reachability is limited to the static application and issuer login/protocol surface; console operations require a valid single-demo-subject access token and current authorized synthetic context. The browser console hop uses TLS and the demo token only. Service identity is retained exclusively on console-to-ACL and ACL-to-processor network hops, using mTLS plus OAuth private_key_jwt-derived access tokens, not SigV4 substitution. AWS workload IAM, callback management and internal invokes remain separate. Browser WebSocket access follows D-5; subject/environment binding follows D-6; token lifecycle follows D-2; controls follow D-7. No evidence admission is exposed to browser or public console. Failure to authenticate never falls back to anonymous access.

**Disposition before normative use:** No new owner choice for relay removal/public reachability. Replacement detail remains proposed; domain review and Security conditions required before normative incorporation. **Independent acquiring-domain review: REQUIRED for this exact delta bundle.**

**Given/When/Then obligation (not executed):** Given no presenter software, when the SPA calls the console API without a demo token, then protected data/payment access is denied. Given valid authorized HTTP and unavailable WS, an explicit payment remains possible; no automatic payment POST follows transport recovery.

### D-2 — C1/C6 session and token custody; OIDC hardening

**PROPOSED NORMATIVE CHANGE — DRAFT.**

**Exact affected files/clauses:**

- channel-contract.md, lines 5–5; SHA-256 495e1172e76ab9ba81f8a8026e7efec25baa6e4fff0d754c0a4c92bd38ea3bca.
- channel-contract.md, lines 51–52; SHA-256 495e1172e76ab9ba81f8a8026e7efec25baa6e4fff0d754c0a4c92bd38ea3bca.
- observation-contract.md, lines 36–36; SHA-256 ca53e384a46d24b3c1119b3e6ba804a9a9d4caac049a6b0c55a40c72842a205a.

**Today:** Local relay owns short-lived JWT; browser receives an opaque HttpOnly SameSite=Strict session and no JWT/refresh token. Service IAM and demo authority are described as adjacent browser-hop credentials.

**Owner-approved direction / authority:** D-A (owner-decisions.md lines 90–99); K-3 (owner-decisions.md lines 129–136); P-1 (owner-decisions.md lines 138–142). Owner file hash is pinned in section1 and in every index entry.

**What the recorded direction now requires:** K-3 removes the component that could hold tokens server-side; P-1 requires the demo login gate and no browser service secret. Public-client storage/refresh/flow controls are proposed realization, not new owner decisions.

**Minimal replacement/addition (candidate):**

The static SPA shall use the public OIDC client console-demo at self-managed Keycloak with authorization code + PKCE (S256), exact registered HTTPS callback, single-use request state bound to the initiating browser flow and checked on return, and nonce checked against the authenticated issuer response. Reject absent/mismatched/reused state, nonce or authorization code; issuer code-reuse detection and the pinned client flow must be verified. No client secret exists for this SPA. Access tokens shall exist only in browser runtime memory, never localStorage, sessionStorage, cookie, persistent HTML, URL/query/fragment, log, event frame or error body; remove the authorization code from the address bar after exchange. No relay session or cookie authenticates the console API: each request explicitly carries the intended access token in Authorization. An issuer SSO cookie is confined to the issuer and is not a console credential.
If refresh is enabled, keep refresh tokens only in runtime memory; require rotation with reuse/theft detection, issuer-side revocation on logout and absolute session expiry, plus client state clearing. Restrict refresh/offline grants and scope/audience expansion; a refresh or ID token is never accepted as an API/WS access token. Eight hours is only the rev4 proposed absolute cap, not an approved requirement. Re-login with no refresh token is the bounded alternative. On expiry/failed refresh stop API issuance, clear sensitive/session state and close WS; renewal makes a new authenticated epoch and retained-source catch-up, never an automatic payment or resumed send. Ordinary token renewal does not extend retained history.

**Disposition before normative use:** Owner already selected Keycloak and no relay. PKCE/state/nonce, refresh behavior and exact cap are proposed realization controls; domain review and independent Security/provider validation remain required. Refresh-versus-relogin is a team implementation choice within the reviewed constraints, not a new owner question. **Independent acquiring-domain review: REQUIRED for this exact delta bundle.**

**Given/When/Then obligation (not executed):** Given a forged callback or reused authorization code, when login completes, then no session is established. Given a stolen rotated refresh token, when reused after access-token expiry, then detect reuse and deny/terminate according to the reviewed issuer policy. Logout must revoke at issuer, not only clear UI.

**Citation/qualification:** ADV-1/ADV-6 are included here and cross-referenced by D-7. The 900s access-token ceiling follows the rev4 design, not a new verified Keycloak setting.

### D-3 — Historical 2026-11-19 screen-share authority overlay

**SOURCED CLARIFICATION — DRAFT.**

**Exact affected files/clauses:**

- owner-decisions.md, lines 46–50; SHA-256 c73ed0aad9dd6ba563755e3b4242f2b25563381fd3a67260608cb8d767429bd6.
- owner-decisions.md, lines 56–60; SHA-256 c73ed0aad9dd6ba563755e3b4242f2b25563381fd3a67260608cb8d767429bd6.
- owner-decisions.md, lines 123–142; SHA-256 c73ed0aad9dd6ba563755e3b4242f2b25563381fd3a67260608cb8d767429bd6.

**Today:** The actual 2026-11-19 owner record says presenter screen-share, no direct client access or remote-public hosting commissioned; later K-3/P-1 reverse that presentation boundary. Earlier P-1-pending text is explicitly historical.

**Owner-approved direction / authority:** K-3 (owner-decisions.md lines 129–136); P-1 (owner-decisions.md lines 138–142). Owner file hash is pinned in section1 and in every index entry.

**What the recorded direction now requires:** K-3 explicitly reverses the prior local-screen-share boundary; P-1 selects public reachability with login plus attempt limits. This is a historical precedence clarification, not new exposure approval.

**Minimal replacement/addition (candidate):**

For presentation exposure only, the 2026-11-19 presenter-local/screen-share/no-direct-client/no-public-hosting restrictions are superseded by K-3 and P-1 on 2026-11-22. Presentation is cloud-hosted and publicly reachable, with the single demo-user login and attempt rate limiting; local execution is for tests and their database, not a required presentation relay. This is design authority only, not deployment, credentials, publication or deletion authorization. Other approved payment, evidence, synthetic-context and retention guarantees remain unchanged. Preserve the original dated owner record; attach this precedence overlay rather than rewriting its historical text.

**Disposition before normative use:** No owner reconfirmation required. Coordinator owns recording/linking supersession; integrated domain review still required before changed behavioral contracts become normative. **Independent acquiring-domain review: REQUIRED for this exact delta bundle.**

**Given/When/Then obligation (not executed):** Given the old screen-share paragraph and later owner decisions, when interpreting reachability, then apply K-3/P-1 without pretending the old paragraph never existed.

**Citation/qualification:** Only the dated 2026-11-19 disposition is affected; no broad revocation of business or evidence constraints.

### D-4 — R19/R25 browser identity and live/read topology

**PROPOSED NORMATIVE CHANGE — DRAFT.**

**Exact affected files/clauses:**

- requirements.md, lines 55–55; SHA-256 49c5d412e3705c320a2f78b190555f49e7e0fa4662cff9329d259f89e2b459df.
- requirements.md, lines 61–61; SHA-256 49c5d412e3705c320a2f78b190555f49e7e0fa4662cff9329d259f89e2b459df.

**Today:** R19 assumes a fixed loopback relay and service-scoped browser binding; R25 names a single principal with WS/catch-up but leaves its exposure realization vague.

**Owner-approved direction / authority:** D-D (owner-decisions.md lines 82–86); K-3 (owner-decisions.md lines 129–136); P-1 (owner-decisions.md lines 138–142). Owner file hash is pinned in section1 and in every index entry.

**What the recorded direction now requires:** Keep one demo principal and current synthetic mandate while replacing the removed relay with public browser-direct read/live/submission; retain service authorization downstream and unchanged financial guarantees.

**Minimal replacement/addition (candidate):**

R19 replacement: Use one authenticated demo subject, without multi-user administration, on the public browser-to-console path. Derive synthetic context/environment authorization from trusted subject/registry data, not caller acquirer fields. No loopback relay or browser-held service credential is involved. Bind queries, subscriptions and cursors to the current demo subject, deployment environment, authorized business context, transaction and generation. Do not expose issuer-only truth.
R25 replacement: Retain one queryable functional event store, asynchronous Streams/notifier notifications and browser-direct authenticated WebSocket with same-source HTTP initial/history/reconnect catch-up. Subscribe/ACK before fixed-snapshot catch-up and existing event/sequence dedupe remain. No every-second polling, automatic financial retry, duplicate history projection or WS prerequisite for authorized explicit HTTP payment is introduced. Live loss is an observation condition, not a changed journal outcome. Apply D-5/D-6/R-2 for authentication and cursor lifecycle; seven-day history approval is distinct from unapproved business quotas or SLO values.

**Disposition before normative use:** Owner direction is already decided. New consolidated rule wording needs independent domain review; no analyst reclassification of the sealed R19/R25 rows or approval of numerical proposals. **Independent acquiring-domain review: REQUIRED for this exact delta bundle.**

**Given/When/Then obligation (not executed):** Given WS failure with valid current HTTP authorization and persistence, when user explicitly submits Operation A, then at most one original financial send follows existing claim rules. Given wrong context, then no data disclosure or dispatch.

### D-5 — Single-credential WS; unresolved service-client/audience contradiction

**OPEN CONFLICT — DRAFT.**

**Exact affected files/clauses:**

- observation-contract.md, lines 38–38; SHA-256 ca53e384a46d24b3c1119b3e6ba804a9a9d4caac049a6b0c55a40c72842a205a.
- observation-contract.md, lines 42–43; SHA-256 ca53e384a46d24b3c1119b3e6ba804a9a9d4caac049a6b0c55a40c72842a205a.
- channel-contract.md, lines 52–52; SHA-256 495e1172e76ab9ba81f8a8026e7efec25baa6e4fff0d754c0a4c92bd38ea3bca.
- design/access-realization/design-rev4.md, lines 154–172; SHA-256 3926423e9f42f6949647e5904271b6f6818b977301b66e81ffcf334f10c5dc56.
- design/access-realization/design-rev4.md, lines 258–263; SHA-256 3926423e9f42f6949647e5904271b6f6818b977301b66e81ffcf334f10c5dc56.

**Today:** O5/C6 require service identity plus demo token; rev4 S6 requires only the demo token but rev4 client table still offers svc-console-ws with ws.observe at the WS audience. The listed console-demo client has console.demo at console API, not an explicit WS token-minting contract.

**Owner-approved direction / authority:** D-D (owner-decisions.md lines 82–86); D-A (owner-decisions.md lines 90–99); K-3 (owner-decisions.md lines 129–136); P-1 (owner-decisions.md lines 138–142). Owner file hash is pinned in section1 and in every index entry.

**What the recorded direction now requires:** D-D accepts non-mTLS observation transport with validation/revocation; K-3 prohibits a browser service key and P-1 fixes public reachability. Neither D-D alone nor P-1 resolves the residual svc-console-ws class; single-credential realization is conditionally acceptable under the pinned Security review, not approved or implemented here.

**Minimal replacement/addition (candidate):**

Candidate pending DevOps/domain/Security reconciliation: The browser shall open WSS with one narrowly scoped demo access token carried only in Sec-WebSocket-Protocol, never X-Demo-JWT, Authorization custom header, URL or subsequent WS messages. The authorizer shall verify issuer/signature/expiry, WS audience, ws.observe, the configured single demo subject, and the allowed public client/authorized-party token class, then exact-match Origin to the deployed HTTPS console origin. Origin is a browser-context check, not an authentication factor or an owner-selected network reachability allowlist. Echo only a fixed accepted protocol identifier, never the bearer-token value, in protocol negotiation. Exclude handshake token/header from every access/execution/WAF/application/failure log and error path; prove this on the chosen stack.
Establish a server-side record binding subject, epoch, expiry, registry status/version, environment and allowed business context. Revalidate on every incoming frame and before query/notification disclosure; an independent idle scheduler enforces revocation even if client sends nothing. Close on expiry or invalid/revoked binding no later than next frame/heartbeat (<=30s target); registry failure fails closed, never assumes cached permission indefinitely. Effective lease is jwtExp, with a <=900s accepted token window; heartbeat, subscription activity and reauthentication never extend the old epoch. No service signing, client certificate or second credential is held by the browser; ws.observe cannot authorize a payment or evidence submission.
Restrictive proposed resolution of ADV-2: reject every service-principal token, including svc-console-ws, at the browser $connect path. Retire that unused client or remove its WS-audience/ws.observe grant; do not enable an undocumented second accepted token class. Specify how console-demo obtains a distinct WS-audience/ws.observe demo token with its permitted public-client flow, rather than weakening the audience check to accept a console API token. No new token-exchange/broker endpoint is authorized by this wording.

**Disposition before normative use:** OPEN: DevOps must reconcile/retire the latent client, subject/client allowlist and actual demo WS audience/scope issuance. Independent domain review and Security confirmation required. No new owner question now: Security judged the single-credential design conditionally acceptable, not built. Escalate to owner only if required controls cannot hold and a broker/HTTP-only change is genuinely necessary. **Independent acquiring-domain review: REQUIRED for this exact delta bundle.**

**Given/When/Then obligation (not executed):** Given valid-signature svc-console-ws token with WS aud/scope but a service subject, when $connect occurs, then reject rather than count it as a demo credential. Given right subject but wrong audience/client, wrong Origin, expired or revoked token, deny/close; silent-client and registry-failure cases must satisfy the bound. Capture must show no bearer echo/log.

**Citation/qualification:** ADV-2 remains explicitly open, not silently resolved by this candidate. D-D accepted no-mTLS transport but did not itself approve reducing identity count. SR2/NV-03 conditions remain. Ticket fallback requires its own reviewed single-use/expiry/binding endpoint delta; no silent automatic fallback or cloud relay is authorized.

### D-6 — Subject/environment binding replaces relay service scope

**PROPOSED NORMATIVE CHANGE — DRAFT.**

**Exact affected files/clauses:**

- observation-contract.md, lines 42–45; SHA-256 ca53e384a46d24b3c1119b3e6ba804a9a9d4caac049a6b0c55a40c72842a205a.
- observation-contract.md, lines 59–60; SHA-256 ca53e384a46d24b3c1119b3e6ba804a9a9d4caac049a6b0c55a40c72842a205a.
- channel-contract.md, lines 57–57; SHA-256 495e1172e76ab9ba81f8a8026e7efec25baa6e4fff0d754c0a4c92bd38ea3bca.

**Today:** O5/O5a bind technical socket state and O6/C7 cursors to demo subject AND demo service/environment scope. Browser no longer has that service credential; O7 is not the location of this wording.

**Owner-approved direction / authority:** D-D (owner-decisions.md lines 82–86); K-3 (owner-decisions.md lines 129–136); P-1 (owner-decisions.md lines 138–142). Owner file hash is pinned in section1 and in every index entry.

**What the recorded direction now requires:** K-3 eliminates the relay service identity from browser authentication. The existing demo subject/current context must still authorize all reads/subscriptions; only downstream service hops retain a service identity.

**Minimal replacement/addition (candidate):**

For browser connections, query authorization, subscriptions, notifications and cursors, replace relay SERVICE binding with the validated single demo subject plus trusted deployment-environment and current registry mandate/version. The subject is not an acquirer; body fields cannot select identity or widen authorization. On every use verify environment/context/transaction permission and the current epoch/generation. Keep immutable request tuple, K=(trusted connectionId,connectionEpoch,requestId), ACK replay, retired-request tombstones and wrong-generation rejection. Cursor signatures are integrity, not independent access authority. Console/ACL service-hop identities are separate downstream controls, not an additional browser credential. Notifier's AWS IAM identity remains technical callback authority, never user authority.

**Disposition before normative use:** No new owner choice; domain review of auth/reference/generation semantics and Security binding review required. **Independent acquiring-domain review: REQUIRED for this exact delta bundle.**

**Given/When/Then obligation (not executed):** Given a signed cursor copied to another subject/environment or new epoch, when query occurs, then deny scope or require resync without disclosing original data; it cannot start a payment.

**Citation/qualification:** Corrects rev4 D-6 attribution from O5/O7 to O5/O6; preserves O7 observation-only retry/replay.

### D-7 — Public edge, CORS/CSP, abuse and OIDC controls

**PROPOSED NORMATIVE CHANGE — DRAFT.**

**Exact affected files/clauses:**

- observation-contract.md, lines 37–37; SHA-256 ca53e384a46d24b3c1119b3e6ba804a9a9d4caac049a6b0c55a40c72842a205a.
- channel-contract.md, lines 51–53; SHA-256 495e1172e76ab9ba81f8a8026e7efec25baa6e4fff0d754c0a4c92bd38ea3bca.
- requirements.md, lines 27–27; SHA-256 49c5d412e3705c320a2f78b190555f49e7e0fa4662cff9329d259f89e2b459df.
- design/access-realization/design-rev4.md, lines 85–143; SHA-256 3926423e9f42f6949647e5904271b6f6818b977301b66e81ffcf334f10c5dc56.

**Today:** The old contracts have local Host/Origin and no-wildcard-CORS text, not a public edge/rate/CSP requirement. Rev4 proposes concrete controls; none is observed deployed.

**Owner-approved direction / authority:** D-A (owner-decisions.md lines 90–99); K-3 (owner-decisions.md lines 129–136); P-1 (owner-decisions.md lines 138–142). Owner file hash is pinned in section1 and in every index entry.

**What the recorded direction now requires:** P-1 requires publicly reachable console gated by demo login and attempt rate limiting. Specific rate values, WAF topology, headers and OIDC hardening are proposed controls requiring review, not owner-approved numbers.

**Minimal replacement/addition (candidate):**

Proposed addition: Serve the public static SPA from private S3 through CloudFront OAC; protect authenticated console operations behind JWT verification and current mandate. Keep SPA/API same HTTPS origin with no API Access-Control-Allow-* response allowance, no ambient API cookies and explicit Authorization on each operation. No-CORS does not prevent forged/simple cross-origin requests from being sent; reject them independently without valid token/content type/context and disclose no protected response. WSS uses separate exact Origin verification under D-5, not CORS. Issuer protocol endpoints have only the precisely configured public-client origin support required for the OIDC flow, no wildcard credentialed CORS.
Deliver proposed CSP on normal and error pages: default-src self; script-src self with no unsafe-inline/eval or third-party runtime scripts; connect-src self plus exact HTTPS issuer and WSS socket destinations; frame-ancestors none; base-uri none; form-action self plus issuer; img-src self/data as needed. Use HSTS, nosniff and no-referrer; validate the final CSP syntax/schemes against the actual deployment. Nothing in rev4 prose establishes these headers exist. Bundle dependencies locally and forbid service keys, static tokens, client secrets or credentials in the SPA artifact.
Rate limits and brute-force controls must exist at the edge/issuer and authorizer, not only in cooperative browser code: proposed login/token20 requests/5min/IP plus issuer temporary brute-force lockout; API100/5min/IP with route burst10/rate5rps; WS<=6 connect attempts/min/subject after identity is established and pre-auth source/IP/concurrency defense; <=10 subscribe/unsubscribe/min/connection; heartbeat no faster than30s; bounded fanout; reserved authorizer/integration concurrency isolated from payment work. Unknown/oversized frames are safely rejected without logging credentials. Return safe throttling/refusal behavior with jittered reconnect1/2/5/10/30s; no automatic financial retry. Monitor route429/4xx/5xx, login failures, issuance latency and refusals. These are proposed technical abuse limits, not business volume/budget gates or owner-approved numeric quotas. No DDoS immunity, fair-user guarantee under shared-IP or distributed-abuse immunity is promised.
Apply mandatory proposed OIDC state/nonce, PKCE/code replay checks and refresh reuse/revocation controls in D-2 (ADV-1/ADV-6). Verify Keycloak release behavior rather than assume it. Bot-control cost and any broader dependency are not selected by this text.

**Disposition before normative use:** Owner selected login plus attempt rate limiting, not these exact numbers or implemented CSP/WAF. Domain review and DevOps/Security feasibility/negative evidence required; tune technical values through reviewed realization, no new business approval gate. **Independent acquiring-domain review: REQUIRED for this exact delta bundle.**

**Given/When/Then obligation (not executed):** Anonymous can fetch static/login pages but receives no protected data. Cross-origin preflight receives no API CORS allowance; text/plain forged POST has no valid bearer and is rejected. Verify error-page CSP, no secret in bundle/logs, distributed/shared-IP and expired-token negatives before build sign-off.

**Citation/qualification:** ADV-1/3/6/7 included. Concrete numeric limits remain proposals. No exact AWS region/attachment/CloudFront certificate fact is newly endorsed: feasibility and the existing us-east-2 infrastructure exception questions remain DevOps work, not a silent copy of rev4 configuration claims.

### D-8 — Console-write scope lineage and console-to-ACL identity

**PROPOSED NORMATIVE CHANGE — DRAFT.**

**Exact affected files/clauses:**

- channel-contract.md, lines 5–5; SHA-256 495e1172e76ab9ba81f8a8026e7efec25baa6e4fff0d754c0a4c92bd38ea3bca.
- observation-contract.md, lines 35–38; SHA-256 ca53e384a46d24b3c1119b3e6ba804a9a9d4caac049a6b0c55a40c72842a205a.
- channel-contract.md, lines 51–52; SHA-256 495e1172e76ab9ba81f8a8026e7efec25baa6e4fff0d754c0a4c92bd38ea3bca.
- design/access-realization/design-rev3.md, lines 155–167; SHA-256 cdd65729ec04708e9bd96626d29980d616fa32261620825987c9eb2cc7d63d2a.
- design/access-realization/design-rev4.md, lines 219–224; SHA-256 3926423e9f42f6949647e5904271b6f6818b977301b66e81ffcf334f10c5dc56.
- owner-decisions.md, lines 129–136; SHA-256 c73ed0aad9dd6ba563755e3b4242f2b25563381fd3a67260608cb8d767429bd6.

**Today:** C1 says Service IAM remains separate but does not literally contain console.write. That name occurs in rev3 relay capability text and owner K-3. Rev4 re-scopes the renamed console identity to console.dispatch at ACL; svc-console-ws still ambiguous under D-5.

**Owner-approved direction / authority:** D-A (owner-decisions.md lines 90–99); K-3 (owner-decisions.md lines 129–136); P-1 (owner-decisions.md lines 138–142); CA-1 (owner-decisions.md lines 101–107). Owner file hash is pinned in section1 and in every index entry.

**What the recorded direction now requires:** K-3 removes or re-scopes svc-console-relay and its console.write consumer; D-A keeps separate mTLS/OAuth service-hop authority. CA-1 keeps evidence admission internal. Scope spelling/migration is a proposed design decision, not an automatic rename.

**Minimal replacement/addition (candidate):**

Replace the relay capability only, not payment semantics: browser-to-console requires the demo access token (console audience, console.demo and current single-subject mandate), never console.write service authority. Remove svc-console-relay and its consumer-specific console.write/console.read grant from the new realization; no browser may inherit it. On console-to-ACL use svc-console mTLS plus OAuth service access token, ACL audience and the rev4 proposed console.dispatch scope; pin certificate identity to OAuth client and current server-side authorized synthetic contexts. A legacy console.write token is not silently aliased into the new scope/audience. ACL-to-processor uses separate svc-acl at simulator audience with sim.iso87a.authorize and mTLS. Reject demo/WS/ID/refresh tokens at service hops. A valid svc-console call can initiate an authorized synthetic payment through normal claim/dispatch; it is not financial-result, issuer-decision or C4-signing authority. Keep demo subject/context provenance across the server-side hop through a reviewed trusted binding; do not accept arbitrary identity headers. Internal evidence admission remains CA-1-only; deny console Lambda invoking it under carried SR2-02.

**Disposition before normative use:** No owner reconfirmation for removing the relay principal. console.dispatch is proposed realization, not an owner-approved spelling; domain and Security review required for scope migration, certificate/client binding and end-to-end demo-mandate propagation. Existing service authorization cannot be bypassed by the rename. **Independent acquiring-domain review: REQUIRED for this exact delta bundle.**

**Given/When/Then obligation (not executed):** Given a captured console.demo, ws.observe or old console.write token, when replayed at ACL, reject wrong audience/client/scope; no dispatch. Given a properly bound svc-console request with authorized explicit demo action, use normal idempotency/possible-send guarantees.

**Citation/qualification:** Corrects D-8 citation; the client-set change is a rename/re-scope, not a reduction from3 to3 (ADV-4). Actual grant removal is not executed by this document.

### D-9 — Keycloak rebuild-not-repair supersession only

**PROPOSED NORMATIVE CHANGE — DRAFT.**

**Exact affected files/clauses:**

- design/access-realization/design-rev2.md, lines 133–141; SHA-256 6b15161875e702ebae74e4f76b4b94cae9ffbbb94d6a9fcd42aee563b8f3873c.
- design/access-realization/design-rev3.md, lines 217–219; SHA-256 cdd65729ec04708e9bd96626d29980d616fa32261620825987c9eb2cc7d63d2a.
- design/access-realization/design-rev3.md, lines 137–151; SHA-256 cdd65729ec04708e9bd96626d29980d616fa32261620825987c9eb2cc7d63d2a.
- owner-decisions.md, lines 117–129; SHA-256 c73ed0aad9dd6ba563755e3b4242f2b25563381fd3a67260608cb8d767429bd6.

**Today:** Rev2 §2.8 proposes PITR/restore rehearsal/monthly patch and rev3 §7 re-carries operations ownership. Rev3 §3 is rotation minima, not a backup clause; it is NOT superseded.

**Owner-approved direction / authority:** D-A (owner-decisions.md lines 90–99); K-1 (owner-decisions.md lines 117–121); K-2 (owner-decisions.md lines 123–127); K-3 (owner-decisions.md lines 129–136). Owner file hash is pinned in section1 and in every index entry.

**What the recorded direction now requires:** K-1 accepts one task and bounded existing-token availability; K-2 chooses recreation from configuration rather than promised backup recovery or scheduled maintenance; K-3 removes only the presenter key class. Other key/financial safeguards survive.

**Minimal replacement/addition (candidate):**

For the disposable Keycloak unit only, replace backup/PITR recovery promises, restore rehearsal, in-place schema-migration commitment and scheduled patching obligation with recreation from pinned image/configuration, fresh realm database and import/public-key registrations. No HA, on-call, SLA or fixed vulnerability-remediation window is promised. Platform-default backups, if present, are not the designated recovery path. Realm sessions/offline tokens can be lost; simulator journal, evidence, event source, no-resend references and authorization registries outside the disposable unit shall not be destroyed. Existing authorized access tokens last no longer than their expiry and current revocation checks; new issuance fails during rebuild and no authentication bypass is allowed.
Configuration and secret custody must be reproducible without embedding private material in Terraform state/plans, images, task definitions, logs, bundle or CI artifacts. Rebuild/import/token checks are future verification obligations, not actions authorized here. K-2 does not waive rev3 §3 planned rotation overlap minima or emergency revocation; apply surviving service/assertion/TLS/issuer/C4 classes after removing the presenter key. Preserve SR2-01/02 effective invocation restrictions and SR2-03/04 findings. No deletion, backup-policy mutation, infrastructure recreation or credential flow is authorized by this contract draft.

**Disposition before normative use:** Owner already accepted disposable single-task/no-maintenance posture. Domain review required to protect financial durability; DevOps/Security confirm secret/rotation/rebuild details. No current owner question or destructive execution approval is implied. **Independent acquiring-domain review: REQUIRED for this exact delta bundle.**

**Given/When/Then obligation (not executed):** Given a Keycloak rebuild, when realm state is lost, then no simulator evidence/history/no-resend data is removed and no login bypass occurs. Existing tokens remain bounded by expiry/registry; restore/rebuild success is unverified until separately authorized evidence exists.

**Citation/qualification:** D-9 targets rev2 §2.8 and rev3 §7, NOT rev3 §3. Review ADV-8 citation fix and ADV-5 carried retest prerequisites remain visible.

### A-1 — ADV-8 requirements preconditions and narrow-demo paragraphs

**PROPOSED NORMATIVE CHANGE — DRAFT.**

**Exact affected files/clauses:**

- requirements.md, lines 25–29; SHA-256 49c5d412e3705c320a2f78b190555f49e7e0fa4662cff9329d259f89e2b459df.
- requirements.md, lines 76–78; SHA-256 49c5d412e3705c320a2f78b190555f49e7e0fa4662cff9329d259f89e2b459df.

**Today:** Outside R19/R25, preconditions preserve loopback JWT/IAM and reject direct public anonymous access; narrow-demo text forbids anonymous public exposure/browser credential and still asks a local-vs-public owner choice.

**Owner-approved direction / authority:** D-A (owner-decisions.md lines 90–99); D-D (owner-decisions.md lines 82–86); K-3 (owner-decisions.md lines 129–136); P-1 (owner-decisions.md lines 138–142). Owner file hash is pinned in section1 and in every index entry.

**What the recorded direction now requires:** K-3/P-1 supersede non-rule-row loopback/no-public-exposure assumptions; publicly fetching static/login pages is allowed, anonymous protected operations are not. Single demo credential plus current mandate remains the gate.

**Minimal replacement/addition (candidate):**

Replace the exposure/session sentences in preconditions and the narrow-demo section: The presentation is a cloud-hosted publicly reachable SPA/login surface; no presenter relay or loopback dependency exists. Anonymous static application fetch and issuer login/protocol reachability are allowed. Anonymous payment submission, console history/detail/query, subscription, notification access, evidence admission and issuer-truth disclosure are not allowed. Console APIs require the validated single-demo-subject token and current authorized synthetic mandate; service hops have their own identities under D-8. No client certificate or service secret is given to the browser; its short-lived demo token is memory-only under D-2. No multiuser administration or per-person privacy promise is added. Public reachability and no relay are already decided under K-3/P-1; remove the pending local-boundary question, not the independent review, issuer-compatibility and runtime gates. Deployment remains unauthorized.

**Disposition before normative use:** No owner question; explicit independent acquiring-domain review required for these non-rule-row changes alongside D-4. Existing business fixture registration, channel validation and no unauthorized issuer-truth access remain prerequisites. **Independent acquiring-domain review: REQUIRED for this exact delta bundle.**

**Given/When/Then obligation (not executed):** Given no login, when fetching static/login, then public reachability is expected; when requesting history or posting payment, then401/403 with no protected disclosure/send. Given valid demo credential, authorized synthetic read/initiation is the accepted consequence, within mandate/rates.

### A-2 — ADV-8 acceptance W-suite and loopback oracle replacement

**PROPOSED NORMATIVE CHANGE — DRAFT.**

**Exact affected files/clauses:**

- acceptance-scenarios.md, lines 36–37; SHA-256 1586898418c09dcf092d567a4155b4524a0bc34500bf4ea76ce8f76465aa7733.
- acceptance-scenarios.md, lines 55–66; SHA-256 1586898418c09dcf092d567a4155b4524a0bc34500bf4ea76ce8f76465aa7733.
- vectors/websocket-vectors.json, lines 1–4; SHA-256 e6d30a83367233d9216c2ef162e82d7f67e1b6841de312a0e5dadcec3dace2a8.

**Today:** The W-suite header fixes loopback origin/serviceJWT failure; exit conditions carry a pending local-boundary question and screen-share-not-client-access disposition. W20/W23 and servicePrincipal-bearing fixtures still encode relay custody and local-Origin assumptions.

**Owner-approved direction / authority:** D-D (owner-decisions.md lines 82–86); K-3 (owner-decisions.md lines 129–136); P-1 (owner-decisions.md lines 138–142). Owner file hash is pinned in section1 and in every index entry.

**What the recorded direction now requires:** K-3/P-1 replace the local presentation boundary for scenario interpretation; D-D preserves observation-only transport and no financial authority. Current acceptance oracles must reflect that change through independent review.

**Minimal replacement/addition (candidate):**

Replace acceptance assumptions, not historical fixtures in place: W01–W33 shall be interpreted only through a newly reviewed browser-direct revision of the suite. Replace loopback-origin boundary with exact deployed HTTPS console-Origin for WS; replace relay token custody/opaque session with D-2 memory-only public-client flow; replace service/demo browser binding with D-5/D-6 single demo subject + epoch/environment/current mandate. Retain immutable subscription request identity/conflict/removal, source order/dedupe, fixed H versus ACK A, no pre-ACK rendering, gaps/truncation and zero automatic financial POST on transport flows.
Review W18 service-scope cursor, W19 issuer expiry, W20 relay secrecy, W23 loopback Origin, W25 token renewal, W26–W31 servicePrincipal-bearing binding, W32/W33 HTTP-versus-WS independence, and all related wire examples. Proposed new W20 oracle: browser has memory-only demo token, no service key/relay cookie/persistent token. Proposed W23 oracle: wrong/missing WS Origin is denied; correct Origin without token is denied; correct Origin plus authorized demo token may connect. Static/login without token is not a failure; protected query/payment without valid token must fail. Remove the obsolete loopback owner question and screen-share restriction from current exit conditions only through reviewed overlay. Preserve previous255 documentary cases as historical inventory; this document neither executes nor re-counts them as new coverage.

**Disposition before normative use:** No owner question; explicit independent domain review of the revised scenario/wire artifacts is required before they serve as normative oracles. This delta document does not revise the JSON or approved scenario file. **Independent acquiring-domain review: REQUIRED for this exact delta bundle.**

**Given/When/Then obligation (not executed):** Given current SPA versus hostile origin, compare authenticated WS/HTTP acceptance and denied protected operations; repeat W26–W31 conflict/ACK cases with only the new binding and unchanged no-send invariants.

**Citation/qualification:** JSON pointer evidence for the affected W cases is in clause-index.json; file SHA and exact cases are preserved. The lines1–4 reference is inventory/status only, not a false claim that those lines contain every W clause.

### R-1 — Retain pending record-specific seven-day expiry work

**SOURCED CLARIFICATION — DRAFT.**

**Exact affected files/clauses:**

- observation-contract.md, lines 74–79; SHA-256 ca53e384a46d24b3c1119b3e6ba804a9a9d4caac049a6b0c55a40c72842a205a.
- owner-decisions.md, lines 46–50; SHA-256 c73ed0aad9dd6ba563755e3b4242f2b25563381fd3a67260608cb8d767429bd6.
- design/retention/retention-delta.md, lines 1–5; SHA-256 d11ead64f0bd3928b4112b29c408eab1d3e0f9401b84a4580e3d4adb8b35fd76.
- adr-operation-a-access-and-environment.md, lines 11–21; SHA-256 d51bbf650aca74e82351dacf0e45d0f14c992044b9ff246bc7b8b281920b0b39.

**Today:** O8 contains proposed numeric quotas and expiry mechanics, but owner approved seven-day history and later one-week TTL where applicable. Corrected retention draft remains non-normative; business/evidence destruction is not seven-day TTL permission.

**Owner-approved direction / authority:** K-2 (owner-decisions.md lines 123–127); D-A (owner-decisions.md lines 90–99). Owner file hash is pinned in section1 and in every index entry.

D3 retention disposition 2026-11-19 line50; access ADR decision2 line11 and safeguards line21 (not authorization from K-2 or D-A).

**What the recorded direction now requires:** The separate owner seven-day history disposition (owner-decisions line50) and access ADR line21 require applicable logical expiry without deleting business/evidence/no-resend identities. K-2 disposability is limited to Keycloak and D-A selects the issuer; neither grants simulator-data destruction authority.

**Minimal replacement/addition (candidate):**

Carry forward the approved seven-day functional-history direction and the existing NV-05 retained-identity safeguards; do not treat this access delta or Keycloak disposability as a new retention approval. Apply payload logical expiry from original collection/critical commit, not read/retry/reconnect; physical TTL lags and must not erase required range/revision/dedupe metadata or business/evidence/no-resend identities. Business journal/POSSIBLE_SEND/result, receipt/evidence, original links, aliases, generation heads and issuer instruction identity survive whenever required. Technical connection/control/cursor/assertion-jti records follow their shorter actual validity, not a blanket week. Stream/TTL/repair must never resurrect expired payload or create send authority. Event tombstones alone are insufficient business evidence; compact equivalence and a final destructive horizon remain unproven/unapproved. O8 numeric business quotas are not revived. Keep every proposed precision/worker/compaction/replay-cache delta in the separate retention draft pending review; this is a preservation cross-reference, not promotion of those proposals.

**Disposition before normative use:** No current owner question for non-destructive history expiry. Independent domain re-review of corrected retention and this cross-surface overlay remains needed. A future requested material destructive loss/horizon requires owner trade-off after equivalence analysis. K-2 applies only to Keycloak, not simulator records. **Independent acquiring-domain review: REQUIRED for this exact delta bundle.**

**Given/When/Then obligation (not executed):** Given history payload expired but POSSIBLE_SEND remains, linked retry reads original current outcome/uncertainty without redispatch. Given late verified internal proof after expiry, admit it normally with its new event origin without restoring old history.

**Citation/qualification:** Primary retention authority is owner seven-day disposition line50 and access ADR line21, not D-A/K-2 themselves. Those decisions only constrain this cross-surface change; additional citations are attached below.

### R-2 — Cursor lifetime versus socket lease, mid-pass interruption

**PROPOSED NORMATIVE CHANGE — DRAFT.**

**Exact affected files/clauses:**

- observation-contract.md, lines 43–43; SHA-256 ca53e384a46d24b3c1119b3e6ba804a9a9d4caac049a6b0c55a40c72842a205a.
- observation-contract.md, lines 52–52; SHA-256 ca53e384a46d24b3c1119b3e6ba804a9a9d4caac049a6b0c55a40c72842a205a.
- observation-contract.md, lines 59–62; SHA-256 ca53e384a46d24b3c1119b3e6ba804a9a9d4caac049a6b0c55a40c72842a205a.
- channel-contract.md, lines 57–57; SHA-256 495e1172e76ab9ba81f8a8026e7efec25baa6e4fff0d754c0a4c92bd38ea3bca.
- owner-decisions.md, lines 86–86; SHA-256 c73ed0aad9dd6ba563755e3b4242f2b25563381fd3a67260608cb8d767429bd6.
- design/access-realization/design-rev3.md, lines 173–186; SHA-256 cdd65729ec04708e9bd96626d29980d616fa32261620825987c9eb2cc7d63d2a.

**Today:** O5 proposes a30min activity-refreshed technical lease, D-D names min(JWT expiry,30min), O6/C7 nominal15min cursor with old service binding. A cursor minted later can outlive the socket; no equality follows. Rev3 explicitly withdraws cursor/lease equality.

**Owner-approved direction / authority:** D-D (owner-decisions.md lines 82–86); K-3 (owner-decisions.md lines 129–136); P-1 (owner-decisions.md lines 138–142). Owner file hash is pinned in section1 and in every index entry.

**What the recorded direction now requires:** D-D requires token-bounded lease/revocation; K-3/P-1 require applying it to the direct browser rather than relay. The nominal cursor TTL and mid-pass continuation behavior are separate proposed contract detail, not a lifetime equality.

**Minimal replacement/addition (candidate):**

For the browser-direct realization, bound WS lease to demo token expiry (accepted token lifetime<=900s; effective min(jwtExp,connectTime+900s)). Do not refresh beyond token expiry by heartbeat or valid activity; new token requires fresh authenticated epoch/generation. Retire technical controls/tombstones only when their actual epoch validity closes, never on arbitrary seven-day expiry.
A cursor has its own proposed maximum900s from mint and may outlive the socket; its existence is not access authorization. Recheck current token/subject/mandate and deployment/transaction/generation/H/R/lastSequence on every page and disclosure. Expired token forbids the page even if cursor remains cryptographically valid. Expired cursor or changed expiryRevision/generation yields existing410 RESYNC_REQUIRED semantics; unknown/tampered400 and wrong scope403 remain.
Before each next page, compare cursor remaining validity with a conservative bounded page round-trip margin. If insufficient, stop at the last authorized completed page and show interrupted/lag, never completeness. At actual token/cursor expiry disclose no further page. Renew/re-authenticate and resubscribe with new epoch/generation, then by default request snapshot=new from verified contiguous watermark with new H/R/F, explicit gaps/truncation and existing page/pass bounds. Do not extend old H, transplant cursor across generation, renew expired payload, or convert reconnect into payment retry. Same-snapshot cursor renewal is NOT specified or authorized by this delta; it needs a separate independently reviewed operation if desired. Server close jitter may close early to spread cohorts, never later than actual expiry/revocation bound.

**Disposition before normative use:** No new owner choice for existing accepted expiry/auth direction. New consolidated browser cursor/mid-pass wording, margin implementation and any later same-snapshot continuation require independent domain/Security review. 15min is an existing proposal, not approved here; corrected retention remains pending. **Independent acquiring-domain review: REQUIRED for this exact delta bundle.**

**Given/When/Then obligation (not executed):** Given cursor minted after socket connect, when JWT expires first mid-pass, then stop protected page fetch even though cursor has time left. After new epoch reject old cursor and make bounded new snapshot; revision change returns410, with no duplicated financial send.

**Citation/qualification:** Citation correction: rev4 §10 is the decisions/escalation section; the staleness is carried in rev4 §3.4/§5 and rev3 §5.1/§6 line215, not invented as text in rev4 §10.

## 3. Integrated exposure and authorization boundary

Anonymous network access to static SPA/assets and the issuer login/selected protocol endpoints is not anonymous authorization. A public OIDC client has no confidential secret, but token issuance still requires the selected code+PKCE or permitted refresh proof and issuer validation; anonymous token minting, password/grant bypass and unrestricted issuer admin endpoints are not introduced. Protected console read/history/detail/query and synthetic submission require the correct access-token class, subject, audience/scope and current mandate. Browser business fields remain untrusted. The public login boundary is not a promise of per-person privacy; anyone holding the shared demo credential can read that context and initiate synthetic payments within mandate/rates, as the review restates. The SPA's demo token can be stolen by XSS despite memory-only custody; controls reduce, not eliminate, that risk.

D-8 explicitly resolves the console.write lineage: it was the removed relay's payment-initiation scope, not text in C1. Rev4 instead proposes svc-console with console.dispatch at the ACL audience; console.write is neither silently accepted there nor moved into the browser token. Exact grant migration and trusted propagation of demo authorization remain subject to domain/Security review. The proposed service token permits authorized initiation through normal processing; it grants no issuer-result or C4-signature authority.

The service-token class in D-5 cannot bypass the required demo sub check merely because audience/scope happen to match. Authorizer and issuer mapping must explicitly agree how a console-demo subject gets the WS-specific token and how latent svc-console-ws is retired or re-scoped. This remains the one OPEN CONFLICT; the candidate restrictive text is not reported as an accepted resolution.

## 4. Review/disposition ledger

| Delta IDs | Current disposition | Human decision still needed? | Independent acquiring-domain review before normative use? |
| --- | --- | --- | --- |
| D-1, D-2, D-4, D-6, D-7, D-8, D-9, A-1, A-2, R-2 | PROPOSED NORMATIVE CHANGE; candidate replacements only | No new owner choice within recorded direction. Material deviations require explicit disposition, not assumed approval. | YES, exact integrated text plus proposed scenarios and subsequent revised artifacts. Security/DevOps must also validate the new surface and carried conditions. |
| D-3, R-1 | SOURCED CLARIFICATION; existing decisions/guarantees only | No current owner question. Historical supersession is Coordinator-owned; this document does not edit the owner record. | YES for the integrated replacement bundle; sourced status does not approve proposals incorporated by reference. |
| D-5 | OPEN CONFLICT: latent service WS client, accepted credential class and demo WS token issuance; candidate restrictive resolution only | None immediately. DevOps + domain + Security must first resolve it consistently with demo-only access. Escalate only if conditional WS controls cannot hold and a broker or HTTP-only behavior change is necessary. | YES; Security's conditional design acceptability is not a normative authorizer allowlist or implementation verdict. |
| Future destructive business/evidence retirement (R-1 / U1) | Outside current authorization; compact equivalence unproven | Only if material loss/final destruction is later requested, after concrete equivalence/loss assessment. No present question. | YES plus owner trade-off if evidence/capability is surrendered. |

All13 delta IDs still require independent acquiring-domain disposition of this exact bundle before normative incorporation. No onward delegation is performed here. After reviewed approval/disposition, the owning roles may commission exact contract/scenario revisions through the change path; this document does not apply them. No blanket human approval is inferred for worker mechanics, cursor duration, refresh cap, technical rate values, endpoint additions or client-mapping details. The previously established30s default is not an owner question; its stale-text harmonization remains a team task from the retention correction.

## 5. Consolidated missing evidence and implementation gate

Security's conditions are still gates before implementation of the new surface, not a report that these controls exist. The present authoring deliverable does not close them:

1. Independent acquiring-domain review of all13 entries, exact candidate replacements, ADV-8 non-rule-row/scenario changes and corrected retention cross-references; independent final normative disposition. D-5 issuer/client/audience reconciliation and D-8 scope/trusted-demo-binding design remain explicit.
2. Full carried rev3 §1.5 sender denial cases and §2.5 effective-invoke restriction evidence: principal/account deny evaluation, lambda:SourceFunctionArn enforcement, qualified-ARN/alias inventory, organization/SCP constraints and no evidence-function invoke path from console runtime. SR2-01/02 stay OPEN. No console route may become late-evidence admission (CA-1).
3. Status/version-bearing registry records, per-frame and disclosure revalidation, idle scheduler and fail-closed registry errors; timestamped <=30s revocation cases around boundary, silent client and expiry/close behavior. No old “SDK client cache means registry cache” claim; per-request registry-read evidence is distinct from absent WS revalidation.
4. Subprotocol end-to-end carriage, authorizer readability, legal value/size/proxy limits, fixed protocol response without credential echo, and no Sec-WebSocket-Protocol/Authorization token in any CloudFront/Gateway/WAF/issuer/application/failure capture. If not provable, ticket fallback needs separate reviewed endpoint/state/single-use/subject/context/expiry contract; it is not enabled here. A broker or HTTP-only change is not silently selected.
5. Public-client code+PKCE/state/nonce/code-reuse behavior in the pinned Keycloak release; exact redirects and CORS at issuer; audience separation among console/WS/service tokens; refresh rotation/reuse detection, issuer logout/session-cap revocation, no API cookie/persistent token and no service material in bundle. Access-token-only and re-login alternative must be assessed rather than assuming refresh configuration works.
6. Same-origin CloudFront-to-console routing, custom WS origin/authorizer behavior, CSP on normal/error paths (with actual wss destination), no API Access-Control-Allow-* allowance, simple cross-origin POST rejection, authentication independent of CORS, actual WAF/issuer/API throttling and authorizer isolation. Numeric rates/absolute cap remain proposed. No rules were exercised, no browser was opened and no code/artifact scan ran.
7. Configuration-secret separation, actual source of runtime keys and custody (not Terraform state/plan/image/log), reproducible realm/import and public-key registration, issuer rebuild/import/client verification in a separately authorized environment. Automated backups do not become a restore promise. rev3 §3 rotation minima and retest requirements remain; planned overlap is not emergency-revocation delay. Disposable issuer rebuild does not erase simulator evidence/history or imply a7-day issuer backup promise.
8. NV-05 record/table/key/TTL mapping, durable expiry marker/range/dedupe barrier, all-query/fanout logical filtering, stream/delete no-resurrection races, retained no-resend/evidence identities, independent clock-based QA and cursor/lease/new-generation checks. Corrected retention remains pending review; an event tombstone cannot demonstrate business-evidence equivalence.
9. A newly reviewed W-scenario/fixture revision replacing loopback/relay bindings, keeping subscription conflicts, fixed snapshots and no financial replay. Existing255 documentary cases and previous unit-test counts are not executed coverage for this new browser surface.

## 6. Execution and evidence limits

Author runtime: native OpenClaw subagent / Codex, system family GPT-6; this exact session's sessions_list reports gpt-6.1-sol, configured default, no model override. Provider-qualified ID unavailable. Do not copy the Security review's glm5.3-flash as the author model. Session: agent:ai-sdlc-requirements:subagent:c537e5bc-0ddb-4d37-b5f5-86dbb451ab13.

Binding is historical Implementation evidence only: /home/pazfernando/projects/acquiring-simulator, feat/wi-acq-auth-001, HEAD cfc456f4f469d9558a78fda8ff9b770b00817315, dirty tree per evidence/t1-traceability-20261120/BINDING.txt and prior handoff; no fresh Git observation. This deliverable attaches to pinned current document bytes, not clean HEAD. No Git/provider commands, approved-file mutation, code/tests, cloud/API probe, web research, deployment, credentials, login, deletion, publication or onward delegation. All writes are under design/contract-deltas/. Local documentary integrity/link/provenance checks are not product tests or acceptance.

Inherited MCP is incomplete (6 and2 tools omitted by policy); no MCP-backed success is claimed. No finding, review condition, implementation/Security/QA gate or NV-05 is closed by authoring. Sources and exact clause/owner quotations, proposed text, classification and disposition are indexed in evidence/clause-index.json. Input SHA identities and closing byte-identity checks are recorded separately; SHA256SUMS provides artifact digests, not proof of correctness or approval.

## 7. Concurrent authority-record append — closing observation

The owner record advanced during drafting from SHA-256 9bf166feed3eed4c0b4d6d4e441ae1ef44d3826a8bdab0cad65277bcd8127cdb to **c73ed0aad9dd6ba563755e3b4242f2b25563381fd3a67260608cb8d767429bd6** (142 to155 lines). The exact original142-line bytes are preserved in [owner record as reviewed](evidence/owner-decisions-as-reviewed-9bf166fe.md); their hash independently matches the initial pin. All cited D-A/D-D/CA-1/K-1/K-2/K-3/P-1 clauses are byte-unchanged. Current-file clause hashes in the index use the new full-file identity and preserve initial identities separately; opening input-identities.json is not overwritten.

Read the new lines144–155: Fernando authorized Implementation’s separate local atomic-runtime wiring/activation and focused tests, preserving30s/no-resend/atomic semantics, with no public endpoint/deployment/credentials or architecture change. That separate authorization is not implementation authority for this new public access surface and does not expand this author’s no-code/no-tests assignment or close Security findings. No access delta or classification changed because of the append. Main input/source chains remain traceable alongside the review’s original owner pin.
