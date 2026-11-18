# Owner decisions — 2026-11-18

Owner: Fernando Paz, Human Product Lead. Evidence: direct requester messages in this session.

## HD-04 — simulator details APPROVED

Literal: “ok aprueba de p01 a p08 y usemos... java spring boot la ultima version”.
Fernando approves all P01–P08 in simulator-profile.md MD5 5483a4a378c0d1101391c11a628d2ae6. This is owner disposition of the eight proposed groups, including P08’s synthetic-exercise disclosure/retention boundary. It does not fabricate independent domain/security approval or settle future production policy.
Requirements r3 MD5 b0c97974197a20dff1b72d38164d0e2b and corrected acceptance scenarios MD5 307922ad99e7468d53e687587a670750 remain the functional inputs. Their earlier pending-approval labels are historical as of this decision; functional text is unchanged. Independent review must assess the artifacts together with this decision record.

## HD-05 — implementation stack SELECTED

Same literal message selects Java and the latest stable Spring Boot, superseding Python. Spring Initializr metadata https://start.spring.io/metadata/client inspected 2026-11-18 lists default/stable Spring Boot 4.1.2 (metadata ID 4.1.2.RELEASE), while 4.2.0 is RC2/SNAPSHOT. Select stable 4.1.2, not a prerelease. Resolve and verify actual Maven artifact coordinates during build preparation; metadata IDs are not necessarily Maven version strings.
Java 25 LTS and Maven are proposed implementation defaults, not an assertion that Fernando requested the latest Java or approved every architecture detail. Configuration belongs in application.properties with validated typed settings; test-only data/profiles are separate. Markdown records decisions and is not runtime configuration.

## HD-06 — local repository direction

Prior literal: “La rama base es main y el url remoto luego yo lo pongo”. Base branch main selected; Fernando will add the remote later. Remote URL/provider/visibility remain unset. No remote creation/push/publication is authorized. Propose local checkout /home/pazfernando/projects/acquiring-simulator with an implementation task branch off main. Commit author identity is still unverified. The earlier remote-URL onboarding dependency is deferred by the owner; retain local checkout/branch/identity validation.

## Limits and next gates

P01–P08 owner approval is not reviewer closure of F02/F04. Request fresh independent domain review of the pinned functional artifacts plus this owner decision. Stack selection is not blanket approval of SQLite storage, interface/state/concurrency implementation or security. Java/Spring-compatible architecture revision is prepared for human review. QA and Security remain without installed independent specialists; no verification/acceptance claim is made.
