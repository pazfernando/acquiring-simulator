# Independent QA re-verification — D-6 C-3 malformed-pagination taxonomy and failure order
# (started 2026-11-23 ~23:45 America/Bogota)

Written first and updated incrementally. Reviewer: ai-sdlc-qa (independent verification, subagent).
Verdict vocabulary: PASS / FAIL / BLOCKED per VERIFICATION_DELIVERY.md.
Commission: independent verification of the C-3 taxonomy and failure order just implemented in
`evidence/continuation-h1-d6-c3-20261123/`. Verify, not trust.

## 1. Inputs read (pins re-computed before any execution)

- Role docs: ai-sdlc-qa AGENTS.md, SOUL.md, IDENTITY.md, USER.md; shared README.md,
  PROJECT_EXECUTION.md, VERIFICATION_DELIVERY.md, GLOSSARY.md.
- Prior QA reports: `reviews/h1-qa-20261123/qa-report.md` (FAIL, QA-DEF-1/2/3) and
  `reviews/h1-qa-recheck-20261123/qa-report.md` (PASS for re-verified scope) — read as scope history,
  not as results for this commission.
- Corrected contract: `design/contract-deltas/d6-clarifications-rev2-20261123/d6-clarifications.md`
  — SHA-256 recomputed: `3aaac88a72cb4abd3936786f8e467fa5fb164cb39c76bfea99daef8edd9643a3` = task pin
  MATCH. Sections C-3.1–C-3.4 and the §7/C-3.3 order read, plus C-1.5, C-2, §6, §7, §8.
- Domain re-check: `reviews/d6-clarifications-rev2-20261123/domain-review.md` — SHA-256 recomputed:
  `fde09a1fdd83850a99a730141ce19d4441c6c5b4e41b38bfd53d059dce3d1ad7` = implementation evidence pin
  MATCH. Verdict advisory PASS for adoption of C-1..C-4 rev 2 text.
- Implementation evidence: `evidence/continuation-h1-d6-c3-20261123/` — RESULTS.md, PROGRESS.md,
  changed-files.sha256 (3 files), runs/compile.{exit,stdout,stderr} (exit `0`), runs/
  focused-console-suites.{exit,stdout,stderr} (exit `0`, 90/0/0/0). Read as developer claims.

## 2. Binding re-attestation (read-only; QA runs no Git/provider command)

- `sha256sum -c changed-files.sha256` from the repo root: **3/3 OK** —
  `ConsoleHistoryRoute.java`, `ConsoleHistoryCursorCodec.java`, `ConsoleHistoryRouteTest.java`
  verified against this working tree.
- Branch `feat/wi-acq-auth-001`, HEAD `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` ("no commit made"):
  cited from the implementation evidence per the Git routing contract (PROJECT_EXECUTION.md);
  content-level binding attested by the hash re-verification above.
- No Git command executed by this pass.

## 3. Expected order — derived from the contract BEFORE reading implementation assertions

Oracle: C-3.3 of the rev 2 text (SHA-pinned above), tightened by C-3.1, C-3.4, C-1.5, C-2.3:

1. `401 UNAUTHENTICATED` (action null)
2. `400 INVALID_CURSOR` request-form step — C-3.1 (a)–(f) ONLY (syntactic `after`; `snapshot` value;
   unknown names; repeated names; empty `cursor`; `cursor` combined with `after`/`snapshot`),
   after authentication, before mandate resolution and before any store call. MUST NOT include
   `after` > frozen head (C-3.4).
3. `503 AUTHORIZATION_UNAVAILABLE` / `403 CONTEXT_DENIED` — mandate resolution.
4. `400 INVALID_CURSOR` — cursor signature does not verify, or signed payload structurally malformed.
5. `410 RESYNC_REQUIRED` — signature verifies but payload schema/version is retired, or a required
   current-representation member is absent (C-1.5). Precedes the context comparison; stated, not
   implicit.
6. `410 RESYNC_REQUIRED` — expired by signed `expiresAt`. (Retired-before-expired is not
   client-observable: identical status/body.)
7. `403 CONTEXT_DENIED` — validly signed, unexpired, current-schema cursor bound to another
   issuer/azp/subject/audience/environment/context/transaction. Checked before the
   changed-representation 410 (C-1.3), pre-read and post-read.
