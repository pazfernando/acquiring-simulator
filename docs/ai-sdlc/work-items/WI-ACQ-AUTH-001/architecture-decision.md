# Architecture decision — Operation A

Status: APPROVED by Fernando Paz, 2026-11-18. This approves construction design, not independent QA/Security or production release.

## Human evidence

Coordinator presented Java 25, Maven, stable Spring Boot, SQLite, no HTTP API, application.properties, local repository /home/pazfernando/projects/acquiring-simulator with base main and no remote. Fernando replied: “si java 25 lts + la ultima version estable de spring boot, sqllite como para testig pero para ambientes tes y prod deberia ser postgresql y todo lo demas de acuerdo”.

This approves the presented design with the database correction below. HD-01–HD-04 and the independent domain PASS remain authoritative for unchanged business semantics. requirements.md MD5 b0c97974197a20dff1b72d38164d0e2b; simulator-profile.md MD5 5483a4a378c0d1101391c11a628d2ae6; acceptance-scenarios.md MD5 307922ad99e7468d53e687587a670750; domain-review.md MD5 6da7c386050eeedaecd63e67c9becf9d.

## Approved design

- Java 25 LTS, Maven and latest stable Spring Boot. Stable version verified at Spring Initializr on 2026-11-18: 4.1.2; recheck before generating build and pin actual Maven coordinate, never RC/SNAPSHOT.
- Configuration via application.properties and validated typed configuration, with separate environment profiles and test fixtures. Markdown is decision evidence, not runtime policy configuration.
- SQLite for local automated tests/development exercise only. PostgreSQL for test environment and production configuration. An environment named test is NOT an alias for SQLite unit testing. Neither production credentials nor actual deployment are authorized.
- Shared persistence port with explicit database-specific migrations/SQL as needed; preserve transaction, uniqueness and concurrency semantics across both engines. PostgreSQL integration tests must verify behavior on PostgreSQL rather than infer compatibility from SQLite.
- Local in-process Spring application, no HTTP endpoint/listener or UI in this slice; authenticated/authorized context originates in a trusted synthetic local harness, not request-asserted identity.
- Four distinguishable business outcomes, authorized result/reference only to callers; internal source/reasons/evidence restricted to the local exercise operator. Allowlist of stored synthetic fields and no arbitrary raw payload/secret logging as in implementation-proposal.md.
- Persistent authorization/key-generation/prior-reference records plus append-only history. Atomic claim/dispatch intent, at most one dispatch per authorization, no waiting on downstream evidence while holding DB write locks. Window expiry does not delete retained links. No redispatch on crash or restart; ambiguity stays unconfirmed.
- Inject clock and deterministic downstream simulator; confirmation-before-uncertainty ordering; late confirmations preserve earlier uncertainty; contradictory confirmation is retained/flagged without silently replacing a confirmed decision.
- Local repository path /home/pazfernando/projects/acquiring-simulator; base main, scoped implementation task branch. Remote deferred to Fernando; no external publication.

## Verification and boundaries

Map all 24 v1 scenarios to meaningful tests, including corrected A19/A25, with isolation/concurrency/expiry/restart/evidence-ordering and output-disclosure coverage. A10/A11 remain deferred. Run PostgreSQL-specific integration tests when an authorized isolated local test DB/container is available; otherwise report an explicit verification gap, never label SQLite results as PostgreSQL proof. Do not contact any existing test/production service or deploy.

Independent QA and triggered Security review remain pending with no installed specialist; developer tests do not replace them. P08 exercise approval and architecture approval are not independent security clearance. No release or acceptance claim until required evidence exists.

Delegate without a model override: the implementation agent configured default is authoritative. Required native-harness compatibility must be observed, not assumed; if unavailable, report the blocker without switching model or launching a shell wrapper.
