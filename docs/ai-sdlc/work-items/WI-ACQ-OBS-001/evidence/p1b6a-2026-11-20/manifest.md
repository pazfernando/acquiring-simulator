# P1b6a manifest — deadline read disposition (documentation-only finalization)

Status: **IMPLEMENTED, TARGETED-TESTED (9/9) AND FULL-SUITE-TESTED (229/229) ON THIS SNAPSHOT;
UNWIRED — no production caller. G6 COMPLETE → NO; `AwsRuntime` still legacy/inactive.**
This manifest was written after the saved runs, from the retained files. No source edit, no test
re-run, no new suite, no Git write, no publication, no cloud/credential action, no deletion and no
specification edit occurred while producing it.

## 1. Binding (saved `binding.txt`)

| Field | Observed |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `cfc456f4f469d9558a78fda8ff9b770b00817315` |
| Origin | `https://github.com/pazfernando/acquiring-simulator.git` |
| Model | configured default `nan/deepseek-v4-flash`; no override |
| Toolchain | JDK 25.0.5 `/home/pazfernando/.local/opt/jdk-25`; Maven 3.10.1 `/home/pazfernando/.local/opt/maven` |

## 2. Delivered files (new; both untracked, `??`)

| SHA-256 (`new-files.sha256.txt`, recomputed) | File |
| --- | --- |
| `abd2df5a2aff8c6f2bc5af1aedd70acaf1e258a467e502a2b20e915347804b3e` | `src/main/java/com/acquiring/simulator/domain/DeadlineDisposition.java` |
| `ec22bbacf063e55d6376e137c44bc17b6fe933691f659465f89e93d733fa68d4` | `src/test/java/com/acquiring/simulator/domain/DeadlineDispositionTest.java` |

`DeadlineDisposition` is a record with phases `NOT_DISPATCHED`, `IN_FLIGHT`, `BOUNDARY_REACHED`,
`SETTLED`, `ALREADY_UNCERTAIN`, constant read-only predicates (`persistsTransition()`,
`advancesCommittedVersion()`, `appendsCriticalEvent()`, `consumesCriticalSlot()`,
`createsFinancialFinality()`, `permitsDispatch()` — all `false`), and
`evaluate(record, evaluatedAt, timeout)` with caller-supplied time (no wall clock, `DeadlineDisposition.java:65-99`).
`AuthorizationState` has no `POSSIBLE_SEND`/`CLAIMED_NOT_SENT` member (`AuthorizationState.java:3-9`); the
atomic persisted representation of a possible send is `AWAITING` **with** `dispatchedAt` set
(`PossibleSendCriticalComposer.java:300-305`), which is exactly the shape the helper classifies.

## 3. Actual commands, exits and results

| Command | Exit file | Exit | Result |
| --- | --- | --- | --- |
| `DeadlineDispositionTest` | `deadline-disposition-test.exit.txt` | 0 | **9 tests, 0 F/E/S**; `BUILD SUCCESS` 12:53:49 |
| targeted regression | `targeted-test.exit.txt` | 0 | **159 tests, 0 F/E/S**; `BUILD SUCCESS` 12:54:34 |
| `mvn -o test` | `full-test.exit.txt` | 0 | **229 tests, 0 F/E/S** in 29 classes; `BUILD SUCCESS` 12:55:32 |
| quiet `compile` | `compile.exit.txt` | 0 | empty stdout/stderr, stamped 12:55:45 |

Caveat: `compile.exit.txt` is stamped **after** the full suite (12:55:45 > 12:55:32), so it is a
post-run compile check, not a pre-run build step. The test commands compiled within their own Maven
lifecycle (`deadline-disposition-test.stdout.log`: `Recompiling the module because of changed source
code`, 33 test sources).

## 4. Run correspondence (which source the runs cover)

- Source writes: `DeadlineDisposition.java` 12:53:10, `DeadlineDispositionTest.java` 12:53:24.
- `hashes-pre.txt` 12:53:38.99 → 121 files, contains both hashes above (`hashes-pre.txt` lines for
  both files match §2).
