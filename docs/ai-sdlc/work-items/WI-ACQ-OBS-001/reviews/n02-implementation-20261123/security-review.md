# Security implementation re-review — N-02 origin-verification gate and history slice (bounded)

## 1. Review metadata

- Reviewer: ai-sdlc-security (independent; no onward delegation). Date: 2026-11-23 (America/Bogota).
- Runtime/model observed for this review: **`nan/glm5.3-flash`**, the session's configured default.
  No model override was applied; no fallback was needed and none was used.
- Commission: bounded implementation-level re-review deciding whether **N-02 can move to CLOSED at
  authoring**. Inputs: this role's contract documents, product `PROJECT.md`/`WORK_ITEM.md`, the two
  standing prior reviews (`f5696ed9…ba50` FAIL, `e5937fb8…eb2e1` bounded PASS / N-02 PARTIAL), the
  contract delta `15959975…f3ad`, and the implementation slice evidence
  `evidence/continuation-h1-20261123/`.
- Scope: exact authored application bytes of the console slice plus its tests and the recorded run
  evidence. Nothing else is judged: no K1 issuer work, no IAM execution, no deployment, no IaC
  re-survey beyond what the delta/§5–§7 questions require.
- Writes confined to `reviews/n02-implementation-20261123/`. No reviewed input modified.

## 2. Input integrity and method (actual commands, re-executed by me)

- `sha256sum` recomputation of the three document pins — all matched exactly (§1 values above).
- `sha256sum -c` over `evidence/continuation-h1-20261123/slice-files.sha256`: **25/25 OK** against
  the current working tree (20 `src/main` + 4 `src/test` files under `console/`, plus the manifest's
  two `git status --short` lines). The green-run bytes are the bytes I reviewed.
- Read of every exit/stdout file in the evidence directory; exits match `RESULTS.md` (compile 0/1/0;
  red run 1; green run 0; existing-console run 0).
- Full reads of all application and test files listed in `PROGRESS.md` §3; targeted greps for
  `@Disabled`/`@Tag`/assumptions, `OV-` identifiers, and entry-point references.
- **Git disclosure:** three **read-only** Git commands (`git log --oneline`, `git rev-parse HEAD`,
  `git status --porcelain`) were executed during initial reconnaissance before the no-Git constraint
  was applied to the working method. No write, stage, commit, push or provider operation was
  performed; the constraint is honored for the remainder of the run. Observed HEAD `5bbd4eb5…`
  matches the implementing run's recorded binding; the working tree is dirty by design and was
  neither reverted nor reformatted. Per role routing this inspection should have been requested
  through Coordinator; it is recorded here as a disclosed deviation, not as independent branch
  attestation.
- No cloud, issuer, browser, IAM or stack execution; no credential flow; no code edits; no scan tool
  beyond my own hash/grep checks. Nothing is deployed; nothing below is runtime evidence.

## 3. What the delivered bytes must implement (delta §5–§7 checklist)

Judged against `contract-delta.md` (`15959975…f3ad`) §5.1–§5.4, §6.1–§6.3, §7, plus §9.2 (behavioural
assertions) and §10 (denial record), which §12 of the delta makes the authoring-scope obligation.

## 4. Q1 — Does `ConsoleOriginVerificationGate` implement §5 faithfully?

**Yes, in every contract-bound dimension, verified against the pinned bytes.**

