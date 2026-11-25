# Continuation H1 — QA-driven fixes results (2026-11-23)

Developer evidence only: **not** independent QA, **not** Security review, **not** human acceptance.
No push, no PR, no merge, no deploy, no cloud call, no new feature.

## 1. Input and binding

| Item | Value |
| --- | --- |
| QA report | `reviews/h1-qa-20261123/qa-report.md` |
| QA report SHA-256 | `2ac3c71662d725bf86a8db4adf86be8447f3405c5aaed6757fd8f6c0152e8844` — re-verified with `sha256sum`, matches the coordinator pin |
| Normative contract | `design/contract-deltas/continuation-rev3-20261123/` (D-6/R-2 in `contracts.md`; `wire-fixtures.json`) |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch / HEAD | `feat/wi-acq-auth-001` / `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` (no commit made) |
| JDK / Maven | Temurin `25.0.5+8`; Maven `3.10.1` offline (`-o`) |
| Model | `nan/deepseek-v4-flash`, no override |
| Changed-file hashes | `changed-files.sha256` (8 files, main + test) |

Baseline `mvn -o -B -DskipTests test-compile`: **exit 0**, empty stderr
(`runs/compile.{stdout,stderr,exit}`).

## 2. Fixes, in the instructed order

### 2.1 QA-DEF-2 — 503 body must be `AUTHORIZATION_UNAVAILABLE` (MEDIUM)

- Authority: `wire-fixtures.json` `historyErrors["503"]` =
  `{"version":1,"error":"AUTHORIZATION_UNAVAILABLE","action":null}`; D-6 requires exact error bodies.
- Change: every history-route `503` now emits `AUTHORIZATION_UNAVAILABLE` — initial registry outage,
  post-read registry outage, store `head` failure and read-range failure
  (`ConsoleHistoryRoute.error(503, "AUTHORIZATION_UNAVAILABLE", null)`).
- Assertion change, stated plainly: two existing assertions in `ConsoleHistoryRouteTest`
  (`registryOutageAfterTheStoreReadIsFiveZeroThreeWithNoPayload`,
  `storeFailureIsFiveZeroThreeWithNoPayload`) encoded `UNAVAILABLE`. **They were changed because
  that value contradicted the adopted contract and the reviewed fixture — not to make a red test
  green.** The implementation was corrected first; the assertions now assert the pinned code.
- Focused test added: `serviceUnavailableBodyExactlyMatchesTheReviewedFixture` byte-compares the
  whole `503` body against the fixture.

### 2.2 QA-DEF-1 — bind the token issuer in the signed cursor (LOW)

- Authority: D-6 binding list and `wire-fixtures.json` `cursorUnsignedHarnessBinding.issuer`.
- Change: `ConsolePrincipal` now carries the verified `issuer`; `DemoConsoleAuthorizer` supplies the
  verified claim. `ConsoleHistoryCursorCodec.Binding` carries `issuer`, the payload always writes
  `issuer`, and `parse` requires it. A continuation compares the current token issuer with the
  cursor issuer and refuses a mismatch with `403 CONTEXT_DENIED`; the post-read re-authentication
  also compares issuer.
- Per-page re-authentication is **not** replaced by the cursor: both authentications still run, and
  a token that expires during the store read still returns `401` before disclosure
  (`tokenExpiryDuringTheStoreReadDisclosesNoPayload`, unchanged, asserts two credential calls).
- Focused tests added: `cursorPayloadBindsTheTokenIssuerAndTargetTerminal`,
  `cursorMintedUnderAnotherIssuerIsForbidden` (same signing key, different issuer → refused).

### 2.3 QA-DEF-3 — bind the `terminalRef` context (LOW)

- Authority: D-6 context `(acquirerRef, merchantRef, terminalRef when target-specific)` and the
  fixture's `context.terminalRef = "TERMINAL-A"`.
- Change: `AuthorizedContext` gains an optional `terminalRef` (a 4-argument constructor keeps
  non-terminal callers unchanged). The history route binds `mandate.terminalRef()` into the cursor,
  and a continuation/re-authentication refuses a different terminal with `403 CONTEXT_DENIED`.
- Focused test added: `cursorBoundToAnotherTerminalIsForbidden`; the payload test above also asserts
  `terminalRef = TERMINAL-A`.
- Fixture correction during the red run (not an assertion relaxation): the
  `retiredMandateGenerationForcesFourTenAndNoPayload` fixture reissued the context through the old
  4-argument constructor, so it produced a `null` terminal and the new terminal check fired before
  the generation check. The reissued fixture now keeps `TERMINAL-A`, isolating the changed mandate
  generation the test names. The `410` assertion is unchanged.

## 3. Executed results

Commands written to `runs/` with separate stdout/stderr and an immediate exit file.

