# P1b2b — journalVersion vs persisted CAS version: conflict evidence (2026-11-20)

Status: **UNRESOLVED CONFLICT — affected production wiring intentionally DISABLED.**
Scope: initial CLAIM only. This note records evidence; it does not reinterpret financial rules and
does not mutate any approved fixture or specification.

## Question

The approved claim fixture labels the claim `journalVersion: 1` and `stateAfter: CLAIMED_NOT_SENT`.
The existing DynamoDB journal write (`DynamoAuthorizationRepository.claim`) persists the internal
`version` attribute as `0`. Is the public `journalVersion` an explicitly documented value that is
distinct from the internal storage/CAS `version`?

## Finding

**No approved document explicitly states the distinction.** The contract defines what
`journalVersion`/`businessVersion` mean semantically (a committed journal version), but never names
the internal `version` attribute, its base value, or a relation such as `journalVersion = version + 1`.
Treating `journalVersion` as `storageVersion + 1` is therefore an inference, not a documented rule.
Per coordinator instruction, it is not encoded as a label transformation in production wiring.

## Exact approved sources

1. `observation-contract.md`, O1a/O2a (SHA-256 recorded below), line 17:
   > "Source wrapper, not canonical content: deliveryClass, eventSha256, journalVersion (critical
   > only), committedAt (critical only), collectedAt, sourceSequence, storedAt, stateAfter
   > (critical-only journal state at that committed version; null for tasks, never
   > caller-selected/current authority)."
   `journalVersion` is the **committed journal version** and `stateAfter` is the state **at that
   committed version**. No storage/CAS counter is mentioned.

2. `observation-contract.md`, line 82:
   > "Event-table history is recorded journal knowledge with original journalVersion/collectedAt,
   > not current financial authority if journal unreadable."

3. `channel-contract.md`, C3 (SHA-256 recorded below), line 21:
   > "businessVersion (positive committed integer or null)"
   The first committed public version is therefore positive, i.e. `1` at claim — not `0`.

4. `vectors/async-projection-vectors.json`, `sourceExamples` (SHA-256 recorded below):
   - sequence 1: `journalVersion: 1`, `stateAfter: CLAIMED_NOT_SENT`, reason `CLAIM_COMMITTED`
   - sequence 2: `journalVersion: 2`, `stateAfter: POSSIBLE_SEND`, reason `DISPATCH_INTENT_COMMITTED`
   - sequence 3: `journalVersion: 3`, `stateAfter: APPROVED`, reason `ISSUER_APPROVED`

5. `vectors/response-vectors.json` (SHA-256 recorded below), cases H05/H16:
   claim/crash-before-CAS history responses carry `"businessVersion": 1` with
   `disposition: NOT_DISPATCHED`, `error: RESERVED_UNSENT`. Case H07 (verified result) carries
   `businessVersion: 2`; H01 carries `businessVersion: 2`.

## Observed implementation (unmodified this turn)

`src/main/java/com/acquiring/simulator/adapter/aws/DynamoAuthorizationRepository.java`
(SHA-256 below):
- `INITIAL_VERSION = 0L` (line 34) is written on claim (line 48).
- `mutate()` does `version + 1` per successful conditional replacement (lines 89–96).
- `claim()` is the pre-existing production path; it inserts version `0` and does not touch the
  EVENT table.

Consequence: on the same claim, the fixture's public committed version is `1` while the persisted
storage attribute is `0`. A `+1` mapping would make the fixture pass, but that mapping is **not
documented** anywhere in the approved set. It is also not established that the storage counter is
the same concept as the public committed journal version (it is a lost-update guard incremented on
every record replacement, including replacements that are not business transitions).

## Disposition

| Item | Disposition |
| --- | --- |
| Public `journalVersion` / `businessVersion` semantics | Defined as the committed journal version; claim = 1 per fixtures |
| Internal `version` attribute | Implementation-only CAS counter, base 0; **no documented public mapping** |
| `journalVersion = version + 1` | **NOT adopted** — no documented basis; would be label fiction |
| Silent migration of persisted `version` base | **NOT performed** |
| Mutation of approved fixtures/spec | **NOT performed** |
| Production wiring of claim transaction (`AuthorizationService`, `AwsRuntime`) | **DISABLED / not changed** |
| Safe adapter + recovery work under this conflict | Allowed and delivered (see progress record) |

The conflict is escalated for an owner decision: either (a) explicitly define the public
journal/business version as a distinct 1-based committed-transition counter stored alongside the
record, or (b) redefine the CAS counter base/meaning. Until then the atomic claim transaction stays
out of the production service path.

## Resolution pointer (2026-11-20, P1b2c)

Coordinator clarification: the approved public contract wins over the legacy internal
implementation, and the owner need not choose private storage fields. This was implemented as option
(a) above — an additive persisted `committedJournalVersion = 1` at claim, beside the unchanged CAS
`version = 0`, with no derivation and no backfill. It is recorded as an implementation mapping of
approved committed-version semantics, not a spec amendment. See
`../p1b2c-2026-11-20/mapping.md`. The atomic claim transaction still stays out of the production
service path pending separate wiring.

## Integrity

See `manifest.md` in this directory for recomputed hashes of the cited specifications, fixtures and
source files.
