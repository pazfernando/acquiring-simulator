# P2a1c evidence manifest — assembled structural scanner

- Work item record: `WI-ACQ-OBS-001` (protocol P2 slice), evidence path
  `evidence/p2a1c-20261120T191659Z/`.
- Binding: repo `/home/pazfernando/projects/acquiring-simulator`; branch
  `feat/wi-acq-auth-001`; HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`;
  origin `https://github.com/pazfernando/acquiring-simulator.git`; configured
  model provider `nan` / `deepseek-v4-flash`, no override; captured
  `2026-11-20T19:16:59Z`.
- Deliverable: `Iso8583DataElement`, `Iso8583Message`,
  `Iso8583PresenceException`, `Iso8583Scanner`, `Iso8583ScannerTest` in
  `com.acquiring.simulator.protocol.iso8583`.
- Git operations: none. HEAD unchanged (`head-after.txt`). Working tree
  preserved; no specification or oracle bytes rewritten.

## Current source/run hash correspondence

`hash-reverify.txt` (rechecked 2026-11-20T19:25:00Z, exit 0) proves the five
`source.sha256` entries and both `oracle.sha256` entries still match the files
the run used:

| Input | SHA-256 |
| --- | --- |
| `Iso8583DataElement.java` | `5a277e46…60aa9438` |
| `Iso8583Message.java` | `32f7573d…81c1a112` |
| `Iso8583PresenceException.java` | `b25ac18e…0f614a4d` |
| `Iso8583Scanner.java` | `7dc09433…26169a00` |
| `Iso8583ScannerTest.java` | `39f0bccb…c621569b` |
| `iso8583-profile.md` (approved profile) | `de02c533…0e1fe481` |
| `vectors/packed-vectors.json` (oracle) | `800bc071…f47c7ff0` |

`hashes-before.sha256` / `hashes-after.sha256` differ only by the five files
above plus the new test (`hashes-diff.txt`); `git-status-diff.txt` is empty.

## Actual results

| Check | Command (offline) | Result |
| --- | --- | --- |
| Compile | `mvn -o -DskipTests compile` | exit 0 (`compile-exit.txt`) |
| Test compile | `mvn -o -DskipTests test-compile` | exit 0 (`test-compile-exit.txt`) |
| Scanner slice | `mvn -o -Dtest=Iso8583ScannerTest test` | **94 tests, 0 failures, 0 errors, 0 skipped**, exit 0 (`test-*.txt`) |
| Protocol regression | `mvn -o -Dtest='Iso8583*Test' test` | **122 tests, 0 failures, 0 errors, 0 skipped**, exit 0 (`protocol-regression-*.txt`) |

## Initial failed attempt disposition

`attempt1-test-exit.txt` is `EXIT=1`; `attempt1-surefire-report.txt` records two
failures in the then-current `Iso8583ScannerTest`:

1. `scannerEnabledFieldsMatchTheHeaderParserProfile` compared List equality and
   rejected ascending order; the intended assertion is set equality in any
   order.
2. `rejectsNullTruncatedAndHeaderOnlyBodies` expected
   `Iso8583PresenceException` for the header-only body, but presence legitimately
   passes (the bitmap is complete) and the first field read fails with
   `Iso8583FieldException` / `BODY_TOO_SHORT`.

Disposition: both were test-expectation defects, not scanner defects. The test
was corrected to set-order equality and the FIELD-phase expectation, then the
same command passed. Production sources were unchanged between attempt 1 and the
green run (source mtimes 14:17; test mtime 14:19; final run recompiled tests
only). The failed attempt is retained, not deleted.

## Fixture classification (85 fixtures)

The corpus is 85 fixture cases, not 94 JUnit tests: parameterized methods expand
to more unit cases than fixtures, and focused scanner tests add cases that are
not fixture rows. Exact scanner-scope split (`FIXTURE-MAPPING.md`, verified
against `expected` labels):

- ACCEPT structurally accepted: **31**
- Scanner-scope structural negatives rejected: **12**
- Structurally parseable, deferred to later layers: **42**
- Total: **85**

Deferred layers: DE48 canonical schema **11** (9 canonical + 1 enum + 1 length),
domain **8** (2 amount + 1 currency + 3 date + 2 DE7), admission **10**
(9 response capacity + 1 freshness), P4 evidence matrix **5**, binding **5**
(3 instruction + 2 DE38), attestation **2**, business conflict/quarantine **1**.

## Scope statement

This evidence is a structural scanner only. It does **not** claim a complete
ISO codec, DE48 canonical schema, domain amount/currency/date validation,
admission budget, binding, attestation, response packing, HTTP/ACL integration or
runtime wiring. Those remain deferred; structural acceptance of a deferred
fixture is not a business pass.
