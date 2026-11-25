# Independent acquiring-domain review — D-6 clarifications (C-1 to C-4)

2026-11-23, America/Bogota. **CONDITIONAL** — C-1, C-2 and C-4 are **PASS** for adoption as
within-direction clarifications; **C-3 is CONDITIONAL** on two non-semantic corrections to its
failure order and stale-schema classification. No clause changes payment or disclosure semantics;
none weakens the no-resend, single-source or truthful-freshness guarantees; none creates a new way
to obtain data across mandates, terminals or generations. The 410-versus-400 migration choice is
sound. Rejecting `cursor` with `after` does not break a documented current client. No new human
financial-rule decision is required; ordinary human acceptance of the corrected text remains, plus a
bounded re-check of the changed C-3 bytes.

This is an advisory documentary review. It is **not** human approval, normative incorporation,
construction clearance, QA execution or deployment authority. "Adopted packet" below means the
pinned `continuation-rev3-20261123` reviewed candidate at manifest `2000bb00…ab467`, which
self-describes as DRAFT/NOT NORMATIVE; it does not mean a human-approved normative contract.

## Execution identity and model actually used

- Agent role: Acquiring Domain Reviewer (`ai-sdlc-acquiring-domain`), subagent depth 1.
- **Model that actually ran: `nan/deepseek-v4-flash`**, the runtime identity of this session. The
  configured default `openai/gpt-6.1-sol` did **not** run this review; I selected no override. The
  D-6 draft's `verification.md` records the same runtime declaration and a `sessions_list`
  observation of `gpt-6.1-sol`; I did not independently re-run `sessions_list`. If the primary was
  unavailable, the fallback that produced this review is `nan/deepseek-v4-flash`. No provider
  qualification is inferred.
- Checkout `/home/pazfernando/projects/acquiring-simulator`, host `fernando-OC`. No Git or provider
  command was run by this review; branch/HEAD are **not** freshly attested. All conclusions attach to
  working-file bytes.
- Applied skill `payment-behavior-review` (Authorization branch: original-reference correlation,
  repeated instruction, no unintended second purchase). Read role files, the shared glossary,
  `docs/ai-sdlc/PROJECT.md`, the work item's current-status sections, the prior rev3 and DR3-01
  domain reviews, the adopted D-6/R-2 contracts and fixtures, the D-6 draft and its manifests, the
  H1 evidence and QA report, the functional guide §13 and requirements R05–R10/R23–R25, and the
  current source for behaviour only.
- Commands: `ls`, `find`, `grep`, `sed`, `wc`, `sha256sum`, `sha256sum -c`, `awk`, one read-only
  Python `json` parse of `wire-fixtures.json`, and owned report writes. No tests, build, HTTP,
  browser, issuer, IAM, Terraform, DynamoDB, cloud, MCP or provider command. No onward delegation.
  Writes are confined to `reviews/d6-clarifications-20261123/`; `PROGRESS.md` was written first.

## Pins recomputed before judgement — all MATCH

| Input | SHA-256 | Result |
| --- | --- | --- |
| `d6-clarifications.md` | `9bb8ca333e7a8b1f4f2e7d9072dfbc00c57b0648b1e62d2aa6161dbfd8bc0237` | MATCH (task pin) |
| D-6 `OUTPUTS.SHA256SUMS` | `22a1f2d89a846a8e432c97ca9db45bb3c556c389c318854dc1af7d7ece517d85` | MATCH (task pin); 5/5 OK in place |
| D-6 `INPUTS.SHA256SUMS` | `8b62b94522b70840b2eb311c2f622e19cc5c8670997c545b985dd59b414202a4` | MATCH; 15/15 input pins OK |
| `continuation-rev3-20261123/OUTPUTS.SHA256SUMS` | `2000bb004e68e6d2974bef3067baeae6774f1922a21dca4ea18fecf6944ab467` | MATCH; **21/21 OK** |
| `continuation-rev3-20261123/INPUTS.SHA256SUMS` | — | **30/32 OK**; two mutable-doc drifts, below |
| `continuation-rev3-dr301-20261123/OUTPUTS.SHA256SUMS` | `cf1a27e92375c8b4703e718692dde23b16596363154322fea69779b384da507d` | MATCH; **6/6 OK** |
| Prior rev3 domain review | `154b6346…7b67c2e4` | MATCH; manifest 4/4 OK |
| Prior DR3-01 domain review | `df5ad1a5…b70c5d0` | MATCH; manifest 2/2 OK |

