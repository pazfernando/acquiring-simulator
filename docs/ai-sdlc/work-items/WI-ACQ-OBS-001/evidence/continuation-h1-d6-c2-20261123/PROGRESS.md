# Continuation H1 — D-6 C-1.3 post-read ordering and C-2 cursor combinations (2026-11-23)

Developer evidence only: **not** independent QA, **not** Security review, **not** human acceptance.
No push, no PR, no merge, no deploy, no cloud call, no new feature.

## Input

- Independent domain review: `reviews/d6-clarifications-20261123/domain-review.md`
- SHA-256 pinned by the coordinator and re-verified here before reading with `sha256sum`:
  `d6a57e0ca4f0e22c838c8e397bd5d576b95e7f6fa9d2a880989ef6b9141464b9` (matches).
- Sections used: **C-1.3 / §7** (post-read context check before mandate-fingerprint check) and **C-2**
  (`cursor` combined with `after` or `snapshot` is refused, no precedence, no silent ignore).
- Out of scope, explicitly: the C-3 malformed-pagination taxonomy and failure-order corrections.
  Its corrected text is being authored separately; nothing in this step changes that mapping.

## Plan

1. **C-1.3 post-read ordering.** In `ConsoleHistoryRoute.reauthenticateAfterRead`, compare the
   resolved-after context (`acquirerRef`/`merchantRef`/`terminalRef`) against the mandate resolved
   before the read and return `403 CONTEXT_DENIED` **before** comparing
   `fingerprint(mandateAfter)` to the cursor binding (`410 RESYNC_REQUIRED`). The pre-read
   continuation is already context-first; keep it that way. Keep both authentication calls and the
   `401`-before-disclosure rule unchanged.
2. **C-2 cursor combinations.** In `ConsoleHistoryRoute.queryOf`, when a non-empty `cursor` is
   present, refuse a simultaneous `after` or `snapshot` with `400 INVALID_CURSOR` (null action)
   instead of letting the cursor win. `queryOf` already runs after authentication and before
   mandate resolution and the first store read, so the refusal inherits that position.
3. **Focused tests**, added to `ConsoleHistoryRouteTest`:
   - both context-mismatched and generation-retired on the post-read path → `403`, and the store
     was read (it is a post-read check);
   - only generation-retired on the post-read path → `410` with `RESYNC_REQUIRED`;
   - both context-mismatched and generation-retired on the continuation path → `403`, store not
     read;
   - `?cursor=X&after=N` → `400 INVALID_CURSOR`, null action, registry and store never consulted;
   - `?cursor=X&snapshot=new` → same.
4. Run the focused console suites once (same eight classes as the prior H1 evidence), record real
   counts and exits, save raw streams under `runs/`.
5. Update `implementation-progress.md` and the affected traceability rows, then stop.

## Session binding

- Repository: `/home/pazfernando/projects/acquiring-simulator`
- Branch: `feat/wi-acq-auth-001`
- HEAD at start: `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14`
- Model: `nan/deepseek-v4-flash`, no override
- JDK: Temurin 25.0.5 (`/home/pazfernando/.local/opt/jdk-25`); Maven 3.10.1 offline

## Chronology

- Created this file before any source edit.
- Re-verified the domain-review pin (matches) and read C-1.3/§7 and C-2.
- C-1.3: reordered `reauthenticateAfterRead` to context `403` before fingerprint `410`; both
  authentication calls, the `401`-before-disclosure rule and the pre-read continuation order are
  unchanged.
- C-2: `queryOf` now refuses `cursor` combined with `after` or `snapshot` (`400 INVALID_CURSOR`,
  null action) before mandate resolution and any store read.
- Added five focused tests to `ConsoleHistoryRouteTest` (24 → 29): continuation both-mismatch,
  post-read both-mismatch, post-read generation-only, and the two cursor-combination cases.
- Compile: `mvn -o -B -DskipTests test-compile` exit 0.
- Focused console suites, single run: exit 0, **79 tests, 0 failures, 0 errors, 0 skipped**.
- Wrote `RESULTS.md`, `changed-files.sha256` and the raw streams under `runs/`.
- Updated `implementation-progress.md` §62 and the OBS-18/OBS-19 traceability evidence cells;
  statuses and counts unchanged. Stopped: no push, deployment or cloud call. C-3 is deferred to
  its corrected text.
