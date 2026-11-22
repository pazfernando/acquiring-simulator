# P2a3a evidence manifest — P4 actor/stage/DE39/result/reason matrix

- Work item: `WI-ACQ-OBS-001` (protocol P2 slice). Evidence path:
  `evidence/p2a3a-20261120T193639Z/`.
- Binding: repo `/home/pazfernando/projects/acquiring-simulator`; branch
  `feat/wi-acq-auth-001`; HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`;
  origin `https://github.com/pazfernando/acquiring-simulator.git`; configured
  model provider `nan` / `deepseek-v4-flash`, no override; captured
  `2026-11-20T19:36:39Z` (`BINDING.txt`). Working tree preserved; no Git write,
  publication, cloud call, credential flow, deletion or spec/oracle edit.
- Status: **IMPLEMENTED AND LOCALLY VERIFIED; documentation-only finalization
  this turn. No source edit, no test rerun, no feature added.**
- Deliverable: `Iso8583P4MatrixValidator`,
  `Iso8583P4MatrixDecision`, `Iso8583P4MatrixException`, and
  `Iso8583P4MatrixValidatorTest` in
  `src/main/java|test/java/com/acquiring/simulator/protocol/iso8583/`.

## What the validator does (eligibility only)

A pure syntactic check of one **claimed** tuple
`(actor, stage, DE39, result, reason)` against the eleven approved SIM-ISO87A-v1
P4 rows. Checks run in this order and fail on the first violation:

1. every component non-null and non-empty → `MISSING_COMPONENT`;
2. DE39 ∈ `{00,05,30,91}` → `DE39_UNSUPPORTED`;
3. actor/stage pair permitted:
   `ISSUER/ISSUER_REPLIED`, `PROCESSOR/BEFORE_NETWORK`, `NETWORK/BEFORE_ISSUER`,
   `NETWORK|PROCESSOR/POSSIBLE_ISSUER` → `ACTOR_STAGE_NOT_PERMITTED`;
4. DE39 permitted for that pair: issuer only `00/05`; processor before network
   only `30`; network before issuer `30/91`; possible-issuer only `91`
   → `DE39_NOT_PERMITTED_FOR_STAGE`;
5. result fixed by DE39 (and for `91`, by stage: `POSSIBLE_ISSUER`→
   `NOT_CONFIRMED`, otherwise `PROCESSOR_REJECTED`) → `RESULT_NOT_PERMITTED`;
6. reason fixed by the tuple (`APPROVED`; `DELIBERATE_DECLINE`; `FORMAT|
   DISALLOWED`; `UNAVAILABLE`; `UNCERTAIN|UNAVAILABLE`)
   → `REASON_NOT_PERMITTED`.

The eleven approved tuples: issuer `00/APPROVED/APPROVED`;
issuer `05/DECLINED/DELIBERATE_DECLINE`; processor-before-network and
network-before-issuer `30/PROCESSOR_REJECTED/FORMAT|DISALLOWED`;
network-before-issuer `91/PROCESSOR_REJECTED/UNAVAILABLE`; network or processor
at possible-issuer `91/NOT_CONFIRMED/UNCERTAIN|UNAVAILABLE`. The "no response at
all" case is not a row; absence is never converted into a manufactured response.

### Explicit non-assertion

A successful call means **only** that the five claimed values match an approved
row. It is **not authentication**, issuer truth, payment/authorization
admission, or trust in evidence. The actor is caller text; the evidence
identifier, DE38 presence/absence, original-instruction/context binding,
durable processor receipt and financial outcome are all later layers.
Exactly as the profile requires, a network/processor claim of an issuer code is
rejected here, and `00/05` are eligible only under a claimed
`ISSUER` at `ISSUER_REPLIED`.

## Corpus partition (exact)

The 41 structurally accepted `0110` fixtures in
`vectors/packed-vectors.json` partition as **22 ACCEPT + 4 schema-negative +
5 P4-negative (`EVIDENCE_MATRIX`) + 10 later-layer**. The 10 later-layer
fixtures are matrix-eligible but owned elsewhere: instruction binding 3,
auth-reference/DE38 binding 2, attestation 2, business conflict 1, domain 2.

### The five P4-negative fixtures and their exact first-failure rule

| Fixture | Rule | Component |
| --- | --- | --- |
| `N-00-network` | `ACTOR_STAGE_NOT_PERMITTED` | `stage` |
| `N-05-processor` | `ACTOR_STAGE_NOT_PERMITTED` | `stage` |
| `N-30-issuer` | `DE39_NOT_PERMITTED_FOR_STAGE` | `de39` |
| `N-91-issuer` | `DE39_NOT_PERMITTED_FOR_STAGE` | `de39` |
| `N-unknown-code` | `DE39_UNSUPPORTED` | `de39` |

Diagnostics are fixed text, never echo the rejected values, and use no `=`/`;`
delimiters.

## Source snapshot ↔ results correspondence

`source.sha256` captures the four delivered files; `hashes-*.sha256` capture
169 inputs each.

