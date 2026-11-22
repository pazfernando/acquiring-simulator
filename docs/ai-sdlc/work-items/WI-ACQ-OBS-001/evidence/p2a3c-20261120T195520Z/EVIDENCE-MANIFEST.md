# P2a3c evidence manifest — complete original-instruction response echo binding

- Work item: WI-ACQ-OBS-001 (protocol slice). Evidence is under OBS as commissioned.
- Phase: P2a3c — remaining echo fields of one original instruction, not the full P5 envelope.
- Date: 2026-11-20T19:55:20Z.
- Repo: `/home/pazfernando/projects/acquiring-simulator`.
- Branch: `feat/wi-acq-auth-001`.
- HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged; no Git write).
- Origin: `https://github.com/pazfernando/acquiring-simulator.git`.
- Model: provider `nan`, `deepseek-v4-flash`; no override.
- Toolchain: JDK 25.0.5 `/home/pazfernando/.local/opt/jdk-25`; Maven 3.10.1 `/home/pazfernando/.local/opt/maven/bin/mvn`.
- Runtime: inactive. No service start, AWS call, HTTP, storage change, credential flow or deletion.

## Delivered scope

The binding now compares the complete business/context/metadata echo that the
approved profile requires a response to repeat, plus the physical elements:

| Layer | Covered values |
| --- | --- |
| Trusted original-wire hash | caller-provided persisted SHA-256, never recomputed from re-encoded bytes |
| DE48 identity/context (unchanged) | `cid, aid, tid, att, acq, mid, term, pay, gen` |
| DE48 remaining metadata (new) | `fixture, cvm, crypto, risk, sent, prior` |
| Physical elements (new) | DE3, DE4, DE7, DE11, DE22, DE25, DE32, DE37, DE41, DE42, DE49 |
| DE38 (unchanged) | trusted assertion presence/absence and exact value; no minted code |

`v`/`op` are fixed canonical literals enforced by both schema parsers and are
not repeated. `prior` mirrors the original exactly: omitted original forbids an
echoed prior; supplied original requires an identical echo.

First-failure order is preserved: P4 matrix upstream, then the trusted hash,
then every instruction echo (identity/context, metadata, physical), then DE38.
A new amount mismatch therefore precedes an invalid authorization reference.

Only equality and the profile's zero-formatted 12-digit DE4 projection are
applied. No amount range/USD policy, calendar/DE7 consistency, registry or
mandate, freshness, 826-byte admission, attestation signature, full P5
environment/principal/evidence identity, or financial admission participates.

## Files changed

Application:

| File | SHA-256 |
| --- | --- |
| `Iso8583PhysicalInstruction.java` (new) | `75a9fdb9efcead3e09986744d7342d137c92ec84b4f5763754b9f415cbbcf67c` |
| `Iso8583OriginalInstruction.java` | `1858407471ee9cba0e6bb9ae9be6162e5f29919aeab87bc9345ee075d2bc506a` |
| `Iso8583InstructionBindingValidator.java` | `110cbf4e3e1bf15730e3ea9df38277033de649e76093228859df83df63ae79fc` |
| `Iso8583InstructionBindingDecision.java` (doc only) | `be0303f25eea8788ffe4cd6c4ae4781c7440dcc70e62618be9a23d7147924397` |
| `Iso8583InstructionBindingException.java` (unchanged) | `455fa932ba83a2157edcadf153f4de68bb1e992f2a243d62f8e8630ebdeccf2d` |

Tests:

| File | SHA-256 |
| --- | --- |
| `Iso8583InstructionBindingValidatorTest.java` | `eeb31869d384cef7716c5fbdef8352861590acbfe862291a2b4cd4cca15b3dbf` |
| `Iso8583InstructionBindingCorpusTest.java` (new) | `919d886599562f85ca53cff5f96a42a6f9ee4281814a3f17b73e23986c5c278f` |

