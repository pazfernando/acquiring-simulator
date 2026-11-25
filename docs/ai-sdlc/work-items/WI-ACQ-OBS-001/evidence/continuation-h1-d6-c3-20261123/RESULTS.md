# Continuation H1 — D-6 C-3 malformed-pagination taxonomy and failure order: results (2026-11-23)

Developer evidence only: **not** independent QA, **not** Security review, **not** human acceptance.
No push, no PR, no merge, no deploy, no cloud call, no new feature.

## 1. Input and binding

| Item | Value |
| --- | --- |
| Corrected text | `design/contract-deltas/d6-clarifications-rev2-20261123/d6-clarifications.md` |
| Corrected text SHA-256 | `3aaac88a72cb4abd3936786f8e467fa5fb164cb39c76bfea99daef8edd9643a3` — re-verified with `sha256sum`, matches the coordinator pin |
| Domain re-check | `reviews/d6-clarifications-rev2-20261123/domain-review.md` |
| Domain re-check SHA-256 | `fde09a1fdd83850a99a730141ce19d4441c6c5b4e41b38bfd53d059dce3d1ad7` — re-verified, matches the coordinator pin |
| Sections applied | **C-3.1–C-3.4** and the fixed order in **C-3.3 / §7** |
| Explicitly not implemented | C-1, C-2, C-4 beyond what the C-3 order requires; the adopted packet; the read-pass store, cursor-key custody and real issuer verification |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch / HEAD | `feat/wi-acq-auth-001` / `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` (no commit made) |
| JDK / Maven | Temurin `25.0.5+8`; Maven `3.10.1` offline (`-o`) |
| Model | `nan/deepseek-v4-flash`, no override |
| Changed-file hashes | `changed-files.sha256` (2 main, 1 test) |

## 2. The implemented failure order

For `GET /console/v1/transactions/{id}/events`, and nothing after the `200` page:

| # | Stop | Status / body | Where in code |
| --- | --- | --- | --- |
| 1 | unauthenticated | `401 UNAUTHENTICATED`, action `null` | `handle` → `authenticate` |
| 2 | request form, syntactic only (C-3.1 a–f) | `400 INVALID_CURSOR`, action `null` | `handle` → `queryOf` |
| 3 | mandate resolution | `503 AUTHORIZATION_UNAVAILABLE` / `403 CONTEXT_DENIED`, action `null` | `handle` → `requireMandate` |
| 4 | malformed payload / bad signature | `400 INVALID_CURSOR`, action `null` | `continuation` → `cursors.open` (`INVALID`) |
| 5 | retired schema/representation (C-1.5) | `410 RESYNC_REQUIRED`, action `RESYNC_REQUIRED` | `continuation` → `cursors.open` (`RETIRED`) |
| 6 | expired by signed `expiresAt` | `410 RESYNC_REQUIRED`, action `RESYNC_REQUIRED` | `continuation` → `cursors.open` (`EXPIRED`) |
| 7 | context mismatch | `403 CONTEXT_DENIED`, action `null` | `continuation` context comparison |
| 8 | changed mandate representation | `410 RESYNC_REQUIRED`, action `RESYNC_REQUIRED` | `continuation` fingerprint comparison |
| 9 | store head unavailable / unknown target | `503` / `404 NOT_FOUND`, action `null` | `requireHead` |
| 10 | `after` beyond frozen head (C-3.4) | `400 INVALID_CURSOR`, action `null` | `newSnapshot`, after `requireHead` |
| 11 | changed `R`/`F` or head regression | `410 RESYNC_REQUIRED`, action `RESYNC_REQUIRED` | `continuation` retention comparison |
| 12 | bound `lastSequence` beyond snapshot head | `400 INVALID_CURSOR`, action `null` | `continuation` |
| 13 | page | `200` | `page` |

One code per failure class; the status/error/action triples match
`continuation-rev3-20261123/wire-fixtures.json.historyErrors` (400/401/403/404/410/503). No second
code was introduced.

## 3. Changes

### 3.1 Codec — `ConsoleHistoryCursorCodec`

- Added a third refusal class, `CursorException.Kind.RETIRED`, so a signature-valid payload whose
  schema version is not current — or which lacks a member the current representation requires
  (C-1.5) — is a retired generation, not tampered input.
- `parse` now applies C-3.3's malformed-before-retired order: a signed payload that is not valid
  JSON, is not a JSON object, or has a wrongly-typed `version`/`keyId` is `INVALID` (400); a
  signature-valid payload with a non-current or absent `version` is `RETIRED` (410); a missing
  current-representation member is `RETIRED` (410); a wrongly-typed current-representation member is
  `INVALID` (400). `keyId` naming another generation while verifying against the current key remains
  contradictory input (`INVALID`).
- `open` keeps its existing order of signature verification → parse (malformed, then retired) →
  future-issued (`INVALID`) → expiry (`EXPIRED`), so the expired `410` still follows the retired
  `410`, both before the route's context `403`.
- `Binding` is unchanged; no payload member was added, removed or renamed.

### 3.2 Route — `ConsoleHistoryRoute`

- `continuation` maps `RETIRED` and `EXPIRED` to `410 RESYNC_REQUIRED` with action
  `RESYNC_REQUIRED`, and `INVALID` to `400 INVALID_CURSOR` with a null action. Because `open()`
  throws before returning a binding, a retired-schema cursor is refused `410` before the context
  comparison can fire.
- The C-3.3 step order is now marked in the route with comments: context `403` before
  representation `410`; head `503`/`404` before `R`/`F` and bound-`lastSequence`; and the
  head-dependent `after > H` refusal in `newSnapshot` after `requireHead` and before any reader call.
