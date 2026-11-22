# p2a1b progress — structural field reader

Status: **complete for the authorized scope.** Codec/service work beyond one
structural field primitive was not started.

## Done this turn

1. Verified binding: repo, branch `feat/wi-acq-auth-001`, HEAD
   `cfc456f4f469d9558a78fda8ff9b770b00817315`, origin, default model.
2. Verified the approved profile SHA256
   `de02c533…0e1fe481` before using its P1/P2 text.
3. Confirmed the four P2a1a protocol files were unchanged before editing.
4. Authored `Iso8583FieldReader` + `Iso8583FieldException` and one focused
   test class (`Iso8583FieldReaderTest`).
5. Compiled and test-compiled offline with JDK 25 / Maven; both exit 0.
6. Ran only `Iso8583FieldReaderTest`: 25 tests, 0 failures, 0 errors,
   0 skipped, exit 0.
7. Hashed all source/test/resource/pom files before and after the targeted
   run: byte-identical (141 files). HEAD unchanged, no tracked file modified.

One defect was found by the first local run (variable-read value offset ignored
the prefix width) and fixed before the final run; the failing-run logs were
replaced, not retained as evidence.

## Open / not done (intentionally)

- Whole structural scanner, presence matrices, DE48 canonical schema, DE38
  rules, amount/date/domain validation: open.
- Full test suite / regression: not run.
- Runtime, HTTP, AWS, mutation, deployment: not touched.
- P1 leftovers from prior slices remain open.
- G6 remains open; no Git write, no publication.

## Next action

Assemble the structural scanner for all enabled fields on this cursor, then
drive it with the approved packed fixtures (positive and negative), keeping
domain/admission checks in later layers.
