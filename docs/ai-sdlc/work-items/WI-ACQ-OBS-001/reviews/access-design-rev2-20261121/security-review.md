# Security Review — Access Design rev2 (WI-ACQ-OBS-001)

- Reviewer: ai-sdlc-security (independent re-review)
- Date: 2026-11-22
- Repository checkout: /home/pazfernando/projects/acquiring-simulator
- Branch: feat/wi-acq-auth-001
- Status: IN PROGRESS (stub; sections appended incrementally)

## Scope and method

Independent re-review of the revised access realization design (design-rev2.md)
for work item WI-ACQ-OBS-001. This is the third attempt at this review; the two
earlier attempts ended in provider timeouts with no output, so all sections are
written incrementally as they are completed.

Method: document-only review of the supplied design, evidence, compatibility
assessments, owner decisions and the reviewer's own earlier findings
(NV-01..NV-05, AD-01). No Git/provider commands, no AWS calls, no probing, no
credentials, no mutation of proposal or assessments. All input file hashes are
verified before use (see inventory at the end of this file).

Per-question verdicts use: ANSWERED (design + owner decision resolve the item,
with residual risk explicitly accepted by the owner), PARTIALLY ANSWERED (the
revision addresses the core but leaves open points or rests on late/unverified
evidence), NOT ANSWERED. Missing evidence is separated from residual risk
throughout.

---

## Q0 — Input verification — PASS (all expected hashes match)

All eight files supplied with expected SHA-256 values were hashed before use and
match exactly. Note: the paths given for five of the inputs were relative to the
work-item directory rather than the checkout root; the files were located under
`docs/ai-sdlc/work-items/WI-ACQ-OBS-001/` and hashed at those locations. The
correct file content was confirmed by hash, so path abbreviation does not
invalidate the inputs. One ambiguity resolved by hash: two candidate
`domain-review.md` files exist (`reviews/retention-20261121/` matches its
expected hash; `reviews/retention-20261122/` does not correspond to any supplied
value and was not used). `design/retention/evidence/revisions/pre-domain-correction-20261121/retention-delta.md`
also matched no supplied value and was not used; `design/retention/retention-delta.md`
matched exactly and was used.

Observed hashes (all equal to the expected values):

| Input | Expected SHA-256 | Match |
|---|---|---|
| `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/access-realization/design-rev2.md` | `6b15161875e702ebae74e4f76b4b94cae9ffbbb94d6a9fcd42aee563b8f3873c` | YES |
| `.../design/access-realization/evidence-rev2/revision-validation.json` | `7b720c0de1d20755c9bbcd69b3aff2595d1df79724667d3bc6d6a222a4e56652` | YES |
| `.../design/access-realization/compatibility-assessment.md` | `4886106c0afbf9ded1736ef3c0756c2bc8c738c58c61457faa0a635ecdd836ac` | YES |
| `.../design/access-realization/compatibility-assessment-rev2.md` | `2f7cfe054aae7d908948288b6e67799aef7c864e1acaeb67c7444e63c432b930` | YES |
| `.../design/access-realization/evidence-rev2-assessment/manifest.json` | `48b8b1faffeb2fb9db7272e834ccb4921607fda3d327859cb68f766a018c2454` | YES |
| `.../owner-decisions.md` (no expected value supplied; observed `0368976f5b1efc557aa0729a59054023ca1a2761e5b1a141544746e5b4073f29`) | — | recorded |
| `.../reviews/access-design-20261120/security-review.md` | `0d71ea007e56b12d9873df14126c22640c3abc68052b42db1f47b0a9688d218e` | YES |
| `.../design/retention/retention-delta.md` | `d11ead64f0bd3928b4112b29c408eab1d3e0f9401b84a4580e3d4adb8b35fd76` | YES |
| `.../reviews/retention-20261121/domain-review.md` | `dcdd04ac330491512b04906264760e47e7dce3c3506f78e76688ed209fc4d322` | YES |

No hash mismatches; proceeding.

---

## Execution-model note (recorded, unresolved discrepancy)

Per the role operating contract no model override was requested or applied by this reviewer.
Runtime observed by this session: native OpenClaw subagent, session `agent:ai-sdlc-security:subagent:54f52f1d-c716-4bf0-ac86-6bdc8361b4c4`, request depth 1/3 of this commission's attempts; session model metadata **`nan/glm5.3-flash`** (reported as the configured default; provider-qualified identifier not exposed and not invented).

**Discrepancy, reported not resolved here:** `owner-decisions.md` as observed on disk (SHA-256 `0368976f…`) records a 2026-11-22 owner decision that *this* re-review must run on `openai/gpt-6.1-sol` with a single owner-approved fallback of `nan/deepseek-v4-flash`, and that a review produced under any other model must make any depth difference visible. This session executed on `nan/glm5.3-flash`, which is **neither** the owner-recorded primary **nor** the owner-recorded fallback. The current commission brief instead stated the owner authorized retrying "with the agent's configured model unchanged". This reviewer cannot change its own model and records the conflict factually; per the contract, any owner re-disposition of the execution model is a Coordinator/owner question, not a reviewer decision. All three attempts of this commission (two prior timeouts and this attempt) have therefore executed on the agent's default, not on the owner-recorded model. This discrepancy is flagged for Coordinator before the verdict is consumed as the commissioned re-review output.

---

## Q1 — Status of NV-01..NV-05 and AD-01 against the revision

Verdict basis: `design-rev2.md` (§1, §2, §3, §4, §8, §11, §12), `compatibility-assessment-rev2.md` (the Implementation delta pass), the preserved v1 assessment (`compatibility-assessment.md`), the earlier Security review (`access-design-20261120/security-review.md`, NV-01..05, AD-01), and the retention chain (`design/retention/retention-delta.md` + `reviews/retention-20261121/domain-review.md`).

