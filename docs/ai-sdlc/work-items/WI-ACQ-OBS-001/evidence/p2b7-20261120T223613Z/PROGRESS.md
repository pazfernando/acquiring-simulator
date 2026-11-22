# Progress — P2b7 internal signed-evidence service facade

UTC: 2026-11-20T22:36:13Z start.

| Slice | Status | Actual result | Evidence |
| --- | --- | --- | --- |
| P2b7 service facade + 3 focused integration tests | **TARGETED GREEN (first run)** | 3 tests, 0 failures, 0 errors, exit 0 | this directory |
| P2b5a normal atomic receipt | incomplete original record; recovered | 4/4 in recovery evidence | `WI-ACQ-AUTH-001/evidence/p2b5a-recovery-20261120T215327Z` |
| P2b5b normal signed gateway recovery | accepted input | 10/10 green | `WI-ACQ-OBS-001/evidence/p2b5b-20261120T220425Z` |
| P2b5c conflict/quarantine | accepted input | 15/15 green | `WI-ACQ-OBS-001/evidence/p2b5c-20261120T221345Z` |
| P2b6a1 late receipt composer | accepted input | 2/2 green | `WI-ACQ-OBS-001/evidence/p2b6a1-20261120T222414Z` |
| P2b6b signed late gateway | accepted input, not rerun | 6/6 + regression 4/4, exit 0 | `WI-ACQ-OBS-001/evidence/p2b6b-20261120T223019Z` |

## What changed

Two new files, nothing else modified:

1. `src/main/java/com/acquiring/simulator/adapter/aws/SignedEvidenceAdmissionService.java` — one
   facade: exact carriage integrity → authenticated-sender binding → existing late gateway → when
   classified as on-time, the existing normal gateway once.
2. `src/test/java/com/acquiring/simulator/channel/SignedEvidenceAdmissionServiceIntegrationTest.java`
   — three tests against real DynamoDB Local, the real DIRECT verifier and the real P6 binder.

No spec, pom, resource, fixture/pin, existing gateway/composer or existing test file was edited.
No dependency was added.

## What the green run proves

- True late evidence is routed to the late gateway once and commits one atomic receipt/result.
- Exact redelivery through the facade is a receipt-level replay with no second transaction and no
  change to first receipt, late metadata, record, dedupe rows, HEAD or slot.
- On-time evidence delivered late is classified by evidence time, then admitted by the normal
  signed gateway exactly once: normal journal version, no late metadata, one transaction.
- A real signature rejection is `REFUSED`; a forged carriage hash label is `NONE`; neither writes
  a receipt, quarantine, version, event, HEAD or slot.

## Still open

- Not production-wired: no `AuthorizationService`/`AwsRuntime` reference, no HTTP endpoint or
  authenticated-transport adapter, no runtime activation, no key custody, no capacity approval,
  no downstream call.
- Sender authentication is a transport concern owned by the future HTTP seam; this facade derives
  only the binding label from the already-authenticated context.
- Diagnostic forwarding through the late envelope is refused here; supporting it would require a
  signed receipt gateway seam that can verify with forwarding permission.
- O8 capacity numbers remain PROPOSED; test capacity is test-only.
- P1 G6 Security review remains open; independent QA, Security review and human acceptance remain
  pending.
- No full suite, C4 regression, feature run, AWS call, Git write, publication, credential flow or
  deletion.