| Delta requirement | Delivered bytes (verified) | Match |
| --- | --- | --- |
| Fixed name `X-Origin-Verify` | `HEADER_NAME = "X-Origin-Verify"` (`ConsoleOriginVerificationGate`) | ✓ |
| Env var `EDGE_ORIGIN_VERIFY` | `ENVIRONMENT_VARIABLE = "EDGE_ORIGIN_VERIFY"`; `fromEnvironment()`/`from(Function)` read it once | ✓ |
| Case-insensitive occurrence detection, byte-exact case-sensitive comparison | `ConsoleHttpRequest.headerValues` matches names case-insensitively and preserves duplicates; comparison on raw bytes | ✓ |
| Closed exhaustive set ABSENT/EMPTY/DUPLICATE/MISMATCH/VERIFIED | `evaluate()` returns exactly these five `Reason`s; no other outcome exists | ✓ |
| ABSENT → REFUSE | zero occurrences → `Result.refused(ABSENT)` | ✓ |
| EMPTY (empty or optional-whitespace only) | single occurrence empty or SP/HTAB-only → `EMPTY` | ✓ |
| DUPLICATE (>1 occurrence, comma-coalesced single value, case-differing map entries) | `values.size() > 1` → `DUPLICATE`; `indexOf(',') >= 0` → `DUPLICATE`; case-variant map entries collapse through the case-insensitive reader into size > 1 | ✓ (tested incl. first-correct-second-wrong and comma form) |
| MISMATCH incl. one-char-different and case-transformed | byte comparison through `MessageDigest.isEqual` on UTF-8 bytes | ✓ |
| VERIFIED only continue | sole non-`REFUSE` outcome | ✓ |
| Constant-time over the full value; length failure without content comparison | `MessageDigest.isEqual(expected, received)` — documented constant-time content comparison; length difference fails without content comparison (vendor-guaranteed behaviour of `MessageDigest.isEqual`) | ✓ |
| No trimming, case folding, percent-decoding, normalization | none present; whitespace-padded correct content is `MISMATCH` (test asserts it) | ✓ |
| No partial acceptance, log-and-continue, fallback, relax flag | no flag, no second path; the gate's API cannot express one | ✓ |
| Expected value fail-closed init: absent/empty/short/out-of-alphabet fails | `configured()` throws `IllegalStateException` on null/empty, <32 chars, or non-`[A-Za-z0-9._~-]` content; `from()` propagates; no default, no placeholder, no degraded mode | ✓ (§5.3) |
| Result carries only the reason class, no echo material | `Result` is `(Decision, Reason)`; nothing else | ✓ |

One deliberate, disclosed mapping: the delta's §8.1 closed token alphabet is realized as
`[A-Za-z0-9._~-]+` with `MINIMUM_LENGTH = 32`, matching the authored Terraform variable validation
(`edge_origin_verify` validated ≥32 chars of token-safe ASCII, per the n02-origin review §4). The
generated value (length 48, `special = false`) satisfies this alphabet. Initialization is therefore
consistent with both provider sides of the control.

## 5. Q2 — Placement: is the gate the first application step (§6)?

**Yes.** `ConsoleIntegrationEntryPoint.handle(request, identity)` evaluates `gate.evaluate(request)`
before `application.apply(request)`; the application function is the only path to any route. A
refusal therefore precedes route dispatch, method/path matching (including the existing 404/405
handling), body read, media-type validation, credential seam, rate-limit hook, registry read and any
service call — the exact list of §6.1 step 5. The zero-effect property is tested
(`refusalPerformsNoDispatchCredentialRateLimitOrRegistryAction`: credential calls 0, rate-limit calls
0, service calls 0, registry instrumented to fail the test if read), and the refusal tests use an
application function that throws `AssertionError` if reached.

