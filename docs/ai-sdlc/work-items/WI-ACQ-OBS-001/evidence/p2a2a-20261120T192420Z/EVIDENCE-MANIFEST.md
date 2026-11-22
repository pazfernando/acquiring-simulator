# P2a2a evidence manifest — strict DE48 request canonical schema

- Work item record: `WI-ACQ-OBS-001` (protocol P2 slice), evidence path
  `evidence/p2a2a-20261120T192420Z/`.
- Binding: repo `/home/pazfernando/projects/acquiring-simulator`; branch
  `feat/wi-acq-auth-001`; HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`;
  origin `https://github.com/pazfernando/acquiring-simulator.git`; configured
  model provider `nan` / `deepseek-v4-flash`, no override;
  captured `2026-11-20T19:24:20Z`.
- Deliverable: `Iso8583De48Exception`, `Iso8583De48Request`,
  `Iso8583De48RequestParser`, `Iso8583De48RequestParserTest`.
- Git operations: none. `head-after.txt` unchanged; final git status unchanged.
  No specification, oracle or other work-item document was edited.

## Implemented rules (request only)

Canonical semicolon-separated `key=value` pairs, no terminal delimiter, spaces,
escaping, percent/nested encoding or empty pair. Case-sensitive keys in approved
order `v,op,aid,cid,tid,att,acq,mid,term,pay,gen,fixture,cvm,crypto,risk,sent`,
optional last `prior`. Reject unknown, duplicate, out-of-order, missing,
over-999-byte, per-key out-of-bounds, non-`[A-Za-z0-9._:-]` values. Literals
`v=1`, `op=A`; enums `cvm∈{VERIFIED,NOT_REQUIRED}`, `crypto∈{0,1}`,
`risk∈{PASS,REJECT}`; `gen` 1–10 digits, no leading zero, 1–2147483647;
`sent` exactly 14 digits. Nothing is trimmed, reordered or normalized.

Explicitly **not** implemented: response schema, DE38/binding, P4 matrix,
amount/currency, Gregorian date and DE7 consistency, freshness, 826-byte
exchange budget, packing, HTTP/ACL, runtime wiring.

## Source/run hash correspondence

`source.sha256` / `oracle.sha256`; `hashes-before.sha256` and
`hashes-after.sha256` are byte-identical (153 inputs, `hashes-diff.txt` empty):

| Input | SHA-256 |
| --- | --- |
| `Iso8583De48Exception.java` | `4de40745…ebbfbc7` |
| `Iso8583De48Request.java` | `f59ac8b0…b0f2e810` |
| `Iso8583De48RequestParser.java` | `f7afee5d…c4777167` |
| `Iso8583De48RequestParserTest.java` | `eef9245e…2a752140` |
| `iso8583-profile.md` | `de02c533…0e1fe481` |
| `vectors/packed-vectors.json` | `800bc071…f47c7ff0` |

## Actual results

| Check | Command (offline) | Result |
| --- | --- | --- |
| Compile | `mvn -o -Dtest=Iso8583De48RequestParserTest test` | 102 sources compiled, exit 0 |
| New slice | same command | **58 tests, 0 failures, 0 errors, 0 skipped**, exit 0 |
| Protocol regression | `mvn -o -Dtest='Iso8583*Test' test` | **180 tests, 0 failures, 0 errors, 0 skipped**, exit 0 |

Stderr is empty for both runs. No failed attempt occurred in this slice.

## Corpus oracle use

Fixture bodies are rebuilt from authoritative `hex`, scanned structurally, and
DE48 is extracted; no expected implementation was generated from the parser.

- 25 otherwise structurally accepted 0100 fixtures parse successfully, including
  `CAP-request-827` (schema-valid, admission deferred) and
  `B-DE48-exact-999` (999 bytes, admission deferred).
- 7 request schema negatives are the negative oracle:
  `N-duplicate-key`→`DUPLICATE_KEY`, `N-unknown-key`→`UNKNOWN_KEY`,
  `N-escaped`→`INVALID_VALUE_ALPHABET`,
  `N-ref-129`→`VALUE_LENGTH_OUT_OF_RANGE`, `K-empty-key`→`EMPTY_FIELD`,
  `K-empty-value`→`EMPTY_FIELD`, `K-out-of-order`→`OUT_OF_ORDER_KEY`.
- Structurally rejected 0100 fixtures and response fixtures are excluded; they
  belong to the scanner or response slice. Focused synthetic cases cover
  trailing delimiter, whitespace, backslash, nested `=`, bounds, enums and the
  `gen` number form.

## Scope statement

This is the canonical **request** DE48 schema only. It is not a complete ISO
codec, not a response parser, and not wired to any route. No QA, Security or
human acceptance is claimed.
