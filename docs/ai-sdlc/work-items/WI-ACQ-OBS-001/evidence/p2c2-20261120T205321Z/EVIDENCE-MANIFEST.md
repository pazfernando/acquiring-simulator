# P2c2 recovery evidence manifest — server-resolved instruction mapper

Recovery of the aborted/no-pending P2c2 attempt. No result log existed in this
directory before recovery; `hashes-before`, `input-paths`, `java-version`,
`mvn-version` and `toolchain-version` were the only prior artifacts. This
recovery authored the missing targeted test (explicitly authorized) and ran the
one target class.

## Binding

- Work item: WI-ACQ-OBS-001, slice P2c2
- Branch: `feat/wi-acq-auth-001`
- HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged before/after)
- Origin: `https://github.com/pazfernando/acquiring-simulator.git`
- Model: `nan/deepseek-v4-flash`; `model_override=none`
- Toolchain: Temurin JDK 25.0.5 at `/home/pazfernando/.local/opt/jdk-25`,
  Maven 3.10.1 at `/home/pazfernando/.local/opt/maven/bin/mvn`
  (system `java` is absent; `JAVA_HOME`/`PATH` set explicitly)

## Delivered

- Existing (not rewritten): `ServerResolvedInstruction`,
  `ServerInstructionResolution`, `ServerResolvedInstructionMapper`,
  `InstructionMappingException`.
- New in this recovery: `ServerResolvedInstructionMapperTest` (3 cases only):
  1. `mapsNewDispatchToTheExactByteOracle` — complete packed body compared to a
     literal profile-composed byte string (1 exact-byte oracle assertion,
     0 roundtrip-body assertions);
  2. `refusesAuthorizedContextThatDoesNotMatchTheRequest` — `CONTEXT_MISMATCH`,
     message carries no caller/resolved value;
  3. `refusesNewDispatchThatExceedsTheDerivedExchangeBudget` — DE48 948 bytes →
     `VALIDATION_REJECTED` at `BUDGET` / `ADMISSION_RESPONSE_CAPACITY`.

## Result

`mvn -o -B -Dtest=ServerResolvedInstructionMapperTest test` → exit `0`,
`Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`, stderr empty, BUILD
SUCCESS. Only the target class ran; no full regression, HTTP, service, runtime,
registry allocation, dispatch or send.

## Integrity chain

- `hashes-after-edits.sha256` — 214 inputs (209 prior inputs + 4 mapper classes +
  new test), captured before the target run.
- `hashes-after-run.sha256` — byte-identical to `hashes-after-edits.sha256`
  (source stable across the run).
- `delivered-files.sha256` — new test file.
- `targeted-stdout.txt` / `targeted-stderr.txt` / `targeted-exit.txt`;
  `head-after-run.txt`; `git-status-after-run.txt`.

## Trust boundary

Mapper output is a mapping artifact only. No admission, authentication,
authorization, claim, dispatch, registry/fixture/mandate resolution, identifier
or alias allocation, counter increment, hashing, outcome, response/evidence,
DE38/DE39, C4 or HTTP. No Git write, publication, cloud or credential access,
deletion, specification/vector edit or new dependency.
