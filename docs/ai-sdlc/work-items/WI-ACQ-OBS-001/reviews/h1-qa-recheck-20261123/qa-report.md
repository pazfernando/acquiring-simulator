# Independent QA recheck — H1 slice after QA-driven fixes (WI-ACQ-OBS-001 continuation)

- Reviewer: ai-sdlc-qa (independent verification). Date: 2026-11-23.
- Runtime/model observed: OpenClaw subagent, provider `nan`, model `glm5.3-flash`, no override
  (`execution-model.json`). Implementation's fix run used `nan/deepseek-v4-flash` per its own
  evidence.
- **Verdict per scoped criterion: QA-DEF-1 PASS, QA-DEF-2 PASS, QA-DEF-3 PASS, red→green judgment
  AGREE, open items left unsettled CONFIRMED. Overall recheck verdict: PASS for the re-verified H1
  scope.** The earlier system-level exclusions remain (§5) and N-02 is still not closed (§6); no
  integrated end-to-end payment exists (§7). This recheck re-runs, it does not re-point at old logs.
- Scope: re-verification of the three QA defects from `reviews/h1-qa-20261123/qa-report.md`
  (SHA-256 `2ac3c71662d725bf86a8db4adf86be8447f3405c5aaed6757fd8f6c0152e8844`), the legitimacy of
  the assertion/fixture changes, and the discipline of the three unsettled items. The original
  scope (N-02 gate; D-6/R-2 history) stays in force; the gate criteria were re-executed by the
  unchanged part of the driver.

## 1. Binding re-attestation (read-only)

| Item | Result |
| --- | --- |
| Prior QA report | SHA-256 matches the coordinator pin exactly (`2ac3c716…8844`) |
| Adopted contract | `design/contract-deltas/continuation-rev3-20261123/` manifest hash = `2000bb00e…ab467` verbatim; `sha256sum -c OUTPUTS.SHA256SUMS` = 21/21 OK |
| Implementation fix evidence | `evidence/continuation-h1-qa-fixes-20261123/changed-files.sha256` re-verified 8/8 OK against this working tree |
| Fix-run exits read back | compile `EXIT=0`; preserved red run `EXIT=1` (74 tests, 1 failure); green run `EXIT=0` (74, 0) |
| Branch / HEAD | `feat/wi-acq-auth-001` / `5bbd4eb…` — cited from the fix evidence per the Git routing contract (QA runs no Git/provider commands); no-commit claim is consistent with the changed-file hashes matching the current tree, which QA verified content-wise |
| Normative oracle re-read | D-6/R-2 in `contracts.md`; `historyErrors["503"]` = `{"version":1,"error":"AUTHORIZATION_UNAVAILABLE","action":null}`; `cursorUnsignedHarnessBinding` binds `issuer` and `context.terminalRef:"TERMINAL-A"` |

## 2. Re-executed checks (fresh runs, this tree)

| Command | Result | Log |
| --- | --- | --- |
| `mvn -o -DskipTests compile` | exit 0, BUILD SUCCESS | `log-01-compile.txt` |
| `mvn -o test -Dtest='ConsoleOriginVerificationGateTest,ConsoleHistoryRouteTest,ConsoleHistoryRouteDynamoIntegrationTest,ConsoleAuthorizationRouteTest,ConsoleSubmissionAtomicIntegrationTest' -DfailIfNoTests=true` | **exit 0 — 53 tests, 0 failures, 0 errors, 0 skipped** (gate 9, history unit 24 = 20+4 new, auth route 13, atomic 3, DynamoDB Local 4 with real base table) | `log-02-focused-tests.txt` |
| RECHECK driver `tools/QaH1Driver.java` (QA-owned fakes, compiled main classes) | **70 checks, 70 PASS, 0 FAIL** | `log-03-qa-driver.txt` |

Environment: Temurin JDK 25.0.5, Maven 3.10.1 offline, Docker 29.1.3 (DynamoDB Local via
Testcontainers), host `fernando-OC`. Driver deltas versus the prior revision are listed in
`PROGRESS.md` and in the driver header; the prior driver's allowed cursor-key set was stale against
the now-contract-mandated `issuer`/`terminalRef` bindings (the fix evidence disclosed this), so the
QA artifact was updated — a QA-side correction, never a product change.

## 3. Criterion judgments

