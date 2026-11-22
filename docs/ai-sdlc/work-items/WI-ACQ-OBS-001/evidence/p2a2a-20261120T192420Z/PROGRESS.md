# P2a2a progress — strict DE48 request canonical parser (verified, unwired)

Status: **IMPLEMENTED AND LOCALLY TESTED 2026-11-20.** Request schema only;
response, domain, admission and runtime integration remain open.

## Added

- `Iso8583De48RequestParser` — ordered canonical request parser.
- `Iso8583De48Request` — immutable ordered map, byte-preserving values.
- `Iso8583De48Exception` — canonical rule codes.
- `Iso8583De48RequestParserTest` — 58 JUnit tests: 25 corpus-positive requests,
  7 corpus schema negatives, focused canonical edge cases.

## Verified

- `mvn -o -Dtest=Iso8583De48RequestParserTest test`: 58/58, exit 0.
- `mvn -o -Dtest='Iso8583*Test' test`: 180/180, exit 0 (25 reader + 58 DE48 +
  94 scanner + 3 header).
- All 153 source/resource/pom/oracle inputs identical before and after the run.
- 7 request schema negatives produce the exact intended rule; 25 other
  structurally accepted 0100 fixtures parse, including the 827-byte and
  999-byte cases whose admission verdict stays deferred.

## Wiring

None. `AwsRuntime` remains inactive; no channel/ACL/HTTP route consumes the
parser; no response construction exists.

## Deferred from this slice

Response canonical schema (request keys then actor/stage/result/reason/evidence/at,
all six required), P4 actor/stage/result matrix, DE38 trusted-evidence binding,
domain amount/currency/date/DE7, freshness, 826-byte admission, response packer,
C4 carriage, HTTP/envelope integration.

## Next slice (one)

**P2a2b — strict DE48 response canonical schema parser + tests only.** Reuse the
same ordered-pair primitive; require the 16 request keys echoed in order followed
by `actor,stage,result,reason,evidence,at`; enforce the profile literals, enums,
bounds and no-normalization. Keep P4 matrix/DE38 binding and domain checks in
separate layers; use response fixture `hex` as the negative/positive oracle.

No Git write, publication, cloud call, credential use, deletion or spec edit was
performed.
