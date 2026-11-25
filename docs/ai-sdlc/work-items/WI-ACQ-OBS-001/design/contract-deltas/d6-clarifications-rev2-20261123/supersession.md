# Supersession note — D-6 clarifications rev 2

## What this directory is

Revision 2 of the D-6 clarification text. It supersedes revision 1 in
`design/contract-deltas/d6-clarifications-20261123/` (text SHA-256 `9bb8ca33…dbfd8bc0237`, packet
manifest `22a1f2d8…e517d85`). Revision 1's directory is unchanged and remains the reviewed bytes;
this directory is a separate owned packet, not an in-place edit.

Both revisions are **PROPOSED CLARIFICATION TEXT — NOT NORMATIVE**. Neither is human-approved,
normatively incorporated or construction-cleared. Revision 2 has not itself been re-reviewed.

## Why it exists

The independent acquiring-domain review of revision 1
(`reviews/d6-clarifications-20261123/domain-review.md`, SHA-256
`d6a57e0ca4f0e22c838c8e397bd5d576b95e7f6fa9d2a880989ef6b9141464b9`) returned **CONDITIONAL**: C-1,
C-2 and C-4 PASS; C-3 CONDITIONAL on two non-semantic gating corrections to its fixed failure order,
with five editorial items and four non-gating future/adjacent notes. Revision 2 carries the gating
and editorial items and changes nothing else. No clause changes which requests are refused, the
status or body used, or any payment, disclosure, authority or freshness rule.

## Gating corrections applied (review items 1–2)

| Review item | Clause | Change made |
| --- | --- | --- |
| 1 | C-3.1(b) | Removed "greater than the frozen snapshot head" from the request-form `400`. C-3.1 now covers only conditions the request form establishes, and a sentence says the head-dependent condition is fixed at its own step. Rationale: the frozen head belongs to the authorized transaction's partition and is unknown before mandate resolution and the store head read, so the revision-1 order was not implementable for that sub-case. |
| 2a | C-3.3 | Added `400 INVALID_CURSOR` for `after` greater than the frozen head as a **separate step after the `503`/`404` head step**, before the `R`/`F` and bound-`lastSequence` comparisons. Status and body are unchanged. |
| 2b | C-3.3 | Renamed the earlier "cursor signature or payload format" `400` to "cursor signature does not verify, or the signed payload is malformed". |
| 2c | C-3.3 | Added a distinct `410 RESYNC_REQUIRED` step "signature verifies, but the payload schema or version is retired, per C-1.5", placed after the malformed-payload `400` and after signature verification, and **before** the context `403`. The schema-before-context precedence is stated in the clause text rather than left implicit, with the reason (a retired payload may not carry the current context members). |
| 2d | C-3.4 (new) | New clause that fixes the position of the head-dependent `400`, states it is not part of the C-3.1 request-form set, and forbids refusing it before mandate resolution or the store head read. |

The resulting order is the review's requested one: `401` → form `400` → resolution `503`/`403` →
malformed-payload `400` → retired-schema `410` → expired `410` → context `403` → changed
representation `410` → head `503`/`404` → `after > H` `400` → `R`/`F` `410` → bound-`lastSequence`
`400` → `200`. The retired-schema `410` is placed before the expiry `410` because a retired payload's
member set may not be interpretable under the current schema; the relative order of those two steps is
unobservable anyway, since both answer `410 RESYNC_REQUIRED` with action `RESYNC_REQUIRED`.

## Editorial items applied (review items 3–7)

