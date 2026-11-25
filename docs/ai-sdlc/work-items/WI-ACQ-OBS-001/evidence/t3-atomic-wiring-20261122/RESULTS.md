# T3 atomic wiring — verification run, 2026-11-22

**Status: developer verification evidence on one working tree. Not independent QA, not Security
review, not human acceptance, not deployment.**

This run confirms and documents the already-green wired runtime. It adds **no feature, no
production change, no assertion change** and does not weaken or skip any test. The pre-fix red run
is preserved in this directory and is not rewritten.

## 1. Binding and runtime

| Item | Value |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` (`verify-branch.txt`) |
| HEAD | `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` before and after (`verify-head.txt`, `verify-head-after.txt`) |
| Runtime | local JVM, `JAVA_HOME=/home/pazfernando/.local/opt/jdk-25` |
| Java | OpenJDK 25.0.5 2026-10-20 LTS |
| Maven | Apache Maven 3.10.1 (`3afd8084b5629f80b97750906a719879ed4cd283`), offline `-o` |
| Model observed | provider `nan`, model `deepseek-v4-flash`; session model, no override requested |
| Test infrastructure | Testcontainers 2.0.5, `amazon/dynamodb-local:2.6.1`, SQLite for the local-profile core |
| Cloud | No AWS call, no credentials, no deployment, no publication, no Git write |

## 2. Diagnosed cause (carried from the fix run)

`AwsRuntime.atomicService(...)` wires the gateways in the correct order. `AuthorizationService`
takes the claim gateway first, reads the committed claim version back from the stored row, then
requests the dispatch intent from the **in-memory undispatched claim** (`state = AWAITING`,
`dispatchedAt = null`, `decisionSource = NONE`). `DynamoClaimGateway` claims only in the single
`CLAIMED_NOT_SENT` (AWAITING) mode and persists `committedJournalVersion = 1`; it never marks the
claim dispatched. `DynamoDispatchIntentGateway` therefore receives exactly the state
`PossibleSendCriticalComposer` requires.

The earlier error "dispatch intent requires an undispatched CLAIMED_NOT_SENT claim" was therefore
**not** a wiring-order defect. It came from two test-side mistakes: the earlier test revision handed
the gateway a claim read back *after* a completed authorization (already dispatched), and assertion
helpers read the wrong EVENT partition.

The single red case in the fix run was also test-side: after a definitively rejected claim the HEAD
row correctly does not exist, but DynamoDB Local returns an **empty map** (not `null`) for an absent
item, so `storeHead()` NPE'd instead of asserting 0.

**Conclusion: no production defect. The runtime composition was already correct.**

## 3. The one change in the whole slice

Exactly one test helper changed, in
`src/test/java/com/acquiring/simulator/adapter/aws/AwsRuntimeAtomicWiringIntegrationTest.java`:

- `storeHead()` treats an empty item map as an absent HEAD (`item == null || item.isEmpty()` → 0).

Why only this: the failing assertion was a test-double artifact of DynamoDB Local's absent-item
shape, not a product behavior. Asserting 0 after a rejected claim is the correct assertion; the
helper needed to recognize the SDK's empty-map representation. No assertion was weakened, removed,
reordered or skipped. No production file changed in the slice.

## 4. Freeze

Frozen **before** the run, re-checked **after**:

| Item | Value |
| --- | --- |
| Scope | `src/main/**/*.java`, `src/test/**/*.java`, `src/main/resources/**`, `pom.xml` |
| Frozen paths | **292** (`verify-freeze-filelist.txt`) |
| Hashes | `verify-hashes-before.sha256` = `verify-hashes-after.sha256`; `diff` exit 0, 0 bytes (`verify-hash-diff.txt`) |
| Integrity check | `sha256sum -c` **292/292 OK**, exit 0 |
| Git working tree | `verify-git-status-before.txt` = `verify-git-status-after.txt`, `diff` exit 0, 0 bytes |
| HEAD | unchanged |

**Freeze limitation, stated plainly:** this is a **post-edit baseline**. `AwsRuntime.java` was
already modified and the new test file already existed when the freeze was taken
(`verify-git-status-before.txt` shows `M .../AwsRuntime.java` and untracked
`src/test/java/com/acquiring/simulator/adapter/`). The freeze proves the run changed nothing; it
does **not** prove the bytes are the pre-edit bytes. `AwsRuntime.java` mtime was 19:20 and the fix
run's own artifacts stop at 20:11, so no edit occurred between the fix run and this one.

## 5. Execution

One Maven invocation, 23 distinct classes (24 selected names, `AwsTransportTest` listed once
explicitly and once by package expansion — Maven executes it once):

```bash
JAVA_HOME=/home/pazfernando/.local/opt/jdk-25 \
PATH="$JAVA_HOME/bin:/home/pazfernando/.local/opt/maven/bin:$PATH" \
mvn -o -B test -Dtest='AwsRuntimeAtomicWiringIntegrationTest,<all com.acquiring.simulator.aws.*Test>,AwsTransportTest,AuthorizationCoreIntegrationTest,ResponseTimeoutConfigTest'
```

Selected set recorded in `verify-selected-classes.txt`; exit in `verify-run-exit.txt`; stdout in
`verify-run-stdout.txt`; stderr in `verify-run-stderr.txt`.

`src/test` needed no recompile ("Nothing to compile - all classes are up to date"), so this run
executed the same compiled test classes as the fix run against the frozen bytes.

### Results per class

| Class | Run | Fail | Err | Skip | Exit |
| --- | --- | --- | --- | --- | --- |
| `adapter.aws.AwsRuntimeAtomicWiringIntegrationTest` | 10 | 0 | 0 | 0 | 0 |
| `aws.AtomicClaimServiceWiringIntegrationTest` | 6 | 0 | 0 | 0 | 0 |
| `aws.DispatchIntentServiceWiringTest` | 9 | 0 | 0 | 0 | 0 |
| `aws.LateEvidenceServiceWiringTest` | 11 | 0 | 0 | 0 | 0 |
| `aws.ResultServiceWiringTest` | 12 | 0 | 0 | 0 | 0 |
| `aws.NetworkResultServiceWiringTest` | 12 | 0 | 0 | 0 | 0 |
| `aws.ObsAcceptanceAtomicChainIntegrationTest` | 2 | 0 | 0 | 0 | 0 |
| `aws.DynamoCriticalClaimIntegrationTest` | 8 | 0 | 0 | 0 | 0 |
| `aws.DynamoAtomicClaimRecoveryIntegrationTest` | 4 | 0 | 0 | 0 | 0 |
| `aws.DynamoDispatchIntentGatewayIntegrationTest` | 4 | 0 | 0 | 0 | 0 |
| `aws.DynamoResultGatewayIntegrationTest` | 6 | 0 | 0 | 0 | 0 |
| `aws.DynamoResultTransitionIntegrationTest` | 7 | 0 | 0 | 0 | 0 |
| `aws.DynamoNetworkResultGatewayIntegrationTest` | 7 | 0 | 0 | 0 | 0 |
| `aws.DynamoNetworkRejectionTransitionIntegrationTest` | 2 | 0 | 0 | 0 | 0 |
| `aws.DynamoPossibleSendIntegrationTest` | 7 | 0 | 0 | 0 | 0 |
| `aws.DynamoEventStoreIntegrationTest` | 16 | 0 | 0 | 0 | 0 |
| `aws.DynamoLateEvidenceAdmissionGatewayIntegrationTest` | 7 | 0 | 0 | 0 | 0 |
| `aws.DynamoLateEvidenceTransitionIntegrationTest` | 11 | 0 | 0 | 0 | 0 |
| `aws.DynamoLateEvidenceServiceIntegrationTest` | 5 | 0 | 0 | 0 | 0 |
| `aws.AwsAuthorizationIntegrationTest` | 10 | 0 | 0 | 0 | 0 |
| `aws.AwsTransportTest` | 6 | 0 | 0 | 0 | 0 |
| `AuthorizationCoreIntegrationTest` | 24 | 0 | 0 | 0 | 0 |
| `config.ResponseTimeoutConfigTest` | 6 | 0 | 0 | 0 | 0 |
| **Total (23 classes)** | **192** | **0** | **0** | **0** | **0** |

`BUILD SUCCESS`, total build time `01:15 min`, finished `2026-11-22T20:14:10-05:00`.

**Pre-fix red run preserved** (`fix-red-run1-*.txt`, not overwritten): 10 run, 0 failures,
1 error, exit 1. The three classes that carried the fix, re-verified above at 6, 9 and 11 tests,
match the fix run exactly.

Stderr (1355 bytes) holds only environment warnings — Mockito self-attach, Byte Buddy dynamic-agent
and JDK native-access warnings for `sqlite-jdbc`. No test or product error.

## 6. What the wired atomic path now does

`ProcessorHandler()` has a no-argument constructor bound to `AwsRuntime::processor`, so the deployed
Lambda entry point resolves to `processor()` and therefore to `atomicService(...)`. From this tree:

- `processor()` builds **one atomic graph**: `DynamoEventStore` over `EVENT_TABLE` with
  `OperationAManifest.manifest(capacity)` and `EventValidator`, plus the claim, dispatch-intent,
  result, network-result and late-evidence gateways and `IdentifierSource.randomHex()`.
- The legacy four-argument `AuthorizationService` is no longer constructed on the AWS runtime path.
  Its only remaining production construction is `LocalPresenterRuntime`, the Spring/JDBC local
  regression harness.
- Claim, dispatch intent, business result, network result and late evidence each commit their
  critical event in the same transaction as the business row/HEAD/dedupe/slot
  (`DynamoCriticalClaimIntegrationTest`, `DynamoEventStoreIntegrationTest`).
- Only a freshly committed intent sends; a recovered intent is knowledge-only and never resends
  (`recoveredDispatchIntentIsKnowledgeOnlyAndNeverResends`, `wiredDispatchIntentReplayGrantsNoSendPermission`).
- A failed intent writes no event and sends nothing; a failed claim is neither retried nor fallen
  back to a legacy claim (`failedDispatchIntentWritesNoEventAndSendsNothing`,
  `failedAtomicClaimIsNeitherRetriedNorFallenBackToTheLegacyClaim`).
- The wired graph serves APPROVED, issuer DECLINE and network rejection from persisted state, and a
  late-evidence approval admits without a second send or reversal.
- Configuration fails closed: `EVENT_TABLE`, `ENVIRONMENT`, `BOOT_INSTANCE_ID`,
  `CRITICAL_MAX_EVENTS_PER_OPERATION` and `CRITICAL_SLOT_BUDGET` have **no defaults**; each missing
  value throws at cold start naming the variable, and a slot budget too small for the manifest is
  rejected (`atomicRuntimeInputsFailClosedInsteadOfInventingDefaults`). The O8 capacity numbers are
  never chosen by the runtime.

## 7. What remains unproven

- **No integrated payment exists.** Every green case above is an in-process composition:
  `AwsRuntime.atomicService(...)` (or a gateway) driven against DynamoDB Local in a container with
  an in-process `StubIssuerSimulator`. No API Gateway, no real Lambda invocation, no real issuer or
  network endpoint, no TLS, no mTLS/OAuth `private_key_jwt`, no real key custody.
- `AwsRuntime.processor()` itself is **not executed** by the test: it reads process environment and
  the `CONFIG#ACTIVE` profile row. The test executes the identical composition through the
  package-private `atomicService(...)` seam. So wiring equality is proven by construction, and the
  production call chain is proven by source, not by executing `processor()`.
- The internal ISO route is still unmounted; ingress remains the JSON payload v2 path.
- Deadline persistence, the timeout **observation-event** vocabulary, and CommittedVersion evidence
  on every branch are not closed.
- History/query/cursor/expiry, Streams/notifier/WebSocket, console, ADOT, Terraform and CI remain
  absent.
- The full suite was **not** run here and is **deferred** by instruction. The baseline is still the
  959-test / 63-class run on `cfc456f`; this slice changes source bytes, so that historical pass
  does not apply to this tree without rerun.
- `AwsRuntime.processor()` itself is still never executed by a test, and the late-evidence
  `AuthorizationService` constructor's javadoc still claims "`AwsRuntime` is unchanged". That
  comment is stale at these bytes and was left unedited because any `src/main` change would
  invalidate the freeze.
- No independent QA, Security review or human acceptance is claimed. All traceability rows remain
  `PENDING`.

## 8. Direct answer

**Does a real integrated payment through the wired runtime now exist in this checkout? No.**

What exists is a **wired composition verified at test level**: the production handler resolves to a
service whose graph now consists of the atomic gateways and the event store, and that graph is
executed successfully against DynamoDB Local with a stub issuer. The step still unrealized is the
**deployed application journey** — a request arriving through the real authenticated boundary, the
real Lambda handler in an AWS runtime with real environment and registry, and a real issuer/network
round trip producing one confirmed payment with no second send. Until that runs, this is
component-and-composition evidence, not an integrated payment.

## 9. Non-claims

No Git write, stage, commit, push, PR, merge, deploy, release, publication, AWS call, credential
flow, provider mutation, approved-specification edit or invented capacity default occurred. No
onward delegation. This document is developer evidence for one frozen working tree.
