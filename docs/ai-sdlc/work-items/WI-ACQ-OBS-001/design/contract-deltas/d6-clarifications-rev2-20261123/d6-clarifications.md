# D-6 clarifications (rev 2) — cursor mandate identity, pagination combination, malformed input, `epoch`

Status: **PROPOSED CLARIFICATION TEXT — NOT NORMATIVE.** Independent acquiring-domain review and
human approval are required before this text replaces or annotates D-6 in the adopted packet. No
code, IaC, fixture or adopted packet file is edited here. Nothing below is execution evidence.

**Revision 2.** This revision supersedes the first revision in
`design/contract-deltas/d6-clarifications-20261123/` (SHA-256 `9bb8ca33…dbfd8bc0237`) and carries
only the corrections the independent review of that revision required. The review's verdict was
CONDITIONAL: C-1, C-2 and C-4 PASS; C-3 CONDITIONAL on two non-semantic gating corrections in its
fixed failure order. Neither the gating nor the editorial corrections change which requests are
refused, the status/body used, or any payment, disclosure or authority rule. The corrected clauses
are C-1.2, C-1.3, C-2.4, C-3.1(b), C-3.3, the new C-3.4, the §6 fixture mappings, the §1
source-behaviour paragraph and the §7 rows (source-reading corrections, not clause changes, because the
working bytes moved), and the §8 non-claims. Every other sentence is the reviewed revision's text
unchanged. What changed and why is recorded in `supersession.md`; the bounded re-review is scoped in
`review-brief.md`.

This document settles exactly the four points left open by
`evidence/continuation-h1-qa-fixes-20261123/RESULTS.md` §4 (1–4) and
`evidence/continuation-h1-20261123/RESULTS.md` §4.1. Every other D-6/R-2 obligation — error bodies,
six-code error set, 900-second cursor TTL, per-page re-authentication before and after the store read,
`H`/`R`/`F` freezing, response schema, margin, retention and payment non-regression — stands unchanged
and is not restated.

## 1. Basis

**D-6 wording under interpretation** (`continuation-rev3-20261123/contracts.md`, D-6/R-2): "Each cursor
binds version, issuer, azp, subject, trusted environment, registry mandate identity/version,
context(acquirerRef, merchantRef, terminalRef when target-specific), transactionId, read-pass/
subscription generation, epoch when live, H, R, F, lastSequence, issuedAt, expiresAt and keyId. Signed
opaque server cursor reveals no tokens/keys. Exact wire handle in fixtures is a documentary symbol
resolved by the test harness, not a usable cryptographic cursor."

**Fixture under interpretation** (`continuation-rev3-20261123/wire-fixtures.json`): the packet
self-describes as "PROPOSED documentary wire values, not signed tokens/cursors or executed HTTP".
`cursorUnsignedHarnessBinding` names `registryMandateId: "M-1"`, `registryVersion: 1` and
`epoch: null` alongside `H: 3`, `R: 1`, `F: 1`, `lastSequence: 1`, `generation: "READ-1"` and the
context triple. `historyErrors["400"]` is `{"version":1,"error":"INVALID_CURSOR","action":null}`.

**Registry fact.** The console mandate resolved for this slice
(`AuthorizedContext`: `acquirerRef`, `merchantRef`, `callerId`, optional `terminalRef`,
`permittedRoutes`) exposes no mandate identifier and no version counter. The values `M-1` and `1`
exist only as documentary symbols; no registry row, sequence or counter emits them.

**Current implementation behaviour** (read from source for this analysis, not executed; bytes as read
for this revision): `ConsoleHistoryCursorCodec` writes and requires `mandateFingerprint`
(`SHA-256(callerId NUL acquirerRef NUL merchantRef NUL sorted-route-names-comma)`), never writes
`epoch`, and writes `terminalRef` as an explicit JSON null when the mandate is not terminal-scoped.
`ConsoleHistoryRoute.queryOf` refuses a `cursor` combined with `after` or with `snapshot` through a
local `InvalidRequest` before mandate resolution and the store read, and validates `after`
syntactically; `after` beyond the store head is refused only after the head is read. Every
`InvalidRequest` maps to `400 INVALID_CURSOR`. These bytes differ from the ones revision 1 described —
see the concurrent-source observation in §7 — so the readings above are taken again rather than
inherited.

## 2. C-1 — Mandate identity in the cursor (clarifies "registry mandate identity/version")

**C-1.1** The cursor MUST bind the resolved mandate of the read-pass generation through exactly one of
two representations:

