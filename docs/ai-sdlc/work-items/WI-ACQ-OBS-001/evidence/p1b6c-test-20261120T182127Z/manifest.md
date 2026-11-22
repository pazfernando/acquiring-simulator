# P1b6c admission-gateway test authoring + targeted execution (2026-11-20)

Binding (verified before and after):

- repository `/home/pazfernando/projects/acquiring-simulator`
- branch `feat/wi-acq-auth-001`
- HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`
- origin `https://github.com/pazfernando/acquiring-simulator.git` (no origin write)
- model provider `nan`, model `deepseek-v4-flash` (configured default, no override)

## Artifact

New file only:
`src/test/java/com/acquiring/simulator/aws/DynamoLateEvidenceAdmissionGatewayIntegrationTest.java`
sha256 `ee2e5e29a1aacbbca51cca954f132aff6a305c9710a4b899eff50f28c45f2b15`
(unchanged before/after the run: `new-test.sha256.txt` == `new-test-after.sha256.txt`).

No main-source, spec, runtime, service, crypto or capacity-default edit was made.

## Command (single attempt, absolute JDK25/Maven)

```
export JAVA_HOME=/home/pazfernando/.local/opt/jdk-25
export PATH="$JAVA_HOME/bin:/home/pazfernando/.local/opt/maven/bin:$PATH"
cd /home/pazfernando/projects/acquiring-simulator
mvn -o -Dtest=DynamoLateEvidenceAdmissionGatewayIntegrationTest test
```

`java -version`: OpenJDK 25.0.5 (Temurin) — see `java-version.txt`, `mvn-version.txt`.

## Result

- exit 0 (`run1.exit.txt`)
- stdout `run1.stdout.log`, stderr `run1.stderr.log` (0 bytes)
- `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS
- surefire `surefire-report.txt` / `surefire-report.xml` (tests=5, errors=0, failures=0, skipped=0)

Test methods and the contract each pins:

1. `newLateEvidenceCommitsThenAFreshReadReplaysWithoutASecondWrite` — COMMITTED, then a
   *different* gateway instance re-admits identical evidence: REPLAY, no second sequence
   (HEAD stays 3), no second slot (usedSlots stays 3), original receipt id retained even when a
   different delivery receipt id is supplied.
2. `unauthorizedCandidatesAreRefusedBeforeAnyDedupeLookup` — a MALFORMED decision and a foreign
   correlation are REFUSED with zero `EventStore.find` calls (recording wrapper) and no write;
   control: the same wiring reaches exactly one lookup and REPLAYs for an authorized candidate.
3. `contradictoryTerminalEvidencePreservesTheOriginalResult` — a contradicting DECLINED late
   candidate against a retained APPROVED result is CONTENT_CONFLICT; original state, receipt,
   2/3 committed version, 3 event rows and 3 slots are unchanged and no DECLINED event exists.
4. `ambiguousCommitRecoversByOneLookupAndAnUnreadableRecordStaysUnknown` — committer performs a
   real append then throws `SdkClientException` (lost response): RECOVERED with 3 rows / 3 slots /
   2-3 evidence, no resend; a repository bound to a missing table returns UNKNOWN with a detail,
   and changes nothing. UNKNOWN is never reported as no-result.
5. `evidenceAvailableByTheDeadlineIsNormalAdmissionAndWritesNothing` — evidence at the deadline,
   delivered later, is DELIVERY_DELAY and NORMAL_RESULT_ADMISSION: 2 rows / 2 slots, AWAITING,
   no late receipt.

## Preservation evidence

- `hashes-pre.txt` / `hashes-post.txt`: 120 tracked+untracked Java files, identical
  (`hashes-diff.txt` 0 bytes).
- `git-status-pre.txt` / `git-status-post.txt` identical (`git-status-diff.txt` 0 bytes); the only
  new entry versus HEAD remains this test class plus the pre-existing uncommitted work.

## Trust seam (unchanged, internal only)

`DownstreamReply` is injected directly; the gateway performs context authorization against the
durable record (dispatch correlation, issuer decision, evidence/delivery/deadline ordering, receipt
bound). It does NOT verify C4 envelopes/signatures/keys, P6 ISO structure or attestations. The green
run is not a C4 authentication claim and exercises no public endpoint, HTTP route,
`AuthorizationService` branch or `AwsRuntime` activation.

## Non-claims

Local DynamoDB Local-only slice test; not QA, Security review or human acceptance. O8 capacity
numbers remain PROPOSED (`CriticalCapacity(5, 8)` is a test example, no default introduced). No full
suite / regression sweep was run. No commit, push, PR, merge, deploy, cloud call, apply, credential
flow, deletion or approved-specification edit occurred.

## Next smallest slice

Still-unwired gateway is now covered for its admission outcomes. The smallest remaining
no-new-vocabulary tests are the preserved/unknown edges already implemented: confirmed record with
an absent late receipt (CONTENT_CONFLICT) and confirmed record with a missing result event (UNKNOWN).
Wiring the gateway into `AuthorizationService`/`AwsRuntime` (or the legacy `NOT_CONFIRMED`
disposition) needs an approved-text/architecture decision and is not in this slice.
