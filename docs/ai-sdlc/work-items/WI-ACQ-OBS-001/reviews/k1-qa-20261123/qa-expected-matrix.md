# QA K1 expected-behaviour matrix — derived BEFORE reading implementation test assertions

Reviewer: ai-sdlc-qa. Date: 2026-11-23. Derived from the adopted contracts
(`design/contract-deltas/continuation-rev3-20261123/contracts.md` D-5 exact replacement + D-6/R-2,
`inherited-candidate.md` D-2, `browser-flow.md`, `construction-handoff.md` K1) and the D-2 token
custody obligations, before reading any test file in
`src/test/java/com/acquiring/simulator/console/jwt/`. Main production classes were inspected for
composition structure only.

Contract obligations for slice K1 (real issuer decoder + composition seam):

- D-5 HTTP authorizer: verified signature/JWKS/algorithm/kid, exact environment issuer, resource
  audience exactly the configured console API (no WS audience), `console.demo` and no `ws.observe`,
  azp=console-demo, sub=configured demo subject, valid NumericDates / lifetime <= 900 s. Fail
  closed on unsupported issuer/key/claim or unavailable current registry.
- D-5 disjointness: neither audience may appear on the other token; a token carrying both resource
  audiences/scopes is refused, never narrowed. ID/refresh/service tokens rejected on both surfaces
  even when signed with matching resource scope/audience.
- D-2: no client secret; access tokens memory-only; never logged/rendered/persisted; no fallback to
  stub, cached authority, default subject or trusted caller on any verification or key failure.
- construction-handoff K1: `decodeAndVerify` seams; strict disjoint audience/client/scope rules;
  separate pure claim checks from key/registry availability; never fall back to stub or cached
  authority on failure; key rotation by refreshing the key set, never pinning one key; tests use
  locally generated keys; no live issuer/network JWKS/browser in this slice.

## E1. Valid token — accepted, exact claim shape

Given a compact JWS signed RS256 with the configured issuer's key, header alg in the configured
allowlist and a non-empty kid present in the current JWKS, payload iss=configured issuer,
aud=[configured console API] (or a single string equal to it), azp=console-demo, sub=configured
demo subject, scope containing `console.demo` and not `ws.observe`, iat/nbf in the past, exp in the
future, lifetime 1..900 s:
expect ACCEPT with the verified claim view (iss, aud list, sub, azp, scopes, iat, nbf, exp) and a
principal carrying exactly those verified values. RS384/RS512 also accepted when configured.

## E2. Refusal cases — each refuses, nothing else runs, reason as expected

| # | Case | Expected result |
| --- | --- | --- |
| E2.1 | `alg` absent | refuse UNSUPPORTED_ALGORITHM; no key consulted |
| E2.2 | `alg: "none"` (any signature bytes, incl. empty) | refuse UNSUPPORTED_ALGORITHM; no key consulted |
| E2.3 | HMAC alg (HS256/HS384/HS512), valid HMAC signature with any secret | refuse UNSUPPORTED_ALGORITHM; no key consulted |
| E2.4 | other non-configured alg (ES256, PS256) | refuse UNSUPPORTED_ALGORITHM |
| E2.5 | missing/blank `kid` | refuse UNKNOWN_KEY; no key-set lookup |
| E2.6 | signature by a different key than the named kid's key | refuse BAD_SIGNATURE |
| E2.7 | tampered payload (any claim byte changed, header/signature intact) | refuse BAD_SIGNATURE |
| E2.8 | signed by configured key but iss != configured issuer (incl. missing iss) | refuse WRONG_ISSUER |
| E2.9 | aud = other single resource | refuse WRONG_AUDIENCE |
| E2.10 | aud = [console API, WS API] (dual resource audience) | refuse WRONG_AUDIENCE |
| E2.11 | aud = [console API, other] or two equal audiences | refuse WRONG_AUDIENCE |
| E2.12 | exp absent or reached (now >= exp) | refuse (MALFORMED_TOKEN if absent, EXPIRED if reached) |
| E2.13 | nbf in the future | refuse NOT_YET_VALID |
| E2.14 | iat in the future | refuse NOT_YET_VALID |
| E2.15 | scope missing / `openid` only | refuse MISSING_SCOPE |
| E2.16 | scope contains the forbidden WS scope | refuse WRONG_SCOPE (disjoint, never narrowed) |
| E2.17 | subject other than the configured demo subject | refuse WRONG_SUBJECT via the unchanged policy core |
| E2.18 | azp other than console-demo | refuse WRONG_AUTHORIZED_PARTY |
| E2.19 | lifetime > 900 s (and <= 0) | refuse TOKEN_LIFETIME_EXCEEDED |
| E2.20 | malformed compact shapes (segments != 3, empty segments, non-JSON header/payload, > 16 KiB, blank/null) | refuse MALFORMED_TOKEN |
| E2.21 | non-empty `crit` header | refuse (no unrecognised critical header may be ignored) |
| E2.22 | key source unavailable (current() or refresh() throws) | refuse KEY_SOURCE_UNAVAILABLE; no cached/stub substitution |
| E2.23 | kid names a key not in the set (after one refresh) | refuse UNKNOWN_KEY; exactly one refresh |
| E2.24 | signed with a key of a kid different from the header kid | refuse (kid is the only key-selection input; no header jwk/x5u/jku trust) |

