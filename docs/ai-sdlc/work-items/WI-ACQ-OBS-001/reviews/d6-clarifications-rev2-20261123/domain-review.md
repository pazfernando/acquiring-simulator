# Independent acquiring-domain re-check — D-6 clarifications rev 2 (changed clauses only)

2026-11-23, America/Bogota. **PASS for adoption of C-1, C-2, C-3 and C-4** on the rev 2 text. All
seven corrections the rev 1 review required are **applied, and applied as asked**; the two gating C-3
corrections are applied in substance and in position, not merely acknowledged. No gating item remains
CONDITIONAL or BLOCKED. Nothing outside the changed clauses moved: the diff touches only the seven
corrected areas plus the revision preamble and two disclosed source-reading rows. No payment or
disclosure semantics changed, and no new authority, source or projection is introduced. Adoption still
requires only **ordinary human acceptance**, not a new owner decision.

This is an advisory documentary re-check. It is **not** human approval, normative incorporation,
construction clearance, QA execution or deployment authority. "Adopted packet" means the pinned
`continuation-rev3-20261123` candidate, whose manifest `2000bb00…ab467` re-verifies 21/21 here; it
self-describes as DRAFT/NOT NORMATIVE.

## Execution identity and model actually used

- Role: Acquiring Domain Reviewer (`ai-sdlc-acquiring-domain`), subagent depth 1, same reviewer as the
  rev 1 CONDITIONAL.
- **Model that actually ran: `nan/deepseek-v4-flash`**, the runtime identity of this session. The
  configured default `openai/gpt-6.1-sol` did not run this pass; no override was selected. No provider
  qualification is inferred, and `sessions_list` was not consulted.
- Checkout `/home/pazfernando/projects/acquiring-simulator`, host `fernando-OC`. Conclusions attach to
  working-file bytes, not to a commit; no branch/HEAD attestation is offered.
- Commands: `ls`, `find`, `sha256sum`, `sha256sum -c`, `diff -u`, `sed`, `grep`, `stat`, owned writes.
  No test, build, route call, browser, issuer, IAM, Terraform, DynamoDB, cloud, MCP or provider
  command; no Git command; no onward delegation. Writes confined to this review directory;
  `PROGRESS.md` written first.
- Skill applied: `payment-behavior-review`, Authorization branch (original-reference correlation,
  repeated instruction, no unintended second purchase), as in rev 1. The four clauses are a history
  GET; the skill's payment boundaries are used to confirm non-effect, not to re-derive the clauses.

## Pins recomputed before judgement — all MATCH

| Input | SHA-256 | Result |
| --- | --- | --- |
| Rev 1 review | `d6a57e0ca4f0e22c838c8e397bd5d576b95e7f6fa9d2a880989ef6b9141464b9` | MATCH (task pin) |
| Rev 1 packet `d6-clarifications.md` | `9bb8ca333e7a8b1f4f2e7d9072dfbc00c57b0648b1e62d2aa6161dbfd8bc0237` | MATCH (superseded text) |
| Rev 1 `OUTPUTS.SHA256SUMS` | `22a1f2d89a846a8e432c97ca9db45bb3c556c389c318854dc1af7d7ece517d85` | MATCH; **5/5 OK in place** — reviewed packet byte-identical |
| Rev 2 `d6-clarifications.md` | `3aaac88a72cb4abd3936786f8e467fa5fb164cb39c76bfea99daef8edd9643a3` | MATCH (task pin) |
| Rev 2 `OUTPUTS.SHA256SUMS` | `1cb58943ada5b6247f2d49818de7ccb682c8b19e5c5147ffe33f03936cc070c8` | 6/6 OK; task pin `c63f4d71…` is in fact rev 2 `PROGRESS.md` (see note) |
| Rev 2 `PROGRESS.md` | `c63f4d7153ba5e6edeb1abdb4ea84edb6c59495842f51ebf05b03e1a98123ea5` | MATCH (task pin, mislabelled as OUTPUTS) |
| Rev 2 `supersession.md` | `3f1ee0fbbfd16eb8b00bde33e43fb339d5fb27a1e072d343528c65a81f632d5b` | MATCH (task pin) |
| Rev 2 `INPUTS.SHA256SUMS` | `061cd1a5…e09b49fd70` | 11/11 OK from repo root |
| Adopted `continuation-rev3-20261123/OUTPUTS.SHA256SUMS` | `2000bb00…ab467` | MATCH; **21/21 OK** |
| `ConsoleHistoryRoute.java` / `Test` / codec | `6da3ca7a…`, `4e4e3a26…`, `388f5f79…` | MATCH rev 2 §7 and rev 2 `INPUTS`; codec matches its rev 1 pin as the packet states |