8. `410 RESYNC_REQUIRED` — changed mandate representation (fingerprint).
9. `503` / `404 NOT_FOUND` — store head unavailable, or authorized but unknown target.
10. `400 INVALID_CURSOR` — `after` greater than the frozen snapshot head (C-3.4): only after the
    head call has happened, MUST NOT be refused before resolution or the head read, and with zero
    reader calls.
11. `410 RESYNC_REQUIRED` — changed `R`/`F` (head regression).
12. `400 INVALID_CURSOR` — bound `lastSequence` beyond the bound snapshot head.
13. `200` page.

`INVALID_CURSOR`/`CONTEXT_DENIED`/`NOT_FOUND`/`AUTHORIZATION_UNAVAILABLE` carry `action: null`;
only `RESYNC_REQUIRED` carries `action: RESYNC_REQUIRED`. Bodies per
`wire-fixtures.json.historyErrors` (400 INVALID_CURSOR, 401 UNAUTHENTICATED, 403 CONTEXT_DENIED,
404 NOT_FOUND, 410 RESYNC_REQUIRED, 503 AUTHORIZATION_UNAVAILABLE).

## 4. Planned execution (independent; implementation tests are not the oracle)

1. Read `ConsoleHistoryRoute.java` / `ConsoleHistoryCursorCodec.java` source for order conformance.
2. Author my own driver `tools/QaC3Driver.java` (QA-owned fakes, QA-owned compiled classes),
   driving every ordered stop and every precedence pair in §3, with byte-for-byte body checks
   against the fixture, and call-count assertions (registry/head/reader) proving position.
3. Compile with the project toolchain (Temurin 25.0.5, offline) and execute the focused console
   suites myself: the same eight suites as the recorded implementation run.
4. Verify the 87→90 test-history disclosure as far as evidence allows; record limitations.
5. Write qa-report.md, raw logs, SHA256SUMS, execution-model.json here.

Restrictions honored: no Git/provider mutation, no cloud/issuer/edge execution, no code edits,
no credential flows, no onward delegation.

## 5. Executed (2026-11-23 23:47–23:58 America/Bogota)

- Source order re-read after the expected order was fixed: `ConsoleHistoryRoute.java`
  (`handle` → `queryOf` → `requireMandate` → `newSnapshot`/`continuation` → `page`),
  `ConsoleHistoryCursorCodec.java` (`open`: signature → parse (malformed before retired) →
  future-issued → expiry). Order conforms to §3, including the post-read context-first order in
  `reauthenticateAfterRead` and the C-3.4 comment/position in `newSnapshot`.
- Driver history: first run 37 PASS / 1 FAIL — that failure was a QA fixture error (the bound
  `lastSequence` case used a store head of 3 against a cursor bound to H=250, correctly answered
  `410` by the head-regression clause); scenario corrected to hold the head at the bound H. Second
  construction error: tampering the final base64 character of an HMAC signature, whose low 4 bits
  are padding-redundant, so the unmodified bytes still verified and the route legitimately answered
  `200`; mutation moved to the first signature character. Final recorded run: **40/40 PASS**,
  exit 0 (`log-02-qa-c3-driver.txt`).
- `mvn -o -B -DskipTests test-compile` exit 0 (`log-03-compile.txt`).
- Eight focused console suites re-executed by QA: **90 tests, 0 failures, 0 errors, 0 skipped**,
  exit 0 (`log-04-focused-tests.txt`) — same per-class counts as the implementation's recorded run
  (history 40, DynamoDB Local 4, gate 9, authorization 13, atomic 3, composition 5, dev server 9,
  dev token issuer 7).
- 87→90 disclosure verification: arithmetic chain documented (74 → 79 → 87 → 90 across
  `continuation-h1-qa-fixes`, `continuation-h1-d6-c2`, `continuation-h1-d6-c3` evidence); all twelve
  §4 additions exist in the final file; 40 − 11 additions = 29 = documented pre-task count; every
  test name cited in any prior artifact still exists. The 87-run stream itself was not preserved —
  the green claim for that intermediate run rests on the implementer's disclosure (recorded in
  qa-report.md §6.1).
- Deliverables written into this directory; SHA256SUMS over all new artifacts; compiled driver
  classes removed, source retained.
