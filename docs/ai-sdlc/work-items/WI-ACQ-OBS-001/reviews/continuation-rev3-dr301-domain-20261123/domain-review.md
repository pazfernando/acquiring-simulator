# Independent acquiring-domain re-review — DR3-01 correction (bounded)

2026-11-23, America/Bogota.

**Verdict for this bounded correction: PASS.** DR3-01 is **CLOSED (documentary)**.
The two added cases force prior-reference precedence: a latest-head-first implementation
cannot satisfy the twelve-case oracle set. The ten inherited cases are byte-identical and
keep their meanings. No new finding is raised and no human decision is required for this
bounded correction.

This is an advisory documentary review. It is **not** human approval, **not** design or
construction authorization, **not** QA execution and **not** a claim that the branch was
run. The prior rev3 packet verdict (CONDITIONAL, integrated runtime not passed) and every
out-of-scope finding keep their recorded dispositions.

## Execution identity and model actually used

- Agent role: Acquiring Domain Reviewer (`ai-sdlc-acquiring-domain`), subagent depth 1.
- **Model that actually ran this review: `nan/deepseek-v4-flash`**, observed in this run's
  runtime identity. The configured default `openai/gpt-6.1-sol` was **not** the model that
  ran here; the correction packet records the primary as unavailable on provider quota and
  the fallback as the producer of the corrected bytes. I selected no override.
- Checkout `/home/pazfernando/projects/acquiring-simulator`; host `fernando-OC`.
  Historical branch/HEAD are **not** freshly attested: no Git command was used to read or
  attest them. All conclusions attach to working-file bytes. Disclosure: two local,
  read-only inspection commands (`git status --short`, `git diff --stat`) ran during the
  initial repository survey before this review boundary; they changed nothing, contacted
  no provider and are not used as evidence.
- Applied skill: `payment-behavior-review` (Authorization branch: original-reference
  correlation, repeated instruction, no unintended second purchase). Role files,
  `docs/ai-sdlc/PROJECT.md`, the work item, the commission/dispatch, the prior rev3
  domain review, packet contracts, acceptance scenarios, response/late-original vectors,
  the functional guide's PROC-01/NET-03 lines, the correction packet and its supersession
  note were read before judging.
- Commands: `sha256sum`, `sha256sum -c`, `sed`/`grep`/`tail`, local Python
  `json`/`hashlib` structural checks, plus the two disclosed read-only `git status` /
  `git diff --stat` inspections. No tests, HTTP, browser, issuer, IAM, Terraform, cloud,
  MCP or provider command occurred; no Git mutation or attestation occurred. Writes are
  confined to this review directory; PROGRESS.md was written first.

## Pins recomputed before judgment — all MATCH

| Input (repository- or packet-relative) | SHA-256 |
| --- | --- |
| `reviews/continuation-rev3-domain-20261123/domain-review.md` (my prior report) | `154b6346544bfb760bb6bf1c5cefd63dcc6f09ab064151a7165972007b67c2e4` |
| `design/contract-deltas/continuation-rev3-dr301-20261123/INPUTS.SHA256SUMS` | `463c9cc047ec7aafe523ecfdd8721a223046b6ba4d5c0c6fd16f3e918cdd2b52` |
| `…dr301-20261123/old-prior-fixtures.json` (corrected artifact) | `1d3f1406b05d95f87e18f37b33eea3bb855ad5c80488f92891dcc132b5f3d5f5` |
| `…dr301-20261123/supersession.md` | `72ba032f5e1264abec1077d238e6c5c943f489a65bc7cd437519052769fd8b17` |
| `…dr301-20261123/re-review-brief.md` | `2417315227ec97cfd77cda5b7c0532de4fe37b1d0c9194b045284e24b1d669de` |
| `…dr301-20261123/OUTPUTS.SHA256SUMS` (packet self-manifest) | `cf1a27e92375c8b4703e718692dde23b16596363154322fea69779b384da507d` — 6/6 OK in place |
| `design/contract-deltas/continuation-rev3-20261123/OUTPUTS.SHA256SUMS` (reviewed packet) | `2000bb004e68e6d2974bef3067baeae6774f1922a21dca4ea18fecf6944ab467` — **21/21 OK at review time** |

