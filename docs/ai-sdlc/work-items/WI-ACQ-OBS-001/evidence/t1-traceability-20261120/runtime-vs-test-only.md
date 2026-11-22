# Runtime path versus test-only construction — concrete evidence

This file supports the axis used by [`requirement-traceability.md`](../../requirement-traceability.md).
Claims are checked against the source at HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`.

## 1. The deployed entry point still constructs the legacy service

`src/main/java/com/acquiring/simulator/adapter/aws/AwsRuntime.java`, `processor()`:

```java
return new AuthorizationService(new DynamoAuthorizationRepository(db, env("JOURNAL_TABLE")),
        new LambdaDownstream(lambda(), env("MASTERCARD_FUNCTION")), props, Clock.systemUTC());
```

That is the four-argument constructor: repository plus downstream plus configuration. It passes
**no** `ClaimGateway`, `DispatchIntentGateway`, `ResultGateway` or `NetworkResultGateway`, and it
constructs no `DynamoEventStore`.

`AuthorizationService` offers eight constructors. The longest (eight-argument) form is the only one
that composes the atomic claim → dispatch-intent → result → network-result chain. That form is
constructed **only by tests** (`Dynamo*IntegrationTest`, `*ServiceWiringTest`,
`ObsAcceptanceAtomicChainIntegrationTest`).

Consequence: every P1/P2 atomic capability is *test-only construction*. The requirements and
scenarios that depend on it cannot be called integrated.

## 2. The channel/ISO boundary is not mounted

- `HttpApiV2InternalRouteTest` exercises payload v2 request/response decoding as a **primitive**. Its
  own recorded limit (implementation-progress.md §53) is: "No HTTP endpoint/runtime activation".
- `ProcessorHandler` routes the legacy JSON payload, not
  `POST /internal/sim-iso87a/v1/authorizations`.
- `C4HttpCarriageBridge` never builds trust itself; it accepts a
  `C4AuthenticatedTransportContext` supplied by the platform boundary. The comment and the P2b8
  record both state the C2 principal mapping "does not exist in this repository".

## 3. Transport authentication does not exist

| Control | What is executed | What is absent |
| --- | --- | --- |
| Ed25519 evidence signing | `C4EvidenceVerifierTest` (33), `C4P6SignedFieldBinderTest` (18), `C4LateDeliveryTest` (23) — real JDK signatures, ephemeral in-memory keys | Key custody, rotation, revocation |
| Transport trust | `C4HttpCarriageBridge` contract shape only | TLS, mTLS, IAM/SigV4 principal, OAuth 2.0 `private_key_jwt` |
| Console identity | `ProcessorHandler` reads `requestContext.authorizer.jwt.claims.sub` and resolves a `CALLER#<subject>` registry row | Demo relay, single-demo-user boundary proof |

The owner ADR selects **API Gateway (external/internal per scenario) + mTLS + OAuth 2.0
`private_key_jwt`**. None of that is implemented. `private_key_jwt` authenticates an OAuth client at
the *token endpoint*; it is not an API access token and it is not C4 attestation evidence.

## 4. Absent deliverable components (searched, not assumed)

| Component | Check | Result |
| --- | --- | --- |
| Console / HTML / browser assets | no `src/main` UI or template directory; no HTML resources under `src/main` | absent |
| WebSocket handling | `grep -rl "WebSocket\|websocket" src/main` | no matches |
| History read model | `EventStore` port exposes only `append`, `find`, `head` — no list, page, cursor or expiry | absent |
| Streams consumer / notifier | no consumer, publisher or notifier class | absent |
| ADOT / telemetry export | no ADOT dependency or exporter configuration | absent |
| Retention / TTL / expiry | no TTL attribute, expiry window or truncation logic | absent |
| Terraform | no `*.tf` anywhere | absent |
| GitHub Actions | no `.github/workflows` | absent |
| Deployable infrastructure | `infra/template.yaml` only — SAM (`AWS::Serverless-2016-10-31`), self-described "not deployed", retained as historical/reference per the completion plan | no Terraform parity |

## 5. What "integrated" would require

An actual journey: browser submission → authenticated service ACL/ISO boundary → atomic claim,
dispatch intent, issuer/network result → atomic business + critical event commit → live task
timeline → history reload → reconnect without resubmission. No test executes that chain; the
strongest executed chains stop at service-plus-adapters against DynamoDB Local.
