# Observed checks, model identity and limitations (rev 2)

## Model that actually ran

- Runtime declaration for this subagent session: **`nan/deepseek-v4-flash`**
  (`model=nan/deepseek-v4-flash`, `default_model=openai/gpt-6.1-sol`).
- The configured default for this agent is `openai/gpt-6.1-sol`; the run therefore took the configured
  fallback. No model override was requested by this task and none was applied.
- `sessions_list` was **not** re-run in this bounded pass, so no second observation is offered. Revision
  1's `verification.md` recorded a disagreement between the runtime declaration and the session record;
  that observation is not inherited as fact here.
- Harness: native OpenClaw subagent session. No ACP, no Codex, no external model call.

## Inputs actually read

- Reviewer input, in full: `reviews/d6-clarifications-20261123/domain-review.md`.
- Superseded text, in full: `d6-clarifications-20261123/d6-clarifications.md`, plus its `review-brief.md`,
  `verification.md`, `PROGRESS.md`, `INPUTS.SHA256SUMS`, `OUTPUTS.SHA256SUMS`.
- Adopted packet, read for the quoted D-6 text and fixture members: `continuation-rev3-20261123/`
  `contracts.md` (D-6/R-2 paragraph quoted in §1) and `wire-fixtures.json`
  (`cursorUnsignedHarnessBinding`, `historyErrors`) — the binding object was parsed with a read-only
  `python3 -c "json.load(...)"` to list its members, not executed as a cursor.
- Source, read to establish current behaviour only: `ConsoleHistoryCursorCodec.java` (payload builder,
  `parse`, `requiredText`, `mandateFingerprint`), `ConsoleHistoryRoute.java` (`queryOf`, `parseAfter`,
  `newSnapshot`, `continuation`, `reauthenticateAfterRead`, `requireHead`), and a targeted grep of
  `ConsoleHistoryRouteTest.java` for the contradictory-pagination cases.
- Repository root `AGENTS.md` (read because it governs the checkout).
- Role files under `/home/pazfernando/.openclaw/workspace/ai-sdlc-requirements/` were not re-read in this
  pass; the task's bounded correction did not require a new term or scope decision, and the existing
  procedure was followed as written.

## Integrity checks actually run

| Check | Result |
| --- | --- |
| `sha256sum reviews/d6-clarifications-20261123/domain-review.md` | `d6a57e0ca4f0e22c838c8e397bd5d576b95e7f6fa9d2a880989ef6b9141464b9` — **matches the task pin** |
| `sha256sum d6-clarifications-20261123/d6-clarifications.md` | `9bb8ca333e7a8b1f4f2e7d9072dfbc00c57b0648b1e62d2aa6161dbfd8bc0237` — matches the reviewed revision |
| `sha256sum -c d6-clarifications-20261123/OUTPUTS.SHA256SUMS` | **5/5 OK** in place, after this pass; the superseded packet is byte-identical |
| `sha256sum -c continuation-rev3-20261123/OUTPUTS.SHA256SUMS` | **21/21 OK**; manifest `2000bb00…ab467` as pinned |
| `diff` of revision 1 against revision 2 text | only the edited paragraphs differ; the full diff was reviewed line by line before `OUTPUTS.SHA256SUMS` was written |
| `git status --porcelain` (read-only) | used once to confirm the working-tree state around the concurrent source changes; **no mutation**. `git rev-parse` was not re-run, so branch/HEAD are not attested for this pass |

Commands used: `pwd`, `ls`, `find`, `sed`, `grep`, `awk`, `wc`, `sha256sum`, `sha256sum -c`, `diff`,
`mkdir`, `cp`, one read-only `python3 -c` JSON member listing, `git status --porcelain`, and file writes
through the editor tool. No test, build, compiler or runtime command was executed.

## Disclosure — concurrent source changes

`ConsoleHistoryRoute.java` (mtime 2026-11-23 23:30:53, SHA-256 `6da3ca7a…`) and
`ConsoleHistoryRouteTest.java` (mtime 2026-11-23 23:31:08, SHA-256 `4e4e3a26…`) differ from the pins in
revision 1's `INPUTS.SHA256SUMS` (`51ad2b71…`, `483d4773…`). The changes were made by another actor
while this revision was being written; this task made no source edit. Consequence for the packet: §1
and §7 record the **current** readings with hashes, note that revision 1 described older bytes, and do
not treat the changes as reviewed, tested or run by this task. `ConsoleHistoryCursorCodec.java` still
matches its revision-1 pin (`388f5f79…`).

## Limitations

- **Behaviour statements are source readings, not execution evidence.** No test, build or route call was
  made; the concurrent changes in particular are described from the bytes, not from a green run.
- **Revision 2 has not been re-reviewed.** The review verdict quoted throughout is the revision-1
  verdict. Reviewer, Security and human acceptance are all absent for these bytes.
- **Four review notes are deliberately unapplied** (items 8–11, future/adjacent); see
  `supersession.md` and `review-brief.md`.
- **Byte identity attaches to working files**, not to a commit; no branch or HEAD attestation is offered.
- **No external effect.** No Git write, stage, commit, push, PR, merge, deployment, provider call,
  credential flow or onward delegation. Writes are confined to this directory.