| Review item | Clause | Change made |
| --- | --- | --- |
| 3 | C-1.2 | "The representation MUST differ…" → "The cursor's bound members, taken together, MUST differ…"; the covered fields are now made explicit — `callerId`, `acquirerRef`, `merchantRef` and the permitted routes through the C-1.1(b) digest, and terminal scope through `terminalRef`. Prevents an implementer from folding `terminalRef` into the digest and breaking the C-1.1(b) formula. |
| 4 | C-1.3 | States that the context-triple `403` precedes the mandate-representation `410` at **both** re-authentication points, including the post-read one, so a cursor whose triple and representation both differ is answered `403` on either path. |
| 4 | §7 | The C-1 row now says "none on the pre-read path" and records the post-read context-first requirement instead of "none for the demo registry". |
| 5 | C-2.4 | "with `after` defaulting to `0` when `after` is absent". |
| 6 | §6 | Added documentary name mappings: `azp` → `authorizedParty`; `H`/`R`/`F` → `snapshotHead`/`expiryRevision`/`firstRetainedSequence`. States that the fixture's absent `audience` is a **documentary omission** of a required member, not a server-internal-only member, grounded in the codec's `requiredText(node, "audience")` and C-3.3's audience binding. |
| 7 | §8 | "No Git … action occurred" reconciled with revision 1's disclosed read-only `git rev-parse` / `git status --porcelain` inspections: now "No Git mutation, provider, credential or cloud action occurred; the only Git use was the read-only inspections disclosed…". §8 also now says the revision itself has not been reviewed. |

## Source-reading corrections carried for truthfulness (not clause changes)

The working tree's `ConsoleHistoryRoute.java` and its test changed **while this revision was being
written**, so two revision-1 source readings no longer matched the bytes:

- Revision 1 described the post-read `reauthenticateAfterRead` as checking the fingerprint `410`
  before the context `403`. The current bytes check context first. §7 records this with hashes and
  states that this task did not make the edit.
- Revision 1 described `queryOf` as returning a continuation at the first non-empty `cursor` and
  silently ignoring `after`. The current bytes refuse that combination before mandate resolution.
  §1 and §7 record the current reading.

Both are disclosed in §7's concurrent-source observation, with the hashes and mtimes that date the
drift. The codec still matches its revision-1 pin, so the C-1.5 schema-`410` change remains open.
Nothing here is execution evidence: no test or build was run.

## Review items not applied, and why

Items 8–11 of the review are marked "Future / adjacent notes (not gating this slice)". Applying them
would add or change contract text beyond the corrections the review requires, so they are recorded
and left alone:

- **8 — C-4 cross-mode.** What a live-bound cursor gets on an HTTP-only read (and the reverse) is
  unstated; the review recommends `410` and assigns it to the future WS slice. Not this slice.
- **9 — C-1.5 scope.** The review offers a choice: broaden "retired schema" to every required-member
  addition, or keep the clause scoped as written. Kept as written; the choice is the team's, not a
  correction.
- **10 — D-8 adjacent consistency.** `identity-map.md` still lists `registryMandateId`/`registryVersion`
  in the D-8/G-4 mandate record. This is a coordinator-level consistency task; this packet does not
  reopen D-8.
- **11 — Implementation note.** The cursor parser treats absent and explicit-null `terminalRef`
  alike. No data leak today; no contract text changes.

## Unchanged by design

§1's D-6 and fixture quotations, C-1.1, C-1.4, C-1.5, the "Why (b) is chosen" discussion, C-2.1–C-2.3,
the C-2 rejection rationale, C-3.2's meaning paragraph (apart from the one added sentence about C-3.4
reusing the code), C-4 in full, the §6 table's existing rows, and the §6 payload member set are the
reviewed revision's text. `d6-clarifications.md` here is a mechanical carry of that text plus the
edits listed above; the diff should show no other change.

## Provenance

- Reviewer input, unmodified: `reviews/d6-clarifications-20261123/domain-review.md`
  `d6a57e0ca4f0e22c838c8e397bd5d576b95e7f6fa9d2a880989ef6b9141464b9`.
- Superseded text, unmodified: `d6-clarifications-20261123/d6-clarifications.md`
  `9bb8ca333e7a8b1f4f2e7d9072dfbc00c57b0648b1e62d2aa6161dbfd8bc0237`.
- Model that ran: runtime `nan/deepseek-v4-flash`; see `PROGRESS.md` and `verification.md` for the
  identity observation and its limits.
- No Git mutation, provider, credential or cloud action; no code, fixture or IaC edit; no onward
  delegation.
