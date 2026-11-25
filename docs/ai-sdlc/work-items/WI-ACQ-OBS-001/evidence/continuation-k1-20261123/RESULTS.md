# Continuation construction slice K1 — execution results (2026-11-23)

Developer evidence for the K1 package (real compact-JWS decoder, JWKS key-source seam, composition
into the unchanged claim-policy core). This file records what was actually executed on the recorded
tree. It is **not** independent QA, **not** Security review and **not** human acceptance. Nothing
here is a deployed-issuer, interoperability or provider result.

## 1. Binding

| Check | Observed |
| --- | --- |
| `realpath .` / `git rev-parse --show-toplevel` | `/home/pazfernando/projects/acquiring-simulator` |
| branch | `feat/wi-acq-auth-001` |
| HEAD | `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` (unchanged; no commit made in this slice) |
| dirty entries on entry and on exit | 61 porcelain entries, all pre-existing continuation work, none reverted, staged or committed |
| toolchain | Temurin `25.0.5+8` (`JAVA_HOME=/home/pazfernando/.local/opt/jdk-25`), Apache Maven `3.10.1`, offline (`-o`) |
| new dependency | none — `pom.xml` unmodified; signature, JSON and key generation use the JDK and the already-present Jackson |
| model | `nan/deepseek-v4-flash`, no override |

Byte identity of the K1 package as executed (SHA-256):

| File | SHA-256 |
| --- | --- |
| `src/main/java/com/acquiring/simulator/console/jwt/JwtConsoleTokenDecoder.java` | `fb4008d0cf6094d9d770b2b741c28db4c53f2809e391a9ac92c17e927dc585ff` |
| `src/main/java/com/acquiring/simulator/console/jwt/TrustedIssuerConfiguration.java` | `c6a3b2e404b7c88c96e6779762fd6d3ee0f2b1a91bf0ef2c7c433d30ed1781fb` |
| `src/main/java/com/acquiring/simulator/console/jwt/ConsoleTokenComposition.java` | `56d5c2357dfbd6b6dc0862feb36f9e7bbeedd1380af3d8ecda9c28f0b174adf5` |
| `src/main/java/com/acquiring/simulator/console/jwt/CompactJws.java` | `f805ad29af11b5958910c99a9e7aae04d09fbcfbc676598d07ad8d0a17aeb817` |
| `src/main/java/com/acquiring/simulator/console/jwt/JwksSource.java` | `2b90aff85fe2b51f4d4cd04e71611dc01e03ee9532fce4d65650773ea95bb372` |
| `src/main/java/com/acquiring/simulator/console/jwt/JsonWebKeySet.java` | `b886d4921868915582c12301992b7e166f9c2b69a36447ccd6e9231545eeb2ac` |
| `src/main/java/com/acquiring/simulator/console/jwt/JwksParser.java` | `aeb54d3bba7bad976dbb536e22cd22743716f68d74cf7ce476ee2eb8124e7d9e` |
| `src/main/java/com/acquiring/simulator/console/jwt/HttpJwksSource.java` | `32fe6887a4ced2587e2605726dcdfc7358c649e24c5f1622cc674726dc2e357c` |
| `src/main/java/com/acquiring/simulator/console/jwt/JwksSourceException.java` | `8102d443e4d9f2a74fd3eb3fc03a69a7b881e14c42cef0711c19ace36cad823b` |
| `src/test/java/com/acquiring/simulator/console/jwt/JwtConsoleTokenDecoderTest.java` | `d92ae2e3663575475e879487d4144561da44ef50a2d461adfca250eb2a79848f` |
| `src/test/java/com/acquiring/simulator/console/jwt/ConsoleTokenCompositionTest.java` | `49fdfd0d7723d93beaa805f1194eca22f20d4974a65dc9e60117cd44b8587785` |
| `src/test/java/com/acquiring/simulator/console/jwt/TrustedIssuerConfigurationTest.java` | `b2bc07f7c3c1c6380104b477dc5d35482b78e72b5fe63d6a66ce5b04367b838e` |
| `src/test/java/com/acquiring/simulator/console/jwt/JwtTestSupport.java` | `6564f75883fdfc640aa37d19bed156eacc6cc8ec7fa1920e9288ad2af215c225` |

## 2. Executed commands, real exits and counts

Each invocation was a separate process with its own captured stdout, stderr and exit file under
`runs/`. `mvn -q` compile emits no stdout/stderr on success; both files are zero bytes.

