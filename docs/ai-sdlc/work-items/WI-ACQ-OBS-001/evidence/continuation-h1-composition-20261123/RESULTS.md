# Continuation H1 — composition slice results (2026-11-23)

Developer evidence only: **not** independent QA, **not** Security review, **not** human acceptance.
No push, no PR, no merge, no deploy, no cloud call, no new dependency. Independent verification stays
**PENDING**.

## 1. Binding

| Field | Observed |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` (no commit made in this slice) |
| JDK | Temurin `25.0.5+8`, `JAVA_HOME=/home/pazfernando/.local/opt/jdk-25` |
| Maven | `/home/pazfernando/.local/opt/maven/bin/mvn`, offline (`-o`) |
| Docker | available; used only by `ConsoleHistoryRouteDynamoIntegrationTest` (DynamoDB Local container) |

Failures, when they occurred, were preserved; no attempt file was overwritten and no test case was
weakened, skipped or removed. Every run file is under `runs/`.

## 2. Compile

| Attempt | Command | Exit | Observation |
| --- | --- | --- | --- |
| 1 | `mvn -o -q -DskipTests compile` | **1** | `ConsoleHandler.java:47` — `ConsoleRuntime::consoleEntryPoint` was ambiguous between the `Function<String,String>` and `Supplier<ConsoleIntegrationEntryPoint>` constructors. Preserved in `runs/compile-attempt-1.*`. |
| 2 | `mvn -o -q -DskipTests compile` | **0** | Fixed by replacing the ambiguous method reference with `this(() -> ConsoleRuntime.consoleEntryPoint());`. No other production change. |

`runs/compile-attempt-1.{exit,stdout,stderr}` and `runs/compile-attempt-2.{exit,stdout,stderr}` hold
the exact exit and streams.

## 3. Tests

`ConsoleHandlerCompositionTest` was the one slice file still missing when the previous run stopped;
it was written in this continuation and is the composed-path proof required by §3.4 of `PROGRESS.md`.

| Suite | Command (each its own invocation) | Attempt | Exit | Tests | Failures | Errors | Skipped |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `ConsoleHandlerCompositionTest` | `mvn -o -B test -Dtest=ConsoleHandlerCompositionTest` | 1 | 0 | 5 | 0 | 0 | 0 |
| `ConsoleOriginVerificationGateTest` | `mvn -o -B test -Dtest=ConsoleOriginVerificationGateTest` | 1 | 0 | 9 | 0 | 0 | 0 |
| `ConsoleHistoryRouteTest` | `mvn -o -B test -Dtest=ConsoleHistoryRouteTest` | 1 | 0 | 20 | 0 | 0 | 0 |
| `ConsoleHistoryRouteDynamoIntegrationTest` | `mvn -o -B test -Dtest=ConsoleHistoryRouteDynamoIntegrationTest` | 1 | 0 | 4 | 0 | 0 | 0 |
| `ConsoleDevServerTest` | `mvn -o -B test -Dtest=ConsoleDevServerTest` | 1 | 0 | 9 | 0 | 0 | 0 |
| **Total** | | | | **47** | **0** | **0** | **0** |

Command shape used for every suite (streams kept separately, exit written immediately):

```
mvn -o -B test -Dtest=<Suite> >runs/<Suite>-attempt-1.stdout 2>runs/<Suite>-attempt-1.stderr
echo $? >runs/<Suite>-attempt-1.exit
```

The composition suite asserts the boundary control in the composed path, not the gate in isolation:

- no `X-Origin-Verify` on `POST /console/v1/authorizations` → fixed `403`
  `{"version":1,"error":"EDGE_ORIGIN_NOT_VERIFIED","action":null}`, application invocation count `0`,
  one denial record with reason `ABSENT`;
- a mismatched value → `403`, application count `0`, reason `MISMATCH`;
- the exact configured value → application invoked once, no denial record;
- absent, short or out-of-alphabet `EDGE_ORIGIN_VERIFY`, and absent
  `AWS_LAMBDA_FUNCTION_VERSION`, each prevent `ConsoleHandler` construction (`IllegalStateException`);
- the deployed composition and the local harness are unchanged in behaviour: gated requests for the
  existing routes answer `503`, an unknown gated path answers `404`.

## 4. What the deployed composition actually does — stated plainly

The deployed composition answers **`503 UNAVAILABLE`** for the two existing console routes
(`POST /console/v1/authorizations` and the history route shape) because it **deliberately does not
fabricate** an authorizer, a registry or a cursor-key collaborator. The real issuer/JWKS/token
verification (K1) and the history cursor signing-key custody (advisory A-4) are undelivered, and no
stub was invented to make a business path look functional.

So the honest statement is: the origin-verification gate **is invoked in the composed path** — it
refuses an unverified request with `403` before the application, and a verified request reaches the
composition — but the **business paths are not yet functional**; they return `503` after the gate.
The gate's presence does not imply that the console API works.

## 5. Scope limits

- No new endpoint, contract, Terraform, artifact or revision was added.
- No Git push, PR, merge, deployment or AWS/Keycloak/cloud call was made.
- No independent QA, Security review or human acceptance is claimed by this file.
