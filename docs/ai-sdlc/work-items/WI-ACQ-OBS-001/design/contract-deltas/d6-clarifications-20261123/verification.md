# Observed checks, model identity and limitations

## Model that actually ran

- Runtime declaration for this run: **`nan/deepseek-v4-flash`**.
- The session record read through `sessions_list` for this session key reports **`gpt-6.1-sol`**, which
  is the configured default for this agent. The two observations disagree; the runtime declaration is
  taken as the identity of the process that produced these files, and the session-record value is
  reported rather than suppressed. **Conclusion recorded, not asserted:** the run is on the configured
  fallback, and the primary model was not the one that produced this draft. No model override was
  requested by this task, and none was applied by it.
- Harness: native OpenClaw subagent session. No ACP, no Codex, no external model call.

## Inputs actually read

Role files `USER.md`, `SOUL.md`, `IDENTITY.md`, `AGENTS.md` in
`/home/pazfernando/.openclaw/workspace/ai-sdlc-requirements/` (outside the repository; not in
`INPUTS.SHA256SUMS` because that manifest is repository-relative and checkable from the checkout
root). Shared glossary and procedure were not re-read this run; the task's bounded scope did not
require a term decision, and the terminology used here is the adopted packet's own.

Repository files, all read in full except where noted:

- `docs/ai-sdlc/PROJECT.md`, `docs/ai-sdlc/README.md`, `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/WORK_ITEM.md`
  (headings plus the current-status sections).
- Adopted packet `design/contract-deltas/continuation-rev3-20261123/`: `contracts.md` (full, 29 lines),
  `wire-fixtures.json` (full), `identity-map.md`, `disposition-ledger.md`, `verification.md`,
  `PROGRESS.md`, `README.md`, `OUTPUTS.SHA256SUMS`, `INPUTS.SHA256SUMS`.
- Evidence and review: `evidence/continuation-h1-qa-fixes-20261123/RESULTS.md` §1–§6,
  `evidence/continuation-h1-20261123/RESULTS.md` §3–§5, `reviews/h1-qa-20261123/qa-report.md` §2–§5.
- Source, read to establish current behaviour only: `ConsoleHistoryCursorCodec.java` (full),
  `ConsoleHistoryRoute.java` (query parsing, snapshot, continuation, re-authentication, render),
  `AuthorizedContext.java` (full), `ConsoleHistoryRouteTest.java` (the pagination and provisional-mapping
  cases).

## Integrity checks actually run

| Check | Result |
| --- | --- |
| `sha256sum -c continuation-rev3-20261123/OUTPUTS.SHA256SUMS` | **21/21 OK**; manifest SHA-256 `2000bb004e68e6d2974bef3067baeae6774f1922a21dca4ea18fecf6944ab467`, matching the coordinator's pin `2000bb00…ab467` |
| `sha256sum -c continuation-rev3-20261123/INPUTS.SHA256SUMS` | **30/32 OK**, 2 FAILED: `WORK_ITEM.md` and `owner-decisions.md`. Both are mutable product records edited after that packet was sealed; the mismatch is expected and is not treated as a defect of this delta. It is disclosed because it means the packet's input manifest no longer reproduces against the working tree |
| `sha256sum` over every file listed in `INPUTS.SHA256SUMS` here | recorded; see manifest |
| `git rev-parse` / `git status --porcelain` (read-only) | branch `feat/wi-acq-auth-001`, HEAD `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14`, matching the record in PROJECT/work item; working tree carries modified mutable docs under `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/` |

Commands used: `pwd`, `ls`, `find`, `sed`, `head`, `grep`, `wc`, `sha256sum`, `sha256sum -c`, `mkdir`,
and file writes through the editor tool. `rg` was not used because it is unavailable in this
environment. No test, build, compiler or runtime command was executed.

## Limitations

- **Behaviour statements are source readings, not execution evidence.** §1 and §7 of the delta describe
  what the current code does; nothing was compiled or run, so no claim is made that the described
  behaviour is what the built artifact does.
- **No independent review.** No domain reviewer, Security reviewer or human has seen these clauses.
  Their verdicts are separate from this draft and none is implied.
- **The four points are settled in text only.** The implementation still silently ignores `after` when a
  `cursor` is present, and its codec still reports a stale payload version as `400`; both are recorded
  in the impact table and neither is changed here.
- **Byte identity attaches to working files.** HEAD is recorded but the reviewed bytes are the working
  tree's, not a commit.
- **No external effect.** No Git write, stage, commit, push, PR, merge, deployment, provider call,
  credential flow or onward delegation occurred. Writes are confined to this directory.
