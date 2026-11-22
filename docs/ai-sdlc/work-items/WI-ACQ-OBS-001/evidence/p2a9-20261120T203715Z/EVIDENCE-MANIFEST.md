# P2a9 evidence manifest — one ordered response-validation pipeline

- Work item: WI-ACQ-OBS-001 (protocol slice).
- Phase: P2a9 — one pure ordered `0110` response pipeline (scanner → canonical
  DE48 response schema → domain → P4 → instruction/DE38 binding). Not
  authentication, attestation, business-conflict admission or a financial
  outcome.
- Run folder: `p2a9-20261120T203715Z`.
- Run start: `2026-11-20T20:37:15Z`.
- Repo: `/home/pazfernando/projects/acquiring-simulator`.
- Branch: `feat/wi-acq-auth-001`.
- HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged; no Git write).
- Origin: `https://github.com/pazfernando/acquiring-simulator.git`.
- Model: provider `nan`, model `deepseek-v4-flash`; `model_override=none`.
- Toolchain: Temurin JDK 25.0.5, Apache Maven 3.10.1
  (`toolchain-version.txt`). Runtime inactive. No service start, AWS call,
  HTTP, storage change, credential flow, publication, deletion or
  specification/vector edit.

## Approved rule applied

`iso8583-profile.md` P6 fixes the response order: structure/header/presence/
fields, DE48 schema, domain profile/amount/currency and `sent`/DE7, then the P4
matrix, then original instruction/context binding, exact authorization-reference
binding, then conditional business conflict/duplicate admission. The first two
steps of P6 — authentic sender/attestation verification when evidence is
submitted — are an explicit upstream precondition here, not implemented in this
slice.

The pipeline applies that order and returns one first-failure label. It applies
no NEW-request freshness window and no 826-byte request budget to a response, and
it does not fabricate an `0110` for a timeout.

## Delivered files (4 new; no pre-existing input edited)

| File | SHA-256 |
| --- | --- |
| `Iso8583ResponseValidationPipeline.java` | `39c7bb8aa38db1c0ba1656f3736892a244e962280b41f286c9e983fc52eeaba5` |
| `Iso8583ResponseValidation.java` | `7f4139d6a9c446541976f0fb907d72279a234acac4e51bc0a87ddbf790f3aa37` |
| `Iso8583ResponseValidationException.java` | `58fde49b296e3d64290c0f8493f7f4c5553f00cbf5ce40a773b59dfe584dc579` |
| `Iso8583ResponseValidationPipelineTest.java` | `9b2b31810a56396af6dd783cc6c9153762cf4594ed254abb228458605103bdb9` |

No scanner, parser, domain, P4, binding, profile, vector, fixture, resource or
P2a8 file was modified. The P2a8 delivered-file hashes still verify.

Behavior:

- `evaluate(body, original, trusted)` requires both trusted inputs. A missing
  one fails closed at `TRUST`/`TRUSTED_RECORD_REQUIRED`; a malformed trusted hash
  or DE38 assertion fails closed at `TRUST`/`TRUSTED_RECORD_INVALID`.
- MTI must be `0110`; a structurally valid `0100` is rejected as `PARSER_MTI`.
- The envelope carries no verified/accepted/authorized/authenticated/admitted
  flag and no freshness/budget field. It exposes no caller boolean.
- The original-wire hash and DE38 assertion are compared exactly as supplied;
  neither is derived from the received response.

## Deterministic tests (actual)

`Iso8583ResponseValidationPipelineTest`: **51/51**, failures 0, errors 0,
skipped 0, exit `0`, stderr empty. The 51 are 38 covered corpus cases plus 13
composition/scope cases.

Covered response corpus: **38 of 41** approved `0110` fixtures — 22 `ACCEPT`
and 16 ordered negatives (4 schema, 2 domain, 5 P4, 5 binding). All 38 rows
match in `response-first-failure-mapping.tsv`.

Explicitly deferred, not executed:

| Fixture | Declared later phase |
| --- | --- |
| `N-forged-actor` | `AUTH_ATTESTATION` |
| `N-unresolvable-evidence` | `AUTH_ATTESTATION` |
| `N-conflicting-confirmation` | `BUSINESS_CONFLICT_QUARANTINE` |

Protocol regression `Iso8583*Test`: **523/523**, failures 0, errors 0, skipped
0, exit `0`, stderr empty (`523 = 472 prior + 51 new`).

Focused ordering proofs use actual corpus bytes and are not restatements of the
individual layer tests: schema over domain, domain over P4/binding, P4 over
binding, instruction echo over DE38, and five repeated deterministic
evaluations.

## Integrity and status correspondence

- `hashes-before.sha256` (188 inputs, recovered P2a8 frozen list) →
  `hashes-frozen-pre-run.sha256` (192 inputs) differs only by the **four added
  files**; `hashes-p2a8-vs-p2a9.diff` contains only additions (exit `1` for the
  diff, as expected).
- `hashes-frozen-pre-run.sha256` and `hashes-after.sha256` are byte-identical
  (`9ac0526b…1173`); `hashes-frozen-vs-after.diff` is empty, exit `0`. The single
  post-freeze targeted and protocol runs therefore correspond to the frozen
  inputs.
- `git-status-frozen-pre-run.txt` = `git-status-after.txt` =
  `git-status-final.txt`; HEAD before and after is the binding above.
- `targeted-stdout.txt` records `Tests run: 51`; `protocol-regression-stdout.txt`
  records `Tests run: 523` and `BUILD SUCCESS`; both exit files contain `0` and
  both stderr files are empty.

## Preserved attempts (no artifact rewritten)

- `targeted-compile-failure-attempt1.txt` — the first test compilation failed on
  a nonexistent `List.join`. Production code was untouched; the one-line test
  fix produced `targeted-pre-freeze-attempt2.txt` (**51/51**).
- `compile-pre-freeze.txt` — pre-freeze main compile, exit `0`, no output.
- The post-freeze `targeted-stdout.txt`/`-stderr.txt`/`-exit.txt` and
  `protocol-regression-*` are the authoritative executions for the final tree.

## Boundary

Local JUnit and byte-fixture evidence only. P2a9 is **not** authentication,
attestation signature/evidence resolution, environment/producer/relay principal
handling, `responseSha256`/signing key binding, transport or durable processor
receipt, business-conflict/duplicate admission, registry/mandate/route/fixture
resolution, response construction, C4 carriage, HTTP/runtime behaviour,
independent QA, Security review or human acceptance. P1 and G6 remain open. No
Git write or publication was performed.
