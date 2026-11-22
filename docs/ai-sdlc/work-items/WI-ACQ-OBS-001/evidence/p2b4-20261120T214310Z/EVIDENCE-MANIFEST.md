# P2b4 evidence manifest — signed-P5 / P6 field binding

## Binding

- Work item: `WI-ACQ-OBS-001` (P2b4 signed-P5 / P6 field binding)
- Repo: `/home/pazfernando/projects/acquiring-simulator`
- Branch: `feat/wi-acq-auth-001`
- HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315` (expected and observed; unchanged)
- Origin: `https://github.com/pazfernando/acquiring-simulator.git` (configured default
  preserved, no override)
- Model: provider `nan` / `deepseek-v4-flash`, configured default, no override
- Toolchain: Temurin JDK 25.0.5 (`/home/pazfernando/.local/opt/jdk-25`), Maven 3.10.1
  (`/home/pazfernando/.local/opt/maven/bin/mvn`), offline (`-o`)
- Runtime: inactive; no AWS/cloud call, HTTP, credential flow, storage change or
  private-key export

## Delivered (new files only; no existing file edited)

| File | Purpose |
| --- | --- |
| `channel/C4P6SignedFieldBinder` | pure signed-P5/P6 field binder |
| `channel/C4P6BindingDecision` | bound signed claims + explicit scope; no confirmed flag |
| `channel/C4P6BindingException` | fixed sanitized category/component/source rule |
| `channel/C4P6BindingViolation` | closed failure vocabulary |
| `test/.../C4P6SignedFieldBinderTest` | 18 cases over the approved byte corpus |

## Inputs

`C4VerifiedAttestation` or `C4VerifiedLateDelivery` from the real verifier; the exact signed
`C4EvidenceAttestation` content whose canonical hash the verified result names; the current
`C4AuthenticatedTransportContext`; the immutable persisted original `0100` octets; the exact
received `0110` octets. No caller-asserted signature/verified flag and no arbitrary
trusted-evidence DTO is accepted.

## Executed results

| Command | Result | Exit |
| --- | --- | --- |
| `mvn -o -Dtest=C4P6SignedFieldBinderTest test` | **18 run, 0 failures, 0 errors, 0 skipped** | 0 |
| `mvn -o -Dtest=C4EvidenceVerifierTest,C4LateDeliveryTest,C4CarrierParserTest,C4EvidenceAttestationSchemaTest test` | **80 run, 0/0/0** (`C4EvidenceVerifierTest` 33, `C4LateDeliveryTest` 23, `C4CarrierParserTest` 18, `C4EvidenceAttestationSchemaTest` 6) | 0 |

Both stderr files are empty. No failing attempt exists in this slice. The targeted class
ran first; the affected C4 regression ran once afterwards.

### 18 executed binder cases (all PASS)

1. `directPositiveBindsTheCompleteSignedFieldSetToExactBytes`
2. `directPositiveBindsAnActualDe38Reference`
3. `relayPositiveBindsBothRealSignaturesToExactBytes`
4. `tamperedResponseBytesAreRejectedByTheExactHash`
5. `tamperedPersistedInstructionBytesAreRejectedByTheExactHash`
6. `echoedCorrelationMismatchFailsInstructionBindingAfterP4` (`cid`)
7. `unsolicitedDe38FailsExactAuthorizationReferenceBinding` (`AUTHORIZATION_REFERENCE_UNSOLICITED`)
8. `missingAssertedDe38FailsExactAuthorizationReferenceBinding` (`AUTHORIZATION_REFERENCE_MISSING`)
9. `domainFailureWinsBeforeP4AndInstructionBinding` (`sent`, `INVALID_GREGORIAN_DATE`)
10. `signedDeclineCannotBindToAnApprovedResponse` (`SIGNED_RESULT_MISMATCH`, `DE39`)
11. `signedAmountCannotBindToADifferentOriginalAmount` (`SIGNED_INSTRUCTION_MISMATCH`, `de4`)
12. `signedAvailabilityMustEqualTheResponseAtSeconds`
13. `subSecondAvailabilityIsRejectedRatherThanTruncated`
14. `signedReferenceCannotBindToDifferentDe38Bytes` (`AUTHORIZATION_REFERENCE_MISMATCH`)
15. `currentEnvironmentSubstitutionIsRejected`
16. `currentPrincipalSubstitutionIsRejected`
17. `verifiedResultCannotBePairedWithDifferentSignedContent`
18. `bindingDecisionClaimsNoConfirmedResultBeforeAtomicReceipt`

Positives use real ephemeral Ed25519 keys; negatives alter exactly one boundary after a
successful verification.

## Freeze and integrity

- `hashes-frozen-pre-run.sha256` == `hashes-after.sha256`: **254/254** inputs
  (`src/main`, `src/test`, `pom.xml`, OBS `vectors/`, `channel-contract.md`,
  `iso8583-profile.md`); `hashes-diff.txt` records `HASH_DIFF_EXIT=0`.
- `delivered-files.sha256` re-verifies the five new files (`DELIVERED_VERIFY_EXIT=0`).
- `git-status-before.txt` == `git-status-after.txt` (`STATUS_DIFF_EXIT=0`); HEAD unchanged.
- `evidence.sha256` / `evidence-verify.txt` cover this folder's artifacts.

## Trust limits

Environment/principal/key/acquirer selection is re-bound to the current trusted context, but
the registry mandate is **not** re-resolved here. Key custody/rotation, TLS/IAM principal
mapping, durable processor receipt, duplicate/conflict resolution, lateness deadline, atomic
admission, the financial outcome, HTTP, storage and runtime remain deferred. The decision
carries signed claims only; no result is confirmed before the atomic receipt transaction and
no identifier is minted. P1 and G6 remain open. Local construction evidence only — not
independent QA, Security review or human acceptance.
