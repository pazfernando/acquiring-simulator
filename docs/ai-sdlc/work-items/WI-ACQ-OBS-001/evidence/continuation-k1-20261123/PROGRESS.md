# Continuation construction slice K1 — progress log (started 2026-11-23)

This file is written first and updated incrementally so an interruption cannot erase the analysis.
It records what was observed, in order. It is developer evidence only: not independent QA, not
Security review and not human acceptance.

## 1. Binding verification (before any write)

| Check | Expected | Observed | Match |
| --- | --- | --- | --- |
| `realpath .` | `/home/pazfernando/projects/acquiring-simulator` | `/home/pazfernando/projects/acquiring-simulator` | yes |
| `git rev-parse --show-toplevel` | same | `/home/pazfernando/projects/acquiring-simulator` | yes |
| branch | `feat/wi-acq-auth-001` | `feat/wi-acq-auth-001` | yes |
| HEAD | `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` | `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` | yes |
| dirty ownership | existing continuation work, preserved | 59 porcelain entries, none reverted | yes |
| toolchain | JDK 25.0.5, Maven 3.10.1 offline | Temurin 25.0.5+8; `/home/pazfernando/.local/opt/maven/bin/mvn` | yes |

No divergence from the H1 baseline. The working tree was dirty on entry with pre-existing
continuation work (modified `WORK_ITEM.md`, `delivery-status.md`, `implementation-progress.md`,
`owner-decisions.md`, `requirement-traceability.md`, `security-review.md`, `AwsRuntime.java`,
`AuthorizationService.java`, plus untracked design/evidence/infra/console files). None of it was
reverted, reformatted or staged.

## 2. Inputs read (observed digests)

| Input | SHA-256 | Note |
| --- | --- | --- |
| `design/contract-deltas/continuation-rev3-20261123/contracts.md` (D-5) | `33d5fb3c8ef6b4aa6baae3389c74032efb1faa6d809a3f09268910edf9ca1f10` | read |
| `design/contract-deltas/continuation-rev3-20261123/browser-flow.md` | `1738c3746a4c85bed4e5cf0d31f883247a97df2e9377035cf295303044abb728` | read; auxiliary flow stays gated |
| `design/contract-deltas/continuation-rev3-20261123/construction-handoff.md` (K1) | `9d56574e3d80404ac036826ab358ce68b52ecf71eb5111ef79f27e8af4535483` | read |
| `design/contract-deltas/continuation-rev3-20261123/acceptance-scenarios.md` (K1-01…K1-05) | `1cc09f281f1a595b5f72c7b48b3e6364105fb783fd4f71edc1538a37d622076a` | read |
| `design/contract-deltas/continuation-rev3-20261123/inherited-candidate.md` (D-2) | `6b1e525f9071ca7f6117386da0ac8a0bb8f30ee050f7a75963e254e22d08c822` | read |
| `handoffs/continuation-20261123/construction-handoff.md` | `b169c99a724695bf6de7b37d9594154b6ec618488e1435e2bab903305882200c` | read (slice K1) |
| `handoffs/continuation-20261123/next-slices.json` | `50d1cfa18ed7c9ae822f4fef1ae989f1eada7536aaedde1d1b6ce2715b917b5f` | read |

Existing implementation reviewed before adding anything: `ConsoleTokenDecoder`,
`ConsoleTokenClaims`, `ConsolePrincipal`, `DemoConsoleAuthorizer`, `ConsoleAuthorizationRoute`,
`ConsoleContextRegistry`, `ConsoleAuthenticationException`, `ConsoleDevConfig`, `ConsoleDevHarness`,
`DevStubTokenIssuer`, `ConsoleDevServer`, `C4EvidenceVerifier` (JDK signature style), `pom.xml`.

## 3. Coordinated scope (exactly K1 item 1–4 of the dispatch)

1. A real compact-JWS decoder (`ConsoleTokenDecoder` implementation) that checks the signature
   against a configured JWKS, plus issuer, audience, expiry, not-before, issue time and authorized
   party, and returns the same `ConsoleTokenClaims` shape. The `DemoConsoleAuthorizer` policy core
   (claim order, current-mandate intersection through `ConsoleContextRegistry`) is **not** changed.
