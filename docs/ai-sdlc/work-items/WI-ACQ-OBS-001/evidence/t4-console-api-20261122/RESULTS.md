# T4 console API boundary — verification results, 2026-11-22

**Status: developer verification evidence on one working tree. Not independent QA, not Security
review, not human acceptance, not deployment.**

This run added no feature and no class. It executed the two console test classes that already
existed on disk and, because they were green, the atomic classes the console route delegates to.
No test was weakened, deleted or skipped.

## 1. Binding and runtime

| Item | Value |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` (`branch.txt`) |
| HEAD | `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` (`head.txt`; unchanged after the run, `head-diff.txt` empty) |
| Java | OpenJDK 25.0.5 2026-10-20 LTS, Temurin-25.0.5+8 (`runtime.txt`) |
| `JAVA_HOME` | `/home/pazfernando/.local/opt/jdk-25` |
| Maven | Apache Maven 3.10.1 (`3afd8084b5629f80b97750906a719879ed4cd283`), `/home/pazfernando/.local/opt/maven/bin/mvn`, offline `-o` |
| Docker | 29.1.3; image `amazon/dynamodb-local:2.6.1` present locally |
| Model observed | `nan/deepseek-v4-flash` (session model, no override) |
| Cloud | no AWS call, no credentials, no deployment, no publication, no Git write |

`mvn` and `java` are not on the default `PATH`; both commands below set `JAVA_HOME` and called the
absolute Maven launcher. No launcher attempt failed in this run, so there is no disclosed 127
attempt to preserve.

## 2. Command 1 — the two console classes

Command, exactly once:

```
JAVA_HOME=/home/pazfernando/.local/opt/jdk-25 \
/home/pazfernando/.local/opt/maven/bin/mvn -o -B test \
  -Dtest=ConsoleAuthorizationRouteTest,ConsoleSubmissionAtomicIntegrationTest
