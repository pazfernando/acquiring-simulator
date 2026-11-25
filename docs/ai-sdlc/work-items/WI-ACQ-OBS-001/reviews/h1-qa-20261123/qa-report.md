# Independent QA report — H1 construction slice (origin-verification gate + read-only history)

- Reviewer: ai-sdlc-qa (independent verification). Date: 2026-11-23.
- Runtime/model observed: OpenClaw subagent, provider `nan`, model `glm5.3-flash`, no override
  (`execution-model.json`).
- **Verdict: FAIL** — all mandatory scoped checks were executable and most passed, but two executed
  checks violated scoped contract criteria (QA-DEF-1, QA-DEF-2). Both are reproduced below with
  severity and reproduction. Nothing was silently omitted; unverifiable-at-this-stage items are
  listed explicitly with reasons.
- Scope: H1 criteria only (N-02 origin-verification gate; D-6/R-2 read-only history protocol).
  Slice K1 (real JWKS/issuer token verification) is out of scope and was not executed.

## 1. Reviewed revisions and binding

| Item | Value |
| --- | --- |
| Checkout realpath / git toplevel | `/home/pazfernando/projects/acquiring-simulator` (both) |
| Branch / HEAD at verification start | `feat/wi-acq-auth-001` / `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` |
| Working tree | dirty by design (48 modified/untracked paths, pre-existing continuation work); no Git mutation performed by QA |
| Normative base | `design/contract-deltas/continuation-rev3-20261123/`, manifest `2000bb004e68e6d2974bef3067baeae6774f1922a21dca4ea18fecf6944ab467` — re-verified: SHA-256 of `OUTPUTS.SHA256SUMS` matches; `sha256sum -c` = 21/21 OK |
| Amendment read | `continuation-rev3-dr301-20261123/` (`old-prior-fixtures.json` `1d3f1406…d5f5` observed; C3 payment oracles — not exercised here, H1 has no payment path) |
| Boundary delta | `n02-origin-verification-20261123/contract-delta.md` `15959975…f3ad` observed (matches pin) |
| Implementation evidence | `evidence/continuation-h1-20261123/` read as developer claims; `slice-files.sha256` re-verified 25/25 OK (2 non-hash comment lines) |
| Functional sources | guide §3, §5 Operation A, §13, §14 (seven-day history, same-source reads, no resubmission), §15 cross-cutting observability |

## 2. Expected behaviour derived from contracts BEFORE reading implementation

From N-02 §5–§7, §9.2, §11: closed classification ABSENT/EMPTY/DUPLICATE (multi-value, comma-coalesced
HTTP API v2 form, case-variant names)/MISMATCH/VERIFIED; constant-time byte-exact case-sensitive
value comparison, length difference fails without content comparison; initialization fails on
absent/empty/short/bad-alphabet `EDGE_ORIGIN_VERIFY`; gate is the first application step, before
dispatch, 404/405, body read, credential seam, rate limit, registry and any service call; refusal =
403 with exact bytes `{"version":1,"error":"EDGE_ORIGIN_NOT_VERIFIED","action":null}`, no echo of
value/length/prefix/hash/reason, no CORS allowance; exactly one closed-field denial record;
sink failure never changes the response; a verified value is not authentication.

From D-6/R-2 (`contracts.md`) + `wire-fixtures.json` (reviewed packet bytes): `GET
/console/v1/transactions/{id}/events`; strong base-table HEAD then bounded Query fixing H/R/F;
opaque signed cursor binding version, issuer, azp, subject, environment, registry mandate
identity/version, context (acquirerRef, merchantRef, terminalRef when target-specific),
transactionId, generation, epoch when live, H, R, F, lastSequence, issuedAt, expiresAt, keyId, and
revealing no token/key material; cursor TTL ≤ mint+900s; re-authentication before and after the
store read; error bodies exactly `{version:1,error:<code>,action:null|RESYNC_REQUIRED}` with only
410 carrying an action; pinned codes INVALID_CURSOR(400), UNAUTHENTICATED(401), CONTEXT_DENIED(403),
NOT_FOUND(404), RESYNC_REQUIRED(410) and **AUTHORIZATION_UNAVAILABLE(503)** per the reviewed
`historyErrors` fixtures; page schema exactly the 12 reviewed O6 keys (version, transactionId,
snapshotHead, expiryRevision, firstRetainedSequence, events, coveredThrough, expiredRanges,
gapRanges, nextCursor, completeThroughSnapshot, retentionTruncated); ≤100 events / 256 KiB per page,
1000 events / 1 MiB per pass; complete = every sequence through H represented or explicitly expired;
gap halts coveredThrough and suppresses the next cursor; expired prefix reported as a range; no GSI
or duplicate projection; HTTP-only read valid without WebSocket; environment from deployment
configuration; **no payment side effect of any kind**.

