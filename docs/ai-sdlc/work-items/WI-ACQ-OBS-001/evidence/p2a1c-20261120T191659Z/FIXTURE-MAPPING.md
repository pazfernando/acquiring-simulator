# P2a1c — fixture-by-fixture classifier mapping

Oracle: `vectors/packed-vectors.json`, SHA-256
`800bc07148cf21d2c6b2f5711a5ca97d7119c32855b7d7826088dddcf47c7ff0`, the
corpus pinned in `vectors/review-manifest.json` (`files[16].sha256`) and marked
`PROPOSED specification data; no codec or QA executed`.

Bodies are rebuilt from the authoritative `hex` octets (ISO-8859-1
one char per octet); the `ascii` field is an escaped rendering and is not used
as input. `ACCEPT` fixtures assert a full structural scan; scanner-scope
negatives assert the exact phase/rule; every other fixture asserts structural
parseability only and records the deferred owner layer. Structural acceptance
of a deferred fixture is **not** a business pass.

## Rule responsibility

| Expected label | Owner layer | Scanner verdict |
| --- | --- | --- |
| `SCHEMA_CANONICAL` | DE48 canonical key=value schema | deferred: parse only |
| `SCHEMA_ENUM` | DE48 canonical schema/enum (v,op) | deferred: parse only |
| `SCHEMA_LENGTH` | DE48 canonical per-key bounds | deferred: parse only |
| `DOMAIN_AMOUNT` | domain amount profile | deferred: parse only |
| `DOMAIN_CURRENCY` | domain currency profile | deferred: parse only |
| `DOMAIN_DATE` | domain Gregorian date | deferred: parse only |
| `DOMAIN_DE7` | domain DE7/sent consistency | deferred: parse only |
| `ADMISSION_FRESHNESS` | admission freshness | deferred: parse only |
| `ADMISSION_RESPONSE_CAPACITY` | exchange admission budget (826) | deferred: parse only |
| `EVIDENCE_MATRIX` | P4 actor/stage/result matrix | deferred: parse only |
| `BINDING_INSTRUCTION` | instruction binding | deferred: parse only |
| `BINDING_AUTH_REFERENCE` | DE38 trusted-evidence binding | deferred: parse only |
| `AUTH_ATTESTATION` | attestation/principal binding | deferred: parse only |
| `BUSINESS_CONFLICT_QUARANTINE` | conditional business admission/quarantine | deferred: parse only |
| `PARSER_MTI` | P6 structure | checked: reject (HEADER) |
| `PARSER_BITMAP` | P6 structure | checked: reject (HEADER) |
| `PARSER_SECONDARY` | P6 structure | checked: reject (HEADER) |
| `PARSER_FORBIDDEN` | P6 structure | checked: reject (HEADER) |
| `PARSER_REQUIRED` | P6 structure | checked: reject (PRESENCE) |
| `PARSER_LENGTH` | P6 structure | checked: reject (FIELD) |
| `PARSER_ALPHABET` | P6 structure | checked: reject (FIELD) |
| `PARSER_TRUNCATED` | P6 structure | checked: reject (FIELD) |
| `PARSER_END_BODY` | P6 structure | checked: reject (FIELD) |
| `PARSER_ASCII` | P6 structure | checked: reject (FIELD) |
| `ACCEPT` | P6 structure (this slice) | checked: accept |

## Fixtures (85)

