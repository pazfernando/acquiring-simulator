# H1 QA recheck — progress (ai-sdlc-qa, 2026-11-23)

Independent re-verification of the QA-driven fixes (developer evidence
`evidence/continuation-h1-qa-fixes-20261123/`, never trusted as QA evidence).

Chronology:
- Verified the coordinator pin (prior QA report SHA-256), the adopted rev3 contract manifest
  (`2000bb00…ab467`, 21/21 OK) and the implementation `changed-files.sha256` (8/8 OK) against
  this working tree — `log-00-binding-attestation.txt`.
- Read the adopted D-6/R-2 text and `wire-fixtures.json` (`historyErrors`, `cursorUnsignedHarnessBinding`)
  as the normative oracle before re-running anything.
- Re-derived expectations for QA-DEF-1/2/3 strictly from the contract: 503 body bytes everywhere a
  503 can occur; cursor issuer binding with refusal of a foreign-issuer cursor; terminalRef context
  binding; per-page re-authentication before AND after the store read; expired token 401 before
  disclosure.
- Updated the QA-owned driver in place of the prior revision (`tools/QaH1Driver.java`, RECHECK
  revision): payload allowed-key set now includes `issuer`/`terminalRef` (as the fix evidence asked),
  all four reachable 503 sites are byte-compared against the fixture, added negative foreign-issuer
  and other-terminal continuation checks, a terminal-scoped positive continuation, a read-range
  failure check, and open-item guards (no `registryMandateId`/`registryVersion`/`epoch` keys; the
  `cursor`+`after` combination still resolves "cursor wins").
- Executed fresh, not reused: `mvn -o -DskipTests compile` (log-01, exit 0); the same focused
  five-class suite as the prior report incl. DynamoDB Local (log-02, 53/53 green); the RECHECK
  driver (log-03, 70/70 PASS).
- Inspected the corrected test and the preserved red run: the only red failure was a fixture that
  reissued the authorized context without `terminalRef`, so the new terminal check fired before the
  generation check; the fixture now carries `TERMINAL-A` and the 410 assertion is unchanged.

No Git/provider command, no code edit, no cloud/issuer/edge execution, no credential flow, no
onward delegation. QA wrote only under `reviews/h1-qa-recheck-20261123/`.