- **Required collaborator / no bypass switch (§6.1 closing, OV-GATE-REQUIRED):** the gate is a
  constructor-required collaborator (`Objects.requireNonNull` with the message "a console entry
  point requires the origin verification gate"); there is no other constructor, no request field,
  environment variable or flag that bypasses it. Tested (`entryPointCannotExistWithoutTheGate`).
- **Ordering parity with the route (§6.1 step 6):** `ConsoleAuthorizationRoute`'s internal order is
  unchanged (method/path → credential seam → rate limit → media type → body → trusted context → one
  service call). The gate sits strictly above it.
- **Not authentication, authorizer independence (§5.4, OV-AUTHZ-INDEPENDENT):** the entry point never
  reads `requestContext.authorizer` or any principal; `verifiedOriginIsNotAuthentication…` proves a
  verified header still receives `401 UNAUTHENTICATED` from the credential seam with zero service
  calls. The gate's Javadoc states explicitly that it is not an authentication factor and does not
  replace, weaken or reorder the JWT authorizer.
- **Denial observability (§10):** exactly one `console_origin_verification_denied` record per
  refusal, emitted before the response is returned, with the closed nine-field set
  (`event, reason, requestId, sourceIp, host, httpMethod, path, timestamp, appVersion`), `event`
  pinned to the contract name, and caller-controlled fields (`host`, `httpMethod`, `path`)
  length-bounded (255/16/2048) and printable-ASCII-checked with replacement by `"invalid"` — never
  partially echoed (tested with a hostile `Host` containing CRLF injection material and a 4096-char
  path). The sink failure is contained: a throwing sink still yields the fixed 403 (tested). The
  record contains no received value, expected value, length, hash, prefix, comparison artifact,
  `Authorization` value or body — structurally, `ConsoleOriginDenial` has no field for them and
  `StandardStreamOriginDenialSink` renders only the closed field set.

## 6. Q3 — Response contract for a refusal (§7)

- **Status/body:** `DENIAL_BODY = {"version":1,"error":"EDGE_ORIGIN_NOT_VERIFIED","action":null}`
  returned with status 403 via `ConsoleHttpResponse.json` (`Content-Type: application/json`). Byte
  comparison against §7: identical, compact, fixed key order, no trailing newline.
- **One code for all four reasons; no echo:** `everyRefusalReturnsTheSameFixedBytesWithoutEcho`
  asserts ABSENT/EMPTY/DUPLICATE/MISMATCH/DUPLICATE-comma responses are byte-identical to the
  constant and contain none of the expected value, the wrong value, the reason words, or the app
  version; no `Access-Control-Allow-*` header is present. No `Retry-After`, `WWW-Authenticate` or
  correlation hint is emitted by the type (`ConsoleHttpResponse.json` adds only `Content-Type` and
  `Cache-Control: no-store`, which also serves the §6.3 "a denial is never cached" requirement).
- **D-6 shape reuse / C3 envelope not reused:** the body borrows only the `{version, error, action}`
  shape with `action: null`; no C3 business key or code appears. The route's own business denials
  keep the C3 envelope (existing route tests assert `CONTEXT_DENIED` there), so the boundary body
  does not bleed into business denials and vice versa.
- **N-03 non-claim:** the gate changes no other response; the platform 401 and C3 refusals are
  untouched by these bytes. Whether the deployed edge passes them through unchanged remains the
  standing N-03 runtime retest — unchanged status, not reopened by this slice.

## 7. Q4 — OV oracle suite: present, and the edge-independent subset passes

The delta §9.2 names four behavioural assertions; the delivered tests realize their substance under
descriptive method names. **The fixed `OV-*` identifiers were not carried into test names, comments
or the evidence** — see finding A-1. Substance mapping established by this review:

| Oracle (delta) | Delivered evidence | Result |
| --- | --- | --- |
| OV-GATE-REQUIRED (§9.2) | `entryPointCannotExistWithoutTheGate` + constructor discipline | present, passing |
| OV-NO-ECHO (§9.2) | `everyRefusalReturnsTheSameFixedBytesWithoutEcho` + `callerControlledRecordFieldsAreBoundedAndNeverEchoed` | present, passing |
| OV-ZERO-EFFECT (§9.2) | `refusalPerformsNoDispatchCredentialRateLimitOrRegistryAction`; refusal tests' unreachable-application assertions | present, passing |
| OV-AUTHZ-INDEPENDENT (§9.2) | `verifiedOriginIsNotAuthenticationAndTheCredentialSeamStillRefuses`; `verifiedOriginReachesTheApplicationUnchanged` | present, passing |
| OV-02/04/05/06/07/08/09/10 (classification) | `classificationIsClosedAndExhaustive` (all reason branches incl. comma coalescing, case-variant name, one-char diff, case-transformed value, padded value) | present, passing |
| OV-13 (history route refused with identical bytes) | refusal-body tests exercise the history path; `ConsoleHistoryRouteTest` covers the route's own denials | present, passing |
| OV-14 (ledger/counters zero-effect) | counting test (submission route) | present, passing |
| OV-15 (init failure) | `initializationFailsClosedOnAnyUnusableConfiguredValue` incl. `from(name -> null)` and bad-alphabet; process-level 5xx/alertability is deployment | application half passing; deployed half deployment-gate |
| OV-16 (byte-identical across reasons) | `everyRefusalReturnsTheSameFixedBytesWithoutEcho` | present, passing |
| OV-17 (no credential, gate passes, seam refuses) | `verifiedOriginIsNotAuthentication…` (harness authorizer refuses) | present, passing |
| OV-18 (C3 envelope untouched) | existing `ConsoleAuthorizationRouteTest.foreignCallerContextIsRefusedWithoutAnyBusinessWrite` asserts `CONTEXT_DENIED` C3 body; gate body constant separate | substance covered |
| OV-20 (one closed denial record, metric) | `refusalEmitsExactlyOneClosedRecordAndContainsASinkFailure` (record fields exact; metric filter still unauthored — FT-2) | application record passing; metric deployment |
| OV-01/11/12 (edge positive, viewer forge/conflict overwritten) | not runnable in-process; runtime only | **deployment-gate retest** |
| OV-03 (platform 401, no denial record, via real authorizer) | substance covered at the seam level; real platform body is deployment | seam passing; body deployment-gate |
| OV-19 (rotation fail-closed outage) | not runnable in-process | **deployment-gate retest** |

The focused green run (33 tests, 0 failures/errors/skipped, exit 0) plus the existing 16 console
tests (exit 0) is the recorded execution evidence for the passing subset. I did not re-execute the
suite (see §13); the recorded outputs are internally consistent (counts, timestamps, per-class
breakdowns) and the current test bytes are hash-pinned and read in full by me.

## 8. Q5 — History route: no payment capability, no duplicate projection, truthful failures

Verified by direct read of `ConsoleHistoryRoute`, `ConsoleHistoryCursorCodec`,
`EventHistoryReader`, `DynamoEventHistoryReader` and the route tests:

- **No payment capability.** The constructor takes authorizer, context registry, `EventStore` (head
  only), `EventHistoryReader`, cursor codec, environment, TTL bound and clock. There is no
  `AuthorizationService`, no purchase/dispatch/claim port, no write path anywhere in the class; a
  page is observation only. `EventHistoryReader` is an explicitly read-only port ("neither appends
  nor creates a second payload copy and never grants payment authority").
- **No duplicate projection.** The route reads the existing functional event source via the existing
  store head and the range reader; `DynamoEventHistoryReader` performs a strongly consistent
  `Query` over the same table rows and copies nothing into a second table. No new table, no
  projection, no TTL/compaction, no transaction creation (matching the H1 handoff's exclusions).
- **Truthful failures.** Snapshot H/R/F frozen per read; continuation revalidates environment,
  subject/azp/audience, transaction, acquirer/merchant, mandate fingerprint (410 on generation
  retirement), R/F/snapshot-head drift (410); tampered/unknown cursor 400 `INVALID_CURSOR`; cursor
  bound to another subject/transaction 403; `after` beyond the durable head 400 rather than silent
  clamp; a missing retained row becomes an explicit `gapRanges` hole that halts `coveredThrough` and
  suppresses the next cursor; the logically expired prefix `1..F-1` is reported as
  `expiredRanges`/`retentionTruncated` rather than silently skipped; token expiry or registry outage
  between the two re-authentications and store failure are 503/401 with no payload; unknown
  authorized target 404 disclosing nothing; missing/duplicated `Authorization` refused before the
  store read. Re-authentication after the read compares subject/azp/audience and both mandate halves
  before disclosure.
- **Cursor discipline.** HMAC-SHA256 over a versioned payload with `MessageDigest.isEqual`
  signature comparison, ≥32-byte key, 4096-byte presented-cursor bound, single-dot wire form,
  future-issuedAt refused rather than clamped, every field re-checked against the current principal
  and mandate. Cursor TTL bounded to 1..900 s at construction.

## 9. Q6 — Red/green test integrity

**No test was deleted, disabled or weakened, on the preserved evidence; one residual is disclosed
and bounded.**

- **Preservation.** The red compile (`compile-attempt2-testcompile-*`, exit 1) and red run
  (`history-and-ov-tests-*`, exit 1) are separate files from the green run (`*-attempt2-*`, exit 0);
  neither was overwritten.
- **No deletion/disablement.** Both runs report the same per-class counts — 4 + 9 + 20 = 33 — across
  the same three classes, with `Skipped: 0` in both. A deleted or `@Disabled` case would change one
  of those numbers; no `@Disabled`/`@Tag`/`Assumptions` exists in any console test today.
- **No weakening.** All six red failure/error sites are test-harness defects, not relaxed
  assertions: the OV-AUTHZ-INDEPENDENT case failed `expected: 401 but was: 405` because the request
  it built was method-rejected before the credential seam (now a well-formed `POST` to the route
  path — the assertion itself was strengthened into reachability, not weakened); the four DynamoDB
  integration errors were the test's own append helper seeding an `EventContext` that did not match
  the collector partition (`CONTEXT_MISMATCH` raised by production validation at the helper's call
  site); the step-2 compile errors were a reassigned `final` local and a reference to a
  non-existent `EXPIRED_TOKEN` enum constant (the enum declares `EXPIRED`). The modification-time
  chronology corroborates test-only edits between the runs: every `src/main` console file is at or
  before 22:38:33, the red run finished 22:40:59, the green run started 22:41:31, and the only files
  written between are the three test files.
- **Residual, disclosed:** the red-revision **sources** were not preserved (the console package and
  its tests are untracked; no byte diff exists), and surefire's stdout lists only failures/errors,
  so the identity of the 27 passing red cases cannot be proven from the recorded output alone — a
  passing case could in principle have been replaced between runs while counts stayed equal.
- **Adequacy judgment:** the remaining proof is adequate for this bounded closure decision. The
  decision does not rest on the red bytes: the green bytes are hash-pinned, and this review read
  every current assertion directly — the suite as it now stands is strong (§7 mapping), which is
  the property the closure depends on. What the red evidence additionally establishes is process
  honesty (a real red run preceded the green) and that no case count dropped. The unexcludable
  replacement of an unknown passing case is a bounded residual: any such case would have been one of
  the 27 passing cases of a suite whose current 33 are each security-positive, and the full suite
  remains re-runnable by QA on these exact bytes. This does not block N-02 closure at authoring;
  it is recorded so the gap is never silently promoted into a stronger claim (e.g., "byte-identical
  red/green diff proven") than the evidence supports.

## 10. Findings

**No new BLOCKER or IMPORTANT defect is confirmed in the reviewed bytes.** N-02's enforcement half
is now authored faithfully. Findings below are advisories and follow-through items with explicit
status.

### A-1 (ADVISORY) — OV oracle identifiers absent from test sources and evidence

The delta §9.2 fixed four identifiers (`OV-GATE-REQUIRED`, `OV-NO-ECHO`, `OV-ZERO-EFFECT`,
`OV-AUTHZ-INDEPENDENT`) and §11 twenty cases; the delivered tests realize their substance but carry
no `OV-*` markers, and the H1 evidence never maps tests to oracles. Traceability currently depends
on this review's §7 mapping instead of the contract's fixed strings, which the delta declares
binding ("must not be paraphrased"). Impact: auditability, not behaviour — a future editor cannot
see which test pins which oracle and could alter one without tripping the identifier.
**Mitigation:** annotate the four §9.2 test methods (and the classification/echo/zero-effect cases)
with their OV identifiers, and record the mapping in the work item. **Retest:** identifiers present
in test sources; QA traceability rows reference them. This does not block closure: the substance
is present and verified.

### A-2 (ADVISORY, binding follow-through) — the gate currently runs nowhere: no composition instantiates the entry point

`ConsoleIntegrationEntryPoint` is referenced only by its own file and its test. There is no console
Lambda handler in the tree and the `console/dev` harness predates the gate (18:39–18:40) and wires
neither the gate nor the entry point. Within today's bytes the boundary control refuses nothing in
any runnable composition — the same class of gap the n02-origin review flagged as FT-1, narrowed
from "no control exists" to "the specified control exists as a mandatory entry-point step that no
composition invokes yet". The delta's §5.3 initialization failure and the "no bypass switch"
property are enforced **where the entry point is used**; a future composition that builds routes
without the entry point would silently reintroduce the bypass the owner paid to remove.
**Mitigation:** the deployment/adapter composition must construct the entry point via
`fromEnvironment()` (so a missing/malformed `EDGE_ORIGIN_VERIFY` fails initialization and is
alertable) and route every console integration request through `handle`. **Retest:** composition
review + deployment-gate OV-01/02. Until then nothing may be represented as protected.

### A-3 (ADVISORY, contract-text follow-up) — provisional `INVALID_CURSOR` mapping for malformed pagination

D-6 names `INVALID_CURSOR` only for an unknown/tampered cursor; the route defensively reuses it for
malformed `snapshot`/`after`/unknown query parameters and for `after` beyond the durable head. The
implementation discloses this as ambiguity 1 rather than presenting it as contract text. The
mapping is coherent (no invented business code, no disclosure, no C3 contamination), but the
contract text should confirm it. **Mitigation:** Requirements confirms or amends D-6's error-code
enumeration for malformed pagination input. **Retest:** contract delta or recorded Requirements
confirmation. Domain/Requirements-owned; not a defect of this slice.

### A-4 (ADVISORY, carried) — cursor signing key sourcing unresolved

The codec takes an injected key (`keyId` + ≥32 secret bytes) as a required collaborator; no
environment-variable name or custody rule is fixed, and the delta deliberately did not invent one.
Runtime sourcing, custody and rotation for the cursor key are deployment-composition inputs.
**Mitigation:** fix the sourcing in the composition slice with the same custody discipline as
`EDGE_ORIGIN_VERIFY` (state/config only, no logs, no tfvars). **Retest:** composition review.
Carried exactly as disclosed in `RESULTS.md`/`PROGRESS.md` ambiguity 3.

### A-5 (ADVISORY, documentary) — history-route transport ordering asymmetry

`ConsoleHistoryRoute.handle` checks the path shape (404) before the method (405), while
`ConsoleAuthorizationRoute` checks method first. Both sit strictly below the gate, so the boundary
answer is identical regardless; the asymmetry only affects which transport error a wrong request
receives after verification. No security consequence; recorded so a future contract pin on 404/405
ordering does not surprise anyone.

### Confirmed non-findings

- No echo channel exists for the header value in the reviewed bytes (response constant, closed
  denial record, bounded caller-controlled fields).
- No second console route, no anonymous path, no "trusted header" constructor; duplicated
  `Authorization` is refused rather than first-value-read in both routes.
- No payment capability, no duplicate projection, no journal write and no fabricated completeness in
  the history route (§8).
- No real PAN/PIN/CVV-like value and no live credential anywhere in the slice; fixtures use
  documentary synthetic symbols (`SYNTHETIC-ORIGIN-VERIFY-…`, `api.sim.synthetic`, `203.0.113.x`).

## 11. Status table (CLOSED / PARTIAL / NOT CLOSED)

| Item | Status | Basis |
| --- | --- | --- |
| **N-02** (direct `api_fqdn` edge-bypass) | **CLOSED at authoring**, per the n02-origin review's projected closure path (§8 step (i)–(ii)) | Delta §5–§7 implemented faithfully in pinned bytes (§4–§6); OV suite substance present and passing (§7); this is the implementation re-review step (ii). Runtime oracles and composition remain explicitly outstanding (A-2, §12) |
| N-02 sub-item: composition wiring | NOT CLOSED (A-2) | No composition instantiates the entry point; binding follow-through on the deployment/adapter slice |
| N-02 sub-item: metric filter + alarm (FT-2) | NOT CLOSED | Awaits frozen log shape + owner-set threshold, as sequenced by the delta and DevOps |
| OV oracle suite — application-runnable subset | CLOSED as substance (identifier traceability A-1 ADVISORY) | §7 mapping; green run 33/33 + existing 16/16 recorded |
| OV oracle suite — deployed-edge subset (OV-01/11/12/15-deployed/19/03-body) | NOT CLOSED — deployment-gate retest | Nothing deployed; §12 statement below |
| History route security goals (no payment capability, no duplicate projection, truthful failures, cursor binding) | CLOSED within the slice's declared scope (stub decoder = K1's subject, unchanged) | §8; real issuer/token verification remains K1 and the C-03 gate |
| Red/green test integrity | CLOSED with disclosed bounded residual | §9; red sources not preserved (disclosed); adequacy reasoned, not assumed |
| Malformed-pagination code (A-3) | NOT CLOSED — Requirements-owned contract confirmation | Disclosure preserved; mapping judged coherent |
| Cursor key sourcing (A-4) | NOT CLOSED — composition input | §10/A-4 |
| WebSocket direct-path surface | NOT CLOSED — owner disposition, outside N-02 scope | Unchanged from n02-origin §7 |
| SR2-01/02 executed IAM negatives (C-01), C-02 rotation, C-03 issuer ten-item list, C-04 public-surface list, NV-05/AD-01, DR3-01 fixture residual | NOT CLOSED — carried unchanged | No new evidence touches them; this review neither upgrades nor reopens them |
| K1 real issuer/claim validation | NOT CLOSED — commissioned construction, not delivered | Stub decoder unchanged by this slice; flagged in `RESULTS.md` |

## 12. What remains a deployment-gate retest (explicit)

Nothing in this report is runtime evidence. The following are and remain deployment-gate items:
CloudFront origin custom-header presence and viewer-supplied same-name overwrite (OV-11/12); HTTP-API
forwarding/duplicate-coalescing behaviour as §5.2 assumes; the deployed initialization-failure 5xx
and its alertability (OV-15 deployed half); coordinated-rotation total outage without partial
accept (OV-19); platform 401 body preservation at the browser (OV-03 deployed half); N-03 edge
pass-through of the new 403 alongside 401/403/404; log read-back across CloudFront/WAF/API
Gateway/Lambda/ADOT proving the value appears in no format; metric emission and visibility
(presupposing FT-2's filter/alarm authoring once the log shape is frozen); plan/apply cleanliness
with value redaction; no second console path; the direct-path negatives (valid token + absent
header → 403 + logged denial; retired `execute-api` host refused).

## 13. Limitations

- **Documentary review of exact pinned bytes.** I did not execute the test suite; the red/green
  outputs are the implementing run's recorded evidence, internally consistent and hash-anchored, and
  my verification is the full read of the current test assertions plus count/signature/chronology
  analysis. An independent re-execution is QA's gate on these exact bytes.
- The red-revision sources are not preserved (disclosed by the implementation); §9 states precisely
  what the remaining proof does and does not exclude.
- Branch/HEAD binding is the implementing run's attestation (`5bbd4eb5…`, dirty tree by design),
  corroborated only by my read-only observation (§2 disclosure); per role routing, Git evidence for
  handoff remains a Coordinator/Implementation route item.
- The three prior-review INPUTS pin drifts (n02-origin §2.1) remain as recorded there; this review
  consumed the delta text and current bytes, not the delta's own snapshot.
- No scan evidence (Trivy/gitleaks/CodeQL) exists or was produced here; absence is not a clean scan.
- Runtime/model observed is recorded in §1; no provider-qualified identifier was exposed or
  invented. This report is English per role convention; Coordinator relays in Spanish.

## 14. Verdict (PASS / FAIL / BLOCKED)

**Bounded re-review verdict: PASS — N-02 moves to CLOSED at authoring.**

Rationale: within the exact commissioned scope — does the delivered application bytes implement the
delta's §5–§7 enforcement faithfully, is the gate first-before-everything and fail closed, is the
response exact and non-echoing, is initialization fail-closed, is the gate explicitly not
authentication and independent of the JWT authorizer, is every denial observable, is the OV suite
present with its edge-independent oracles passing, does the history route add no payment capability
or duplicate projection and keep its failures truthful, and was no test deleted/disabled/weakened
between red and green — every mandatory check was completed (hash recomputation, full byte reads of
gate/entry-point/record/route/codec/tests, red-green evidence analysis) and no defect was found;
the four advisories are traceability/follow-through items, none behavioural. This satisfies the
n02-origin review's closure path steps (i)–(ii): the gate exists in application bytes per delta
§5–§7 and this implementation re-review confirms those bytes, so N-02 projects to CLOSED at
authoring with the runtime oracles outstanding.

**What this PASS is not.** It is not a Security PASS for any implemented revision, does not
authorize publication, merge, deployment or any external effect, and accepts no risk on anyone's
behalf. A-2 states the operative limit: the authored gate is not yet invoked by any composition, so
no deployable revision refuses the direct path today — the owner-level protection becomes real only
at the deployment gate.

**Standing overall Security verdict for the product's Security gate:** the rev3 FAIL's sole
confirmed-defect driver (the N-02 residual) is now closed at authoring scope, and this review
confirms no new BLOCKER or IMPORTANT in the reviewed bytes. The gate therefore does not remain FAIL
on a confirmed defect. It is also not PASS: mandatory implementation-level evidence remains
unexecuted (SR2-01/02 IAM negatives, C-02/C-03/C-04, scans, N-03 runtime) and K1 real authentication
is undelivered — evidence absence, not confirmed failure. Recorded accurately per
VERIFICATION_DELIVERY as **BLOCKED (evidence-gated)** for the overall implemented revision, with
**zero confirmed defects** asserted by this review; the full-scope implementation re-review (K1 +
IAM/public-surface/scan evidence) is the next gate that can convert BLOCKED into PASS. All carried
statuses in §11 stand until their own gates run.