The two rev3 input failures are `WORK_ITEM.md` and `owner-decisions.md`, mutable product records
edited after the packet was sealed. The D-6 draft discloses the same 30/32 result and pins the
current `WORK_ITEM.md` hash in its own inputs. **No reviewed packet output file changed**: the rev3
packet is byte-identical to the manifest the prior review accepted, and the DR3-01 packet is 6/6.
This is input-record drift, not a packet change.

## What is being decided, and why it is in scope

The four points are exactly the four left open by the H1 evidence: (1) mandate fingerprint versus
named `registryMandateId`/`registryVersion`; (2) `epoch` in an HTTP-only read; (3) `?cursor=X&after=N`;
(4) malformed-pagination mapping. The D-6 draft does not restate or change the rest of D-6/R-2
(error bodies, six-code set, 900-second TTL, per-page re-authentication before and after the store
read, H/R/F freezing, response schema, margin, retention, payment non-regression). I verified that
scope claim against `evidence/continuation-h1-qa-fixes-20261123/RESULTS.md` §4 and
`evidence/continuation-h1-20261123/RESULTS.md` §4.1: the four clauses map one-to-one to the four
open observations, and no fifth point is smuggled in.

## Clause-by-clause verdicts

### C-1 — mandate identity in the cursor: **PASS** (text), with two non-semantic corrections

**What it does.** For a registry with no mandate identifier and no version counter, the normative
representation of D-6's "registry mandate identity/version" is `mandateFingerprint`: lowercase
hex SHA-256 of `callerId` NUL `acquirerRef` NUL `merchantRef` NUL, followed by each permitted route
name in ascending order, each followed by `,`. Named `registryMandateId`/`registryVersion` become
mandatory only for a versioned registry. A signature-valid pre-migration cursor is `410
RESYNC_REQUIRED`; a signature-invalid one remains `400 INVALID_CURSOR`.

**Does it stay inside D-6/R-2?** Yes. D-6 asks for the property — the binding must be stable while
the authority is unchanged and must change when the authority changes — and does not prescribe a
particular encoding. The `AuthorizedContext` record has exactly five components (`acquirerRef`,
`merchantRef`, `callerId`, optional `terminalRef`, `permittedRoutes`) and no mandate id or version
counter; the registry fact is verified. The digest covers four, `terminalRef` is bound separately,
and the union covers all five. Requiring the literal `M-1`/`1` would make D-6 unsatisfiable in this
slice without inventing registry state, which D-6 excludes when it says read-pass identity is
"never a client-asserted authority". The "authority, not row identity" choice is therefore the
minimal non-fabricating reading, not a new business rule.

**Does it change payment or disclosure behaviour?** No. It reinterprets a binding term, not a
payment or disclosure rule. The fingerprint is a hash in an already signed, client-held payload that
already carries issuer, subject, azp, audience, environment, context, transaction, generation,
H/R/F and timestamps; it adds no token, key or new identifier class. It is recomputed from the
currently resolved mandate before and after the store read, so current authorization is still
checked. It does not add a data source or a projection.

**Guarantees.** No-resend: no payment path; the 410 is a GET resync. Single-source: no new source.
Truthful freshness: no weakening; the existing H/R/F and current-mandate checks remain, and the
fingerprint is recomputed. Cross-mandate/terminal/generation: no new path. Subject, issuer, azp,
audience, environment, transaction, generation, H/R/F and `terminalRef` remain separately bound.
Two mandates with identical authority are treated as the same generation; that is acceptable
because every D-6 check is authority-based and no additional data is reachable. The only assumption
is that the five `AuthorizedContext` fields fully capture read authority; C-1.2's "adding an
authority-bearing registry field is a change to this clause" is the correct guard.

**Corrections needed (non-semantic).**

