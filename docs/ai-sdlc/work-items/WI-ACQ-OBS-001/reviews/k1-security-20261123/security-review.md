# Independent Security Review — K1 authentication slice (2026-11-23)

## 1. Review metadata

- Reviewer: ai-sdlc-security (independent). Date: 2026-11-23 (America/Bogota).
- Commission: implementation-level Security review of the K1 slice (real compact-JWS decoder,
  JWKS key-source seam, composition into the unchanged claim-policy core), per the coordinator's
  subagent task. This review session: `agent:ai-sdlc-security:subagent:de927807-93c5-47ac-9db9-c8fba12271c5`.
- **Observed runtime model: `nan/glm5.3-flash` (the agent's configured default; no override, no
  fallback event observed).** Provider-qualified identifier is not exposed by the runtime; none was
  invented. This matches the model recorded by the prior bounded reviews of 2026-11-23.
- Writes confined to `reviews/k1-security-20261123/` (`PROGRESS.md`, this report, `SHA256SUMS`).
  No reviewed input, production file, other specialist's artifact or work-item record was modified.

## 2. Scope, revision binding and inputs

**Reviewed bytes:** the working tree of `/home/pazfernando/projects/acquiring-simulator`, branch
`feat/wi-acq-auth-001`, HEAD `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` (dirty by design with
pre-existing continuation work). This binding is taken from the implementer's evidence
(`evidence/continuation-k1-20261123/PROGRESS.md` §1) and the standing QA/Security binding record;
**this review executed no Git command** per commission restriction, so the HEAD is a recorded
binding, not a freshly attested one. All K1 conclusions attach to the working-file bytes listed
below, which I re-hashed myself.

**Inputs read in full or in targeted part:**

- Role contract: shared `README.md`, `PROJECT_EXECUTION.md`, `VERIFICATION_DELIVERY.md`; glossary
  terms via role documents.
- Work item `WORK_ITEM.md` (K1-relevant entries: normative adoption, N-02 re-review/implementation,
  H1 QA + composition + re-verification, D-6 application/adoption).
- Adopted contracts: `design/contract-deltas/continuation-rev3-20261123/contracts.md` (D-5 exact
  replacement text), `inherited-candidate.md` section D-2, `browser-flow.md` (auxiliary-context
  flow — read-only; no browser/issuer execution performed), `design/contract-deltas/
  n02-origin-verification-20261123/contract-delta.md` (fail-closed boundary rule and its
  interaction with the JWT authorizer).
- Prior reviews: `reviews/continuation-rev3-security-20261123/security-review.md` (full), the
  N-02 re-review and implementation-review verdicts, H1 QA reports.
- K1 evidence: `evidence/continuation-k1-20261123/RESULTS.md`, `PROGRESS.md`, `runs/*` (3 exit
  files, each `0`; stdout/stderr read where non-empty).

**Input integrity gate (recomputed by me before substantive review):** all 13 SHA-256 digests
recorded in K1 `RESULTS.md` §1 for the jwt package sources and tests were recomputed against the
working tree — **13/13 OK**. The green-run evidence therefore describes the bytes I reviewed.

**Actual commands:** `sha256sum` recomputation (via a Python one-liner over the manifest), `grep`,
`sed`, `head`, `wc`, directory listings, full/targeted file reads. No Maven run, no test execution,
no Git/provider command, no cloud/issuer/browser execution, no credential flow, no code edit, no
onward delegation. The implementer's recorded runs (compile exit 0; 32 decoder/configuration tests;
5 composition tests; 90 focused console suites — **127 tests, 0 failures/errors/skipped**) were
read as recorded developer evidence and corroborated byte-wise by reading every asserted test body;
they were not re-executed by me.

## 3. Actors, assets and trust boundaries in this slice

