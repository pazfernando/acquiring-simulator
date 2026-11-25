# Independent QA re-verification — D-6 C-3 malformed-pagination taxonomy and failure order (WI-ACQ-OBS-001)

- Reviewer: ai-sdlc-qa (independent verification, subagent). Date: 2026-11-23 (America/Bogota).
- **Model that actually ran: `nan/glm5.3-flash`** (OpenClaw runtime identity of this session; recorded
  in `execution-model.json`). Implementation's own C-3 run used `nan/deepseek-v4-flash` per its
  evidence. No model override was selected by QA.
- **Verdict: PASS** for the commissioned C-3 criteria, with two recorded evidence limitations (§6)
  and the standing system-level exclusions (§7). No product defect was found by any executed check.
  This report does not approve acceptance, deployment, N-02 closure or release.
- Scope: independent verification of the C-3 malformed-pagination taxonomy and the fixed failure
  order implemented in `evidence/continuation-h1-d6-c3-20261123/`, against
  `design/contract-deltas/d6-clarifications-rev2-20261123/d6-clarifications.md` (C-3.1–C-3.4 and the
  C-3.3/§7 order) and the reviewed `wire-fixtures.json` error bodies. QA-owned artifacts only; QA ran
  no product test as its oracle.

## 1. Binding re-attestation (read-only)

| Item | Result |
| --- | --- |
| Corrected contract `d6-clarifications-rev2-20261123/d6-clarifications.md` | SHA-256 recomputed `3aaac88a72cb4abd3936786f8e467fa5fb164cb39c76bfea99daef8edd9643a3` — **MATCH** task pin |
| Domain re-check `reviews/d6-clarifications-rev2-20261123/domain-review.md` (advisory PASS) | SHA-256 recomputed `fde09a1fdd83850a99a730141ce19d4441c6c5b4e41b38bfd53d059dce3d1ad7` — MATCH implementation pin |
| Implementation changed files | `sha256sum -c evidence/continuation-h1-d6-c3-20261123/changed-files.sha256` → **3/3 OK** against this working tree (`ConsoleHistoryRoute.java`, `ConsoleHistoryCursorCodec.java`, `ConsoleHistoryRouteTest.java`) |
| Implementation runs re-read | `runs/compile.exit` = 0; `runs/focused-console-suites.exit` = 0; stdout shows 90 tests / 0 F/E/S; stderr empty — consistent with `RESULTS.md` §5 |
| Branch / HEAD | `feat/wi-acq-auth-001` / `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14`, "no commit made" — cited from the implementation evidence per the Git routing contract (QA runs no Git/provider command); content binding attested by the hash re-verification above |
| Prior QA reports | `reviews/h1-qa-20261123/qa-report.md` (FAIL, QA-DEF-1/2/3) and `reviews/h1-qa-recheck-20261123/qa-report.md` (PASS for re-verified scope) read as scope history |

## 2. Expected order — derived from the contract BEFORE reading implementation assertions

Recorded in `PROGRESS.md` §3 before any implementation assertion was read, from the SHA-pinned
contract text: `401` → request-form `400 INVALID_CURSOR` (C-3.1 a–f only, syntactic) → resolution
`503`/`403` → malformed-payload `400` → retired-schema `410` (C-1.5, after signature verification,
before the context comparison) → expired `410` → context `403` → changed-representation `410`
(C-1.3 order, pre-read and post-read) → head `503`/`404` → `after` > frozen head `400` (C-3.4, only
after the head read, zero reader calls) → `R`/`F` `410` → bound-`lastSequence` `400` → `200` page.
`action: null` for 400/401/403/404/503; `action: RESYNC_REQUIRED` only for 410.

## 3. Executed checks and results (all executed by QA in this pass)

Environment: Temurin JDK 25.0.5+8 (`~/.local/opt/jdk-25`), Maven 3.10.1 offline, host `fernando-OC`,
Docker/Testcontainers DynamoDB Local via the suite. All commands run from the checkout root.

| Command | Result | Log |
| --- | --- | --- |
| `mvn -o -B -DskipTests test-compile` | exit 0, BUILD SUCCESS | `log-03-compile.txt` |
| `mvn -o -B test -Dtest='ConsoleHistoryRouteTest,ConsoleHistoryRouteDynamoIntegrationTest,ConsoleOriginVerificationGateTest,ConsoleAuthorizationRouteTest,ConsoleSubmissionAtomicIntegrationTest,ConsoleHandlerCompositionTest,ConsoleDevServerTest,DevStubTokenIssuerTest' -DfailIfNoTests=true` | **exit 0 — 90 tests, 0 failures, 0 errors, 0 skipped** (history unit 40, DynamoDB Local 4, origin gate 9, authorization route 13, atomic submission 3, composition 5, dev server 9, dev token issuer 7) | `log-04-focused-tests.txt` |
| QA driver `tools/QaC3Driver.java` (QA-owned fakes, compiled main classes, javac exit 0) | **40 checks, 40 PASS, 0 FAIL** | `log-01-driver-javac.txt`, `log-02-qa-c3-driver.txt` |