- **C-1.2 wording.** The clause says "The representation MUST differ whenever any registry-supplied
  field that changes what the read may disclose differs", but the digest by C-1.1(b) excludes
  `terminalRef` (and environment/subject are bound by other members). The next sentence already says
  a field outside the digest must be bound by another cursor member. To avoid an implementer adding
  `terminalRef` to the digest and breaking the exact C-1.1(b) formula, change the first sentence to
  "The cursor's bound members, taken together, MUST differ whenever any registry-supplied field that
  changes what the read may disclose differs", and make "the bound members together MUST cover all
  of those fields" explicit. No behaviour changes.
- **C-1.3 / §7 post-read impact.** C-1.3 says a context-triple mismatch is `403` and "checked
  first". The pre-read continuation honours that. The post-read
  `ConsoleHistoryRoute.reauthenticateAfterRead` checks `fingerprint(mandateAfter)` (`410`) before
  the `acquirerRef`/`merchantRef`/`terminalRef` comparison (`403`). No data is disclosed either way,
  but the implementation and the clause differ. Either reorder the post-read check to context
  (`403`) before fingerprint (`410`), or state an explicit post-read exception. Update §7's "none
  for the demo registry" to "none on the pre-read path; the post-read path needs the context-first
  reorder."

**Residual.** The fingerprint formula assumes route names contain no `,` or NUL and are ASCII; the
current fixed route names satisfy that. Java's natural string sort equals code-point order for
ASCII; a future non-BMP route name would need the formula to specify the collation more tightly.
These are implementation assumptions, not current defects.

### C-2 — `cursor` combined with `after`: **PASS** (text), with one editorial clarification

**What it does.** The history read accepts exactly two forms: initial (`no cursor`; optional
`snapshot=new`; optional `after` 0–12 ASCII digits) or continuation (exactly one non-empty
`cursor`, no `snapshot`, no `after`). A request fitting neither is malformed and refused under C-3.
`cursor` with `after`, and `cursor` with `snapshot`, are refused; no precedence and no silently
ignored parameter.

**Does it stay inside D-6/R-2?** Yes. D-6 fixes the two forms implicitly; C-2 makes them explicit.
The combination is contradictory input: the cursor carries `lastSequence`, `after` is a position for
a fresh snapshot, and a server that picked one would silently disagree with the client's page
arithmetic. Rejecting it is the fail-closed reading of D-6's closed error set. It adds a `400` for
a combination that was previously silently accepted, but that is a tightening of malformed input,
not a payment or disclosure change. It reduces disclosure (no page) rather than expanding it.

**Guarantees.** No-resend: no payment path; a history `400` must not be mapped to a payment retry,
and the existing "No GET/login/WS/retry/expiry/repair sends any payment" clause forbids that.
Single-source: the client restarts at `?snapshot=new`, still the same event source. Truthful
freshness: strengthened, because a client can no longer keep a stale snapshot while its own page
arithmetic assumes a different position. Cross-mandate/terminal/generation: no new path; the
refusal occurs before mandate resolution and before any store read.

**Client impact.** No current D-6 client breaks. `ConsoleHistoryRoute` is instantiated only in
tests; the deployed `ConsoleRuntime` recognizes the path but returns an empty `503` without
constructing the route. `console-ui/js/session-history.js` explicitly does not implement
the server history read. The only paginated client, `src/main/resources/presenter/app.js`, targets
the legacy `PresenterRelayServer`; its `catchUp` sends `snapshot=new&after=N` and would add `cursor`
only if `payload.nextCursor` were non-null, but the relay's `handleEvents` always returns
`nextCursor:null`, so it never sends a cursor. It also sends `generation`, which the console route
already rejects as an unknown parameter — a pre-existing incompatibility independent of C-2. If the
presenter is ever repointed at the console route, it must drop `generation` and send `?cursor=X`
alone on continuations. That is a client-migration note, not a contract defect.

**Correction (editorial).** C-2.4 says an absent `snapshot` is the initial read "with `after`
defaulting to 0". Read literally, that could mean a supplied `after` is ignored. Change to "with
`after` defaulting to 0 when `after` is absent". No behaviour changes.

### C-3 — malformed pagination mapping and failure order: **CONDITIONAL**

