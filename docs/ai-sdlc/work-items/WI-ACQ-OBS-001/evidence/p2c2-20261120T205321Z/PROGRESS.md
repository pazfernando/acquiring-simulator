# P2c2 recovery progress

- Inspected prior `p2c2-20261120T205321Z`: hashes-before (209 inputs),
  input-paths, java/mvn/toolchain version notes; no targeted result log. The
  accepted operation was aborted with no pending state.
- Verified the four mapper artifacts already existed; the mapper test did not.
  Authored only that test with three bounded cases (oracle / context / budget).
- Captured binding and post-edit input hashes before the target run; ran
  `-Dtest=ServerResolvedInstructionMapperTest` only; captured stdout, stderr and
  exit immediately; captured post-run hashes and confirmed byte-identical.
- Result: 3/3 pass, exit 0, stderr empty. 1 exact-byte oracle assertion, 0
  roundtrip-body assertions.
- Did not touch Git, remote, cloud, credentials, specifications or vectors; no
  file recreated or deleted.
- Remaining: C4/HTTP/service/runtime wiring, dispatch, registry allocation and
  P1/G6 acceptance stay deferred and inactive.
