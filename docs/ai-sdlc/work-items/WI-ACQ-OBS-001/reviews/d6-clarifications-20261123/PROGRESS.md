# D-6 clarifications — bounded independent acquiring-domain review — progress

Status: **COMPLETE** — bounded review written and sealed. Overall verdict **CONDITIONAL**: C-1, C-2
and C-4 are PASS for adoption as within-direction clarifications; C-3 is CONDITIONAL on two
non-semantic corrections to its failure order and stale-schema classification.

## Assignment

Bounded independent acquiring-domain review of exactly four proposed clarifications to the reviewed
D-6/R-2 cursor contract:

- **C-1** — mandate identity in the cursor: content fingerprint (`mandateFingerprint`) is the
  normative representation for this unversioned registry; named `registryMandateId` /
  `registryVersion` become mandatory only for a future versioned registry; a signature-valid
  pre-migration cursor is `410 RESYNC_REQUIRED`, not `400 INVALID_CURSOR`.
- **C-2** — a request carrying both `cursor` and `after` is refused as `400 INVALID_CURSOR`; no
  precedence rule and no silently ignored parameter.
- **C-3** — every unusable pagination form maps to `400` with the exact
  `{"version":1,"error":"INVALID_CURSOR","action":null}` body, reusing the closed six-code set, plus
  a fixed observable failure order.
- **C-4** — an HTTP-only read's cursor omits the `epoch` member; an explicit `"epoch": null` is not
  emitted; a live-bound read carries and compares its epoch.

Decide per clause: PASS / CONDITIONAL / BLOCKED for adoption; whether any clause changes payment or
disclosure semantics; whether any weakens the no-resend, single-source or truthful-freshness
guarantees or creates a new way to obtain data across mandates, terminals or generations; whether
the 410-versus-400 migration choice is sound; whether rejecting `cursor` with `after` risks breaking a
documented client; and whether independent adoption needs a further human decision or is a
within-direction clarification.

## Authority and limits

- Requester: `ai-sdlc-coordinator` (webchat), subagent depth 1. No onward delegation.
- Writes confined to `reviews/d6-clarifications-20261123/`.
- No Git or provider command, no application/IaC/input edit, no cloud/credential effect, no external
  send, no code or test execution. Read-only inspection only.
- Role applied: Acquiring Domain Reviewer, with `payment-behavior-review` (Authorization branch:
  original-reference correlation, repeated instruction, no unintended second purchase). This review
  is advisory; it is not human approval, normative incorporation, construction clearance, QA
  execution or deployment authority.

## Model actually used

- **This review ran on `nan/deepseek-v4-flash`** — the runtime identity for this subagent session.
- The configured default is `openai/gpt-6.1-sol`; it did **not** run this review. I selected no model
  override.
- The D-6 draft's `verification.md` records the same runtime declaration and a `sessions_list`
  observation of `gpt-6.1-sol`; the two observations disagree there too. I did not independently
  re-run `sessions_list`. If the primary was unavailable, the fallback that produced this review is
  `nan/deepseek-v4-flash`; that is the model identity for this record. No provider qualification is
  inferred.

## Pins verified before judgment — all MATCH

### D-6 clarification packet (`design/contract-deltas/d6-clarifications-20261123/`)

| File | SHA-256 | Result |
| --- | --- | --- |
| `d6-clarifications.md` | `9bb8ca333e7a8b1f4f2e7d9072dfbc00c57b0648b1e62d2aa6161dbfd8bc0237` | MATCH (task pin and packet manifest) |
| `OUTPUTS.SHA256SUMS` | `22a1f2d89a846a8e432c97ca9db45bb3c556c389c318854dc1af7d7ece517d85` | MATCH (task pin) |
| `INPUTS.SHA256SUMS` | `8b62b94522b70840b2eb311c2f622e19cc5c8670997c545b985dd59b414202a4` | MATCH (self-listed) |
| `PROGRESS.md` | `af6a1215afc6afe6fa7771c65b76e4ae942e5a2e85bd5af41ef54b8847e5af3a` | MATCH (self-listed) |
| `review-brief.md` | `b07b3a9cb221e52d05813ad665ae3a48974af64d291c33288544a0438c0a069e` | MATCH (self-listed) |
| `verification.md` | `eaddba6443637839f2eacf73e3933d728e94e5e6d929aa4532e3b84b5bfb5b5d` | MATCH (self-listed) |
| `OUTPUTS.SHA256SUMS` in place | — | **5/5 OK** (the manifest does not list itself) |

### Adopted reviewed packet (`design/contract-deltas/continuation-rev3-20261123/`)