## 3. Executed checks and results (all executed by QA, not reused from the implementer)

### 3.1 Toolchain and suite runs

| Command | Result | Log |
| --- | --- | --- |
| `mvn -o -DskipTests compile` (JDK 25) | exit 0 | `log-01-compile.txt` |
| `mvn -o test -Dtest='ConsoleOriginVerificationGateTest,ConsoleHistoryRouteTest,ConsoleHistoryRouteDynamoIntegrationTest,ConsoleAuthorizationRouteTest,ConsoleSubmissionAtomicIntegrationTest' -DfailIfNoTests=true` | **exit 0 — 49 tests, 0 failures, 0 errors, 0 skipped** (4 DynamoDB Local integration + 13 auth route + 9 gate + 20 history unit + 3 atomic submission) | `log-02-focused-tests.txt` |
| QA driver `tools/QaH1Driver.java` (QA-owned fakes, compiled main classes) | **63 checks: 61 PASS, 2 confirmed defects** | `log-03-qa-driver.txt` |
| `sha256sum -c slice-files.sha256` | 25/25 OK | `log-04-slice-hash-verify.txt` |

Environment: JDK 25.0.5, Maven 3.10.1 (offline), Docker 29.1.3, Testcontainers 2.0.5,
`amazon/dynamodb-local:2.6.1`, host `fernando-OC`.

### 3.2 Gate criteria (driver G1–G26 + suite)

| Criterion | Result | Evidence |
| --- | --- | --- |
| Init fails on absent/empty/short/bad-alphabet expected value | PASS | G1–G4 |
| Minimum valid value initializes | PASS | G5 |
| ABSENT / EMPTY / whitespace-only / DUPLICATE (two values, case-variant names, correct-then-wrong) / comma-coalesced forms / MISMATCH (one-char, case-transformed) all refuse | PASS | G6–G15 (OV-02/04/05/06/07/08/09/10 analogues) |
| All four refusals byte-identical, exact §7 body, no echo of value | PASS | G16 (bodies distinct-count = 1) |
| Header-name match case-insensitive; value comparison case-sensitive | PASS | G17, G15 |
| Verified value reaches application unchanged; gate is not authentication (credential seam 401) | PASS | G18–G20 (OV-01/17 analogues) |
| Sink failure contained; response unchanged; exactly one emit | PASS | G21 |
| Denial record: closed 9-field set, correct reason/event, bounded caller-controlled fields, no echo | PASS | G22–G24 |
| Gate precedes application's own 404/405 handling; no application call on refusal | PASS | G25 (and zero application invocations in G6–G15) |
| Entry point cannot be constructed without the gate (OV-GATE-REQUIRED) | PASS | G26 + `ConsoleIntegrationEntryPoint` requires all five collaborators |
| Constant-time comparison | PASS by construction (source: `MessageDigest.isEqual`, length-first) — timing property not measurable here | code inspection |
| OV-ZERO-EFFECT (no claim/dispatch/event/journal/send/registry/rate-limit on refusal) | PASS at application level (no dispatch); platform-level counters not observable locally | G6–G15, G25 |

### 3.3 History read criteria (driver H1–H23 + suites incl. DynamoDB Local integration)

