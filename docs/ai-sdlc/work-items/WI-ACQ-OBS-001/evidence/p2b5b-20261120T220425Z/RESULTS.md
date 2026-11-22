# P2b5b bounded recovery — actual results

## Binding
Branch `feat/wi-acq-auth-001`, HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`, origin
`https://github.com/pazfernando/acquiring-simulator.git`, default configuration, no override.
Temurin JDK 25.0.5 / Maven 3.10.1 offline, absolute paths used. Model `nan/deepseek-v4-flash`.
Gateway unwired, runtime inactive, P1 and G6 Security still pending.

## Inputs found
`signed gateway` `src/main/java/com/acquiring/simulator/adapter/aws/SignedEvidenceReceiptGateway.java`
and `src/test/java/com/acquiring/simulator/channel/SignedEvidenceReceiptGatewayIntegrationTest.java`
exist as saved, complete and compiling. No P2b5b result evidence existed before this run, which
confirms the aborted prior attempt.

## Run 1 — no source change
`mvn -o test -Dtest=SignedEvidenceReceiptGatewayIntegrationTest`
Result: `Tests run: 10, Failures: 1, Errors: 0, Skipped: 0`, exit `1`.
Sole failure: `declinedEd25519EvidenceCommitsTheSameAtomicReceiptWithDeclinedState`
expected `COMMITTED`, got `UNKNOWN`.

A read-only diagnostic run of that one method surfaced the gateway's own safe detail:
`UNKNOWN — persisted original record is absent`.

## Root cause (test defect, not a gateway defect)
The pinned decline vector `EF:V04-decline-response` / `V04-decline-response` describes
`AUTH-002` / `TX-002` / `CORR-002`, while the test helper seeded and asserted `AUTH-001` /
`TX-001` / `CORR-001` for every case. The gateway correctly refused to name a record that was
never persisted. Fixtures are pinned specification vectors, so they were not edited.

Fix (test only): the per-case business identity (`authorizationId`, `transactionId`,
`dispatchCorrelation`, `EventContext`) now comes from instance state initialised in `@BeforeEach`
to the original AUTH-001 values; only the decline case selects the fixture's actual AUTH-002
identity. No assertion was weakened: the decline case still asserts `COMMITTED`, `DECLINED`,
version `2/3`, `DECLINED-<authorizationId>` receipt event id, one receipt, three events, HEAD 3
and three slots, and now binds to the identity the signed evidence actually describes.

## Run 2 — after the test fix
`mvn -o test -Dtest=SignedEvidenceReceiptGatewayIntegrationTest`
Result: `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`, exit `0`, stderr empty.
Atomic, replay, conflict, unauthorized-context, ambiguous-commit and absent-original assertions
were left exactly as authored and all passed.

## Files changed
`src/test/java/com/acquiring/simulator/channel/SignedEvidenceReceiptGatewayIntegrationTest.java`
only. Verified by `hashes-frozen-pre-run.sha256` vs `hashes-post-edit.sha256` over 245
`src`/`pom.xml` inputs: exactly that one path differs. Gateway source unchanged.

## Not covered
No HTTP route, late-delivery envelope, service caller, key custody, production wiring, AWS
effect, full suite or regression sweep was run. Local construction evidence only — not
independent QA, Security review, human acceptance, business admission or deployment.
