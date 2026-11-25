# Independent QA report — K1 construction slice (real issuer decoder, JWKS seam, composition)

- Reviewer: ai-sdlc-qa (independent verification; implementer records were never trusted as
  evidence). Date: 2026-11-23.
- Runtime/model observed: OpenClaw subagent, provider `nan`, model `glm5.3-flash`, no override
  (`execution-model.json`).
- **Verdict: PASS for slice K1 at this revision** — every mandatory scoped check was executed by
  QA and passed; the three disclosed gaps are independently assessed below as acceptable for this
  slice with named deployment retests (§7). This PASS covers the K1 slice only, on a dirty
  uncommitted working tree at HEAD `5bbd4eb`; it is not deployed-issuer, provider, Security,
  human-acceptance or release evidence, and no integrated end-to-end payment exists (§8).
- Scope: construction-handoff K1 (real compact-JWS decoder, JWKS key-source seam, composition into
  the unchanged `DemoConsoleAuthorizer` claim-policy core) against the adopted contracts
  D-2/D-5/browser-flow.md. H1/N-02 criteria and the WS token class/browser auxiliary flow are out
  of scope for this report (browser-flow remains gated by its own feasibility review).

## 1. Binding re-attestation (read-only; re-verified by QA, not taken from implementer records)

| Item | QA-observed value |
| --- | --- |
| Checkout realpath / git toplevel | `/home/pazfernando/projects/acquiring-simulator` (both) |
| Branch / HEAD | `feat/wi-acq-auth-001` / `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` (matches the implementer pin; no commit was made in the slice) |
| Working tree | dirty by design (62 porcelain entries at QA time vs 61 recorded at slice exit; the delta is this review's own new directory `reviews/k1-qa-20261123/` plus coordinator records; no QA mutation outside this review directory and no Git write/stage/commit/push) |
| Implementation evidence | `evidence/continuation-k1-20261123/RESULTS.md` SHA-256 re-verified = `ae3a8193a2db5f961f6f63be92ea4cedbbcbe55b00c2b6ecfa2cefc157c0ff7f` (matches the commissioned pin); `PROGRESS.md` `63bd84e0…301f` |
| K1 package byte identity | all 13 `src/{main,test}/java/com/acquiring/simulator/console/jwt/*.java` files re-hashed by QA; every digest matches the table in `RESULTS.md` §1 — the code executed by QA is byte-identical to the code the implementer executed |
| Contracts read | `design/contract-deltas/continuation-rev3-20261123/contracts.md` (D-5 exact replacement + D-6/R-2), `inherited-candidate.md` (D-2), `browser-flow.md`, `construction-handoff.md` (K1 interfaces) |

## 2. Expected behaviour derived from contracts BEFORE reading test assertions

The full derived matrix is preserved in `qa-expected-matrix.md` (written before
`JwtConsoleTokenDecoderTest`/`ConsoleTokenCompositionTest`/`TrustedIssuerConfigurationTest` were
read). Contract-derived obligations: verified signature/JWKS/algorithm/kid; exact environment
issuer; resource audience exactly the configured console API with no WS audience; `console.demo`
and no `ws.observe`; azp=console-demo; sub=configured demo subject; valid NumericDates and lifetime
≤ 900 s; disjoint resource classes refused, never narrowed; rotation via key-set refresh, never a
pinned key; fail-closed on unsupported issuer/key/claim and unavailable key source; no claim read
before signature verification; token never persisted/logged/echoed; dev stub unreachable from the
production composition; credential refusals must stop before any business action.

## 3. Executed checks and raw results (all counts are QA's own)

Environment: Temurin JDK 25.0.5+8, Apache Maven 3.10.1 (offline `-o`), host `fernando-OC`,
`log-01-toolchain.txt`. No network egress, no cloud, no issuer, no browser, no Docker other than
the pinned DynamoDB Local 2.6.1 used by the pre-existing history integration test suite.

| # | Command | Exit | QA-observed result | Log |
| --- | --- | --- | --- | --- |
| 1 | `mvn -o -q -DskipTests compile` | 0 | no stdout/stderr | `log-02-compile.*` |
| 2 | `mvn -o test -Dtest='JwtConsoleTokenDecoderTest,TrustedIssuerConfigurationTest,ConsoleTokenCompositionTest,ConsoleHistoryRouteTest,ConsoleHistoryRouteDynamoIntegrationTest,ConsoleOriginVerificationGateTest,ConsoleAuthorizationRouteTest,ConsoleSubmissionAtomicIntegrationTest,ConsoleHandlerCompositionTest,ConsoleDevServerTest,DevStubTokenIssuerTest' -DfailIfNoTests=true` | 0 | **127 tests, 0 failures, 0 errors, 0 skipped** — per class: decoder 25, config 7, composition 5, history unit 40, history Dynamo integration 4, origin gate 9, auth route 13, atomic submission 3, handler composition 5, dev server 9, dev stub 7 | `log-03-focused-suites.*` |
| 3 | QA driver `tools/QaK1Driver.java` — QA-owned, independent keys/tokens, compiled with `javac` against `target/classes` and run directly | exit 1 after completion only because the QA fixture HTTP executor thread is non-daemon (annotated in the log); all checks finished | **94 checks: 94 PASS, 0 FAIL** | `log-05-qa-driver.txt`, `log-04-driver-compile.stderr` |
| 4 | Static composition greps (main tree) | — | see §6 | `log-06-static-composition.txt` |

Corroboration (not substituted for QA execution): the implementer's raw run artifacts
(`runs/k1-decoder.stdout` 32 tests, `runs/k1-composition.stdout` 5, `runs/k1-console-suites.stdout`
90, all exit files `0`) match their stated counts; QA independently reproduced the same totals in
its own run (127 total).

## 4. Criteria-to-check mapping and results (QA driver `log-05-qa-driver.txt`)

### 4.1 Configuration fails closed at construction (C1–C8)
Explicit values only; blank issuer refused; configured ES256 refused; 901 s lifetime refused above
the 900 s ceiling; identical required/forbidden scopes and audiences refused; non-loopback plain
HTTP `jwksUri` refused; loopback HTTP accepted only for local use. **All PASS.**

### 4.2 Valid token accepted, exact claim shape (D1–D5)
RS256 token with locally generated issuer key accepted; claims carry exactly the verified iss, aud
(one-element list), sub, azp, scopes, iat, nbf, exp; single-string `aud` normalised; lifetime of
exactly 900 s accepted; RS384/RS512 accepted when configured. The authorizer consumes this shape
(F1): the principal carries the verified issuer/subject/azp/audience/scopes. **All PASS.**

### 4.3 Refusal cases (E2.1–E2.37, F4–F11) — each actually refuses with the expected error path
| Contract case | QA result |
| --- | --- |
| absent alg / `none` / genuine-HMAC HS256+HS384+HS512 / ES256 / PS256 | refused `UNSUPPORTED_ALGORITHM` **before any key consult (0 source calls)**; `none` with an empty signature segment refused as a malformed shape — both paths fail closed |
| missing/blank `kid` | refused `UNKNOWN_KEY`, zero key-set lookups |
| signature by a different key than the named `kid` (kid steering) | refused `BAD_SIGNATURE` |
| tampered payload (header/signature bytes intact) | refused `BAD_SIGNATURE` |
| modified header with original signature | refused `BAD_SIGNATURE` |
| wrong issuer / missing issuer (valid signature) | refused `WRONG_ISSUER` |
| wrong single audience; dual-resource `[console,ws]`; `[console,other]`; duplicated audience | refused `WRONG_AUDIENCE` (never narrowed) |
| wrong azp | refused `WRONG_AUTHORIZED_PARTY` |
| missing scope / `openid` only | refused `MISSING_SCOPE` |
| token carrying `ws.observe` | refused `WRONG_SCOPE` (disjoint classes) |
| exp reached; exp absent; future `nbf`; future `iat`; missing `iat` | refused `EXPIRED` / `MALFORMED_TOKEN` / `NOT_YET_VALID` / `NOT_YET_VALID` / `MALFORMED_TOKEN` |
| lifetime 901 s | refused `TOKEN_LIFETIME_EXCEEDED`; zero/negative lifetime co-occurs with expiry and is refused (expiry check fires first — recorded nuance, still fail-closed) |
| malformed compact shapes (wrong segment count, empty segments, non-JSON, oversized >16 KiB, blank, null) | refused `MALFORMED_TOKEN` |
| non-empty `crit` header | refused `MALFORMED_TOKEN` |
| header `jwk`/`jku` attacker material | never selected; attacker-signed token refused `BAD_SIGNATURE` |
| subject other than configured demo (decoder returns claims; policy core decides) | authorizer/route refuse; 401 at the route, service never called (F8/F11) |
| route-level mapping | attacker-signed 401 `UNAUTHENTICATED`; WS-audience 401; WS-scope 403 `CONTEXT_DENIED`; wrong subject 401; expired 401; duplicated Authorization header 401; key-source outage 401 — **service.calls == 0 in every refusal case** (F4–F11) |

### 4.4 Rotation (R1–R5)
New `kid` accepted only after exactly one `current()` + one `refresh()`; retired `kid` refused
after exactly one refresh with no key pinned; rotating the source away from a previously accepted
key refuses its tokens; the still-current key keeps working; every decode re-consults the source
(no per-decoder key cache/pinning). **All PASS.**

### 4.5 Verification cannot be skipped (X1, E2.1–E2.9, E2.32–E2.37)
Seven adversarial inputs (attacker-signed token with unknown kid; `alg:none`; genuine HMAC;
right-kid/wrong-key; corrupted, whitespace-prefixed and uppercased compact tokens) produce no
claims; every accepted claim set in this report was produced only after signature verification
against a key obtained from the configured source. Unexpected key-source failures map to
`KEY_SOURCE_UNAVAILABLE`, never to trust (K1–K3). **All PASS.**

### 4.6 No claim trusted before verification, including the cursor-binding issuer
Claims are parsed only after `verifySignature` succeeds in `JwtConsoleTokenDecoder` (code order
verified by QA; demonstrated behaviourally by E2.13/E2.14 — issuer/subject changes with a valid
signature are refused before any principal can exist). The history route binds the cursor issuer
from `principal.issuer()` (`ConsoleHistoryRoute` lines 176–177 and 207–211) and `ConsolePrincipal`
is constructible only by the authorizer seam after verification, so no unverified claim —
including the issuer used by the cursor binding — can reach the cursor. Runtime cursor-binding
behaviour itself belongs to the H1 slice (verified separately in the H1 QA reports); this slice
adds the verified-issuer guarantee it consumes. **PASS.**

### 4.7 Token custody
Decoder and composition hold the token only inside one call; the route response never contains it;
exception messages are fixed labels (`neverRendersOrRepeatsTheTokenInItsOwnText` — implementer
test, plus QA's F3 response check). No persistence path exists in the package. **PASS.**

### 4.8 Gaps independently reduced by QA (beyond the implementer's pass)
`JwksParser` (no implementer coverage) was exercised directly by QA: valid RSA sig key resolves and
verifies; EC/enc/incomplete keys skipped; missing keys array, duplicate kid, non-JSON, blank,
sub-2048-bit key and EC-only sets all fail or leave the decoder refusing (P1–P8).
`HttpJwksSource` was exercised against a **loopback 127.0.0.1 fixture HTTP server only** (no
external network): parse+cache, refresh publishing a rotated kid, non-200 refused, redirects never
followed, non-JSON refused, oversized document refused (H1–H7). **All PASS.** These are QA-owned
additions; they do not replace the deployment retests in §7.

## 5. Disclosed-gap assessment (independent judgement, not a repetition of the implementer's notes)

| Gap | QA assessment |
| --- | --- |
| `HttpJwksSource`/`JwksParser` have no test coverage | **Acceptable for this slice, now partially reduced.** QA added direct coverage: parser behaviour (P1–P8) and loopback HTTP behaviour including redirect refusal, status refusal, size bound and cache/refresh (H1–H7) all pass. Remaining unverifiable locally: TLS behaviour, real-Keycloak JWKS document shape, timeouts under real latency, redirect policy against the real issuer, and deployed-domain fetch. **Named deployment retest (DEP-K1-A):** fetch the pinned realm's JWKS over the deployed HTTPS path and verify a real issuer-minted token end-to-end, capturing redirects/headers/timeouts. |
| JWKS-unavailable surfaces as 401 rather than 503 | **Acceptable for this slice; recorded as a truthfulness limitation, not a security defect.** QA confirmed the mapping chain (`ConsoleAuthorizationRoute.denialOf`: `KEY_SOURCE_UNAVAILABLE` → `Denial.UNAUTHENTICATED` → C3 401) and that the outage refuses before any business call (F5). The response is fail-closed; it is only semantically imprecise (client-visible "unauthenticated" for a server-side outage). D-5 requires "unavailable current registry fails closed", which holds. **Named deployment retest (DEP-K1-B):** with the deployed composition active, present a valid token during a forced JWKS outage and assert the observed status; if the deployed error model requires 503, a new route branch must be specified and re-verified — no code was changed by QA. |
| No network fetch or real issuer round trip | **Acceptable and unavoidable in this slice's authorized scope** (no issuer, cloud or browser execution permitted). QA's loopback HTTP coverage exercises the transport mechanics without inventing an issuer. Everything issuer-interop-specific remains unproven by design. **Named deployment retests:** DEP-K1-A above plus **DEP-K1-C** — pinned-Keycloak PKCE happy path, `login_required`, disjoint optional scopes, dual-scope request denial, replayed code/state/nonce, service/ID/refresh-token rejection, revocation/logout and the ≤900 s issued lifetime, per construction-handoff K1 and D-5; these require issuer execution that only a deployment can supply. |

None of the three gaps is a defect of executed behaviour at this revision; each is an
unverifiable-here item with a concrete retest. No acceptance criterion within QA's authorized scope
failed.

## 6. Dev stub isolation from the production composition (S1–S4, G1–G4, static)

- Runtime: the deployed composition (`ConsoleRuntime.deployedApplication()` invoked by QA via
  reflection) answers **503 with empty bodies** for both business routes regardless of any
  credential — including a `devstub_` handle — and 404 otherwise; entry-point composition fails
  closed without its required environment; `compose()` takes no authorizer/decoder/registry/service
  parameter. No business route is composed in this revision.
- Static: `adapter/aws` (ConsoleHandler → ConsoleRuntime) references no dev class and no jwt class;
  the only main-tree mentions of `console.dev` outside the dev package are javadoc in
  `ConsoleTokenDecoder`; the dev stub (`DevStubTokenIssuer`) is reachable only from
  `console/dev` (loopback dev harness) and tests.
- Cross-seam refusals: a stub handle is refused by the real verifier (S1); a real verified JWT is
  refused by the stub decoder seam (S2); the stub handle is opaque and not a JWT (S4).
  **All PASS.**

## 7. Coverage boundaries (explicitly not verified here)

Not executed by QA, with reasons: real Keycloak/issuer interop and PKCE/nonce/state/code-reuse
flows (issuer execution — deployment retests DEP-K1-A/C); browser auxiliary window, opener/COOP and
callback behaviour (browser execution, gated by browser-flow.md feasibility review); WS token
class, WS authorizer and subprotocol carriage (separate slice; no WS composition exists); deployed
Lambda/API-Gateway/CloudFront behaviour (deployment); IAM/edge configuration (DevOps/Security
scope). RS384/RS512 were verified only with QA-generated local keys, not issuer-minted tokens.
RSA-only algorithm allowlist and no clock-skew allowance are contract-consistent configuration
choices, re-confirmed, not defects.

## 8. Payment-behavior-review application (managed checklist)

Flow: console submission fronting Operation A. K1 adds no payment semantics: issuer decision,
processor rejection and unconfirmed outcome stay separated behind the unchanged domain service; the
credential refusal path never reaches it (service.calls == 0 for every refusal, exactly one call on
acceptance); no replay/idempotency surface is introduced (bearer-token reuse within lifetime is
ordinary, and no payment reference is minted on any refusal path); wrong-acquirer isolation remains
the registry intersection verified by the unchanged policy-core tests and H1 QA reports. No GET,
login, retry or authentication flow sends any payment: the deployed composition performs no
business action at all, and the dev harness is loopback-only. **No integrated end-to-end payment
exists in this revision — re-confirmed independently by QA** (503-not-composed composition, §6;
loopback-only listeners, static grep §3/§6).

## 9. QA restrictions honoured

No Git or provider mutation (read-only `git rev-parse`/`branch --show-current`/`status --porcelain`
only); no cloud, issuer, browser or external network execution (the only HTTP is the QA loopback
fixture); no production-code, contract, or other-specialist file edits; no credential flows; no
onward delegation. QA wrote only `reviews/k1-qa-20261123/`.

## 10. Deliverables in this directory

`qa-report.md` (this file), `qa-expected-matrix.md`, `execution-model.json`, `tools/QaK1Driver.java`,
`SHA256SUMS`, raw logs `log-01-toolchain.txt`, `log-02-compile.{stdout,stderr,exit}`,
`log-03-focused-suites.{stdout,stderr,exit}`, `log-04-driver-compile.stderr`,
`log-05-qa-driver.txt`, `log-06-static-composition.txt`.
