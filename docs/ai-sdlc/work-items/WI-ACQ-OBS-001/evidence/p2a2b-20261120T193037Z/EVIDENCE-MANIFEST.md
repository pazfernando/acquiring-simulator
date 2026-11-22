# P2a2b evidence manifest — strict DE48 response canonical schema

- Work item record: `WI-ACQ-OBS-001` (protocol P2 slice), evidence path
  `evidence/p2a2b-20261120T193037Z/`.
- Binding: repo `/home/pazfernando/projects/acquiring-simulator`; branch
  `feat/wi-acq-auth-001`; HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`;
  origin `https://github.com/pazfernando/acquiring-simulator.git`; configured
  model provider `nan` / `deepseek-v4-flash`, no override;
  captured `2026-11-20T19:30:37Z`.
- Deliverable: `Iso8583De48Response`,
  `Iso8583De48ResponseParser`, `Iso8583De48ResponseParserTest`; shared
  `Iso8583De48Exception` Javadoc widened (rules/behaviour unchanged).
- Git operations: none. `head-after.txt` unchanged; final git status unchanged
  apart from this slice's files. No specification or oracle was edited.

## Implemented rules (response only)

Canonical semicolon-separated `key=value` pairs, no terminal delimiter, spaces,
escaping, percent/nested encoding or empty pair. The complete sequence is the
16 request keys echoed in approved order (`v..sent`), optional `prior`, then
mandatory `actor,stage,result,reason,evidence,at`. Reject unknown, duplicate,
out-of-order, missing, over-999-byte, per-key out-of-bounds and disallowed
alphabet. Literals `v=1`, `op=A`; enums `cvm∈{VERIFIED,NOT_REQUIRED}`,
`crypto∈{0,1}`, `risk∈{PASS,REJECT}`,
`actor∈{ISSUER,NETWORK,PROCESSOR}`,
`stage∈{ISSUER_REPLIED,BEFORE_ISSUER,POSSIBLE_ISSUER,BEFORE_NETWORK}`,
`result∈{APPROVED,DECLINED,PROCESSOR_REJECTED,NOT_CONFIRMED}`,
`reason∈{APPROVED,DELIBERATE_DECLINE,FORMAT,DISALLOWED,UNAVAILABLE,UNCERTAIN}`;
`gen` 1–10 digits without leading zero, 1–2147483647; `evidence` 1–64;
`sent`/`at` exactly 14 digits. Nothing is trimmed, reordered or normalized.

The full response order is checked directly. The echoed prefix is **not**
delegated to the request parser, so a request-only key set or a `prior` after
the tail cannot slip through a permissive request shortcut.

Explicitly **not** implemented: P4 actor/stage/result matrix, DE38/binding,
attestation, business conflict/quarantine, Gregorian date/DE7/actual `at` time,
amount/currency, freshness, 826-byte exchange budget, response packer, HTTP/C4
carriage, runtime wiring.

## Source/run hash correspondence

`hashes-before.sha256` and `hashes-after.sha256` are byte-identical (156 inputs,
`hashes-diff.txt` empty). `source.sha256` / `oracle.sha256` rechecked `OK` in
`hash-reverify.txt`.

| Input | SHA-256 |
| --- | --- |
| `Iso8583De48Exception.java` | `966aa654…237e42e0` |
| `Iso8583De48Response.java` | `368e66c2…fc8bccc5e` |
| `Iso8583De48ResponseParser.java` | `27ce332e…3e45e48f92e` |
| `Iso8583De48ResponseParserTest.java` | `85a55d61…94706d458` |
| `iso8583-profile.md` | `de02c533…0e1fe481` |
| `vectors/packed-vectors.json` | `800bc071…f47c7ff0` |

The exception file hash differs from P2a2a only because its class Javadoc now
names the shared request+response grammar; no rule, enum or control flow changed.

## Actual results

| Check | Command (offline) | Result |
| --- | --- | --- |
| Compile + targeted | `mvn -o -Dtest=Iso8583De48ResponseParserTest test` | **74 tests, 0 failures, 0 errors, 0 skipped**, exit 0, stderr empty |
| Protocol regression | `mvn -o -Dtest='Iso8583*Test' test` | **254 tests, 0 failures, 0 errors, 0 skipped**, exit 0, stderr empty |

Unique `test-*.txt` / `protocol-regression-*.txt` stdout, stderr and exit files
are retained. No failed attempt occurred in the recorded slice.

## Corpus oracle use

Fixture bodies are rebuilt from authoritative `hex`, scanned structurally, and
DE48 is extracted; no expected implementation was generated from the parser.
Exact 0110 fixture accounting: **41 checked** = **22** schema-positive `ACCEPT`
+ **4** schema-negative + **15** later-layer deferred.

- Schema negatives: `N-wrong-op`→`INVALID_LITERAL` (`op`);
  `N-missing-actor`, `N-missing-result`, `N-missing-evidence`→
  `MISSING_REQUIRED_KEY`.
- Deferred and schema-valid: P4 5 (`N-00-network`, `N-05-processor`,
  `N-30-issuer`, `N-91-issuer`, `N-unknown-code`), binding 5 (instruction 3,
  auth-reference 2), attestation 2, conflict 1, domain 2 (`N-response-DE7`,
  `N-response-invalid-date`).
- `CAP-response-3` is exactly 999 DE48 octets and parses; 826/827 exchange
  admission stays deferred. `N-response-invalid-date` (`20270229…`) and
  `N-response-DE7` are schema-valid because Gregorian/DE7 checks belong to the
  domain layer; `at` may syntactically precede `sent` for the same reason.

Focused synthetic cases cover trailing delimiter, whitespace/percent, nested
`=`, empty key/value, duplicates, unknown keys, every tail reordering, missing
tail keys, evidence 64/65, `sent`/`at` 13/14/15 and non-digits, all enum
members, and the absence of any P4/domain decision.

## Scope statement

This is the canonical **response** DE48 schema only. It is not a complete ISO
codec, not P4/binding/domain admission, not a response packer and not wired to
any route. No QA, Security or human acceptance is claimed.