### 3.1 Criterion-to-check mapping (driver ids in parentheses)

| Commissioned criterion | Result | Evidence |
| --- | --- | --- |
| Form `400` fires ONLY for the C-3.1 syntactic set (a)–(f) and only before any registry or store call | **PASS** | 11 form cases each `400` + exact body with registry/head/reader deltas all 0: `snapshot=old`, `snapshot=nope`, `after=abc`, `after=-1`, `after=` empty, 13-digit `after`, unknown parameter, repeated `after`, empty `cursor`, `cursor`+`after`, `cursor`+`snapshot` (F-*). A 12-digit `after` (`100000000000`) is syntactically valid and is NOT refused at the form step — it reaches the C-3.4 step (head delta 1, reader 0), proving the form set did not absorb the head bound (F-after-12digits-not-a-form-refusal) |
| Form `400` precedes resolution even under a registry outage | **PASS** | `after=abc` with a failing registry → `400`, registry delta 0 (F-form-wins-over-registry-outage) |
| `401` precedes the form step (C-2.3) | **PASS** | missing and duplicated `Authorization` with a malformed form → exact `401` body, zero registry/head/reader calls (U-*) |
| Resolution `503`/`403` come next, before any cursor parse and before the store | **PASS** | registry outage → exact `503`, head 0, reader 0; mandate `null` → exact `403`, head 0; a garbage cursor under a failing/absent registry is answered `503`/`403`, not `400` (R-*) |
| Malformed-payload `400` precedes retired-schema `410`; both precede the context `403` | **PASS** | signature-valid non-JSON payload → `400`, head 0 (M-*); a wrongly-typed `version` string is judged malformed (`400`), not retired (M-wrongly-typed-version); a signature-valid `version:2` payload whose context ALSO differs → `410`, not `403`, head 0 (T-retired-schema-is-410-not-403…); missing `version` and missing a required current-representation member → `410` per C-1.5 (T-*). Positional corroboration in source: structural-malformation checks precede the schema decision inside `parse` |
| Retired-schema `410` precedes expired `410` | **PASS (order stated; not client-observable)** — both are `410 RESYNC_REQUIRED` with identical body/action, so no request can distinguish the order; verified as implemented in the codec `open()` sequence (signature → parse: malformed, retired → future-issued → expiry) by source read; the observable part of the clause — "both precede the context `403`" — is executed-Proven below | X-*, T-*; ordering accepted per the domain re-check's own note |
| Expired `410` precedes the context `403` | **PASS** | expired cursor bound to another transaction → `410` (not `403`), head 0, reader 0 (X-expired-cursor-is-410-not-403…) |
| Context `403` precedes the changed-representation `410` (pre-read) | **PASS** | payload with differing subject AND differing fingerprint → `403` exactly (C-context-mismatch-wins…); differing fingerprint only → `410` (C-representation-only…), both head 0 |
| Context-first order also holds post-read (C-1.3) | **PASS** | registry reissues a differing acquirer AND caller after the store read → `403` (P-post-read-context-denied…); same triple with a different caller → `410` (P-post-read-representation-only…) |
| Head `503`/`404` precede the `R`/`F` and bound-`lastSequence` steps | **PASS** | continuation with a store outage AND a changed `R` payload → `503` exact body, reader 0, head delta 1 (H-head-outage…); unknown target with changed `R` and `lastSequence=251` → `404` exact body, reader 0 (H-unknown-target…) |
| `after` beyond the frozen head → `400` ONLY after the head call, with zero reader calls (C-3.4) | **PASS** | `after=4` with head 3 → exact `400`, head delta exactly 1, reader delta 0 (A-after-beyond-head-400-after-one-head-read-zero-reader); with a store outage → `503` not `400` (A-head-outage-outranks…); unknown target → `404` not `400` (A-unknown-target-outranks…); the error carries no head value, sequence or page |
| `R`/`F` `410` then bound-`lastSequence` `400`, both after the head read | **PASS** | changed `expiryRevision` payload → `410` after head delta ≥1, reader 0 (RF-*); `lastSequence=251` against snapshot head 250 with matching H/R/F → exact `400` after head delta 1, reader 0 (L-*); a head REGRESSED below the bound H is correctly `410` before the lastSequence check (observed while calibrating this case — matches C-3.3 step 11) |
| The `200` page still closes the order | **PASS** | valid continuation → 200 page with events (Z-*) |
| Bodies byte-for-byte equal to the reviewed `historyErrors` fixtures | **PASS** | the driver loads `continuation-rev3-20261123/wire-fixtures.json` `historyErrors` at runtime, rebuilds each compact body from its own parsed values, and equality-checks it against the built-in expected bytes (S0) and against every response body above — all byte-identical, including `action: null` on 400/401/403/404/503 and `action: "RESYNC_REQUIRED"` on 410 (S-closed-error-body-set) |
| 87→90 test-history disclosure ("no test removed") | **PARTIALLY VERIFIED — see §6** | arithmetic and name-continuity consistent; the 87-run stream was not preserved |