Refusal reasons are labels; exact Reason enum naming is implementation-facing. The contract
requirement is: refusal with the expected error path (fail-closed, no partial identity, business
service never reached for a credential refusal).

## E3. Rotation

Given the current key set does not contain the presented kid and the refreshed set does: accept
after exactly one refresh; a new kid must not be usable without the refresh having occurred. Given
the presented kid was retired (refreshed set no longer contains it): refuse after exactly one
refresh, and no key from an earlier successful lookup may remain pinned.

## E4. Verification cannot be skipped by any input

No compact token, header field (alg/crit/jwk/jku/x5u/kid), payload claim, key-source behaviour or
composition state may produce an accepted principal without signature verification against a key
obtained from the configured source, followed by the exact claim policy. Malformed, unsigned,
HMAC-signed and attacker-signed tokens are all refused. Unexpected decoder failure is a refusal,
never a partial identity.

## E5. No claim trusted before verification — including the cursor-binding issuer

Claims (iss, aud, sub, azp, scope, iat, nbf, exp) may only be read after signature verification.
The cursor binding issuer must originate from the verified principal (post-verification), never
from an unverified token claim or client input. Demonstration: a token signed by an attacker key or
carrying an unverified issuer/subject never produces a principal; the principal's issuer is the
verified claim value that equals the configured issuer.

## E6. Dev stub isolation

`DevStubTokenIssuer` (unsigned local double, `devstub_` handles) must be unreachable from the
production composition: the deployed console Lambda composition must not reference the dev package
or the stub, must compose no unsigned/stub authentication path, and (in this revision) must not
activate the business routes at all (503 UNAVAILABLE is the truthful not-composed answer). A real
verified token must be refused by the stub seam and a stub handle refused by the real verifier.

## E7. Error-path mapping at the route

Credential refusals map to the authentication/context denial row (401 UNAUTHENTICATED for the
credential-class refusals, 403 CONTEXT_DENIED where the identity is known but not authorized);
the business service is never invoked for any refused credential.

## E8. Not verifiable in this slice (deployment retests, named)

- Real issuer/JWKS behaviour over the network (Keycloak release, realm config, PKCE, code/nonce/
  state, login_required): requires the pinned issuer deployment — issuer round trip.
- `HttpJwksSource`/`JwksParser` production-path behaviour against a live issuer: QA may cover
  parser and loopback HTTP behaviour locally, but the deployed-domain fetch, redirect policy and
  timeouts against the real issuer remain deployment retests.
- WS token class, WS authorizer, browser auxiliary flow, COOP/opener behaviour: out of K1 scope
  (gated by browser-flow.md feasibility), issuer/browser execution.
