# P2b1 progress

Recovery state: **partial evidence recovered; no rerun performed.**

- Located the existing targeted run artifacts in `target/surefire-reports`
  (run `2026-11-20T16-10-53_008-jvmRun1`): 18 tests, 0 failures, 0 errors, 0 skipped.
- Confirmed `test=C4CarrierParserTest` in the Surefire XML properties, i.e. a
  targeted class run.
- Confirmed no saved stdout/stderr/exit file exists for that run; the prepared capture
  script aborted before writing. Provenance is therefore Surefire-artifact-level,
  weaker than stdout + exit code (stated in `run-provenance.md`).
- Captured current hashes of all `src/main` + `src/test` bytes, `pom.xml` and the
  carrier/evidence oracle vectors. Recorded mtime chronology of the run inputs,
  compiled classes and Surefire outputs.
- Extracted the 12-case carrier fixture category mapping from the test and
  `vectors/carrier-vectors.json` (ids and categories only; no payloads).
- No source edit, no new feature, no full regression, no rerun, no HTTP/credential
  flow, no git write, no publication, no cloud call, no deletion, no spec edit.
- Runtime remains inactive. P1/G6 remain open.

Deferred to the next slice: actual Ed25519 verification against a configured test
public key, plus P5 signed-schema/role/mandate and transport sender binding.
