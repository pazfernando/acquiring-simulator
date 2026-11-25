# D-6 clarifications rev 2 — progress

Status: **COMPLETE — CORRECTED DRAFT, NOT NORMATIVE, NOT RE-REVIEWED.** Stub written first; this line
updated at the end.

- **Model that actually ran:** the runtime context for this subagent session declares
  `model=nan/deepseek-v4-flash`; the configured default for this agent is `openai/gpt-6.1-sol`. The run
  therefore used the configured fallback, not the primary, and no model override was requested or
  applied. The declaration is the identity of the process that produced these files; `sessions_list`
  was not re-run in this bounded task, so no second observation is offered. Details in
  `verification.md`.
- **Scope:** one bounded correction pass. Apply every gating and editorial item in
  `reviews/d6-clarifications-20261123/domain-review.md` §"Exact corrections needed before adoption"
  (items 1–7) to the full revision-1 clarification text, and change nothing else. Items 8–11 are
  future/adjacent and were recorded, not applied.
- **Revised settlement (unchanged in substance):** (1) `mandateFingerprint` is the normative
  representation of "registry mandate identity/version" for this unversioned registry; (2) `cursor`
  plus `after` is refused, with no precedence rule; (3) `INVALID_CURSOR` covers unusable pagination
  input, now split between a request-form `400` and a head-dependent `400` at its implementable
  position, with a distinct retired-schema `410` before the context `403`; (4) an HTTP-only cursor
  omits `epoch` and must not emit an explicit null.
- **Files written (this directory only):** `PROGRESS.md`, `d6-clarifications.md`, `supersession.md`,
  `review-brief.md`, `verification.md`, `INPUTS.SHA256SUMS`, `OUTPUTS.SHA256SUMS`.
- **Preserved byte-for-byte:** `design/contract-deltas/d6-clarifications-20261123/` (`sha256sum -c`
  5/5 OK after this pass) and every other reviewed packet. No file outside this directory was edited.
- **Inputs verified:** reviewer file `d6a57e0c…` (matches the task pin); revision-1 text `9bb8ca33…`
  (matches); revision-1 `OUTPUTS.SHA256SUMS` 5/5 OK; `continuation-rev3-20261123/OUTPUTS.SHA256SUMS`
  21/21 OK.
- **Disclosure:** `ConsoleHistoryRoute.java` and `ConsoleHistoryRouteTest.java` changed in the working
  tree while this revision was written. Revision 2 records the current source readings with hashes and
  mtimes, states that this task did not make those edits, and does not treat them as reviewed or run.
- **Not done, by instruction:** no Git mutation or provider command; no code, test, fixture or IaC
  edit; no build or execution; no cloud or credential effect; no onward delegation.

Independent re-review of this revision and human approval are pending and are not implied here.
