# Checkpoint — unified local signed purchase follow-up (accepted run `7322ca1a`)

Written 2026-11-22T01:43:50Z (local 2026-11-21). Local construction evidence only.
Not QA, Security review, acceptance, deployment or production activation.

## Run identity

| Item | Value |
| --- | --- |
| Accepted run | `7322ca1a-dd25-4c7d-ac01-452833a8c1f5` (interrupted, no pending input; resumed, not restarted) |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `cfc456f4f469d9558a78fda8ff9b770b00817315` |
| Model | `provider nan / deepseek-v4-flash` (OpenCode ACP default, no override) |
| Toolchain | JDK 25.0.5 Temurin `/home/pazfernando/.local/opt/jdk-25`; Maven 3.10.1 `/home/pazfernando/.local/opt/maven/bin/mvn`; offline (`-o`) |
| Runtime | local tests only; DynamoDB Local container; no AWS, deploy, credential flow or Git write |

## Scope of this checkpoint

Only the two target items of the accepted run are in scope:

1. Original outbound instruction persisted **before** any dispatch/send.
2. Approved `USD` ↔ ISO `840` comparison at the signed-evidence receipt cross-check,
   only if already present in the preserved partial edits.

The full signed replay harness, runtime activation, presenter UI and access changes
remain out of scope for this checkpoint. No prior dirty change was reverted.

## Preserved partial edits (inspection)

Snapshot `source-hashes-before.sha256` was taken before the interrupted edits. Exactly
three snapshotted source files now differ; three files were added after the snapshot.
No other snapshotted source file changed.

| File | Snapshot SHA-256 | Current SHA-256 | State |
| --- | --- | --- | --- |
| `adapter/aws/DynamoDispatchIntentGateway.java` | `b231c304…a71c75c` | `89649f28…fad30a035` | changed |
| `adapter/aws/EvidenceReceiptCriticalComposer.java` | `503fce05…0acdac1a` | `33dfa4b7…f3eeccd1` | changed |
| `adapter/aws/PossibleSendCriticalComposer.java` | `b9a2579f…9afeca2` | `37055dc4…d83ec58` | changed |
| `channel/BusinessCurrencyProjection.java` | absent | `049491dd…5c7517` | added |
| `channel/SyntheticInstructionMaterializer.java` | absent | (see RESULTS hashes) | added |
| `domain/port/OutboundInstructionMaterializer.java` | absent | `c7c87f75…8df55c8` | added |

`DynamoOriginalInstructionReader.java` is byte-identical to the snapshot
(`5ef6b7f6…8c60450f`); its shared key scheme was reused, not rewritten.

## Verification of item 1 — original instruction before send

- `DynamoDispatchIntentGateway` gained an optional `OutboundInstructionMaterializer`.
  When injected, `dispatchIntent` materializes the exact `0100` octets **before** the
  transaction; a `null`/empty instruction or a materialization exception returns
  `NOT_COMMITTED` with no commit and no send permission.
- `PossibleSendCriticalComposer` persists the octets in the **same**
  `TransactWriteItems` call as the business transition, critical event, HEAD/dedupe and
  optional slot, under the reader's shared key scheme
  `AUTH#<authorizationId>` / `ORIGINAL`, attribute `original0100`, with
  `attribute_not_exists(pk)` so a first instruction is never overwritten.
- `AuthorizationService.dispatchCommitted` sends only in the `COMMITTED` branch after
  `permitsSend()`; `REPLAY/RECOVERED` never resend and `CONTENT_CONFLICT/NOT_COMMITTED/
  UNKNOWN` never send. The persisted original is therefore durable before any downstream
  call on the atomic path.
- `SyntheticInstructionMaterializer` reuses the approved `ServerResolvedInstructionMapper`
  and derives synthetic aliases/STAN/RRN deterministically from persisted claim values;
  it is a simulator-local allocation seam, explicitly not the approved durable T2
  alias/STAN/RRN allocation, and is not constructed by any production composition root.

## Verification of item 2 — `USD` ↔ `840` comparison

- `BusinessCurrencyProjection` maps **only** `USD` → `840` and `840` → `USD`; every other
  value throws. It changes no persisted field, attestation or ISO field.
- `EvidenceReceiptCriticalComposer` now compares the signed decision currency to
  `BusinessCurrencyProjection.isoNumeric(intent.currency())` instead of comparing
  `840`-vs-`USD` directly. The equality check is preserved, not weakened: a persisted
  non-USD currency throws, and a signed currency other than `840` still fails equality.
- This closes the specific mismatch recorded in
  `../t3-local-approval-retry-20261121/BLOCKER.md` §3 for the composer. It is an
  implementation projection derived from `channel-contract.md` C1 and
  `iso8583-profile.md` P1/P5, **not** a new owner decision, and it does not rewrite the
  legacy persisted record.

## Not yet verified / remaining at checkpoint time

- Narrow compile and affected tests had **not** been run when this checkpoint was written;
  results are appended in `RESULTS.md` and the `*.txt` logs in this directory.
- No test yet dynamically proves the persisted original is readable through
  `DynamoOriginalInstructionReader` after a dispatch-intent commit; that assertion belongs
  to the pending full signed replay harness.
- No unified service-created signed flow exists yet: the signed receipt gateway is still
  not a `ResultGateway` and `AwsRuntime` still builds the legacy path.
- `SyntheticInstructionMaterializer` is not wired into any runtime/tests.
- Independent QA/Security review and human acceptance remain pending.

## Constraints honoured

No production activation, AWS call, console/access change, Git write, push, PR or
publication was performed. Prior dirty/untracked work was preserved.

## Post-checkpoint outcome

This file records the state at checkpoint time. See `RESULTS.md` for the final outcome:
the partial edits needed a missing import and acceptance of the ISO `840` representation,
after which compile passed and all 19 affected tests passed (exit 0). The final source
hashes are in `source-hashes-final.sha256`; the hashes above are the pre-fix checkpoint
values and are retained as history. The exact remaining gap is listed in `RESULTS.md`.