Suite-side corroboration: the implementation's own twelve new/strengthened `ConsoleHistoryRouteTest`
cases assert the same positions with call counts (registry 0, `headCalls` 0/1, reader 0) and were
inspected for honesty after my expected order was fixed; they agree with the contract and my
independent results. Implementation tests were never treated as the oracle.

## 4. Defects

**None.** No executed check violated a scoped criterion (40/40 driver checks; 90/90 suite tests;
compile exit 0).

Two QA-side construction errors occurred while building the driver and were corrected before the
recorded run (both were my fixtures, not product behaviour, and are disclosed in PROGRESS.md §5):
calibrating the `lastSequence` case with a regressed head (correctly answered `410` by the route's
head-regression clause), and tampering the padding-redundant final base64 character of an HMAC
signature (the unmodified decode verified — the route legitimately answered `200`).

## 5. Results summary

- **PASS for every commissioned C-3 criterion**: form-step scope and position, resolution position,
  malformed-before-retired, retired/expired-before-context, context-before-representation (pre-read
  and post-read), head-before-R/F/lastSequence, and the C-3.4 head-dependent refusal at exactly the
  mandated position with zero reader calls.
- **Bodies** match the reviewed `wire-fixtures.json.historyErrors` byte for byte at every stop.
- No new error code, no new body, no early refusal observed anywhere in the tested surface.

## 6. Evidence limitations and coverage gaps (recorded, not defects)

1. **The 87-test intermediate run is attested only by text.** The disclosure (implementation
   `RESULTS.md` §5; v`PROGRESS.md`) says an earlier green run of the same eight suites had 87 tests,
   then three resolution/head-stop cases were added for the recorded 90-test run. No stdout/exit
   artifact of the 87-run was preserved, and QA cannot re-execute a past run. What QA verified:
   (a) the arithmetic chain is fully documented and consistent — 74 tests at the QA-fix evidence
   (`continuation-h1-qa-fixes-20261123`, history 24) → 79 at the C-1.3/C-2 evidence
   (`continuation-h1-d6-c2-20261123`, history 29, green run recorded) → +8 first-batch = 87 → +3 =
   90; (b) the current file has exactly 40 test methods; (c) all twelve §4 additions exist in the
   final file, and the 40 − 11 additions = 29 non-additions exactly equal the documented pre-task
   count, so any removal between the runs would require an undocumented compensating addition, for
   which no evidence exists. Every test name cited anywhere in prior evidence/reviews still exists.
   Residual limit: the run itself and "both executions were green" rest on the implementer's
   disclosure; the final 90-run stream is QA-reproduced above.
2. **Not executable here (standing exclusions, unchanged):** real issuer/JWKS/signature verification
   (slice K1 seam); cursor signing-key custody/rotation; the server-side read-pass store, TTL and
   compaction; deployed edge (CloudFront/WAF/API Gateway authorizer ordering, Lambda, Keycloak);
   real CloudWatch read-back; WebSocket route and browser surfaces.
3. **Retired-vs-expired order** is not client-observable by design (identical status/body) —
   accepted as documented by the domain re-check; only the shared precede-the-context-403 property
   is testable.
4. **Interpretation notes accepted (implementation-consistent, not contract-contradicting):** a
   wrongly-typed `version`/`keyId` is malformed `400` while an absent version or a missing required
   member is retired `410` (C-1.5 boundary reading); a `keyId` naming another generation while
   verifying against the current key is contradictory `400` INVALID_CURSOR.

## 7. System-level statements (restated)

- **No integrated end-to-end payment exists.** This slice remains the boundary gate plus a
  read-only history route: no transaction creation, no projection, no dispatch capability, no
  payment send. No Operation A execution against any issuer was run in this verification.
- **N-02 remains open at the edge.** The origin-verification gate is proven only through the
  in-process seam; deployed-edge behaviour (CloudFront header injection/overwrite, WAF, direct
  `api_fqdn`) is not exercised and N-02 is not closed by these bytes.
- This PASS does not approve acceptance, deployment, merge, publication or N-02 closure; it covers
  the C-3 taxonomy/order criteria for the working-tree bytes verified here. Any further changed
  code would require renewed independent re-verification.

## 8. Deliverables

`qa-report.md` (this file), `PROGRESS.md`, `SHA256SUMS`, `execution-model.json`,
`log-01-driver-javac.txt`, `log-02-qa-c3-driver.txt`, `log-03-compile.txt`,
`log-04-focused-tests.txt`, `tools/QaC3Driver.java` (driver source; compiled classes not
preserved). No product source, contract, work-item or other specialist artifact was modified; no
Git or provider mutation; no cloud, issuer or edge execution; no credential flows; no onward
delegation.