| Criterion | Result | Evidence |
| --- | --- | --- |
| New snapshot pins frozen H/R/F; page schema exactly the reviewed 12 keys in order | PASS | H1 |
| Logically expired prefix reported as range, retentionTruncated true | PASS | H2 |
| Pagination ≤100 events/page; opaque cursor issued; cursor TTL = mint+900; payload field set closed, binds principal/azp/audience/environment/mandate/txn/H/R/F/generation; no token/key material | PASS | H3a–H3e, H3g, H23 |
| Continuation freezes snapshot when source grows; nothing beyond H disclosed | PASS | H3h–H3k (matches W02/G5 semantics) |
| Tampered/unknown/future cursor → 400 INVALID_CURSOR exact fixture body | PASS | H4, H6 |
| Expired cursor → 410 RESYNC_REQUIRED with action, exact fixture body | PASS | H5 |
| Changed R → 410; truncated F → 410; retired mandate generation → 410 no payload | PASS | H7–H9 |
| Cursor bound to another subject / another transaction → 403 CONTEXT_DENIED exact body | PASS | H10–H11 |
| Gap halts coveredThrough, reports range, suppresses next cursor (fixture gapPage shape) | PASS | H12 |
| Authorized unknown target → 404 NOT_FOUND exact body, nothing disclosed | PASS | H13 |
| Store failure → 503 no payload | PASS (status) | H14 |
| Registry outage before read / after store read → 503 no payload | PASS (status) | H15–H16 |
| Token expiry between the two authentications → 401 no payload | PASS | H17 |
| Missing/duplicated Authorization → 401 before any store read | PASS | H18a–H18b |
| Wrong method → 405 empty; unknown path → empty 404 (transport class, distinct from versioned NOT_FOUND) | PASS | H19a–H19b |
| Malformed pagination → 400 INVALID_CURSOR (provisional mapping, see §6) | PASS | H20 |
| `after` beyond durable HEAD → 400, not clamped | PASS | H21 |
| **No payment side effect of any kind from history reads** | PASS | H22: fake store `append`/`find` counters zero across all history scenarios; route constructor admits no payment collaborator (source); event store never written |
| Cursor TTL above 900 refused at construction | PASS | H23 |
| DynamoDB Local integration: retained rows read from the real base table, frozen head on growth, changed R → 410, physically missing retained row = explicit gap (not fabricated) | PASS | 4/4 green in `log-02-focused-tests.txt` |
| Payment non-regression (existing C3 submission route) | PASS | 13+3 existing console tests green |

## 4. Defects (executed checks that violated scoped criteria)

### QA-DEF-1 — Signed cursor payload does not bind the token issuer — severity LOW (MEDIUM if issuer federation ever widens)
- Contract: D-6 binding list and the reviewed `wire-fixtures.json` `cursorUnsignedHarnessBinding`
  both bind `issuer`. Observed payload keys (decoded by QA from a minted cursor): version, keyId,
  subject, authorizedParty, audience, environment, mandateFingerprint, generation, acquirerRef,
  merchantRef, transactionId, snapshotHead, expiryRevision, firstRetainedSequence, lastSequence,
  passedEvents, passedBytes, issuedAt, expiresAt — **no `issuer`** (`ConsolePrincipal` carries none).
- Reproduction: mint a page via `GET /console/v1/transactions/TX-001/events`, base64url-decode the
  cursor payload; `issuer` absent. Driver: `QA-DEF-1` in `log-03-qa-driver.txt`.
- Impact: mitigated in the current deployment because every page re-authenticates with a live token
  that the authorizer validates against the exact configured issuer; a foreign-issuer holder of
  subject/azp/audience cannot pass re-authentication. Deviation is nevertheless normative text not met.

### QA-DEF-2 — 503 error body code `UNAVAILABLE` instead of pinned `AUTHORIZATION_UNAVAILABLE` — severity MEDIUM
- Contract: reviewed `wire-fixtures.json` `historyErrors["503"]` =
  `{"version":1,"error":"AUTHORIZATION_UNAVAILABLE","action":null}`; D-6 requires exact error bodies.
  Observed for registry outage and store failure:
  `{"version":1,"error":"UNAVAILABLE","action":null}`.
- Reproduction: registry throw on first resolution (H15) or store `head` failure (H14) → observed
  body above; byte-compare against fixture fails. Driver: `H14b` in `log-03-qa-driver.txt`.

### QA-DEF-3 — Cursor context binding lacks `terminalRef` — severity LOW (advisory-leaning)
- D-6 binds context "(acquirerRef, merchantRef, terminalRef when target-specific)"; the reviewed
  fixture's context includes `terminalRef: TERMINAL-A`. The implementation deliberately omits it
  (`ConsoleHistoryCursorCodec` javadoc: demo trusted context exposes no terminal; the event partition
  is not terminal-scoped; `AuthorizedContext` has no terminal field). Recorded as a contract/fixture
  deviation needing either a contract clarification or a binding when the registry supplies a terminal.
  Same reproduction route as QA-DEF-1.

