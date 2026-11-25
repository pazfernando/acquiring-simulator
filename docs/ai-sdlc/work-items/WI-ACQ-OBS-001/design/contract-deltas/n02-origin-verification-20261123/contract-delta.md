# Contract delta — N-02 closure by CloudFront origin-verification header

Status: DRAFT / NOT NORMATIVE until independently reviewed. This packet is the Requirements-owned contract delta that Security's N-02 condition (1) requires **before construction**. It does not close N-02, does not change any owner decision, and does not authorize deployment.

## 1. Authority, composition and precedence

- **Owner decision (SOURCED).** Fernando Paz, 2026-11-23, exact instruction "adelante con lo recomendado", recorded in `owner-decisions.md` § "N-02 closure (Option B) and normative adoption — 2026-11-23": the console API is protected by a CloudFront origin-verification header, fail-closed on absent or mismatched value, under four binding conditions (contract-first; Terraform-generated state-only value with a policy assertion; denylist-safe non-semantic name; observable direct-path denials).
- **Reviewed defect (SOURCED).** `reviews/continuation-rev3-security-20261123/security-review.md` (SHA-256 `f5696ed917f009c7569cccbba5f42b431170d15a53ef06bdd38c10e61d53ba50`), N-02: the dedicated `api_fqdn` alias remains publicly resolvable, so a caller can reach the console HTTP API without passing the CloudFront edge, evading the per-IP `/api/*` WAF rate bound and the managed rule groups. The JWT authorizer still refuses unauthenticated callers; the exposed class is a holder of the single demo credential.
- **Normative base.** `design/contract-deltas/continuation-rev3-20261123/` (manifest `2000bb004e68e6d2974bef3067baeae6774f1922a21dca4ea18fecf6944ab467`), as amended by `continuation-rev3-dr301-20261123/`. This delta **adds** a boundary clause and does not supersede any D1–D9/A1/A2/R1/R2, G1–G5, C3 or payment/retention clause. Where this text and an inherited clause appear to conflict, the inherited clause governs the business behaviour and this text governs only the pre-business boundary refusal; a conflict that cannot be read that way is a defect to report, not to resolve by preference.
- **Preservation.** No C3 golden byte, no payment outcome, no idempotency rule, no evidence/admission rule and no retention rule changes. The gate runs strictly before any of them.

## 2. Scope

**In scope.** Every request that the CloudFront distribution routes to the console HTTP API origin under path pattern `/api/*`, i.e. everything the `ANY /api/{proxy+}` integration hands to the console Lambda: the submission route (`POST /console/v1/authorizations`), the history read route (`GET /console/v1/transactions/{id}/events`), and any future route added under the same integration. The rule is defined on the **integration boundary**, not per route, so a new route cannot be added without it.

**Explicitly out of scope, recorded rather than silently omitted.**

1. The **WebSocket API** (`ws_fqdn`, module `ws`). It has its own default endpoint disabled and its own publicly resolvable custom domain; the analogous direct-path question is **not** covered here and is not closed by the owner's N-02 decision, which names the console API. The WS authorizer still requires a valid `ws.observe` token per connect and frame revalidation; direct `ws_fqdn` traffic bypasses only edge controls. Flagged for Security/owner disposition as an adjacent residual.
2. The **CloudFront edge** itself (WAF rules, managed rule groups, rate bound) — unchanged by this delta.
3. **Platform-level refusals** by the API Gateway JWT authorizer, which occur before the integration and keep the platform's own response body (see §5.4).
4. Internal routes, issuer surface, TLS/mTLS configuration and IAM.

## 3. Fixed identifiers

These strings are binding and must not be paraphrased. Requirements, DevOps and Implementation all use them verbatim.

| Identifier | Fixed value | Where it appears |
| --- | --- | --- |
| Origin-verification header name (canonical spelling) | `X-Origin-Verify` | CloudFront origin custom header; application expectation; policy check 18 |
| Header name, log-redaction form | `x-origin-verify` | WAF `redacted_fields`, application log deny-list (lowercase as those facilities require) |
| Console Lambda environment variable carrying the expected value | `EDGE_ORIGIN_VERIFY` | `modules/api` environment block; application configuration loader |
| Boundary error code | `EDGE_ORIGIN_NOT_VERIFIED` | response body only |
| Boundary HTTP status | `403` | response status only |
| Denial log event name | `console_origin_verification_denied` | application structured log |
| Denial metric name | `ConsoleOriginVerificationDenied` | CloudWatch metric filter |

