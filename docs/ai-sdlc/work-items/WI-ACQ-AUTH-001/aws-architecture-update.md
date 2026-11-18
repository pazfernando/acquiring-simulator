# Operation A AWS architecture update

Date: 2026-11-18. Owner: Fernando Paz. Status: implementation update authorized by the owner's current request; developer verification pending below. Not independent QA, Security approval, deployment authorization or human acceptance.

## Authority and bounded supersession

Owner supplied the architecture folder and requested: “hubo actualizaciones a software_component_catalog y de hecho ya tenemos arquitectura en software_components_aws ... Actualiza la implementacion de nuestro scope ... Operations A”. The checked-out work item confirms Operation A only: Payment Authorization Request. This request authorizes adopting the supplied AWS mapping for that slice; it does not add operations B/C, CNP/3DS, partial approvals, stand-in, live Mastercard connectivity, capture or settlement.

`architecture-decision.md` remains the immutable evidence of the earlier local SQL/in-process approval. For the AWS execution path, this record supersedes its SQL storage, no-HTTP and combined in-process downstream choices. Java 25, USD, synthetic CP credentials, validated business scope, 24-hour idempotency window, restricted response fields and no automatic redispatch remain. Spring Boot/SQLite/PostgreSQL remain a local regression harness, not the new AWS test/prod design.

## Versioned upstream inputs

Connector-readable snapshots, with provenance/hashes in `../../sources/imports/2026-11-18/manifest.json`:

- Functional guide v0.5.2, especially §13. Prior v0.5.1 source and approved requirements/scenarios/review hashes are preserved, not relabeled as reviewed v0.5.2.
- `software_components_aws.md` v0.3.0, evolving design. It consolidates logical components and AWS mapping; its own text retires the older catalog/mapping after acceptance. A specific Drive search returned the consolidated file, not a separate current catalog.
- Upstream C1/C2 Mermaid snapshots are working proposals describing A/B/C. They are reference evidence, not proof that all those operations are implemented. Scope-specific C2 is under `docs/architecture/`.

## Implemented AWS boundary

- HTTP API `POST /authorizations`, JWT authorizer required. No other operation endpoint.
- Processor Java Lambda uses the existing domain service and DynamoDB adapter. Logical validation, risk, routing and authorization remain modules inside it.
- Mastercard Lambda invokes a separate Issuer Lambda. Issuer decisions are journaled in a separate DynamoDB table inaccessible to the Processor role. Internal issuer truth is never used as proof that the Processor received it.
- DynamoDB on-demand: authorization records, business-key generation head, evidence and rejection audit, active acquirer/merchant/terminal/credential profile, and subject-to-context registry. Profile and caller records must be explicitly provisioned before use; missing registry entries fail closed.
- Conditional transactional claim publishes the record and generation head atomically. Strong reads and version-checked record replacement prevent lost updates. Confirmed states cannot regress to uncertainty. No TTL deletes linked references or idempotency heads.
- Separate issuer truth table deduplicates the exact authorization instruction; conflicting repeated instructions fail rather than create another issuer decision.
- Operator-controlled network failure configuration; request bodies cannot choose faults, caller identity or permissions. There is no direct public function URL.
- CloudWatch standard Lambda metrics/log groups with seven-day log retention. Application does not log bodies or credentials. No custom tracing/metrics ingestion pipeline is claimed.

## Synthetic contract v1 and timing

`SimulatedDecisionRequest` carries authorization ID, correlation ID, fixture-derived decision, logical route label, dispatch time and response timeout. `DownstreamReply` carries correlation, decision and evidence availability time. JSON uses ISO-8601 instants/durations. This is an internal fixture contract, not a Mastercard single-message/ISO mapping. Both existing DOMESTIC/INTERNATIONAL route labels use the same synthetic downstream realization; no real domestic switch or new scheme rule is implied.

The AWS path defaults to a two-second processor response boundary, while the legacy local harness retains its approved 30-second default. Lambda SDK invocations disable automatic retries; each call is bounded by the request response timeout. Logical delay injection returns an availability timestamp after the deadline without sleeping or relying on AWS timeout scheduling. An on-time valid reply yields issuer APPROVED/DECLINED; loss/error/mismatch yields the existing `NOT_CONFIRMED` API value (business UNKNOWN). Explicit pre-issuer rejection yields `PROCESSOR_REJECTED`, with NETWORK provenance internally. A late correlated reply follows existing late-evidence behavior: first response is unconfirmed, later retry sees recorded confirmed evidence. If a real remote invocation times out, its later result is NOT automatically harvested: uncertainty persists until separately delivered verified evidence. No late-evidence public endpoint or automatic reconciliation worker is included.

The initial synthetic contract is implemented for A only; real network single-message details remain unspecified. Environment switching/fixture seeding, infrastructure costs and deployment limits must be verified in an isolated AWS account before deployment, separately authorized.

## Scenario delta

- NET-01: explicit network rejection without issuer invocation.
- NET-02 / ISS-03: unavailable/no-response remains uncertain, never fabricated issuer decline.
- NET-03 / ISS-04: issuer approval is independently persisted; dropped network reply leaves Processor uncertain. Duplicate submission does not redispatch. A later explicit correlated evidence submission can reconcile.
- NET-04 / ISS-05: logical late response and idempotent evidence handling.
- NET-05: malformed/mismatched evidence is not issuer confirmation.
- ISS-01/02: delivered issuer approval/decline retains issuer provenance.
- PROC-01: concurrent duplicate, restart and generation-expiry coverage on DynamoDB Local.
- REV-01/02, INQ-01 and the reversal/inquiry observations of the priority end-to-end scenario are explicitly deferred to B/C. Internal record inspection in tests is NOT an implemented Operation C.

The old independent domain PASS applies only to its pinned specification bundle. This new network/provenance/HTTP delta still needs independent domain review, QA and triggered Security review; no installed reviewer is impersonated. Developer tests cannot approve these gates.

## Observed developer verification

Executed on the local task branch `feat/wi-acq-auth-001`, using Java 25.0.5 and Maven 3.10.1:

- `mvn -q test package`: PASS, 64 tests, zero failures/errors/skips. Existing SQLite scenarios: 24; PostgreSQL scenarios: 24; new DynamoDB Local scenarios: 10; Lambda transport/HTTP unit tests: 6. No AWS account used.
- The first run exposed an empty DynamoDB ExclusiveStartKey in history queries; corrected to omit the field until a real pagination cursor exists. Full final suite rerun passed.
- `cfn-lint` 1.60.1 on `infra/template.yaml`: exit 0, no diagnostics. This is static lint, not an applied CloudFormation change set or proof of IAM/runtime/JWT behavior in AWS.
- Packaged-classpath smoke instantiated the actual default ProcessorHandler from `target/lambda` and returned HTTP 403 without JWT, without an AWS call or credentials. The Lambda artifact is about 44 MiB uncompressed, below the standard 250 MiB zip-package limit.
- Mermaid CLI 11.12.0 exported `docs/architecture/operation-a-aws.png` from the matching `.mmd`; visually inspected the export. Upstream imported diagrams remain unchanged.
- Four imported source SHA-256 identities checked against the manifest. Five original requirements/profile/scenarios/domain-review/architecture approval MD5 identities remain unchanged.
- Offline fixture renderer exercised: 2 acquirers, 3 merchants, 3 credential ranges; no table write performed. `git diff --check` passed.

Not verified: deployed Lambda execution, real API Gateway JWT validation, account IAM behavior, costs/load/cold starts, end-to-end real AWS fault timing, or independently reviewed security/domain/QA gates. No deployment, remote, push, PR, merge or release performed.