Design-rev2 §11 itself claims **zero findings closed**; this section independently re-states each status and what would actually close it.

| Finding | Status against rev2 | Where answered | Open part / why not closed |
|---|---|---|---|
| **NV-01** scenario topology and alternate ingress | **PARTIALLY ANSWERED** | rev2 §8 scenario matrix (S1–S8 + S5i); S5 public late route withdrawn; S4 corrected to "resource policy absent today"; per-row exposure/TLS/identity/audience/mandate/deny columns | The matrix is a document, not executed evidence. No IaC or static assertion exists; the checkout has exactly one SAM HttpApi with a native JWT authorizer (`TestJwt`) and one route, which implements none of the rows. Alternate-ingress negatives (default `.execute-api.` hostname, missing/untrusted/expired certificate, deployed route inventory) are unexecuted. S6 accurately records "no mTLS, undocumented feature support" — the WebSocket TLS/identity question is answered only by the owner-accepted residual (D-D), not by a compatible mechanism. Assessment §1 confirms even the current single route cannot realize S2/S3/S6.
| **NV-02** token endpoint, certificate/client/mandate binding, C4 key binding | **PARTIALLY ANSWERED** | rev2 §2 (Keycloak as issuer, Signed-JWT clients, no secrets, audience mappers, revocation two-switch design), §2.9 (identity planes preserved), §5 (role mapping); Design v1 §3–§5 carried forward | The issuer is now *selected*, not *verified*: token_endpoint_auth_method advertisement, RS256 allowlist, token lifespan, and not-before semantics are explicitly self-listed unverified (rev2 §12 items 1–4). Access and refresh of the cert↔client↔mandate binding for S2/S3 is not implementable with the current checkout — no code reads `client_id`, `aud`, `scope`, or certificate fingerprints (`AuthorizedContext` carries only `acquirerRef/merchantRef/callerId/permittedRoutes`; no JOSE library, no HTTP client, no relay process; assessment §1 items 1–5). App-side revalidation is now honestly labeled **required work**, not existing behavior.
| **NV-03** WebSocket service authentication and lifecycle | **PARTIALLY ANSWERED** | rev2 §3 (lease corrected to `min(jwtExp, now+900 s)` = `jwtExp`; the false 30-minute constant deleted; reconnect = renewal with new epoch; registry cache ≤30 s + strong read on scope-changing frames; D-D residual accepted) | Correct arithmetic does not equal implementation: the current code caches the registry at cold start **with no TTL** (assessment §3, rev2 §3 both note this as required work); no `$connect` REQUEST authorizer, no per-frame revalidation, no epoch/cursor code exists. The mid-pass cursor re-mint mechanism has neither contract text nor a code hook (assessment §3 item "mid-pass re-mint"). The pinned contract text (`observation-contract.md` O4 30-minute lease, C6 IAM-signing) is stale and CA-5's list is incomplete (see AD-01). New DoS/revocation surface under the corrected lease is analyzed in Q4.
| **NV-04** key custody, GitHub OIDC, Terraform state, secret data flow | **PARTIALLY ANSWERED** (scope widened, still no artifact) | rev2 §2.6 (assertion-key custody: out-of-band generation, parameter store/local file, rotation overlap, two-switch revocation), §9 (schema delta: new `KC_*` variables, `KC_DB_PASSWORD`, one-time bootstrap admin, digest pinning; S3 OIDC/state retained from v1 §6–§8) | Still configuration-schema only: no Terraform, no workflow, no runtime injection mechanism implemented. The Keycloak decision adds fresh sensitive surface with its own unverified items: ALB→Keycloak proxy-header trust with dynamic ALB addresses (rev2 §12 item 5, residual honestly recorded), SSM port-forward availability (item 6), Aurora pricing (item 7). Self-listed unverified, correctly not claimed.
| **NV-05** one-week TTL without loss of no-resend/evidence identity | **NOT CLOSED — answered only in a different work stream, still pending re-review** | rev2 §11: "out of scope (`design/retention/`) — unchanged" | The retention delta (`design/retention/retention-delta.md`, rev. 2026-11-21) supplies a record-by-record matrix, R-RET-01..08, explicit two-phase expiry ordering, and late-evidence-after-expiry semantics; the acquiring-domain review (2026-11-21) is **CONDITIONAL, advisory only** and applied to the *previous* draft (`ff5fbba0…`), not to the corrected revision (`d11ead64…`). The corrected revision's own status line states "**NV-05 remains OPEN**" and requires independent re-review. No Security closure exists; this re-review does not grant one.
| **AD-01** historical contracts must remain explicitly overlaid | **STILL OPEN** | rev2 §1, §6 CA-5 (C6/O4 recorded stale pending a traceable contract delta), §3/D-D | No contract delta has been authored; requirements/traceability have not been updated; and the delta assessment found CA-5 **incomplete**: `observation-contract.md` O4 line 43 still pins a *30-minute* technical lease and rev2's stale list omits it (assessment rev2 §3). AD-01's exact risk — an implementer reading pinned stale text — is still live.

### What executed evidence would actually close each item