The spelling `X-Origin-Verify` is already declared as `local.origin_verify_header` in `modules/console/main.tf` and redacted as `x-origin-verify` in the WAF logging configuration. This delta **binds the authored spelling** rather than introducing a second one; if the authored bytes and this table ever disagree, this table is the contract and the divergence is a defect.

## 4. Header name justification

### 4.1 Against the CloudFront custom-header denylist

Re-verified against the AWS CloudFront developer guide, "Add custom headers to origin requests", on 2026-11-23 22:06 America/Bogota (2026-11-24T03:06Z; fetch recorded in `RUN.md`). CloudFront cannot add: `Cache-Control`, `Connection`, `Content-Length`, `Cookie`, `Host`, `If-Match`, `If-Modified-Since`, `If-None-Match`, `If-Range`, `If-Unmodified-Since`, `Max-Forwards`, `Pragma`, `Proxy-Authenticate`, `Proxy-Authorization`, `Proxy-Connection`, `Range`, `Request-Range`, `TE`, `Trailer`, `Transfer-Encoding`, `Upgrade`, `Via`, any name beginning `X-Amz-` or `X-Edge-`, and `X-Real-Ip`.

`X-Origin-Verify` is in none of those classes: it does not begin with `X-Amz-` or `X-Edge-`, is not a hop-by-hop header, is not a range/conditional header and is not `X-Real-Ip`. It is therefore a permitted custom origin header.

### 4.2 Against headers the application treats semantically

| Header | Why it cannot be used |
| --- | --- |
| `Authorization` | Bearer credential; the platform authorizer's identity source and the app's credential seam. |
| `Content-Type`, `Content-Encoding`, `Content-Length` | Parsed by the C1/channel decoders and the console body validator. |
| `X-Simulator-Profile` | Approved C2 profile selector; a wrong value changes protocol behaviour. |
| `X-Simulator-Evidence` | C4 evidence carriage; bounded and refused when duplicated. |
| `X-Presenter-Request` | `PresenterAccessPolicy.REQUEST_HEADER`; a semantically treated request class. |
| `X-Authenticated-Sender`, `X-Authenticated-Principal`, `X-Simulator-Principal`, `X-Simulator-Sender`, `X-Remote-User`, `X-Forwarded-User` | Refused identity assertions (`HttpApiV2Wire.IDENTITY_HEADERS`); reusing one collides with an explicit refusal rule. |
| `Sec-WebSocket-Protocol` | WS token carriage on the separate API. |
| `Origin` | Browser-context check for WS and CSP; reusing it would conflate a browser assertion with an edge assertion. |
| `Host` | CloudFront denylist and the origin-addressing fact this control depends on. |
| `traceparent`, `X-Amzn-Trace-Id`, `X-Forwarded-*` | Distributed-tracing and proxy metadata; ADOT and any future proxy may rewrite them. |
| `X-Demo-JWT` | Explicitly forbidden by D-5 as a token carrier. |

`X-Origin-Verify` is absent from application code, tests and fixtures. It appears in the repository only as the fixed name in the IaC (`modules/console` local and custom header), in the WAF redaction, in `tools/policy_checks.py` as `ORIGIN_VERIFY_HEADER`, and in this delta. It carries no pre-existing semantic anywhere.

### 4.3 Where the header may be added

Only as a **CloudFront origin custom header** on the console API origin (`custom_header` inside the `/api/*` origin block). It must **not** be added by a viewer-request or viewer-response CloudFront Function, a WAF rule, an origin-request Lambda@Edge function, a response-headers policy or the application. Reason: the value must never be observable in a viewer-side evaluation context, where WAF logs and function logs could capture it, and it must not depend on application cooperation to be present.

The `/api/*` origin request policy forwards viewer headers (that is how `Authorization` reaches the origin), so a viewer can also present this header name. The documented overwrite rule — CloudFront replaces the value before forwarding when the name is already present — is therefore **load-bearing**: the gate must compare the received value against the expected value and must never treat the presence of a header as verification. OV-11 and OV-12 exercise exactly this, and §12 requires it to be confirmed at runtime rather than assumed.

