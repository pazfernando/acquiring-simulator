# Progress — P2b6b signed late admission gateway

UTC: 2026-11-20T22:30:19Z start.

| Slice | Status | Actual result | Evidence |
| --- | --- | --- | --- |
| P2b6b signed late admission gateway + tests | **TARGETED GREEN (first run)** | 6 tests, 0 failures, 0 errors, exit 0 | this directory |
| Justified regression: `EvidenceReceiptAtomicIntegrationTest` (shared receipt writer from P2b6a1) | **GREEN** | 4 tests, 0 failures, 0 errors, exit 0 | this directory |
| P2b6a1 late signed receipt composer | reviewed / accepted input | 2/2 green at 22:24:14Z | `WI-ACQ-OBS-001/evidence/p2b6a1-20261120T222414Z` |

## What changed

Two new files, nothing else modified:

1. `src/main/java/com/acquiring/simulator/adapter/aws/LateSignedEvidenceReceiptGateway.java` —
   ONE gateway: trusted context → real C4 verify → strong original read → real P6 bind → envelope
   authorization → evidence-time classification → receipt dedupe → atomic late composition.
2. `src/test/java/com/acquiring/simulator/channel/LateSignedEvidenceReceiptGatewayIntegrationTest.java`
   — six tests against DynamoDB Local.

No spec, pom, resource, fixture/pin, existing composer or existing test file was edited. No new
dependency.

## What the green runs prove

- Real Ed25519 DIRECT verification + real P6 binding + one atomic `TransactWriteItems` covering record
  (CAS 2, committed 3), late metadata, 3 event rows, HEAD 3, dedupe 3, slot 3, one P5 receipt row.
- Fresh gateway exact P5 identity/hash replay after terminal state preserves the first receipt,
  first `collectedAt` (dedupe rows byte-equal before/after), first late metadata, slot and sequence.
- A contradictory late signed outcome writes one sanitized quarantine marker and preserves the
  established confirmation unchanged.
- Lost commit response is `RECOVERED` once with no second result; unreadable lookup is `UNKNOWN`
  while the write is known (from a clean client) to have committed — never reported as no-send.
- On-time evidence delivered late returns `NORMAL_RESULT_ADMISSION` with zero writes and no relabel.

## Still open

- Not production-wired: no `AuthorizationService`/`AwsRuntime` reference (`wiring.txt`).
- No HTTP/service route, no runtime activation, no key custody, no downstream call.
- Reused-identity-different-content quarantine is implemented but not fixture-addressed in a late
  window; the contradictory-outcome class is exercised. An owned late fixture for a reused identity
  would close that coverage gap.
- O8 capacity numbers remain PROPOSED; test capacity is test-only.
- P1 G6 Security review remains open.
- No full suite, C4 regression, feature run, AWS call, git write, publication or deletion.