- **NV-01:** reviewed parameterized IaC (or SAM change) for every S-row with default `execute-api`/default WS endpoints disabled, truststore versions pinned, and a Lambda REQUEST authorizer where needed; a deployed route/function-URL inventory proving no alternate invocable path (including no late-evidence route); then independently authorized handshake negatives — missing/untrusted/expired certificate, default hostname, wrong-acquirer context — recorded with actual responses.
- **NV-02:** deployed discovery document showing `token_endpoint_auth_method=private_key_jwt` and RS256 for the three clients; executed local negative vectors: assertion replay (`jti`), wrong audience, unknown `kid`, wrong algorithm, expired/long-lived assertions, realm-cross tokens; a request authorizer that binds leaf certificate → registered client → registry mandate with cross-client/swap negatives; executed denial of assertion-as-API-bearer and demo/ID-token-as-bearer.
- **NV-03:** implemented `$connect` REQUEST authorizer and per-frame revalidation with the ≤30 s registry policy; executed revocation test (client disabled in registry → close within one heartbeat); new-epoch reconnect test; stale cursor/epoch/foreign-transaction negatives; and the mid-pass re-mint behavior defined and tested.
- **NV-04:** authored and reviewed Terraform/workflow artifacts passing static trust-policy/policy negatives (wrong repo/ref/environment/audience); a secret data-flow inventory proving no private key reaches Git, artifacts, state, logs or browser bundles; backend with locking and per-environment keys; on owner authorization, an OIDC assumption/rotation check.
- **NV-05:** independent re-review of the corrected retention draft (`.d11ead64…`), then a concrete record/table/key/TTL mapping, crash/concurrency clock-driven QA (NV05-S01..S14 class oracles) executed on the realized store, and selectable issuer replay/skew/cache-failure behavior evidence (R-RET-08) before the assertion-replay row is treated as supplied.
- **AD-01:** an authored traceable contract delta (Requirements path) for C6/O4/O8-class stale clauses — **including the 30-minute lease text** — and refreshed requirement traceability mapping superseded clauses to their replacements.

---

## Q2 — Self-managed Keycloak (D-A realized in rev2 §2)

Verdict: **PARTIALLY ANSWERED — the design is coherent and honest about its residuals, but every load-bearing property is documented-intent, not executed evidence, and three of its own “irreducible decisions” (K-1, K-2, K-3) are still pending owner disposition.** No Keycloak instance, realm or client exists in the checkout; the delta assessment confirms the code cannot even hold a client identity yet.

### 2.1 Realm and client configuration with signed-JWT, no client secret — design present, capability unverified

