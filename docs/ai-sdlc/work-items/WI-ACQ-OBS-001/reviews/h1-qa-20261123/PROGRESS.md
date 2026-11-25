# Independent QA — H1 construction slice (started 2026-11-23 ~22:50 America/Bogota)

Written first and updated incrementally. Reviewer: ai-sdlc-qa (independent verification).
Verdict scope: H1 criteria only (origin-verification gate + read-only history protocol).
Status vocabulary: PASS / FAIL / BLOCKED per VERIFICATION_DELIVERY.md.

## 1. Inputs and role docs read

- Role docs: ai-sdlc-qa AGENTS.md, SOUL.md, IDENTITY.md, USER.md.
- Shared: README.md, PROJECT_EXECUTION.md, VERIFICATION_DELIVERY.md.
- `docs/ai-sdlc/PROJECT.md`; work item `WORK_ITEM.md`; commission `handoffs/continuation-20261123/`
  and `construction-handoff.md` (H1 scope).
- Normative contracts:
  - `design/contract-deltas/continuation-rev3-20261123/` — manifest
    `2000bb004e68e6d2974bef3067baeae6774f1922a21dca4ea18fecf6944ab467` re-verified:
    SHA-256 of `OUTPUTS.SHA256SUMS` matches; `sha256sum -c` = 21/21 OK, no FAIL lines.
    Read: `contracts.md` (D-6/R-2 cursor/read replacement; error semantics; C3/payment
    non-regression), `README.md`.
  - `design/contract-deltas/continuation-rev3-dr301-20261123/` — `old-prior-fixtures.json`
    SHA-256 `1d3f1406…d5f5` observed (matches pin); `supersession.md` + `verification.md` read.
    Scope: C3 old-prior reference oracles (payment path; not executed here — H1 has no payment).
  - `design/contract-deltas/n02-origin-verification-20261123/contract-delta.md` — SHA-256
    `15959975…f3ad` observed (matches pin). Gate rule §5–§7, OV oracle suite §11, §9.2
    behavioural assertions.
- Functional guide `docs/ai-sdlc/sources/acquiring_simulator_functional_guide.md`: §3,
  §5 Operation A, §13 failure scenarios, §14 channel test interface (seven-day history,
  same-source history reads), §15 cross-cutting observability.
- Implementation evidence `evidence/continuation-h1-20261123/`: RESULTS.md, PROGRESS.md,
  slice-files.sha256, exit/stdout files — read as developer claims, NOT accepted as results.

## 2. Planned execution (independent; do not reuse implementer output as result)

1. Read-only re-attestation: realpath, `git rev-parse --show-toplevel`, branch, HEAD
   (expect `feat/wi-acq-auth-001` / `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14`, dirty tree).
2. Derive expected behaviour from contracts BEFORE reading implementation assertions.
3. Review new console sources + tests for contract conformance and test honesty
   (with the discipline that implementation tests are not the oracle).
4. Build with the project toolchain (JDK 25 + offline Maven) and run the focused console
   suites myself against DynamoDB Local (Testcontainers, amazon/dynamodb-local:2.6.1).
   Count totals from my own reports only.
5. Judge each H1 criterion: PASS / FAIL / cannot-verify (with reason).
6. Write qa-report.md, raw command logs, SHA256SUMS. Record model that actually ran.

## 3. Boundaries

No Git/provider mutation, no cloud/issuer/edge execution, no code edits, no credential
flows, no onward delegation. Payment-side: H1 must show NO payment side effect of any kind;
no Operation A execution needed in this slice beyond non-regression observation.

## 4. Log

- 22:48 — Read-only re-attestation executed: realpath `/home/pazfernando/projects/acquiring-simulator`,`|` git toplevel same, branch `feat/wi-acq-auth-001`, HEAD `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14`, 48 dirty/untracked paths (pre-existing continuation work). Matches the implementing run's binding.
- Read all new console sources (`ConsoleOriginVerificationGate`, `ConsoleOriginDenial`, `ConsoleOriginDenialSink`, `StandardStreamOriginDenialSink`, `ConsoleIntegrationEntryPoint`, `ConsoleHttpRequest` (new query support), `ConsoleHistoryRoute`, `ConsoleHistoryQuery`, `ConsoleHistoryCursorCodec`) plus pre-existing seams (`ConsolePrincipal`, `ConsoleTokenClaims`, `ConsoleTokenDecoder`, `ConsoleAuthorizer`, `ConsoleContextRegistry`, `ConsoleAuthenticationException`, `ConsoleRateLimitHook`).
- Static findings so far (to be dispositioned against executed tests): (a) cursor payload does NOT bind the token `issuer` (D-6 binding list includes issuer; `ConsolePrincipal` has no issuer field) — candidate defect; (b) `terminalRef` not bound — contract says "when target-specific", demo context has no terminal; likely acceptable with rationale; (c) registry "mandate identity/version" approximated by SHA-256 mandate fingerprint (callerId+refs+routes) rather than a registry version — fingerprint-change forces 410, defensible; (d) malformed pagination provisional mapping to `400 INVALID_CURSOR` — implementer-disclosed ambiguity; (e) no server-side read-pass store — implementer-disclosed ambiguity, generation is a UUID inside the signed cursor.
- Test reading: 9 gate cases, 20 history unit cases, 4 DynamoDB Local integration cases.
- Toolchain confirmed present: docker OK, JDK 25 at `~/.local/opt/jdk-25`, maven at `~/.local/opt/maven`, `.m2` populated (offline feasible).

## 4. Log (continued)

- 22:48–22:51 — Compiled main sources (mvn -o, JDK 25.0.5): exit 0 (`log-01-compile.txt`).
- 22:51 — Ran focused console suites independently: **49 tests, 0 failures, 0 errors, 0 skipped**
  (4 DynamoDB Local integration via Testcontainers 2.0.5 + amazon/dynamodb-local:2.6.1; 13 auth
  route; 9 gate; 20 history unit; 3 atomic submission) (`log-02-focused-tests.txt`). Matches the
  implementer's claimed green counts exactly.
- 22:55–23:10 — Built QA-owned driver `tools/QaH1Driver.java` (no product-tree edits): first two
  runs were driver-harness bugs (Jackson Integer-vs-Long comparisons; driver fakes that authorized
  every token); fixed and re-run. Final: **63 checks: 61 PASS, 2 confirmed defects**
  (`log-03-qa-driver.txt`).
- Confirmed defects: QA-DEF-1 signed cursor payload lacks `issuer` binding (D-6 binding list +
  reviewed wire-fixtures `cursorUnsignedHarnessBinding` both bind issuer; mitigated by per-page
  re-auth vs single-issuer authorizer); QA-DEF-2 503 body code `UNAVAILABLE` vs pinned
  `AUTHORIZATION_UNAVAILABLE`. Also recorded QA-DEF-3 (terminalRef context binding absent, LOW) and
  three observations (mandate fingerprint vs registryMandateId/registryVersion; epoch key omitted;
  cursor+after silently ignores after).
- 23:10 — `sha256sum -c` on implementer `slice-files.sha256`: 25/25 OK (`log-04-slice-hash-verify.txt`).
- 23:12 — qa-report.md written: verdict **FAIL** for scoped H1 criteria (two executed checks
  violated contract criteria); everything else passed; integrated end-to-end payment does not exist.
