# RESULTS — checkpoint of accepted run `7322ca1a`

Recorded 2026-11-22T01:50Z (local 2026-11-21). Local construction evidence only.
Not QA, Security review, acceptance, deployment or production activation.

Status: **checkpoint verified for the two in-scope items** (compile + affected tests +
projection runtime check). No unified signed replay harness was added.

## Two corrections needed to finish the partial edits

The preserved edits did not compile and did not satisfy the approved `USD`/`840` pair as
written. Both were finished minimally; neither weakens a comparison nor reverts prior work.

1. **Missing import.** `DynamoDispatchIntentGateway` referenced
   `OutboundInstructionMaterializer` without importing
   `com.acquiring.simulator.domain.port.OutboundInstructionMaterializer`; `test-compile`
   failed with two `cannot find symbol` errors. Added the import only.
2. **Projection rejected the ISO representation.** `BusinessCurrencyProjection.isoNumeric`
   accepted only `USD`, but the approved signed fixtures and the existing `AuthorizationRecord`
   fixtures persist `840`, so the composer threw `unsupported business currency` and the
   signed path was refused. It now accepts either approved representation (`USD` or `840`)
   and normalizes to `840`; every other value still throws. The composer's equality check
   is unchanged.

## Verification

| Check | Command | Iteration 1 | Iteration 2 (final) |
| --- | --- | --- | --- |
| Compile + test-compile | `mvn -o -DskipTests test-compile` | exit 1 (missing import) | **exit 0** |
| Affected tests | `mvn -o -Dtest=DynamoDispatchIntentGatewayIntegrationTest,DynamoPossibleSendIntegrationTest,EvidenceReceiptAtomicIntegrationTest,LateSignedEvidenceReceiptAtomicIntegrationTest,T3LocalApprovalRetryIntegrationTest -DfailIfNoTests=false test` | 19 run, 1 failure, 6 errors (currency projection) | **19 run, 0 failures, 0 errors, 0 skipped, exit 0** |
| Projection runtime check | `java -cp target/classes` on `BusinessCurrencyProjection` | — | **PASS** |

Per-class final result: `T3LocalApprovalRetryIntegrationTest` 2/2,
`LateSignedEvidenceReceiptAtomicIntegrationTest` 2/2, `EvidenceReceiptAtomicIntegrationTest`
4/4, `DynamoDispatchIntentGatewayIntegrationTest` 4/4, `DynamoPossibleSendIntegrationTest`
7/7.

Projection check: `USD→840`, `840→840`, and `EUR`, `usd`, `0840`, `null` all refused.
This covers the `USD` branch the existing fixtures do not exercise and confirms the
comparison was not widened. Logs: `currency-projection-check-*.txt`.

Iteration-1 logs are preserved as `*-failure-1.txt` (compile log was overwritten by the
final run; its failure is recorded above and in the checkpoint).

## Final source hashes (post-fix)

```
71b2c4ab233cac497168c63987e8f5af785896e4c36a615f7a1237597784a1ba  adapter/aws/DynamoDispatchIntentGateway.java
4c3b54cd47f4899b1e17b4ba835be8812b15dd46ee8e45ccbf6f3a1b3b64759f  adapter/aws/EvidenceReceiptCriticalComposer.java
37055dc4b93517f81eb36eb1146b22bba5cbab42591fd08c77c4787cbd83ec58  adapter/aws/PossibleSendCriticalComposer.java
e2ec2e3a952f1bfdcf696d2208107a1b9d839b8a36542fa13e2ad8f5ffbdb607  channel/BusinessCurrencyProjection.java
91f099f83f60edb1a50855ea54ae6d6d7a3fe423cf5425c5a9e73e110a6eb4d0  channel/SyntheticInstructionMaterializer.java
c7c87f75218507b83a9fc22939b82d7ecbe603dce3084f904daaf640a8df55c8  domain/port/OutboundInstructionMaterializer.java
```

`PossibleSendCriticalComposer`, `SyntheticInstructionMaterializer` and
`OutboundInstructionMaterializer` are unchanged since the checkpoint.

## What is verified

- **Original instruction before send (structural + compile).** The materializer runs before
  the transaction; its failure returns `NOT_COMMITTED` with no send permission; the exact
  bytes are written in the same `TransactWriteItems` call under the reader's shared
  `AUTH#<authorizationId>` / `ORIGINAL` key; `attribute_not_exists(pk)` prevents overwrite;
  the service sends only on `COMMITTED` + `permitsSend()`.
- **`USD`/`840` comparison.** Projection accepts only the approved pair and normalizes to
  `840`; signed currency must still equal it; the persisted field is never rewritten.

## Exact remaining gap

1. **No dynamic proof of original persistence yet.** Every existing `DynamoDispatchIntentGateway`
   construction (including the 4/4 affected test) uses the legacy 7-argument constructor, so
   the materializer branch and the `ORIGINAL` item have not been asserted against DynamoDB
   Local. `SyntheticInstructionMaterializer` is compiled but not executed by any test.
2. **No service-created `USD` → signed `840` composer assertion.** The `USD` projection is
   proven by the runtime check; the existing composer tests persist `840`, so the positive
   `USD` path through `EvidenceReceiptCriticalComposer` is not exercised end to end.
3. **No unified signed flow.** `SignedEvidenceReceiptGateway` is still not a `ResultGateway`;
   `AwsRuntime` still builds the legacy path; the presenter/access and full signed replay
   harness remain pending (deliberately not started until this checkpoint was verified).
4. Independent QA/Security review and human acceptance remain pending.

## Constraints honoured

No production activation, AWS call, console/access change, Git write, push, PR or
publication. The 11 pre-existing tracked modifications are unchanged; no prior dirty or
untracked work was reverted.
