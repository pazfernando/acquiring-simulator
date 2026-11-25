# Continuation H1 — QA-driven fixes (2026-11-23)

Developer evidence only: **not** independent QA, **not** Security review, **not** human acceptance.
No push, no PR, no merge, no deploy, no cloud call, no new feature.

## Input

- Independent QA report: `reviews/h1-qa-20261123/qa-report.md`
- SHA-256 pinned by the coordinator and re-verified here before reading:
  `2ac3c71662d725bf86a8db4adf86be8447f3405c5aaed6757fd8f6c0152e8844` (matches).
- Defects addressed, in this order: **QA-DEF-2** (503 body code), **QA-DEF-1** (cursor issuer
  binding), **QA-DEF-3** (cursor `terminalRef` context binding).
- Observations addressed only where the contract text settles them; unsettled points are recorded
  as open questions for Requirements (§4 below), not decided in code.

## Plan

1. QA-DEF-2: emit pinned `AUTHORIZATION_UNAVAILABLE` on every history `503`; add an exact-body test;
   update the two existing assertions that encoded `UNAVAILABLE`. State plainly that those
   assertions were changed because they contradicted the contract, not to make a red test green.
2. QA-DEF-1: bind the verified token issuer into the signed cursor payload; compare it on
   continuation; keep both per-page authentications.
3. QA-DEF-3: carry an optional trusted `terminalRef` in the authorized context, bind it into the
   cursor and compare it on continuation.
4. Record observations: mandate identity representation (open), `epoch` (settled for HTTP-only by
   D-6 wording, documentary fixture difference), `cursor`+`after` (open).
5. Run the focused console suites once; report real counts and exits; update
   `implementation-progress.md` and the affected traceability rows; stop.

## Session binding

- Repository: `/home/pazfernando/projects/acquiring-simulator`
- Branch: `feat/wi-acq-auth-001`
- HEAD at start: `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14`
- Model: `nan/deepseek-v4-flash`, no override
- JDK/Maven: Temurin 25.0.5 (`/home/pazfernando/.local/opt/jdk-25`), Maven 3.10.1 offline

## Chronology

- Created this file before any source edit.
- QA-DEF-2 fixed first: all four history `503` bodies emit `AUTHORIZATION_UNAVAILABLE`; the two
  assertions that encoded `UNAVAILABLE` were changed because they contradicted the contract and the
  reviewed fixture, not to make a red test green.
- QA-DEF-1 fixed: verified token issuer added to `ConsolePrincipal`, bound in the signed cursor, and
  compared on continuation and on the post-read re-authentication; re-authentication is retained.
- QA-DEF-3 fixed: optional trusted `terminalRef` added to `AuthorizedContext`, bound in the cursor,
  and compared on continuation and re-authentication.
- Observations recorded in `RESULTS.md` §4: mandate identity representation and `cursor`+`after` are
  open questions for Requirements; `epoch` is settled for the HTTP-only read by D-6's "when live".
- Compile: `mvn -o -B -DskipTests test-compile` exit 0.
- Focused run 1 (preserved red): exit 1, 74 tests, 1 failure — a fixture that reissued the context
  without `terminalRef`, isolating the wrong check. Fixture corrected; no assertion relaxed.
- Focused run 2 (green): exit 0, **74 tests, 0 failures, 0 errors, 0 skipped**.
- Raw streams and exits are under `runs/`; the full write-up is `RESULTS.md`; changed-file hashes
  are in `changed-files.sha256`.
- Updated `implementation-progress.md` §61 and the affected `requirement-traceability.md` rows; then
  stopped. No push, deployment or cloud call.