No `pom.xml`, resource, oracle/vector, profile or specification file was edited.
`hashes-before.sha256` and `hashes-after.sha256` are identical (178 source,
test, resource, `pom.xml` and vector inputs; empty diff, `HASH_DIFF_EXIT=0`).

## Byte-corpus oracle

`Iso8583InstructionBindingCorpusTest` rebuilds each `0110` body from its
authoritative `hex`, then runs the real pipeline: scanner → canonical DE48
response schema → P4 matrix → binding. The original instruction and the trusted
DE38 assertion come from the explicit `evidence-fixtures.json` entry. The test
rehashes the fixture's exact `original0100Hex` and asserts it equals the
attested `instructionSha256`, so the caller-provided hash is the exact persisted
octets, not a re-encoding.

| Fixture | Expected layer | First failure |
| --- | --- | --- |
| `N-wrong-correlation` | `BINDING_INSTRUCTION` | `INSTRUCTION_FIELD_MISMATCH` / `cid` |
| `N-wrong-context` | `BINDING_INSTRUCTION` | `INSTRUCTION_FIELD_MISMATCH` / `acq` |
| `N-mismatch-amount` | `BINDING_INSTRUCTION` | `INSTRUCTION_FIELD_MISMATCH` / `de4` |
| `N-unsolicited-DE38` | `BINDING_AUTH_REFERENCE` | `AUTHORIZATION_REFERENCE_UNSOLICITED` / `de38` |
| `N-missing-DE38-asserted` | `BINDING_AUTH_REFERENCE` | `AUTHORIZATION_REFERENCE_MISSING` / `de38` |

Positive byte cases: `V10-actual-DE38` binds `AB1234`; `V02-approved-response`
binds with no DE38. For both, the test also asserts the response octets hash
equals the fixture's attested `responseSha256`. No `verified`/`trusted` flag
exists or is asserted.

## Commands and actual results

All logs are in this folder with unique names.

| Attempt | Command | Exit | Result |
| --- | --- | --- | --- |
| Compile 1 | `mvn -o -DskipTests test-compile` | 1 | failed: five malformed test helper calls (missing `ORIGINAL` argument). Logs `compile-stdout.txt`, `compile-stderr.txt`, `compile-exit.txt`; preserved. |
| Compile 2 | `mvn -o -DskipTests test-compile` | 0 | `BUILD SUCCESS`. Logs `compile-stdout-2.txt`, `compile-stderr-2.txt`, `compile-exit-2.txt`. |
| Targeted | `mvn -o -Dtest='Iso8583InstructionBindingValidatorTest,Iso8583InstructionBindingCorpusTest' -DfailIfNoTests=false test` | 0 | `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0` (8 unit + 7 corpus). |
| Protocol regression | `mvn -o -Dtest='Iso8583*Test' -DfailIfNoTests=false test` | 0 | `Tests run: 331, Failures: 0, Errors: 0, Skipped: 0`. `331 = 316 prior + 15 new` (25 FieldReader, 62 P4, 7 corpus, 74 response schema, 8 binding unit, 58 request schema, 94 scanner, 3 header). |

`targeted-stderr.txt` and `protocol-regression-stderr.txt` are empty. The
protocol regression was run once after the targeted run; no full suite was run.
`git-status-before.txt`, `git-status-after.txt` and `git-status-final.txt` are
identical (`STATUS_DIFF_EXIT=0`).

## Deferred / separate seams

- Full P5: environment, producer/relay principals, evidence identifier,
  attestation signature, `responseSha256` binding, signing principal/key,
  transport receipt, durable processor receipt.
- Domain: amount range/USD policy, currency, Gregorian date, DE7/`sent`
  consistency, fixture/registry resolution, mandate/context authorization.
- Exchange/freshness/timeout, 826-byte admission, packer, C4 carriage, HTTP
  route, production caller and runtime wiring.

## Non-claims

Local JUnit and byte-fixture evidence only. This is not authentication, issuer
truth, cryptographic verification, QA, Security review or human acceptance. No
full-codec, full-suite, admission or financial-outcome claim is made. P1 and G6
remain open; P4 remains a separate layer.