- One realm per environment (`acq-<env>`), one realm per environment isolation of tokens; registration disabled; brute-force detection; SSL mode *External requests* (documented to permit loopback HTTP only inside private/loopback addresses). Three confidential clients (`svc-console-relay`, `svc-console-ws`, `svc-acl`) authenticate by **Signed JWT (RFC 7523)**, RS256 with fixed `kid`, public key registered, **no client secret generated or retained**; the demo client is public with PKCE; direct-access grants disabled (rev2 §2.5).
- Required completeness items that the design itself flags and that this review confirms as **not yet evidenced**: whether the deployed discovery document advertises `token_endpoint_auth_method=private_key_jwt`; the exact algorithm allowlist in the client-credential form (RS256 is required by the API-side/Gateway RSA-only expectation but the UI's allowlist is unconfirmed); whether Keycloak's default access-token lifespan and per-client overrides behave as assumed (rev2 §12 items 1–3, assessment conclusion). Until those are read from a deployed instance, “no client secret” and “Signed JWT only” are design intent.
- Positive check the design gets right: the audience-mapper requirement is explicitly stated (default Keycloak `aud` would otherwise fail the authorizer's audience check — the silent breakage), scope comes from client scopes, and assertion rules (issuer/client/time/replay) are carried from v1 rather than dropped with the provider change.
- A residual the rev2 statement does not fully spell out: with `jti` replay rejection, the design (v1 carried) treats a short-lived `jti` cache as a *practical implementation choice*; retention-delta R-RET-08/NV05-S12 and the domain review correctly require realized issuer behavior evidence before treating replay prevention as supplied. Consistent; still missing.

### 2.2 Are admin endpoints genuinely unreachable behind the proxy? — design-forced by construction, residual on header trust

- Public surface is allowlisted by host and path at the ALB: `/realms/<realm>/protocol/openid-connect/*`, `/resources/*`, `/.well-known/*`; everything else (`/admin/*`, `/realms/master/*`, `/metrics`, `/health*`, root `/`) returns 403 at the proxy; no public admin DNS record; task security group admits only the ALB and the operator path (rev2 §2.4, §9.6). This matches Keycloak's own reverse-proxy guidance the design cites (only those public paths needed; separate admin hostname by itself does not restrict the admin REST API), so the *place­ment* argument is documented-source-grounded and direction is correct.
- **Not executed:** no ALB listener, no ACL, no realm exists; the §9.6 static checks are design obligations. The real residual the design records honestly: `proxy-trusted-addresses` cannot be pinned to a stable ALB address list; without it, control degrades to security-group isolation plus ALB-overwritten `X-Forwarded-*` — a documented downgrade, not a solved control (rev2 §2.4 bullet, §12 item 5). If that degraded shape is deployed, spoofed proxy headers from *outside* the SG remain impossible in principle, but the defense-in-depth layer is thinner than the text implies.
- The 403-at-proxy choice (rather than network-level absence) is acceptable but means the admin API is one listener-rule regression away from exposure; this makes the §9.6 static checks and a deploy-time assertion materially important, not optional hygiene.

### 2.3 Private placement and administrator access

- Task in private subnets; Aurora in private subnets with TLS verify-server on the DB connection; admin reached only by SSM port forwarding (or equivalent operator-only path); one-time bootstrap admin credential held as a GitHub Secret and removed/disabled after a named operator account exists (rev2 §2.4, §9.3). **Assessment-corrected realism:** SSM port-forward availability is itself unverified (§12 item 6) — if unavailable, there is currently no specified alternative admin path, which would force exactly the wrong decision (re-opening a public admin surface). That dependency should be resolved before realm construction.
- Bootstrap-to-named-operator credential rotation is specified; operator-level accountability (who is the named operator) is deferred to the pending K-2 decision.

### 2.4 Backup, patching, availability, ownership

- Backup: Aurora automated backups + PITR (proposal: 7-day window, explicitly not the functional-history retention), realm configuration as **code** via boot-time import because Admin-Console exports are documented unsuitable for backup — the correct documented fact, cited. Restore procedure requires a rehearsed run; “no restore claim without a rehearsed run” is honest. Patching: digest-pinned official container, monthly rebuild/redeploy, major upgrades rehearsed on a copy.
- Availability: **single Fargate task, no HA (K-1 pending)**; ownership: product owner accountable, DevOps-authored runbook, no on-call/SLA (K-2 pending). The document explicitly refuses to call this a “non-functional detail”. This review records the same: these are accepted-risk decisions *the owner has not yet made*; the design is therefore incomplete pending K-1..K-3, and the residual must be dispositioned, not assumed.

### 2.5 Assertion-key custody, rotation overlap, revocation propagation

- Custody: keys generated out-of-band; only the public key registered; private keys never enter Keycloak/Terraform/Git/CI artifacts; runtime custody via parameter-store SecureString or operator-provisioned local file for the presenter relay. Sound and consistent with NV-04's data-flow requirement.
- Rotation: “register new → overlap window → switch signing → remove old”; the zero-touch JWKS-URL path is documented by Keycloak but not selected (no static JWKS host exists); the default is a planned swap of uploaded keys. Rotation overlap is thus defined but its verification depends on the deployed JWKS/KV caching behavior — which rev2 itself flags (§12 item 4): the Gateway may serve a cached verification key for up to ~2 h after removal, so *removal* of the old key propagates slowly. Overlap-window design must be sized against that convergence, not merely against Keycloak's own fetch behavior; the document names the issue without sizing the window.
- Revocation: two independent switches — realm/client **not-before** (invalidates tokens issued before a timestamp) plus the simulator registry marking the client `REVOKED` so the API refuses tokens even if Keycloak still considers them valid. Authorizer-side cache bounded at ≤30 s. This two-switch design is the strongest part of the rev2 Keycloak work: it correctly does not trust a single lagging mechanism — but it only works if the API-side registry re-read is actually implemented, and the current code caches at cold start with no TTL (assessment §3; rev2 §3 both record this as required work).
- Presenter-local relay key (K-3, pending): private key lives outside AWS control; mitigated by narrow scopes (`console.*`, `ws.observe`), rotation per demo window, immediate revocation on suspected loss, no payment/evidence authority. The residual is genuinely stated, not papered over; whether it is *accepted* is exactly the pending K-3 decision.

### 2.6 What a Keycloak outage does to payment availability

- Rev2's statement: an outage blocks **new** authentication and token issuance therefore new work; in-flight payments unaffected because the payment path holds no Keycloak dependency after authorization. This review adds precision: access and refresh tokens have 900 s validity, so *new* ISO payment dispatchs (S3), console writes (S2) and WebSocket connects (S6) degrade to the lifetime of tokens already held at outage onset — a ACL holding an unexpired token can keep sending until token expiry (≤15 min); after that, new authorizations fail closed until Keycloak returns. In-flight *already-dispatched* ISO processing and already-authorized payments are unaffected, as rev2 states. There is no bypass, no cached long-lived credential, no break-glass that could widen the compromise surface — appropriate fail closed for a demonstrator, but the K-1 decision (single task, accept outage) must be read with this quantification: the outage blast window is “present token remainder + outage duration”.
- Timing effects: realm boot-time import on image upgrade, and Aurora failover behavior for the single task, are named as unverified (§12 items 8–9); a single-task deployment also means patching windows are authentication outages by construction.

### 2.7 New single points of compromise introduced

1. **Keycloak itself is a new identity root.** A compromise of the always-on, internet-fronted issuer (admin API, container, or Aurora backing store) lets an attacker rewrite client registration/JWKS or mint arbitrary service tokens — i.e., full API ingress impersonation for the relay/ACL/WS planes at once, up to the 900 s token horizon but indefinitely by repeated minting. This is a materially larger blast radius than the previous managed-tenant idea and must be treated as a tier-0 asset (WAF, patching cadence, restricted admin, SGs — all present but none executed).
2. **`KC_DB_PASSWORD` and the one-time bootstrap admin secret** are high-value secrets in the CI secret store and task environment; compromise yields the DB and admin plane.
3. **The three assertion private keys** (parameter store + presenter-local file): compromise yields client impersonation per client (bounded by scope, revocation ≤ registry heartbeat); the presenter-local one additionally has the out-of-AWS exposure acknowledged in K-3.
4. **Single-task availability SPOF** (K-1 pending): Keycloak outage = authentication outage as quantified in 2.6.
5. Not a SPOF by design, but worth stating: the mTLS/`X-Simulator-*` CA4 evidence plane and GitHub OIDC plane are unchanged and remain independent of Keycloak — the design's separation claims hold.

No finding is closed by §2; the strongest statement available is that design-rev2 §2 answers the D-A design questions in the requested depth and correctly refuses to claim deployed behavior.

---

## Q3 — Internal-only late evidence (CA-1 realized, rev2 §4, S5i)

Verdict: **PARTIALLY ANSWERED with one material design defect.** The *placement* (no public route, no URL, no DNS) is correct and confirmed unconflicted by code and pinned contract; the nine deny obligations are correctly written as future tests; but the design's central identification mechanism — sender identity derived from the invoking principal via the invocation context — is **not implementable as written** on a shared admission function, and the implementation delta pass proves it.

### 3.1 The private invoke (S5i)

- Mechanism: private Lambda invocation into a dedicated admission function; no function URL, no API Gateway route, no VPC endpoint, no public DNS (rev2 §4.1). The assessment verified the *negative* claim across the current checkout: `infra/template.yaml` defines exactly three functions with identity-policy grants only, **no `AWS::Lambda::Permission`, no resource policy, no source conditions, no function URL, no `VpcConfig`**; the only late-delivery gateway's own javadoc states there is no HTTP route; `C4LateDeliveryParser.parse` has no `src/main` caller. So there is currently no reachable late-evidence path at all — the "no route exists" property is true today trivially, and the design preserves it.
- HTTP-based submission from an internet client would require creating a route; deny-test 8 (“no route; verified by deployed API mappings and function URLs”) is the right realized-check shape for later.

### 3.2 Resource policy with source ARN and account conditions

- Design intent: resource-based policy permitting only enumerated producer role ARNs with `aws:SourceArn`/`aws:SourceAccount` conditions; absent today, correctly recorded as required work (rev2 §4.1, §8 S4 corrected wording; assessment §2 item 2).
- **Wording defect, flagged by the assessment and confirmed here:** for a *direct* IAM `lambda:InvokeFunction`, the caller is the principal to name; `aws:SourceArn`/`aws:SourceAccount` are condition keys for service-brokered invocations and are not generally present on direct invokes. rev2 itself lists the exact form as unverified (§12 item 11). The deployable policy must name producer-role principals, with source conditions only if a broker is introduced; “SourceArn” in the design text should not be carried into IaC as-is. This is a wording/realisation risk, not a broken control.
- Residual worth stating: with only identity-policy grants (current state), *any* in-account principal holding `lambda:InvokeFunction` on the function could invoke it; the resource policy is what actually delivers “internal-only between enumerated producers”. Until it exists, the control is aspirational — and the design does not claim otherwise (S4/S5i wording is explicit).

### 3.3 Mapping of the invoking principal to a sender role and allowed key ids

- Design: sender identity derived from the invocation context — the calling principal's ARN mapped through the registry to a `C4PrincipalRole` and an allowed `keyId` set; never from payload fields/headers (rev2 §4.1).
- **Material defect: the Lambda runtime does not expose the caller to a shared function.** The delta assessment read the locally cached `aws-lambda-java-core` 1.2.3 jar: `Context` exposes `getInvokedFunctionArn` — the *invoked function's own ARN* — plus request id/log streams/remaining-time etc.; there is **no caller-identity accessor** for a direct invoke. A single shared admission function therefore cannot distinguish processor from network-simulator (or diagnostic forwarder) from its context, and rev2 §4.1's registry mapping presumes a fact the runtime never supplies.
- The assessment's structural options are correct: one admission function (or alias/version) per producer role so that function identity implies the sender; or an authenticating broker passing a trusted identity; or a caller-produced signature (which would re-introduce a credential on the late path and must not be a payload field). Choosing one is a design decision rev2 must make; as written, S5i's sender-binding sentence is not a buildable control.
- Transport-record mismatch compounds it: `C4AuthenticatedTransportContext.authenticatedSender` is 1–64 chars of `[A-Za-z0-9._:-]`; an IAM ARN cannot pass validation, so any mapping must produce short principal ids — and no such mapping exists (assessment §2 item 4). CA-3 widening is acknowledged but deferred to Implementation; widened or not, no code path currently transports the fact.
- The four-value role vocabulary and the diagnostic permission's deny-by-default shape are correctly carried from §5; note assessment's correction that `ACL_AUTH` is a *non-signing transport identity* — rev2 §5's phrase “signs the instruction-side attestation” assigns an authority the verifier deliberately withholds (P5→P4 row map returns null for `ACL_AUTH`). Cosmetic for access design, material for the C4 verification contract that S5i depends on.
- Additional unreachability the assessment surfaced and rev2 does not state: the diagnostic-permission path is defined and unit-tested but **not reachable through either production composition** (`SignedEvidenceAdmissionService` refuses non-direct/relay senders; `LateSignedEvidenceReceiptGateway` calls `verify`, not `verifyForwarded`) — so rev2 §4.1's “Diagnostic delivery” row is not realized end-to-end by any existing composition.

### 3.4 Residual paths for an outside party to submit evidence

- Internet/relay/browser paths: structurally absent by design — no route, no URL, no DNS; the essential protection is then keeping it that way (route inventory is the closure test, correctly enumerated as deny #8).
- In-account residuals, on the *future* deployed shape: (a) any principal granted `lambda:InvokeFunction` and named in the resource policy — bounded by the registry-to-role mapping and, critically, by the unchanged Ed25519 verification (instruction/response hashes, DIRECT/RELAY roles, relay countersignature), which exists independently of invocation identity and remains the backstop; (b) a future convenient “proxy/principal-vending” refactor that would either widen the resource policy or pass sender claims inside the payload — deny tests #1, #2, #5 exist to reject exactly these; they must be executed, not merely listed.
- Failure semantics are preserved: a rejected delivery never becomes a payment result; no resend/recovery worker is introduced; post-expiry arrival truncates rather than resurrects (deny #9, consistent with R-RET-06c). No new financial authority is created by this section.

**Net for Q3:** placement and denial obligations are sound; the sender-identity mechanism must be redesigned (per-producer function identity or a broker), the resource-policy condition wording corrected, and the diagnostic path mapped to a real admission composition. Until then the internal-only property is *enforceable by absence of routes* but its producer-identity contract is not yet a buildable control.

---

## Q4 — WebSocket lease = token expiry with re-mint per reconnect (rev2 §3, S6)

Verdict: **PARTIALLY ANSWERED — the arithmetic correction is sound and revocation stays prompt *if* the required registry re-read is implemented; the shortened lease does introduce new availability cost and a not-yet-designed reconnect surface, both of which need disposition.** D-D's accepted residual (no mTLS on this path) stands; NV-03 remains open.

### 4.1 Arithmetic and revocation timeliness

- The correction is right: with access tokens ≤900 s minted before connect, the v1 `min(jwtExp, now+30 min)` never actually bound on the 30-minute term; quoting a second constant implied a control that did not exist. Rev2 deletes the constant and sets the effective lease to `jwtExp` (≤900 s), keeping the token lifetime at 900 s rather than lengthening the credential to make the dead term bind — the correct direction, explicitly refusing to pay for a longer bearer lifetime on the boundary that lost mTLS.
- Revocation timeliness: with per-frame revalidation keyed on a ≤30 s registry cache plus an unconditional strongly-consistent read on the 30 s heartbeat and on scope-changing frames, registry-level revocation bounds at roughly one heartbeat (~30 s). Token-level revocation (Keycloak client not-before/disable) does *not* propagate through the Gateway JWKS cache promptly (up to ~2 h; rev2 §12 item 4), so the registry switch is the operative one — correct design, conditional on the cache being real.
- **The condition is unmet in code:** the current `AwsRuntime.caller` registry read is cached at cold start with no TTL, which would make revocation bound at *deploy lifetime*, not one heartbeat. Rev2 records this as required work; NV-03 stays open until it exists. Correct recording, not yet a control.
- Lease-expiry connect closure is server-driven; a socket at lease end is closed and the relay mints a fresh assertion + token and reconnects with a new epoch. New epoch kills stale cursor/subscription reuse; retention-delta's NV05-S11 and O5a's epoch/generation bindings align.

### 4.2 New availability / DoS exposure of the shorter lease

- **Reconnect frequency scales inversely with lease length.** At lease = token expiry, every live socket reconnects roughly every ≤15 min (more often if the token was minted mid-validity before connect). Against v1's nominal 30-minute lease this at least doubles reconnect and `$connect` authorizer invocations per client, and multiplies token-endpoint load on the (already single-task) Keycloak and assertion re-signing at the relay. For a demonstrator load this is acceptable, but it is a new constant load on the tier-0 identity component identified in Q2 — the two owner decisions interact: K-1 (single task) + lease=jwtExp raises Keycloak's minimum required throughput for the same console experience.
- **Amplification/abuse surface at `$connect`:** the WS custom domain is a public, authenticated API Gateway endpoint; identity is a bearer service token plus the demo token (no mTLS, per D-D). A holder of any leaked/abused `ws.observe` token for the demo subject can churn `$connect`/`$disconnect`, burning authorizer invocations, registry reads, connections, and (at each failed reconnect) Keycloak token requests. No per-principal connection limit, rate limit, or WAF coverage is specified for the WS path anywhere in rev2 (WAF appears only on the Keycloak ALB). The demo-border trust model makes this low-impact, but the DoS exposure is real and should be named in the residual: connect-rate limiting/Throttling on the WS stage or an explicit documented acceptance is missing.
- **Token-mint amplification:** each reconnect is one signed assertion + one token request. A hostile relay loop could use the public Keycloak token endpoint as a cryptogenic load. rev2 proposes WAF and rate limiting without parameterizing them (§2.4); this remains an unspecified control with a stated intent.
- **Availability interference guard preserved:** losing the WebSocket must not block an already-authorized HTTP payment, and reconnect/heartbeat never sends payment — carried forward correctly (S6 deny column; D-D scope limits). revocation/lease failure modes therefore degrade notification, not payment paths.
- **Cursor interplay (assessment-found imprecision, confirmed):** `CURSOR_TTL_SECONDS=900` with `WS_LEASE_SECONDS=900` does not make cursor and lease *coincide* — the lease is typically shorter (token minted before connect), so a cursor can outlive the lease until the token's own expiry. Not a security break (C7 bounds the cursor at 15 min anyway), but rev2's “coincides exactly” sentence should not be read as an equality proof. The catch-up mid-pass re-mint mechanism (needed on any pass longer than 900 s) has neither contract text nor code hook (assessment §3) — that is the sharpest remaining design hole in the S6 story: a long pass currently has no defined, safe continuation, and an under-specified re-mint on an expiring authorization is precisely where stale-authorization reuse would hide.

### 4.3 Bottom line

- Revocation: stays timely **only conditionally** (registry re-read ≤30 s must be implemented; token-revocation propagation through Gateway JWKS cache is and remains slow — correctly mitigated by the registry switch and the ≤900 s natural expiry).
- New exposures: modest but real reconnect/authorizer amplification, Keycloak load coupling, and an unspecified WS-path rate-limit control; the long-pass re-mint mechanism is undefined. These are design-completion items and one residual (no WS rate limiting) that should be recorded as an accepted or a resolved item, not silently omitted.

---

## Q5 — Controls quietly relaxed relative to v1, and new risks from the three owner decisions

Method: v1 `design.md` (SHA-256 `83e4ea14…`) and `configuration-schema.md` (`1f497daa…`) were hashed and read in full and compared against rev2's own delta table (§1) and the rev2 body. Rev2's delta table is honest; the items below are what it does *not* list.

### 5.1 Quiet regressions / unannounced differences (v1 → rev2)

| # | Observation | Effect | Disposition requested |
|---|---|---|---|
| R1 | **Key-rotation overlap window unquantified.** v1 §3.3 pinned a concrete minimum: old assertion key published but not signing for **≥24 h** (sized against the Gateway JWKS cache, documented up to 2 h). Rev2 §2.6 keeps only “keep the old one valid for an overlap window, switch signing, then remove” with **no minimum**, and no replacement number appears anywhere in rev2. A deleted constant was treated as “removed”, but here removal dissolved a control without a replacement. | A short overlap can leave a window where tokens from the old key fail verification (or the new key cannot be fetched), or force operators to shorten revocation latency to compensate. | Restore an explicit minimum (at minimum v1's ≥24 h, sized to measured JWKS convergence) in the configuration schema. |
| R2 | **Late-evidence sender-identity chain weakened on paper, and the replacement is not implementable as written.** v1 S5 gave late evidence two independent proofs: mTLS leaf certificate + dedicated OAuth client (`svc-evidence*`, scopes `evidence.*`). Rev2 deletes both clients/scopes (documented, and consistent with CA-1's internal-only rule) and replaces identity with a resource-policy + invocation-context mapping that the delta assessment proves cannot be derived at runtime on a shared function (see Q3). | The *network* boundary is strictly stronger (no public surface), but the *sender-identity* proof chain drops from a well-defined two-factor mechanism to a currently undefined one. The registry/backstop Ed25519 verification still stands, so no forged evidence is possible from this gap — but “who delivered” is not yet attributable. | Not a control to relax silently: requires the S5i redesign (per-producer function identity or broker) before this boundary has a real sender-identity control at all. |
| R3 | **Five v1-flagged irreducible decisions dropped from the decision list without recorded owner disposition.** v1 §14 listed D-A…D-G. Rev2 §13 announces “only three remain” (K-1..K-3) and its validation claims “no others were minted”, but only D-A (owner) and D-C (settled by owner's CA-1 ruling) have recorded dispositions. D-B (C2 exposure option A vs private topology), D-E (runtime secret-injection service), D-F (Terraform state locking mechanism), D-G (IAM-only internal hops) were recommended and then silently treated as settled defaults with no owner or Security disposition recorded. | D-B in particular was the owner-facing exposure trade; quietly converting “owner/security disposition required” into “team default adopted” is exactly the pattern AD-01 warns about. | Re-present D-B, D-E, D-F, D-G as adopted-by-team-default (not owner-decided), or obtain disposition; do not let them disappear silently. |
| R4 | **`HISTORY_TTL_DAYS=7` appears as a blanket TTL variable in the access schema (§9.2)** while the retention stream's whole point (R-RET-06, O8 77–79) is that physical TTL is record-scoped and must never precede the expiry-metadata barrier. The parenthetical defers applicability to NV-05, but the schema variable as named invites a uniform table TTL. | Implementers wiring `HISTORY_TTL_DAYS` to every table could delete journal/alias/identity rows the retention contract requires to survive. | Schema should split the variable (payload vs metadata) or annotate the barrier condition inline. |
| R5 | **App-side token revalidation stated as absent.** v1 implied edge+application double checking existed; rev2 corrects this to “required work”. Not a relaxation of design, but a *downgrade of realized-control status* that must stay visible until implemented (assessment §1 item 5: any token with correct audience is accepted for any route today). | Tracked under NV-02 closure evidence; recorded here so the corrected honesty is not read as “control present”. | Close with the authorizer/registry work item. |

Nothing else was found relaxed: mTLS truststore/TLS 1.2 on S2/S3, loopback/Host/Origin/CSRF controls, `authorizerResultTtlInSeconds = 0`, `jti` replay table, deny catalogues, demo-subject discipline and the evidence-plane separation all carry forward intact.

### 5.2 Risks introduced by the three owner decisions — recorded as accepted-by-owner, residuals stated

These are restated from the owner's recorded decisions, not accepted by this reviewer on the owner's behalf. Owner disposition already recorded: D-D (“si lo acepto”), D-A (“autogestionado”), CA-1 (“si lo confirmo”).

| Decision | New risk introduced | Residual as recorded by the owner / to remain visible |
|---|---|---|
| **D-A — self-managed Keycloak** | In-house tier-0 identity root: always-on public endpoint, admin plane, backing Aurora DB; compromise yields arbitrary service-token minting (Q2 §2.7). Availability dependency on a single task (K-1 pending) quantified in Q2 §2.6. New operational custody: `KC_DB_PASSWORD`, one-time bootstrap admin, presenter-local assertion key (K-3 pending). | Owner accepted the always-on cost/operational-surface trade when selecting “autogestionado”. Residuals *not yet* dispositioned by the owner: K-1 (no-HA availability), K-2 (no on-call/SLA), K-3 (presenter-local private key). These three remain open owner decisions; this review declines to fold them into D-A's acceptance. |
| **D-D — WebSocket without mTLS** | Public WS endpoint with bearer-only transport: theft of a current `ws.observe` service token plus the demo token yields an observer connection; TLS protects on the wire only; no client authentication at all on the path. Revocation delay is registry-bounded (≤1 heartbeat once implemented) but token theft within a token's ≤900 s life is unpreventable; per-frame revalidation cannot authenticate the transport peer. Amplification/DoS residues noted in Q4 §4.2 (no per-principal connection limit or WS rate limiting specified). | Owner recorded the residual explicitly in D-D (“residual risk is recorded, not removed… does not close NV-03”). Scope limits (observe-only, never send/retry/reverse/re-price, no payment-block on socket loss) are mandatory and unchanged. | 
| **CA-1 — late evidence internal-only** | Control is network-strengthening (public route removed). Residual risks: the admission function becomes an in-account identity-of-last-resort whose resource policy is the *whole* sender boundary (no OAuth client, no mTLS); a mis-scoped resource policy or a future convenience role grant would expose admission to in-account principals — backstopped only by the unchanged Ed25519 verification; and the sender-identity mapping is currently undefined (Q3). | Owner decision scope: no public late endpoint, no automatic financial recovery. Residual to record: “internal-only” is enforceable while the resource policy is minimal and subject to deployed inventory; the sender-attribution chain inside the account must still be designed and executed-tested before S5i is a real control. |

### 5.3 Additional finding outside the three questions that the verdict needs

- **The rev2 delta compatibility assessment is truncated.** `compatibility-assessment-rev2.md` ends at the heading of §6 “New contradictions introduced by rev2” with **no body**, and its own manifest documents the truncation (“no section 6 verdict exists to substantiate”) without reconstructing it. The assessment's final conclusions — and any contradiction it might have found — are absent from the evidence base. Any dependent decision that cites “assessment complete” is currently citing an unfinished artifact. This is recorded here as a fact; completing or superseding §6 is Implementation's work, and Sections 1–5 of the assessment remain usable.

---

## Verdict (overall)

**Verdict: BLOCKED — design closure cannot be completed; confirmed findings and missing mandatory evidence preserved.**

Per the shared Security PASS/FAIL/BLOCKED rules: a Security PASS requires every mandatory scoped check completed with no unresolved BLOCKER/IMPORTANT finding; BLOCKED means missing mandatory evidence prevents completion while preserving any confirmed failures. That is this review's status, for three independent reasons:

1. **No executed evidence exists for any mandatory closure check.** Every retest/closure item from NV-01..04 (reviewed IaC, deployed route inventory, handshake negatives, discovery-document capability, revocation/authorizer behavior) is design obligation only. The delta assessment confirms the current checkout cannot realize any of it yet (no client plane, no relay, no request authorizer, no resource policy, no cursor/range read, no late-evidence function).
2. **A confirmed IMPORTANT design defect is present in the revision under review:** S5i's sender-identity mechanism presumes a caller fact the Lambda runtime does not supply on a shared function (F-01 below). A proposal with a confirmed buildability defect on a mandated control cannot be the basis of design readiness for that boundary.
3. **A mandatory input is incomplete:** the commissioned rev2 compatibility assessment is truncated at its final conclusions section (§6 heading, no body; documented by its own manifest). A dependent re-review cannot treat that artifact as a complete assessment.

No runtime vulnerability is established by this report (document-only review; no scans, no tests, no probes). Findings below are design/process findings with delivery priority, consistent with the earlier review's classification.

| ID | Severity | Finding | Minimal mitigation and retest |
|---|---|---|---|
| F-01 | **IMPORTANT** | S5i sender identity “derived from the invocation context of the calling principal” is not obtainable: `Context.getInvokedFunctionArn` returns the invoked function's ARN, and the 64-character transport-sender field cannot carry an ARN (assessment §2, verified against the cached `aws-lambda-java-core` jar and `C4AuthenticatedTransportContext`). | Choose and document one structure: per-producer admission function/alias, or an authenticating broker; map principals to short registry ids. Retest: deliverings from each producer verified by end-to-end invocation with role/keyId mapping and deny tests S5i 1–9 executed. |
| F-02 | ADVISORY | v1's ≥24 h assertion-key rotation overlap minimum was dropped without replacement (R1). | Restore an explicit minimum sized to measured JWKS convergence; static check in the schema. |
| F-03 | ADVISORY | v1 decisions D-B, D-E, D-F, D-G disappear from rev2's decision list without recorded disposition (R3). | Re-present as adopted-by-team-default or obtain explicit disposition; record in the design's decision table. |
| F-04 | ADVISORY | `HISTORY_TTL_DAYS=7` as a blanket schema variable invites uniform TTL application contrary to R-RET-06's barrier (R4). | Split or annotate the variable so payload TTL cannot be applied to identity/journal/alias records. |
| F-05 | ADVISORY (process) | `compatibility-assessment-rev2.md` §6 is truncated with no body; its manifest documents this. | Implementation completes or supersedes §6 with a fresh artifact and identity. |
| F-06 | ADVISORY (process) | Execution-model discrepancy: the owner-recorded decision (gpt-6.1-sol, fallback deepseek-v4-flash) does not match the model this review actually ran on (`nan/glm5.3-flash`, no override, per contract). | Coordinator surfaces the conflict to the owner; any owner re-disposition supersedes; this report's content should be re-commissioned if the owner requires the recorded model. |

All NV-01..NV-05 / AD-01 statuses in Q1 remain: PARTIALLY ANSWERED (NV-01..04), NOT CLOSED (NV-05, in its own stream), OPEN (AD-01). Nothing here closes any finding; QA, final implementation Security review and human acceptance remain separate and pending. The owner-accepted residuals (D-A/D-D/CA-1) remain visible; K-1..K-3 are still open owner decisions.

---

## Input/output SHA-256 inventory

Inputs: all nine commissioned hashes matched exactly (instances verified before use; see Q0 for path abbreviations). No hash mismatch occurred, so no commissioned input was rejected.

Additional inputs computed (not commissioned; used for v1 comparison and Q0 disambiguation):

| Input | SHA-256 |
|---|---|
| `design/access-realization/design.md` (v1 design) | `83e4ea14b3959dcc6d8535aee0f0dd715783764e8922a72b6c0c3454a40829f4` |
| `design/access-realization/configuration-schema.md` (v1 schema) | `1f497daade1a8429458e2cc40047adf326a97f2fb6636d583e1bf2e930bf1bb2` |
| `owner-decisions.md` | `0368976f5b1efc557aa0729a59054023ca1a2761e5b1a141544746e5b4073f29` (no expected value was supplied; observed — note it differs from every hash cited inside v1/rev2/assessment documents, consistent with that file continuing to advance) |
| `design/retention/evidence/revisions/pre-domain-correction-20261121/retention-delta.md` | `ff5fbba0a8a92f37af971b3b8d308230277e3cace4d29156f0acf8d9555fe6af` (rejection check only) |
| `reviews/retention-20261122/domain-review.md` | `50fa88b49d3779a54ce43be4fa4e8283635967e7c1400df76afd5734f4cd6ecb` (rejection check only) |

Output (this report, after final write; end marker byte-exact above is the freeze point for the hash below): freshly computed after completing this file — see the value returned in the review handoff. The report contains no secrets, no synthetic card data, no exploit detail beyond design-level analysis.

Commands actually executed in this review: `sha256sum` on the nine commissioned inputs (plus four identification checks), `find`/`ls` for path location, and `read` operations on the listed documents. `cat` was used only on the role-owned VERIFICATION_DELIVERY.md. No Git, provider, AWS, network, test, build, scan or probe command was executed; checkout/branch `feat/wi-acq-auth-001` recorded from the supplied binding, not from Git.

Runtime/model observed and recorded: native OpenClaw subagent; session `agent:ai-sdlc-security:subagent:54f52f1d-c716-4bf0-ac86-6bdc8361b4c4`; model `nan/glm5.3-flash`; no task-level model override applied (contract compliance); provider-qualified identifier not exposed and not invented. Execution-model discrepancy with the owner-recorded decision: see F-06/Q0 note.

--- END OF REPORT ---