## 5. The fail-closed decision rule

### 5.1 Inputs

The gate receives the request headers as delivered by the API Gateway HTTP API (payload format 2.0) to the integration, and the expected value from `EDGE_ORIGIN_VERIFY`.

Because HTTP header names are case-insensitive, occurrence detection is case-insensitive; the expected value comparison is **byte-exact and case-sensitive**.

### 5.2 Classification (closed set, exhaustive)

Collect **all** values supplied for any case-insensitive spelling of `X-Origin-Verify`, in delivery order.

| Reason | Condition | Decision |
| --- | --- | --- |
| `ABSENT` | zero occurrences of the header name | REFUSE |
| `EMPTY` | exactly one occurrence whose value is empty or consists only of optional whitespace | REFUSE |
| `DUPLICATE` | more than one occurrence, **or** one occurrence whose value contains a comma (the HTTP API v2 form of coalesced duplicate headers), **or** more than one map entry differing only in case | REFUSE |
| `MISMATCH` | exactly one occurrence, non-empty, byte-unequal to the expected value — including a value of correct length differing in one character, or a case-transformed value | REFUSE |
| `VERIFIED` | exactly one occurrence, non-empty, byte-equal to the expected value | CONTINUE |

No other outcome exists. The rule is evaluated before any business action (§6) and refuses on `ABSENT`, `EMPTY`, `DUPLICATE` and `MISMATCH` — there is no partial acceptance, no "log and continue", no fallback, and no configuration flag that relaxes it.

**Comparison discipline.** Constant-time comparison over the full value; a length difference fails without a content comparison. No trimming beyond transport-level optional-whitespace handling, no case folding of the value, no percent-decoding, no normalization. The value alphabet is ASCII by construction (§8.1), so no character-set ambiguity is permitted.

**Whitespace around the value.** If the deployed transport preserves leading or trailing whitespace, the value is `MISMATCH` unless the transport has already removed it as optional whitespace. Implementation must not trim to be permissive; the correct classification of a whitespace-padded value of otherwise correct content is `MISMATCH` (it is not `EMPTY`, because the non-whitespace content is present).

**Duplicated `X-Origin-Verify` values that differ only in case of the header *name* are still duplicates.** A caller cannot defeat the single-value rule by spelling the name twice.

### 5.3 Expected-value sourcing

The expected value is read once at initialization from `EDGE_ORIGIN_VERIFY`. The gate refuses to operate with a missing, empty, malformed or defaulted value:

| Configuration state at initialization | Required behaviour |
| --- | --- |
| `EDGE_ORIGIN_VERIFY` absent, empty, shorter than the minimum length, or containing a character outside the fixed alphabet | **Fail initialization.** The function must not serve any request. The integration returns a platform 5xx; the initialization error is alertable. Never default to "allow", never default to a placeholder, never start in a degraded mode. |
| Valid value | Gate active for every request. |

A stack that deploys this control with no value is a defect that must fail loudly at the first invocation, not silently open the API.

### 5.4 Interaction with the JWT authorizer, stated precisely

The gate is **independent of** the authorizer, and the platform ordering is **not** changed:

1. The API Gateway JWT authorizer runs first, because it is a platform authorizer attached to the route. A request without a valid token is refused there, with the platform's own 401 body. That body is not this delta's and must not be rewritten to imitate it.
2. The gate therefore observes only requests that already carry a token the platform accepted. Its decision is derived solely from the header and the configured value: it never reads `requestContext.authorizer`, never consumes the verified principal, and produces the same outcome whether the principal would later be authorized or refused for business reasons.
3. The gate remains effective **if the route's authorization were changed or a new unauthorized route were added** under the same integration: such a request without the injected header is still refused, so removing the authorizer cannot silently expose direct callers.
4. The gate is **not an authentication factor** and must never be described, logged or tested as one. It does not replace the authorizer, does not identify a subject, and a request bearing a correct value is not thereby authenticated. Both controls are mandatory; either alone is insufficient.
5. Recorded residual: the gate does not remove the platform authorizer-evaluation cost on the direct path for callers who present a token. It removes business action, data access and disclosure.