`INPUTS.SHA256SUMS` re-derives 15/15 of its own claimed input pins, including the
functional guide `90a798c7…5a5ae094`, `contracts.md` `33d5fb3c…e9ca1c0`,
`response-vectors.json` `38acdad4…b169ca4`, `late-original-fixtures.json`
`5b156fdf…0fc328` and `acceptance-scenarios.md` `1cc09f28…22076a` — all recorded values
matched. **The main reviewed
packet is byte-identical** to the manifest the prior review accepted. The corrected
predecessor `continuation-rev3-20261123/old-prior-fixtures.json` still hashes
`f07439b6d93d4002209db34b9bf9871c7ee1c381a8b5be2b5a0ac0cc324b345c`.

## Scope

Reviewed only `continuation-rev3-dr301-20261123/old-prior-fixtures.json` and
`supersession.md` against the reviewed packet's contracts, acceptance scenarios,
response/late-original vectors and the functional guide. I did not reopen G1–G4,
DR2-01/DR2-03, late-original mechanics, H01–H26, security or retention findings.

## DR3-01 — CLOSED (documentary)

The defect recorded in my prior report: `after24h-different-data` and
`after7d-different-data` submitted `10.01` while **both** the pinned original and the
newer head stayed `10.00`, so a latest-head-first implementation also returned the expected
`409 PAYLOAD_CONFLICT`. The corrected file adds
`after24h-different-data-head-match` and `after7d-different-data-head-match`.

### 1. The adversary actually bites

Per new case, recomputed:

- Requested canonical projection: the request's 12 canonical fields (excluding
  `priorAuthorizationId` and `correlationId`) are **exactly equal** to
  `newHead.canonicalEqualityData` (amount `10.01`).
- Pinned `original.canonicalEqualityData` differs from both the submission and the head in
  **exactly one** field: `amount` (`10.00` vs `10.01`). All other canonical fields
  (`acquirerRef`, `contractVersion`, `credentialFixtureRef`, `cryptogramPresent`,
  `currency`, `cvm`, `entryMode`, `merchantRef`, `operation`, `paymentRef`,
  `terminalRef`) are identical.
- So the newer head is the *tempting-but-wrong* record and the head is a match while the
  prior-referenced original is not.

Why a latest-head-first implementation must fail: with a matching head it would
coalesce/replay against the head (returning `200`/`REPLAY`, non-null `authorizationId` and
`outcome`), or claim/link/send a new attempt (`newClaims`/`attemptLinks`/`financialSends`
nonzero, `newHeadUnchanged:false`). Any of these breaks the `httpStatus 409`,
`error PAYLOAD_CONFLICT`, null-disclosure or zero-effect oracles.

More decisive, the twelve cases together are unsatisfiable by head-first alone. The eight
other invalid-prior cases also carry a head whose canonical data equals the submission, yet
expect `400 INVALID_REFERENCE`; the two new cases carry the same head-match condition but
expect `409 PAYLOAD_CONFLICT`. Distinguishing `INVALID_REFERENCE` from `PAYLOAD_CONFLICT`
requires resolving and comparing the **pinned prior-referenced original**, not the latest
head. Prior-reference precedence is therefore forced, not merely hinted.

The functional basis is consistent: guide §13 PROC-01 ("do not produce an unintended second
authorization; return known outcome or uncertainty") and NET-03/NET-04 (correlate to the
original; never create a second purchase), plus packet contracts "prior-reference-first
applies even after24h/newer head; compare pinned original data/instruction" and "late proof
selects exact original payment generation/instruction/hash/correlation/version, not latest
head or STAN alone".

### 2. Identity separation — PASS

Original `AUTH-001` / `TX-001` / `paymentGeneration 1`; newer head `AUTH-NEW-HEAD` /
`TX-NEW-HEAD` / `paymentGeneration 2`; submitted `priorAuthorizationId` is `AUTH-001`,
never the head. Original `instructionCorrelation CORR-001`; request `correlationId CORR-002`.
Case `id`s and `resetScope`s are distinct and unique.

### 3. Golden reuse — PASS

Both new `expected` blocks are **byte-equal** to the reviewed different-data cases:
`httpStatus 409`, `error PAYLOAD_CONFLICT`, the same 13-key `body` object and the same
**317-byte** `bodyUtf8`. The string is canonical under sorted-key compact JSON
(re-serialises to itself). `asOf` stays the injected historical `2026-11-18T12:00:05Z`.
`response-vectors.json` (the historical H01–H26 C3 envelopes) is byte-identical
(`38acdad4…`) and byte-equal to `vectors/response-vectors.json`; no historical C3 response
byte was modified, re-serialised or extended. The two different-data cases that own the 409
golden are byte-identical to the reviewed predecessor, so the historical golden itself is
untouched.

