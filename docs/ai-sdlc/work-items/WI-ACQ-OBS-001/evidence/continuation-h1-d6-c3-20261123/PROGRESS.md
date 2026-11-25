# Continuation H1 — D-6 C-3 malformed-pagination taxonomy and failure order (2026-11-23)

Developer evidence only: **not** independent QA, **not** Security review, **not** human acceptance.
No push, no PR, no merge, no deploy, no cloud call, no new feature.

## Input

- Approved adoption target (coordinator message): **C-3** of the D-6 clarifications rev 2.
- Corrected text, SHA-256 re-verified here before reading with `sha256sum`:
  `design/contract-deltas/d6-clarifications-rev2-20261123/d6-clarifications.md`
  `3aaac88a72cb4abd3936786f8e467fa5fb164cb39c76bfea99daef8edd9643a3` (matches).
- Independent domain re-check, SHA-256 re-verified:
  `reviews/d6-clarifications-rev2-20261123/domain-review.md`
  `fde09a1fdd83850a99a730141ce19d4441c6c5b4e41b38bfd53d059dce3d1ad7` (matches).
- Sections used: **C-3.1–C-3.4** and the fixed failure order in **§7 / C-3.3**.
- Out of scope, explicitly: C-1, C-2, C-4; the adopted packet; Terraform; Prism/UI; the server-side
  read-pass store, cursor-key custody and real issuer verification.

## Required failure order (implemented, and nothing after it)

`401 UNAUTHENTICATED` → form `400 INVALID_CURSOR` (syntactic only, C-3.1 a–f) → resolution
`503 AUTHORIZATION_UNAVAILABLE` / `403 CONTEXT_DENIED` → malformed-payload `400 INVALID_CURSOR`
(signature does not verify, or signed payload malformed) → retired-schema `410 RESYNC_REQUIRED`
(after signature verification, before the context check) → expired `410 RESYNC_REQUIRED` →
context `403 CONTEXT_DENIED` → changed-representation `410 RESYNC_REQUIRED` → head
`503`/`404 NOT_FOUND` → `after` beyond the frozen head `400 INVALID_CURSOR` (only after the head has
been read) → changed `R`/`F` `410 RESYNC_REQUIRED` → bound `lastSequence` beyond the snapshot head
`400 INVALID_CURSOR` → `200` page.

One code per failure class; `INVALID_CURSOR`/`CONTEXT_DENIED`/`NOT_FOUND`/`AUTHORIZATION_UNAVAILABLE`
carry `action: null`; `RESYNC_REQUIRED` carries `action: RESYNC_REQUIRED` — matching
`wire-fixtures.json.historyErrors`.

## Plan

1. **Codec.** Add a distinct `RETIRED` cursor outcome so a signature-valid payload whose schema
   version is not current — or which lacks a member required by the current representation (C-1.5) —
   is `410`, not `400`; keep genuinely malformed/tampered input `400`. Order inside `open`: decode and
   signature → structural malformation (`400`) → retired schema/representation (`410`) → future-issued
   (`400`) → expiry (`410`).
2. **Route.** Map `RETIRED` → `410 RESYNC_REQUIRED`; it surfaces from `open()` before the context
   comparison, satisfying schema-before-context. Keep the request-form check (`queryOf`) after
   authentication and before resolution; keep `after > H` in `newSnapshot` after `requireHead` and
   before any reader call; comment the early-refusal prohibition so it is visible in code.
3. **Focused tests**, one per ordered stop, including forged signature-valid payloads so the
   retired-schema and malformed-payload steps are actually exercised; assert call counts proving
   position (`after > H`: head read once, reader zero; retired: 410 not 403; form step: registry zero).
4. Run the focused console suites **once**, record real counts and exits, raw streams under `runs/`.
5. Update `implementation-progress.md` and the affected traceability rows, then stop.

## Session binding

- Repository: `/home/pazfernando/projects/acquiring-simulator`
- Branch: `feat/wi-acq-auth-001`
- HEAD at start: `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` (no commit made)
- Model: `nan/deepseek-v4-flash`, no override
- JDK: Temurin 25.0.5 (`/home/pazfernando/.local/opt/jdk-25`); Maven 3.10.1 offline

## Chronology

- Created this file before any source edit.
- Re-verified both SHA-256 pins (match) and read C-3.1–C-3.4 and §7.
- Codec: added `CursorException.Kind.RETIRED` and split `parse` into malformed (`400`) before
  retired schema/representation (`410`); `open` keeps signature → parse → future → expiry.
- Route: `RETIRED`/`EXPIRED` → `410 RESYNC_REQUIRED`; `INVALID` → `400 INVALID_CURSOR`; C-3.3 step
  order marked in comments; `after > H` kept after `requireHead` with the early-refusal prohibition
  stated in code; form step documented as syntactic-only.
- Tests: `ConsoleHistoryRouteTest` 29 → 40 (eleven new cases plus the strengthened
  `afterBeyondTheDurableHead…`), covering every ordered stop including resolution 503/403, head 503,
  malformed payload, retired schema before context, expired before context, form unknown/repeat,
  `after > H` with `headCalls == 1`/`reader == 0`, and the bound `lastSequence`.
- Compile: `mvn -o -B -DskipTests test-compile` exit 0.
- Focused console suites, recorded run: exit 0, **90 tests, 0 failures, 0 errors, 0 skipped**. An
  earlier green run (87 tests) preceded the last three stop tests; disclosed in `RESULTS.md` §5.
- Wrote `RESULTS.md`, `changed-files.sha256` and the raw streams under `runs/`.
- Updated `implementation-progress.md` §63 and the OBS-18/OBS-19/R01/R25 traceability evidence
  cells (counts 29 → 40, suites 79 → 90); statuses unchanged, `independent verification` stays
  `PENDING`.
- Stopped: no push, deployment or cloud call.
