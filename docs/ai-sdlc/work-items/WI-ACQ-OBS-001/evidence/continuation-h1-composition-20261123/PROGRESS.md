# Continuation H1 — composition slice: make the origin-verification gate run (2026-11-23)

Developer evidence only: **not** independent QA, **not** Security review, **not** human acceptance.
This directory records what was executed and observed. Independent verification stays **PENDING**.

## 1. Binding (observed before any edit)

| Field | Observed |
| --- | --- |
| Repository realpath | `/home/pazfernando/projects/acquiring-simulator` |
| `git rev-parse --show-toplevel` | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` |
| Working tree | dirty by design; preserved, nothing reset, checked out or cleaned |
| `git status --porcelain=v1` digest before edits | `f2936651a514596f2c960f21a085b6d0e465f9764a4befe6772a7e3982e52eaf` |
| JDK | Temurin `25.0.5+8`, `JAVA_HOME=/home/pazfernando/.local/opt/jdk-25` |
| Maven | `/home/pazfernando/.local/opt/maven/bin/mvn`, offline (`-o`) |
| Model | provider `nan`, model `deepseek-v4-flash`; no override |

No commit is made in this slice unless a later section records one. Local commits only; no push, no
PR, no merge, no deploy, no AWS/Keycloak/cloud call, no new dependency.

## 2. Commission and starting finding

Security review `reviews/n02-implementation-20261123/security-review.md` §10 **A-2** (binding
follow-through): `ConsoleIntegrationEntryPoint` is referenced only by its own file and its test; no
console Lambda handler exists and the `console/dev` harness predates the gate. Nothing deployable
refuses the direct `api_fqdn` path today. A-2's mitigation: the deployment/adapter composition must
construct the entry point via `fromEnvironment()` (missing/malformed `EDGE_ORIGIN_VERIFY` fails
initialization, alertable) and route every console integration request through `handle`.

## 3. What this slice intends

1. Add the console Lambda handler at the class path already pinned by Terraform
   (`com.acquiring.simulator.adapter.aws.ConsoleHandler::handleRequest`, `infra/terraform/modules/api/variables.tf`)
   and a cold-start composition (`ConsoleRuntime`) that builds
   `ConsoleOriginVerificationGate.from(System::getenv)` → `ConsoleIntegrationEntryPoint`.
2. Keep the gate mandatory: absent/short/bad-alphabet `EDGE_ORIGIN_VERIFY` throws during handler
   construction; there is no default, flag or degraded path.
3. Wire the local dev harness through the same entry point. The harness acts as the local edge and
   injects the header (replacing any caller-supplied value); it refuses to start when
   `EDGE_ORIGIN_VERIFY` is absent. No bypass exists in production code.
4. Prove in composed-path tests (not isolated gate unit tests): no header → 403 before the
   application; valid header → application reached; malformed configuration → handler cannot be
   constructed. Then rerun the existing console suites.

## 4. Known contract gaps deliberately not invented

- **K1** (real issuer/JWKS/token verification) is undelivered; `ConsoleTokenDecoder` has no
  production implementation. The submission route's credential seam cannot be truthfully composed.
- **A-4** (history cursor signing-key sourcing) is unresolved; no environment-variable name or
  custody rule is fixed.
- **A-3** (provisional `INVALID_CURSOR` mapping) is Requirements-owned and untouched.

Consequence for item 1: the deployed composition recognizes the two existing route shapes but
answers `503 UNAVAILABLE` for them; it does not fabricate authorizer, registry or cursor-key
collaborators, does not add an endpoint and does not claim the business paths work. `RESULTS.md`
states this limit explicitly.

## 5. Files in scope

New:
- `src/main/java/com/acquiring/simulator/adapter/aws/ConsoleHandler.java`
- `src/main/java/com/acquiring/simulator/adapter/aws/ConsoleRuntime.java`
- `src/test/java/com/acquiring/simulator/adapter/aws/ConsoleHandlerCompositionTest.java`

Changed:
- `src/main/java/com/acquiring/simulator/console/dev/ConsoleDevHarness.java`
- `src/main/java/com/acquiring/simulator/console/dev/ConsoleDevServer.java`
- `src/test/java/com/acquiring/simulator/console/dev/ConsoleDevServerTest.java`

Untouched: `EDGE_ORIGIN_VERIFY` Terraform wiring, the gate/entry-point/route classes, any approved
contract document, artifact or revision.

## 6. Runs

Runs executed in the continuation are recorded, with real counts and exit codes, in
[`RESULTS.md`](RESULTS.md); the per-attempt `exit`, `stdout` and `stderr` files are under `runs/`.
