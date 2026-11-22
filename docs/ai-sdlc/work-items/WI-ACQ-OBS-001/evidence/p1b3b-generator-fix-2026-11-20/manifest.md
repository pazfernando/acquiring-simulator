# P1b3b — generated-identifier sanitizer safety (evidence)

Date: 2026-11-20 (America/Bogota). Binding: `/home/pazfernando/projects/acquiring-simulator`,
branch `feat/wi-acq-auth-001`, HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged; no commit).
Configured model provider `nan`, model `deepseek-v4-flash`; no override. Toolchain:
JDK 25.0.5 `/home/pazfernando/.local/opt/jdk-25`, Maven 3.10.1
`/home/pazfernando/.local/opt/maven/bin/mvn`, both invoked by absolute path with `JAVA_HOME`
set per command (no reliance on `PATH`).

## Failure being fixed (preserved, not re-run)

`evidence/p1b3a-current-verification-2026-11-20/`: full suite **154 tests, 0 failures, 1 error,
exit 1**. Error: `AtomicClaimServiceWiringIntegrationTest` →
`EventValidationException: PROHIBITED_CONTENT at correlationId`. `randomId()` emitted 24 hex
characters; a 13–19 digit run (bounded by hex letters) matches `EventSanitizer.PAN_SHAPE`.
The failed run is kept as-is; it was not re-executed unchanged.

## Deterministic reproduction (now an executed test)

`SafeIdentifierTest.knownDangerousGeneratorOutputIsRejectedBeforeRepairAndAcceptedAfter` asserts
that the fixed candidate `ab1234567890123cdef01234` (13-digit run, the observed shape) is rejected
by `EventSanitizer.requireSafe("correlationId", "CORR-" + candidate)` and that the normalized
identifier passes. `GeneratedIdentifierSanitizerTest` repeats the proof through the service with an
injected `IdentifierSource`, so the dangerous input is fixed rather than probabilistic.

## Change

- `domain/IdentifierSource` — injectable seam for the service's own identifier candidates;
  production default is 128 random bits as 32 lowercase hex characters.
- `domain/event/SafeIdentifier` — keeps the first 24 lowercase hex characters and caps every digit
  run at 12 by replacing only the character that would extend the run past the limit with a
  hexadecimal letter derived from that digit. Contract alphabet/length (24 hex) and all other
  characters are preserved; already-safe candidates are returned unchanged.
- `domain/AuthorizationService` — optional `IdentifierSource` constructor (existing 4-arg and
  5-arg constructors unchanged, delegating to the random source); `randomId()` now returns
  `SafeIdentifier.normalize(...)`. `AUTH-`/`CORR-`/`EVT-` prefixes and all existing references
  are unchanged.
- `EventSanitizer` was **not** modified (no relaxation). No user-supplied identifier is rewritten.

Tests added: `SafeIdentifierTest` (6) and `GeneratedIdentifierSanitizerTest` (1).

## Commands, exits and results (stdout+stderr captured)

| # | Command (JAVA_HOME pinned) | Log | Exit | Result |
| --- | --- | --- | --- | --- |
| 1 | `mvn -o -q -DskipTests compile` | `compile.log` | 0 | quiet |
| 2 | `mvn -o -Dtest=SafeIdentifierTest,GeneratedIdentifierSanitizerTest test` | `targeted.log` | 0 | 7 tests, 0 failures/errors/skips |
| 3 | `mvn -o test` (after freeze) | `full-test.log` | 0 | **161 tests, 0 failures, 0 errors, 0 skipped** |

`AtomicClaimServiceWiringIntegrationTest` in run 3: 6 tests, 0 failures/errors/skips.
161 = 154 prior + 7 new, so the previously failing case is included and green.

## Freeze and correspondence

`hashes-before.txt` / `hashes-after.txt` cover 105 files (`src/main` + `src/test` Java/SQL/
properties/YAML resources, `pom.xml`, `infra/template.yaml`, `scripts/render_aws_fixtures.py`),
taken immediately before and immediately after run 3; `diff` is empty (`HASHES_UNCHANGED`), so the
161-test result is bound to exactly the tested source. `source-hashes.txt` lists the five touched
files:

| File | SHA-256 |
| --- | --- |
| `domain/IdentifierSource.java` | `9e18f5a8…31537b68` |
| `domain/event/SafeIdentifier.java` | `d4cd03fc…0d0817` |
| `domain/AuthorizationService.java` | `10b981d6…4ffded` |
| `domain/event/SafeIdentifierTest.java` | `ef7bbd93…c6b469` |
| `domain/GeneratedIdentifierSanitizerTest.java` | `0b01aa81…f13959` |

`git status --porcelain` grew by exactly one entry (`src/main/java/.../domain/IdentifierSource.java`);
the other new files live inside already-untracked directories (`domain/event/`,
`src/test/java/.../domain/`). All pre-existing edits were preserved; nothing was staged, reverted or
deleted.

## Assertions in the regression

- Actual generated `AUTH-…`, `CORR-…` and `EVT-…` values from `AuthorizationService.authorize`
  pass `EventSanitizer.requireSafe` (PAN, PIN-block, Bearer/JWT heuristics remain live and are
  positively re-checked as still rejecting).
- Bounded schema: each identifier matches `(AUTH|CORR|EVT)-[0-9a-f]{24}` and is ≤64 characters
  (`EventValidator.requiredText` bound).
- Entropy/uniqueness: a safe candidate passes through byte-identical; a dangerous candidate differs
  in exactly the one repaired position (23 of 24 characters retained), the digit run is ≤12, all
  identifiers in the run are distinct, and 16 hexadecimal variants after the repair point map to 16
  distinct identifiers. This is a construction, not a sampled collision estimate.

## Non-claims

Local implementation and tests only. Not QA, Security review or acceptance. DynamoDB service
behaviour remains UNVERIFIED (emulator only). `AwsRuntime` remains inactive and G6 is not complete.
No send-path feature work, Git write, publication, cloud call, apply, credential flow, deletion or
specification change occurred this turn. The previous 154-test failure is treated as a real defect
in the generator, now deterministically covered; no claim is made that the original random failure
is reproducible on demand beyond the fixed shape reproduced in the tests.
