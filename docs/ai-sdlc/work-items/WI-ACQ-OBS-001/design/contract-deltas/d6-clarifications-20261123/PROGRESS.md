# D-6 clarifications — progress

Status: **COMPLETE DRAFT** (stub written first, then substantive work; this line updated at the end).

- **Model identity:** runtime-declared `nan/deepseek-v4-flash`. `sessions_list` reports `gpt-6.1-sol`
  for this session key, i.e. the configured default. The discrepancy is recorded, not resolved by
  assertion: the run is treated as being on the configured fallback. Details in verification.md.
- **Scope:** four points only — mandate identity in the cursor, `cursor` combined with `after`,
  malformed-pagination mapping, and the `epoch` member in an HTTP-only read. Nothing else in D-6/R-2 is
  restated or changed.
- **Decisions taken:** (1) the SHA-256 mandate digest is the normative representation of D-6's
  "registry mandate identity/version" for a registry without a mandate identifier or version counter,
  with named `registryMandateId`/`registryVersion` mandatory only for a versioned registry;
  (2) `cursor` + `after` is rejected as `400 INVALID_CURSOR` with no precedence rule, rather than
  silently ignoring either parameter; (3) one code — `400 INVALID_CURSOR`, `action: null` — covers all
  unusable pagination input, with a fixed observable failure order; (4) an HTTP-only cursor omits
  `epoch` and must not emit an explicit `null`, while a live read must carry and compare it.
- **Inputs verified:** adopted packet `OUTPUTS.SHA256SUMS` **21/21 OK** (manifest `2000bb00…ab467`, as
  pinned); packet `INPUTS.SHA256SUMS` **30/32**, with `WORK_ITEM.md` and `owner-decisions.md` changed
  since sealing — disclosed, not attributed to this delta.
- **Files written (this directory only):** `PROGRESS.md`, `d6-clarifications.md`, `review-brief.md`,
  `verification.md`, `INPUTS.SHA256SUMS`, `OUTPUTS.SHA256SUMS`.
- **Not done, by instruction:** no code, test, fixture or IaC edit; no build or execution; no Git or
  provider command; no cloud effect; no onward delegation; no review requested on the owner's behalf.

Independent domain review and human approval are pending and are not implied by this record.