| File | SHA-256 |
| --- | --- |
| `Iso8583P4MatrixValidator.java` | `2aa986da571753f4de21c1b3dab00849f8cbe11d1bc092747de3400eb8d1a771` |
| `Iso8583P4MatrixDecision.java` | `92b415935935825061f03a362c95e40e1f786fcdec26165b9c57c5f7d9dc2ffb` |
| `Iso8583P4MatrixException.java` | `a3da1f11ba557ad49fa9bf5345a741302dc8536903de02c3e67c17389f70633d` |
| `Iso8583P4MatrixValidatorTest.java` | `849f21be8561f4ecc8db3e77b52bae250e57e7aae8c9a6f715fe31ecc2dbf7b9` |

- `hashes-before-final.sha256` and `hashes-after.sha256` are **byte-identical**
  (169 entries, `hashes-diff.txt` empty, `HASH_DIFF_EXIT=0`).
- `hashes-before.sha256` (14:36 capture, before the final test expansion)
  differs from `hashes-before-final` at **exactly one** entry: the test file
  `04dc119e97336ab105ad5bf42e754e6ecc592a2863e1cb65de18aab1ae770fcd` →
  `849f21be…c2dbf7b9`. No main source, resource, `pom.xml` or oracle hash changed.
- Therefore the accepted **62/62** and **316/316** logs correspond to the
  `849f21be` test snapshot plus the three main hashes above. The earlier
  **29/29** and **283/283** attempt logs correspond to the superseded
  `04dc119e` test bytes. The old test bytes were not preserved beyond their
  hash, so the exact 29→62 delta is not reconstructible from evidence.
- `git-status-before.txt`, `git-status-before-final.txt`,
  `git-status-after.txt` and `git-status-final.txt` are identical
  (`git-status-diff.txt` empty, `STATUS_DIFF_EXIT=0`); the P4 sources remain
  under the untracked `src/.../protocol/` tree. `head-after.txt` is still
  `cfc456f4…`. Oracle recheck (`hash-reverify.txt`): profile, packed vectors and
  review manifest all `OK`, `ORACLE_CHECK_EXIT=0`.

## Actual results (recorded, not rerun)

| Attempt | Selector | Result | Exit |
| --- | --- | --- | --- |
| 1 `attempt1-test` | `Iso8583P4MatrixValidatorTest` | 29/29, 0 F/E/S | 0 |
| 1 `attempt1-protocol-regression` | `Iso8583*Test` | 283/283, 0 F/E/S | 0 |
| 2 `test-attempt2` | `Iso8583P4MatrixValidatorTest` | 62/62, 0 F/E/S | 0 |
| 2 `attempt2-protocol-regression` | `Iso8583*Test` | 316/316, 0 F/E/S | 0 |
| final `test` | `Iso8583P4MatrixValidatorTest` | 62/62, 0 F/E/S | 0 |
| final `protocol-regression` | `Iso8583*Test` | 316/316, 0 F/E/S | 0 |

`316 = 25 FieldReader + 62 P4Matrix + 74 DE48Response + 58 DE48Request +
94 Scanner + 3 HeaderParser`. The **62-test** log covers only the P4 matrix
class; the **316-test** log covers the six protocol classes above. Neither is
the full project suite and neither is a codec.

### Attempt-failure statement

No failing test, compile or protocol-regression attempt is recorded inside this
slice. Attempt 1 was green on a 29-test class subset; the class was then
expanded (test-only) to 62 tests and both attempt 2 and the final rerun are
green. The reported "delivery failure" was the coordinator return/operation
channel (aborted/no pending), **not** a build or test failure; the code and
evidence were saved despite it. No production source was edited between
attempts.

## Trust limitations / non-claims

Local JUnit results only. No authentication, producer-principal verification,
issuer attestation/trust, evidence-ID resolution, DE38 presence resolution,
instruction/context/amount binding, durable processor receipt, business
conflict admission or financial outcome is implemented or claimed. No C4
carriage, response packer, 826-byte admission, domain/Gregorian/DE7 validation,
HTTP route or runtime wiring. No full-codec, full-suite, QA, Security review or
human-acceptance claim.

## Remaining after P2a3a

- **Next smallest slice:** instruction/context binding + exact
  authorization-reference (DE38) binding over an already-verified **trusted
  record seam** — consume that record as an input; do not implement
  authentication or attestation. Rules from `iso8583-profile.md:26/:97`:
  DE38 optional on approval, never mandatory without real evidence; missing
  DE38 invalid only when the trusted record asserts a reference the response
  omits; unsolicited/mismatching DE38 invalid; DE38 is not a transaction ID or
  approval proof. Fixtures: `N-wrong-correlation`, `N-wrong-context`,
  `N-mismatch-amount` (instruction) and `N-unsolicited-DE38`,
  `N-missing-DE38-asserted` (auth reference).
- Then: C4 evidence carriage/attestation (`N-forged-actor`,
  `N-unresolvable-evidence`), domain Gregorian/DE7/amount/currency
  (`N-response-DE7`, `N-response-invalid-date`), business conflict/quarantine,
  response packer, 826 admission, HTTP/runtime wiring.