### (a) QA-DEF-2 — 503 body equals the reviewed fixture everywhere it can occur — PASS
- Source inspection: the history route emits `503` at exactly four reachable sites — initial registry
  outage, post-read registry outage, store `head` failure and read-range failure — and all four now
  carry `AUTHORIZATION_UNAVAILABLE` (`ConsoleHistoryRoute.java` lines ~134/472/491/499). No other
  503-producing path exists in the route; the unrelated `ADMISSION_UNAVAILABLE` belongs to the C3
  authorization route, not this surface.
- Driver re-execution: `H14`/`H14b` (store `head` failure), `H14c` (read-range failure, newly added),
  `H15` (registry before read) and `H16` (registry after read) each byte-compare the whole body
  against `{"version":1,"error":"AUTHORIZATION_UNAVAILABLE","action":null}` — all PASS, no payload.
- Assertion change judged contract-based, not convenience: the two updated assertions previously
  encoded `UNAVAILABLE`, which contradicted the adopted `historyErrors["503"]` fixture; the new
  `serviceUnavailableBodyExactlyMatchesTheReviewedFixture` asserts the exact fixture bytes. The
  contract, not an existing failing assertion's weaker shape, is the oracle. My independent
  byte-comparisons agree with the new assertions.

### (b) QA-DEF-1 — cursor binds the issuer; per-page re-authentication preserved — PASS
- `ConsolePrincipal` now carries the verified issuer, supplied by `DemoConsoleAuthorizer`, which
  rejects any token whose issuer differs from the configured realm issuer before a principal exists.
  `ConsoleHistoryCursorCodec.Binding` carries `issuer`; the payload always writes it and `parse`
  requires it, so no issuer-less cursor can even decode.
- Driver re-execution: minted-cursor payload contains `issuer` equal to the token issuer
  (`H3f`, `H3e`); a same-key cursor minted under a foreign issuer is refused `403 CONTEXT_DENIED`
  **before any store read** (`H11b`, store `headCalls` delta 0). Same result in the implementation's
  own focused test `cursorMintedUnderAnotherIssuerIsForbidden`.
- Re-authentication discipline preserved: authentication happens once before the store read and
  again after it, before disclosure; identical issuer/subject/azp/audience is re-checked post-read
  and a registry failure there is 503. The unchanged expiry test still proves a token that expires
  during the read returns `401 UNAUTHENTICATED` with one store read and two credential calls
  (`H17` + suite `tokenExpiryDuringTheStoreReadDisclosesNoPayload`). The cursor did not become an
  authentication substitute.

### (c) QA-DEF-3 — terminalRef binding behaves as D-6 requires — PASS
- `AuthorizedContext` gains an optional trusted `terminalRef` (the 4-arg constructor keeps
  non-terminal callers unchanged; the registry — never the request — is its only source). The route
  binds `mandate.terminalRef()` into the cursor, writes `terminalRef` (JSON `null` when the context
  is not terminal-specific), and refuses a differing terminal with `403 CONTEXT_DENIED` on
  continuation and on the post-read re-authentication.
- Driver re-execution: a terminal-scoped registry mints a cursor whose payload carries
  `terminalRef: TERMINAL-A` and whose matching continuation succeeds (`H11d`, both halves); a cursor
  recorded with `TERMINAL-B` against a `TERMINAL-A`/null context is refused `403` before the read
  (`H11c`); a non-terminal registry's payload carries `terminalRef` as JSON `null` (`H3e`), matching
  "when target-specific". This matches the reviewed fixture binding and closes the recorded
  deviation.

### (d) The red→green transition — AGREE: stale test fixture, not a relaxed assertion
- Preserved red run (`runs/focused-console-suites.*`): 74 tests, exactly 1 failure —
  `ConsoleHistoryRouteTest.retiredMandateGenerationForcesFourTenAndNoPayload` at line 222, which is
  that test's `410` assertion. Green run: same 74 tests, 0 failures, 0 skipped; per-class counts
  identical except `ConsoleHistoryRouteTest` 24 = the prior 20 + 4 newly added QA-fix tests.