### 4. No disclosure and zero effect — PASS

Null in both cases: `authorizationId`, `transactionId`, `originalCorrelationId`, `outcome`,
`businessVersion`. `originalDisclosure:false`. `newClaims:0`, `financialSends:0`,
`automaticPaymentPosts:0`, `attemptLinks:0`, `newHeadUnchanged:true`. No original amount,
original ID or head result is disclosed.

### 5. Boundary pairing — PASS

`after24h` uses `now 2026-11-19T12:00:05Z` (original firstReceipt `2026-11-18T12:00:00Z`
plus 24h and 5s, outside `PAYMENT24h`); `after7d` uses `now 2026-11-25T12:00:05Z` (plus 7d
and 5s). Both keep the injected response clock `2026-11-18T12:00:05Z`, identical to the
reviewed cases at each boundary. Head firstReceipt `2026-11-19T12:00:00Z` is the newer
generation in both. Window semantics are unchanged from the reviewed packet.

### 6. Isolation — PASS

Each new case carries its own distinct `resetScope` and complete inline `request`,
`original`, `newHead`, `otherPriorRecords`, `now`, `expected` and `responseClockInjected`.
No shared setup and no dependency on another case's result.

## No other case silently altered — proven

- All ten inherited cases compare **equal by value** to the predecessor and in the same
  order; only the file `status` line differs outside the added block.
- Additive byte proof: replacing the new `status` line with the predecessor's and deleting
  the two added case blocks (from the appended `after24h-different-data-head-match`
  object through the new trailing comma) reproduces the predecessor **byte-for-byte**
  (`f07439b6…324b345c`, sha256 recomputed), delta 7931 bytes.
- Exactly 12 unique case ids; the two new ids were absent from the predecessor.

## Supersession precision — PASS

`supersession.md` names **exactly one** replaced file (the predecessor
`old-prior-fixtures.json`, `f07439b6…`, under manifest `2000bb00…`), **exactly two**
defective case ids (`after24h-different-data`, `after7d-different-data`, retained verbatim
as non-discriminating regression oracles), and **exactly two** added case ids
(`after24h-different-data-head-match`, `after7d-different-data-head-match`). It states
that no other file, case, golden byte, contract clause, disposition, finding, manifest
entry or approval record is superseded. That matches what the bytes show. Its
non-normative/self-closure disclaimers are accurate and do not themselves close anything.

## Finding-state changes

| Finding | Before | Now | Basis |
| --- | --- | --- | --- |
| **DR3-01** (MEDIUM) | OPEN (PARTIAL residual of DR2-02) | **CLOSED documentary** | Two head-match adversaries force prior-reference precedence; no new defect |
| **DR2-02** (MEDIUM) | PARTIAL | **CLOSED documentary** | Its only recorded residual was the missing adversarial precondition, now supplied; executed realization still belongs to QA |
| **G-5** (oracle executability) | PARTIAL | **PARTIAL, unchanged label** | Documentary residual is gone; the only remaining basis is "not executed" — no execution is claimed here |
| All other findings (G1–G4, DR2-01, DR2-03, NV/AD, SR2/C, Security N-02..N-07) | as recorded | **unchanged** | Explicitly out of scope; not re-derived |

No new finding: there is no DR3-01-N1. DR2-02's closure is documentary fixture coverage
only; the packet's executed realization remains NOT CLOSED for the whole correction set.

## Recorded reservation (not a finding, not a state change)

The inherited `after7d` cases still expect `409 PAYLOAD_CONFLICT` even though the original
payload's collection window has closed. This review does not reopen it and does not treat
it as DR3-01: the artifact does not alter that expectation, `late-original-fixtures.json`
owns expiry/late-admission (a *valid* late proof is admitted with its own commit time and
`expiredPayloadRestored:false`), and the contracts require original links/journal to survive
payload expiry. The two branches are distinct (invalid/different data vs valid late proof),
so no contradiction was found. It remains separately challengeable by QA/owner.

## Questions

None required. No financial-rule question is raised by this bounded correction.

## Limits

Documentary/structural verification only. JSON checks are invariant checks, not a declared
schema validation. No execution of the twelve cases occurred; QA must still run them on the
implementation branch. No Git/GitHub mutation or attestation, cloud, credential, deployment or external effect;
no approval or closure of any out-of-scope finding is implied.