| # | Fixture | MTI | Expected | Scanner outcome | Classification |
| --- | --- | --- | --- | --- | --- |
| 1 | `V01-approved-request` | 0100 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 2 | `V02-approved-response` | 0110 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 3 | `V03-decline-request` | 0100 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 4 | `V04-decline-response` | 0110 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 5 | `V05-network-format` | 0110 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 6 | `V06-network-unavailable` | 0110 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 7 | `V07-private-uncertainty` | 0110 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 8 | `V08-late-approval` | 0110 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 9 | `V09-late-decline` | 0110 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 10 | `V10-actual-DE38` | 0110 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 11 | `N-00-network` | 0110 | `EVIDENCE_MATRIX` | ACCEPT (body consumed exactly) | deferred: P4 actor/stage/result matrix |
| 12 | `N-05-processor` | 0110 | `EVIDENCE_MATRIX` | ACCEPT (body consumed exactly) | deferred: P4 actor/stage/result matrix |
| 13 | `N-30-issuer` | 0110 | `EVIDENCE_MATRIX` | ACCEPT (body consumed exactly) | deferred: P4 actor/stage/result matrix |
| 14 | `N-91-issuer` | 0110 | `EVIDENCE_MATRIX` | ACCEPT (body consumed exactly) | deferred: P4 actor/stage/result matrix |
| 15 | `N-unknown-code` | 0110 | `EVIDENCE_MATRIX` | ACCEPT (body consumed exactly) | deferred: P4 actor/stage/result matrix |
| 16 | `N-wrong-op` | 0110 | `SCHEMA_ENUM` | ACCEPT (body consumed exactly) | deferred: DE48 canonical schema/enum (v,op) |
| 17 | `N-wrong-correlation` | 0110 | `BINDING_INSTRUCTION` | ACCEPT (body consumed exactly) | deferred: instruction binding |
| 18 | `N-wrong-context` | 0110 | `BINDING_INSTRUCTION` | ACCEPT (body consumed exactly) | deferred: instruction binding |
| 19 | `N-unsolicited-DE38` | 0110 | `BINDING_AUTH_REFERENCE` | ACCEPT (body consumed exactly) | deferred: DE38 trusted-evidence binding |
| 20 | `N-missing-result` | 0110 | `SCHEMA_CANONICAL` | ACCEPT (body consumed exactly) | deferred: DE48 canonical key=value schema |
| 21 | `N-missing-actor` | 0110 | `SCHEMA_CANONICAL` | ACCEPT (body consumed exactly) | deferred: DE48 canonical key=value schema |
| 22 | `N-missing-evidence` | 0110 | `SCHEMA_CANONICAL` | ACCEPT (body consumed exactly) | deferred: DE48 canonical key=value schema |
| 23 | `B-min-amount` | 0100 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 24 | `B-max-amount` | 0100 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 25 | `B-below-min` | 0100 | `DOMAIN_AMOUNT` | ACCEPT (body consumed exactly) | deferred: domain amount profile |
| 26 | `B-over-max` | 0100 | `DOMAIN_AMOUNT` | ACCEPT (body consumed exactly) | deferred: domain amount profile |
| 27 | `B-non-usd` | 0100 | `DOMAIN_CURRENCY` | ACCEPT (body consumed exactly) | deferred: domain currency profile |
| 28 | `B-DE2` | 0100 | `PARSER_FORBIDDEN` | REJECT UNSUPPORTED_FIELD (bitmap) | structural reject (scanner scope) |
| 29 | `B-DE55` | 0100 | `PARSER_FORBIDDEN` | REJECT UNSUPPORTED_FIELD (bitmap) | structural reject (scanner scope) |
| 30 | `B-alias-11` | 0100 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 31 | `B-alias-12` | 0100 | `PARSER_LENGTH` | REJECT LENGTH_OUT_OF_RANGE (DE32=12) | structural reject (scanner scope) |
| 32 | `B-padded-terminal` | 0100 | `PARSER_ALPHABET` | REJECT INVALID_UPPER_ALNUM (DE41) | structural reject (scanner scope) |
| 33 | `N-missing-DE3` | 0100 | `PARSER_REQUIRED` | REJECT MISSING_REQUIRED_FIELD (DE3) | structural reject (scanner scope) |
| 34 | `N-secondary` | 0100 | `PARSER_SECONDARY` | REJECT SECONDARY_BITMAP_FORBIDDEN | structural reject (scanner scope) |
| 35 | `N-bitmap` | 0100 | `PARSER_BITMAP` | REJECT INVALID_BITMAP | structural reject (scanner scope) |
| 36 | `N-MTI` | 0800 | `PARSER_MTI` | REJECT UNSUPPORTED_MTI | structural reject (scanner scope) |
| 37 | `N-truncated` | 0100 | `PARSER_TRUNCATED` | REJECT BODY_TOO_SHORT (DE49) | structural reject (scanner scope) |
| 38 | `N-trailing` | 0100 | `PARSER_END_BODY` | REJECT TRAILING_BYTES (1) | structural reject (scanner scope) |
| 39 | `N-nonascii` | 0100 | `PARSER_ASCII` | REJECT NON_PRINTABLE_ASCII (DE48) | structural reject (scanner scope) |
| 40 | `Y-2026` | 0100 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 41 | `Y-2027` | 0100 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 42 | `B-DE48-exact-999` | 0100 | `ADMISSION_RESPONSE_CAPACITY` | ACCEPT (body consumed exactly) | deferred: exchange admission budget (826) |
| 43 | `N-DE48-1000` | 0100 | `PARSER_ALPHABET` | REJECT INVALID_NUMERIC (DE49 after LLL 100) | structural reject (scanner scope) |
| 44 | `N-duplicate-key` | 0100 | `SCHEMA_CANONICAL` | ACCEPT (body consumed exactly) | deferred: DE48 canonical key=value schema |
| 45 | `N-unknown-key` | 0100 | `SCHEMA_CANONICAL` | ACCEPT (body consumed exactly) | deferred: DE48 canonical key=value schema |
| 46 | `N-escaped` | 0100 | `SCHEMA_CANONICAL` | ACCEPT (body consumed exactly) | deferred: DE48 canonical key=value schema |
| 47 | `N-ref-129` | 0100 | `SCHEMA_LENGTH` | ACCEPT (body consumed exactly) | deferred: DE48 canonical per-key bounds |
| 48 | `M-PROCESSOR-FORMAT` | 0110 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 49 | `M-PROCESSOR-DISALLOWED` | 0110 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 50 | `M-NETWORK-DISALLOWED` | 0110 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 51 | `M-PROCESSOR-UNCERTAIN` | 0110 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 52 | `M-PROCESSOR-UNAVAILABLE` | 0110 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 53 | `M-NETWORK-UNAVAILABLE` | 0110 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 54 | `M-authorized-relay` | 0110 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 55 | `N-conflicting-confirmation` | 0110 | `BUSINESS_CONFLICT_QUARANTINE` | ACCEPT (body consumed exactly) | deferred: conditional business admission/quarantine |
| 56 | `N-missing-DE38-asserted` | 0110 | `BINDING_AUTH_REFERENCE` | ACCEPT (body consumed exactly) | deferred: DE38 trusted-evidence binding |
| 57 | `N-forged-actor` | 0110 | `AUTH_ATTESTATION` | ACCEPT (body consumed exactly) | deferred: attestation/principal binding |
| 58 | `N-unresolvable-evidence` | 0110 | `AUTH_ATTESTATION` | ACCEPT (body consumed exactly) | deferred: attestation/principal binding |
| 59 | `N-mismatch-amount` | 0110 | `BINDING_INSTRUCTION` | ACCEPT (body consumed exactly) | deferred: instruction binding |
| 60 | `D-leap-valid` | 0100 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 61 | `D-leap-invalid` | 0100 | `DOMAIN_DATE` | ACCEPT (body consumed exactly) | deferred: domain Gregorian date |
| 62 | `D-bad-month` | 0100 | `DOMAIN_DATE` | ACCEPT (body consumed exactly) | deferred: domain Gregorian date |
| 63 | `D-DE7-mismatch` | 0100 | `DOMAIN_DE7` | ACCEPT (body consumed exactly) | deferred: domain DE7/sent consistency |
| 64 | `D-stale` | 0100 | `ADMISSION_FRESHNESS` | ACCEPT (body consumed exactly) | deferred: admission freshness |
| 65 | `K-empty-key` | 0100 | `SCHEMA_CANONICAL` | ACCEPT (body consumed exactly) | deferred: DE48 canonical key=value schema |
| 66 | `K-empty-value` | 0100 | `SCHEMA_CANONICAL` | ACCEPT (body consumed exactly) | deferred: DE48 canonical key=value schema |
| 67 | `K-out-of-order` | 0100 | `SCHEMA_CANONICAL` | ACCEPT (body consumed exactly) | deferred: DE48 canonical key=value schema |
| 68 | `CAP-request-826` | 0100 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 69 | `CAP-request-827` | 0100 | `ADMISSION_RESPONSE_CAPACITY` | ACCEPT (body consumed exactly) | deferred: exchange admission budget (826) |
| 70 | `CAP-response-1` | 0110 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 71 | `CAP-over-request-1` | 0100 | `ADMISSION_RESPONSE_CAPACITY` | ACCEPT (body consumed exactly) | deferred: exchange admission budget (826) |
| 72 | `CAP-response-2` | 0110 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 73 | `CAP-over-request-2` | 0100 | `ADMISSION_RESPONSE_CAPACITY` | ACCEPT (body consumed exactly) | deferred: exchange admission budget (826) |
| 74 | `CAP-response-3` | 0110 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 75 | `CAP-over-request-3` | 0100 | `ADMISSION_RESPONSE_CAPACITY` | ACCEPT (body consumed exactly) | deferred: exchange admission budget (826) |
| 76 | `CAP-response-4` | 0110 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 77 | `CAP-over-request-4` | 0100 | `ADMISSION_RESPONSE_CAPACITY` | ACCEPT (body consumed exactly) | deferred: exchange admission budget (826) |
| 78 | `CAP-response-5` | 0110 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 79 | `CAP-over-request-5` | 0100 | `ADMISSION_RESPONSE_CAPACITY` | ACCEPT (body consumed exactly) | deferred: exchange admission budget (826) |
| 80 | `CAP-response-6` | 0110 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 81 | `CAP-over-request-6` | 0100 | `ADMISSION_RESPONSE_CAPACITY` | ACCEPT (body consumed exactly) | deferred: exchange admission budget (826) |
| 82 | `CAP-response-7` | 0110 | `ACCEPT` | ACCEPT (body consumed exactly) | structural pass |
| 83 | `CAP-over-request-7` | 0100 | `ADMISSION_RESPONSE_CAPACITY` | ACCEPT (body consumed exactly) | deferred: exchange admission budget (826) |
| 84 | `N-response-DE7` | 0110 | `DOMAIN_DE7` | ACCEPT (body consumed exactly) | deferred: domain DE7/sent consistency |
| 85 | `N-response-invalid-date` | 0110 | `DOMAIN_DATE` | ACCEPT (body consumed exactly) | deferred: domain Gregorian date |

## Counts

- ACCEPT fixtures structurally accepted: **31**
- Scanner-scope structural negatives rejected: **12**
- Structurally parseable but deferred to later layers: **42**
- Total: **85** of 85

Deferred breakdown: DE48 canonical schema 11, domain 8, admission 10,
P4 evidence matrix 5, binding 5, attestation 2, business-conflict quarantine 1.