```

| Item | Value |
| --- | --- |
| Exit | `0` (`console-tests-exit.txt`) |
| Result | `BUILD SUCCESS` |
| Aggregate | **Tests run: 16, Failures: 0, Errors: 0, Skipped: 0** (`console-surefire-summary.txt`) |
| Stderr | empty, 0 bytes (`console-tests-stderr.txt`) |

Per class, cross-checked against the Surefire XML attributes:

| Class | Tests | Failures | Errors | Skipped |
| --- | --- | --- | --- | --- |
| `ConsoleAuthorizationRouteTest` | 13 | 0 | 0 | 0 |
| `ConsoleSubmissionAtomicIntegrationTest` | 3 | 0 | 0 | 0 |
| **Total** | **16** | **0** | **0** | **0** |

Because the result is green, no `console-analysis.md` was written and no failing case required a
diagnosis.

## 3. Command 2 — directly affected atomic classes

The console integration test wires the same atomic collaborators the AWS runtime installs
(`DynamoAuthorizationRepository`, `DynamoEventStore`, `DynamoClaimGateway`,
`DynamoDispatchIntentGateway`, `DynamoResultGateway`, the atomic `AuthorizationService`
constructor). Their wiring, gateway and runtime-binding classes were run once:

```
JAVA_HOME=/home/pazfernando/.local/opt/jdk-25 \
/home/pazfernando/.local/opt/maven/bin/mvn -o -B test -Dtest=\
AwsRuntimeAtomicWiringIntegrationTest,AtomicClaimServiceWiringIntegrationTest,\
DispatchIntentServiceWiringTest,ResultServiceWiringTest,\
DynamoAtomicClaimRecoveryIntegrationTest,DynamoCriticalClaimIntegrationTest,\
DynamoDispatchIntentGatewayIntegrationTest,DynamoResultGatewayIntegrationTest,\
DynamoResultTransitionIntegrationTest
```

| Item | Value |
| --- | --- |
| Exit | `0` (`atomic-tests-exit.txt`) |
| Result | `BUILD SUCCESS` |
| Aggregate | **Tests run: 66, Failures: 0, Errors: 0, Skipped: 0** (`atomic-surefire-summary.txt`) |
| Stderr | empty, 0 bytes (`atomic-tests-stderr.txt`) |

Per class (Surefire XML cross-check):

| Class | Tests |
| --- | --- |
| `AwsRuntimeAtomicWiringIntegrationTest` | 10 |
| `DynamoCriticalClaimIntegrationTest` | 8 |
| `DynamoAtomicClaimRecoveryIntegrationTest` | 4 |
| `DynamoDispatchIntentGatewayIntegrationTest` | 4 |
| `DynamoResultGatewayIntegrationTest` | 6 |
| `DynamoResultTransitionIntegrationTest` | 7 |
| `AtomicClaimServiceWiringIntegrationTest` | 6 |
| `DispatchIntentServiceWiringTest` | 9 |
| `ResultServiceWiringTest` | 12 |
| **Total** | **66** |

Selection is bounded to claim, dispatch-intent and result, because an approval on the console path
does not execute the late-evidence or network-result ports; those classes were left out
deliberately, not skipped to hide a failure.

## 4. What these bytes do prove

Within the test seams (a test-double token decoder and a fixed context registry), against DynamoDB
Local:

1. **An authorized submission reaches the atomic service exactly once.**
   `authorizedSubmissionCommitsOnePaymentThroughTheWiredAtomicPath` gets HTTP 200, exactly one
   downstream dispatch, one persisted `APPROVED` payment at generation 1, three critical event rows
   (claim, possible-send, verified result), HEAD sequence 3, and zero legacy `claim`,
   `markDispatched`, `updateOutcome`, `appendEvent` or `appendRejection` calls. The recording-service
   boundary test independently asserts one `authorize` call with the registry's trusted context,
   not the caller-declared one.
2. **A rejected token leaves zero business writes and zero sends.**
   `refusedTokenRecordsNoClaimNoEventAndNoSend` gets 401 with zero dispatches, zero claims, zero
   updates, zero appends, zero rejection audits, an empty event table and no persisted record. The
   route-level refusal cases (missing, duplicated, malformed, wrong issuer/audience/subject/client,
   expired, missing scope, wrong media type, unknown field, oversized body, foreign context,
   missing registry context, rate-limit refusal) all assert zero service calls.
3. **A duplicate submission replays the stored result.**
   The second identical POST returns the same authorization reference with one total dispatch, the
   same three events and no second claim.

## 5. What these bytes do not prove

- **No token signature or issuer truth.** The decoder is a test double for the seam; no Keycloak,
  JWKS, `private_key_jwt`, mTLS or signature verification runs. A green result is evidence about
  policy and ordering, not cryptography or a deployed authorizer.
- **No browser-visible surface.** There is a request/response class, not a listener, page, form,
  relay, SPA, history read, timeline, WebSocket or rate-limiting numeric bound. No API Gateway,
  CloudFront, WAF or CORS configuration exists.
- **No deployed submission.** `AwsRuntime.processor()` is not executed here; no Lambda invocation,
  no real issuer/network round trip, no mounted internal ISO route.
- **Partial response envelope.** A reached-service approval returns a provisional
  `{authorizationReference, correlationId, outcome}` acknowledgement, not the closed thirteen-key
  C3 envelope; the pre-admission refusals do use `C3ResponseBuilder`.
- **Not independent.** This is developer evidence on an uncommitted working tree.

## 6. Freeze and limitation

The 13 console slice files were hashed before and after the runs:
`slice-hashes-before.sha256` and `slice-hashes-after.sha256` are identical
(`slice-hash-diff.txt` empty), `git-status-diff.txt` and `head-diff.txt` are empty. No source,
test, resource or `pom.xml` was mutated by either Maven run.

**Freeze limitation:** this is a **post-edit baseline**. The console package and both test classes
are untracked at HEAD, and `AwsRuntime.java` / `AuthorizationService.java` were already modified
before this run. The freeze proves the run changed nothing in the 13 slice files; it does **not**
prove those bytes are committed or pre-edit. The repository-wide freeze from
`evidence/t3-fullsuite-20261122/` covers the same commit but a different recorded moment.

## 7. Non-claims

No Git write, stage, commit, push, PR, merge, deploy, release, publication, AWS call, credential
flow, provider mutation, approved-specification edit or invented capacity default occurred. No
onward delegation, no model override. These bytes are not an authorized deployed submission and do
not close T4, T6 or any integrated row.
