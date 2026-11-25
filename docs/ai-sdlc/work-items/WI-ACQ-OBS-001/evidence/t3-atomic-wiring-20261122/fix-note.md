# T3 atomic wiring — short fix run, 2026-11-22

Binding: /home/pazfernando/projects/acquiring-simulator, branch feat/wi-acq-auth-001.
Runtime: local JVM, JAVA_HOME=/home/pazfernando/.local/opt/jdk-25 (OpenJDK 25.0.5),
Apache Maven 3.10.1, offline (`-o`). Model observed: nan/deepseek-v4-flash.
No Git writes, no AWS call, no credentials, no deployment, no publication.

## Cause (diagnosed before the edit)

`AwsRuntime.atomicService` wires the gateways in the correct order, and
`AuthorizationService.authorizeAtomic` -> `dispatchCommitted` calls the claim gateway first, reads
the committed claim version back from the stored row, and only then requests the dispatch intent
with the **in-memory** undispatched claim (`state = AWAITING`, `dispatchedAt = null`,
`decisionSource = NONE`). `DynamoClaimGateway` always claims in the single `CLAIMED_NOT_SENT`
(AWAITING) mode and persists `committedJournalVersion = 1`; it never marks the claim dispatched.
`DynamoDispatchIntentGateway` therefore receives exactly the state `PossibleSendCriticalComposer`
requires, so the recorded error "dispatch intent requires an undispatched CLAIMED_NOT_SENT claim"
was not a wiring-order defect: it came from the earlier test revision handing the gateway a claim
read back *after* a completed authorization (already dispatched), and from test assertion helpers
that read the wrong EVENT partition. Both were test-side.

The single red case in this run was also test-side: after a definitively rejected claim the HEAD
row correctly does not exist, but DynamoDB Local returns an **empty map** (not `null`) for an absent
item, and `storeHead()` only handled `null`, producing an NPE instead of asserting 0.

## Change

One helper in `AwsRuntimeAtomicWiringIntegrationTest.storeHead()`: treat an empty item map as an
absent HEAD (`item == null || item.isEmpty()` -> 0). No production code change; no assertion
weakened, removed or skipped.

## Results

- `AwsRuntimeAtomicWiringIntegrationTest`: 10 run, 0 failures, 0 errors, exit 0 (`fix-exit.txt`).
  The pre-fix red run is preserved as `fix-red-run1-*.txt` (10 run, 0 failures, 1 error, exit 1).
- `AtomicClaimServiceWiringIntegrationTest`: 6/0/0, exit 0.
- `DispatchIntentServiceWiringTest`: 9/0/0, exit 0.
- `LateEvidenceServiceWiringTest`: 11/0/0, exit 0.

Full verification (freeze, MANIFEST, PROGRESS, traceability) is deliberately left to the separate
verification run.