- **(a) versioned registry.** The payload carries `registryMandateId` and `registryVersion`, each
  echoed verbatim from the resolved mandate.
- **(b) unversioned registry.** Where the registry exposes no stable mandate identifier, or no version
  counter, the normative representation of "registry mandate identity/version" is `mandateFingerprint`:
  the lowercase hexadecimal SHA-256 digest of the UTF-8 byte string `callerId` + U+0000 +
  `acquirerRef` + U+0000 + `merchantRef` + U+0000, followed by each permitted route name in ascending
  code-point order, each followed by `,`.

**C-1.2** The cursor's bound members, taken together, MUST differ whenever any registry-supplied field
that changes what the read may disclose differs. Those fields are the caller identity, the acquirer,
merchant and, when present, terminal scope, and the permitted routes. A field outside the digest MUST
be bound by another cursor member, and the bound members together MUST cover all of those fields:
`callerId`, `acquirerRef`, `merchantRef` and the permitted routes through the C-1.1(b) digest, and
terminal scope through `terminalRef`. Adding an authority-bearing registry field is a change to this
clause, not a code-local decision.

**C-1.3** A continuation MUST recompute the representation in force from the currently resolved mandate
and compare it with the cursor. A mismatch is a changed mandate generation: `410 RESYNC_REQUIRED` with
action `RESYNC_REQUIRED`. A mismatch of the D-6 context triple remains `403 CONTEXT_DENIED` and is
checked first; the two checks are not interchangeable. This order holds at both re-authentication
points: before the store read and in the post-read re-authentication, the context-triple `403` is
evaluated before the mandate-representation `410`, so a cursor whose triple and representation both
differ is answered `403`, not `410`, on either path.

**C-1.4** A server MUST NOT require both representations. In a versioned registry it MAY emit both as
defence in depth; the named fields then govern and a present digest MUST also match.

**C-1.5** **Migration.** When a registry begins to expose a stable mandate identifier and a version
counter, representation (a) becomes mandatory and (b) is retired; the digest MAY be kept under C-1.4.
A cursor whose signature verifies but whose payload lacks a member required by the representation now
in force — or carries a payload version other than the current one — is a **retired-schema** cursor,
not a tampered one, and MUST be refused as `410 RESYNC_REQUIRED`. A cursor whose signature does not
verify is `400 INVALID_CURSOR`. Since a cursor lives at most 900 seconds, no cursor minted under (b)
can outlive a cutover by more than that window.

**Why (b) is chosen for this registry, against the alternative readings.** D-6 asks for the property,
not for a particular encoding: the binding must be stable while the authority is unchanged and
different as soon as it changes. A registry version counter delivers that property only if the
registry increments it on every authority-relevant change — an invariant this contract cannot
observe — whereas a digest over exactly those fields delivers it by construction. Requiring the named
fields literally would make D-6 unsatisfiable in this slice without inventing registry functionality,
and a server-invented `M-1`/`1` would be fabricated state of the same kind D-6 excludes when it says
read-pass identity is "never a client-asserted authority". Content identity is also the correct notion
of identity here: the read-pass generation is bound to the authority the mandate confers, not to a
database row, so two mandates with identical authority are indistinguishable to every check D-6
defines and treating them as different generations would force `410` re-syncs with no security gain.
The fixture's `M-1`/`1` are therefore read as the documentary form of the same binding for a versioned
registry, not as required members of an HTTP-only cursor.

## 3. C-2 — Cursor combined with `after`

**C-2.1** The history read accepts exactly two request forms:

- **(i) initial read:** no `cursor`; optional `snapshot`, whose only accepted value is `new`; optional
  `after` of 0–12 ASCII digits forming a non-negative integer not greater than the frozen snapshot
  head.
- **(ii) continuation:** exactly one non-empty `cursor`, and no `snapshot`, and no `after`.

**C-2.2** A request fitting neither form is a malformed pagination request and is refused under C-3.
In particular, `cursor` with `after`, and `cursor` with `snapshot`, are refused. There is no precedence
rule, because the combination never takes effect: neither parameter is ignored, and a server MUST NOT
accept one while discarding the other.

**C-2.3** The refusal occurs after authentication (so `401` precedes it) and before mandate resolution
and before any store read.

**C-2.4** An absent `snapshot` is read as the initial read with `after` defaulting to `0` when `after`
is absent. The initial read is authorized afresh and discloses only within the authorized transaction;
its response reports `snapshotHead` and coverage, so a client that expected a continuation can see that
it received a fresh snapshot. Requiring `snapshot=new` literally would change reviewed green
behaviour for no authority gain.