## 6. Pipeline placement

### 6.1 Deployed pipeline (unchanged platform steps, one new application step)

1. Viewer → CloudFront (TLS, WAF, rate bound).
2. CloudFront `/api/*` ordered cache behaviour, caching disabled, origin request to `api_fqdn` with `X-Origin-Verify` injected by the origin custom header.
3. Regional custom domain (`api_fqdn`, TLS 1.2+), route `ANY /api/{proxy+}`, platform JWT authorizer → platform 401 on failure.
4. Lambda integration.
5. **Origin-verification gate — the first application step.** It runs before: route dispatch, method and path matching (including the existing 404/405 handling), any body read or size check, media-type validation, the credential seam, the rate-limit hook, the context registry read, any service call, and any log line that carries request data.
6. Existing console route order, unchanged from `ConsoleAuthorizationRoute`: method/path → credential seam → rate-limit hook → media type → body validation → trusted-context match → exactly one service call.

The gate is a **required collaborator** of the integration entry point, in the same sense as the required authorizer (`ConsoleAuthorizer` has no anonymous or "trusted header" constructor): construction without it must fail, and no request-data, environment variable or feature flag may disable it at runtime. This is the "no bypass switch" property the four conditions depend on; it is asserted by unit test and code review (`OV-GATE-REQUIRED`, §9.2), not by Terraform policy.

### 6.2 Why first, and what "before any business action" excludes

A refused request must produce no claim, no dispatch intent, no event, no journal write, no evidence admission, no send, no context disclosure and no rate-limit consumption. Positioning the gate before the route's own 404/405 handling also means a refusal cannot be distinguished as "valid path, wrong origin" versus "unknown path": every request on the integration gets the same boundary answer when the value is wrong.

### 6.3 Interaction with N-03 error pass-through

- The distribution has no `custom_error_response` blocks, and the SPA viewer-request function returns `/api` and `/api/*` untouched (rev3 N-03 correction, verified in the reviewed bytes). The gate's 403 therefore reaches the viewer **with its status and body unchanged**, like any other API 4xx.
- The `/api/*` behaviour keeps a caching-disabled cache policy. A denial is never cached and never served to a different viewer. Enabling caching on `/api/*` while this control is relied on is a defect.
- The gate changes no other response: the platform 401 (§5.4) and the app's business refusals, including the existing `403 CONTEXT_DENIED` C3 envelope, keep their exact status and body. The boundary body must not bleed into business denials, and the C3 envelope must not be used for the boundary refusal.
- Evidence for byte-comparison must record the decoded body and the observed `Content-Encoding`; CloudFront may compress, and compression is not a body change.

## 7. Response contract for a refusal

**Status.** `403`.

**Body.** Exactly these bytes, UTF-8, compact, no trailing newline, fixed key order:

```json
{"version":1,"error":"EDGE_ORIGIN_NOT_VERIFIED","action":null}
```

**Headers.** The origin response carries `Content-Type: application/json` and no content-related header of its own (framing headers the runtime adds, such as `Content-Length`, are not contract content). The edge may add the headers its response-headers policy configures; that policy must not add an `Access-Control-Allow-*` allowance.

**Reuse statement, in three parts.**

1. **Shape reused.** The body reuses the already-contractual boundary error shape from D-6/R-2 — `{version, error, action}` with `action: null` — rather than inventing a new envelope. `action` is `null` because this refusal has no resync instruction; only `410 RESYNC_REQUIRED` carries an action value.
2. **C3 envelope not reused.** The 13-key C3 business envelope is deliberately not used. It enumerates business outcomes and error codes and its bytes are pinned; a transport-boundary refusal before any business action must not fabricate business facts (`authorizationId`, `outcome`, `businessVersion`, attempt identifiers) and must not add a code to a closed C3 enumeration. Pre-route responses that are not C3 envelopes already exist (the existing empty 404/405), so a non-C3 boundary response is not a new class of response; only the versioned error shape is borrowed, and it is borrowed from D-6/R-2, not from C3.
3. **Platform bodies untouched.** The API Gateway authorizer's 401 body stays the platform's; this delta neither reproduces nor replaces it.