| # | Step | Command | Exit | Result |
| --- | --- | --- | --- | --- |
| 1 | compile | `mvn -o -q -DskipTests compile` | **0** | `compile.stdout` 0 bytes, `compile.stderr` 0 bytes |
| 2 | K1 decoder | `mvn -o -B test -Dtest='JwtConsoleTokenDecoderTest,TrustedIssuerConfigurationTest' -DfailIfNoTests=true` | **0** | **32 tests, 0 failures, 0 errors, 0 skipped** — BUILD SUCCESS |
| 3 | K1 composition | `mvn -o -B test -Dtest='ConsoleTokenCompositionTest' -DfailIfNoTests=true` | **0** | **5 tests, 0 failures, 0 errors, 0 skipped** — BUILD SUCCESS |
| 4 | focused console suites | `mvn -o -B test -Dtest='ConsoleHistoryRouteTest,ConsoleHistoryRouteDynamoIntegrationTest,ConsoleOriginVerificationGateTest,ConsoleAuthorizationRouteTest,ConsoleSubmissionAtomicIntegrationTest,ConsoleHandlerCompositionTest,ConsoleDevServerTest,DevStubTokenIssuerTest' -DfailIfNoTests=true` | **0** | **90 tests, 0 failures, 0 errors, 0 skipped** — BUILD SUCCESS |

Total asserted in this pass: **127 tests, 0 failures, 0 errors, 0 skipped**, compile and all three
test exits `0`. No red run occurred, so no preserved-red attempt is recorded.

Per-class counts, step 2 and 3:

| Class | Tests |
| --- | --- |
| `JwtConsoleTokenDecoderTest` | 25 |
| `TrustedIssuerConfigurationTest` | 7 |

Per-class counts, step 4:

| Class | Tests |
| --- | --- |
| `ConsoleHistoryRouteTest` | 40 |
| `ConsoleHistoryRouteDynamoIntegrationTest` | 4 |
| `ConsoleOriginVerificationGateTest` | 9 |
| `ConsoleAuthorizationRouteTest` | 13 |
| `ConsoleSubmissionAtomicIntegrationTest` | 3 |
| `ConsoleHandlerCompositionTest` | 5 |
| `ConsoleDevServerTest` | 9 |
| `DevStubTokenIssuerTest` | 7 |

## 3. Refusal cases actually exercised

Each row below is a test that ran green in step 2, 3 or 4 above. "Exercised" means the case was
driven through the decoder (or, where marked, through the unchanged policy core behind it) and the
refusal reason was asserted — not that it was reasoned about.

| Case | Reason asserted | Exercising test | Step |
| --- | --- | --- | --- |
| Bad algorithm (`HS256`) | `UNSUPPORTED_ALGORITHM`, key source not consulted at all | `refusesAnUnknownAlgorithmWithoutConsultingAnyKey` | 2 |
| Bad/absent algorithm field | `UNSUPPORTED_ALGORITHM` | `refusesATokenWithNoAlgorithm` | 2 |
| Missing `kid` | `UNKNOWN_KEY`, zero `current()` calls | `refusesAMissingKeyId` | 2 |
| Signature mismatch | `BAD_SIGNATURE` (local key two against key one) | `refusesASignatureMismatch` | 2 |
| Signature mismatch via tampered payload | `BAD_SIGNATURE` (tampered `sub`, valid header/key) | `refusesATamperedPayloadEvenThoughTheHeaderAndKeyAreValid` | 2 |
| Wrong issuer | `WRONG_ISSUER` | `refusesAWrongIssuer` | 2 |
| Wrong audience | `WRONG_AUDIENCE` | `refusesAWrongAudience` | 2 |
| Both resource audiences (HTTP + WS) | `WRONG_AUDIENCE` | `refusesATokenCarryingBothResourceAudiences` | 2 |
| Expired | `EXPIRED` | `refusesAnExpiredToken` | 2 |
| Not yet valid (`nbf` in future) | `NOT_YET_VALID` | `refusesATokenThatIsNotYetValid` | 2 |
| Not yet valid (`iat` in future) | `NOT_YET_VALID` | `refusesAnIssueTimeInTheFuture` | 2 |
| Missing scope | `MISSING_SCOPE` (`openid` only) | `refusesAMissingRequiredScope` | 2 |
| Wrong subject | `WRONG_SUBJECT` | `thePolicyCoreStillRefusesASubjectOtherThanTheConfiguredDemoSubject` | 3 |
| Retired key after rotation | `UNKNOWN_KEY`, one refresh performed | `refusesTheRetiredOldKeyAfterRotation` | 2 |
| Rotation, new `kid` | accepted only after exactly one `current()` + one `refresh()` | `acceptsARotatedKeyOnlyAfterRefreshingTheKeySet` | 2 |

