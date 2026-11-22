# p2a1b results — strict field reader/cursor

Date (UTC): 2026-11-20
Model: configured default (provider `nan`, model `deepseek-v4-flash`), no override
Binding: `/home/pazfernando/projects/acquiring-simulator` @ `feat/wi-acq-auth-001`
HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315` (matched expected; unchanged at end)
Governing profile SHA256: `de02c533536dcb6e87e4c6e93357efb9efa60c085a59f6f63bbfbb8c0e1fe481`

## Delivered behaviour

| Encoding | Method | Framing | Alphabet | Non-claims |
| --- | --- | --- | --- | --- |
| fixed `n` | `readFixedDigits(width)` | exactly `width` octets | ASCII `0-9` | no amount/date semantics |
| fixed `an` | `readFixedUpperAlnum(width)` | exactly `width` octets | `A-Z`, `0-9` | no alias/fixture lookup |
| LL `n` (DE32) | `readLlDigits(1,11)` | 2-digit decimal length + value; prefix excluded | ASCII `0-9` | no alias registry |
| LLL ASCII (DE48) | `readLllPrintableAscii(1,999)` | 3-digit decimal length + value; prefix excluded | printable ASCII `U+0020..U+007E` | no key=value schema/admission |

`requireEnd()` rejects leftover octets; there is no trailing-ignore and no
pad/truncate path. Reads advance the cursor only after full validation.

## Commands and actual results

```
export JAVA_HOME=/home/pazfernando/.local/opt/jdk-25
/home/pazfernando/.local/opt/maven/bin/mvn -o -DskipTests compile
/home/pazfernando/.local/opt/maven/bin/mvn -o test-compile
/home/pazfernando/.local/opt/maven/bin/mvn -o \
  -Dtest=Iso8583FieldReaderTest -DfailIfNoSpecifiedTests=false test
```

| Step | Exit | Observed |
| --- | --- | --- |
| `compile` | `0` | `Compiling 95 source files ...`; `BUILD SUCCESS` |
| `test-compile` | `0` | `Compiling 40 source files ...`; `BUILD SUCCESS` |
| targeted `test` | `0` | `Tests run: 25, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS` |

All three stderr captures are empty (`0` bytes). Raw stdout, stderr and exit
codes are preserved beside this file.

The first local run failed on a value-offset defect in the variable-length
reads (25 run, 13 failures); it was fixed and re-run. Only the final passing
run is retained as evidence; the defect is recorded here rather than claimed
as a clean first pass.

## Meaningful test coverage

- **DE32 min/max**: prefix `01`+`1` and `11`+11 digits, asserting prefix
  exclusion through `position()`.
- **DE32 negatives**: truncated prefix, truncated value, declared `12`
  (over-limit even with 12 octets present), `00`, `1A` prefix, non-digit value,
  Unicode digit.
- **DE42 exactly 15**: approved fixture walk DE32→DE37→DE41→DE42 returns
  `MERCH0000000001` and leaves the DE48 prefix `188` untouched. This directly
  guards the historical fixed-DE32 misalignment that shifted DE42.
- **DE42 negatives**: 14 octets (`BODY_TOO_SHORT`), 16 octets consumed 15 then
  `TRAILING_BYTES` (no truncation), lowercase, space padding, Unicode.
- **DE48 bounds**: 1 and 999 octets accepted with exact consumption.
- **DE48 negatives**: zero length, truncated prefix/value, non-decimal prefix,
  control `U+0007`, DEL `U+007F`, non-ASCII `U+00E9`.
- **Cursor**: non-zero start offset, exact end accepted, trailing byte rejected,
  negative/over-length offsets rejected.
- **Fixed numeric**: exact width, short body, letter, space and Unicode digit.

## Immutability

- `hashes-before-test.sha256` vs `hashes-after-test.sha256`: **byte-identical**
  across 141 `pom.xml`/`src/main/java`/`src/test/java`/`src/main/resources`
  files.
- `git-status-before.txt` vs `git-status-after.txt`: identical.
- HEAD unchanged; branch unchanged; no tracked file added or modified.
- P2a1a protocol files retain their recorded hashes:
  `Iso8583Header.java 51f2d7cf…`, `Iso8583HeaderParseException.java 2212896b…`,
  `Iso8583HeaderParser.java e4157df5…`, `PrimaryBitmap.java 9123c0b5…`,
  `Iso8583HeaderParserTest.java 07a44647…`.
- New file hashes are in `source.sha256`.
- No Git write, publication, deployment, cloud call, credential use or deletion.

## Limitations (not success claims)

- Local compile and one targeted test class only; not independent QA, Security
  review or human acceptance.
- No full suite, no regression run, no runtime/HTTP/AWS path, no fixture corpus
  replay yet.
- DE48 structural bytes are printable-ASCII checked only; canonical key=value
  grammar and the 826-byte exchange budget are unimplemented by design.
- Presence matrices, DE38 evidence, domain amounts/dates/currencies/fixtures
  and response construction remain open.
- P1 leftovers remain open; G6 remains open.

## Next slice

A single structural codec scanner that maps the enabled DE set onto this cursor
and is driven by the approved packed-fixture corpus (positive and negative),
still separating structural checks from domain/admission semantics.