### Recorded observations (not defects by themselves)
- `registryMandateId`/`registryVersion` are represented by a SHA-256 mandate-content fingerprint
  (callerId + refs + sorted routes). Content change → 410 (H9 verified); a registry version bump with
  unchanged content does not retire the generation. Semantically defensible; differs from the
  fixture's named fields. Recommend explicit contract wording.
- Cursor payload omits the `epoch` key entirely; the reviewed fixture carries `epoch: null` for the
  HTTP-only read-pass. Documentary difference only.
- `?cursor=X&after=N` silently ignores `after` (no error); unknown query params are rejected.
  Not pinned by D-6; flagged for contract clarity.
- Malformed pagination (`snapshot` not `new`, non-numeric `after`, unknown params, empty `cursor`)
  maps to 400 INVALID_CURSOR — a provisional mapping (D-6 names INVALID_CURSOR only for
  unknown/tampered cursors). Implementer-disclosed ambiguity QA-side confirmed; needs contract text,
  not silent acceptance.

## 5. Could not be verified here, and why

1. Deployed-edge behaviour (CloudFront origin-header injection/overwrite OV-11/12, WAF, edge
   pass-through, N-03 retest criteria 1–6, direct `api_fqdn` OV-02/13 through a real edge): requires a
   deployed stack; no cloud/edge execution authorized. **N-02 is not closed by these bytes alone.**
2. Platform 401 body (OV-03) and API Gateway authorizer ordering: platform layer not present locally.
3. Real token verification (JWKS/signature/issuer/audience/expiry truth): the console decoder is a
   test double; that is slice K1's subject. Gate and route were exercised with the authorizer seam only.
4. Server-side read-pass store, TTL/compaction, GSI absence in the real table schema: no table added
   (disclosed implementer ambiguity 2); expiry is logical via `expiryRevision`. The DynamoDB Local
   integration tests read the real base-table partition; GSI absence was checked by source, not schema.
5. Cursor signing-key sourcing/rotation: codec takes an injected key (`keyId`+32B minimum); deployed
   composition unresolved (ambiguity 3).
6. Log/metric emission into real CloudWatch, value absence from real log formats, alarm behaviour:
   §10 observability verified only at the application-record level (closed field set, single emit,
   sink containment); no deployed log read-back possible.
7. Implementer's red-run reproduction: the red revision's sources were not preserved; QA could not
   re-execute it. The claim "no case deleted/disabled/weakened" rests on identical per-class counts
   (4+9+20) with 0 skipped in both red and green runs and on failure signatures — consistent with what
   QA independently re-executed green (same 33 focused cases plus 16 existing), but the red→green
   editing sequence itself is not byte-level provable.
8. WebSocket route, browser/UI, subscription ACK/catch-up (WS-specific D-6 clauses): separate surface,
   out of H1 scope.

## 6. Integrated end-to-end payment

**None exists.** The slice adds only the boundary gate and a read-only history route; it creates no
transaction, no projection, no dispatch capability, and performs no payment send (route admits no
payment collaborator; store append/find never invoked from history reads — H22; atomic-submission
non-regression tests remain green). No Operation A execution against a real or simulated issuer was
run in this verification; DR3-01's C3 prior-reference oracles were read as adopted contracts but not
executed because H1 contains no payment path.

## 7. Verdict

**FAIL for the scoped H1 criteria** — caused by QA-DEF-1 and QA-DEF-2 (plus QA-DEF-3 recorded).
Everything else executed passed: 49/49 suite tests and 61/63 driver checks. Defects are implementation
corrections (Implementation, via Coordinator); QA-DEF-2 is a one-line error-code fix, QA-DEF-1/3 need
an issuer/terminal binding decision (contract clarification may be required for QA-DEF-3). Changed
code requires independent re-verification of the affected criteria. This report does not approve
acceptance, deployment or N-02 closure; deployment-gate retests (§12 of the N-02 delta) remain open.

## 8. Deliverables

`qa-report.md` (this file), `PROGRESS.md`, `log-01-compile.txt`, `log-02-focused-tests.txt`,
`log-03-qa-driver.txt`, `log-04-slice-hash-verify.txt`, `tools/QaH1Driver.java`, `SHA256SUMS`,
`execution-model.json`. No product source, contract, work-item or other specialist artifact was
modified; no Git/provider mutation; no cloud, issuer or edge execution.