**One code for all four reasons.** `ABSENT`, `EMPTY`, `DUPLICATE` and `MISMATCH` produce byte-identical responses. The response must not echo the received value, its length, a prefix, a hash, a character diff, the reason class, or the fact that a header was present at all. There is no `Retry-After`, no `WWW-Authenticate`, no CORS allowance and no correlation hint. The reason class exists only in the server-side denial record (§10).

**Why 403 and not another status.** Not 401: no credential judgment is made, and a request carrying a valid token receives the same answer. Not 404: the refusal is a deliberate, observable control, the caller already knows which host it addressed, and a stable status is required by the retest criteria. Not 5xx: the request is refused, not the service failed.

## 8. Custody rules

### 8.1 Generation and form

- Exactly **one** Terraform-generated value for this control, created in the stack root (`envs/stack`), not inside a reusable module and not per environment file. A module-internal resource would be regenerated per instantiation and would silently produce diverging values.
- The generated value must carry **at least 256 bits of entropy**, be a **fixed length**, and use a **closed RFC 7230 token-safe alphabet** with no whitespace, no control characters and no punctuation requiring quoting. The authored `random_password` (length 48, `special = false`, mixed case and digits) satisfies this; an equivalent `random_bytes`/hex form of the same entropy would also satisfy it. Any change to length, alphabet or resource identity is a contract change, not a tuning change, because the application compares exactly.
- The value must be marked sensitive wherever Terraform supports it, and must never acquire a `default` in any variable declaration.

### 8.2 Storage — what "state only" means in this topology

The **durable** copy is the encrypted remote Terraform state: S3 backend with `encrypt = true`, a KMS CMK, TLS-only and unencrypted-upload-denying bucket policy, state locking, and no local or committed state (`*.tfstate*` must remain ignored).

Two **provider-owned runtime copies are unavoidable** by construction of the mechanism and are therefore not violations, but they are bounded and recorded:

1. the CloudFront origin configuration (`custom_header` value), readable by any principal holding `cloudfront:GetDistributionConfig`;
2. the console Lambda environment (`EDGE_ORIGIN_VERIFY`), readable by any principal holding `lambda:GetFunctionConfiguration` / `lambda:GetFunction`.

The bounding control is IAM least privilege on both actions for the console API resources, restricted to the deployment role and break-glass access. This is an honest statement of the residual: a principal that can read the distribution configuration or the function configuration can read the value, and no design within Option B removes that.

### 8.3 Exclusions — mandatory, asserted

The value must never appear in: any `*.tfvars` or `*.auto.tfvars`; any CI variable, workflow `env:` block, repository/environment secret, or pipeline log; any Terraform output or `terraform output`; any plan file, uploaded artifact, Lambda deployment package, container image or SBOM; any document, ticket, chat or dashboard; and any log format, including:

- **CloudFront** standard and real-time logs — no field may carry request headers;
- **API Gateway** access logs — the format remains the closed explicit field list, with no `$context.request.header.*` field of any kind;
- **WAF** logs — the value is added at the origin, after WAF evaluation, so WAF never observes it; the header name is redacted anyway as defence in depth against a future forwarding change;
- **application/Lambda logs and error paths** — the header name is on the redaction deny-list, and no log statement may include the received value, its length, a hash, a prefix or a comparison result;
- **ADOT/OpenTelemetry** traces, metrics and span attributes — request headers must not be captured;
- **response bodies** — never echoed (§7).

### 8.4 Rotation

No automatic rotation is specified, and none is claimed. The value is a shared secret between two provider configurations; a rotation is a coordinated change that must update the distribution and the function together. Between the two updates every request fails closed, i.e. the console API is unavailable — that is the correct failure direction, and it must be stated in the runbook rather than discovered. Rotation evidence does not exist in this packet and Security's rotation finding (C-02) is untouched by it.

## 9. Policy assertions expected

### 9.1 Terraform policy check 18 — already authored, with named gaps

`tools/policy_checks.py` already carries check 18 at the reviewed bytes. This delta **confirms** what it asserts and **names what it must additionally assert**; it does not ask for a rewrite of what is already correct. Each assertion must fail on a synthetic violating tree (mutation-tested both directions, as check 17 already is).