| Run | Command | Exit | Result |
| --- | --- | --- | --- |
| compile | `mvn -o -B -DskipTests test-compile` | **0** | BUILD SUCCESS, empty stderr |
| focused run 1 (red, preserved) | `mvn -o -B test -Dtest='ConsoleHistoryRouteTest,ConsoleHistoryRouteDynamoIntegrationTest,ConsoleOriginVerificationGateTest,ConsoleAuthorizationRouteTest,ConsoleSubmissionAtomicIntegrationTest,ConsoleHandlerCompositionTest,ConsoleDevServerTest,DevStubTokenIssuerTest' -DfailIfNoTests=true` | **1** | **74 tests, 1 failure, 0 errors, 0 skipped** — `retiredMandateGenerationForcesFourTenAndNoPayload` (fixture terminal mismatch, §2.3) |
| focused run 2 (green) | same command | **0** | **74 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS, empty stderr |

Green per class (`runs/focused-console-suites-attempt2.stdout`):

| Class | Tests |
| --- | --- |
| `ConsoleHistoryRouteTest` | 24 (was 20; +4 QA-fix tests) |
| `ConsoleHistoryRouteDynamoIntegrationTest` | 4 |
| `ConsoleOriginVerificationGateTest` | 9 |
| `ConsoleAuthorizationRouteTest` | 13 |
| `ConsoleSubmissionAtomicIntegrationTest` | 3 |
| `ConsoleHandlerCompositionTest` | 5 |
| `ConsoleDevServerTest` | 9 |
| `DevStubTokenIssuerTest` | 7 |
| **Total** | **74** |

The red run is preserved (`focused-console-suites.stdout/.stderr/.exit`); the green run wrote
`focused-console-suites-attempt2.*` and did not overwrite it. The red failure was a test fixture that
did not carry the new terminal binding, not a removed or weakened assertion.

## 4. Observations — settled text vs open questions

1. **Mandate fingerprint vs `registryMandateId`/`registryVersion` — OPEN QUESTION for Requirements.**
   D-6 says the cursor binds "registry mandate identity/version"; the fixture names
   `registryMandateId: "M-1"` and `registryVersion: 1`. The implementation binds a SHA-256
   content fingerprint (`callerId` + refs + sorted routes), which retires the generation on content
   change (QA H9 is green) but does not carry the fixture's named fields. The contract does not
   settle whether a content fingerprint satisfies "identity/version" for a registry without a
   version counter, so **no behaviour was chosen or changed**. Question for Requirements: must the
   cursor carry named `registryMandateId`/`registryVersion` in addition to (or instead of) the
   content fingerprint?
2. **`epoch` key — settled for this HTTP-only read; documentary fixture difference.** D-6 binds
   "epoch when live". This slice is an HTTP-only read with no socket, so no live epoch value exists
   to bind; the fixture's `epoch: null` is a documentary placeholder. No code change. If Requirements
   wants the key present as an explicit `null` for wire-shape parity, that is a separate decision.
3. **`?cursor=X&after=N` — OPEN QUESTION for Requirements.** D-6 does not say whether `after` may be
   combined with `cursor`. Current behaviour is that `cursor` wins and `after` is silently ignored.
   The contract does not settle it, so **no behaviour was chosen or changed**. Question: should the
   combination be `400 INVALID_CURSOR` or accepted with `after` ignored?
4. **Malformed pagination mapping — still open (pre-existing).** D-6 names `INVALID_CURSOR` only for
   unknown/tampered cursors; the provisional `400 INVALID_CURSOR` mapping for malformed
   `snapshot`/`after`/unknown params remains disclosed in `evidence/continuation-h1-20261123/RESULTS.md`
   §4.1 and is unchanged.

**Note for independent re-verification.** The QA driver's `H3d-cursor-payload-no-extra-fields`
asserts the cursor key set equals a literal set that does **not** contain `issuer`, while
`QA-DEF-1` requires `issuer`. The driver's allowed set must be updated to include `issuer` (and
`terminalRef`) before re-running, or the two checks will contradict each other. This is a QA-side
artifact observation, not a product change.

## 5. What this proves and what it does not

- Proves, in-process with a test-supplied HMAC key and test seams: the history route's `503` body is
  the pinned fixture; the cursor binds and checks the verified issuer and the optional trusted
  terminal; both per-page authentications still run.
- Does **not** prove: real JWKS/signature/issuer verification (slice K1), a deployed edge or
  CloudFront/WAF, a deployed cursor-key source, a server-side read-pass store, a listener, browser/UI,
  or N-02 closure. No payment path was touched.

## 6. Non-claims

No new endpoint, contract, Terraform or revision was added. No Git write, stage, commit, push, PR,
merge, deployment, release or publication was performed. No AWS/Keycloak/cloud call and no provider
mutation. Independent verification remains **PENDING**; this is developer evidence only.
