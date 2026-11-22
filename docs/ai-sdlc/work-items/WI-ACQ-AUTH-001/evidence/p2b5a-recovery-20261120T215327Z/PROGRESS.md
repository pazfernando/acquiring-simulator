# Progress — P2b5a recovery

| Slice | Status | Actual result | Evidence |
| --- | --- | --- | --- |
| P2b5a atomic normal verified-result receipt | **TARGETED TEST GREEN (recovery)** | 4 tests, 0 failures, 0 errors, exit 0, BUILD SUCCESS | this directory |
| P2b5a prior attempt | FAILED / one run incomplete | 4 tests, 2 errors (`journal row is missing journalVersion`), exit 1; later run empty with no exit | `p2b5a-20261120T214745Z` (preserved) |

## What changed

One scoped production read fix in
`src/main/java/com/acquiring/simulator/adapter/aws/DynamoAuthorizationRepository.java`:
`readEvidenceReceipt` now reads the persisted numeric `journalVersion` via a new `requiredNumber`
helper instead of the string accessor. Persisted schema, composer, record model and all four test
assertions are unchanged.

## What the green run proves

One transaction commits and preserves: business record (`APPROVED`, CAS `version` 2, committed
`journalVersion` 3), critical event (3 rows), EVENT HEAD (3), dedupe replay (`EXACT_REPLAY`), slot
(3) and normalized receipt (1 row, `journalVersion` 3). Abort path commits nothing; refusal path
writes nothing. Evidence is the real C4 DIRECT verification and P6 binding of the persisted
`0100` / received `0110` octets with an ephemeral test key.

## Still open

- Not production-wired; no `AuthorizationService`/`AwsRuntime` reference.
- Signed receipt gateway and authorized read seam not implemented.
- Receipt-identity conflict/recovery and durable quarantine not implemented.
- O8 capacity numbers remain PROPOSED; test capacity is test-only.
- P1 G6 Security review remains open.
- Full suite / C4 regression / feature tests were not run in this recovery.