**What it does.** Maps every unusable pagination input to `400` with the exact
`{"version":1,"error":"INVALID_CURSOR","action":null}` body: bad `snapshot`, bad `after` (empty,
non-digit, negative, >12 chars, or greater than the frozen head), unknown parameter names, repeated
names, empty `cursor`, and `cursor` combined with `snapshot`/`after`. It adds no new code and keeps
the six-body set. It also fixes an observable failure order.

**Does it stay inside D-6/R-2?** Mostly, yes. It codifies the provisional mapping already disclosed
in the H1 evidence and reuses the existing code, as D-6's closed error set requires. It adds no
payment or disclosure behaviour. The `after > H` choice (`400`, not `410`) is defensible: the
client's claimed position is *ahead* of the durable head, not a retired cursor generation, and an
initial read has no cursor to resync; `410` would imply a generation change that did not occur. The
client remedy is the same either way (restart at `?snapshot=new`), and C-3.2 documents that remedy.

**Two defects in the fixed order as written.**

1. **`after > frozen snapshot head` cannot be checked pre-mandate.** C-3.1(b) includes "greater
   than the frozen snapshot head" in the request-form errors, and C-3.3 puts "400 INVALID_CURSOR
   (request form, C-3.1)" before mandate resolution. But the frozen head belongs to the authorized
   transaction's event partition; it is not known until after the mandate is resolved and the store
   head is read. The current test
   `afterBeyondTheDurableHeadIsInvalidRatherThanSilentlyClamped` confirms this: it reads the head,
   unlike the syntactic malformed-pagination test, which asserts zero head calls. As written, the
   order is not implementable for that one sub-case. **Correction:** keep only the syntactic `after`
   errors (empty, non-digit, negative, >12 chars) in the pre-mandate request-form `400`; move
   `after > frozen head` to a separate `400` after the head is established (after the
   `503`/`404` head step). The body and status do not change.
2. **C-1.5's stale-schema `410` is not distinguished from a malformed-payload `400`.** C-3.3 lists
   "400 INVALID_CURSOR (cursor signature or payload format)" and never names a signature-valid
   retired-schema `410`. C-1.5 says a signature-valid payload lacking the representation now in
   force, or carrying a non-current payload version, is "a retired-schema cursor, not a tampered
   one, and MUST be refused as `410 RESYNC_REQUIRED`". For a stale-schema cursor that is also bound
   to another context, C-3.3's "403 context before 410 changed mandate representation" would
   conflict with C-1.5's `410`. **Correction:** change C-3.3's "payload format" `400` to "malformed
   payload" and add a distinct step "`410 RESYNC_REQUIRED` (signature verifies, but the payload
   schema/version is retired, per C-1.5)" after signature verification and before the context
   comparison (or explicitly state the schema-before-context precedence). This makes the two
   clauses mutually consistent and keeps the test oracle satisfiable.

**Does the order disclose anything a caller does not already know?** No. `401` precedes the form
check; the pre-mandate `400` reveals only request-form validity; a wrong-context caller gets `403`
before the store head, so it cannot probe another tenant's transaction existence; `503` reveals
registry unavailability, not tenant data. The post-read `410`/`403` ordering issue in C-1 is
fail-closed and discloses no page.

**Verdict.** C-3 is **CONDITIONAL** on the two corrections above. They are non-semantic: the same
requests are refused, the same body/status family is used, and no payment or disclosure rule
changes. After the corrections, C-3 would be PASS.

### C-4 — `epoch` in an HTTP-only read: **PASS** (text), with future and implementation notes

**What it does.** An HTTP-only read has no socket and no live epoch, so its cursor omits the `epoch`
member and must not emit `"epoch": null`. A live-bound read must carry its connection epoch and a
continuation must compare it; inequality is `410 RESYNC_REQUIRED`. General rule: a member whose D-6
condition does not hold is omitted; a nullable context attribute that is genuinely not applicable is
present as JSON null (`terminalRef` for a non-terminal mandate). "Absent" and "present as null"
state different facts.

**Does it stay inside D-6/R-2?** Yes. D-6 says "epoch when live"; an HTTP-only generation is not
live, so omission is the natural reading. The explicit-null prohibition is stricter than D-6
literally requires, but it is an internal payload-shape rule: the cursor is opaque, HMAC-signed,
schema-versioned and read only by the minting service. It does not change what data is disclosed or
what a client can do. The `terminalRef` null rule matches the current payload and C-4.3's
absent-vs-null distinction is coherent.

