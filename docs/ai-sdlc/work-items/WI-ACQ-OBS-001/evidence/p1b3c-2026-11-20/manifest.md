# P1b3c evidence manifest — service-level dispatch-intent gateway

Date: 2026-11-20 America/Bogota. Owner: Fernando Paz.
Repository: `/home/pazfernando/projects/acquiring-simulator`
Branch: `feat/wi-acq-auth-001`
HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged; no Git write this turn)
Origin: `https://github.com/pazfernando/acquiring-simulator.git`
Model: provider `nan`, model `deepseek-v4-flash`; no override.
Toolchain: JDK 25 `/home/pazfernando/.local/opt/jdk-25`; Maven 3.10.1
`/home/pazfernando/.local/opt/maven/bin/mvn` (`JAVA_HOME` pinned in every command).

## Delivered files (SHA-256 of the current tree)

```
0eed43ff28b31840f66d5a643addd587cae83238d80191c2bf6f75450935b4d1  src/main/java/com/acquiring/simulator/domain/port/DispatchIntentGateway.java
b231c304f9a30c0c7d8a5a06b7085dcd1492dc7fe6ba8368b6c395432a71c75c  src/main/java/com/acquiring/simulator/adapter/aws/DynamoDispatchIntentGateway.java
aa2bfe92538c3db399e05023189a9b1a2d8967c01061cb821133fa6032955633  src/main/java/com/acquiring/simulator/domain/AuthorizationService.java
2085dab9892a42ce2e3f2f58f84b5829d9bc43c9ce68d52198f6db5aa7914de7  src/test/java/com/acquiring/simulator/aws/DispatchIntentServiceWiringTest.java
554b1461f7d70d9c93a0de7085bf160fda87add862cc22807180bd4feea179e4  src/test/java/com/acquiring/simulator/aws/DynamoDispatchIntentGatewayIntegrationTest.java
d4cd03fce1a82c39bf8855c2ac339c694977ec588d0ef82bcd04859d0b0d0817  src/main/java/com/acquiring/simulator/domain/event/SafeIdentifier.java
664ca1954b6faa11c2595e0dfc921b17e96629bc0a0d2272b2423b6d0b70fbf4  src/test/java/com/acquiring/simulator/domain/event/SafeIdentifierTest.java
7501c3ec87c2848118b5f9d8ec34d5bdcbfa97e355be8b25d2aaec08deef5bd0  src/test/java/com/acquiring/simulator/domain/GeneratedIdentifierSanitizerTest.java
1818d9bbac3f425d19d899d3fc00011065802e79f9fb94e8548dbf1960c22018  docs/ai-sdlc/work-items/WI-ACQ-OBS-001/implementation-progress.md
```

`hashes-after.txt` records the same set. `hashes-before.txt` is the pre-run capture of the nine
touched files; it differs only for files edited afterwards (the two test renames and the progress
record). `SafeIdentifier.java` is byte-identical to the P1b3b fix: no generator redesign.

## Commands and results

| Command | Log | Exit | Result |
| --- | --- | --- | --- |
| `mvn -o -q -DskipTests compile` | `compile.log` | 0 | quiet |
| `mvn -o -Dtest=...DispatchIntentServiceWiringTest test` | `service-test.log` | 0 | 9 tests, 0 F/E/S |
| `mvn -o -Dtest=...DynamoDispatchIntentGatewayIntegrationTest test` | `intent-adapter-test.log` | 0 | 4 tests, 0 F/E/S |
| `mvn -o test` | `full-test.log` | 0 | **174 tests, 0 F/E/S; BUILD SUCCESS** |

## Run binding

`hashes-run-before.txt` covers 102 `src/**` Java/SQL files plus `pom.xml` (103 lines).
`hashes-run-after.txt` is identical; `hashes-diff.txt` is empty. `git-status-before.txt` and
`git-status-after.txt` are identical; `git-status-diff.txt` is empty. The full-suite result is
therefore bound to the code revision that produced it. The P1b3c progress section was written after
the run and is documentation only.

## Send gate (what the tests prove)

- `COMMITTED` with persisted evidence: one downstream call, after the intent commit.
- `NOT_COMMITTED`, `UNKNOWN`, `CONTENT_CONFLICT`, `COMMITTED` without evidence: no downstream call.
- `REPLAY`, `RECOVERED`: knowledge recovery only, no second financial call.
- No outcome falls back to `repository.markDispatched` or a legacy event append.
- The atomic trail is CAS `version = 1`, `committedJournalVersion = 2`, event sequence 2,
  `stateAfter = POSSIBLE_SEND`, HEAD 2, slot 2.

## Documentation correction

Identifier normalization is **not** globally injective and does **not** guarantee uniqueness.
Normalization truncates the 32-hex candidate to the first 24 hex characters (at most 96 bits of
candidate material) and repairs over-long digit runs with `digit mod 6` letters, which can collide
with a naturally safe candidate. Test descriptions now state only the fixed-sample assertions that
execute. No generator change was made.

## Wiring status

`AwsRuntime.processor()` still uses the 4-arg `AuthorizationService`: legacy claim and legacy
dispatch. `DynamoDispatchIntentGateway` and the optional service branch are constructed by tests
only. Result coupling (confirmed outcome + terminal event atomicity) and runtime activation are not
implemented.

## Non-claims

Local implementation plus DynamoDB Local and in-memory service tests — not QA, Security review or
acceptance. DynamoDB service behaviour (throttling, Streams, IAM, encryption, TTL) remains
UNVERIFIED. No commit, push, PR, merge, deployment, cloud call, apply, credential flow or deletion
occurred.