**Pin-label discrepancy, recorded not gating.** The handoff called `c63f4d71…` the rev 2
`OUTPUTS.SHA256SUMS`. It is the rev 2 `PROGRESS.md`; `OUTPUTS.SHA256SUMS` is `1cb58943…` and passes
6/6. The three digests the handoff named all exist and all match the bytes they actually label, so the
packet is verified; only the label was wrong.

The rev 1 packet keeps its 23:16–23:17 mtimes, after my 23:28 review, and verifies 5/5: the reviewed
bytes did not move under this pass.

## Method

1. Full `diff -u` of rev 1 against rev 2 `d6-clarifications.md`, read hunk by hunk and to the end of the
   diff. The diff is the complete change set; I did not rely on the packet's own summary of it.
2. Each of my seven items mapped to the hunk that satisfies it, and judged against the exact wording I
   asked for — not against whether something similar was added.
3. The packet's two §1/§7 source-behaviour claims re-read in the current bytes, because a §7 row that
   asserts the code already has the right order is an implementation claim I should not inherit. The
   code was read for order only; nothing was run.
4. Cross-checks against D-6 text and the fixture in the adopted packet, which I re-verified 21/21.
5. No re-derivation of the C-1, C-2, C-4 business analysis, as scoped.

## Item-by-item result

| # | Item (rev 1 ask) | Status | Exactly as asked? |
| --- | --- | --- | --- |
| 1 | C-3.1(b): keep only syntactic `after` errors; move "greater than the frozen head" out of the request-form `400` | **Applied** | Yes |
| 2a | C-3.3: separate `400` for `after` > frozen head, after the `503`/`404` head step | **Applied** | Yes |
| 2b | C-3.3: rename "signature or payload format" `400` to malformed payload | **Applied** | Yes |
| 2c | C-3.3: distinct retired-schema `410` after signature verification and before the context `403`, precedence stated | **Applied** | Yes |
| 3 | C-1.2: "bound members, taken together" plus explicit field coverage | **Applied** | Yes |
| 4 | C-1.3 and §7: post-read case covered; §7 no longer "none for the demo registry" | **Applied** | Yes |
| 5 | C-2.4: "defaulting to `0` when `after` is absent" | **Applied** | Yes |
| 6 | §6: `azp`/`H`/`R`/`F` mappings and the `audience` omission declaration | **Applied** | Yes |
| 7 | §8: reconcile the Git non-claim with the disclosed read-only inspections | **Applied** | Yes |

### 1. C-3.1(b) — head-dependent condition removed from the form set. Applied; correct.

C-3.1 now reads "every unusable pagination input **that the request form alone establishes**", and (b)
is "`after` empty, containing a non-digit, negative, or longer than 12 characters". A new sentence
states that "a value greater than the frozen snapshot head — is not part of this set: it cannot be
judged from the request form alone and is fixed at its own step in C-3.4."

*Is the request-form set now free of anything that needs the mandate or a store read?* Yes. The six
items are: `snapshot` value, `after` syntax, unknown names, repeated names, empty `cursor`, and the
`cursor`+`snapshot`/`after` combination. Each is decidable from the parsed query alone; none depends on
the registry or the event partition. The zero-head assertion of
`malformedPaginationInputUsesTheRecordedProvisionalMapping` is therefore satisfiable, and its case list
(`?after=abc`, `?after=-1`, `?after=1&after=2`, `?snapshot=old`, `?after=` , `?after=9999999999999`,
…) contains only syntactic cases.

### 2. C-3.3 / C-3.4 — order and the retired-schema step. Applied; correct and implementable.

