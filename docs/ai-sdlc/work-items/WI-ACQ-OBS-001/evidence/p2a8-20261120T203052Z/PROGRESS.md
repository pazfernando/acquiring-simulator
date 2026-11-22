# P2a8 progress — one ordered request-validation pipeline

Status: **IMPLEMENTED AND LOCALLY VERIFIED** for the bounded pure request
pipeline slice.

Evidence: `EVIDENCE-MANIFEST.md`, `BINDING.txt`,
`hashes-frozen-pre-run` / `hashes-after`, hash diffs,
`request-first-failure-mapping.tsv`, `ATTEMPTS.md`, git status, the single
combined protocol log, `toolchain-version.txt`.

## Delivered

One pure
`Iso8583RequestValidationPipeline.evaluate(body, receivedAt, mode)` that runs
the existing components in the approved P6 order and stops at the first
failure:

1. `Iso8583Scanner` — MTI + primary bitmap, presence matrix, ascending field
   framing/alphabet, exact end of body;
2. `Iso8583De48RequestParser` — canonical DE48 schema;
3. `Iso8583DomainValidator` — profile codes, DE4 amount, DE49 currency,
   Gregorian `sent`, DE7;
4. `Iso8583FreshnessValidator` — NEW first-dispatch 300-second window;
5. `Iso8583ExchangeBudgetValidator` — NEW request 826-byte budget.

Supporting files:

- `Iso8583RequestValidation` — validated structural/domain envelope. It exposes
  the trusted mode, message, canonical extension, domain projection, freshness
  and budget decisions. It deliberately has **no** accepted/admitted/authorized/
  authenticated flag.
- `Iso8583RequestValidationException` — the first failing stage and the stable
  approved first-failure label.

The admission mode is a trusted server-side enum, never a wire field.
`AUTHORIZED_HISTORICAL_REPLAY` and `LATE_EVIDENCE` still validate structure,
schema and domain, but return `NOT_APPLICABLE` for freshness and the 826 budget:
the persisted original is not re-encoded, re-admitted or re-dispatched as a new
wire claim. The request boundary accepts MTI `0100` only; a structurally valid
`0110` is rejected as `PARSER_MTI`.

No pre-existing source, scanner, validator, profile, vector, fixture, resource
or specification file was edited. The scanner needed no ordering correction:
its actual order already is header/bitmap → presence → ascending fields → end
of body, and every request fixture matched the approved oracle.

## Actual results

- Approved `0100` request corpus: **43** fixtures = **9 ACCEPT** + **34 ordered
  negatives**. All **43/43** expected labels matched the actual first failure.
- `Iso8583RequestValidationPipelineTest`: **53/53**, failures 0, errors 0,
  skipped 0, exit `0`. That is 43 corpus cases + 10 composition/scope cases.
- One combined `-Dtest='Iso8583*Test'` invocation: **472/472**, failures 0,
  errors 0, skipped 0, exit `0`, stderr empty. The pipeline class itself ran
  `53/53` inside that same run; the protocol set is `419 prior + 53 new`.
- Main compile: 126 sources; test compile: 52 sources.
- Inputs: P2a7 final **184** → frozen pre-run **188** → after **188**; the only
  signed diff against P2a7 is the **four added files**; frozen and after are
  byte-identical (empty diff, exit 0); `git status` and HEAD unchanged.

### Request fixture label distribution (actual)

| First failure | Count |
| --- | ---: |
| `ACCEPT` | 9 |
| `PARSER_FORBIDDEN` | 2 |
| `PARSER_REQUIRED` | 1 |
| `PARSER_BITMAP` | 1 |
| `PARSER_SECONDARY` | 1 |
| `PARSER_MTI` (corpus) | 0 |
| `PARSER_TRUNCATED` | 1 |
| `PARSER_END_BODY` | 1 |
| `PARSER_LENGTH` | 1 |
| `PARSER_ALPHABET` | 2 |
| `PARSER_ASCII` | 1 |
| `SCHEMA_CANONICAL` | 6 |
| `SCHEMA_LENGTH` | 1 |
| `DOMAIN_AMOUNT` | 2 |
| `DOMAIN_CURRENCY` | 1 |
| `DOMAIN_DATE` | 2 |
| `DOMAIN_DE7` | 1 |
| `ADMISSION_FRESHNESS` | 1 |
| `ADMISSION_RESPONSE_CAPACITY` | 9 |

The exact fixture-by-fixture expected/observed mapping is saved as
`request-first-failure-mapping.tsv` (43 rows, all `match=true`).

### Multiply-invalid ordering proofs (actual)

- Unknown DE48 key + stale timestamp → `SCHEMA_CANONICAL`.
- Unknown DE48 key at exactly 999 bytes → `SCHEMA_CANONICAL` (schema precedes
  capacity).
- Non-USD currency + stale timestamp → `DOMAIN_CURRENCY`.
- Non-USD currency + 827-byte request → `DOMAIN_CURRENCY`.
- Stale timestamp + 827-byte request → `ADMISSION_FRESHNESS`.
- Five repeated evaluations of the same multiply-invalid request →
  `DOMAIN_CURRENCY` every time.

No fixture was forced to a label to match a faulty oracle; the ordering is the
approved phase order, and the actual results agreed with every approved request
vector.

## Initial pre-final attempts (production code unchanged)

Recorded in `ATTEMPTS.md`: one test-helper compile error (`JsonNode` passed
where a `String` body was expected) and two focused tests that re-applied the
stale mutation to the already-stale `D-stale` fixture. Both fixes were in the
new test only. The final frozen run is the single clean combined run above.

## Not covered / deferred

Registry/fixture/mandate/route resolution, authentication/attestation, P4 matrix
and instruction/DE38 binding, duplicate/conflict semantics, claim, dispatch,
response construction/packing, C4 carriage, HTTP route, production caller and
runtime wiring remain deferred. The P5 negative-case family and the remaining
response-path checks are still open. P1 and G6 remain open. Local JUnit and
byte-fixture evidence only; not QA, Security review or human acceptance.

## Next slice

The next bounded seam is the response-side ordered pipeline (scanner → canonical
DE48 response schema → P4 → domain → binding) or, separately, the pure ACL
principal/role eligibility boundary. Registry and duplicate/claim remain outside
both.
