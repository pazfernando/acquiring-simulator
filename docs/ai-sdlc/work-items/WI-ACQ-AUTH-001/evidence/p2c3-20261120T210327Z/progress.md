# P2c3 progress

## Delivered

- `C3ResponseEnvelope` — immutable, closed **13-key** C3 envelope. Exact key set is
  asserted; no extra key, no response authentication flag, no raw ISO/attestation/issuer
  private value is representable. Canonical compact JSON matches the vector `bodyUtf8`
  form; size is capped at 4096 UTF-8 bytes; identifiers are bounded to the profile
  alphabet; `businessVersion` is null or positive; `outcome`/`disposition` coupling is
  enforced; `UNKNOWN` observation requires `UNKNOWN` freshness.
- `C3Response` — typed immutable `(HttpStatus enum, envelope)` pair.
- `C3ResponseBuilder` — one pure precedence function over trusted internal facts:
  denial → pre-admission → history-unknown → readable confirmation → unsent claim or
  positive-unsent admission → current possible-send with unavailable journal → readable
  active/deadline possible send → unreadable restart. It performs no I/O, reads no clock
  and has no send/retry/Retry-After path. Async defects only degrade observation,
  freshness and the async error code; a committed confirmation keeps its outcome and
  version.
- `C3ResponseBuilderTest` — 9 tests; one executes all 26 approved cases and compares both
  expected HTTP status and exact canonical body. Focused tests cover the closed key set,
  denial non-leak, 401, 404, the full pre-admission status map, and envelope rejections.

## Key-count correction

C3 lists **13** keys. The coordination note asked to verify "14?" against C3 rather than
the prompt: the contract text and all 26 fixture bodies agree on 13.

## Explicitly deferred

- HTTP adapter/emission, C4 evidence carriage, storage/domain wiring.
- C1/C2/C5–C8 coupling and runtime behavior. The service remains runtime-inactive.
- P1/G6 remain open; P4 remains a precondition for this layer.

## Sequencing, not authority

C4 signed evidence carriage and the HTTP adapter are already authorized for local
construction. "Deferred" here means pending sequencing after this envelope/builder slice,
not missing user authority. No spec, gate or policy was reopened.

## Known limits

- Fixture observation quality for H02/H03/H04/H20/H23/H24/H25 is modelled as async
  source-health facts; combined simultaneous defects are ordered by the builder
  (event history → delivery gap → live notifications → store lost after read) and are
  not themselves fixture cases.
- No independent QA, Security review or human acceptance is implied.