| File | SHA-256 | Result |
| --- | --- | --- |
| `OUTPUTS.SHA256SUMS` | `2000bb004e68e6d2974bef3067baeae6774f1922a21dca4ea18fecf6944ab467` | MATCH (task pin); **21/21 OK in place** |
| `contracts.md` | `33d5fb3c8ef6b4aa6baae3389c74032efb1faa6d809a3f09268910edf9ca1f10` | MATCH |
| `wire-fixtures.json` | `e03a2747a34aabf19a0d7d4da038d1fe0124faf246b6b088d34b4f70d5a82936` | MATCH |
| `identity-map.md` | `b099fe8e396f6b65fdba61de1ed4e8968b54def835b97ae28dd0da515673ebae` | MATCH |
| `disposition-ledger.md` | `6a4dbdeb72f41a670fc88658588ff59c66b2fc93dadd59e50a0acf06fe84b6cd` | MATCH |
| `INPUTS.SHA256SUMS` | — | **30/32 OK**; `WORK_ITEM.md` and `owner-decisions.md` FAILED |

The two `INPUTS.SHA256SUMS` failures are mutable product records edited after the rev3 packet was
sealed. The D-6 draft discloses the same drift (its `verification.md` says 30/32) and pins the
current `WORK_ITEM.md` hash `67609d985a3bf195f87492274deb87932fdd1e2064ef2402fcb2f1563b7c75cb`
in its own inputs. `owner-decisions.md` currently hashes
`d5208f2b6bd28047ce7394656b0d5de2fff717d68b004a6d204feeaa2443338e`. **No reviewed packet output
file changed**: the rev3 manifest is 21/21 and its bytes are byte-identical to the manifest the prior
review accepted. This is input-record drift, not a packet change.

### DR3-01 correction packet (`design/contract-deltas/continuation-rev3-dr301-20261123/`)

| File | SHA-256 | Result |
| --- | --- | --- |
| `OUTPUTS.SHA256SUMS` | `cf1a27e92375c8b4703e718692dde23b16596363154322fea69779b384da507d` | MATCH; **6/6 OK in place** |
| `INPUTS.SHA256SUMS` | `463c9cc047ec7aafe523ecfdd8721a223046b6ba4d5c0c6fd16f3e918cdd2b52` | MATCH |
| `old-prior-fixtures.json` | `1d3f1406b05d95f87e18f37b33eea3bb855ad5c80488f92891dcc132b5f3d5f5` | MATCH |
| `supersession.md` | `72ba032f5e1264abec1077d238e6c5c943f489a65bc7cd437519052769fd8b17` | MATCH |
| `re-review-brief.md` | `2417315227ec97cfd77cda5b7c0532de4fe37b1d0c9194b045284e24b1d669de` | MATCH |

### Prior independent reviews relied on

| File | SHA-256 | Result |
| --- | --- | --- |
| `reviews/continuation-rev3-domain-20261123/domain-review.md` | `154b6346544bfb760bb6bf1c5cefd63dcc6f09ab064151a7165972007b67c2e4` | MATCH; manifest **4/4 OK** |
| `reviews/continuation-rev3-dr301-domain-20261123/domain-review.md` | `df5ad1a5979888387ea4b6b25813ee8cd5c2c9643664d505d29894199b70c5d0` | MATCH; manifest **2/2 OK** |

## Inputs read

Role files (`AGENTS.md`, `SOUL.md`, `IDENTITY.md`, `USER.md`) in
`/home/pazfernando/.openclaw/workspace/ai-sdlc-acquiring-domain/`; shared glossary;
`docs/ai-sdlc/PROJECT.md`; the work item `WORK_ITEM.md` current-status sections; the prior rev3 and
DR3-01 domain reviews; the adopted rev3 `contracts.md` (D-5, D-6/R-2, payment/retention
non-regression), `wire-fixtures.json` (`cursorUnsignedHarnessBinding`, `historyErrors`),
`identity-map.md`, `disposition-ledger.md`, `acceptance-scenarios.md`, `browser-flow.md`,
`construction-handoff.md`; the DR3-01 packet; the D-6 packet (`d6-clarifications.md`,
`review-brief.md`, `verification.md`, `PROGRESS.md`, both manifests); the H1 evidence
`evidence/continuation-h1-qa-fixes-20261123/RESULTS.md` §1–§6 and
`evidence/continuation-h1-20261123/RESULTS.md` §4.1; the QA report
`reviews/h1-qa-20261123/qa-report.md` §4–§5 and its driver; the functional guide §13
PROC-01/NET-03/NET-04/ISS-04 and observation lines; requirements R05–R10, R23–R25; and the current
source for behaviour only (`ConsoleHistoryCursorCodec.java`, `ConsoleHistoryRoute.java`,
`AuthorizedContext.java`, the presenter relay handler, `ConsoleHistoryRouteTest.java`,
`console-ui/js/session-history.js`, `src/main/resources/presenter/app.js`).

## Checks completed before judgement

1. **Pins.** Every manifest above recomputed independently; the D-6 and DR3-01 packets are
   byte-identical to their published manifests; the rev3 packet is 21/21 and byte-identical. The
   two rev3 input failures are mutable-doc drift and are disclosed, not hidden.
2. **Four points are the four points.** The D-6 draft's §1 sources match
   `evidence/continuation-h1-qa-fixes-20261123/RESULTS.md` §4 items 1–4 and
   `evidence/continuation-h1-20261123/RESULTS.md` §4.1. No fifth point or out-of-scope change was
   smuggled in; the draft explicitly leaves error bodies, TTL, H/R/F, response schema, margins,
   retention and payment semantics unchanged.