The retired-key row is the rotation requirement: the current key set contains only `key-2`, a token
signed by the previously operative key `key-1` is presented, the decoder refreshes once and still
finds no `key-1`, and refuses with `UNKNOWN_KEY`. No key is pinned from an earlier successful
lookup.

Adjacent refusals also exercised and recorded for completeness, though not in the requested list:
key-source unavailable (`KEY_SOURCE_UNAVAILABLE`, both on the current set and on the rotation
refresh), unsupported critical header (`MALFORMED_TOKEN`), missing `exp` (`MALFORMED_TOKEN`),
lifetime above the 900-second ceiling (`TOKEN_LIFETIME_EXCEEDED`), malformed compact-JWS shapes, and
the WS-scope-on-HTTP-token case (`WRONG_SCOPE`). At the route level the composition tests also show
an unauthenticated `401 UNAUTHENTICATED`, a context-denied `403 CONTEXT_DENIED`, and a key-source
outage refusing the token before the business service is called (`service.calls == 0` in each).

## 4. Explicit statements about what was and was not involved

- **Keys were generated locally, in the test JVM.** `JwtTestSupport` creates two RSA key pairs with
  the JDK `KeyPairGenerator` and assembles/signs every compact JWS with the JDK `Signature` API. No
  third-party token library was added.
- **No network JWKS fetch occurred.** Every decoder test injects an in-memory `JwksSource` double
  (`JwtTestSupport.CountingJwksSource`) that counts consultations and performs no I/O. One test
  (`verifiesUsingOnlyTheInjectedKeySourceAndPerformsNoNetworkIo`) asserts the decoder consults only
  that object (one `current()` call, no refresh).
- **No real Keycloak round trip occurred.** No issuer was contacted, no realm or client was
  registered, no authorization-code/PKCE flow was run, and no browser was involved. `HttpJwksSource`
  and `JwksParser` were compiled but are **not** exercised by any test in this pass; they are an
  unverified path and were not run against any endpoint.
- **The bundled dev stub remains an unsigned local double and is not authentication.**
  `DevStubTokenIssuer` is unchanged and still the explicitly labelled unsigned local double for the
  loopback browser harness. Its tests (`DevStubTokenIssuerTest`, 7 green) assert the stub's local
  behaviour only. Nothing in this slice makes it an authentication path, and no deployed
  composition was activated.
- **No token was persisted, logged or echoed.** `neverRendersOrRepeatsTheTokenInItsOwnText` asserts
  the decoder's own text and the exception message contain no token; the composition test asserts
  the HTTP response body contains no token. The decoder holds the token only inside one call.

## 5. Open points (recorded, not resolved)

Carried forward verbatim in substance from `PROGRESS.md` §5; this slice does not invent resolutions.

1. **JWKS-unavailable currently surfaces as `401`, not a truthful `503`.** The K1 dispatch routes
   refusals through the existing authentication error path, and `ConsoleAuthenticationException`
   maps to `401 UNAUTHENTICATED` in the submission route, so an unavailable key source also refuses
   as `401`. A truthful `503` needs a new route branch; recorded for reviewer confirmation, not
   implemented here. The composition test `aKeySourceOutageRefusesTheTokenAndNeverReachesTheService`
   shows the observed `401`.
2. **Dev stub retained.** `DevStubTokenIssuer` remains the unsigned local double for the loopback
   harness. The real decoder is wired through the new `ConsoleTokenComposition` seam and exercised
   by tests; activating it in the deployed Lambda needs DevOps-supplied
   issuer/audience/JWKS/subject configuration, which is not invented in this slice.
3. **RSA-only algorithm allowlist.** Only `RS256`/`RS384`/`RS512` are accepted;
   `TrustedIssuerConfiguration` refuses any other configured algorithm at construction, and the
   decoder refuses any other token algorithm (including `none` and HMAC) before consulting a key.
   The narrower set is a recorded limitation, not a claim of full JOSE coverage.

## 6. Non-claims

No Git write, stage, commit, push, PR, merge, deploy, release or publication occurred. No AWS, cloud,
Keycloak or other provider call was made. No credential flow, Terraform change or new dependency was
introduced. Independent QA, Security review and human acceptance remain **PENDING**; every
traceability row keeps `independent verification: PENDING` and no A/B/C status moved.
