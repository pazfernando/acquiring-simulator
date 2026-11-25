# D-6 clarifications — cursor mandate identity, pagination combination, malformed input, `epoch`

Status: **PROPOSED CLARIFICATION TEXT — NOT NORMATIVE.** Independent acquiring-domain review and
human approval are required before this text replaces or annotates D-6 in the adopted packet. No
code, IaC, fixture or adopted packet file is edited here. Nothing below is execution evidence.

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

**Current implementation behaviour** (read from source for this analysis, not executed):
`ConsoleHistoryCursorCodec` writes and requires `mandateFingerprint`
(`SHA-256(callerId NUL acquirerRef NUL merchantRef NUL sorted-route-names-comma)`), never writes
`epoch`, and writes `terminalRef` as an explicit JSON null when the mandate is not terminal-scoped.
`ConsoleHistoryRoute.queryOf` returns a continuation as soon as one non-empty `cursor` is present, so a
supplied `after` is never read; malformed pagination input raises a local `InvalidRequest` that the
route maps to `400 INVALID_CURSOR`.

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

**C-1.2** The representation MUST differ whenever any registry-supplied field that changes what the
read may disclose differs. Those fields are the caller identity, the acquirer, merchant and, when
present, terminal scope, and the permitted routes. A field outside the digest MUST be bound by another
cursor member, and the bound members together MUST cover all of them. Adding an
authority-bearing registry field is a change to this clause, not a code-local decision.

**C-1.3** A continuation MUST recompute the representation in force from the currently resolved mandate
and compare it with the cursor. A mismatch is a changed mandate generation: `410 RESYNC_REQUIRED` with
action `RESYNC_REQUIRED`. A mismatch of the D-6 context triple remains `403 CONTEXT_DENIED` and is
checked first; the two checks are not interchangeable.

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

**C-2.4** An absent `snapshot` is read as the initial read with `after` defaulting to `0`. The initial
read is authorized afresh and discloses only within the authorized transaction; its response reports
`snapshotHead` and coverage, so a client that expected a continuation can see that it received a fresh
snapshot. Requiring `snapshot=new` literally would change reviewed green behaviour for no authority
gain.

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
response for every unusable pagination input, namely:

- **(a)** `snapshot` present with any value other than `new`;
- **(b)** `after` empty, containing a non-digit, negative, longer than 12 characters, or greater than
  the frozen snapshot head;
- **(c)** any query parameter name other than `snapshot`, `after` or `cursor`;
- **(d)** any of those names repeated;
- **(e)** `cursor` present and empty;
- **(f)** `cursor` combined with `snapshot` or with `after`.

**C-3.2** In this route `INVALID_CURSOR` means "the pagination handle is not one this contract
defines": an unknown, tampered, unparseable or contradictory handle. No second code is introduced.
The reviewed error set for the history route is closed at six bodies, and (a)–(f) share one client
remedy — discard the handle and restart at `?snapshot=new` — so a distinct code would add contract
surface and fixture obligations without adding an action the client can take. The code's name is kept
rather than renamed, because renaming it would rewrite every reviewed error fixture for a vocabulary
gain only.

**C-3.3** Failure precedence is fixed and observable, in this order: `401 UNAUTHENTICATED` → `400
INVALID_CURSOR` (request form, C-3.1) → `503 AUTHORIZATION_UNAVAILABLE` / `403 CONTEXT_DENIED`
(mandate resolution) → `400 INVALID_CURSOR` (cursor signature or payload format) → `410
RESYNC_REQUIRED` (cursor expired by its signed `expiresAt`) → `403 CONTEXT_DENIED` (validly signed,
unexpired cursor bound to another issuer, azp, subject, audience, environment, context or transaction)
→ `410 RESYNC_REQUIRED` (changed mandate representation) → `503`/`404 NOT_FOUND` (store head
unavailable or authorized but unknown target) → `410 RESYNC_REQUIRED` (changed `R` or `F`, head
regression) → `400 INVALID_CURSOR` (bound `lastSequence` beyond the bound snapshot head) → `200` page.
A later check MUST NOT be promoted to answer a request that an earlier one already refuses. Two
consequences are intended: a malformed request is never answered `403`; and a cursor that fails
signature or format is `400` even when its context is also wrong, because the handle cannot be
interpreted at all, while a well-formed unexpired cursor bound to another context is `403` before any
mandate-generation comparison.

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
| `historyErrors["400"]` | one body | unchanged body; the set of requests that produce it now includes (a)–(f) of C-3.1 |
| `historyGapPage` (12 keys) | closed schema | unchanged; no cursor or error member is added to the page |
| QA driver `H3d-cursor-payload-no-extra-fields` | exact key-set equality | must be restated (see below); the current literal set also omits `issuer` and `terminalRef`, which QA-DEF-1/3 require, so the check contradicts the reviewed contract independently of this delta |

**Payload member set (interpretation of existing D-6 text, not a new obligation).** For this slice the
required members are `version`, `keyId`, `issuer`, `subject`, `authorizedParty`, `audience`,
`environment`, `mandateFingerprint`, `generation`, `acquirerRef`, `merchantRef`, `terminalRef`,
`transactionId`, `snapshotHead`, `expiryRevision`, `firstRetainedSequence`, `lastSequence`, `issuedAt`,
`expiresAt`. `epoch` and the two named registry members are absent per C-1 and C-4. Additional
server-internal members MAY appear inside the signed payload — the implementation's pass counters
(`passedEvents`, `passedBytes`) are of this kind — and no client or fixture may require or forbid them
by name: D-6 constrains disclosure, not membership. The fixture assertion must therefore be
"every required member present; no token, key or credential material present", not set equality.

## 7. Implementation and test impact (recorded, not executed)

| Clause | Current behaviour (source reading) | Change required |
| --- | --- | --- |
| C-1 | writes and compares `mandateFingerprint`; comparison runs after the context checks and returns 410 | none for the demo registry. A versioned registry needs the two named members, their parse/compare, and — for C-1.5 — a distinct `410` path for a signature-valid payload whose schema is stale, which the codec currently reports as `INVALID` (`400`) |
| C-2 | `queryOf` returns a continuation on the first non-empty `cursor`; `after` and `snapshot` are then never read, so `after` is silently ignored | `cursor` with `after`, and `cursor` with `snapshot`, must fail the form check before mandate resolution; adds one shape rule and two tests |
| C-3 | local `InvalidRequest` already maps to `400 INVALID_CURSOR` and covers (a)–(e) and `after > H` | (f) only; no code or body change otherwise |
| C-4 | `epoch` is not written; `terminalRef` is written as JSON null | none |

Test and oracle impact: `ConsoleHistoryRouteTest.malformedPaginationInputUsesTheRecordedProvisionalMapping`
gains the contradictory cases; a focused case is needed for "valid cursor plus `after` is refused
before any read"; `afterBeyondTheDurableHeadIsInvalidRatherThanSilentlyClamped` is unchanged and is now
contract text rather than provisional; the QA driver's cursor key-set check is restated as in §6. No
fixture byte in the adopted packet changes, so the packet manifest `2000bb00…ab467` stays valid.

## 8. Non-claims

Draft clarification only. No independent review, human approval, normative adoption or QA PASS is
claimed or implied. No code, test, fixture, IaC, Terraform or adopted-packet file was edited; nothing
was compiled, executed or deployed. No Git, provider, credential or cloud action occurred. The
behaviour statements in §1 and §7 are source readings, not execution evidence. The server-side
read-pass store, cursor-key sourcing and real issuer verification remain open exactly as disclosed in
the H1 evidence and are out of scope here.