- **Actors:** the single demo subject via the public `console-demo` OIDC client (D-5); the
  self-managed Keycloak issuer as token authority; an anonymous or malicious bearer-token presenter
  (the adversarial actor for this slice); the deployed console Lambda (future consumer of this
  code); the local dev harness operator (loopback only).
- **Assets:** the console HTTP API; the synthetic mandate/context registry; the JWKS endpoint of
  the pinned issuer; the cursor HMAC key (A-4 sourcing still open, unchanged); the demo token
  itself (bearer credential, ≤900 s, single resource class).
- **Boundaries touched by K1:** browser↔console API (bearer token verification — this slice);
  console↔issuer (JWKS retrieval, `HttpJwksSource`); local dev harness↔browser (stub, loopback).
  K1 adds no new network listener, no new dependency (`pom.xml` unchanged; JDK + Jackson only)
  and no new custody surface. The N-02 origin gate remains the first application step, ahead of
  any credential processing; K1 does not reorder it.

Fixtures and test keys are synthetic-only; keys are generated in the test JVM; no real PAN, PIN,
CVV or live cardholder credential appears anywhere in the reviewed slice.

## 4. Property-by-property verification (in the actual bytes)

Each row states what I verified in source and/or pinned test bytes, not what the summary claims.

| # | Required property | Verdict | Evidence in bytes |
| --- | --- | --- | --- |
| P1 | Signature verification against a configured key set, rotation supported, no pinned single key | **CLOSED (slice scope)** | `JwtConsoleTokenDecoder.resolveKey`: current set first, one `refresh()` on unknown `kid`, refuse `UNKNOWN_KEY` if still absent; a key is never stored across calls. Tests `acceptsARotatedKeyOnlyAfterRefreshingTheKeySet` (1 `current()` + 1 `refresh()` for a new `kid`) and `refusesTheRetiredOldKeyAfterRotation` (refresh performed, old key still refused) pin the behavior with a counting source. No key-pinning path exists: the decoder holds no key field. Residual noted in F-2 (no cache TTL in `HttpJwksSource`). |
| P2 | Unsupported/absent algorithm refused — including `none` and HMAC — **before any key is consulted** | **CLOSED** | Decoder checks `alg` against `configuration.allowedAlgorithms()` immediately after parse and before `resolveKey`; blank/absent → `UNSUPPORTED_ALGORITHM`. `TrustedIssuerConfiguration` refuses any configured algorithm outside `SUPPORTED_ALGORITHMS = {RS256,RS384,RS512}` at construction, so `none`/HMAC can never be configured. Tests `refusesAnUnknownAlgorithmWithoutConsultingAnyKey` (HS256; asserts **zero** `current()`/`refresh()` calls) and `refusesATokenWithNoAlgorithm`. Additionally `crit` headers are refused (`MALFORMED_TOKEN`), closing the critical-header bypass. |
| P3a | Missing `kid` refused | **CLOSED** | Blank/absent `kid` → `UNKNOWN_KEY` before key lookup; test asserts zero source calls. |
| P3b | Signature mismatch and tampered payload refused | **CLOSED** | `verifySignature` over the exact signing input; mismatch → `BAD_SIGNATURE`; `GeneralSecurityException` also → `BAD_SIGNATURE` (fail-closed, never trust). Tests: key-two signature against key-one set; tampered `sub` with valid header/key. |
| P3c | Wrong issuer refused | **CLOSED** | Decoder exact-equality on `iss` (`WRONG_ISSUER`) **and** the unchanged policy core re-checks it. |
| P3d | Wrong audience refused | **CLOSED** | Decoder requires exactly one audience equal to the configured console API; array/non-textual shapes malformed. Test `refusesAWrongAudience`. |
| P3e | Dual-resource audience refused | **CLOSED** | `audience.size() != 1` refuses any multi-audience token; explicit `refusesATokenCarryingBothResourceAudiences`; construction additionally refuses equal required/forbidden audiences (overlapping resource classes cannot be configured). |
| P3f | Expired / not-yet-valid (`nbf`, future `iat`) refused | **CLOSED** | `exp` mandatory (`MALFORMED_TOKEN` if absent), `now.isBefore(exp)` refused; `nbf` and `iat` future-refused; `TOKEN_LIFETIME_EXCEEDED` enforces the D-5 900 s ceiling (`exp-iat ≤ 900 s`, no zero/negative lifetime). Same instant `now` is passed to decoder and policy so they cannot disagree. |
| P3g | Missing scope refused | **CLOSED** | Decoder refuses the forbidden scope (`WRONG_SCOPE`, exercising the WS-scope-on-HTTP-token case) and then requires the resource scope (`MISSING_SCOPE` with `openid` only). |
| P3h | Wrong subject refused | **CLOSED** | The subject is policy-owned by design; `ConsoleTokenCompositionTest.thePolicyCoreStillRefusesASubjectOtherThanTheConfiguredDemoSubject` drives a fully valid signature/claims token with a foreign `sub` through the real decoder + unchanged `DemoConsoleAuthorizer` and asserts refusal before any service call. `azp` is checked before `sub` so a service-client token is refused by class. |
| P4 | No trust in unverified claims anywhere, incl. the cursor issuer binding | **CLOSED** | `claimsOf` is reachable only after `verifySignature` succeeds; `DemoConsoleAuthorizer` re-derives every identity check from the decoder-returned view and never from raw request data. The cursor path (`ConsoleHistoryRoute.continuation`) opens the HMAC-signed cursor and compares `binding.issuer()` against `principal.issuer()` — the **verified** token issuer — plus subject/azp/audience/environment/transaction/context triple, before any store read (QA-DEF-1 fix verified in bytes). The cursor codec itself never feeds a client-asserted field into authorization. |
| P5 | Token in memory only; no storage, URL or cookie path | **CLOSED (application side)** | `CompactJws` retains only decoded header/payload/signing-input/signature inside one call; `CompactJws.parse`'s input string is not retained; the token never enters an exception message (`refused(reason)` is a fixed label); `toString()` of decoder and claims excludes the token (test-pinned); response body excludes the token (composition test). `grep` over the whole console package finds **no** cookie, `localStorage`, `sessionStorage` or URL-token code; the only accepted credential location is the `Authorization: Bearer` header, with duplicate headers refused. SPA-side custody is governed by `browser-flow.md` and remains a separate gate (not in this slice). |
| P6 | No combined payment-capable socket token introduced | **CLOSED** | One token class is modeled (`TrustedIssuerConfiguration` documents the HTTP console class); the WS class is refused via `forbiddenScope`/`forbiddenAudience`; the WS authorizer/token remains unimplemented (out of slice). No socket-token type, no `tokenClass` mixing, no relay principal exists in the new package. |
| P7 | Dev stub unreachable from the production composition; not enable-able by configuration mistake | **CLOSED (with deployment-gate condition)** | Production wiring is `ConsoleHandler` → `ConsoleRuntime.compose` → `ConsoleIntegrationEntryPoint`, whose application recognizes the two route shapes and answers `503` — it instantiates **neither** the real decoder nor the stub. `grep` confirms `DevStubTokenIssuer`/`ConsoleDevServer`/`ConsoleDevConfig`/`ConsoleDevHarness` are referenced only inside `console/dev/` (and a documentation comment in `ConsoleTokenDecoder`). The dev harness is a local `main` (loopback server, `ConsoleDevConfig` with a non-resolvable `.invalid` default issuer and `CONSOLE_DEV_*` variables read only by `fromArgs`); no production environment variable, constructor or request field selects it, and the authored Terraform pins the handler to `ConsoleHandler::handleRequest` (whose `main(String[])` signature the stub harness does not share — a handler mis-point to the harness class fails rather than serving). Condition: the deployment gate must re-pin and re-verify this (D-G7 below). |