| Sub-check | Status | Assertion |
| --- | --- | --- |
| `origin-verify-name` | authored | The fixed local equals `X-Origin-Verify`, is not in the CloudFront denylist and does not use a reserved prefix. |
| `origin-verify-denied-name` | authored | Denylist and reserved-prefix classes are rejected explicitly. |
| `origin-verify-header` / `origin-verify-header-name` | authored | Exactly one `custom_header` on the custom origin, name drawn from the fixed local. |
| `origin-verify-value-source` / `origin-verify-not-generated` | authored | The value expression is `var.origin_verify_secret`, fed from `random_password.origin_verify.result` in the stack root. |
| `origin-verify-entropy` / `origin-verify-generator` | authored | `length >= 32`, `special = false`, exactly one adequate generator. |
| `origin-verify-sensitive` / `origin-verify-default` / `origin-verify-output` / `origin-verify-literal` / `origin-verify-ci` | authored | Sensitive, no default, no output, no literal assignment, no workflow reference. |
| `origin-verify-waf-redaction` | authored | WAF logging redacts `x-origin-verify`. |
| access-log formats | authored | The closed field list rejects the header name and any request-header field. |
| `origin-verify-env` | **required addition** | `modules/api` declares the expected-value variable as `sensitive`, the console Lambda environment maps `EDGE_ORIGIN_VERIFY` from the same root resource, and no second instantiation produces an independent value. Cannot pass until the wiring exists (§5.3, §8). |
| `origin-verify-cache-disabled` | **required addition** | The `/api/*` ordered cache behaviour keeps a caching-disabled cache policy. |
| `origin-verify-single-origin` | **required addition** | Exactly one CloudFront origin in the stack resolves `api_fqdn`. |
| `origin-verify-not-in-function` | **required addition** | The header name appears in no CloudFront Function source, so the value cannot be added at a viewer-side evaluation point (§4.3). |

**Reporting defect in the authored check (reported, not fixed here).** Check 18 prints its PASS line only when the global `FAILURES` list is empty, unlike the other checks that call `ok()` unconditionally. An unrelated violation therefore hides the N-02 pass line and can be misread as "check 18 did not run". The exit code is unaffected; the reporting is. It must be corrected when check 18 gains the four additions above.

**Pin drift.** The Terraform tree was being edited concurrently while this delta was authored. The "authored" column states what check 18 asserts at the bytes pinned in `INPUTS.SHA256SUMS`; a reviewer must re-pin and re-confirm those claims against the bytes it actually reads. `RUN.md` records the observed drift.

### 9.2 Behavioural assertions (test suite, not Terraform)

| Identifier | Assertion |
| --- | --- |
| `OV-GATE-REQUIRED` | The integration entry point cannot be constructed without the gate; no runtime switch, environment variable or request field bypasses it. |
| `OV-NO-ECHO` | For all four refusal reasons, the response bytes equal the §7 constant; no substring of the received value appears in the body or headers. |
| `OV-ZERO-EFFECT` | A refused request performs no claim, dispatch-intent, event, journal, evidence, send, registry read or rate-limit consumption. |
| `OV-AUTHZ-INDEPENDENT` | A correct header does not authenticate: with the platform authorizer absent (harness), the credential seam still refuses; with a wrong header and a valid token, the gate refuses before the seam. |

### 9.3 Scan hygiene

The value must not be discoverable by the repository secret scanners as a literal (it has no literal form), and no fixture may contain a value presented as real. Fixtures use a documentary symbol; test doubles use an injected value, never a value copied from an environment.

## 10. Observability of direct-path denials

Every refusal emits exactly one structured application record, emitted before the response is written:

| Field | Content |
| --- | --- |
| `event` | `console_origin_verification_denied` |
| `reason` | one of `ABSENT`, `EMPTY`, `DUPLICATE`, `MISMATCH` |
| `requestId` | API Gateway request id from the integration event |
| `sourceIp` | source IP as delivered by the platform |
| `host` | request host, length-bounded and validated before logging (it is caller-controlled) |
| `httpMethod`, `path` | method and path, length-bounded |
| `timestamp`, `appVersion` | emission time and deployed application version |

Forbidden in that record, and in every other log line: the received value, the expected value, any prefix, length, hash or comparison artifact of either, the `Authorization` value, and the request body.

