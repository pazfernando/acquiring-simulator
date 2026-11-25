# Re-review brief — D-6 clarifications rev 2 (changed clauses only)

For the same independent acquiring-domain reviewer that returned CONDITIONAL on revision 1, then for
human approval. **Bounded**: re-check the corrected bytes only. The business analysis of C-1, C-2 and
C-4 does not need to be redone; they were PASS and none of their semantics moved.

## Exact bytes under review

Repository `/home/pazfernando/projects/acquiring-simulator`, directory
`docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/contract-deltas/d6-clarifications-rev2-20261123/`:

- `d6-clarifications.md` — SHA-256 `3aaac88a72cb4abd3936786f8e467fa5fb164cb39c76bfea99daef8edd9643a3`
- `OUTPUTS.SHA256SUMS` — authoritative for this packet; verify it first.
- Superseded text for diffing: `d6-clarifications-20261123/d6-clarifications.md`
  `9bb8ca333e7a8b1f4f2e7d9072dfbc00c57b0648b1e62d2aa6161dbfd8bc0237`.
- Your revision-1 review: `reviews/d6-clarifications-20261123/domain-review.md`
  `d6a57e0ca4f0e22c838c8e397bd5d576b95e7f6fa9d2a880989ef6b9141464b9`.

## What changed, and the single question for each

Gating (your items 1–2):

1. **C-3.1(b)** now keeps only the syntactic `after` errors; the head-dependent condition moved out.
   *Question:* is the request-form set now free of anything that needs the mandate or a store read?
2. **C-3.3** now orders: form `400` → resolution `503`/`403` → malformed-payload `400` → retired-schema
   `410` → expired `410` → context `403` → changed-representation `410` → head `503`/`404` →
   `after > H` `400` → `R`/`F` `410` → bound-`lastSequence` `400` → `200`. The earlier `400` is renamed
   "malformed payload", the schema `410` is new, and schema-before-context is stated explicitly.
   *Question:* is the order now implementable and does the explicit schema-before-context precedence
   answer your defect 2 without introducing a distinguishable state a client should not see?
3. **C-3.4** (new) fixes the head-dependent `400` at that later step and forbids checking it early.
   *Question:* does it match `afterBeyondTheDurableHeadIsInvalidRatherThanSilentlyClamped`, which reads
   the head, without weakening the zero-head assertion of the syntactic form test?

Editorial (your items 3–7):

4. **C-1.2** — "The cursor's bound members, taken together, MUST differ…", with the covered fields
   made explicit (digest covers four, `terminalRef` separately).
   *Question:* can an implementer still mistakenly fold `terminalRef` into the digest? If not, the
   item is closed.
5. **C-1.3 / §7** — context `403` before fingerprint `410` at both re-authentication points, including
   post-read; §7's "none for the demo registry" replaced.
   *Question:* is the post-read case now unambiguously covered?
6. **C-2.4** — "with `after` defaulting to `0` when `after` is absent".
7. **§6** — documentary mappings `azp` → `authorizedParty`, `H`/`R`/`F` →
   `snapshotHead`/`expiryRevision`/`firstRetainedSequence`, and the absent fixture `audience` declared a
   **documentary omission** of a required member (grounded in `requiredText(node, "audience")` and the
   C-3.3 audience binding).
   *Question:* is that the correct reading, or is `audience` better treated as server-internal?
8. **§8** — "No Git mutation or provider command; read-only `git rev-parse` / `git status --porcelain`
   inspections were disclosed in `verification.md`", plus an explicit statement that revision 2 has not
   been re-reviewed.

## Deliberately not applied

Your items **8–11** are marked future/adjacent and are recorded, not applied: C-4 cross-mode (future WS
slice), the C-1.5 "every required member" choice (left as written — that is a team decision), D-8
`identity-map.md` consistency (coordinator-level task; D-8 not reopened), and the `terminalRef`
absent-versus-null parser note. If you consider any of them gating after all, say so and it will be
handled as a separate bounded change rather than folded in here.

## Source drift you should know about before judging §7

`ConsoleHistoryRoute.java` and `ConsoleHistoryRouteTest.java` changed in the working tree **while this
revision was being written** (hashes and mtimes in §7's concurrent-source observation). The current
bytes already show the post-read context-first order and the C-2 refusal; the codec still matches its
revision-1 pin, so the C-1.5 schema-`410` path is still unimplemented. Revision 2 records the current
readings and does not claim those edits. §7 remains a recorded impact table, not execution evidence.

## Still out of scope

Error bodies, TTL, `H`/`R`/`F`, response schema, margins, retention, payment semantics, the server-side
read-pass store, cursor-key sourcing and issuer verification. No code, fixture or IaC edit accompanies
this revision. A reviewer verdict, a Security verdict and human acceptance remain separate; revision 2
has none of them yet.