**Guarantees.** No-resend/single-source/truthful freshness: no effect. The epoch is a technical
generation, not a second source; a live mismatch is a `410` resync, not a payment action. No new
cross-generation path: a live cursor's epoch is compared, and an HTTP-only cursor has no epoch to
transplant.

**Future note (not a correction for this slice).** The draft does not state what a live-bound
cursor (with `epoch`) gets on an HTTP-only read, or an HTTP-only cursor (no `epoch`) on a live-bound
read. D-6's "read-pass/subscription generation" suggests both are retired generations and should be
`410 RESYNC_REQUIRED`, but the text does not say so. This is a future WS-slice clarification; the
H1 slice is HTTP-only and is not blocked by it.

**Implementation note (low severity).** `ConsoleHistoryCursorCodec.parse` reads `terminalRef` with
`text(...)`, which treats an absent member and an explicit null identically. The emitted payload is
correct (always present, null when not terminal-scoped), so C-4.3's emission rule is satisfied. If
the team wants the absent/null distinction enforced on input, add a presence check that allows a
NullNode. Today a signature-valid older cursor missing `terminalRef` is accepted as "no terminal";
a terminal-scoped mandate then gets `403`, so no data leaks. This is a schema-strictness note, not a
blocker.

## Guarantee analysis — explicit answers

| Guarantee | Effect of C-1..C-4 | Basis |
| --- | --- | --- |
| **No unintended resend / no second authorization** | **No weakening.** | All four are history-GET rules; no POST, dispatch or financial invocation. `contracts.md` payment non-regression: "No GET/login/WS/retry/expiry/repair sends any payment." C-1.5's `410` is a GET resync; C-2/C-3 are GET refusals. A client must not map a history `400`/`410` to a payment retry; the existing rule forbids it. |
| **Single event source / no duplicate projection** | **No weakening.** | No new projection, GSI or source. C-2.4's fresh snapshot reads the same store; C-4's epoch is a technical generation, not a second source; C-1's mandate registry is an authorization input already required by D-6. |
| **Truthful freshness / no false completeness** | **No weakening; C-2 and C-3 strengthen it.** | C-2 refuses a contradictory position instead of silently continuing a stale snapshot; C-3 refuses `after > H` instead of silently clamping; C-1.3 recomputes the current mandate before and after the read (with the post-read ordering correction); C-4 preserves the live-epoch condition. No clause claims completeness; `completeThroughSnapshot`, gaps and expired ranges remain. |
| **No cross-mandate / cross-terminal / cross-generation disclosure** | **No new path.** | The fingerprint covers caller, acquirer, merchant and routes; `terminalRef` is separately bound; subject, issuer, azp, audience, environment, transaction, generation, H/R/F and key id remain bound. Two mandates with identical authority are treated as the same generation, which reaches no additional data. C-1.2 makes adding an authority-bearing field a contract change. |
| **Issuer/processor separation, Authorization vs reversal/status inquiry, 3DS** | **Untouched.** | This is an observation-history cursor; it is not an authorization, reversal, status inquiry or 3DS surface. No issuer decision, payment outcome or network message is introduced. |

## Is the 410-versus-400 migration choice sound?

**Yes, subject to the C-3 harmonization.** A cursor whose HMAC verifies is server-minted; a payload
that lacks the representation now in force, or carries a non-current payload version, is a retired
schema/generation, not "unknown/tampered". D-6's taxonomy is "unknown/tampered = 400" and "retired
generation = 410"; 410 is the correct category. It also gives the client the actionable
`RESYNC_REQUIRED` remedy (discard and restart at `?snapshot=new`), which is the same remedy C-3.2
documents for `INVALID_CURSOR`, so no new client capability is required. The 900-second TTL bounds
the migration window: no cursor minted under the old representation can outlive a cutover by more
than that window, assuming the cutover retires the old representation at all issuance points (a
mixed-version fleet could extend it; that is an operational condition, not a contract defect).