- The early-refusal prohibition is visible in code: the `after > H` comment states it MUST stay after
  the head read/`503`/`404` and before any page/range read, and `queryOf`/`parseAfter` are documented
  as the syntactic-only form step that never reads the head.
- The request-form step already refused unknown names (C-3.1(c)), repeated names (d), invalid
  `snapshot` (a), invalid `after` syntax (b), empty cursor (e), and `cursor` combined with
  `snapshot`/`after` (f); the comments now name those classes. No behaviour was added to or removed
  from the form step beyond that documentation.
- No change to authentication, post-read re-authentication, pagination arithmetic, coverage, gaps,
  expiry reporting or the `200` body.

## 4. Focused tests added (`ConsoleHistoryRouteTest`, 29 → 40; suites 79 → 90)

| Stop | Test | Pins |
| --- | --- | --- |
| resolution 503 | `registryOutageBeforeTheStoreReadIsFiveZeroThreeWithNoHead` | registry throws → `503 AUTHORIZATION_UNAVAILABLE`, null action, head 0, reader 0 |
| resolution 403 | `missingMandateIsFourZeroThreeBeforeTheStoreRead` | registry returns null → `403 CONTEXT_DENIED`, null action, head 0, reader 0 |
| form vs resolution | `malformedFormWinsOverARegistryOutage` | `?after=abc` with a failing registry → `400`, not `503`; head/reader 0 |
| form, unknown | `unknownQueryParameterIsRefusedInTheRequestFormStep` | `?snapshot=new&unknown=1` → `400 INVALID_CURSOR`, null action, registry 0, head 0, reader 0 |
| form, repeat | `repeatedQueryParameterIsRefusedInTheRequestFormStep` | `?after=1&after=2` → same counters all zero |
| malformed payload | `malformedSignedPayloadIsFourHundredBeforeRetiredAndContextChecks` | forged signature-valid but non-JSON payload → `400`, null action, head/reader 0 |
| retired schema | `retiredSchemaCursorIsFourTenBeforeAnyContextCheck` | forged signature-valid `version:2` payload whose context also differs → `410 RESYNC_REQUIRED` (not `403`), head/reader 0 |
| expired | `expiredCursorIsFourTenBeforeAContextMismatch` | expired cursor bound to another transaction → `410` (not `403`), head/reader 0 |
| head 503 | `headUnavailableIsFiveZeroThreeBeforeAnyRead` | `store.head` throws while `after=4` → `503` before the later `after > H` `400`; head 1, reader 0 |
| after > H | `afterBeyondTheDurableHeadIsInvalidRatherThanSilentlyClamped` (strengthened) | `?after=4` with head 3 → `400`, null action, **head 1, reader 0** |
| head 404 (continuation) | `unknownTargetOnAContinuationIsFourZeroFourAfterTheHeadStep` | head absent → `404 NOT_FOUND`, null action, head 1, reader 0 |
| lastSequence bound | `boundLastSequenceBeyondTheSnapshotHeadIsFourHundredAfterTheHeadRead` | cursor `lastSequence=5`, snapshot head 3 → `400`, head 1, reader 0 |

Existing cases continue to pin `401`, context `403`, representation `410`, changed `R`/`F` `410`,
tampered signature `400`, expired `410` and the `200` page.

## 5. Executed results

Raw streams and immediate exit files are in `runs/`.

| Run | Command | Exit | Result |
| --- | --- | --- | --- |
| compile | `mvn -o -B -DskipTests test-compile` | **0** | BUILD SUCCESS |
| focused console suites | `mvn -o -B test -Dtest='ConsoleHistoryRouteTest,ConsoleHistoryRouteDynamoIntegrationTest,ConsoleOriginVerificationGateTest,ConsoleAuthorizationRouteTest,ConsoleSubmissionAtomicIntegrationTest,ConsoleHandlerCompositionTest,ConsoleDevServerTest,DevStubTokenIssuerTest' -DfailIfNoTests=true` | **0** | **90 tests, 0 failures, 0 errors, 0 skipped** — BUILD SUCCESS |

Per class (`runs/focused-console-suites.stdout`):

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
| **Total** | **90** |

Test-development disclosure: an earlier green execution of the same eight suites (87 tests, exit 0)
was taken after the first batch of cases and before three resolution/head-stop cases were added. The
recorded run above is the final test set; both executions were green, and the final `runs/` streams
are the authoritative artifact.

## 6. What this proves and what it does not

**Proves (in-process, test-supplied seams).** Within `ConsoleHistoryRoute` with a stub
store/reader/registry and an in-process codec, the history route refuses in the C-3.3 order; a
signature-valid retired-schema cursor is `410` before any context `403`; an `after` beyond the frozen
head is `400` only after exactly one head read and no reader call; unknown and repeated parameters are
refused in the form step before resolution or any store read; and each status/action body matches the
reviewed error set.

**Does not prove.** No real issuer/JWKS/signature verification, no deployed edge, CloudFront, WAF,
API Gateway authorizer, Lambda invocation or Keycloak; no cursor-key custody; no server-side read-pass
store, TTL, compaction or projection; no browser or UI. **N-02 is not closed by these bytes.** Every
traceability row keeps `independent verification: PENDING`.

## 7. Non-claims

No Git write, stage, commit, push, PR, merge, deploy, release, publication, AWS call, credential
flow, provider mutation, approved-specification edit or test weakening occurred. No onward
delegation, no model override. Local evidence is not independent QA, Security review or human
acceptance, and the domain re-check's advisory PASS is not adoption or approval.
