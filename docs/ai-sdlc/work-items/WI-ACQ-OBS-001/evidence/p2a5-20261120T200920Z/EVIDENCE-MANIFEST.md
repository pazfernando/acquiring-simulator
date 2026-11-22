# P2a5 evidence manifest — real packer byte-exact oracle

- Work item: WI-ACQ-OBS-001 (protocol slice).
- Phase: P2a5 — deterministic structural packer verified against the approved
  byte oracle. Record of the already-saved run; **no test was rerun and no
  artifact was recreated to write this manifest**.
- Run timestamp: `2026-11-20T20:09:20Z`; evidence folder
  `p2a5-20261120T200920Z`.
- Repo: `/home/pazfernando/projects/acquiring-simulator`.
- Branch: `feat/wi-acq-auth-001`.
- HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged; no Git write).
- Origin: `https://github.com/pazfernando/acquiring-simulator.git`.
- Model: provider `nan`, model `deepseek-v4-flash`; `model_override=none`.
- Toolchain: JDK 25.0.5 (Temurin) and Maven 3.10.1 at the recorded local
  prefixes; runtime inactive. No service start, AWS call, HTTP, storage change,
  credential flow, publication or deletion.

## Oracle counts (actual corpus)

| Measure | Count | Meaning |
| --- | --- | --- |
| `vectors` in `packed-vectors.json` | 85 | whole approved corpus |
| `expected=ACCEPT` request fixtures | 31 | packed byte-exactly |
| canonical P4 response shapes | 5 | `EVIDENCE_MATRIX`, packed structurally |
| byte-exact bodies exercised | 36 | 31 + 5 |
| explicit `@Test` methods | 17 | boundary/negative cases |
| targeted `Iso8583PackerTest` run | 53 | 31 parameterized + 5 parameterized + 17 unit |
| protocol `Iso8583*Test` run | 407 | 354 prior + 53 new |

Inventory is asserted in-test: `oracleInventoryIsTheApprovedThirtyOnePlusFiveResponses`
pins `ACCEPT=31` and the five response IDs. The five responses are packed
byte-exactly but keep `EVIDENCE_MATRIX`; packing is not a financial verdict.

## Body-bound rules (structural only)

The packer emits exactly **MTI (4 ASCII digits) + 16 uppercase hex bitmap +
ascending data elements — body only**. No header, no length prefix, no
trailing octet.

| Order | Rule | Exact bound |
| --- | --- | --- |
| 1 | MTI | exactly 4 ASCII digits; only the profile-selected MTIs (`0100`, `0110`) |
| 2 | field map / keys | map non-null; key non-null; DE1/secondary indicator rejected; unknown/disabled DE rejected |
| 3 | presence | scanner required/forbidden matrix per MTI, scanned ascending DE 1..64 |
| 4 | bitmap | 16 uppercase hex characters, high bit first; secondary mask never set |
| 5 | order | ascending DE emission, independent of map iteration order |
| 6 | fixed numeric | exact profile width; digits only, no padding/truncation |
| 7 | fixed upper alnum | exact profile width; `A-Z0-9` only, no normalization |
| 8 | LL numeric | 2-digit zero-padded length prefix excluding the prefix; profile min..max; digits only |
| 9 | LLL printable ASCII | 3-digit zero-padded length prefix excluding the prefix; profile min..max; octets `0x20..0x7E` |
| 10 | DE48 structural | `<=999` bytes (`STRUCTURAL_DE48_MAX_BYTES`); the **826 admission budget is not applied** |
| 11 | DE38 | encoded only when supplied; never minted; `0100` forbids DE38/DE39 through the P1 matrix |

Deliberately absent: canonical DE48 key=value schema, DE38 trusted-evidence
semantics, amount/currency/date/domain rules, P4 matrix, instruction binding,
freshness, 826 admission, attestation, dispatch and any financial outcome.

## Source ↔ run correspondence

| Input | SHA-256 (current = `hashes-before` = `hashes-after`) |
| --- | --- |
| `Iso8583Packer.java` | `1a6eb030ee51a6b126d51c65c0a1b239dea9bff15002ab53624354f4950b99d0` |
| `Iso8583PackException.java` | `058b212332326bf3ecaf788efa3fc54d9aa0f4f0f0d6086aa6e808985fb4a23b` |
| `Iso8583DataElement.java` | `5a277e46e02a654031f8cf19a5eed2534ad352437a9f4f9c43d0890e60aa9438` |
| `Iso8583PackerTest.java` | `3eeb77bec0160e4a9bb41b3ee4d4b47f1de478ea9f55de8826f15ddc61684243` |
| `packed-vectors.json` (oracle) | `800bc07148cf21d2c6b2f5711a5ca97d7119c32855b7d7826088dddcf47c7ff0` |
| `iso8583-profile.md` (approved) | `de02c533536dcb6e87e4c6e93357efb9efa60c085a59f6f63bbfbb8c0e1fe481` |

- `hashes-before.sha256` and `hashes-after.sha256` are byte-identical: 185
  inputs each, empty `hashes-diff.txt`, `hash-diff-exit.txt = 0`.
- At the saved run, compiled classes sat between the recorded timestamp and the
  test run: `target/classes/.../Iso8583Packer.class` `20:09:11Z`,
  `target/test-classes/.../Iso8583PackerTest.class` `20:09:14Z`, targeted run
  finished `20:09:28Z`. Maven reported "Nothing to compile — all classes are
  up to date", so the saved run consumed exactly the classes built from the
  hashed sources. (A later additive P2a6 compile may refresh `target/`; it did
  not edit these sources, whose hashes remain as listed.)
- `git-status-before.txt` = `git-status-after.txt` = `git-status-final.txt`;
  HEAD after run equals HEAD above. `review-manifest.json` still matches the
  packed-vector oracle.

## Actual results (from saved logs)

| Check | Result | Exit |
| --- | --- | --- |
| targeted `Iso8583PackerTest` | `Tests run: 53, Failures: 0, Errors: 0, Skipped: 0` | `0` |
| protocol regression `Iso8583*Test` | `Tests run: 407, Failures: 0, Errors: 0, Skipped: 0` | `0` |
| stderr (both runs) | empty | — |
| all-input hash diff | empty | `0` |

## Boundary

This is local JUnit/byte-fixture evidence only. It is **not** domain validation,
registry/mandate resolution, freshness, 826 admission, P4/binding admission,
attestation/crypto verification, HTTP/runtime behaviour, independent QA,
Security review or human acceptance. P1 and G6 remain open. No specification,
vector, profile or fixture was edited.

## Files in this pack

`BINDING.txt`, before/after/final git status, before/after hashes, hash diff and
exit, `head-after.txt`, Java/Maven versions, targeted and protocol
stdout/stderr/exit logs, and this manifest plus `PROGRESS.md`.