- Runs then execute: 12:53:49 (9/9), 12:54:34 (159), 12:55:32 (229).
- `hashes-post.txt` 12:55:36.53 is **byte-identical** to `hashes-pre.txt`; `hashes-diff.txt` is
  0 bytes (exit 0). `git-status-before.txt` and `git-status-after.txt` are byte-identical (5260
  bytes each); `git-status-diff.txt` is 0 bytes (exit 0).
- Therefore the 9/9 and 229/229 runs are bound to `abd2df5a…` / `ec22bbac…` and nothing under
  coverage changed during the runs. Residual uncertainty: no source digest is embedded in the
  Maven logs; identity rests on the pre/post manifests, the digest diff and the timestamps.
- Not edited in this slice: `AuthorizationService` `ac2ceaeeca1fdb45e9ab70ce2d48e2ef50be687b791796926f432dd877594af4`
  (same as the P1b5c record) and `AwsRuntime` `99c32249f1b577dc0645ffed7e2751aa0c1edcdd1b51a22b92ebb639f0f36c7b`.

## 5. Pure read disposition vs persisted timeout transition — sourced resolution

### Approved rule = read disposition (no committed business transition)

| Citation | Text (exact) | What it fixes |
| --- | --- | --- |
| `channel-contract.md:17` | "Deadline 2 seconds from processor dispatch, inherited AWS A default; no automatic retries and no assumed harvesting of a timed-out invocation. Empty/missing body/timeout/non-200/invalid 0110 after possible dispatch => unknown absent previously confirmed evidence." | The timeout is an uncertainty statement, not a stored financial state. |
| `channel-contract.md:32` | "readable POSSIBLE_SEND without confirmation, healthy journal, evaluation still active … 202 NOT_CONFIRMED / PENDING / null | original IDs/version, never null payment outcome for admitted in-flight work" | The possible-send row stays readable while evaluation is active. |
| `channel-contract.md:33` | "readable POSSIBLE_SEND without confirmation at/after normal deadline | 200 | NOT_CONFIRMED / REPLAY for duplicate/history or COMPLETED for initial deadline response / null | original IDs/version; COMPLETED means this exchange ended, not payment finality" | Past the deadline the response carries the **original** IDs/version and null `businessVersion`; only the exchange is "COMPLETED". |
| `observation-contract.md:31` (O3) | "after possible send current invocation returns conservative NOT_CONFIRMED unless prior committed result readable … No auto payment resend, rollback, reversal or inquiry." | The conservative outcome is an invocation reply; no resend is authorized. |
| `observation-contract.md:76` (O8) | "Current A emits<=5 critical transitions, repeated evidence/timers duplicate no new transition." | Repeated timers add nothing; the budget is not consumed by repetition. |
| `acceptance-scenarios.md:19` (OBS-06) | "Processor NOT_CONFIRMED; … received task events do not prove payment result" | Displayed processor uncertainty is not issuer truth. |
| `acceptance-scenarios.md:20` (OBS-07) | "Preserve dispatch/failure provenance and UNKNOWN rather than decline; no trace-triggered retry/reversal is sent" | Provenance is preserved, not overwritten by a decline. |
| `acceptance-scenarios.md:23` (OBS-10) | "Apply evidence once; confirmed business state does not regress; timeout task observation may appear without replacing known result" | A timeout **task observation** is contemplated, but cannot replace a known result. |
| `requirements.md:43` (R07) | "…do not regress confirmed state to uncertainty." | Confirmed precedence over the boundary. |
| `vectors/state-vectors.json` S27 | "202 NOT_CONFIRMED PENDING, no redispatch; deadline response UNKNOWN" | The persisted/readable shape after a send crash remains possible-send PENDING. |
| `vectors/async-projection-vectors.json` A16 | "valid late evidence transaction … consumes own slot; no payment resend" | Late evidence is the later transition, not a timer transition. |

Consequence encoded in the helper: a deadline evaluation does not change the persisted state,
advance the committed version, append a critical event/HEAD/dedupe row, consume a slot, declare
payment finality or permit dispatch; only the caller-visible outcome is conservative `NOT_CONFIRMED`
(`DeadlineDisposition.java:10-34, 106-139`).

### Legacy persisted transition (still present and still runtime-reachable)