2. Fail closed for: unknown algorithm, missing `kid`, signature mismatch, wrong issuer, wrong
   audience, expired, not-yet-valid, unsupported/missing scopes, non-configured subject, unavailable
   key source. Key rotation by refreshing the key set, never by pinning one key.
3. Token in memory only — the decoder never persists, logs or echoes it; no combined payment-capable
   socket token; no auxiliary-window flow (gated by `browser-flow.md` feasibility).
4. Tests with locally generated keys only; no live issuer, no network JWKS fetch, no browser flow.

Out of scope for this slice (unchanged): deployed composition/activation, Keycloak, the WS token
class and WS authorizer, the SPA login flow, the history/mandate services, Terraform, any AWS or
cloud call, any new dependency.

## 4. Status log

- [x] Binding verified (§1).
- [x] Contract inputs read and digested (§2).
- [x] Evidence directory and this file created before code.
- [x] Configuration type and JWKS source seam implemented
      (`TrustedIssuerConfiguration`, `JwksSource`, `JsonWebKeySet`, `JwksParser`, `HttpJwksSource`).
- [x] Real decoder implemented and wired to the unchanged policy core (`JwtConsoleTokenDecoder`,
      `CompactJws`, `ConsoleTokenComposition`).
- [x] Focused tests written; exact counts/exits recorded in `RESULTS.md`.
- [x] `implementation-progress.md` and genuine traceability rows updated; independent verification
      kept PENDING.

### 4a. Observed runs (2026-11-23)

Separate invocations, each with its own exit/stdout/stderr file under `runs/`; full detail in
`RESULTS.md`. Nothing red occurred, so no preserved-red attempt exists to disclose.

| Step | Command | Exit | Observed |
| --- | --- | --- | --- |
| compile | `mvn -o -q -DskipTests compile` | 0 | no stdout/stderr |
| K1 decoder | `mvn -o -B test -Dtest='JwtConsoleTokenDecoderTest,TrustedIssuerConfigurationTest' -DfailIfNoTests=true` | 0 | 32 tests, 0 failures/errors/skipped |
| K1 composition | `mvn -o -B test -Dtest='ConsoleTokenCompositionTest' -DfailIfNoTests=true` | 0 | 5 tests, 0 failures/errors/skipped |
| focused console suites | `mvn -o -B test -Dtest='ConsoleHistoryRouteTest,ConsoleHistoryRouteDynamoIntegrationTest,ConsoleOriginVerificationGateTest,ConsoleAuthorizationRouteTest,ConsoleSubmissionAtomicIntegrationTest,ConsoleHandlerCompositionTest,ConsoleDevServerTest,DevStubTokenIssuerTest' -DfailIfNoTests=true` | 0 | 90 tests, 0 failures/errors/skipped |

All requested refusal cases were exercised green, including wrong subject (through the unchanged
policy core) and the retired-key-after-rotation case. Keys were locally generated in the test JVM;
no network JWKS fetch, no Keycloak round trip and no browser flow occurred. `HttpJwksSource` and
`JwksParser` compiled but are not covered by any test in this pass.

## 5. Open points recorded, not silently resolved

1. **Key-source-unavailable HTTP status.** The K1 dispatch says refusals use the existing
   authentication error path. `ConsoleAuthenticationException` maps to `401 UNAUTHENTICATED` in the
   submission route, so an unavailable JWKS also refuses as `401`, not `503`. A truthful `503`
   would need a new route branch and is not invented here; recorded for reviewer confirmation.
2. **Dev-stub harness retained.** `DevStubTokenIssuer` remains the explicitly labelled unsigned
   local double for the loopback browser harness. It is not used to claim authentication. The real
   decoder is wired through a new composition seam and exercised by tests; activating it in the
   deployed Lambda needs DevOps-supplied issuer/audience/JWKS/subject configuration, which is not
   invented in this slice.
3. **Algorithm set.** Only RSA JWS (`RS256`/`RS384`/`RS512`) is accepted by the allowlist. Any other
   algorithm, including `none` and HMAC, is refused as unsupported before any key is consulted.
