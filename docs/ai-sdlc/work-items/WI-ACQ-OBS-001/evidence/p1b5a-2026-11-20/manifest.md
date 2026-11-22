# P1b5a evidence manifest — atomic NETWORK_REJECTED composer + targeted tests

Date: 2026-11-20 America/Bogota. Owner: Fernando Paz.
Repository: `/home/pazfernando/projects/acquiring-simulator`
Branch: `feat/wi-acq-auth-001`
HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged; no Git write this turn)
Origin: `https://github.com/pazfernando/acquiring-simulator.git`
Model: provider `nan`, model `deepseek-v4-flash`; no override.
Toolchain: JDK 25 `/home/pazfernando/.local/opt/jdk-25`; Maven 3.10.1
`/home/pazfernando/.local/opt/maven/bin/mvn` (`JAVA_HOME` pinned in every command).

## Recovery determination

Sanitized history confirmed the commission was admitted but recorded no assistant result, no
`pendingInputs` and no `p1b5a` evidence directory. This turn did **not** assume prior work was done
and did **not** restart a broad inventory. Instead it verified the binding and inspected whether a
partial network composer already existed.

**Finding: the composer already existed** as an untracked file and compiled cleanly, but had no test
reference anywhere in the tree. It was therefore preserved unmodified (SHA-256
`b781020f...181a5dd` before and after) and the missing piece — its targeted tests — was added.

## Delivered files

```
b781020f383860d19e4dcb6e6b005bdd4e8c47a8140a48255a9ba6c09181a5dd  src/main/java/com/acquiring/simulator/adapter/aws/NetworkRejectedCriticalComposer.java   (pre-existing, unmodified)
edaa6b5b9448c15dc63e046268996a57e58395e053d6f79158cf67202a864236  src/test/java/com/acquiring/simulator/aws/DynamoNetworkRejectionTransitionIntegrationTest.java   (new this turn)
```

No pre-existing source, test, specification or work-item document was modified. `hashes-pre-test.txt`
and `hashes-post-test.txt` are identical (`hashes-diff.txt` empty, diff exit 0), and
`git-status-before-test.txt` / `git-status-after.txt` are identical: the test run wrote no source.

## Approved rule citations

Saved verbatim in `citations.md`: guide **NET-01**
(`docs/ai-sdlc/sources/acquiring_simulator_functional_guide.md:356`), requirements **R05**
(`requirements.md:41`), **R10** (`requirements.md:46`), **R22** (`requirements.md:58`), acceptance
**OBS-03** (`acceptance-scenarios.md:16`). No specification file was edited.

## Commands and results

| Command | Log | Exit | Result |
| --- | --- | --- | --- |
| `mvn -o -q -DskipTests compile` | `compile.log` | 0 | quiet; pre-existing composer compiles |
| targeted test, attempt 1 | `targeted-test-attempt1.{stdout,stderr}.log`, `.exit.txt` | 1 | 2 run, 1 failure |
| targeted test, attempt 2 | `targeted-test-attempt2.{stdout,stderr}.log`, `.exit.txt` | 0 | **2 run, 0 F/E/S; BUILD SUCCESS** |

Attempt 1 failure (preserved, not overwritten): `invalidEvidenceOrMissingDispatchIntentWritesNothing`
line 231 failed with *"Expecting code to raise a throwable"*. Root cause was a test defect, not a
composer defect: the method first built a **dispatched** intent for `AUTH-001`, so the later
"claim-only" precondition re-used an already-dispatched record and the constructor correctly
accepted it. The test was restructured to exercise the claim-only precondition **first**, then
dispatch and assert the invalid-evidence refusals. Attempt 2 is green.

## Test coverage (exactly the two requested scenarios)

1. `correlatedNetworkRejectionCommitsNetworkProvenanceWithoutIssuerDispatch`
   - valid correlated `NETWORK_REJECTED` evidence commits one transaction;
   - persisted record is `NETWORK_REJECTED` / `DecisionSource.NETWORK` /
     `NETWORK_REJECTION`, preserving `dispatchedAt`, `dispatchCorrelationId` and fingerprint;
   - three events, HEAD 3, slot `usedSlots` 3 of budget 8, journal version 3, event id
     `NETWORK_REJECTED-AUTH-001`;
   - **OBS-03**: every stored event payload is non-`ISSUER` actor and non-`issuer-decide` task; the
     rejection event is `PROCESSOR`/`business-persist` with the issued correlation;
   - CAS `(version=2, committedJournalVersion=3)` agrees with `readVersionEvidence`.
2. `invalidEvidenceOrMissingDispatchIntentWritesNothing`
   - claim-only record → refusal `undispatched-outcome`, no write;
   - after dispatch: uncorrelated, `APPROVED` and pre-dispatch `NETWORK_REJECTED` evidence each
     refused *before any write*; record remains `AWAITING`/`NONE`, `outcomeDeterminedAt` null,
     `(1, 2)` unchanged, still two events and two slots.

## Decision and limitation record

- The composer is unchanged and owns the fixed transition: journal version 3, state
  `NETWORK_REJECTED`, CAS pair `(1,2)`, `BUSINESS_PERSIST`/`SUCCEEDED`/`VALID`, `PROCESSOR` actor,
  event identity `NETWORK_REJECTED-<authorizationId>`.
- `NETWORK_REJECTION` is the composer's implementation constant; NET-01/OBS-03 do not name it. This
  is recorded as a citation conflict in `citations.md` and was not resolved by editing a spec.
- The composer remains unreferenced by `AuthorizationService` and `AwsRuntime`; no service coupling,
  result gateway, downstream call or runtime activation was added.
- Capacity is explicit (`withSlotAccounting`); the `(5, 8)` budget is a test example, not approved
  O8 capacity. No implicit default exists.

## Non-claims

Local implementation plus DynamoDB Local tests — **not** QA, Security review or acceptance. Runtime
remains **inactive** and G6 remains **incomplete**. DynamoDB service behaviour (throttling, Streams,
IAM, encryption, TTL) is UNVERIFIED. No full-suite run, no commit, push, PR, merge, deployment,
cloud call, apply, credential flow, deletion or approved-specification edit occurred.

## Next smallest slice

Service-level coupling for `NETWORK_REJECTED` (route an explicit correlated network rejection through
this composer and return the conservative `PROCESSOR_REJECTED` caller outcome), with `AwsRuntime`
still inactive — or the named ambiguous-result-after-possible-send service scenario. Each as its own
separately scoped change.