| Citation | Code | Effect |
| --- | --- | --- |
| `AuthorizationService.java:270-275` | `evaluateDeadline` when `resultGateway != null` | Read-only: returns `repository.findById(...)`; writes nothing. |
| `AuthorizationService.java:281-289` | `evaluateDeadline` legacy branch (`resultGateway == null`) | Persists `AWAITING → NOT_CONFIRMED` via `updateOutcome(... "NO_CONFIRMATION" ...)` and appends a legacy evidence event type `"TIMEOUT"`. |
| `DynamoAuthorizationRepository.java:89-95` | `updateOutcome` | Replaces the record state; does not guard `NOT_CONFIRMED` out (only APPROVED/DECLINED/NETWORK_REJECTED are frozen). |
| `DynamoAuthorizationRepository.java:96-111` | `mutate` | Bumps the storage CAS `version` by 1 per replacement; **copy-forwards** `committedJournalVersion` without advancing it. |
| `AwsRuntime.java:38` | `new AuthorizationService(4-arg …)` | The runtime still builds the legacy service, so the legacy persisted branch is the live shape. |

So: the approved-visible effect of the legacy path is a readable `NOT_CONFIRMED` business state (and
a legacy `TIMEOUT` evidence row), while the atomic branch and the new helper describe a read-only
disposition that keeps `AWAITING`+`dispatchedAt` readable. The helper does **not** change the legacy
path.

## 6. Which code references the helper — UNWIRED

A fresh `src/main` + `src/test` scan for `DeadlineDisposition` (saved as `source-reference-scan.txt`)
returns hits only in `DeadlineDisposition.java` itself and `DeadlineDispositionTest.java`. There is
**no** reference from `AuthorizationService`, `AwsRuntime`, any adapter or any service branch. The
atomic `evaluateDeadline` branch returns `repository.findById(...)` directly and does not consult the
helper (`AuthorizationService.java:271-275`). Therefore:

- helper: **unused by production code**; tested as a pure unit;
- service: **not wired** to the helper; legacy write path unchanged and still the runtime path.

## 7. Approved-vs-legacy distinction: exact unresolved portion (no invented rule)

The approved texts above describe the deadline **response**. They do not explicitly settle the
following, so none of it is adopted as a business rule by this slice:

1. **Whether the first deadline evaluation may commit a `NOT_CONFIRMED` business state.** No
   approved clause forbids or requires the legacy `AWAITING → NOT_CONFIRMED` write;
   `observation-contract.md:76` ("repeated evidence/timers duplicate no new transition") can be read
   as allowing a first timer-observed transition. "Always read-only" is an inference from
   `channel-contract.md:33` / `observation-contract.md:31`, not an explicit prohibition.
2. **What a "timeout task observation" is.** `acceptance-scenarios.md:23` permits it, but
   `EventTask` declares no timeout/timeout-task member (`EventTask.java:8-18`) and `EventReason` has
   only `NO_RESPONSE` with no owning timeout task (`EventReason.java:8-24`). No approved text assigns
   a task, reason, criticality (critical vs best-effort) or sequence rule, so a canonical timeout
   event cannot be composed without inventing vocabulary.
3. **Disposition of already-persisted legacy `NOT_CONFIRMED` rows.** The helper reads them as
   `ALREADY_UNCERTAIN` without a second transition (`DeadlineDispositionTest.java:133-143`), but no
   approved text states whether such rows are retained as legacy evidence, normalized, or retired.
4. **Channel `businessVersion` for a legacy-bumped row.** `channel-contract.md:33` requires null for
   the boundary reply and `channel-contract.md:21` requires `businessVersion` to be a positive
   committed integer; the legacy write bumps only the storage CAS `version`
   (`DynamoAuthorizationRepository.java:102-104`). No approved text maps a bumped legacy CAS to a
   channel version — `committedJournalVersion` is not advanced.

No business rule, capacity value, fixture or specification was changed to close these gaps.

## 8. Non-claims

G6 is not complete. P1b6a is local implementation plus unit/full-suite testing on this snapshot only
— not QA, Security review or human acceptance. DynamoDB service behaviour (throttling, Streams, IAM,
encryption, TTL, concurrency) remains UNVERIFIED. The 229/229 pass is evidence for
`abd2df5a…`/`ec22bbac…`, not a standing proof for later edits. No commit, push, PR, merge, deploy,
cloud call, apply, credential flow, deletion or approved-specification edit occurred; this manifest
and the progress note are documentation-only.