**Why rejection rather than a defined precedence.** A cursor already carries `lastSequence`, and
`after` is a position for a fresh snapshot; one request asserting two positions is contradictory
input, not a preference. Precedence would be silent by construction: the server would continue from
the cursor's `lastSequence` while the client computed its page arithmetic from `after`, and the two
would disagree about which range the page covers. D-6 fixes exactly two request forms and a closed
error set; a third, ambiguous form has no oracle and cannot be tested. Rejection also fails closed on
a client defect: a client that appends `after` to a continuation it has lost would otherwise silently
keep a stale snapshot instead of being forced to restart.

## 4. C-3 — Malformed pagination mapping

**C-3.1** Status `400` with the exact body `{"version":1,"error":"INVALID_CURSOR","action":null}` is the
response for every unusable pagination input that the request form alone establishes, namely:

- **(a)** `snapshot` present with any value other than `new`;
- **(b)** `after` empty, containing a non-digit, negative, or longer than 12 characters;
- **(c)** any query parameter name other than `snapshot`, `after` or `cursor`;
- **(d)** any of those names repeated;
- **(e)** `cursor` present and empty;
- **(f)** `cursor` combined with `snapshot` or with `after`.

The remaining `after` condition — a value greater than the frozen snapshot head — is not part of this
set: it cannot be judged from the request form alone and is fixed at its own step in C-3.4.

**C-3.2** In this route `INVALID_CURSOR` means "the pagination handle is not one this contract
defines": an unknown, tampered, unparseable or contradictory handle. No second code is introduced.
The reviewed error set for the history route is closed at six bodies. The head-dependent refusal of
C-3.4 reuses this same code and body: an `after` beyond the frozen head is a position this contract
does not define, and the client remedy is identical. (a)–(f) share one client remedy — discard the
handle and restart at `?snapshot=new` — so a distinct code would add contract surface and fixture
obligations without adding an action the client can take. The code's name is kept rather than renamed,
because renaming it would rewrite every reviewed error fixture for a vocabulary gain only.

**C-3.3** Failure precedence is fixed and observable, in this order: `401 UNAUTHENTICATED` → `400
INVALID_CURSOR` (request form, C-3.1) → `503 AUTHORIZATION_UNAVAILABLE` / `403 CONTEXT_DENIED`
(mandate resolution) → `400 INVALID_CURSOR` (cursor signature does not verify, or the signed payload
is malformed) → `410 RESYNC_REQUIRED` (signature verifies, but the payload schema or version is
retired, per C-1.5) → `410 RESYNC_REQUIRED` (cursor expired by its signed `expiresAt`) → `403
CONTEXT_DENIED` (validly signed, unexpired, current-schema cursor bound to another issuer, azp,
subject, audience, environment, context or transaction) → `410 RESYNC_REQUIRED` (changed mandate
representation) → `503`/`404 NOT_FOUND` (store head unavailable or authorized but unknown target) →
`400 INVALID_CURSOR` (`after` greater than the frozen snapshot head, C-3.4) → `410 RESYNC_REQUIRED`
(changed `R` or `F`, head regression) → `400 INVALID_CURSOR` (bound `lastSequence` beyond the bound
snapshot head) → `200` page. A later check MUST NOT be promoted to answer a request that an earlier one
already refuses. The schema-before-context precedence is stated rather than left implicit: a
signature-valid cursor whose payload schema or version is retired is answered `410` before the context
comparison, because such a payload may not carry the current context members and cannot be interpreted
under the schema now in force. Two consequences are intended: a malformed request is never answered `403`; and a cursor that
fails signature, or whose signed payload is malformed, is `400` even when its context is also wrong,
because the handle cannot be interpreted at all, while a well-formed, current-schema, unexpired cursor
bound to another context is `403` before any mandate-generation comparison.

**C-3.4** One unusable input is not decidable from the request form: `after` greater than the frozen
snapshot head. The frozen head belongs to the authorized transaction's event partition and is not
known until the mandate is resolved and the store head read, so this condition is not checked with
C-3.1 and MUST NOT be refused before mandate resolution or before the store head read. It is answered
`400` with the same body at the separate step fixed in C-3.3, after the `503`/`404` head step and
before the `R`/`F` and bound-`lastSequence` comparisons. The status, body and client remedy are those
of C-3.1; only the position in the order differs.

## 5. C-4 — `epoch` in an HTTP-only read

**C-4.1** `epoch` is conditionally bound ("epoch when live"). An HTTP-only initial or history read has
no socket and therefore no live epoch. For that generation the cursor payload MUST omit the `epoch`
member; an explicit `"epoch": null` MUST NOT be emitted.