- A metric filter turns `console_origin_verification_denied` into the custom metric `ConsoleOriginVerificationDenied`, dimensioned by `reason`.
- **Alarm thresholds are not fixed here.** They are deployment inputs (an owner/DevOps decision); inventing a number would violate the standing prohibition on invented capacities, rates and quotas.
- The denial decision must not depend on logging success: if the log emission fails, the response is still the 403. A logging failure is itself a defect to alert on, not a reason to permit the request.
- **Why the application record is the primary signal.** The API Gateway access log shows a 403 but cannot distinguish this refusal from any other 403; WAF and CloudFront logs see only viewer-side traffic, where the value is present or absent by construction and never injected. Direct-path denials are therefore visible only in the application record, with the access log as corroboration. The N-03-style edge evidence (anonymous `GET /api/…` → 401 JSON through CloudFront) remains a separate oracle and is unaffected.

### 10.1 Retest criteria carried from Security (must all pass at the deployment gate)

1. Direct request to the retired `execute-api` host is refused (already true by `disable_execute_api_endpoint`).
2. Direct request to `api_fqdn` with a valid token and no header is refused at the boundary, with logged denial, and the value appears in **no** log format.
3. A forged unauthenticated request through the deployed edge still receives the exact expected error body (N-03 semantics) and is not rewritten to `index.html`.
4. Authenticated direct-path traffic no longer reaches any business action or disclosure.
5. The value is absent from CloudFront, WAF, API Gateway, application and ADOT outputs after a read-back.
6. The metric is emitted on a denial and is visible without the value.

## 11. Given/When/Then cases

Documentary oracles, not execution results. Each case runs in isolated state. Except for the OV-01 positive control, every case asserts: `tokensInstalled=0`, `claims=0`, `events=0`, `journalWrites=0`, `evidenceAdmissions=0`, `financialSends=0`, `automaticPaymentPosts=0`, `contextReads=0`, `rateLimitConsumptions=0`, and no data disclosure.

| ID | Given | When | Then |
|---|---|---|---|
| OV-01 | Edge path; valid demo token; origin header injected | submit a valid authorization | normal approved path; no denial record; metric unchanged |
| OV-02 | Valid demo token; direct to `api_fqdn` | submit with no `X-Origin-Verify` | 403, exact §7 bytes, reason `ABSENT`, zero effect |
| OV-03 | No token; direct to `api_fqdn` | submit | platform 401 with the platform's own body; gate not reached; **no** denial record — documents that the gate is not an unauthenticated control |
| OV-04 | Valid token; direct | send `X-Origin-Verify:` empty | 403, exact §7 bytes, reason `EMPTY` |
| OV-05 | Valid token; direct | send `X-Origin-Verify:   ` (whitespace only) | 403, reason `EMPTY` |
| OV-06 | Valid token; direct | send two identical header values | 403, reason `DUPLICATE` |
| OV-07 | Valid token; direct | send the correct value first and a wrong value second | 403, reason `DUPLICATE` (first-value reading is forbidden) |
| OV-08 | Valid token; direct | send the payload-v2 comma-coalesced form of two values, the first correct | 403, reason `DUPLICATE` |
| OV-09 | Valid token; direct | send a same-length value differing in one character | 403, reason `MISMATCH` |
| OV-10 | Valid token; direct | send the correct value with changed letter case | 403, reason `MISMATCH` (value comparison is case-sensitive) |
| OV-11 | Edge path; valid token | viewer forges a wrong `X-Origin-Verify` value | CloudFront overwrites it; the request succeeds normally; zero denials — the anti-bypass proof (runtime-verified at deployment) |
| OV-12 | Edge path; valid token | viewer sends two conflicting values of the header | CloudFront forwards one overwritten value; normal behaviour; zero denials |
| OV-13 | Valid token; direct | `GET /console/v1/transactions/{id}/events` with no header | 403 with body bytes identical to OV-02; no page payload, no cursor issued |
| OV-14 | Valid token; direct | refused submission, then inspect ledger and counters | no claim, dispatch intent, event, journal row, evidence admission, send or registry read |
| OV-15 | `EDGE_ORIGIN_VERIFY` unset or empty in the deployed environment | any request | function initialization fails; no request served; a platform 5xx is observed; never a fail-open 2xx |
| OV-16 | Either refusal reason from OV-02/04/06/09 | compare the four responses | byte-identical status, headers and body; no echo of value, length, hash or reason |
| OV-17 | Correct header value; platform authorizer absent (test harness) | submit with no credential | gate passes, credential seam refuses 401; proves the gate is not authentication and that authorizer removal is not an origin bypass |
| OV-18 | Valid token, correct header, wrong `acquirerRef`/`merchantRef` | submit | 403 `CONTEXT_DENIED` in the existing C3 envelope — the boundary body does not leak into business denials |
| OV-19 | Distribution updated to a new value, function still holding the old one | submit through the edge | 403 for every request (fail closed, total console API outage) — rotation never partially accepts |
| OV-20 | Refused request | inspect application record | exactly one `console_origin_verification_denied` record with the closed field set and correct `reason`; no forbidden field; metric emitted |

