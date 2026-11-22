# P2a9 progress — one ordered response-validation pipeline

Status: **IMPLEMENTED AND LOCALLY VERIFIED** for the bounded pure response
pipeline slice.

Evidence: `EVIDENCE-MANIFEST.md`, `BINDING.txt`, `hashes-before.sha256`,
`hashes-frozen-pre-run.sha256`, `hashes-after.sha256`, hash diffs, git status,
`response-first-failure-mapping.tsv`, targeted/protocol logs and exit files,
`toolchain-version.txt`, the JUnit XML, and the preserved pre-freeze attempts.

## Delivered

One pure
`Iso8583ResponseValidationPipeline.evaluate(body, original, trusted)` that runs
the approved P6 response order and stops at the first failure:

1. `Iso8583Scanner` — MTI + primary bitmap, presence matrix, ascending field
   framing/alphabet, exact end of body;
2. `Iso8583De48ResponseParser` — canonical DE48 response schema;
3. `Iso8583DomainValidator.validateResponse` — profile codes, DE4 amount, DE49
   currency, Gregorian echoed `sent`, DE7 against the original `sent` (never
   `at`);
4. `Iso8583P4MatrixValidator` — actor/stage/DE39/result/reason;
5. `Iso8583InstructionBindingValidator` — trusted original-wire hash, the
   complete instruction/context echo, then exact DE38 presence/value.

Supporting files:

- `Iso8583ResponseValidation` — validated structural/domain envelope with the
  scanned message, canonical extension, domain projection, P4 eligibility and
  equality-only binding. It has **no** verified/accepted/authorized/
  authenticated/admitted flag, and no freshness or budget component.
- `Iso8583ResponseValidationException` — stage plus the stable approved
  first-failure label.

**Trusted-record precondition.** Authentication and attestation verification are
not implemented here. The caller must supply the already-verified persisted
original instruction and the already-verified trusted-evidence projection from
the internal trusted seam. A missing record fails closed at `TRUST` with
`TRUSTED_RECORD_REQUIRED`; a malformed trusted hash or DE38 assertion fails
closed with `TRUSTED_RECORD_INVALID`. The pipeline never hashes the received
response and never substitutes a hash, so an attested original hash is compared
as supplied. There is no response re-encoding.

No freshness window and no 826-byte request budget is applied to a response or
to a historical instruction read, and no `0110` is fabricated for a timeout.

## Actual results

### Covered response corpus (38 of the 41 approved `0110` fixtures)

| First failure | Count |
| --- | ---: |
| `ACCEPT` | 22 |
| `SCHEMA_CANONICAL` | 3 |
| `SCHEMA_ENUM` | 1 |
| `DOMAIN_DATE` | 1 |
| `DOMAIN_DE7` | 1 |
| `EVIDENCE_MATRIX` (P4) | 5 |
| `BINDING_INSTRUCTION` | 3 |
| `BINDING_AUTH_REFERENCE` | 2 |

All **38/38** expected labels matched the actual first failure. The
fixture-by-fixture mapping is `response-first-failure-mapping.tsv` (38 rows, all
`match=true`).

### Explicitly deferred (not executed, not presented as accepted)

| Fixture | Declared later phase |
| --- | --- |
| `N-forged-actor` | `AUTH_ATTESTATION` |
| `N-unresolvable-evidence` | `AUTH_ATTESTATION` |
| `N-conflicting-confirmation` | `BUSINESS_CONFLICT_QUARANTINE` |

A dedicated test proves these three IDs are in the 41-response wire corpus,
carry those declared later-phase oracles, and are disjoint from the covered
execution set. They are not run through this pipeline.

### Multiply-invalid ordering proofs (actual)

- Renamed `evidence` key on `N-response-DE7` (+ invalid DE7) → `SCHEMA_CANONICAL`.
- `N-00-network` with DE49 changed to `978` → `DOMAIN_CURRENCY` (domain before
  P4 and binding).
- `N-wrong-correlation` with an equally long stage change → `EVIDENCE_MATRIX`
  (P4 before binding).
- `N-unsolicited-DE38` with a `cid` echo mismatch → `BINDING_INSTRUCTION`
  (instruction echo before the DE38 reference).
- Five repeated evaluations of a domain+stage multiply-invalid response →
  `DOMAIN_CURRENCY` every time.

### Test executions

| Run | Exit | Result |
| --- | --- | --- |
| `Iso8583ResponseValidationPipelineTest` (post-freeze, one run) | `0` | **51/51**, failures 0, errors 0, skipped 0 |
| combined `-Dtest='Iso8583*Test'` (one run) | `0` | **523/523**, failures 0, errors 0, skipped 0 |

`523 = 472 prior + 51 new`. The 51 targeted cases are 38 corpus cases plus 13
composition/scope cases. Stderr is empty in both runs.

### Inputs

- `hashes-before.sha256` is the recovered P2a8 frozen list: **188** inputs.
- `hashes-frozen-pre-run.sha256` = **192** inputs; the diff against P2a8 is
  **only the four added files**.
- `hashes-frozen-pre-run.sha256` and `hashes-after.sha256` are byte-identical
  (empty diff, exit `0`), so the single post-freeze runs above correspond to the
  frozen tree.
- `git status` before/after/final is identical; HEAD is unchanged.

## Preserved attempts (unique failures)

- `targeted-compile-failure-attempt1.txt`: test compilation failed because the
  new test called `List.join`, which does not exist. Production code was not
  changed; the one-line test fix was applied and `targeted-pre-freeze-attempt2.txt`
  passed **51/51**. Both are preserved, not deleted or rewritten.
- `compile-pre-freeze.txt`: pre-freeze `mvn -o -q -DskipTests compile` had exit
  `0` with no output.
- The post-freeze targeted run and protocol regression are the only
  authoritative executions for the final tree.

## Not covered / deferred

Authentication, attestation signature/evidence resolution (the two
`AUTH_ATTESTATION` fixtures), business-conflict/duplicate admission
(`N-conflicting-confirmation`), environment/producer/relay principals,
`responseSha256`/signing principal/key, transport and durable processor
receipt, registry/mandate/route/fixture resolution, response construction and
packing, C4 carriage, HTTP route, production caller and runtime wiring remain
deferred. P1 and G6 remain open. Local JUnit and byte-fixture evidence only;
not QA, Security review or human acceptance.

## Next slice

ONE pure ACL principal/role eligibility boundary seam, with no claim, dispatch,
HTTP or runtime wiring. Authentication and attestation verification remain a
separate later seam, as does business-conflict admission.
