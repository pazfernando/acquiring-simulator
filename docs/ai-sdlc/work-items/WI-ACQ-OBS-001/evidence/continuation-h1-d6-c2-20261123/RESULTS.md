# Continuation H1 — D-6 C-1.3 ordering and C-2 cursor combinations: results (2026-11-23)

Developer evidence only: **not** independent QA, **not** Security review, **not** human acceptance.
No push, no PR, no merge, no deploy, no cloud call, no new feature.

## 1. Input and binding

| Item | Value |
| --- | --- |
| Domain review | `reviews/d6-clarifications-20261123/domain-review.md` |
| Domain review SHA-256 | `d6a57e0ca4f0e22c838c8e397bd5d576b95e7f6fa9d2a880989ef6b9141464b9` — re-verified with `sha256sum`, matches the coordinator pin |
| Sections applied | **C-1.3 / §7** (post-read context `403` before fingerprint `410`); **C-2** (`cursor` with `after` or `snapshot` refused) |
| Explicitly not implemented | C-3 malformed-pagination taxonomy and the two C-3 failure-order corrections; its corrected text is still being authored |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch / HEAD | `feat/wi-acq-auth-001` / `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` (no commit made) |
| JDK / Maven | Temurin `25.0.5+8`; Maven `3.10.1` offline (`-o`) |
| Model | `nan/deepseek-v4-flash`, no override |
| Changed-file hashes | `changed-files.sha256` (1 main, 1 test) |

## 2. Changes

### 2.1 C-1.3 — post-read context check before the fingerprint check

`ConsoleHistoryRoute.reauthenticateAfterRead` compared `fingerprint(mandateAfter)` (retired
generation → `410 RESYNC_REQUIRED`) **before** comparing the resolved-after context triple
(`acquirerRef`/`merchantRef`/`terminalRef`) with the mandate resolved before the read (`403
CONTEXT_DENIED`). C-1.3 says the context-triple mismatch is `403` and "checked first". The
pre-read continuation already compared context first; only the post-read path diverged.

- Reordered: the `403 CONTEXT_DENIED` context comparison now runs first, the `410
  RESYNC_REQUIRED` fingerprint comparison second.
- Everything else is unchanged: both authentications still run (before the store read and after
  it), the post-read `401 UNAUTHENTICATED`/`503 AUTHORIZATION_UNAVAILABLE` refusals are unchanged
  and still precede disclosure, and the pre-read continuation order is untouched.
- Consequence: a request that is both context-mismatched and generation-retired now returns `403`;
  a request that is only generation-retired still returns `410 RESYNC_REQUIRED`. No payload is
  disclosed in either case.

### 2.2 C-2 — `cursor` combined with `after` or `snapshot`

`ConsoleHistoryRoute.queryOf` previously let `cursor` win: when exactly one non-empty cursor was
present, any `after` or `snapshot` in the same request was silently ignored and the continuation
was served.

- Now: when a cursor is present together with `after` or with `snapshot`, the request is refused
  with `400 INVALID_CURSOR` and a null action — the same provisional malformed-pagination mapping
  already recorded in the H1 evidence.
- Position: `queryOf` is evaluated after authentication (`401` still wins) and before mandate
  resolution and every store/reader call, so the refusal precedes both. The registry and the event
  store are never consulted for these combinations.
- Unchanged: the initial (`snapshot`/`after`) and continuation (cursor alone) forms, all other
  malformed inputs, and every other behaviour.

## 3. Focused tests added (`ConsoleHistoryRouteTest`, 24 → 29)

| Test | Pins |
| --- | --- |
| `continuationContextMismatchWinsOverARetiredGeneration` | cursor bound to another transaction *and* a retired fingerprint → `403 CONTEXT_DENIED`, store head never called |
| `postReadContextMismatchWinsOverARetiredGeneration` | registry returns another merchant after the read (context *and* fingerprint differ) → `403`, both registry calls observed, reader called once (the refusal is genuinely post-read), no `events` in the body |
| `postReadRetiredGenerationAloneStaysFourTen` | registry reissues the same acquirer/merchant/terminal with a different caller after the read → `410 RESYNC_REQUIRED` with the action, no `events` |
| `cursorCombinedWithAfterIsRefusedBeforeMandateResolutionAndTheStore` | `?cursor=X&after=0` → `400 INVALID_CURSOR`, null action, registry calls 0, `headCalls` 0, reader calls 0 |
| `cursorCombinedWithSnapshotIsRefusedBeforeMandateResolutionAndTheStore` | `?cursor=X&snapshot=new` → same |

The two post-read tests assert two registry calls, so they fail if the route stops re-resolving the
mandate; the combination tests count registry calls and store/reader calls, so they fail if refusal
moves after either.

## 4. Executed results

Raw streams and immediate exit files are in `runs/`.

| Run | Command | Exit | Result |
| --- | --- | --- | --- |
| compile | `mvn -o -B -DskipTests test-compile` | **0** | BUILD SUCCESS |
| focused console suites (single run) | `mvn -o -B test -Dtest='ConsoleHistoryRouteTest,ConsoleHistoryRouteDynamoIntegrationTest,ConsoleOriginVerificationGateTest,ConsoleAuthorizationRouteTest,ConsoleSubmissionAtomicIntegrationTest,ConsoleHandlerCompositionTest,ConsoleDevServerTest,DevStubTokenIssuerTest' -DfailIfNoTests=true` | **0** | **79 tests, 0 failures, 0 errors, 0 skipped** — BUILD SUCCESS |

Per class (`runs/focused-console-suites.stdout`):

| Class | Tests |
| --- | --- |
| `ConsoleHistoryRouteTest` | 29 |
| `ConsoleHistoryRouteDynamoIntegrationTest` | 4 |
| `ConsoleOriginVerificationGateTest` | 9 |
| `ConsoleAuthorizationRouteTest` | 13 |
| `ConsoleSubmissionAtomicIntegrationTest` | 3 |
| `ConsoleHandlerCompositionTest` | 5 |
| `ConsoleDevServerTest` | 9 |
| `DevStubTokenIssuerTest` | 7 |
| **Total** | **79** |

The count is the prior 74 plus the five new cases; nothing was removed or disabled
(`Skipped: 0`). This run is the single focused run; no red run is claimed for this step.

## 5. What this proves and what it does not

**Proves (in-process, test-supplied seams).** Within `ConsoleHistoryRoute` with a stub store/reader
and a stub registry, the post-read refusal order is context `403` before generation `410`, on both
the continuation and post-read paths; and a cursor combined with `after` or `snapshot` is refused
`400 INVALID_CURSOR` with a null action before mandate resolution and before any store read.

**Does not prove.** No real issuer/JWKS/signature verification, no deployed edge, CloudFront, WAF,
API Gateway authorizer, Lambda invocation or Keycloak; no cursor-key custody; no server-side
read-pass store, TTL, compaction or projection; no browser or UI. C-3's malformed-pagination
taxonomy and failure order are **not** implemented here. **N-02 is not closed by these bytes.**
Every traceability row keeps `independent verification: PENDING`.

## 6. Non-claims

No Git write, stage, commit, push, PR, merge, deploy, release, publication, AWS call, credential
flow, provider mutation, approved-specification edit or test weakening occurred. No onward
delegation, no model override. Local evidence is not independent QA, Security review or human
acceptance, and the domain review's advisory verdict is not adoption or approval.