**C-4.2** Where a read is bound to a live subscription, `epoch` MUST be present and carry that
connection's epoch; a continuation MUST compare it with the current live epoch, and inequality is a
retired subscription generation: `410 RESYNC_REQUIRED` with action `RESYNC_REQUIRED`.

**C-4.3** General rule, used by C-1 and C-4: a member whose D-6 condition does not hold in this
generation is omitted. A nullable attribute of the bound context that is genuinely not applicable is
present with JSON `null` — `terminalRef` for a mandate without terminal scope. "Absent" and "present
as null" therefore state different facts and are not interchangeable.

**Why absence is correct, and no explicit null is required.** A null would assert "bound, and empty", a
third state distinct from both "absent" and "bound to a value", and every future reader would have to
interpret all three. Wire-shape parity buys nothing here because the cursor is opaque, HMAC-signed,
schema-versioned and read only by the service that minted it: its member set is not an interoperability
surface. The only constraint D-6 places on the payload's contents is disclosure — "reveals no
tokens/keys" — not membership. The adopted fixture states in its own header that it is documentary and
that its handles are "not a usable cryptographic cursor", so `epoch: null` is a harness placeholder for
"no live epoch", not a wire requirement.

## 6. Fixture implications

The adopted packet is **not** edited. These are the readings a harness and a future fixture revision
must apply.

| Fixture element | Current reading | Reading under this delta |
| --- | --- | --- |
| `cursorUnsignedHarnessBinding.registryMandateId = "M-1"`, `registryVersion = 1` | reads as two required cursor members | documentary form of representation (a); a cursor minted by the demo registry satisfies C-1 through `mandateFingerprint`, and its absence of these two members is conformant |
| `cursorUnsignedHarnessBinding.mandateFingerprint` (absent) | reads as a missing member | must be accepted as the representation-(b) member; a harness may compute the expected digest from the demo mandate instead of comparing a literal |
| `cursorUnsignedHarnessBinding.epoch = null` | reads as a required null member | placeholder for "no live epoch" in this HTTP-only generation; a minted payload must **omit** the member and must not be failed for the absence |
| `cursorUnsignedHarnessBinding.context.terminalRef = "TERMINAL-A"` | a value for a terminal-scoped mandate | unchanged; where the mandate has no terminal scope the member is present with JSON `null` (C-4.3) |
| `cursorUnsignedHarnessBinding.azp = "console-demo"` | documentary name for the authorized party | maps to the payload member `authorizedParty`; a harness compares it to the token's authorized party and must not look for a member literally named `azp` |
| `cursorUnsignedHarnessBinding.H` / `R` / `F` | documentary short names for the frozen snapshot triple | map to the payload members `snapshotHead` (`H`), `expiryRevision` (`R`) and `firstRetainedSequence` (`F`); no other reading |
| `cursorUnsignedHarnessBinding.audience` (absent) | reads as a missing required member | a **documentary omission**, not an additional server-internal member: `audience` is a required member of the payload set below and is bound by C-3.3's context check, so a minted payload MUST carry it and a harness MUST require it |
| `historyErrors["400"]` | one body | unchanged body; the set of requests that produce it includes (a)–(f) of C-3.1 and, at the later head-dependent step, `after` beyond the frozen head (C-3.4) |
| `historyGapPage` (12 keys) | closed schema | unchanged; no cursor or error member is added to the page |
| QA driver `H3d-cursor-payload-no-extra-fields` | exact key-set equality | must be restated (see below); the current literal set also omits `issuer` and `terminalRef`, which QA-DEF-1/3 require, so the check contradicts the reviewed contract independently of this delta |

**Payload member set (interpretation of existing D-6 text, not a new obligation).** For this slice the
required members are `version`, `keyId`, `issuer`, `subject`, `authorizedParty`, `audience`,
`environment`, `mandateFingerprint`, `generation`, `acquirerRef`, `merchantRef`, `terminalRef`,
`transactionId`, `snapshotHead`, `expiryRevision`, `firstRetainedSequence`, `lastSequence`, `issuedAt`,
`expiresAt`. The fixture's `azp`, `H`, `R` and `F` are the documentary names for `authorizedParty`,
`snapshotHead`, `expiryRevision` and `firstRetainedSequence`; its absent `audience` is a documentary
omission of a required member, not a member only the server knows. `epoch` and the two named registry
members are absent per C-1 and C-4. Additional server-internal members MAY appear inside the signed
payload — the implementation's pass counters
(`passedEvents`, `passedBytes`) are of this kind — and no client or fixture may require or forbid them
by name: D-6 constrains disclosure, not membership. The fixture assertion must therefore be
"every required member present; no token, key or credential material present", not set equality.