The distinction does not let a client learn another mandate's, terminal's or generation's data. The
client already holds the cursor and can see its own payload version; the only oracle is a
schema-version difference, which is low-value. If the team wants to avoid even that, it could check
environment/context before schema, but a stale payload may not carry the current context members, so
the recommended schema-before-context order is pragmatic and fail-closed. The important correction
is to state it explicitly (C-3 defect 2), not to change the `410` choice.

## Does rejecting `cursor` with `after` risk breaking a documented client?

**No current documented D-6 client breaks.** The evidence is in the progress record: the console
route is not constructed by the deployed runtime; `console-ui` does not implement the server read; the only
paginated client is the legacy presenter, whose relay handler always returns `nextCursor:null`, so
it never sends a cursor; the packet's acceptance scenarios (G5-12, R2-01) do not specify a
`cursor`+`after` request; and the legacy presenter already sends `generation`, which the console
route rejects independently of C-2.

The residual risk is a *future* client that copies the legacy presenter's "always send
`snapshot=new`" pattern. C-2.2 is explicit that a continuation must omit both `snapshot` and
`after`; a client that sends `?snapshot=new&cursor=X` will get `400`. That is a client-contract
change the coordinator should carry into the history-UI work, not a contract defect. If the team
wants the legacy pattern to remain legal, it would have to add a precedence rule (accept the cursor
and ignore the redundant `snapshot`), which C-2 deliberately rejects because it makes the page
position ambiguous. The rejection is the safer choice.

## Exact corrections needed before adoption

**Gating (C-3; non-semantic):**

1. In **C-3.1(b)**, keep only the syntactic `after` errors (empty, non-digit, negative, >12 chars)
   in the request-form `400`; move "greater than the frozen snapshot head" to a separate `400`
   checked after the mandate resolution and the store head read.
2. In **C-3.3**, place that `after > frozen head` `400` after the `503`/`404` head step; rename the
   earlier "payload format" `400` to "malformed payload"; add a distinct "`410 RESYNC_REQUIRED`
   (signature verifies, payload schema/version retired, per C-1.5)" step after signature
   verification and before the context `403` (or state the schema-before-context precedence
   explicitly).

**Editorial (no behaviour change, recommended before the text is final):**

3. **C-1.2:** change "The representation MUST differ…" to "The cursor's bound members, taken
   together, MUST differ…"; make "all of those fields" explicit.
4. **C-1.3 / §7:** reorder the post-read `reauthenticateAfterRead` check to context `403` before
   fingerprint `410` (or add an explicit post-read exception); update §7's "none for the demo
   registry" accordingly.
5. **C-2.4:** "with `after` defaulting to 0 when `after` is absent".
6. **§6 fixture table:** add the documentary name mappings the harness needs — `azp` →
   `authorizedParty`; `H`/`R`/`F` → `snapshotHead`/`expiryRevision`/`firstRetainedSequence`; and
   state whether the fixture's missing `audience` member is a documentary omission or an additional
   server-internal member. The implementation's §6 required set includes `audience`; the fixture
   does not. This is a clarity fix, not a semantic change.
7. **§8 non-claims:** reconcile "No Git … action occurred" with `verification.md`'s disclosed
   read-only `git rev-parse` / `git status` inspections; say "no Git mutation or provider command;
   read-only inspections were disclosed." Record integrity only.

**Future / adjacent notes (not gating this slice):**

8. **C-4 cross-mode:** state what a live-bound cursor (with `epoch`) gets on an HTTP-only read and
   an HTTP-only cursor (no `epoch`) gets on a live-bound read; recommend `410 RESYNC_REQUIRED` as a
   retired generation. This belongs to the future WS slice.
9. **C-1.5 scope:** "retired schema" currently covers the mandate representation and the payload
   version, not every member added by a code change. If the team wants all required-member
   additions to yield `410`, say "a member required by the current payload schema" and bump the
   payload version when adding required members. Otherwise keep the clause scoped as written.
10. **D-8 adjacent consistency (not a finding on C-1..C-4):** `identity-map.md` lists
    `registryMandateId`/`registryVersion` in the D-8/G-4 mandate record. The same registry fact (no
    id, no version counter) means those fields need a documentary/empty-value treatment consistent
    with C-1. This review does not reopen D-8; the coordinator should ensure the two clauses do not
    diverge.
11. **Implementation note:** the cursor parser treats absent `terminalRef` and explicit null
    identically; see C-4. No data leak today.