3. **Source readings.** Verified the D-6 draft's statements: `mandateFingerprint` is
   `SHA-256(callerId NUL acquirerRef NUL merchantRef NUL route1,route2,...)`; `epoch` is never
   written; `terminalRef` is written as a JSON null when not terminal-scoped; `queryOf` returns a
   continuation on the first non-empty `cursor` and ignores `after`/`snapshot`; malformed pagination
   maps to `400 INVALID_CURSOR`; a stale payload version is currently `400`, not `410`.
4. **Registry fact.** `AuthorizedContext` has exactly five components (`acquirerRef`, `merchantRef`,
   `callerId`, optional `terminalRef`, `permittedRoutes`) and no mandate identifier or version
   counter. The fingerprint covers four; `terminalRef` is bound separately; the union covers all
   five.
5. **C-1 binding analysis.** No new cross-mandate, cross-terminal or cross-generation path found.
   Subject/issuer/azp/audience/environment/transaction/generation/H/R/F remain bound. The
   authority-not-row-identity choice is defensible because every D-6 check is authority-based; the
   only assumption is that the five `AuthorizedContext` fields fully capture read authority, and
   C-1.2 makes adding an authority-bearing field a contract change.
6. **C-2 client analysis.** No current D-6 client breaks. `ConsoleHistoryRoute` is instantiated only
   in tests; the deployed `ConsoleRuntime` recognizes the path but returns an empty `503` without
   constructing the route. `console-ui/js/session-history.js` does not implement the server read. The legacy
   presenter client (`src/main/resources/presenter/app.js`) targets `PresenterRelayServer`, whose
   history handler always returns `nextCursor:null`, so it never sends a cursor; it also sends
   `generation`, which the console route already rejects as an unknown parameter (pre-existing and
   independent of C-2). Repointing it at the console route would require dropping `generation` and
   sending `?cursor=X` alone on continuations.
7. **C-3 order analysis.** Found one internal inconsistency: C-3.1(b) puts `after > frozen snapshot
   head` among the request-form errors, and C-3.3 puts the request-form `400` before mandate
   resolution, but the head cannot be known before the mandate/store read. The current test
   `afterBeyondTheDurableHeadIsInvalidRatherThanSilentlyClamped` reads the head, confirming the
   check is post-head. Also found that C-3.3's "payload format" `400` is not distinguished from
   C-1.5's signature-valid stale-schema `410`.
8. **C-1 post-read ordering.** Found that `ConsoleHistoryRoute.reauthenticateAfterRead` checks the
   fingerprint (`410`) before the context triple (`403`), while C-1.3 says the context mismatch is
   checked first. No data is disclosed either way, but the implementation and the clause differ;
   recorded as an implementation-impact correction.
9. **C-4 shape analysis.** Omission of `epoch` is the natural reading of D-6's "epoch when live";
   the explicit-null prohibition is an internal payload rule with no client effect. Recorded the
   parser's absent/null conflation for `terminalRef` as a low-severity implementation note and the
   live/HTTP-only cross-mode error as a future WS-slice clarification.
10. **Record integrity.** The D-6 draft's §8 says "No Git ... action occurred", while its own
    `verification.md` discloses read-only `git rev-parse` / `git status` inspections. Recorded as a
    non-claim wording correction, not a packet change.

## Outcome

- **C-1 PASS** (text) — content fingerprint is the only non-fabricating representation for an
  unversioned registry; no payment/disclosure change; two non-semantic corrections: C-1.2 wording
  and the post-read context-first ordering recorded in §7.
- **C-2 PASS** (text) — no current D-6 client breaks; the legacy presenter client never sends a
  cursor (its relay returns `nextCursor:null`) and would already fail the console route's
  unknown-parameter check on `generation`; one editorial C-2.4 wording fix.
- **C-3 CONDITIONAL** — `after > frozen head` cannot be checked pre-mandate, and C-1.5's
  signature-valid stale-schema `410` is not distinguished from a malformed-payload `400`. Exact
  corrections are in `domain-review.md`; both are non-semantic.
- **C-4 PASS** (text) — HTTP-only epoch omission is the correct reading of "epoch when live"; the
  live/HTTP-only cross-mode error remains a future WS-slice clarification; parser absent/null
  `terminalRef` conflation is a low-severity implementation note.
- No clause weakens no-resend, single-source or truthful freshness, and none creates a new
  cross-mandate/terminal/generation data path. The 410 migration choice is sound. Rejecting
  `cursor` with `after` does not break a documented current client. No new human financial-rule
  decision is required; ordinary human acceptance of the corrected text and a bounded re-check of
  the changed C-3 bytes remain.

## Sealing

`domain-review.md` and this progress record are sealed by `SHA256SUMS` in this directory. No Git,
provider, code, IaC, cloud or external effect occurred; writes are confined to this review
directory.
