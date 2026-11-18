# Toolchain decisions

Recorded 2026-11-18 during project provisioning. Values are pinned in
`pom.xml`; change them here first if they are ever revised.

## Java

- **Pinned:** Java 25 (release `25`).
- **Installed:** Eclipse Temurin `25.0.5+8-LTS`, unpacked at
  `~/.local/opt/jdk-25` (user-owned, no system-wide install).
- `java -version` reports `openjdk version "25.0.5" 2026-10-20 LTS`.

## Maven

- **Pinned:** Apache Maven 3.10.1 at `~/.local/opt/maven`.
- Requires `JAVA_HOME` to be exported; there is no global Maven settings
  change, no `~/.m2/settings.xml` credentials, and no wrapper committed yet.

## Spring Boot

- **Pinned:** `4.1.2` (latest stable on Maven Central at time of writing).
- Rationale: `maven-metadata.xml` for `spring-boot-starter-parent` reported
  `<latest>`/`<release>` as `4.2.0-RC2`, which is a pre-release. The newest
  non-prerelease version is `4.1.2`, so pre-release builds are excluded.
- Spring Boot 4.1.x declares a Java 17 baseline and is used here with Java 25
  via `<java.version>25</java.version>`.

## PostgreSQL for tests

No local PostgreSQL server binaries are present (`psql`, `initdb`, `pg_ctl`
are not on `PATH`; `/usr/lib/postgresql` does not exist). The Docker daemon is
reachable, so Testcontainers is the intended isolated-testing path:

- Dependencies: `spring-boot-testcontainers`, `org.testcontainers:postgresql`,
  `org.testcontainers:junit-jupiter` (versions managed by the Spring Boot BOM).
- Requires an image pull of `postgres:17-alpine` on first run.
- Docker image pulls are exercised only on demand; no image is baked into the
  build, and tests must skip or fail clearly when no daemon is available.

## Not yet done

- No `mvnw` wrapper committed (builds currently use the provisioned Maven
  3.10.1 binary).
- No CI, no containers for the app itself, no deployment configuration.