Cases that are inapplicable, and why: timeout/unknown-outcome cases do not apply (the gate is a pure local comparison with no I/O); duplicate *business* submission cases are untouched and remain governed by C3 idempotency fixtures; reattempt cases do not apply because a refusal is terminal for that request and creates no state to retry.

## 12. What remains unproven until deployment

Nothing in this packet is runtime evidence. The following must be demonstrated at the deployment gate and are **not** established here:

1. That the CloudFront origin custom header is actually present on origin requests at runtime, and that a viewer-supplied same-name header is overwritten (§4.1's documented behaviour, not observed behaviour).
2. That the API Gateway HTTP API forwards the header to the integration unmodified, with the duplicate/coalescing behaviour assumed in §5.2.
3. That no log format anywhere (CloudFront, WAF, API Gateway, Lambda, ADOT) contains the value, verified by read-back of real log records.
4. That the denial metric is emitted and visible, and that the initialization-failure path is alertable.
5. That the coordinated rotation in §8.4 behaves as described and does not partially accept.
6. That the N-03 pass-through remains intact in the presence of the new 403, with the exact status and body observed at the browser.
7. That `terraform plan`/`apply` are clean, that the value is redacted in plan output, and that check 18 fails on a violating tree.
8. That no second path to the console API exists (another origin, another domain mapping, an in-account direct invoke). The lambda invoke surface is a different trust boundary and is out of scope.
9. That the WebSocket adjacent surface (§2.1) is either accepted as-is by the owner or covered by a later delta.
10. Security's SR2-01/02, NV-05/AD-01, D-5 issuer evidence and the public-surface evidence list remain open and are untouched by this delta.

## 13. Items fixed here that are not owner decisions

These are contract mechanics within the authorized option, fixed so the three implementation roles cannot diverge. They are subject to independent review; if a reviewer rejects the header name, the body shape, the status or the placement, the fallback named by Security is Option A (REST API + regional WAF), which is an owner decision, not a specialist's.

| Fix | Rationale | Alternative if rejected |
| --- | --- | --- |
| Header name `X-Origin-Verify` | Denylist-safe, semantically unused, already the authored spelling | Any denylist-safe non-semantic name, changed in all three places at once |
| Env var `EDGE_ORIGIN_VERIFY` | Matches the existing uppercase constant style of the lambda environment | Any single fixed name, recorded here |
| Status 403 + code `EDGE_ORIGIN_NOT_VERIFIED` | Refusal, not authentication failure; unambiguous versus the browser `Origin` concept | A 404-style indistinguishability only if the owner prefers concealment over observability |
| D-6 body shape reuse | No new envelope, no C3 change | A C3 code would require reopening pinned C3 bytes |

## 14. Open items for the owner (non-blocking for this delta's review)

1. **WebSocket direct-path surface** (§2.1): the owner's N-02 decision names the console API. Extending the same control to `ws_fqdn` would need its own delta because the WS token carriage and authorizer ordering differ. Recorded, not decided.
2. **Alarm threshold** for `ConsoleOriginVerificationDenied` (§10): a deployment input; no number is invented here.
3. **Rotation runbook ownership**: coordinated manual rotation is assumed; if an automated rotation is wanted, it is a DevOps design item.
