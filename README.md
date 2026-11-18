# AWS Operation A update

The current AWS adapter path uses API Gateway, Processor Lambda, separate Mastercard/Issuer Lambdas and DynamoDB. Operation A only; no reversal, inquiry or 3DS. Start with [AWS usage and verification](docs/aws-operation-a.md) and [architecture delta](docs/ai-sdlc/work-items/WI-ACQ-AUTH-001/aws-architecture-update.md). Legacy local SQL harness instructions below remain for regression tests, not the current AWS target.

# acquiring-simulator

Local acquiring simulator service. Spring Boot + PostgreSQL, Java 25 toolchain.

Product specifications, acceptance scenarios, independent domain reviews and the approved architecture decision are versioned under [`docs/ai-sdlc/`](docs/ai-sdlc/README.md). Coding agents start from the checked-out product documents and verify approval/revision evidence before implementation.

## Status

Authorization core (validation, idempotent replay, linked retries, timeout and
late-evidence handling, rejection audit) is implemented with JDBC persistence
and Spring integration tests. Tests currently execute against SQLite; the
PostgreSQL path is present in configuration but has not been executed.

## Toolchain

Verified on this machine (2026-11-18):

| Component | Version | Path |
| --- | --- | --- |
| JDK | Temurin 25.0.5 (25.0.5+8-LTS) | `~/.local/opt/jdk-25` |
| Maven | Apache Maven 3.10.1 | `~/.local/opt/maven` |
| PostgreSQL | not installed natively; Docker image `postgres:17-alpine` | see `docs/toolchain.md` |

Nothing is installed globally; both tools are unpacked under `~/.local/opt`.

## Build

```bash
export JAVA_HOME=$HOME/.local/opt/jdk-25
export PATH=$JAVA_HOME/bin:$HOME/.local/opt/maven/bin:$PATH

mvn -B verify
```

## Configuration

Runtime settings come from `src/main/resources/application.yml` and are
overridable by environment variables:

- `SIM_DB_URL` (default `jdbc:postgresql://localhost:5432/acquiring_simulator`)
- `SIM_DB_USERNAME` (default `acquiring`)
- `SIM_DB_PASSWORD` (default `acquiring`)
- `SIM_SERVER_PORT` (default `8080`)

Defaults are local-development placeholders only; no credentials are committed.
