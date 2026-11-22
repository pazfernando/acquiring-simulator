# P2a1a evidence — strict ISO header/bitmap parser primitive

Status: PARTIAL codec slice delivered; P1 leftovers and all later codec phases remain open.

## Binding

- Repository: /home/pazfernando/projects/acquiring-simulator
- Branch: feat/wi-acq-auth-001
- HEAD: cfc456f4f469d9558a78fda8ff9b770b00817315
- Origin: github.com/pazfernando/acquiring-simulator.git
- Model: configured default, no override
- Governing profile: docs/ai-sdlc/work-items/WI-ACQ-OBS-001/iso8583-profile.md
  - SHA256: de02c533536dcb6e87e4c6e93357efb9efa60c085a59f6f63bbfbb8c0e1fe481
  - Clauses applied: P1 (packing/bitmap), P6 (header/bitmap first-failure order)
- Fixture provenance: vectors/evidence-fixtures.json,
  original0100Hex prefix for EF:V02-approved-response (no bytes re-encoded)

## Delivered

New package `com.acquiring.simulator.protocol.iso8583`:

- `Iso8583HeaderParser` — strict ASCII MTI (4 digits) + primary bitmap
  (16 uppercase hex chars, high bit first), header length 20.
- `Iso8583Header` / `PrimaryBitmap` — parsed result and read-only bit access.
- `Iso8583HeaderParseException` — rule codes: BODY_TOO_SHORT, NON_ASCII,
  INVALID_MTI, UNSUPPORTED_MTI, INVALID_BITMAP, SECONDARY_BITMAP_FORBIDDEN,
  UNSUPPORTED_FIELD.

Accepted MTIs: 0100, 0110. Enabled bitmap fields: 3, 4, 7, 11, 22, 25, 32,
37, 38, 39, 41, 42, 48, 49. Bit 1 (secondary) and every other bit are
rejected. No field presence matrix, field widths/alphabet, DE48 schema,
amount/date/domain validation, admission, dispatch or response construction.

## Verification

Command (absolute JDK 25 / Maven):

```
JAVA_HOME=/home/pazfernando/.local/opt/jdk-25 \
  /home/pazfernando/.local/opt/maven/bin/mvn -o -Dtest=Iso8583HeaderParserTest test
```

Result: EXIT=0; Tests run: 3, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
See `run.log`; post-edit source hashes in `source.sha256`.

Tests: known packed fixture header; truncated/lowercase/non-hex bitmap
rejection; secondary-bitmap and disabled-DE2 rejection.

## Not claimed / next

Not full codec, not DE48 or DE38/DE85 fixtures, not P1 completion, not domain
validation, not financial admission, not independent QA. Next slice: one DE
field primitive (length/alphabet from P1) with 2–3 focused tests.