- The corrected fixture explanation is verified against the code path: a context reissued through
  the old 4-argument constructor has `terminalRef = null`, so `Objects.equals(mandate.terminalRef(),
  binding.terminalRef())` fails and the terminal check (a 403 check) runs before the mandate
  fingerprint check (the 410 path the test name isolates). The reissued fixture now carries
  `TERMINAL-A` with a changed caller id, so the fingerprint mismatch alone produces the 410. The
  assertion itself (`410`, no `"events"` payload) is unchanged; no assertion was weakened, and all
  twenty original test names still exist. I could not byte-diff the red-run fixture (sources not
  preserved — same limitation as the prior report), but the failure line, the fixed code semantics
  and the preserved logs are mutually consistent, so I accept the fixture-correction account.

### (e) Three open items left unsettled — CONFIRMED not silently implemented
1. **Registry mandate identity representation:** the payload still binds only the SHA-256 mandate
   content fingerprint; no `registryMandateId`/`registryVersion` keys were added and no naming
   behaviour was chosen (`H3m` driver check). The question is correctly routed to Requirements.
2. **`cursor` combined with `after`:** behaviour is unchanged — the cursor wins and `after` is
   silently ignored (`H3n` driver check reproduced a 200 continuation ignoring `after=999`); no
   new 400 mapping or acceptance was invented. Open question recorded.
3. **Malformed pagination mapping:** still the disclosed provisional `400 INVALID_CURSOR` mapping
   with no contract text change (quarter of clause checks re-run: `snapshot` value, non-numeric and
   oversized `after`, unknown params, empty/duplicated params; suite test
   `malformedPaginationInputUsesTheRecordedProvisionalMapping` unchanged). Open as before.
- The `epoch` key remains absent for the HTTP-only read under D-6's "epoch when live" wording — a
  documentary fixture difference, settled by contract wording, not by implementation invention.

## 4. Defects found in this recheck
**None.** No executed check violated a scoped criterion (70/70 driver checks, 53/53 suite tests).

## 5. Still not verifiable at this stage (unchanged exclusions, retained explicitly)
1. Deployed-edge behaviour (CloudFront origin-header injection/overwrite, WAF, direct `api_fqdn`,
   N-03/N-02 deployment-gate retests): no cloud/edge execution authorized; **N-02 is not closed by
   these bytes alone.**
2. Platform 401 body and API Gateway authorizer ordering: platform layer not present locally.
3. Real JWKS/signature/issuer/audience/expiry verification truth (slice K1): exercised only through
   the authorizer seam; the issuer now bound into the cursor is the verified claim of that seam, but
   signature truth is still K1's subject.
4. Cursor signing-key custody/rotation and the server-side read-pass store: injected key and logical
   expiry in this slice; deployed composition unresolved.
5. Real CloudWatch log/metric read-back, alarm behaviour: application-record level only.
6. WebSocket route, browser/UI surfaces, WS D-6 clauses: out of H1 scope, unchanged.
7. Byte-level proof of the red→green editing sequence: not possible without preserved red sources;
   judged on consistent indirect evidence (§3d).

## 6. Integrated end-to-end payment
**None exists — restated.** The slice is the boundary gate plus a read-only history route: no
transaction creation, no projection, no dispatch capability, no payment send. The driver's
`H22` payment-side-effect audit again measured zero `append`/`find` invocations across every history
scenario, the route constructor admits no payment collaborator, and the existing atomic-submission
and authorization-route non-regression tests remain green. No Operation A execution against any
issuer was run in this recheck; DR3-01's C3 oracles were not exercised because H1 contains no
payment path.

## 7. Verdict
**PASS for the re-verified scoped criteria** (QA-DEF-1, QA-DEF-2, QA-DEF-3, assertion-change
justification, open-item discipline). This recheck does not approve acceptance, deployment, N-02
closure or release; deployment-gate retests, slice K1 and the Requirements clarifications remain
open. Changed code beyond the re-verified fix set would require renewed independent re-verification.

## 8. Deliverables
`qa-report.md` (this file), `PROGRESS.md`, `SHA256SUMS`, `execution-model.json`,
`log-00-binding-attestation.txt`, `log-01-compile.txt`, `log-02-focused-tests.txt`,
`log-03-qa-driver.txt`, `log-03a-driver-javac.txt`, `tools/QaH1Driver.java` (RECHECK revision).
No product source, contract, work-item or other specialist artifact was modified; no Git or
provider mutation; no cloud, issuer or edge execution; no credential flows; no onward delegation.
