# P2a1c progress — assembled structural scanner (verified, unwired)

Status: **PARTIAL, locally tested 2026-11-20.** The scanner adds the missing
assembly over the earlier header parser and field cursor. It is not a codec and
not wired into `AwsRuntime`, the channel route or HTTP.

## Added

- `Iso8583DataElement` — framing/alphabet table for the 14 enabled DEs.
- `Iso8583PresenceException` — missing-required / forbidden-present rules.
- `Iso8583Message` — immutable structural decode; fields must match bitmap.
- `Iso8583Scanner` — header → presence → ascending field read → exact end.
- `Iso8583ScannerTest` — 94 JUnit tests driven by the 85-fixture oracle plus
  focused structural cases.

## Verified

- `mvn -o -Dtest=Iso8583ScannerTest test`: 94/94, exit 0.
- `mvn -o -Dtest='Iso8583*Test' test`: 122/122, exit 0.
- Source and oracle hashes rechecked after the run and still match
  `source.sha256` / `oracle.sha256` (`hash-reverify.txt`).
- Initial attempt failed 2/94 for test-expectation reasons only; corrected and
  retained in evidence. No production defect or production edit was involved.

## Fixture counts

85 cases = 31 ACCEPT + 12 scanner-scope rejects + 42 deferred. Deferred:
DE48 schema 11, domain 8, admission 10, P4 5, binding 5, attestation 2,
business-conflict 1. The 94 JUnit tests are not 94 fixture cases.

## Not done / next

- DE48 canonical request schema (`P2a2a`, started separately).
- DE48 response schema, DE38 binding, domain date/amount/currency, admission.
- Response packer, channel/ACL/HTTP integration, `AwsRuntime` activation.

No Git write, publication, cloud call, credential use or spec edit was performed.
This is not independent QA, Security review or human acceptance.