## 7. Implementation and test impact (recorded, not executed)

| Clause | Current behaviour (source reading) | Change required |
| --- | --- | --- |
| C-1 | writes and compares `mandateFingerprint`; the comparison follows the context checks, and in the bytes read here the post-read `reauthenticateAfterRead` also checks the context triple (`403`) before the fingerprint (`410`) | none on the pre-read path for the demo registry; the post-read path needs the context-first order C-1.3 states, which the current bytes already show (concurrent-source note below). A versioned registry needs the two named members, their parse/compare, and — for C-1.5 — a distinct `410` path for a signature-valid payload whose schema is stale, which the codec currently reports as `INVALID` (`400`) |
| C-2 | in the bytes read here, `queryOf` refuses `cursor` with `after` and `cursor` with `snapshot` through `InvalidRequest` before mandate resolution and the store read | present in the working tree already; no further shape rule recorded. Revision 1 recorded the earlier bytes, where a continuation was returned at the first non-empty `cursor` and `after` was silently ignored; see the concurrent-source note |
| C-3 | local `InvalidRequest` already maps to `400 INVALID_CURSOR` and covers (a)–(e); the `after > H` refusal already occurs after the store head read, matching C-3.4 | (f) only in the request-form check; the head-dependent refusal needs no move; no code or body change otherwise. The schema-retired `410` path remains the code change recorded in the C-1 row |
| C-4 | `epoch` is not written; `terminalRef` is written as JSON null | none |

**Concurrent-source observation (disclosed, not resolved).** `ConsoleHistoryRoute.java` and
`ConsoleHistoryRouteTest.java` changed while this revision was being prepared. Their hashes were
`51ad2b71ee41b5e9…` and `483d4773e466ff22…` when revision 1 was drafted (that packet's input pins) and
are `6da3ca7a2d90fb105…` (mtime 2026-11-23 23:30:53) and `4e4e3a26fc86cfff…` (mtime 2026-11-23
23:31:08) in the bytes read here. In those current bytes the post-read re-authentication checks the
context triple (`403`) before `fingerprint(mandateAfter)` (`410`) with a comment citing C-1.3, and
`queryOf` refuses `cursor` with `after` or `snapshot` with a comment citing C-2; the test file carries
cases for both. This task did not make those edits and makes no claim about who did or whether they are
committed. The §7 C-1 and C-2 rows therefore record the corrections' target state as present in the
working tree, subject to the implementer's own change record and to the tests actually being run, which
this task did not do. `ConsoleHistoryCursorCodec.java` still matches its revision-1 pin (`388f5f79…`),
so the C-1.5 schema-`410` path remains unimplemented as recorded.

Test and oracle impact: `ConsoleHistoryRouteTest.malformedPaginationInputUsesTheRecordedProvisionalMapping`
must cover the contradictory cases; the current test bytes already carry focused cases
`cursorCombinedWithAfterIsRefusedBeforeMandateResolutionAndTheStore` and its `snapshot` sibling, which
this task read but did not run. `afterBeyondTheDurableHeadIsInvalidRatherThanSilentlyClamped` is now
contract text rather than provisional, and reads the store head — the position C-3.4 fixes; the
syntactic form test keeps its zero-head assertion. The QA driver's cursor key-set check is restated as
in §6. No fixture byte in the adopted packet changes, so the packet manifest `2000bb00…ab467` stays
valid.

## 8. Non-claims

Draft clarification only. No independent review of this revision, human approval, normative adoption or
QA PASS is claimed or implied. The independent acquiring-domain review of revision 1 is recorded in
`reviews/d6-clarifications-20261123/domain-review.md`; it passed C-1, C-2 and C-4 and made C-3
conditional, and its corrections are carried here without being re-reviewed yet. No code, test,
fixture, IaC, Terraform or adopted-packet file was edited; nothing was compiled, executed or deployed.
No Git mutation, provider, credential or cloud action occurred; the only Git use was read-only
working-tree inspection — `git status --porcelain` in this pass, and the `git rev-parse` / `git status
--porcelain` recorded in revision 1's `verification.md` — with no staging, commit, push, PR, merge or
branch change. The behaviour statements in §1 and §7 are source
readings, not execution evidence. The server-side read-pass store, cursor-key sourcing and real issuer
verification remain open exactly as disclosed in the H1 evidence and are out of scope here.
