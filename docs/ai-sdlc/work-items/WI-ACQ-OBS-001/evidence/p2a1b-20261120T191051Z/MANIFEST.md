# P2a1b evidence — strict field reader/cursor primitive

Status: PARTIAL codec slice delivered; P1 leftovers and all later codec phases remain open.

## Binding

- Repository: /home/pazfernando/projects/acquiring-simulator
- Branch: feat/wi-acq-auth-001
- HEAD: cfc456f4f469d9558a78fda8ff9b770b00817315 (unchanged at end)
- Origin: github.com/pazfernando/acquiring-simulator.git
- Model: configured default (provider nan, model deepseek-v4-flash), no override
- Governing profile: docs/ai-sdlc/work-items/WI-ACQ-OBS-001/iso8583-profile.md
  - SHA256: de02c533536dcb6e87e4c6e93357efb9efa60c085a59f6f63bbfbb8c0e1fe481
  - Clauses applied: P1 (packing, ASCII, fixed/LL/LLL framing), P2 structure/schema
    separation, P6 ordering (field length/alphabet checks after header/bitmap)
- Fixture provenance: docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/packed-vectors.json,
  V01-approved-request ASCII octets 59..99 (DE32/DE37/DE41/DE42 + DE48 prefix),
  copied byte-for-byte; no fixture byte was rewritten.

## Delivered

Package `com.acquiring.simulator.protocol.iso8583`:

- `Iso8583FieldReader` — strict cursor over an ASCII body with:
  - `readFixedDigits(width)` — fixed `n`, exact digit width, no padding;
  - `readFixedUpperAlnum(width)` — fixed `an` restricted to `A-Z`/`0-9`;
  - `readLlDigits(min,max)` — two-digit ASCII decimal byte length excluding
    the prefix, then digits (DE32 `1..11`);
  - `readLllPrintableAscii(min,max)` — three-digit length excluding the prefix,
    then printable ASCII U+0020..U+007E (DE48 `1..999`);
  - `position()`, `remaining()`, `hasRemaining()`, `requireEnd()`.
- `Iso8583FieldException` — structural rules: START_OUT_OF_BOUNDS,
  BODY_TOO_SHORT, INVALID_LENGTH_PREFIX, LENGTH_OUT_OF_RANGE, INVALID_NUMERIC,
  INVALID_UPPER_ALNUM, NON_PRINTABLE_ASCII, TRAILING_BYTES.

Reads are atomic: the cursor advances only after the whole field is validated.
A failed read does not consume octets. There is no implicit trailing-ignore.

## Deliberately not checked here

Bitmap field presence/matrices, DE48 canonical key=value grammar (ordering,
duplicates, unknown keys, per-key bounds, no-space rule), aggregate 826/999
admission budget, amounts, dates, currencies, fixtures, DE38 evidence,
dispatch and response construction. These remain later structural/domain
phases; this primitive is not domain validation.

## Verification

```
JAVA_HOME=/home/pazfernando/.local/opt/jdk-25 \
  /home/pazfernando/.local/opt/maven/bin/mvn -o -DskipTests compile
JAVA_HOME=/home/pazfernando/.local/opt/jdk-25 \
  /home/pazfernando/.local/opt/maven/bin/mvn -o test-compile
JAVA_HOME=/home/pazfernando/.local/opt/jdk-25 \
  /home/pazfernando/.local/opt/maven/bin/mvn -o \
  -Dtest=Iso8583FieldReaderTest -DfailIfNoSpecifiedTests=false test
```

Result: compile exit 0 (95 sources), test-compile exit 0 (40 sources),
targeted test exit 0 — **25 tests, 0 failures, 0 errors, 0 skipped**;
all stderr files empty. See `compile-stdout.txt`, `test-compile-stdout.txt`,
`test-stdout.txt`, `test-stderr.txt`, and the `*-exit.txt` files.

All `src/main/java`, `src/test/java`, `src/main/resources` and `pom.xml` files
were hashed before and after the targeted run: `hashes-before-test.sha256` and
`hashes-after-test.sha256` are byte-identical (141 files).

## Test groups (25 executions)

1. DE32 LL `n1..11`: accepts 1 and 11 digits; rejects truncated prefix/value,
   over-limit `12`, zero length, non-decimal prefix, non-numeric value and a
   Unicode digit.
2. DE42 fixed 15: fixture walk proves DE32 prefix exclusion and exact 15-octet
   DE42 (`MERCH0000000001`) at the correct offset (the historical fixed-DE32
   misread would shift it); rejects 14 octets, 16-without-end, lowercase,
   space padding and Unicode.
3. DE48 LLL printable: accepts 1 and 999 octets; rejects zero length, truncated
   prefix/value, non-decimal prefix, control, DEL and non-ASCII octets.
4. Cursor: honours a non-zero start offset, consumes exactly, requires exact
   end of body, rejects trailing bytes and out-of-bounds offsets.
5. Fixed numeric: consumes exactly its width; rejects short and non-decimal
   bodies, including Unicode digits.

## Not claimed / next

Not the whole codec, not field presence matrices, not DE48 canonical schema,
not domain/amount/date/fixture validation, not admission, dispatch or response
construction, not independent QA/Security review/human acceptance. No full
suite, runtime, AWS or HTTP path was run. Next slice: assembled structural
scanner for all enabled fields plus the packed-fixture positive/negative suite,
built on this cursor.