**Fail-closed ordering** (parse → algorithm → `crit` → `kid` → key lookup → signature → claims) is
implemented exactly as documented in the class contract, and every unexpected runtime failure in
key retrieval or decoding maps to a refusal (`KEY_SOURCE_UNAVAILABLE` / `MALFORMED_TOKEN`), never
to a partial identity. `load()` also converts a `null` key-set return into a refusal.

## 5. Findings

No BLOCKER and no confirmed IMPORTANT defect was found in the reviewed bytes. The following are
advisory, each with mitigation and retest criteria.

### F-1 — JWKS-unavailable surfaces as `401 UNAUTHENTICATED`, not a distinct `503` — ADVISORY; **acceptable for the demo threat model**

`ConsoleAuthorizationRoute.denialOf` maps `KEY_SOURCE_UNAVAILABLE` to `Denial.UNAUTHENTICATED`
(401). The D-6 contract pins `503` only for the **authorization registry** outage ("unavailable
authorization registry = 503 with no page payload"); D-5 requires merely that an unavailable key
source "fails closed", which the 401 satisfies, and the response body is the uniform C3
denial envelope with no reason differentiation, so no authentication-oracle is leaked.

**Decision:** accept `401` for this slice. A truthful `503` remains the better operational shape
(distinguishes "your token is bad" from "the issuer is unreachable", guides client retry, and
keeps alarm semantics honest), so it is routed as a route-layer refinement, **not** a defect of
K1, and it must not delay composition of the deployed routes. Retest criteria at the deployment
gate: (a) with the JWKS endpoint refusing/down, a request with a well-formed token receives the
denial with no business call (service-call count 0, already unit-pinned) and the denial record
count matches; (b) if the 503 refinement is adopted, the exact `AUTHORIZATION_UNAVAILABLE` body
fixture bytes are emitted and the existing 401 taxonomy is unchanged for genuine token failures.

### F-2 — `HttpJwksSource` has no cache expiry and refreshes only on an unknown `kid` — ADVISORY; **acceptable for the demo threat model**

`current()` serves the cached set indefinitely; a key retired at the issuer remains locally
accepted until the first token naming an unknown `kid` (i.e., the first post-rotation token) or a
process restart. With rotation, the new `kid` is unknown by construction, so the stale window
closes at the first token signed by the new key; residual exposure is bounded by token lifetimes
(≤900 s) and the single demo subject. This is standard JWKS-caching behavior, not a trust
widening, and the decoder never pins a key itself. Retest criteria at the deployment gate:
pinned-Keycloak rotation drill (mint token under key A, rotate to key B, prove B accepted after
one refresh and A refused; record `refresh()` counts from logs/metrics if available); confirm the
issuer outage-after-cache behavior (cached keys keep verifying) is an explicitly understood
property, not an assumption.

### F-3 — `HttpJwksSource` and `JwksParser` compile with zero test coverage — **does not block this slice; becomes mandatory evidence at the composition/deployment gate**

These are the only production key-source paths and they are unexercised: the implementer
disclosed it, and my test inventory confirms it (every decoder test injects the in-memory
counting double; no test references `HttpJwksSource` or `JwksParser`).

**Decision:** **not a blocker for K1 as delivered**, for two reasons: (1) the slice's verified
security properties live in `JwtConsoleTokenDecoder`/`TrustedIssuerConfiguration`, which are
fully covered, and the key-source seam is an interface whose failure contract (`JwksSourceException`
→ refusal) is pinned; (2) no deployed composition instantiates the real decoder yet — the Lambda
routes still answer `503` — so no unverified path is reachable in production bytes today. Absence
of this evidence is **not** a clean pass for deployment: before any route composes
`ConsoleTokenComposition.authorizer(configuration, clock)` (the `HttpJwksSource`-creating
overload), the following named retest criteria are mandatory at the deployment gate:
- `JwksParser` unit tests: non-JSON / no-`keys` documents hard-fail; non-RSA and `use!=sig` keys
  skipped; missing `kid`/`n`/`e` skipped; duplicate `kid` hard-fails; RSA key <2048 bits
  hard-fails; bad base64url hard-fails; a well-formed two-key document parses both.
- `HttpJwksSource` behavior tests (loopback server or injectable transport): redirect never
  followed (fail, no trust transfer); non-200 fails; >64 KiB body fails; read timeout fails;
  malformed parsed body propagates as refusal; `current()` serves the last good cache; a parsed
  successful refresh atomically replaces the cache.
- Live evidence on the pinned Keycloak release: real JWKS fetch over the deployed HTTPS path, one
  real issued token verified end-to-end, and the F-2 rotation drill (C-03 issuer evidence item).

### F-4 — RSA-only algorithm allowlist — **adequate; recorded limitation, not a defect**

`SUPPORTED_ALGORITHMS = {RS256, RS384, RS512}`; `TrustedIssuerConfiguration` refuses any other
configured algorithm at construction (so EC/EdDSA cannot silently degrade — the runtime fails to
start instead), and `JwksParser` accepts only `kty=RSA` with ≥2048-bit keys, so an HMAC
symmetric-secret confusion is structurally impossible (only `RSAPublicKey` reaches the verifier).
The pinned self-managed Keycloak realm is RSA; ES256/EdDSA support is not required by any adopted
clause. **Decision:** adequate for the demo threat model. Requirement carried forward: if the
issuer is ever reconfigured for EC keys, the correct outcome is a construction failure and a
deliberate scope decision, never a silent key-type acceptance. Retest criterion: configuration
negative test at deployment (attempting a non-RSA `allowedAlgorithms` value fails startup).

### F-5 — Denial-reason consolidation is information-safe — observation, no action

Multiple refusal reasons consolidate into a single uniform 401 envelope without reason-specific
bodies or timing-shaped differences beyond the unavoidable path lengths; no token, issuer
response or key material appears in any error text (`JwksSourceException` messages are fixed
labels). No finding.

## 6. Decisions on the implementer's three open points (requested by commission)

1. **JWKS-unavailable as 401:** **accepted for this slice** — see F-1. Compliant with the adopted
   fail-closed requirement; 503 refinement routed as a non-gating route-layer follow-up with named
   retest criteria.
2. **Retained dev stub:** **safe as wired; keep it, under one deployment-gate condition.** The
   stub is referenced only from `console/dev/`, is a loopback `main`-launched harness with a
   `.invalid` default issuer, is excluded from every production composition path, cannot be
   selected by any production environment variable, and is documented as "NOT authentication" in
   the seam it implements. The condition: the deployment gate re-verifies that the Lambda handler
   remains pinned to `ConsoleHandler::handleRequest` and that no `CONSOLE_DEV_*` variable is set
   in any deployed function configuration (D-G7). No production wiring change is requested.
3. **RSA-only allowlist:** **adequate** — see F-4. Fails closed on any configuration outside RSA;
   no adopted clause requires EC/EdDSA.

## 7. Prior findings — status in this review

| Prior item | Status | Basis |
| --- | --- | --- |
| H1 QA-DEF-1 (LOW: cursor issuer binding) | **CLOSED (verified in bytes)** | `ConsoleHistoryCursorCodec` always writes/requires `issuer`; the route compares it against the verified principal's issuer before disclosure; QA re-verification previously confirmed; re-confirmed here independently. |
| N-02 implementation A-2 (no composition instantiates the gate) | **CLOSED (by the H1 composition slice, unchanged here)** | `ConsoleRuntime` composes the gate for the deployed handler; not re-litigated in K1. |
| N-02 implementation A-3 (provisional `INVALID_CURSOR` mapping) | **NOT CLOSED — unchanged** | Domain/requirements confirmation still pending; not a K1 item. |
| N-02 implementation A-4 (cursor key sourcing) | **NOT CLOSED — unchanged** | The codec still takes an injected HMAC key; the deployed composition does not supply it. Explicitly out of K1's slice. |
| Overall implementation-level Security gate (BLOCKED evidence-gated) | **UNCHANGED** | SR2-01/02 executed IAM negatives, C-02 rotation evidence, C-03 issuer ten-item list, C-04 public-surface list, and scans remain outstanding; K1 real-issuer verification is now additionally required (§8). |
| Standing Security verdict FAIL (rev3 design/authoring) | **UNCHANGED in scope** — this slice does not alter the N-02 architecture disposition; the implementation-level gate stands BLOCKED, not FAIL-on-defect. | No new IaC or contract bytes were reviewed here. |

## 8. What remains a deployment-gate (or later-gate) retest — explicit list

None of the following is asserted as passed by this review, and their absence blocks the overall
implementation PASS, not this slice-level verdict:

- **D-G1:** `JwksParser` + `HttpJwksSource` test coverage and live pinned-Keycloak JWKS fetch (F-3).
- **D-G2:** real issuer round trip: realm/client configuration per D-5, two real tokens (HTTP and
  WS classes) verified end-to-end through the composed route; negative grant/scope/audience tests.
- **D-G3:** deployed Lambda composition wiring the real decoder behind the N-02 gate (today the
  routes answer 503); end-to-end 401/403/404/503 bodies through the edge (N-03 evidence).
- **D-G4:** key-rotation drill on the pinned issuer (F-2) and issuer outage behavior (F-1).
- **D-G5:** auxiliary-browser flow (browser-flow.md) with the full real-browser negative list —
  explicitly not executed by this review; no browser or issuer was launched.
- **D-G6:** standing C-01..C-04 evidence (IAM negatives, rotation evidence, issuer feasibility
  list, public-surface list) and DevOps scan evidence (Trivy/gitleaks/CodeQL remain
  configured-not-demonstrated).
- **D-G7:** deployment config negatives: handler pin to `ConsoleHandler::handleRequest`, no
  `CONSOLE_DEV_*` variables, no dev-harness class as a handler.

## 9. Verdict (PASS / FAIL / BLOCKED)

**PASS for the K1 slice, bounded exactly to the delivered authentication slice and the reviewed
bytes; the overall implementation-level Security gate remains BLOCKED (evidence-gated), unchanged.**

Rationale: every property the commission required of this slice was verified in the actual source
and pinned test bytes (§4); no BLOCKER or IMPORTANT finding exists in this scope; the three open
points are decided (§6) and the disclosed coverage gap is judged non-blocking for the slice with
mandatory named retest criteria (F-3). This PASS certifies nothing beyond K1: it does not close
N-02, does not certify a deployable authentication path (the deployed composition still does not
authenticate — that is D-G3), does not replace issuer/browser evidence (D-G2/D-G5), and does not
authorize deployment, publication or risk acceptance. Human acceptance and the standing gates
remain with the coordinator and Fernando.

## 10. Limitations

- **Static review of exact bytes.** No Git command, no Maven/test execution, no cloud, issuer,
  browser or network execution, no credential flow. Test results (127 tests green) are the
  implementer's recorded runs, corroborated by hash-verified byte identity of every test file and
  by my direct read of each asserted case; they are not re-executed evidence.
- The branch/HEAD binding is the recorded one from the implementer's evidence and prior reviews;
  a fresh attestation requires the Coordinator/Implementation Git route.
- Model identity is the runtime-reported `nan/glm5.3-flash`; no provider-qualified identifier is
  available to this session and none was invented. No fallback model event occurred.
- Output inventory: `PROGRESS.md`, this `security-review.md`, `SHA256SUMS` (excluding its own
  digest) — the only writes, confined to `reviews/k1-security-20261123/`.