## Human decision or within-direction clarification?

**Within-direction clarification; no further human financial-rule decision.** The owner direction
already requires cursor/page binding to the current demo subject, deployment environment,
authorized business context, transaction and subscription generation, with `403`/`410` semantics
and no payment side effect (the R19 replacement in the inherited candidate, R25, D-6/R-2, G-1). C-1 chooses the only non-fabricating
representation for this registry; C-2 and C-3 tighten malformed input; C-4 states the HTTP-only
epoch shape. None adds a payment outcome, a disclosure rule, an operation B/C, 3DS, clearing or
settlement rule, or a new authority.

C-1 should be **explicitly acknowledged** by the owner/coordinator because it retires the fixture's
literal `M-1`/`1` for this registry and answers the QA open question ("must the cursor carry named
`registryMandateId`/`registryVersion`?") with "no for an unversioned registry; yes for a future
versioned one." That is an interpretation within the recorded direction, not a new business choice.
If the owner instead wants the literal named fields, that would be a material deviation requiring
explicit direction — and it would require inventing registry state, which D-6 excludes.

Normative incorporation still needs the human approval step recorded in the packet and the role
contract ("Independent domain review and human approval are required before this text replaces or
annotates D-6"). My verdict is advisory and does not approve or adopt. Because C-3 needs a text
correction, the changed C-3 bytes need a bounded re-check by this role before they are treated as
reviewed; the business analysis above need not be redone.

## Limits and non-claims

Documentary/structural review only. I did not execute the route, the tests, the QA driver, a
browser, an issuer, IAM, Terraform, DynamoDB Local, a deployed edge or any cloud surface. The source
readings in the D-6 draft's §1/§7 were independently checked against the working files, but they
remain source readings, not execution evidence. No real JWKS/signature/issuer verification,
server-side read-pass store, cursor-key custody, CloudFront/WAF, Keycloak or browser capture is
claimed. The H1 slice's read-pass generation is a signed cursor label; server-side read-pass
storage/revocation remains open as disclosed in the H1 evidence, and no clause here is claimed to
close it. N-02 and every other open Security/QA/retention finding keep their recorded dispositions;
this review does not close them. No Git or provider command, no application/IaC/input edit, no
cloud/credential effect, no external send, no onward delegation. No human approval, normative
incorporation, construction clearance, QA PASS or deployment authority is implied.

## References

- `design/contract-deltas/continuation-rev3-20261123/contracts.md` (D-6/R-2; payment and retention
  non-regression), SHA-256 `33d5fb3c…e9ca1c0`.
- `design/contract-deltas/continuation-rev3-20261123/wire-fixtures.json`
  (`cursorUnsignedHarnessBinding`, `historyErrors`), SHA-256 `e03a2747…d5a82936`.
- `design/contract-deltas/continuation-rev3-20261123/identity-map.md`,
  `disposition-ledger.md`, `acceptance-scenarios.md`, `browser-flow.md`, `construction-handoff.md`.
- `design/contract-deltas/d6-clarifications-20261123/d6-clarifications.md`, SHA-256
  `9bb8ca33…dbfd8bc0237`; `review-brief.md`; `verification.md`; `OUTPUTS.SHA256SUMS` `22a1f2d8…`.
- `evidence/continuation-h1-qa-fixes-20261123/RESULTS.md` §4;
  `evidence/continuation-h1-20261123/RESULTS.md` §4.1; `reviews/h1-qa-20261123/qa-report.md` §4–§5.
- Functional guide §13 PROC-01, NET-03, NET-04, ISS-04; observation lines 401/425;
  requirements R05–R10, R23–R25.
- Prior reviews `reviews/continuation-rev3-domain-20261123/domain-review.md` (154b6346…) and
  `reviews/continuation-rev3-dr301-domain-20261123/domain-review.md` (df5ad1a5…).
- Source for behaviour only: `ConsoleHistoryCursorCodec.java`, `ConsoleHistoryRoute.java`,
  `AuthorizedContext.java`, `ConsoleHistoryRouteTest.java`, `PresenterRelayServer.java`,
  `console-ui/js/session-history.js`, `src/main/resources/presenter/app.js`.