The fixed order is now: `401` → form `400` → resolution `503`/`403` → malformed-payload `400` →
retired-schema `410` → expired `410` → context `403` → changed-representation `410` → head
`503`/`404` → `after > H` `400` → `R`/`F` `410` → bound-`lastSequence` `400` → `200` page.

- **2a position.** The `after > H` `400` sits after the `503`/`404` head step and before the `R`/`F`
  and bound-`lastSequence` comparisons — exactly the place I asked for, and the place the current
  `newSnapshot` already refuses `afterSequence() > head.lastSequence()`. New C-3.4 fixes it and adds
  the converse obligation: "MUST NOT be refused before mandate resolution or before the store head
  read", with the reason (the frozen head belongs to the authorized transaction's partition). That is
  stronger than my wording and in the same direction.
- **2b naming.** The earlier step is now "cursor signature does not verify, or the signed payload is
  malformed", so a retired-schema cursor is no longer collapsed into "format".
- **2c precedence.** A distinct `410 RESYNC_REQUIRED` step ("signature verifies, but the payload schema
  or version is retired, per C-1.5") sits after signature verification and **before** the context
  `403`, and the clause states the precedence explicitly with its reason: a retired payload may not
  carry the current context members and cannot be interpreted under the schema now in force. That
  resolves the C-1.5 versus C-3.3 conflict I raised, and it is stated rather than left to be inferred
  from the step list alone.
- *Unobservable sibling ordering.* Retired-schema `410` precedes expiry `410`; both are `410
  RESYNC_REQUIRED` with the same action, so the relative order is not client-observable and is sound
  either way. `supersession.md` says the same.
- *Does the explicit schema-before-context order expose a state the client should not see?* No. The
  caller holds the cursor and can already read its payload version; the only oracle is "your own
  cursor's schema is retired", and a wrong-context caller still gets no page and no tenant data. The
  `403`-before-`410` rule for a *current-schema* cursor is unchanged, so no cross-tenant signal is
  added. This is the trade I accepted in rev 1, now written into the clause.
- *Implementability.* The order matches the route for every step that exists: `queryOf` refuses the
  combination before resolution and the store; `continuation` checks signature/expiry, then context
  `403`, then fingerprint `410`, then `requireHead` `503`/`404`, then `R`/`F`, then bound
  `lastSequence`; `newSnapshot` reads the head then refuses `after > H`. The one step with no code is
  the retired-schema `410`; the packet discloses that the codec still maps a stale schema to `400`,
  and §7 records the code change. That is an implementation gap, not a text defect — see risks.

### 3. C-1.2 — bound members, taken together. Applied; exactly the sentence asked for.

"The cursor's **bound members, taken together**, MUST differ whenever any registry-supplied field that
changes what the read may disclose differs", and the coverage is made explicit: "`callerId`,
`acquirerRef`, `merchantRef` and the permitted routes through the C-1.1(b) digest, and terminal scope
through `terminalRef`". *Can an implementer still fold `terminalRef` into the digest?* Not without
contradicting both C-1.1(b) (the digest formula is exact and excludes it) and this sentence (which
assigns terminal scope to another member). The item is closed. No behaviour change: the union of bound
members is what the code already writes.

### 4. C-1.3 / §7 — post-read ordering. Applied; unambiguously covered, and the §7 claim is true of the current bytes.

C-1.3 now says the context-first order "holds at both re-authentication points: before the store read
and in the post-read re-authentication … a cursor whose triple and representation both differ is
answered `403`, not `410`, on either path." That closes the ambiguity without needing an exception
clause. §7's C-1 row now says "none on the pre-read path …; the post-read path needs the context-first
order C-1.3 states, which the current bytes already show", replacing "none for the demo registry".

I checked that parenthetical claim rather than accepting it: in the current
`ConsoleHistoryRoute.reauthenticateAfterRead`, the sequence is `401` (authentication) → `503`
(registry unavailable) → `403` (mandate null) → `403` (acquirer/merchant/terminal triple) → `410`
(fingerprint), with a comment citing C-1.3. Context does precede the fingerprint, so the clause and the
working bytes now agree; the earlier divergence I reported is gone. The row is still a source reading,
not execution evidence — the tests that would prove it were not run (by me or, per the packet, by the
packet's author).

### 5. C-2.4 — after defaulting. Applied verbatim.

"with `after` defaulting to `0` **when `after` is absent**". The ambiguity I flagged (a supplied
`after` appearing to be ignored) is removed, and it matches `queryOf`'s initialise-then-parse shape. No
behaviour change.

### 6. §6 — mappings and the `audience` declaration. Applied; and the reading is the correct one.

Three rows were added — `azp` → `authorizedParty`; `H`/`R`/`F` →
`snapshotHead`/`expiryRevision`/`firstRetainedSequence`; and `audience` absent in the fixture declared a
**documentary omission** of a required member — plus one sentence in the payload paragraph. The
`historyErrors["400"]` row now includes the head-dependent case.

*Is "documentary omission" right, or is `audience` server-internal?* Documentary omission is right. The
codec builds `node.put("audience", binding.audience())` and its parse path calls
`requiredText(node, "audience")`, so `audience` is a member of the signed payload that the service
requires; C-3.3's `403` list includes `audience` as a bound check. A member the codec requires cannot
be "server-internal only", and the fixture, which the packet itself calls documentary, simply omits it.
The harness consequence (require `audience` when validating a minted payload, do not read the fixture
as the schema) is the safe direction: it can only cause a harness to reject a non-conforming payload,
never to accept one.

### 7. §8 — non-claims reconciled. Applied; and now also states rev 2 is unreviewed.

"No Git mutation, provider, credential or cloud action occurred; the only Git use was read-only
working-tree inspection — `git status --porcelain` in this pass, and the `git rev-parse` / `git status
--porcelain` recorded in revision 1's `verification.md` — with no staging, commit, push, PR, merge or
branch change." That is the reconciliation I asked for, and §8 now also says this revision has not been
re-reviewed. Accurate as of this pass except for the last clause, which my verdict now supersedes for
the changed bytes only.

## Nothing else moved

The diff touches, and only touches: the title and a new revision preamble; §1's "current
implementation behaviour" paragraph (source-reading correction, disclosed); C-1.2; C-1.3; C-2.4; C-3.1;
two added sentences in C-3.2; C-3.3; new C-3.4; three new §6 rows and one modified, plus two sentences
in the §6 payload paragraph; the three §7 rows, the concurrent-source observation and the test-impact
paragraph; §8.

Unchanged, verified against the rev 1 bytes: C-1.1 (including the exact digest formula), C-1.4, C-1.5,
the "Why (b) is chosen" discussion, C-2.1, C-2.2, C-2.3, the "Why rejection" discussion, C-4 in full
(C-4.1–C-4.3 and the absence rationale), the §6 existing rows, and the §6 required-member list. The
preamble's claim that §1's and §7's source-behaviour edits are readings, not clause changes, is
consistent with what moved. The packet's own "Unchanged by design" list in `supersession.md` is
accurate.

Two observations on the moved bytes, neither of which is an unrequested semantic change:

- The C-3.2 additions are the "closed at six bodies" sentence split and the sentence saying C-3.4
  reuses the same code and remedy. Both are required by the C-3.4 split and were disclosed.
- §1 and §7 were re-read against the current bytes because those bytes changed mid-revision. The
  packet discloses the drift, its hashes and mtimes, and does not claim the edits or a green run. I
  confirmed the two readings it now records (post-read context-first; `queryOf` refusing the
  combination) are true of the bytes; the codec pin is unchanged, so the retired-schema path is still
  absent, exactly as §7 says.

## No payment or disclosure semantics changed

- **Payment.** No clause touches an authorization, reversal, status inquiry, 3DS, clearing or
  settlement path. All four are history GET rules. `contracts.md`'s non-regression sentence ("No
  GET/login/WS/retry/expiry/repair sends any payment") is untouched. A history `400`/`410` must not be
  mapped to a payment retry, and C-3.2's remedy is restart at `?snapshot=new`.
- **Disclosure.** Every changed clause refuses or reclassifies a refusal; none adds a page, a member, a
  source or a projection. `after > H` still returns the same exact body and no page — it is merely
  decided after an internal head read, which discloses nothing because the head does not appear in the
  body. The retired-schema `410` replaces, for a verifiable-but-stale cursor, a `400` that also
  returned no page; the status change aligns C-3.3 with C-1.5 and D-6's "retired generation = `410`"
  taxonomy, and the client remedy is identical.
- **Order-driven deltas are refusals, not disclosures.** A wrong-context caller with `after > H` now
  receives `403` where rev 1's order would have produced `400`; a stale-schema caller with a wrong
  context now receives `410` where rev 1's text implied `403`. Neither response carries tenant data,
  a sequence number or a page.
- **Guarantees.** No-resend: unchanged and still absolute for this surface. Single-source: unchanged —
  the fingerprint is recomputed from the already-required mandate registry, not a new source.
  Truthful freshness: unchanged or strengthened — `after > H` is refused rather than clamped, the
  retired-schema cursor is refused rather than treated as malformed and retried, and the post-read
  context-first order keeps the freshness page guard intact. Cross-mandate/terminal/generation: no new
  path; the fingerprint plus `terminalRef` and the context triple still cover the five
  `AuthorizedContext` fields, and C-1.2 now spells that out.
- **Issuer/processor separation, reversal versus refund, status inquiry versus network management,
  3DS.** Not engaged by any changed sentence.

## Are C-1 to C-4 now PASS for adoption?

**Yes — all four, PASS for adoption of this text.**

- **C-1 PASS**, with C-1.2 and C-1.3 corrected exactly as required and verified against the working
  bytes.
- **C-2 PASS**, with C-2.4 corrected.
- **C-3 PASS** now: the two gating corrections are present, the head-dependent `400` is at an
  implementable position with a `MUST NOT` against early refusal, and the retired-schema `410` is a
  distinct step with stated precedence over the context `403`.
- **C-4 PASS**, unchanged from rev 1, with its cross-mode question still a future WS-slice note.

Nothing remains CONDITIONAL or BLOCKED at the text level. What remains is implementation and ordinary
human acceptance — not a contract question I can or should decide.

## Adoption still needs only ordinary human acceptance

My rev 1 conclusion stands and the corrections do not change it: these are within-direction
clarifications of recorded owner direction (cursor/page binding to the current subject, environment,
authorized context, transaction and read-pass generation, with `403`/`410` semantics and no payment
side effect). The owner direction already covers them; C-1 chooses the only non-fabricating
representation for an unversioned registry, and C-2/C-3 tighten malformed input and its order.

- **No new owner decision is required.** The changes are order, naming, member-coverage wording and
  non-claims. None alters which requests succeed, what data a caller can obtain, who may obtain it, or
  any payment outcome.
- **Ordinary human acceptance remains**, plus the explicit acknowledgment of C-1 already recommended
  (that the demo registry uses `mandateFingerprint` rather than the fixture's literal `M-1`/`1`). That
  acknowledgment is an interpretation of already-recorded direction, not a new business choice. If the
  owner instead wants the literal named fields, that remains a material deviation requiring explicit
  direction — and it would need registry state this slice does not have.
- My verdict is advisory. It does not approve, adopt, incorporate or clear construction. Normative
  incorporation still needs the human approval step recorded in the packet and the role contract.

## Remaining risk the implementation must respect

1. **`after > H` is now refused after the store head read.** The implementation must (a) return the
   exact `INVALID_CURSOR` body with `action: null` and no page; (b) not leak the head, `snapshotHead`
   or any sequence number in that error; (c) keep the head `503`/`404` ahead of it, so a store outage
   is `503`, not `400`; (d) not add a page or range read on this path — the current `newSnapshot`
   refuses before `page(...)`, which is correct; and (e) keep the syntactic form check free of any
   head call, so `malformedPaginationInputUsesTheRecordedProvisionalMapping`'s zero-head assertion stays
   true. The two behaviours pull in opposite directions and are exactly what C-3.4 separates.
2. **Test position is not yet pinned.** `afterBeyondTheDurableHeadIsInvalidRatherThanSilentlyClamped`
   asserts status and error but not `headCalls`, `reader.calls` or registry calls, so it would still
   pass if the refusal moved earlier or later. Strengthen it with `headCalls == 1` and
   `reader.calls == 0` (and a registry call count) so the C-3.4 position is actually tested. QA note,
   not contract text.
3. **Retired-schema `410` is unimplemented.** The codec still returns `INVALID` (`400`) for a
   verifiable but stale-schema payload, so the rev 2 order's `410` step has no code. Until it exists,
   a stale-schema cursor gets `400` — the same refusal family and remedy, but not the written status.
   The packet discloses this; the implementer must not treat the packet as proof it is done.
4. **Concurrent source drift.** `ConsoleHistoryRoute.java` and its test changed mid-revision and are
   not reviewed, committed or run in either pass. The post-read order and the C-2 refusal are true of
   the bytes I read; they are not proven by a green test run. Whoever accepts the change must attach
   the implementer's change record and the test result.
5. **Schema-before-context is a small oracle.** A caller learns that its own cursor's schema is
   retired before learning that its context is wrong. Accepted: the caller holds the payload, no page
   is returned, and no other tenant's data is reachable. It should not be widened into "answer schema
   before signature" — signature verification must stay first, or a forged payload could probe
   schema/version handling.
6. **`C-2.1(i)` still bounds the initial form's `after` by the frozen head.** Read with C-3.4's
   `MUST NOT`, the check is at the later step; read alone, a literal implementer could think the bound
   belongs to the form. This is a wording residue of a sentence that was already in rev 1, not a moved
   byte, and it is non-gating. If the text is touched again, say "… and, when the head is known, not
   greater than it (C-3.4)".
7. **Fingerprint preconditions.** The digest assumes ASCII route names without `,` or NUL and Java
   string order equal to code-point order; the current fixed routes satisfy this. Two mandates with
   identical authority remain the same generation by design and reach no additional data.
8. **Future/adjacent notes still open, as recorded:** C-4 cross-mode live/HTTP-only behaviour (WS
   slice), the C-1.5 "every required member" choice (team decision), D-8 `identity-map.md`
   consistency (`registryMandateId`/`registryVersion` still listed there), and the `terminalRef`
   absent-versus-null parser strictness. None gates this text.

## Limits and non-claims

Documentary re-check of the changed bytes only. I did not execute the route, tests, QA driver, a
browser, an issuer, IAM, Terraform, DynamoDB Local, an edge or any cloud surface. The §7 post-read
order and the C-2 refusal are source readings of the bytes at the hashes above, not execution
evidence; the retired-schema path is absent there. No real JWKS, signature, issuer, read-pass store or
cursor-key custody claim is made. N-02 and every other Security/QA/retention disposition stand as
recorded. No Git or provider command, no application/IaC/fixture/input edit, no cloud/credential
effect, no external send, no onward delegation. No human approval, normative incorporation,
construction clearance, QA PASS or deployment authority is implied.

## References

- Reviewed text: `design/contract-deltas/d6-clarifications-20261123/d6-clarifications.md`,
  `9bb8ca33…`; packet 5/5 OK.
- Corrected text: `design/contract-deltas/d6-clarifications-rev2-20261123/d6-clarifications.md`,
  `3aaac88a…`; `supersession.md` `3f1ee0fb…`; `review-brief.md`; `verification.md`; `OUTPUTS.SHA256SUMS`
  `1cb58943…` (6/6); `INPUTS.SHA256SUMS` (11/11).
- Rev 1 review: `reviews/d6-clarifications-20261123/domain-review.md`, `d6a57e0c…`.
- Adopted packet: `continuation-rev3-20261123/contracts.md` (D-6/R-2, `33d5fb3c…`),
  `wire-fixtures.json` (`e03a2747…`), manifest `2000bb00…ab467` (21/21).
- Evidence: `evidence/continuation-h1-qa-fixes-20261123/RESULTS.md` §4;
  `evidence/continuation-h1-20261123/RESULTS.md` §4.1; `reviews/h1-qa-20261123/qa-report.md` §4–§5.
- Functional guide §13 PROC-01, NET-03, NET-04, ISS-04; requirements R05–R10, R23–R25.
- Source, read for order only: `ConsoleHistoryRoute.java` (`6da3ca7a…`), `ConsoleHistoryRouteTest.java`
  (`4e4e3a26…`), `ConsoleHistoryCursorCodec.java` (`388f5f79…`).
