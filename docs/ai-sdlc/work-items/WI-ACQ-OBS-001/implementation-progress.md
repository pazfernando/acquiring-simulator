# Implementation progress — WI-ACQ-OBS-001

Status: **Phase 0 (baseline + preflight recovery) COMPLETE; P1a (canonical event contract subset) IMPLEMENTED AND LOCALLY TESTED 2026-11-20; P1b1 (EVENT store/HEAD/dedupe/sequence, G7 core) IMPLEMENTED AND LOCALLY TESTED 2026-11-20; P1b2a (atomic INITIAL CLAIM composition + opt-in slot accounting) IMPLEMENTED AND LOCALLY TESTED 2026-11-20, NOT PRODUCTION-WIRED; P1b2b (claim adapter + ambiguous-commit recovery) IMPLEMENTED AND LOCALLY TESTED 2026-11-20, NOT PRODUCTION-WIRED (journalVersion/version conflict escalated); P1b2c (additive committed-version persistence + read representation) IMPLEMENTED AND LOCALLY TESTED 2026-11-20, NOT PRODUCTION-WIRED; P1b2d (optional ClaimGateway service branch + DynamoClaimGateway adapter + no-fallback dispositions) IMPLEMENTED AND LOCALLY TESTED 2026-11-20, AwsRuntime STILL INACTIVE. P1b3a (POSSIBLE_SEND dispatch-intent composer + opt-in slot accounting) IMPLEMENTED 2026-11-20 AND TARGETED-TESTED (7/7) AGAINST THE CURRENT COMPOSER SNAPSHOT `b9a2579f`; THE COMPLETE 154-TEST SUITE IS EVIDENCE FOR THE EARLIER SNAPSHOT `089d76ca` ONLY, AND THE FINAL FULL-SUITE ATTEMPT IS INCOMPLETE. STILL NOT PRODUCTION-WIRED. P1b3c (narrow dispatch-intent gateway + optional atomic service path) IMPLEMENTED AND LOCALLY TESTED 2026-11-20; RESULT COUPLING AND AwsRuntime ACTIVATION NOT STARTED. P1b2/P1b3 remainder (result coupling, ambiguous-send resolver, quarantine, retention, principal auth, runtime activation) NOT STARTED.**
Date: 2026-11-19 America/Bogota; P1a/P1b1 update 2026-11-20. Owner: Fernando Paz.

Status note (2026-11-20): the status paragraph above is preserved as written for the P1a–P1b3a slices. Later verified slices are recorded as §16 P1b3b, §17 P1b3c, §18 P1b4a, §19 P1b4b, §20 P1b4c, §21 P1b5b, §22 P1b5c, §23 P1b6a, §24 P1b6b, §25–§27 P1b6c, §28 P1b7a evidence location, §29 P1b7b and §30 P1 closure assessment/OBS-20/22 acceptance tests; those sections supersede the earlier "P1b2/P1b3 remainder NOT STARTED" wording. The protocol slice continues at §31–§33 (P2a1c/P2a2/P2a3a), §34 (P2a3b evidence location) and §35 (P2a3c complete echo binding). `AwsRuntime` remains inactive and G6 remains incomplete.

Authority for this phase: owner instruction relayed 2026-11-19 authorizing completion of Operation A locally, including application, pipelines and IaC (superseding the earlier preflight-only scope), with native Terraform HCL as the IaC standard and Java 25 retained. This document records only verified state, gaps and proposed phase boundaries. It does not invent business rules and is not independent QA, Security review or human acceptance.

## 1. Execution binding — VERIFIED this turn

| Field | Observed |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Git top-level | `/home/pazfernando/projects/acquiring-simulator` (containment OK) |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `cfc456f4f469d9558a78fda8ff9b770b00817315` (`cfc456f`, 2026-11-19 09:25:03 -0500) |
| Configured model | provider `nan`, model `deepseek-v4-flash` (DeepSeek V4 Flash); no override |
| Harness | OpenCode via the authorized ACP route |
| Toolchain | JDK 25.0.5 `/home/pazfernando/.local/opt/jdk-25`; Maven 3.10.1 `/home/pazfernando/.local/opt/maven/bin/mvn` (neither on `PATH`) |
| Missing tools | `terraform`, `tflint`, `checkov`, `cfn-lint` absent from `PATH` |
| Baseline build | `mvn -o -q -DskipTests compile` → exit 0 |
| Git operations | None (no commit/push/PR). Working tree preserved. |

### Working-tree ownership

34 pre-existing modified/untracked entries (OBS-001 specification set, diagram assets, `technical-preflight.md`, guide/domain-review revisions, plus modified `AGENTS.md`, `docs/aws-operation-a.md`, AUTH/OBS work-item records). They were **not modified, reverted or staged**. This turn added only:

- `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/implementation-progress.md` (this file)
- `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/evidence/preflight-2026-11-19/` (copies + manifest)
- `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/technical-preflight-evidence/` (Java/Python rerun, exits, token-quoting check, manifest)
- updates to `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/technical-preflight.md` (DE42 resolution, evidence paths, binding)

## 2. Authority and pinned identity — VERIFIED

Owner architecture approval and the eight pinned SHA-256 identities in `owner-decisions.md` ("Architecture approval and technical-preflight commission") were recomputed and **all match**:

- `requirements.md` `49c5d412…e2b459df`
- `acceptance-scenarios.md` `15868984…aa7733`
- `iso8583-profile.md` `de02c533…0e1fe481`
- `channel-contract.md` `495e1172…38ea3bca`
- `observation-contract.md` `ca53e384…842a205a`
- `decision-proposal.md` `becbabcd…fe219166`
- `activity-flow.md` `7f09731a…fb3c67bd`
- `activity-flow.mmd` `e2fbb1aa…388393ce`
- `domain-review.md` `73adba20…ee0ca2`
- `vectors/review-manifest.json` `dac6fc14…eb5082dc`

Approved scope: Operation A only; private SIM-ISO87A-v1 over HTTPS; single demo JWT principal + separate service IAM; presenter screen-share; one queryable DynamoDB event source with atomic business + critical-event persistence; asynchronous Streams/WebSocket notification + HTTP catch-up; asynchronous ADOT; seven-day functional history (business evidence not deleted). No B/C, live networks, public client hosting, multi-user admin or invented financial rules.

## 3. Technical preflight outcome

Report: `technical-preflight.md`. Evidence: `evidence/preflight-2026-11-19/` and re-execution evidence in `technical-preflight-evidence/`.

- **DE42 discrepancy: CORRECTED PROBES REPRODUCIBLE; HISTORICAL CAUSE UNPROVEN.** The corrected probes reproduce: 31/31 ACCEPT, 24/24 structural negatives, `problems=0`; a second run with JDK 25 produced byte-identical stdout (exit 0, empty stderr) and Python reproduced its own output. Approved documents byte-unchanged. The earlier turn attributed the original mismatch to a probe defect (DE32 declared fixed instead of `LL n1–11`, plus an Integer/String DE48 lookup). That narrative is a record of that turn only: the pre-correction probe source was not preserved and no pre-correction diff exists, so the **historical root cause remains unproven**. What is verified is that the corrected probe is viable and reproducible; it is not evidence that any specific earlier defect caused the mismatch.
- ISO structural alignment is evidence for **probe viability only**; no production codec exists or was selected.
- DynamoDB probe, **local checks now captured (2026-11-20)**: `pass=21 fail=0`, exit 0, empty stderr, against loopback DynamoDB Local (pre-existing in-memory emulator, dummy creds). PASS for atomic append, cancellation/no-partial-write, idempotency, CAS/single-sequence, cursor pagination and size/transaction limits. Evidence: `technical-preflight-evidence/ddb-run-2026-11-20/`. AWS service behavior (capacity, throttling, Streams, IAM, encryption, TTL timing, concurrency) stays **UNVERIFIED** — an emulator cannot prove it. The two earlier "script defect" claims (46-character `ClientRequestToken`, impossible `attribute_not_exists(pk)`) remain **withdrawn**; the token is 16 characters because `'$T'` is single-quoted, and `attribute_not_exists(pk)` is true for a key with no pre-existing item. Neither is a product defect. The run copy changed **only** token derivation (run isolation against the emulator's cached tokens), not any check.
- JWT issuer, WebSocket auth, ADOT/Java 25 and AWS service behavior remain **UNVERIFIED**. Cloud-only checks stay unverified while local work proceeds.

## 4. Baseline implementation inventory (what exists)

Java 25 / Spring Boot 4.1.2 Maven project; 64 developer tests recorded in the last build (24 SQLite scenarios, 24 PostgreSQL/Testcontainers, 10 DynamoDB Local, 6 AWS transport).

- Domain: `AuthorizationService` (authorize, submitEvidence, evaluateDeadline, inspect, history), states `NETWORK_REJECTED/AWAITING/APPROVED/DECLINED/NOT_CONFIRMED`.
- Persistence ports: `AuthorizationRepository` (claim, markDispatched, updateOutcome, find*, eventsFor, appendEvent, appendRejection).
- AWS adapters: `ProcessorHandler`, `MastercardHandler`, `IssuerHandler`, `DynamoAuthorizationRepository`, `LambdaDownstream`, `WireJson`, `AwsRuntime`.
- Local harness: JDBC/SQLite + PostgreSQL schema (`src/main/resources/db/schema.sql`).
- IaC: a single **SAM** template `infra/template.yaml` (HTTP API + JWT authorizer, 2 DynamoDB tables, 3 Lambdas, 7-day log groups).
- Tests: core integration, PostgreSQL integration, DynamoDB Local integration, transport/handlers.
- No console, HTML, relay, ISO codec, event table/Streams/WebSocket, notifier, ADOT, Terraform or CI pipeline.

## 5. Acceptance gap inventory (approved contracts vs. current code)

| # | Area | Current state | Approved target | Gap |
| --- | --- | --- | --- | --- |
| G1 | Channel/ACL | JSON body → `ProcessorHandler` directly | C1/C2: channel ACL translates to SIM-ISO87A-v1 octet-stream at private `POST /internal/sim-iso87a/v1/authorizations`, media/profile checks, 1–2048 bytes, no wrapper leakage | No codec, no ACL module, no internal route or media validation |
| G2 | ISO codec | None (throwaway probes only) | P1/P2/P4 packing, presence matrix, DE48 canonical keys, response vectors | Production codec, aliasing (DE32/41/42), all 85 packed + 5 response vectors as tests |
| G3 | Channel response | Raw `AuthorizationResult` JSON, ad-hoc `{error}` | C3 exact envelope (`contractVersion`, IDs, outcome, disposition, businessVersion, asOf, observation, serverFreshness, error) + precedence table | Envelope builder, error taxonomy, precedence, no-unknown-keys |
| G4 | Evidence carriage | None | C4 `X-Simulator-Evidence` Ed25519 envelope, DIRECT/RELAY, canonical JSON, hash binding, late-delivery handler | Signing/verification, canonicalization, header bounds, late-evidence seam |
| G5 | Event contract | `EvidenceEvent` internal row (id, authId, type, decision, source, correlation, detail, recordedAt) | O1a/O2a canonical payload (schemaVersion, producerId, task, status, times, causal links, manifest) + immutable hash + source wrapper + enums/bounds | New contract, manifest, sanitizer, canonical hash, dedupe/quarantine |
| G6 | Atomic durability | `claim()` transaction (record + business head); events appended separately, non-atomic | O3: business record + head + critical event + event HEAD/dedupe + slot accounting in one `TransactWriteItems`; ambiguous-commit lookup | Event store integration into claim/POSSIBLE_SEND/result; slot counters; no dual write |
| G7 | Event store/keys | Two tables (`Journal`, `IssuerTruth`); evidence under `AUTH#…/EVT#…` | O2a: environment/acquirer/merchant/transaction partition, `E#<12-digit sourceSequence>`, `HEAD`, dedupe metadata, optional index | Table schema, sequence/HEAD CAS, dedupe partition, wrapper fields |
| G8 | Retention/expiry | None; only CloudWatch log retention | O8: 7-day functional history by original `collectedAt`, logical expiry, `expiryRevision`, expired ranges, no TTL on business/evidence | Expiry worker, range metadata, truncation contract, query enforcement |
| G9 | Query read model | `eventsFor()` only | C7/O6 `GET /console/v1/transactions/{id}/events` with snapshot HEAD, cursor, ranges, pagination, 400/403/410 | Query Lambda/API, cursor signing, retention metadata, page bounds |
| G10 | Live delivery | None | O7 Streams → notifier → API Gateway WebSocket → frame schema; offline catch-up via same table; heartbeat/sync | Streams view, notifier Lambda, WS API, connection table, 410 cleanup |
| G11 | Subscription idempotency | None | O5a/C8: K=(connectionId, epoch, requestId), immutable T, ACK-before-render, conflicts/retirement/tombstones/control cap | Routing-claim store + state machine + fixtures W01–W33 |
| G12 | Auth/identity | HTTP API JWT authorizer (issuer/audience params) + subject→context registry | Single demo subject; WS `$connect` validates `X-Demo-JWT` + IAM service binding separately; loopback relay sessions | WS JWT validation, demo subject config, relay session store, origin policy |
| G13 | Console UI | None | HTML POS form, live timeline, history/detail, inert text rendering, no raw payloads | Relay + HTML/JS + rendering/subscription logic |
| G14 | Task hook/manifest | None | O1: every capability/task emits started/terminal; critical vs best-effort; bounded queue; manifest | Hook API, async writer, manifest for A, redaction |
| G15 | ADOT | None | Async OTel export, no functional evidence | Instrumentation + layer/agent config (Java 25 compat unproven) |
| G16 | IaC | SAM `infra/template.yaml` | Native Terraform HCL; S3 remote state (encrypted/versioned/locked, env-separated keys); explicit bootstrap; least-privilege IAM; parameters for account/region/issuer | Entire Terraform module set + bootstrap; SAM disposition decision |
| G17 | CI pipeline | None | Build/test/scans, artifact identity, plan review + approval gate, no auto-deploy | Pipeline definition (GitHub Actions assumed) + gated plan |
| G18 | Tests | 64 developer tests | OBS 28 scenarios, 85 packed, 35 state, 8 channel amount, 5 canonical event, 12 carrier, 26 HTTP response, 23 event-source semantic, 33 WS/race; 41 evidence fixtures | Test suites per phase; documentation fixtures are not executed tests |

Inventory totals from the approved specs (documentary, not executed): 255 acceptance cases.

## 6. Proposed phase boundaries

Each phase is independently reviewable, keeps the branch building, and reports actual commands/results. Later phases depend on earlier contracts; no phase invents business rules.

| Phase | Scope | Primary gaps | Exit evidence | Explicit non-goals |
| --- | --- | --- | --- | --- |
| **P0 (done)** | Baseline, preflight recovery, evidence preservation, gap inventory | — | `compile` and `test-compile` exit 0 (offline); pinned hashes; probe stdout; this file | Implementation |
| **P1** | Event contract + atomic event store. **Split:** P1a = contract model, validation, sanitizer, canonical hash, wrapper, dedupe identity, manifest (delivered 2026-11-20); P1b1 = EVENT store/HEAD/dedupe/sequence/ambiguous-lookup and DynamoDB Local tests (delivered 2026-11-20, G7 core; business composition seam inert); P1b2a = atomic initial-claim composer + opt-in slot accounting, tested but not service-wired (delivered 2026-11-20); P1b2b = service wiring, POSSIBLE_SEND/result coupling, ambiguous send, quarantine/retention metadata, principal auth (not started, G6) | G5–G7 | P1a: 42→52 executed `domain.event` unit tests; P1b1: 10 DynamoDB Local tests; P1b2a: 6 claim transaction tests; P1b2b: scenario OBS-20/21/22 core | Streams, WS, query API |
| **P2** | Production SIM-ISO87A-v1 codec + ACL + internal HTTPS route + C3 response envelope | G1–G3 | 85 packed + 5 response vector tests; adapter rejection/uncertainty tests; OBS-01–14 transport subset | C4 signing (P2b), console |
| **P2b** | C4 evidence carriage + late-evidence admission seam | G4 | Envelope/hash/bounds tests; OBS-08/09 vectors | Public late endpoint |
| **P3** | Console read model: query API, snapshot cursor, ranges, retention/expiry, journal-vs-events authority | G8, G9 | OBS-15–19, 28; cursor 400/403/410 tests | WS |
| **P4** | Streams + notifier + WebSocket + subscription idempotency (C8/O5a) | G10, G11 | W01–W33 fixtures; OBS-17/18/20/21; notification-only-failure tests | Payment-path changes |
| **P5** | Single-demo JWT: WS `$connect` validation, loopback relay, HTML console, screen-share boundary | G12, G13 | OBS-23/24 + JWT NumericDate fixtures; relay origin/session tests | New auth provider, public hosting |
| **P6** | Common task hook + ADOT async instrumentation | G14, G15 | Hook/manifest tests; instrumentation present; ADOT compat reported (not assumed) | Changing payment outcome |
| **P7** | Native Terraform HCL: modules, S3 backend bootstrap (encryption/versioning/locking), least-privilege IAM, parameterized inputs | G16 | `terraform fmt -check`/`validate` where available; reviewable plan output **not applied** | `terraform init` against S3, state migration, apply |
| **P8** | CI pipeline: build/test/scans/artifact identity/plan approval gate | G17 | Pipeline config + documented gate; local dry-run where possible | Remote execution, deployment |
| **P9** | Evidence pack + independent QA/Security handoff | G18 | Command/result log, limitations, unverified paths | Claiming acceptance |

### Sequencing notes

- P2b and P3 are independent of each other after P2; P4 depends on P1 (event table) + P3 (query contract) + P5 (auth binding) for end-to-end verification, though the notifier can be built against P1 alone.
- P7 is intentionally late so Terraform describes the final resource set (event table, Streams, WS API, connections, notifier, query Lambda, IAM). Terraform **authoring** is authorized; `init`/plan-against-S3/migrate/apply are not.
- SAM `infra/template.yaml` disposition is an open decision (see §8), not silently deleted.

## 7. Material blockers and unverified dependencies

| Item | Status | Consequence |
| --- | --- | --- |
| JWT issuer URL/JWKS/audience/demo subject | **UNVERIFIED** — no issuer inspected, no credential flow initiated | P5 cannot be validated end-to-end; code can be written against parameters but not proven |
| Java 25 + ADOT layer/runtime compatibility | **UNVERIFIED** — no layer ARN/architecture match | P6 instrumentation may need a different packaging or agent; must be reported, not assumed |
| DynamoDB service behavior (throttling, Streams, IAM, TTL timing) | **UNVERIFIED** — local check family passed 21/21 on DynamoDB Local (2026-11-20) | P1/P3/P4 local correctness has captured support; throttling, Streams, IAM, encryption and TTL timing still rest on contract review, not AWS |
| WebSocket `$connect` auth semantics, header preservation | **UNVERIFIED** | P4/P5 design follows C6/O4 but needs real preflight |
| Independent Security and QA reviewers | **QA: INSTALLED / Security: UNAVAILABLE** | QA (`ai-sdlc-qa`) is delegable; its independent verification is a gate after implementation, not a substitute for implementation-owned preflight. Security (`ai-sdlc-security`) is not callable, so security review cannot be scheduled yet. |
| AWS account/region/issuer/audience values | **NOT SUPPLIED** | Terraform/pipeline inputs must be parameters, never invented |
| Terraform/SAM lint tools | **ABSENT** on this host | P7 validation limited to what can be installed later; state explicitly |
| Publication (push/PR/merge/deploy) | **NOT AUTHORIZED** for this new work | Earlier publication authority covered the prior version only |

## 8. Open decisions requiring owner/coordinator input (not blocking P1–P2 authoring)

1. **SAM vs Terraform disposition.** Native Terraform is the standard; the existing `infra/template.yaml` is the only IaC today. Options: keep SAM as a local/dev artifact until Terraform reaches parity, or replace it in P7. Recommendation: keep until Terraform covers processor/network/issuer, then mark deprecated; deleting earlier would remove working reference infrastructure.
2. **CI provider.** No CI config exists. GitHub Actions is the assumed default from the GitHub origin; confirm before authoring P8.
3. **Event table placement.** O2a describes a dedicated functional EVENT table; current Journal holds evidence rows. P1 will follow O2a (separate EVENT table + HEAD/dedupe) unless the owner prefers reusing the Journal table. Recommendation: separate table per approved contract.
4. **Deployment identity/region** for parameterized Terraform (values only, no secrets).

## 9. Non-claims

- No implementation, commit, push, PR, merge, deployment, resource provisioning, credential flow or external pipeline execution occurred in this turn.
- Preflight is not QA, Security review or acceptance; probe viability is not production codec evidence.
- 255 acceptance cases remain documentary; no test was executed for them.
- Cloud-only checks remain UNVERIFIED by design while local work proceeds.

## 10. P1a — canonical event contract subset (2026-11-20)

Authority for this slice: owner-expanded local implementation authority recorded in §6/WORK_ITEM and relayed by the coordinator ("ok continua con lo pendiente"), limited to local application code and tests. No capacity values, payment semantics, Streams/WebSocket/query work or cloud action were added.

### Binding re-verified before writes

| Field | Observed |
| --- | --- |
| Git top-level | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged; no commit this turn) |
| Origin | `https://github.com/pazfernando/acquiring-simulator.git` |
| Model | provider `nan`, model `deepseek-v4-flash`; no override |
| Pre-existing changes | 34 modified/untracked entries preserved; no revert, stage or overwrite |

### Delivered (new package `com.acquiring.simulator.domain.event`)

Application (12 files):
- `CanonicalEvent` — closed 32-field payload record + builder (absent optionals stay null).
- `CanonicalEventCodec` — RFC 8785 canonical JSON, UTF-8 bytes, SHA-256 hex.
- `CanonicalEventJson` — strict ingress: undeclared field rejection, explicit collector-field rejection (`collectedAt`, `ingestionSequence`, `collectorReceiptId`), typed enum/task parsing.
- `EventValidator` — bounds (IDs 1–64, context refs 1–128, capability/version/manifest 1–32), UTC RFC 3339 `Z` times, nonnegative sequence/duration, task/actor/producer/manifest cross-checks, skipped-task evidence rule, same-clock duration rule, 2048-byte limit; plus wrapper admission.
- `EventSourceWrapper` + wrapper rules — hash equality, 12-digit source sequence, critical-only `journalVersion`/`committedAt`/`stateAfter`, `collectedAt == committedAt` for critical, metadata forbidden for best-effort, sampled telemetry refused.
- `EventSanitizer` — printable-ASCII-only and credential-shape rejection (JWT, Bearer, PEM, 13–19 digit PAN run, PIN-block wording) without echoing values.
- `EventManifest` + `OperationAManifest` (`A-1`) — the O1 task list, producer assignments, conditional network/issuer branch, bounded reason vocabulary, declared critical budget.
- Enums `EventActor`, `EventStatus`, `EventProvenance`, `EventClockQuality`, `EventGapCode`, `EventDurability`, `EventTask`, `EventReason`; `EventViolation`, `EventValidationException`, `EventDedupeKey`, `EventDeduplication`.

Tests (6 files, 42 tests) in `src/test/java/com/acquiring/simulator/domain/event/`: `CanonicalEventCodecTest` (7), `EventValidatorTest` (14), `EventSanitizerTest` (6), `EventSourceWrapperTest` (6), `OperationAManifestTest` (6), `EventDeduplicationTest` (3).

### Commands and captured results (logs saved before analysis)

- `mvn -o -q -DskipTests compile` → exit 0.
- `mvn -o -Dtest='com.acquiring.simulator.domain.event.*Test' test` → exit 0, `Tests run: 42, Failures: 0, Errors: 0` (`/tmp/opencode/p1a/test.log`).
- `mvn -o test` (full suite) → exit 0, `Tests run: 106, Failures: 0, Errors: 0` — 64 pre-existing + 42 new (`/tmp/opencode/p1a/full-test.log`). PostgreSQL/Testcontainers and DynamoDB Local images were already cached; no network pull occurred.

### Fixture execution status

`schemaVersion`-1 canonical fixtures from `vectors/event-vectors.json` are now executed for the first time: EV1 canonical JSON and SHA-256 (`ed1044b9…298687b5`) match the oracle byte-for-byte; EV2 classifies `EXACT_REPLAY`; EV3 classifies `CONTENT_CONFLICT`; EV5 preserves `BOOT-001` and the same dedupe identity under a new delivery instance. EV4 (context reject before dedupe) is only demonstrated as a hash divergence today; producer/context authorization belongs to P1b and remains unexecuted.

### P1 completion state

| Item | State |
| --- | --- |
| G5 contract model, enums, bounds, sanitizer, canonical hash, wrapper, manifest, dedupe identity | **COMPLETE locally** (P1a) |
| G5 `collectedAt` first-collection retention across redelivery | **REMAINING** (store metadata, P1b) |
| G6 atomic business + head + critical event + HEAD/dedupe + slot accounting transaction; ambiguous-commit lookup | **REMAINING** (P1b) |
| G7 EVENT table schema, `E#<12-digit>`/`HEAD` keys, sequence CAS, dedupe partition, optional index | **REMAINING** (P1b) |
| OBS-20/21/22 core integration tests | **REMAINING** (P1b) |
| Context/producer authorization before dedupe (EV4) | **REMAINING** (P1b) |

### Decisions and limitations in this slice

- Producer wiring (CHANNEL-A, ACL-A, PROC-A, NETWORK-A, ISSUER-A) follows the components that exist today; it is a manifest modelling choice open to review, not an owner decision.
- The critical budget reproduces O8's **PROPOSED** figures (8 reserved slots, ≤5 transitions) as declared configuration; it is not owner-approved capacity.
- `EventReason` reuses the existing `RejectionReason` vocabulary plus `VALID`, `SKIPPED_NOT_APPLICABLE` and `NO_RESPONSE`; no free-text reason is admitted.
- Wrapper `stateAfter` is a bounded token, not yet bound to the journal state enum; that binding belongs to the atomic store.
- Existing `EvidenceEvent` and all payment code are untouched. No dual write was introduced.

### Next bounded slice (P1b)

Add the EVENT table adapter and atomic claim/possible-send/result transaction: `AUTH`-style partition with `E#<12-digit>` rows + `HEAD`, conditional HEAD CAS, dedupe metadata partition, critical slot counters inside the same `TransactWriteItems`, ambiguous-commit lookup by stable transition token, and DynamoDB Local tests including OBS-20/21/22 core. Streams, WebSocket, query API, expiry and console stay out of scope.

### Non-claims

P1a is local implementation, not QA, Security review or acceptance. The manifest wiring, critical budget and producer authorization are reviewable proposals. DynamoDB service behavior, Streams, IAM, encryption and TTL timing remain UNVERIFIED. No commit, push, PR, merge, deployment, cloud call or credential flow occurred.

## 11. P1b1 — functional EVENT store core (2026-11-20) — VERIFIED PARTIAL

Scope delivered in this slice: G7 (EVENT table keys/sequence/HEAD) and the store half of G6 (atomic event row + HEAD CAS + dedupe metadata in one `TransactWriteItems`, ambiguous-commit read path). **Business coupling is NOT proven** — see remaining gaps.

### Delivered files (hashes in `evidence/p1b1-2026-11-20/manifest.md`)

Application (4 new):
- `domain/event/EventContext`, `EventHead`, `EventSubmission`, `EventTableKeys`, `CriticalCapacity`, `EventAppendResult`, `StoredEvent`
- `domain/port/EventStore` — `append`, `find`, `head`
- `adapter/aws/DynamoEventStore` — atomic append with `E#<12-digit>` rows, `HEAD`, dedupe partition, conditional sequence CAS, strongly consistent replay/conflict classification, context pre-check before any write
- `adapter/aws/CriticalWriteComposer` — transaction-composition seam, **inert default `NONE`**; capacity injection is explicit, no production capacity default

Tests (2 new, 10 tests): `DynamoEventStoreIntegrationTest` (10) against DynamoDB Local; `EventSubmissionTest` (4) and `EventTableKeysTest` (5) join the `domain.event` suite (52 total).

### Commands and captured results (re-executed in the recovery turn, 11:08)

| Command | Exit | Result |
| --- | --- | --- |
| `mvn -o -DskipTests compile` | 0 | `BUILD SUCCESS` |
| `mvn -o -Dtest='com.acquiring.simulator.domain.event.*Test' test` | 0 | 52 tests, 0 failures/errors/skips |
| `mvn -o -Dtest='com.acquiring.simulator.aws.DynamoEventStoreIntegrationTest' test` | 0 | 10 tests, 0 failures/errors/skips |
| `mvn -o test` | 0 | 126 tests, 0 failures/errors/skips (106 prior + 20 new) |

Logs with explicit `EXIT=` lines and hashes: `evidence/p1b1-2026-11-20/` (`compile.log`, `domain-test.log`, `dynamo-event-test.log`, `full-test.log`, `manifest.md`). All 21 code hashes in the manifest were recomputed against the working tree and matched.

### Verified behaviour (DynamoDB Local only)

First append `E#000000000001` with HEAD `lastSequence=1/firstRetainedSequence=1/expiryRevision=0`; exact replay keeps sequence 1 and original `BOOT-001`; changed payload → `CONTENT_CONFLICT`, stored bytes unchanged; 8 concurrent appends → contiguous 1..8, HEAD 8; EV4 context mismatch throws `CONTEXT_MISMATCH` before any dedupe/HEAD/event write; best-effort journal metadata rejected; composed write commits atomically; failing composed condition leaves no event/HEAD/dedupe row; business composition rejected for best-effort events.

### Remaining gaps — P1b2 (exact next action)

1. **Atomic business coupling NOT proven.** `CriticalWriteComposer.NONE` is the only default; no claim/POSSIBLE_SEND/result path composes `DynamoAuthorizationRepository` business rows into the same transaction. That is the G6 core and the first P1b2 task: one composer implementation plus tests showing business head + event + HEAD + dedupe commit/abort together.
2. **Critical slot accounting** (reserved slots/transitions) not enforced in the transaction; `CriticalCapacity` is explicit test-only configuration.
3. **Ambiguous-commit resolution on the send path** — `find()` exists and is strongly consistent, but no caller/retry policy is wired.
4. `CONTENT_CONFLICT` quarantine marking and `collectedAt` first-collection retention metadata not stored.
5. Producer/context authorization before dedupe (EV4) is enforced for the event partition only, not tied to principal authorization.
6. OBS-20/21/22 core scenarios not yet expressed as named tests.

### Non-claims

P1b1 is local implementation, not QA, Security review or acceptance. DynamoDB service behaviour (throttling, Streams, IAM, encryption, TTL) remains UNVERIFIED. No capacity default, business rule or payment semantic was introduced. No commit, push, PR, merge, deployment, cloud call or credential flow occurred.

## 12. P1b2a — atomic INITIAL CLAIM composition (2026-11-20) — VERIFIED PARTIAL

Scope: the first half of G6/the P1b2 core — business record + business head + critical claim event + EVENT head + dedupe metadata in ONE `TransactWriteItems`, plus opt-in critical-slot reservation in the same transaction. **Not wired into `AuthorizationService`**: `repository.claim()` still runs separately, so this is a coherent, tested transaction composer, NOT production wiring.

### Delivered files (hashes in `evidence/p1b2a-2026-11-20/manifest.md`)

Application:
- `adapter/aws/ClaimCriticalComposer` — new `CriticalWriteComposer` for the initial claim. Emits the `AUTH#<id>/META` record (version 0, `attribute_not_exists`), the `BIZ#<digest>/HEAD` row (generation 1 or `generation = :previous`), and, only when an explicit `CriticalCapacity` is supplied, a slot `ADD` conditioned on `(attribute_not_exists(used) AND budget > 0) OR used < budget`. Rejects a submission that does not declare journal version 1 / `CLAIMED_NOT_SENT`, or a record that is already dispatched.
- `adapter/aws/DynamoEventStore` — added `append(EventSubmission, CriticalWriteComposer)` one-shot overload; the default `append(EventSubmission)` still uses the constructor composer (inert `NONE`). Replays return before composition, so no second business write or slot reservation.
- `adapter/aws/DynamoAuthorizationRepository` — extracted `recordKey`, `businessHeadKey`, `INITIAL_VERSION` so the claim composer cannot drift from `claim()`; `claim()` behavior and version 0 base unchanged.
- `domain/event/EventTableKeys` — added capacity partition/row keys (`.../TX#<tx>#CAPACITY`, `SLOTS`), technical accounting metadata outside the event partition.

Tests (1 new file, 6 tests): `DynamoCriticalClaimIntegrationTest` against DynamoDB Local with both an EVENT table and a journal table.

### Commands and captured results

| Command | Exit | Result |
| --- | --- | --- |
| `mvn -o -DskipTests compile` | 0 | `BUILD SUCCESS` |
| `mvn -o -Dtest=...DynamoCriticalClaimIntegrationTest test` | 0 | 6 tests, 0 failures/errors/skips |
| targeted suite (`domain.event.*`, DynamoEventStore, DynamoCriticalClaim, AwsAuthorization, AuthorizationCore) | 0 | 102 tests, 0 failures/errors/skips |
| `mvn -o test` | 0 | 132 tests, 0 failures/errors/skips (126 prior + 6 new) |

Logs/hashes: `evidence/p1b2a-2026-11-20/` (`compile.log`, `claim-test.log`, `targeted-test.log`, `full-test.log`, `manifest.md`).

### Verified behaviour (DynamoDB Local only)

Claim record, business head, event row, HEAD and dedupe commit together in one transaction; the record is readable through the real `DynamoAuthorizationRepository`. A pre-existing `AUTH#<id>/META` aborts the transaction and leaves no event row, HEAD, dedupe row, slot row or business head. An exhausted/zero slot budget aborts the same way. An exact replay returns `EXACT_REPLAY`, keeps sequence 1, does not create a second business row and does not increment the slot counter. A composition with no injected capacity writes no slot row. A payload labelled with a later journal version/state is rejected before any write.

### Remaining gaps — P1b2b (exact next action)

1. **Production wiring.** `AuthorizationService` still calls `repository.claim()` then appends events separately. `ClaimCriticalComposer` is not called by the service; `DynamoEventStore` is not constructed in `AwsRuntime`.
2. **POSSIBLE_SEND / result coupling** and ambiguous-send recovery policy (still not wired; `find()` exists).
3. **`CONTENT_CONFLICT` quarantine marking** and `collectedAt` first-collection retention metadata.
4. **Principal/context authorization** before dedupe (EV4 enforcement is event-partition-only).
5. **OBS-20/21/22** core scenarios are not yet named tests.
6. **Contract-consistency question for review:** the approved vector labels the claim `journalVersion` 1 while the existing journal `version` attribute base remains 0. The composer treats `journalVersion` as the 1-based business transition version and leaves the storage CAS counter at 0; this is documented, not resolved by an owner decision.
7. **Slot capacity** is opt-in only; no approved capacity values exist, and the capacity key layout is a reviewable technical choice.

### Non-claims

P1b2a is local implementation, not QA, Security review or acceptance. G6 is **not complete**: only the initial-claim transaction composer is proven. DynamoDB service behaviour (throttling, Streams, IAM, encryption, TTL) remains UNVERIFIED. No commit, push, PR, merge, deployment, cloud call or credential flow occurred, and the business repository's existing `claim()` path is unchanged in behavior.

## 13. P1b2b — atomic claim adapter + ambiguous-commit recovery (2026-11-20) — VERIFIED PARTIAL, WIRING DISABLED

Scope: the smallest safe P1b2b slice — a single-transaction claim adapter with stable-token ambiguous-commit recovery and no duplicate reservation. **No production wiring**: `AuthorizationService` and `AwsRuntime` are untouched, and `repository.claim()` remains the only runtime claim path.

### Version conflict blocks wiring

Before wiring, the `journalVersion` question was checked against the approved contract and fixtures. Result: **UNRESOLVED; wiring intentionally disabled.** Evidence: `evidence/p1b2b-2026-11-20/version-conflict.md`.

- `observation-contract.md` line 17 defines `journalVersion` as the **committed journal version** and `stateAfter` as the state at that committed version; it never names a storage/CAS counter.
- Fixtures: `async-projection-vectors.json` assigns claim `journalVersion: 1` / `CLAIMED_NOT_SENT`, then 2 / `POSSIBLE_SEND`, then 3 / `APPROVED`; `response-vectors.json` H05/H16 give claim `businessVersion: 1` (`RESERVED_UNSENT`).
- `channel-contract.md` requires `businessVersion` to be a **positive committed integer**.
- Implementation: `DynamoAuthorizationRepository.claim()` persists `version = 0` (`INITIAL_VERSION`) and `mutate()` increments it per replacement.
- No approved text explicitly distinguishes the public committed version from that internal counter, and no approved text defines `journalVersion = version + 1`. That mapping is therefore **not adopted**; no +1 label transformation, no persisted-version migration, no fixture/spec edit.

### Delivered files (hashes in `evidence/p1b2b-2026-11-20/manifest.md`)

- `adapter/aws/AtomicClaimGateway` — performs exactly one composed claim transaction via `DynamoEventStore.append(submission, composer)`; requires an explicit `CriticalCapacity`; never calls `claim()`, never dispatches payment, never retries the write or resends. On an ambiguous SDK outcome it performs a strongly consistent dedupe lookup (environment-bound `producerId`/`eventId` stable token): exact match → `RECOVERED`, different content → `CONTENT_CONFLICT`, empty/failed lookup → `UNKNOWN`. A `TransactionCanceledException` is classified `NOT_COMMITTED`, not ambiguous.
- Tests (`DynamoAtomicClaimRecoveryIntegrationTest`, 4) against DynamoDB Local: ambiguous-after-commit recovery; ambiguous-before-commit → `UNKNOWN` with no writes; retry after recovery → `REPLAY`, one slot, one business row; aborted business precondition → `NOT_COMMITTED` with no event/HEAD/dedupe/slot/head.

### Commands and captured results

| Command | Exit | Result |
| --- | --- | --- |
| `mvn -o -q -DskipTests compile` | 0 | `BUILD SUCCESS` |
| `mvn -o -Dtest=...DynamoAtomicClaimRecoveryIntegrationTest test` | 0 | 4 tests, 0 failures/errors/skips |
| targeted suite (`domain.event.*`, `DynamoEventStore`, `DynamoCriticalClaim`, recovery, `AuthorizationCore`) | 0 | 96 tests, 0 failures/errors/skips |
| `mvn -o test` | 0 | 136 tests, 0 failures/errors/skips (132 prior + 4 new) |

Logs: `evidence/p1b2b-2026-11-20/` (`compile.log`, `claim-recovery-test.log`, `targeted-test.log`, `full-test.log`).

### Verified behaviour (DynamoDB Local only)

An ambiguous transport failure after an actual commit is resolved by one strongly consistent lookup, returning `RECOVERED` with the original sequence; no second event, slot, business record or dispatch is created. An ambiguous failure without a commit returns `UNKNOWN` and leaves all business/event/slot rows absent. A retry after recovery returns `REPLAY` with the same sequence and keeps the slot at 1. A composed business-condition abort is `NOT_COMMITTED` and leaves nothing behind. No record was dispatched (`dispatchedAt` null, state `AWAITING`), and the persisted `version` stayed `0` — this slice does not migrate it.

### Remaining gaps — next smallest slice

1. **Owner decision on version semantics** (public committed version vs storage CAS counter). Production claim wiring stays disabled until resolved.
2. **Production wiring** of the gateway into the claim path, replacing `claim()`-then-append; prerequisite: item 1.
3. `POSSIBLE_SEND` / result coupling, ambiguous send-path recovery, `CONTENT_CONFLICT` quarantine, `collectedAt` retention metadata, principal/context authorization, named OBS-20/21/22 tests.

### Non-claims

P1b2b-so-far is local implementation, not QA, Security review or acceptance; G6 is **not complete**. DynamoDB service behaviour (throttling, Streams, IAM, encryption, TTL) remains UNVERIFIED. No commit, push, PR, merge, deployment, cloud call or credential flow occurred. Full G6 stays pending until every transition is proven.

## 14. P1b2c — additive committed journal version (2026-11-20) — VERIFIED PARTIAL, NOT WIRED

Scope: resolve the P1b2b version blocker as an **implementation mapping** of the approved
committed-version semantics, not a business decision or spec amendment. The public committed journal
version is now persisted as its own claim field beside the unchanged internal CAS counter; a read
representation distinguishes a recorded committed version from a legacy row that has none.

### Mapping record

- Approved contract wins over legacy internal implementation (coordinator clarification): `journalVersion`/`businessVersion` are the committed journal version (claim = 1, positive integer); the internal `version` is an implementation-only CAS counter.
- Approved-text check found **no clause forbidding a separate storage field**; the contract requires the committed version to travel in the same critical transaction, which the composer does.
- Persisted explicitly: `committedJournalVersion = 1` on `AUTH#<id>/META`; `version = 0` unchanged.
- Never derived: no `version + 1` label transformation anywhere.
- Wrapper binding: the claim composer requires the composed wrapper's `journalVersion` to equal the persisted committed value.
- Future transitions (not this slice): any later business transition must atomically advance `committedJournalVersion`; CAS stays the lost-update guard.
- No migration, no backfill, no fabricated value for old rows. Legacy `claim()` writes no committed version, so an old row reads as committed evidence absent.

### Delivered files (hashes in `evidence/p1b2c-2026-11-20/`)

Application:
- `domain/event/CommittedVersionEvidence` (new) — `(storageVersion, committedJournalVersion)`, `committedVersionRecorded()`, `agreesWith(...)`; committed value must be positive when present.
- `domain/event/StoredEvent` — added `journalVersion` (null = no committed-version evidence).
- `adapter/aws/DynamoAuthorizationRepository` — additive attribute constant/helper and `readVersionEvidence(id)`; `claim()` and `mutate()` unchanged.
- `adapter/aws/ClaimCriticalComposer` — writes `committedJournalVersion = 1` beside `version = 0`; wrapper/version checks.
- `adapter/aws/DynamoEventStore` — dedupe metadata carries `journalVersion`; `find` returns it.
- `adapter/aws/AtomicClaimGateway` — javadoc only; recovery now returns the committed metadata via `StoredEvent`.

Tests (5 new): `CommittedVersionEvidenceTest` (3); 2 in `DynamoCriticalClaimIntegrationTest` (CAS0/committed1 agreement with stored event; legacy row missing evidence without backfill); 1 in `DynamoAtomicClaimRecoveryIntegrationTest` (ambiguous recovery reads committed metadata); 1 assertion added in `DynamoEventStoreIntegrationTest`.

### Commands and captured results

| Command | Exit | Result |
| --- | --- | --- |
| `mvn -o -q -DskipTests compile` | 0 | `BUILD SUCCESS` |
| targeted (4 classes) | 0 | 25 tests, 0 failures/errors/skips |
| `mvn -o test` | 0 | 141 tests, 0 failures/errors/skips (136 prior + 5 new) |

Logs: `evidence/p1b2c-2026-11-20/` (`compile.log`, `targeted-test.log`, `full-test.log`, `mapping.md`, `manifest.md`, `hashes.txt`).

### Verified behaviour (DynamoDB Local only)

On one committed claim: `version = 0` and `committedJournalVersion = 1` coexist; the event row and dedupe metadata carry `journalVersion = 1` and `stateAfter = CLAIMED_NOT_SENT`; `readVersionEvidence` returns `(0, 1)` with `agreesWith(0, 1) == true`. An ambiguous-after-commit recovery returns the stored committed version and state. A legacy `claim()` row has no `committedJournalVersion`; the read returns `(0, null)`, `committedVersionRecorded() == false`, and `agreesWith(0, 1) == false` — explicit absence, no backfill.

### Actual wiring status

Source-reference scan (`AtomicClaimGateway|ClaimCriticalComposer|DynamoEventStore` in `src/main/java`) returns only the three adapter files. `AuthorizationService` and `AwsRuntime` do not reference them; `repository.claim()` remains the runtime claim path. The atomic claim gateway stays disabled in `AwsRuntime`.

### Exact remaining

1. **Production wiring** of the atomic claim gateway into the claim path (replacing `claim()`-then-append) — not started; requires a coherent service-level slice, not a blind replacement.
2. `POSSIBLE_SEND` / result coupling and ambiguous send-path recovery — not started.
3. `CONTENT_CONFLICT` quarantine marking and `collectedAt` first-collection retention metadata — not started.
4. Principal/context authorization before dedupe (EV4 is event-partition-only) — not started.
5. Named OBS-20/21/22 core tests — not started.
6. Once a later business transition is implemented, it must atomically advance `committedJournalVersion`; that transition is not in this slice.

### Next smallest slice

Wire the claim adapter into the service/runtime only after a coherent read of the service claim flow, in one small change that keeps `claim()` as the fallback and proves the claim response reads the persisted committed version; no `POSSIBLE_SEND`/result work in that same step.

### Non-claims

G6 is not complete. P1b2c is local implementation, not QA, Security review or acceptance. DynamoDB service behaviour (throttling, Streams, IAM, encryption, TTL) remains UNVERIFIED. No capacity default, payment semantic, approved fixture/spec edit, commit, push, PR, merge, deployment, cloud call or credential flow occurred.

## 15. P1b2d — service-level claim branch (2026-11-20) — VERIFIED PARTIAL, AwsRuntime INACTIVE

Status: **IMPLEMENTED — LOCAL ONLY, NOT RUNTIME-WIRED.** Saved run recorded, not re-executed this turn.

### Binding and evidence

Branch `feat/wi-acq-auth-001`, HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`; model provider `nan`,
`deepseek-v4-flash`, no override. Evidence: `evidence/p1b2d-2026-11-20/` (`manifest.md`, `hashes.txt`,
both attempt-2 logs, XML reports). `full-test.log` is an interrupted run and is preserved as such.

| Saved command (already captured) | Exit | Result |
| --- | --- | --- |
| `mvn -o -q test-compile` (`compile-attempt2.log`) | 0 | quiet, no compiler output |
| `AtomicClaimServiceWiringIntegrationTest` (`wiring-attempt2.log`) | 0 | 6 tests, 0 F/E/S |
| targeted claim tests (`claim-tests-attempt2.log`) | 0 | `BUILD SUCCESS` |
| `mvn -o test` (`full-test-attempt2.log`) | 0 | **147 tests, 0 F/E/S** (141 prior + 6 new) |

Correspondence with current code: last source write 11:31:12; all attempt-2 logs 11:35–11:38; no file
under `src/main` modified after 11:38:15. Supported by chronology, not by embedded build hashes.

### What changed

- `domain/port/ClaimGateway` — new port; `UNKNOWN`, `CONTENT_CONFLICT`, `NOT_COMMITTED` are terminal.
- `adapter/aws/DynamoClaimGateway` — adapts `AtomicClaimGateway` + committed-evidence read.
- `domain/AuthorizationService` — optional gateway constructor; injected gateway performs exactly one
  `claim(...)`, with **no** fallback to `repository.claim()`, no retry, no resend.
- `adapter/aws/DynamoAuthorizationRepository` — shared key helpers, explicit
  `committedJournalVersion` attribute, and copy-forward on replacement (never drops recorded evidence).

### Precise wiring status

`AwsRuntime.processor()` still uses the 4-arg `AuthorizationService` constructor, so the atomic branch
is **inactive in the AWS runtime**. `DynamoClaimGateway` is constructed only by tests. Activation
remains blocked on the pending atomic transitions (dispatch-intent/`POSSIBLE_SEND`, result coupling,
later `committedJournalVersion` advance).

### Dispositions proven in the saved suite

| Gateway status | Service result | Dispatch / write |
| --- | --- | --- |
| `UNKNOWN` | `NOT_CONFIRMED` | none; no legacy claim, no retry |
| `CONTENT_CONFLICT` | `PAYMENT_DATA_CONFLICT` rejection | none |
| `NOT_COMMITTED` (no competing winner) | rejection path | no write, no reclaim |
| `CLAIMED` without persisted evidence | `NOT_CONFIRMED` | none; version never synthesized |

Metadata-loss disposition is explicit: additive `committedJournalVersion`, **no backfill** of legacy
rows, absence reported as `(0, null)` / `committedVersionRecorded() == false`, never invented and never
treated as "not sent". `UNKNOWN` is never converted to a no-send assertion.

### Next smallest slice

Implement the `POSSIBLE_SEND` / dispatch-intent + result coupling so the dispatch boundary is durable
before `AwsRuntime` is activated. Then, separately, construct `DynamoClaimGateway` in
`AwsRuntime.processor()` and prove the claim response reads the persisted committed version. Do not
activate the runtime in the same change as the send-path work. Verification command on the frozen tree
when authorized: `mvn -o test`.

### Non-claims

G6 is not complete. This is local implementation plus DynamoDB Local tests — not QA, Security review or
acceptance. DynamoDB service behaviour remains UNVERIFIED. No commit, push, PR, merge, deployment, cloud
call or credential flow occurred.

## 8. P1b3a — POSSIBLE_SEND dispatch intent (evidence finalization, 2026-11-20)

Status: **IMPLEMENTED, LOCALLY TARGETED-TESTED ON THE CURRENT SNAPSHOT, NOT PRODUCTION-WIRED; G6 COMPLETE → NO.**
Evidence and full chronology: `evidence/p1b3a-2026-11-20/manifest.md`.

- `PossibleSendCriticalComposer` composes the dispatch intent in **one** `TransactWriteItems`:
  conditioned business transition (claim journal version 1 / `CLAIMED_NOT_SENT` →
  `POSSIBLE_SEND` / version 2), immutable critical event (`POSSIBLE_SEND-<id>`, sequence 2),
  EVENT `HEAD` + dedupe metadata, and optional slot reservation.
- Guards: recorded CAS base (`version = 0`) plus `committedJournalVersion = 1` and undispatched
  record. Legacy/missing/concurrent/converted state fails the whole transaction closed. Public
  committed version advances explicitly to 2; it is never derived from the CAS counter.
- Slot accounting is **opt-in** (`withSlotAccounting(..., CriticalCapacity)` vs
  `withoutSlotAccounting`); no implicit capacity, O8 numbers remain **PROPOSED**. The first write
  requires a positive explicit budget; later writes require `usedSlots < slotBudget`.
- It records that a send is *possible* only: no downstream call, no payment result, no ambiguous
  recovery, and no resend authority. Ambiguous-commit resolution is a separate lookup slice.

### Evidence correspondence (important)

Two composer snapshots were tested, not one:

| Run | Composer SHA-256 | Result |
| --- | --- | --- |
| Attempt A | `089d76ca…` | compile 0; targeted send-intent 7/7; targeted regression 35/35; **full suite 154/154, exit 0** |
| Attempt B (current) | `b9a2579f…` | compile 0; targeted send-intent **7/7, exit 0**; full suite **INCOMPLETE** (aborted at startup) |

`hashes-after.txt` (`089d76ca`) is Attempt A and must not be read as the final hash;
`hashes-before-final.txt` (`b9a2579f`) is Attempt B. The behavioral delta between the two snapshots
cannot be reconstructed because the pre-edit composer content was not saved. The current tree is
verified only by the compile log and the 7-test targeted run; **do not claim the full suite passed
for the current source**. `full-test-final.log` is preserved as incomplete evidence, not as a
failure or a pass.

### Next smallest slice

Service-level `POSSIBLE_SEND` branch consuming this composer (still no `AwsRuntime` activation),
then the result-coupling / ambiguous-send resolver, each as a separately scoped change. Runtime
activation stays out of those slices.


## 9. P1b3a current-tree verification — full suite run (2026-11-20)

Status: **FULL SUITE RUN ON THE CURRENT TREE — 154 tests, 1 error, exit 1. NOT GREEN.**
Verification-only slice: no source edit, no feature, no Git write, no publication, no cloud call,
no apply, no credential use, no deletion, `AwsRuntime` inactive.
Evidence: `evidence/p1b3a-current-verification-2026-11-20/`.

- Binding re-verified: `/home/pazfernando/projects/acquiring-simulator`, branch
  `feat/wi-acq-auth-001`, HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`, configured default model,
  no override. `git status --porcelain` identical before and after (55 entries).
- Pre-run freeze over 101 files (`src/**` 98 + `pom.xml` + `infra/template.yaml` +
  `scripts/render_aws_fixtures.py`), post-run freeze identical — `hashes-diff.txt` empty. The run is
  therefore provably bound to composer `b9a2579f…`, send-intent test `075eccc8…`, `pom.xml`
  `b47fb547…`.
- `p1b3a-2026-11-20/hashes-current.txt` was reused **only after verification**: all 93 files it
  covered are byte-identical in the current tree; 5 previously unhashed resource files were added to
  coverage.
- Result belongs to the current source and supersedes the incomplete `full-test-final.log`:

  | Metric | Value |
  | --- | --- |
  | Tests run | 154 (18 classes) |
  | Failures | 0 |
  | Errors | 1 |
  | Skipped | 0 |
  | Exit | 1 — BUILD FAILURE |

- Single error: `AtomicClaimServiceWiringIntegrationTest.atomicClaimPathCommitsOnceDispatchesOnce…`
  → `EventValidationException: PROHIBITED_CONTENT at correlationId`. `randomId()` emits 24 hex
  characters; `EventSanitizer.PAN_SHAPE` (`\d{13,19}`) rejects a chance run of 13-19 digits. Measured
  collision rate **1.55%** (3,104 / 200,000). Not on the composer path (this test uses
  `CriticalWriteComposer.NONE`), and Attempt A passed the same 154 tests → non-determinism, not a
  regression of `b9a2579f…`.
- Next fix (not applied): one targeted rerun of that class to confirm flake, then make `randomId()`
  PAN-shape-proof plus a generator regression test. The sanitizer itself must not be relaxed (R20).
- Still unverified: DynamoDB service behaviour (emulator only), runtime wiring/activation (composer
  unreferenced by `AuthorizationService`/`AwsRuntime`). **G6 complete: NO.** No 154/154 claim.

## 16. P1b3b — generated-identifier sanitizer safety (2026-11-20) — FIXED, FULL SUITE GREEN 161/161

Status: **FIX APPLIED AND VERIFIED ON THE FROZEN CURRENT TREE — 161 tests, 0 failures, 0 errors,
exit 0.** Evidence: `evidence/p1b3b-generator-fix-2026-11-20/`.

The §9 failure is accepted as a real generator defect: `randomId()` could emit a 13–19 digit run
that `EventSanitizer.PAN_SHAPE` correctly treats as a PAN shape. The sanitizer was **not** relaxed
and no caller/user identifier is rewritten.

- `domain/IdentifierSource` (new) — injectable generator seam; the production source supplies 128
  random bits rendered as 32 lowercase hex characters, and normalization keeps only the first 24
  hex characters (contract length), so the output carries at most 96 bits of candidate material.
- `domain/event/SafeIdentifier` (new) — takes the first 24 lowercase hex characters (contract
  alphabet/length unchanged) and caps every digit run at 12 by replacing only the character that
  would extend the run past the limit with a hex letter derived from that digit. Safe candidates are
  returned unchanged; a dangerous candidate loses at most one nibble. This is a PAN-shape
  construction, **not** a global uniqueness guarantee: truncation can collide, and the digit→letter
  repair (`digit mod 6`) can map a repaired candidate onto a naturally safe one. Normalization is
  therefore not globally injective, and generated identifiers are not proven unique across the
  whole input space.
- `domain/AuthorizationService` — optional 6-arg constructor with `IdentifierSource` (existing 4-/5-arg
  constructors unchanged); `randomId()` is now `SafeIdentifier.normalize(identifiers.nextCandidate())`.
  `AUTH-`/`CORR-`/`EVT-` prefixes, references and event derivations are unchanged.
- Tests (7 new): `SafeIdentifierTest` (6) — exhaustive repair over every digit-run length 13–24 and
  start position, safe-identity, deterministic reproduction of the rejected raw value, sampled
  distinctness of 16 variants after the repair point (a sample assertion, not global injectivity),
  input rejection, and proof the PIN-block/JWT/Bearer heuristics still reject;
  `GeneratedIdentifierSanitizerTest` (1) — real `AuthorizationService.authorize` with a controllable
  dangerous generator, asserting for that fixed sample that the generated authorization/correlation/
  event identifiers pass `EventSanitizer`, match `(AUTH|CORR|EVT)-[0-9a-f]{24}`, stay ≤64
  characters, are distinct within the sample, keep 23 of 24 characters on repair and pass a safe
  candidate through unchanged. These are sample assertions; they do not claim universal uniqueness
  or preserved 128-bit output.

| Command (absolute JDK/Maven, `JAVA_HOME` pinned) | Exit | Result |
| --- | --- | --- |
| `mvn -o -q -DskipTests compile` | 0 | quiet |
| targeted (2 new classes) | 0 | 7 tests, 0 F/E/S |
| `mvn -o test` (105 files frozen before/after; `diff` empty) | 0 | **161 tests, 0 F/E/S** |

`AtomicClaimServiceWiringIntegrationTest` (the §9 failing class) ran 6 tests with 0 failures/errors.
The prior 154-test failure log is preserved and was not re-run unchanged. The deterministic repair
argument backs the **PAN-shape safety** construction only; it is not a uniqueness proof, and
truncation/repair can still collide (documentation corrected in P1b3c below).

Still open: `AwsRuntime` inactive; G6 not complete; DynamoDB service behaviour unverified. No
send-path feature work, Git write, publication, cloud call, apply, credential flow, deletion or
specification change occurred this turn.

## 17. P1b3c — service-level dispatch-intent gateway (2026-11-20) — IMPLEMENTED, LOCALLY TESTED, RUNTIME INACTIVE

Status: **IMPLEMENTED AND LOCALLY TESTED on the frozen current tree — 13 new tests, full suite
174/174, exit 0. `AwsRuntime` remains inactive; result coupling is NOT implemented.**
Evidence: `evidence/p1b3c-2026-11-20/` (`compile.log`, `service-test.log`,
`intent-adapter-test.log`, `full-test.log`, `hashes-before.txt`, `hashes-run-before.txt`,
`hashes-run-after.txt`, `hashes-diff.txt`, `git-status-*.txt`, `manifest.md`).

### Binding re-verified

| Field | Observed |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged; no commit this turn) |
| Origin | `https://github.com/pazfernando/acquiring-simulator.git` |
| Model | provider `nan`, model `deepseek-v4-flash`; no override |
| Pre-existing changes | preserved; no revert, stage or overwrite |
| Run binding | 103-file `src/**` + `pom.xml` hash set identical before/after the run (`diff` empty); `git status --porcelain` identical before/after |

### Delivered files

Application:
- `domain/port/DispatchIntentGateway` (new) — narrow port with `COMMITTED`, `REPLAY`, `RECOVERED`,
  `CONTENT_CONFLICT`, `NOT_COMMITTED`, `UNKNOWN`. `Decision.permitsSend()` is true **only** for
  `COMMITTED` with recorded persisted evidence; replay/recovery are explicitly not resend permission.
- `adapter/aws/DynamoDispatchIntentGateway` (new) — exactly one composed
  `PossibleSendCriticalComposer` transaction through `DynamoEventStore`; reads the exact persisted
  transition back into `CommittedVersionEvidence`; maps a cancelled transaction to `NOT_COMMITTED`;
  resolves an ambiguous transport outcome by one strongly consistent dedupe lookup (`RECOVERED` on
  exact match, `CONTENT_CONFLICT` on different content, `UNKNOWN` on absence/failure). It never calls
  `markDispatched`, appends no legacy event and defaults no capacity.
- `domain/AuthorizationService` — optional 7-arg constructor adds `DispatchIntentGateway` (existing
  4/5/6-arg constructors unchanged). A dispatch-intent gateway without the atomic claim gateway is
  rejected at construction. `dispatchCommitted` commits the intent before sending and gates the
  downstream on `COMMITTED` + recorded evidence; every other outcome returns `NOT_CONFIRMED` with no
  send, no `markDispatched` and no legacy append. Legacy path unchanged when the port is absent.

Tests (2 new files, 13 tests):
- `DispatchIntentServiceWiringTest` (9, no container) — new intent commits before the fake downstream
  and sends exactly once; cancelled, unknown, replay, recovered, conflict and evidence-absent
  outcomes never reach the downstream or the legacy `markDispatched`; the legacy path still works
  without the port; invalid port combination rejected.
- `DynamoDispatchIntentGatewayIntegrationTest` (4, DynamoDB Local) — new intent persists CAS 1 /
  committed 2 / `POSSIBLE_SEND` event with HEAD 2 and slot 2; replay writes nothing new; cancelled
  intent leaves the claim untouched; ambiguous-without-commit stays `UNKNOWN` with no intent row.

### Commands and captured results

| Command (absolute JDK/Maven, `JAVA_HOME` pinned) | Exit | Result |
| --- | --- | --- |
| `mvn -o -q -DskipTests compile` | 0 | quiet |
| `DispatchIntentServiceWiringTest` | 0 | 9 tests, 0 F/E/S |
| `DynamoDispatchIntentGatewayIntegrationTest` | 0 | 4 tests, 0 F/E/S |
| `mvn -o test` (frozen tree) | 0 | **174 tests, 0 F/E/S**, `BUILD SUCCESS` |

### Verified behaviour (DynamoDB Local + service doubles)

A physical send occurs only after a freshly committed intent and only when that intent's committed
evidence was read back from persistence. `REPLAY` and `RECOVERED` recover knowledge without granting
a resend; `UNKNOWN` stays uncertain (no send, no claim that nothing was sent); `NOT_COMMITTED` and
`CONTENT_CONFLICT` send nothing. The exact transition is durable: CAS `version = 1`, committed
`committedJournalVersion = 2`, `stateAfter = POSSIBLE_SEND`, event sequence 2, HEAD 2, slot 2.

### Documentation correction (identifier normalization)

The P1b3b record was overstated and is corrected: normalization is **not** globally injective and
does **not** guarantee uniqueness. Truncation to the first 24 hex characters reduces the candidate
to at most 96 bits, and the digit→letter repair (`digit mod 6`) can collide with a naturally safe
candidate. The test description is limited to the fixed-sample assertions actually executed.
No generator redesign was made in this slice.

### Wiring status and exact next slice

`AwsRuntime.processor()` still builds the 4-arg `AuthorizationService` (legacy claim and legacy
dispatch). `DynamoDispatchIntentGateway` and the optional service branch are constructed only by
tests. The incomplete result path (confirmed outcome + terminal event coupled atomically) is **not**
wired and would run no external call in this slice. Next bounded slice: result coupling and
ambiguous-result resolution for the already-durable intent, keeping runtime activation separate.

### Non-claims

G6 is not complete. This is local implementation plus DynamoDB Local tests — not QA, Security review
or acceptance. DynamoDB service behaviour (throttling, Streams, IAM, encryption, TTL) remains
UNVERIFIED. No commit, push, PR, merge, deployment, cloud call, apply, credential flow, deletion or
approved-specification edit occurred.

## 18. P1b4a — atomic verified RESULT composer (2026-11-20) — IMPLEMENTED, LOCALLY TESTED, RUNTIME INACTIVE

Status: **IMPLEMENTED AND LOCALLY TESTED on the frozen current tree — 7 new tests, full suite
181/181, exit 0. `AwsRuntime` and `AuthorizationService` unchanged; no result gateway and no
ambiguous-result resolution exist yet. G6 COMPLETE → NO.**
Evidence: `evidence/p1b4a-2026-11-20/` (`binding.txt`, `hashes-before.txt`, `hashes-run-before.txt`,
`hashes-run-after.txt`, `hashes-diff.txt`, `git-status-*.txt`, `compile.log`, `result-test.log`,
`targeted-test.log`, `full-test.log`, `manifest.md`).

### Binding re-verified

| Field | Observed |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged; no commit this turn) |
| Origin | `https://github.com/pazfernando/acquiring-simulator.git` |
| Model | provider `nan`, model `deepseek-v4-flash`; no override |
| Pre-existing changes | preserved; no revert, stage or overwrite |
| Run binding | 109-file `src/**` + `pom.xml` hash set identical before/after (`diff` empty); `git status --porcelain` identical before/after |

### Delivered files

- `adapter/aws/ResultCriticalComposer` (new) — one `TransactWriteItems`: conditioned business
  record from the durable dispatch intent to the confirmed outcome, explicitly advanced committed
  journal version 2→3, immutable critical result event (sequence 3), EVENT HEAD and dedupe
  metadata, plus opt-in slot reservation. It accepts only authenticated, correlated
  `APPROVED`/`DECLINED` evidence whose availability is not before the durable dispatch. No
  capacity default, no downstream call, no resend, no reversal.
- `DynamoResultTransitionIntegrationTest` (new, 7 tests) against DynamoDB Local.

No pre-existing source or test file was modified; the P1b3a/B/C paths are byte-identical.

### Fixed mapping and precedence

| Aspect | Value |
| --- | --- |
| Prior state | journal version 2 / `POSSIBLE_SEND`, CAS 1, dispatched, no determined outcome |
| Result state | journal version 3 / `APPROVED` or `DECLINED` (`decisionSource = ISSUER`, `outcomeDeterminedAt` = evidence time) |
| Result event | `<STATE>-<authorizationId>`, producer sequence 3, `business-persist`/`SUCCEEDED`/`VALID`, correlation = issued dispatch correlation |
| Persistence guard | exact CAS/committed pair `(1, 2)` written only by the dispatch-intent transition; any later write advances the CAS and aborts a stale result |
| Evidence guard | correlation must equal the persisted dispatch correlation; availability cannot predate the dispatch; `NO_RESPONSE`/`MALFORMED`/`NETWORK_REJECTED`/uncorrelated evidence refused |

Late evidence commits the same confirmed state with the evidence time; the caller's conservative
boundary outcome and the late/conflict evidence event variants stay a later slice. A contradictory
confirmation is refused rather than applied, so the first committed result is never overwritten.

### Commands and captured results

| Command (absolute JDK/Maven, `JAVA_HOME` pinned) | Exit | Result |
| --- | --- | --- |
| `mvn -o -q -DskipTests compile` | 0 | quiet |
| `DynamoResultTransitionIntegrationTest` — first attempt | 1 | 7 run, 3 errors: record condition used a top-level `dispatchedAt` that the row does not carry; corrected to the CAS/committed guard (failed log not retained) |
| same targeted command after correction | 0 | 7 tests, 0 F/E/S |
| targeted regression (`domain.event.*`, claim, send, result, recovery) | 0 | 87 tests, 0 F/E/S |
| `mvn -o test` (frozen tree) | 0 | **181 tests, 0 F/E/S; BUILD SUCCESS** |

### Verified behaviour (DynamoDB Local only)

APPROVED result commits state/evidence/event/HEAD/dedupe/slot together (CAS 2, committed 3, sequence
3, HEAD 3, `usedSlots` 3 of explicit budget 8, `stateAfter APPROVED`). DECLINED has the same shape
with a distinct stable event identity. Exact replay returns `EXACT_REPLAY` at sequence 3 with no
second event and no second slot. A competing determined outcome cancels the composed transaction
with no partial event/HEAD/slot and no state override. A result without a durable dispatch intent,
a mislabelled journal version/state, or uncorrelated/non-confirmed/pre-dispatch evidence is refused
before any write.

### Exact remaining

1. Service-level result branch consuming this composer (still one bounded slice) — not started.
2. Ambiguous-result resolution on the already-durable intent — not started; this composer neither
   retries nor resends.
3. Late-evidence/conflict evidence event variants and boundary disposition — partially preserved
   (state retained, contradiction refused); named variants not implemented.
4. `NETWORK_REJECTED` result path, `CONTENT_CONFLICT` quarantine, `collectedAt` retention metadata,
   principal/context authorization before dedupe, named OBS-20/21/22 tests — not started.
5. `AwsRuntime` activation remains blocked on the remaining atomic transitions.

### Non-claims

G6 is not complete. P1b4a is local implementation plus DynamoDB Local tests — not QA, Security
review or acceptance. DynamoDB service behaviour (throttling, Streams, IAM, encryption, TTL)
remains UNVERIFIED. The test capacity `(5, 8)` is an example, not approved capacity. No commit,
push, PR, merge, deployment, cloud call, apply, credential flow, deletion or approved-specification
edit occurred.

## 19. P1b4b — RESULT gateway/recovery port + adapter (2026-11-20) — IMPLEMENTED, LOCALLY TESTED, RUNTIME INACTIVE

Status: **IMPLEMENTED AND LOCALLY TESTED on the frozen current tree — 6 new tests, full suite
187/187, exit 0. `AwsRuntime` and `AuthorizationService` unchanged; no service result branch and
no runtime activation. G6 COMPLETE → NO.**
Evidence: `evidence/p1b4b-2026-11-20/` (`binding.txt`, `hashes-run-before.txt`,
`hashes-run-after.txt`, `hashes-diff.txt`, `git-status-*.txt`, `compile.log`,
`result-gateway-test-attempt1.log`, `targeted-test.log`, `full-test.log`, `manifest.md`).

### Binding re-verified

| Field | Observed |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged; no commit this turn) |
| Origin | `https://github.com/pazfernando/acquiring-simulator.git` |
| Model | provider `nan`, model `deepseek-v4-flash`; no override |
| Pre-existing changes | preserved; no revert, stage or overwrite |
| Run binding | 111-file `src/**` + `pom.xml` hash set identical before/after (`diff` empty); `git status --porcelain` identical before/after |

### Delivered files

- `domain/port/ResultGateway` (new) — narrow port for one atomic verified-result commit, with
  `COMMITTED`/`REPLAY`/`RECOVERED`/`CONTENT_CONFLICT`/`NOT_COMMITTED`/`UNKNOWN`, the persisted
  confirmed state and committed-version evidence. `recorded()` is true only for persistence-backed
  success/replay/recovery.
- `adapter/aws/DynamoResultGateway` (new) — constructs exactly one
  `ResultCriticalComposer.withSlotAccounting(...)` per call, appends once, then reads the stored
  record and committed version back before reporting success.
- `DynamoResultGatewayIntegrationTest` (new, 6 tests) against DynamoDB Local.

No pre-existing source or test file was modified. `AuthorizationService` stays
`aa2bfe92538c3db399e05023189a9b1a2d8967c01061cb821133fa6032955633`; `AwsRuntime` stays
`99c32249f1b577dc0645ffed7e2751aa0c1edcdd1b51a22b92ebb639f0f36c7b`.

### Fixed mapping and recovery rule

| Aspect | Value |
| --- | --- |
| Success gate | persisted state = evidence decision, `decisionSource = ISSUER`, preserved dispatch correlation, CAS 2, committed journal version 3 |
| Ambiguous outcome | one strong lookup of `APPROVED-<id>`/`DECLINED-<id>`: exact metadata → `RECOVERED` knowledge; different content → `CONTENT_CONFLICT`; empty or failed lookup → `UNKNOWN` |
| Competing outcome | conditioned transaction cancelled → `NOT_COMMITTED`; the stored terminal outcome is retained, never overwritten |
| Forbidden paths | no resend, no legacy event append, no `updateOutcome`, no `markDispatched`, no reversal or inquiry |
| Capacity | constructor requires an explicit `CriticalCapacity`; no default |

### Commands and captured results

| Command (absolute JDK/Maven, `JAVA_HOME` pinned) | Exit | Result |
| --- | --- | --- |
| `mvn -o -q -DskipTests compile` | 0 | quiet |
| `DynamoResultGatewayIntegrationTest` attempt 1 | 0 | 6 tests, 0 F/E/S |
| targeted regression (`domain.event.*`, claim, send, result, recovery, gateway, wiring doubles) | 0 | 112 tests, 0 F/E/S |
| `mvn -o test` (frozen tree) | 0 | **187 tests, 0 F/E/S; BUILD SUCCESS** |

### Verified behaviour (DynamoDB Local only)

`COMMITTED` requires persisted `APPROVED`, issuer provenance, evidence time, CAS 2, committed
version 3, sequence 3, HEAD 3 and slot 3 of the explicit budget 8. A committed-but-lost response
recovers once as `RECOVERED` with evidence `(2, 3)` and no duplicate event/HEAD/slot. Ambiguity
before commit and a failing strong read both stay `UNKNOWN` with no partial result. Exact replay
returns `REPLAY` with no extra version, event or slot. A competing `DECLINED` is retained while the
attempted `APPROVED` commit is cancelled.

### Failure-artifact disclosure

The P1b4a missing failed-attempt log remains missing and is not claimed. P1b4b attempt logs use
distinct file names and were not overwritten; the first attempt passed, so no failure log exists.

### Exact remaining

1. Service-level result branch consuming `ResultGateway` (separate slice) — not started.
2. Result service coupling with the dispatch/send path — not started.
3. Late-evidence/conflict evidence event variants and boundary disposition — not started.
4. `NETWORK_REJECTED` result path, `CONTENT_CONFLICT` quarantine, `collectedAt` retention metadata,
   principal/context authorization before dedupe, named OBS-20/21/22 tests — not started.
5. `AwsRuntime` activation remains blocked on the remaining atomic transitions.

### Non-claims

G6 is not complete. P1b4b is local implementation plus DynamoDB Local tests — not QA, Security
review or acceptance. DynamoDB service behaviour (throttling, Streams, IAM, encryption, TTL)
remains UNVERIFIED. The test capacity `(5, 8)` is an example, not approved capacity. No commit,
push, PR, merge, deployment, cloud call, apply, credential flow, deletion or approved-specification
edit occurred.

## 20. P1b4c — service-level RESULT integration (2026-11-20) — IMPLEMENTED, LOCALLY TESTED, RUNTIME INACTIVE

Status: **IMPLEMENTED AND LOCALLY TESTED on the frozen current tree — 12 new tests, full suite
199/199, exit 0. `AwsRuntime` unchanged/inactive; late-evidence variants, `NETWORK_REJECTED`,
conflict/quarantine metadata, ambiguous-result policy and named OBS-20/21/22 remain open.
G6 COMPLETE → NO.**
Evidence: `evidence/p1b4c-2026-11-20/` (`binding.txt`, `hashes-run-before.txt`,
`hashes-run-after.txt`, `hashes-diff.txt`, `git-status-*.txt`, `compile.log`, `test-compile.log`,
`result-service-test-attempt1.log`, `targeted-test.log`, `full-test.log`, `manifest.md`).

### Binding re-verified

| Field | Observed |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged; no commit this turn) |
| Origin | `https://github.com/pazfernando/acquiring-simulator.git` |
| Model | provider `nan`, model `deepseek-v4-flash`; no override |
| Pre-existing changes | preserved; no revert, stage or overwrite |
| Run binding | 113-file `src/**` + `pom.xml` hash set (after) differs from the before set only by `AuthorizationService` and the new test file; `git status --porcelain` differs only by the new untracked test file |

### Delivered files

- `domain/AuthorizationService` (modified) — new optional `ResultGateway` field and 8-arg constructor;
  `sendDownstream` branches to `evaluateAtomicResult` only when the port is present. Existing
  4/5/6/7-arg constructors and the legacy result path are unchanged.
- `ResultServiceWiringTest` (new, 12 tests, no container) — service doubles for the claim,
  dispatch-intent and result ports; a counting downstream and a repository that counts every legacy
  write.

No pre-existing test file was modified. `AwsRuntime` stays
`99c32249f1b577dc0645ffed7e2751aa0c1edcdd1b51a22b92ebb639f0f36c7b` and still builds the 4-arg
legacy `AuthorizationService`.

### Fixed mapping and fail-closed disposition

| Reply / gateway outcome | Service result | Writes |
| --- | --- | --- |
| Correlated on-time `APPROVED`/`DECLINED`, gateway `COMMITTED`/`REPLAY`/`RECOVERED` with recorded evidence | stored `APPROVED`/`DECLINED` outcome | none beyond the atomic transaction |
| Recorded status but absent committed-version evidence | `NOT_CONFIRMED` | none |
| `CONTENT_CONFLICT` | authoritative **stored** outcome read back | none; caller reply never overwrites |
| `NOT_COMMITTED` / `UNKNOWN` | `NOT_CONFIRMED` | none; no fabricated result, no no-send assertion |
| Late confirmation | `NOT_CONFIRMED` (boundary); gateway **not** called | none |
| `NO_RESPONSE` / `MALFORMED` / `NETWORK_REJECTED` / uncorrelated | `NOT_CONFIRMED` | none; no legacy path |
| `submitEvidence` / `evaluateDeadline` on an atomic-mode service | explicit pending / read-only | none |

### Commands and captured results

| Command (absolute JDK/Maven, `JAVA_HOME` pinned) | Exit | Result |
| --- | --- | --- |
| `mvn -o -q -DskipTests compile` | 0 | quiet |
| `mvn -o -q -DskipTests test-compile` (final) | 0 | quiet |
| `ResultServiceWiringTest` attempt 1 | 0 | 12 tests, 0 F/E/S |
| targeted regression (`domain.event.*`, claim, send, result, recovery, gateway, wiring doubles) | 0 | 151 tests, 0 F/E/S |
| `mvn -o test` (frozen tree) | 0 | **199 tests, 0 F/E/S; BUILD SUCCESS** |

### Failure-artifact disclosure

The first `test-compile` attempt failed inside the new test file (four `cannot find symbol: calls`
errors from a locally typed fake gateway). That log was overwritten by the successful recompile and
is **not retained**; the correction was test-only typing. `result-service-test-attempt1.log` is the
untouched first run and passed, so no failed test log exists. The P1b4a missing failed-attempt log
remains missing and is not claimed.

### Verified behaviour (service doubles)

Approved and declined replies each produce exactly one downstream call and one result-gateway call,
with the outcome read from persisted state. A committed-but-lost response recovers the persisted
result with one downstream call. A duplicate returns the stored outcome without a resend. `UNKNOWN`
and `NOT_COMMITTED` return `NOT_CONFIRMED` with no legacy outcome/event write and no fabricated
financial result. A competing `DECLINED` is reported even though the caller's reply was `APPROVED`.
Late, network-rejected and uncorrelated replies fail closed with zero legacy writes; late evidence
admission is explicitly pending. A result gateway without both atomic predecessors is rejected at
construction.

### Exact remaining

1. Late-evidence and conflict event variants, boundary disposition (C3 envelope disclosure of the
   conflict) — not started.
2. `NETWORK_REJECTED` atomic result path — not started; currently fails closed.
3. Ambiguous-result policy at the service level (the adapter rule exists) and `CONTENT_CONFLICT`
   quarantine/retention metadata — not started.
4. `collectedAt` first-collection retention, principal/context authorization before dedupe, named
   OBS-20/21/22 tests — not started.
5. `AwsRuntime` activation remains blocked on the remaining atomic transitions; do not activate in
   the same change as the send/result work.

### Next smallest slice

Add the `NETWORK_REJECTED` atomic composer plus its service coupling (or, alternatively, the named
OBS-22 service scenario for ambiguous result after possible send), each as a separately scoped
change with the runtime still inactive. Do not bundle late variants, conflict variants, quarantine,
retention and activation into one step.

### Non-claims

G6 is not complete. P1b4c is local implementation plus service-double tests — not QA, Security
review or acceptance. DynamoDB service behaviour (throttling, Streams, IAM, encryption, TTL)
remains UNVERIFIED. No capacity default, payment semantic, approved fixture/specification edit,
commit, push, PR, merge, deployment, cloud call, apply, credential flow or deletion occurred.


## 21. P1b5b — network-result gateway/adapter + citation attribution (2026-11-20) — IMPLEMENTED, LOCALLY TESTED, RUNTIME INACTIVE

Status: **IMPLEMENTED AND LOCALLY TESTED on the frozen current tree — 7 new tests, full suite
208/208, exit 0. `AwsRuntime` and `AuthorizationService` unchanged; no service coupling and no
runtime activation. G6 COMPLETE → NO.**
Evidence: `evidence/p1b5b-2026-11-20/` (`binding.txt`, `hashes-pre-test.txt`,
`hashes-post-test.txt`, `hashes-diff.txt`, `git-status-before-test.txt`, `git-status-after.txt`,
`git-status-diff.txt`, `network-gateway-test-attempt1.{stdout,stderr}.log`, `.exit.txt`,
`targeted-test.*`, `full-test.*`, `wiring-scan.txt`, `reason-allowlist-check.txt`,
`citations-addendum.md`, `manifest.md`).

**Correction (2026-11-20, documentation only):** this list previously named `compile.log` and
`test-compile.log` in `evidence/p1b5b-2026-11-20/`. Neither file exists there — that directory
contains no compile artifact and no compile exit file. The P1b5b compile and test-compile steps were
quiet runs whose streams were not retained, so P1b5b carries no independent compile evidence of its
own; its verified claim rests on the retained hash/status manifests and the targeted/full-suite logs.
P1b5c later captured a quiet `compile.exit.txt` = 0 for the following slice. No other P1b5b line is
amended.

Context: `P1b5a` (evidence `evidence/p1b5a-2026-11-20/`) supplied the atomic
`NetworkRejectedCriticalComposer` and its two targeted tests (full suite 199 → 201). This slice adds
the narrow gateway/adapter over it. No full-suite claim is made for P1b5a itself; the 208-test run
below covers the current tree, which includes it.

### Binding re-verified

| Field | Observed |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged; no commit this turn) |
| Origin | `https://github.com/pazfernando/acquiring-simulator.git` |
| Model | provider `nan`, model `deepseek-v4-flash`; no override |
| Pre-existing changes | 71 dirty entries preserved; no revert, stage or overwrite |
| Run binding | 118-file `src/**` + `pom.xml` hash set identical before/after (`diff` empty); `git status --porcelain` identical before/after |

### P1b5a citation review — attribution corrected, not a business conflict

Verified rather than assumed: the legacy `AuthorizationService` already records state
`NETWORK_REJECTED`, `DecisionSource.NETWORK` and the literal internal category `"NETWORK_REJECTION"`
(lines 209–210, 577–578) and returns `PROCESSOR_REJECTED` (line 580), with the replay mapping
`NETWORK_REJECTED → PROCESSOR_REJECTED` (line 618). The approved event reason allowlist
`EventReason` does not contain `NETWORK_REJECTION`, and neither does `RejectionReason`.
Therefore NET-01/OBS-03 not naming the literal is **not** a conflict: the string is existing internal
domain-code vocabulary placed on the business record, while the critical event keeps
`EventReason.VALID`. The composer's javadoc was corrected to attribute sourced state/source to
R05/R10/R22 and the reason string to domain code; its executable behaviour is unchanged. No public
code was invented and no specification, fixture or worklist document was edited. The byte-preserved
P1b5a record stands; `citations-addendum.md` records the correction.

### Delivered files

- `domain/port/NetworkResultGateway` (new) — narrow network counterpart of `ResultGateway`;
  statuses `COMMITTED`/`REPLAY`/`RECOVERED`/`CONTENT_CONFLICT`/`NOT_COMMITTED`/`UNKNOWN`.
  `recorded()` is true only for persistence-backed success with `NETWORK_REJECTED` and recorded
  committed version; `callerOutcome()` renders the existing fixed mapping
  (`PROCESSOR_REJECTED` for recorded evidence, otherwise `NOT_CONFIRMED`).
- `adapter/aws/DynamoNetworkResultGateway` (new) — constructs exactly one
  `NetworkRejectedCriticalComposer.withSlotAccounting(...)` per call, appends once, then reads the
  stored record and committed version back before reporting success. Cancelled transaction →
  `NOT_COMMITTED`; ambiguous transport outcome → one strongly consistent dedupe lookup
  (`RECOVERED` on exact match, `CONTENT_CONFLICT` on different content, `UNKNOWN` on absence or
  lookup failure). It never calls `markDispatched`, `updateOutcome`, a legacy event/rejection append
  or a downstream operation, and defaults no capacity.
- `adapter/aws/NetworkRejectedCriticalComposer` (javadoc only) — attribution correction described
  above; hash `a3160a50…` replaces P1b5a's `b781020f…` for the comment change alone.
- `DynamoNetworkResultGatewayIntegrationTest` (new, 7 tests) against DynamoDB Local.

### Fixed mapping

| Aspect | Value |
| --- | --- |
| Admitted evidence | explicit `NETWORK_REJECTED` only; correlated to the issued dispatch; not before the durable dispatch; `AWAITING`/`NONE` intent |
| Committed state | `NETWORK_REJECTED`, `DecisionSource.NETWORK`, internal category `NETWORK_REJECTION`, evidence time |
| Persistence guard | exact CAS/committed pair `(1, 2)`; any later write cancels a stale rejection |
| Committed version | journal version 3, explicitly persisted; never derived |
| Event | `NETWORK_REJECTED-<id>`, sequence 3, `PROCESSOR`/`business-persist`/`SUCCEEDED`/`VALID`, issued correlation; no issuer actor/task |
| Channel outcome | `PROCESSOR_REJECTED` (never `ISSUER_DECLINED`); `NOT_CONFIRMED` when nothing was recorded |
| Capacity | constructor-required explicit `CriticalCapacity`; no default; `(5, 8)` in tests is an example |

### Commands and captured results

| Command (absolute JDK/Maven, `JAVA_HOME` pinned) | Exit | Result |
| --- | --- | --- |
| `mvn -o -q -DskipTests compile` | 0 | quiet |
| `mvn -o -q -DskipTests test-compile` | 0 | quiet |
| `DynamoNetworkResultGatewayIntegrationTest` attempt 1 | 0 | 7 tests, 0 F/E/S |
| targeted regression (`domain.event.*`, store/claim/send/result/network transition + gateways, wiring doubles) | 0 | 136 tests, 0 F/E/S |
| `mvn -o test` (frozen tree) | 0 | **208 tests, 0 F/E/S; BUILD SUCCESS** |

No failed attempt occurred; attempt logs use unique names so nothing was overwritten.

### Verified behaviour (DynamoDB Local only)

A correlated network rejection commits one transaction: state/evidence/event/HEAD/dedupe/slot
together, CAS 2, committed 3, sequence 3, HEAD 3, slot 3 of explicit budget 8, and the caller sees
`PROCESSOR_REJECTED`. An exact replay adds no sequence, event or slot. A committed-but-lost response
recovers once with `NETWORK_REJECTED`/`NETWORK` provenance and `(2, 3)` evidence. Ambiguity before
commit and a failing strong lookup both stay `UNKNOWN` with no partial rejection. A stored issuer
`APPROVED` outcome cancels the rejection and is retained; `APPROVED` evidence passed to the network
gateway is refused before any write.

### Precise wiring status

Source scan finds `NetworkResultGateway`/`DynamoNetworkResultGateway` only in the two new files; no
reference from `AuthorizationService` or `AwsRuntime`. `AuthorizationService` stays
`eda810e9…` (P1b4c state, not edited here), `AwsRuntime` stays `99c32249…` and still builds the 4-arg
legacy service. Runtime activation remains blocked on the remaining atomic transitions.

### Exact remaining

1. Service-level network-rejection branch consuming `NetworkResultGateway` — not started.
2. Late-evidence/conflict event variants, boundary disposition (C3 envelope) — not started.
3. Ambiguous-result policy at service level and `CONTENT_CONFLICT` quarantine — not started.
4. `collectedAt` first-collection retention, principal/context authorization before dedupe, named
   OBS-20/21/22 tests — not started.
5. `AwsRuntime` activation — not started and not bundled into any of the above.

### Next smallest slice

The service-level network branch (recorded rejection → conservative `PROCESSOR_REJECTED`; every other
outcome → `NOT_CONFIRMED`, no legacy write), runtime still inactive.

### Non-claims

G6 is not complete. P1b5b is local implementation plus DynamoDB Local tests — not QA, Security review
or acceptance. DynamoDB service behaviour (throttling, Streams, IAM, encryption, TTL) remains
UNVERIFIED. The `(5, 8)` capacity is an example, not approved capacity. No commit, push, PR, merge,
deployment, cloud call, apply, credential flow, deletion or approved-specification edit occurred.

## 22. P1b5c — service-level network-rejection branch (2026-11-20) — IMPLEMENTED, LOCALLY TESTED, RUNTIME INACTIVE

Status: **IMPLEMENTED AND LOCALLY TESTED — full suite 220/220, 0 failures/errors/skips, exit 0,
BUILD SUCCESS at 12:45:52 on snapshot `5803cdf6`. `AwsRuntime` unchanged and still builds the legacy
4-argument service; runtime activation not started; G6 COMPLETE → NO.**

This section was written **after** the slice ran, from the retained files, as the documentation
finalization of work already on disk. No source file was edited, no test was re-run and no new suite
was started to produce it. Evidence and its per-file classification: `evidence/p1b5c-2026-11-20/`,
with `manifest.md` in that directory.

### Binding

| Field | Observed |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `cfc456f4f469d9558a78fda8ff9b770b00817315` (no commit this slice) |
| Configured model | provider `nan`, model `deepseek-v4-flash`; no override |
| Test snapshot after the run | 119-file `sha256sum` set, `hashes-post-test.txt` (12:45:56) — byte-identical to `hashes-pre-final-test.txt` and to the current tree |
| Working tree | `git status --porcelain` byte-identical before/after and still identical now (71 dirty entries preserved) |

### Delivered change

- `domain/AuthorizationService` now takes an optional `NetworkResultGateway` as its **last**
  dependency (`9`-argument constructor) and routes one explicit, correlated, on-time
  `NETWORK_REJECTED` reply to it from the atomic result path. Diff against HEAD: `+369/−3`.
  Current hash `ac2ceaeeca1fdb45e9ab70ce2d48e2ef50be687b791796926f432dd877594af4` (same before and
  after the test run).
- `aws/NetworkResultServiceWiringTest` — 12 tests covering the dispositions below. Current hash
  `5803cdf6683f7b513e06b09e0d5ee9199947907609e2fc5648cd8948c8bd1825`.

Constructor prerequisites are enforced, not documented only: a network gateway requires the atomic
claim, dispatch-intent **and** result gateways (`a network-result gateway requires…`), and a result
gateway already requires claim + dispatch intent. Null network gateway is the legacy shape.

### Exact network conflict/uncertainty disposition (no legacy fallback)

| Outcome | Service result | Writes |
| --- | --- | --- |
| `COMMITTED` / `REPLAY` / `RECOVERED` **and** `decision.recorded()` | `PROCESSOR_REJECTED` | none beyond the gateway's single transaction |
| `COMMITTED`/`REPLAY`/`RECOVERED` but `recorded()` false | `NOT_CONFIRMED` | none |
| `CONTENT_CONFLICT` (competing stored outcome) | authoritative stored state read back and reported (`retainedResult`) | no overwrite |
| `NOT_COMMITTED` | `NOT_CONFIRMED` | none |
| `UNKNOWN` (ambiguity/lookup failure) | `NOT_CONFIRMED` | none |
| Network gateway absent | `NOT_CONFIRMED` (fail closed) | no legacy `updateOutcome`/event |
| Reply uncorrelated, malformed, or absent | `NOT_CONFIRMED` before the port | none |
| Evidence timestamped before the durable dispatch | `NOT_CONFIRMED` before the port | none |
| Reply decision is `APPROVED`/`DECLINED` | issuer-result branch only; network port never called | result port |

`ISSUER_DECLINED` is never produced from network evidence, no issuer task or issuer decline is
fabricated, and the atomic branch performs no retry, resend, reversal, `updateOutcome` or legacy event
append. The port is invoked at most once per call.

The legacy network path (`updateOutcome(NETWORK_REJECTED)`, `appendEvent(NETWORK_REJECTION)`, replay
mapping `NETWORK_REJECTED → PROCESSOR_REJECTED`) is **not** used as a fallback inside the atomic
branch. It remains reachable only on the separate legacy path where no atomic gateway is injected
(the 4-argument constructor still used by `AwsRuntime` and the SQL harness). One test
(`legacyHarnessConstructorKeepsTheLegacyNetworkPath`) asserts that legacy path still behaves as
before, and one (`networkGatewayWithoutAtomicPredecessorsIsRejected`) asserts the guards.

### Actual attempts and exits

| Attempt | Exit | Recorded result |
| --- | --- | --- |
| quiet `compile` | 0 | empty streams |
| `NetworkResultServiceWiringTest` attempt 1 | **1** | **12 tests, 1 failure, 0 errors, 0 skipped** — `networkGatewayWithoutAtomicPredecessorsIsRejected:272` |
| `NetworkResultServiceWiringTest` attempt 2 | 0 | empty streams — **no count recorded, none asserted** |
| targeted regression set | 0 | container startup output only — **no `Tests run:`/BUILD line, no count asserted** |
| full suite, snapshot `5803cdf6` | 0 | **220 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS** |
| full suite, post-manifest re-run | **not recorded** | **INCOMPLETE** — truncated at 12:46:25 inside `DynamoResultGatewayIntegrationTest`; 2 classes passed (24 + 2 tests) |

### Initial failure — what actually happened

Attempt 1 failed on the assertion, not on production behaviour. The saved failure text is: expected
the exception message to contain `network-result gateway requires`, but the thrown message was
`a result gateway requires the atomic claim and dispatch-intent gateways`
(`AuthorizationService.java:178`). The guard order is intended: a result gateway without claim/intent
is rejected first. The test file was corrected at 12:43:32 (hash `05c3ecb3…` → `5803cdf6…`); the
production file was not touched after 12:42:20 and hashed identically in `hashes-pre-test.txt` and
`hashes-post-test.txt`. The corrected test is what attempt 2 compiled (test-class mtime 12:43:42) and
what the 220 run executed.

The exact text of the `05c3ecb3` file is **not preserved**; only its hash and the failure text are.
No attempt is made to reconstruct it.

### Which snapshot the 220 pass covers

`hashes-pre-test.txt` (12:43:00.95) is **32 seconds older than the test fix** (12:43:32.75), so it is
not the manifest of the run. The run is bound to `5803cdf6` by three saved artifacts: attempt 2
recompiled the test class at 12:43:42 from the 12:43:32 source; the 12:45:02 full-suite log reports
`Nothing to compile - all classes are up to date` for compile and test-compile and 12/12 green for
`NetworkResultServiceWiringTest`; and `hashes-post-test.txt` (12:45:56) is byte-identical to the
current tree with no `src/**` mtime after 12:43:32. The one-line pre/post hash diff is therefore the
mid-sequence test edit, **not** a mutation during the run. Residual uncertainty: no source hash is
embedded in the Maven log, so identity rests on those artifacts rather than on an in-run digest.
The incomplete post-manifest re-run started from the same `5803cdf6` set and is labelled INCOMPLETE —
it is neither a pass nor a failure and is not used for any claim.

### Precise wiring status

`NetworkResultGateway` is now consumed by `domain/AuthorizationService`; `DynamoNetworkResultGateway`
is still referenced by nothing outside its own file and the tests. `AwsRuntime` remains
`99c32249f1b577dc0645ffed7e2751aa0c1edcdd1b51a22b92ebb639f0f36c7b`, unchanged, and still calls the
4-argument constructor with `DynamoAuthorizationRepository`, `LambdaDownstream`, `props`,
`Clock.systemUTC()` — no claim, dispatch-intent, result or network gateway is passed. The service is
therefore atomic-capable but not atomic-active at runtime. No runtime activation, no IAM/table change,
no deployment.

### Exact remaining

1. ~~Service-level network-rejection branch~~ — done in this slice.
2. Late-evidence/conflict event variants and the timeout-boundary disposition (the current atomic
   branch returns `NOT_CONFIRMED` without a write and says "later slice" in code) — not started.
3. `CONTENT_CONFLICT` quarantine representation and service-level ambiguous-result policy — partially
   handled as read-back, quarantine not started.
4. `collectedAt` first-collection retention, principal/context authorization before dedupe, named
   OBS-20/21/22 tests — not started.
5. `AwsRuntime` activation (construct the atomic gateways and remove the legacy path) — not started
   and must not be bundled with 2–4.

### Next smallest slice

The timeout-boundary and late-evidence disposition on the atomic result path: decide and implement,
from the approved scenarios only, whether a late confirmed reply is persisted as retained evidence
(R20/A14) and what the boundary outcome is, keeping every uncovered shape at `NOT_CONFIRMED` with no
legacy write. Runtime stays inactive.

### Non-claims

G6 is not complete. This is local implementation plus SQLite/DynamoDB Local/Testcontainers testing —
not QA, Security review or human acceptance. AWS service behaviour (throttling, Streams, IAM,
encryption, TTL timing, concurrency) remains UNVERIFIED. The 220 pass is evidence for the
`5803cdf6` snapshot, not a standing proof for future edits. No commit, push, PR, merge, deploy, cloud
call, credential flow, deletion or approved-specification edit occurred; this finalization itself was
documentation-only.

## 23. P1b6a — pure deadline read disposition (2026-11-20) — IMPLEMENTED, TESTED, UNWIRED

Status: **IMPLEMENTED AND TESTED ON THIS SNAPSHOT — 9 targeted, 159 targeted regression, 229/229 full
suite, 0 F/E/S, exit 0. UNWIRED: no production caller. `AwsRuntime` unchanged and still legacy;
G6 COMPLETE → NO.** Evidence: `evidence/p1b6a-2026-11-20/` (`binding.txt`, `hashes-pre.txt`,
`hashes-post.txt`, `hashes-diff.txt`, `git-status-*.txt`, all `*.exit.txt`, all `*.stdout/stderr.log`,
`source-reference-scan.txt`, `new-files.sha256.txt`, `manifest.md`). This section is a
documentation-only finalization written after the saved runs; no test was re-run and no source edited.

### Binding and delivered files

Repository `/home/pazfernando/projects/acquiring-simulator`, branch `feat/wi-acq-auth-001`, HEAD
`cfc456f4f469d9558a78fda8ff9b770b00817315`, configured default `nan/deepseek-v4-flash`, no override.
New (untracked): `domain/DeadlineDisposition.java` `abd2df5a…` and
`DeadlineDispositionTest.java` `ec22bbac…`. Both were on disk before `hashes-pre.txt`
(12:53:38.99), and `hashes-post.txt` (12:55:36.53) is byte-identical, `hashes-diff.txt` empty;
`git status --porcelain` is byte-identical before/after. The runs are therefore bound to those two
hashes; no in-run digest is embedded in the Maven logs. `AuthorizationService` `ac2ceae…` (P1b5c
hash) and `AwsRuntime` `99c32249…` were not edited.

### Actual attempts and exits

| Command | Exit | Result |
| --- | --- | --- |
| `DeadlineDispositionTest` | 0 | 9 tests, 0 F/E/S; `BUILD SUCCESS` 12:53:49 |
| targeted regression | 0 | 159 tests, 0 F/E/S; `BUILD SUCCESS` 12:54:34 |
| `mvn -o test` | 0 | **229 tests, 0 F/E/S, 29 classes; `BUILD SUCCESS` 12:55:32** |
| quiet `compile` | 0 | empty streams, stamped 12:55:45 — **after** the suite, so a post-run check only |

### Sourced rule: read disposition, not a committed transition

The approved contract treats the deadline as a response rule, not a state transition:
`channel-contract.md:17` (timeout after possible dispatch ⇒ unknown absent prior confirmed evidence;
no retries/harvesting), `:32` (readable `POSSIBLE_SEND` stays readable while active), `:33` (at/past
deadline: `NOT_CONFIRMED`, **original** IDs/version, null `businessVersion`, `COMPLETED` "not payment
finality"), `observation-contract.md:31` (conservative `NOT_CONFIRMED`, no resend/rollback/reversal),
`:76` (repeated evidence/timers duplicate no transition), `acceptance-scenarios.md:19,20,23`
(OBS-06/07/10), `requirements.md:43` (R07 no regression), vectors S27 and A16. The helper encodes that
as constant `false` write predicates. The persisted representation it reads is `AWAITING` +
`dispatchedAt`, because `AuthorizationState` has no `POSSIBLE_SEND` member.

The **legacy** persisted path still exists: `AuthorizationService.evaluateDeadline` writes
`NOT_CONFIRMED` and a legacy `"TIMEOUT"` evidence row when no result gateway is injected
(`AuthorizationService.java:281-289`), while the atomic branch is read-only
(`:271-275`). `DynamoAuthorizationRepository.mutate` bumps only the storage CAS `version` and
copy-forwards `committedJournalVersion` (`:96-111`), so the approved committed version is not
advanced. `AwsRuntime` still builds the 4-argument service (`AwsRuntime.java:38`), so the legacy write
remains the runtime shape.

### Which code references the helper

None. A fresh scan finds `DeadlineDisposition` only in its own file and its test
(`source-reference-scan.txt`); `AuthorizationService` and `AwsRuntime` never reference it. The helper
is unit-tested but **unused**, and the service is **not wired** to it.

### Exact unresolved portion (no invented rule)

1. No approved clause says whether the first deadline evaluation may commit `NOT_CONFIRMED`; O8's
   "repeated … timers duplicate no new transition" can be read as allowing a first timer transition.
   "Always read-only" is inferred from C3/O3, not explicitly mandated.
2. OBS-10 permits a "timeout task observation", but `EventTask` has no timeout task and
   `EventReason` has no timeout reason/task pair; no approved text assigns task, reason, criticality
   or sequence, so a canonical timeout event would require invented vocabulary.
3. Disposition of already-persisted legacy `NOT_CONFIRMED` rows (retain as evidence, normalize, or
   retire) is not defined; the helper reads them without a second transition.
4. A legacy-bumped storage CAS has no approved mapping to the channel `businessVersion`, which the
   contract requires to be a positive committed integer; the helper does not invent one.

### Next smallest late-evidence slice

On the atomic result path, the named OBS-08 late verified-evidence commitment: a correlated late
approval/decline for an operation already past the boundary is persisted once at the confirmed state
with its evidence time and a distinct late variant, never as a resend and never replacing a known
result (R07; A16; `ResultCriticalComposer` already fixes committed version 3). Before persisting a
timeout shape, the unresolved items above need an approved-text decision; runtime activation stays
out of that slice, and the pure helper should be wired to the atomic read branch only if that
decision says yes.

### Non-claims

G6 is not complete. This is local implementation plus unit/full-suite testing on one snapshot — not
QA, Security review or human acceptance. DynamoDB service behaviour remains UNVERIFIED. The 229/229
pass is not a standing proof for later edits. No commit, push, PR, merge, deploy, cloud call, apply,
credential flow, deletion or approved-specification edit occurred.

## 24. P1b6b — late verified-evidence composer (2026-11-20) — IMPLEMENTED, PARTIALLY TESTED, UNWIRED

### Binding and file-set correspondence

Repository `/home/pazfernando/projects/acquiring-simulator`, branch `feat/wi-acq-auth-001`, HEAD
`cfc456f4f469d9558a78fda8ff9b770b00817315`, configured default `nan/deepseek-v4-flash`, no override.
Evidence: `evidence/p1b6b-2026-11-20/` (`binding.txt`, `manifest.md`, `hashes-pre.txt` and the
saved logs). This section is a documentation-only finalization written after the saved run; no test
was re-run and no source edited.

New files: `adapter/aws/LateEvidenceCriticalComposer.java` `56920642…` and
`aws/DynamoLateEvidenceTransitionIntegrationTest.java` `03d23571…`. A finalization-time `find src`
plus `pom.xml` hash set has 123 entries and is byte-identical to `hashes-pre.txt` (empty diff); the
`git status --porcelain` captured immediately after that recomputation is identical to
`git-status-before.txt`. The tree therefore did not change between run start and finalization. That
is a tree-level check, not an in-run Maven digest, and the aborted script never wrote
`hashes-post.txt`/`git-status-after.txt`.

### Actual attempts and exits

| Step | Exit | Result |
| --- | --- | --- |
| quiet `compile` | 0 | empty streams |
| `DynamoLateEvidenceTransitionIntegrationTest` | 0 | **11 tests, 0 F/E/S**; `BUILD SUCCESS` 13:05:40 |
| literal targeted selector | 0 | **94 tests, 0 F/E/S, 14 classes**; `BUILD SUCCESS` 13:05:58 |
| `mvn -o test` | **no exit file** | **INCOMPLETE**: 9 class summaries / 78 tests with 0 F/E, then stopped after `Running …AwsTransportTest`; no `Results:`, no `BUILD SUCCESS` |

The targeted selector named 8 patterns; `DynamoClaimTransitionIntegrationTest` and
`DynamoDispatchIntentTransitionIntegrationTest` match no class in this tree. The 14 classes that
actually ran are listed in `targeted-test.stdout.log`, so this is narrower than §23's 159-test
sweep. The full suite is **not** a pass for this snapshot: 78 completed tests cannot be reported as
229/229 or any total, and the earlier 229/229 belongs to the P1b6a snapshot.

### Sourced late-evidence rules

- `observation-contract.md:29` — P5/C4 verified evidence becomes processor-known only when
  evidence + business version + critical consultable event commit atomically; **late evidence uses
  the same internal admission**, with no public late endpoint or automatic financial recovery;
  event/task/span alone cannot admit a payment result.
- `observation-contract.md:76` — slot accounting joins the same transaction; an already reserved
  operation retains outcome/late-evidence headroom; repeated evidence/timers duplicate no new
  transition.
- `channel-contract.md:42` — late delivery is an authorized internal P5 invocation with the bounded
  envelope and 1–64-character `deliveryReceiptId`; verify bounds, sender/role/key/signatures and
  both hashes, then P6 binding, then the atomic P5 commit, then the channel result, then async
  notification; no public endpoint and no issuer-table fetch.
- `channel-contract.md:43` — failed/missing attestation is rejected before business admission; the
  wire fixtures' signature material is **noncryptographic interface shape**, while a deployed
  carrier must verify real signatures; no `VALID` flag is accepted.
- `iso8583-profile.md:52,54` — evidence time may be later than send; late responses retain the
  original DE7/sent and use `at` for reply time; authorized late evidence uses the persisted
  request, not the first-dispatch freshness check.
- `iso8583-profile.md:81,93-94` — verified late approval/decline appends to the same journal and
  reconciles UNKNOWN; exact repeats are idempotent; contradictory signed evidence is quarantined
  and flagged for separately authorized resolution, never silently replacing a confirmation; late
  handling is the same authenticated internal handler, and signed attestation without processor
  admission is issuer truth, not known processor state.
- `acceptance-scenarios.md:21,23` — OBS-08 reconciles the same operation with the earlier
  uncertainty and as-of timestamps, no second purchase, and appends after a timeout display;
  OBS-10 applies identical late evidence once, does not regress confirmed state, and permits a
  timeout task observation without replacing a known result.
- `acceptance-scenarios.md:8` — use a separately authorized existing test seam; do not add a public
  late-evidence endpoint to run tests. `requirements.md:43` — R07 requires verified correlated late
  evidence, original references, idempotency and no regression to uncertainty.

### Evidence time versus delivery time

`classify(evidenceAt, deliveredAt, deadline)` decides from the **evidence time** only:

| Condition | `Timing` | Disposition |
| --- | --- | --- |
| evidence at/before deadline, delivered by deadline | `ON_TIME` | normal result path |
| evidence at/before deadline, delivered after | `DELIVERY_DELAY` | refused by this composer as "not late evidence" |
| evidence strictly after deadline | `LATE` | eligible for this composer |
| evidence exactly at deadline | `ON_TIME` | not late |

Delivery before evidence availability is rejected. The confirmed `outcomeDeterminedAt`, the
canonical `occurredAt` and the `committedAt` all use the evidence time; `evidenceDeliveredAt` and
`evidenceDeadline` are additive receipt metadata outside the canonical event hash. That is why an
identical redelivery hashes the same and becomes `EXACT_REPLAY` instead of a second outcome.

### What the composer actually writes

`LateEvidenceCriticalComposer` is a `CriticalWriteComposer`, not a standalone writer. Its
`compose(...)` returns the conditional journal `Put` plus an optional critical-slot `Update`; the
existing `DynamoEventStore` adds the event row, HEAD/dedupe metadata and sequence in the **same**
`TransactWriteItems` call. The journal `Put` requires `version = 1 AND committedJournalVersion = 1`
(the dispatch-intent pair) and writes `version = 2`, committed journal version 3, the confirmed
state, `DecisionSource.ISSUER`, the original dispatch correlation/time and the receipt metadata
(`evidenceReceiptId`, `evidenceAvailableAt`, `evidenceDeliveredAt`, `evidenceDeadline`,
`lateEvidence=true`). A failed condition commits nothing. The event reuses the approved normal
vocabulary (`BUSINESS_PERSIST`/`SUCCEEDED`/`VALID`, the `APPROVED-<id>`/`DECLINED-<id>` identity,
journal version 3); no late-specific task, reason, status or state is invented.

Admission rejects a confirmed/network-rejected/legacy-`NOT_CONFIRMED` record, a determined outcome,
a non-`NONE` decision source, a missing dispatch correlation, a non-APPROVED/DECLINED or
uncorrelated reply, evidence before dispatch, an empty/oversized receipt id, a deadline before
dispatch, and delivery before evidence availability.

### Contradiction and replay limits

- Contradiction is **refuse-and-preserve**, not durable quarantine. A late decline after a late
  approval targets a different stable event id, so the first journal condition (`version = 1`) and
  dedupe guard abort the transaction (`TransactionCanceledException` in the test); the approved
  result and its evidence remain. The composer does **not** persist a quarantine row or flag, while
  the cited approved text requires contradictory evidence to be quarantined and flagged for
  separately authorized resolution. That storage/decision shape remains open.
- Exact replay is proven only with the **in-memory pre-result intent** reused for both appends:
  `EXACT_REPLAY`, one result event, one HEAD advance, one slot, one committed version. The
  canonical identity excludes `deliveryReceiptId` and delivery time, so an identical decision at
  the same evidence time with a *different* receipt id is not distinguishable from an exact replay
  and is not flagged. Constructing the composer from a freshly read confirmed record throws
  "already retained" before dedupe; nothing in this slice performs the dedupe-first read that a
  real redelivery needs.

### Internal test seam — not cryptographic verification

The test's `reply(...)` helper constructs `new DownstreamReply(correlation, decision,
availableAt)` directly and hands it to the composer (and through `store.append(...)`). It bypasses
every C4 step: no envelope bounds, sender/relay role, key lookup, signature, hash, P6 ISO check or
attestation resolution is exercised. The test Javadoc and `manifest.md` say this explicitly. A green
test therefore proves local atomic composition and precedence mechanics only; it is **not**
cryptographic verification and says nothing about whether a real `DownstreamReply` was
authenticated. No `VALID` flag is accepted by the composer.

### Actual service and runtime references

- `AuthorizationService.submitEvidence` (`:214-219`) is the existing internal late-evidence entry.
  When `resultGateway != null` it throws `IllegalStateException("late evidence admission is not
  implemented on the atomic result path")` and writes nothing; it does **not** call this composer.
  When `resultGateway == null` it takes the legacy path that can still `updateOutcome` and append
  legacy events.
- `AuthorizationService.evaluateAtomicResult` (`:471-477`) documents that no-response, malformed,
  uncorrelated and late replies have no atomic composer in this slice and fail closed.
- `AwsRuntime.processor()` (`:38`) still constructs the 4-argument `AuthorizationService`
  (repository, downstream, properties, clock), i.e. no `ResultGateway`; the runtime therefore takes
  the legacy shape, not the atomic one.
- A fresh `grep` finds `LateEvidenceCriticalComposer` only in its own file and its test; production
  code never references it. The composer is implemented and narrowly tested but **unwired**, and
  the service branch it would need is absent.

### Next smallest gateway or missing test slice

The smallest next slice is an internal late-evidence **admission gateway**, still behind the
authorized test seam and with no HTTP/auth/cloud activation: strong-read the current record; if it
is already confirmed and the candidate has the same stable identity, return the stored result /
`EXACT_REPLAY` without composing; if it is the dispatch-intent shape, compose once; if it is
confirmed with a contradictory identity or a legacy `NOT_CONFIRMED` row, return a refusal that
preserves state and writes nothing. This closes the fresh-read replay gap without inventing storage.
Durable quarantine/flagging, the `NOT_CONFIRMED` legacy disposition, the timeout-event vocabulary,
the `AuthorizationService` branch and `AwsRuntime` activation each need an approved-text or
architecture decision and are deliberately **not** bundled into that slice. A missing test slice
that does not need new vocabulary is the OBS-08 "late evidence after the UI showed timeout" case,
but only if it targets the atomic `AWAITING` shape rather than the unresolved legacy row.

### Non-claims

P1b6b is local, DynamoDB Local-only implementation and partial testing on one snapshot — not QA,
Security review or human acceptance. The full suite did not finish, so no suite-success total is
claimed for these bytes. AWS service behaviour, C4 authentication, the service branch and runtime
activation remain UNVERIFIED. `AwsRuntime` is inactive and G6 remains open. No commit, push, PR,
merge, deploy, cloud call, apply, credential flow, deletion or approved-specification edit occurred.

## 25. P1b6c — late-evidence admission gateway (2026-11-20) — PARTIAL, COMPILES, UNTESTED, UNWIRED

Recovery-only inspection of the slice left by an earlier abort. Nothing was recreated or
researched. Evidence: `evidence/p1b6c-2026-11-20/` (`binding.txt`, `git-status-before/after.txt`,
`hashes-pre/post.txt`, `hashes.txt`, `new-files.sha256.txt`, `test-compile.*`, `pending-command.txt`,
`manifest.md`). Binding unchanged: `/home/pazfernando/projects/acquiring-simulator`, branch
`feat/wi-acq-auth-001`, HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`, configured default
`nan/deepseek-v4-flash`, no override.

### Files present

`adapter/aws/LateEvidenceAdmissionGateway.java` `29e2d366…` (new),
`domain/event/LateEvidenceReceipt.java` `f7cd1cce…` (new), and
`adapter/aws/DynamoAuthorizationRepository.java` `710c34c9…` (tracked, modified with
`readVersionEvidence`/`readLateEvidenceReceipt`). All composer constants and event types the
gateway references exist. No source edit was required: `mvn -o -q test-compile` exited **0** with
both streams empty. `hashes-pre`/`hashes-post` (120 entries) and `git status` are byte-identical
before/after; diffs are empty.

### What is missing

**No gateway test class exists** (`find src/test -iname '*LateEvidenceAdmission*'` is empty). The
targeted run therefore did not happen and the gateway has **no test coverage**: authorization order,
fresh-read replay, contradiction preservation, ambiguous-commit single-lookup recovery and
unknown-is-not-no-send are all unverified. Writing the test was out of scope for this recovery; the
exact pending command and expected path are saved in `pending-command.txt`:
`mvn -o -Dtest=DynamoLateEvidenceAdmissionGatewayIntegrationTest test`.

No full suite or regression sweep was run in this recovery. The P1b6b incomplete full suite and
P1b6a's 229/229 are not re-claimed here.

### Non-claims

This is a local compile check of a partial gateway slice — not QA, Security review or human
acceptance. The `DownstreamReply` input remains an explicit non-cryptographic internal test seam; no
C4 envelope/signature/P6 verification is exercised. `AwsRuntime` is unchanged and inactive; the
gateway is not wired into `AuthorizationService` or runtime. G6 remains open. No commit, push, PR,
merge, deploy, cloud call, apply, credential flow, deletion or approved-specification edit occurred.

## 26. P1b6c test authoring + targeted execution (2026-11-20) — 5/5 GREEN ON ONE CLASS, STILL UNWIRED

Bounded test authoring against the existing `LateEvidenceAdmissionGateway`, `LateEvidenceReceipt`
and `DynamoAuthorizationRepository` accessors. New file only:
`src/test/java/com/acquiring/simulator/aws/DynamoLateEvidenceAdmissionGatewayIntegrationTest.java`
sha256 `ee2e5e29a1aacbbca51cca954f132aff6a305c9710a4b899eff50f28c45f2b15`. Need: P1b6c had no
gateway test, so admission order, replay, contradiction, ambiguity and unknown were unverified.

Command (absolute JDK 25.0.5 / Maven, offline):
`mvn -o -Dtest=DynamoLateEvidenceAdmissionGatewayIntegrationTest test` → exit **0**, stdout captured,
stderr empty, `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`, BUILD SUCCESS.

Covered: (1) commit then a fresh gateway instance replays with no second sequence/receipt/slot;
(2) unauthorized (MALFORMED) and uncorrelated candidates are REFUSED with **zero** `EventStore.find`
calls, with an authorized control showing exactly one lookup → REPLAY; (3) contradicting late
evidence is CONTENT_CONFLICT and preserves the original state, receipt, 2/3 version, 3 events and
3 slots; (4) a real append followed by a thrown `SdkClientException` is RECOVERED by one lookup
(no resend), and an unreadable journal is UNKNOWN with a detail and no change — never "no result";
(5) evidence available by the deadline but delivered later is DELIVERY_DELAY →
NORMAL_RESULT_ADMISSION with nothing written.

Preservation: `hashes-pre`/`hashes-post` (120 Java files) and `git status` are byte-identical
before/after. Binding unchanged: HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`, branch
`feat/wi-acq-auth-001`, configured default model, no override. Evidence:
`evidence/p1b6c-test-20261120T182127Z/` (`manifest.md`, hashes, git status, run logs, surefire).

Trust seam unchanged: `DownstreamReply` is the explicit internal non-cryptographic test seam. A
green run is not a C4 authentication claim and touches no public endpoint, service branch or
runtime. The gateway remains unwired; `AwsRuntime` inactive and G6 open. No product defect was
found in the exercised paths, so no source fix was made. No full suite/regression was run. No
commit, push, PR, merge, deploy, cloud call, apply, credential flow, deletion or approved-spec edit.

Next smallest slice: the two implemented preserved/unknown edges — confirmed record with absent late
receipt (CONTENT_CONFLICT) and confirmed record with missing result event (UNKNOWN). Service/runtime
wiring and the legacy `NOT_CONFIRMED` disposition still need an approved decision.

## 27. P1b6c remaining gateway edges + one full suite (2026-11-20) — 247/247 GREEN, STILL UNWIRED

Closed the two edges named at the end of §26 against the existing gateway. File modified:
`src/test/java/com/acquiring/simulator/aws/DynamoLateEvidenceAdmissionGatewayIntegrationTest.java`
sha256 `d9efe45e…ef465a3`; existing five tests preserved, class now **7/7**.

1. `confirmedRecordWithoutALateReceiptIsAConflictThatPreservesTheOutcome` — outcome confirmed via
   the normal `ResultCriticalComposer` admission (no late receipt, asserted empty), then late
   admission → `CONTENT_CONFLICT` with `APPROVED`, issuer provenance, `outcomeDeterminedAt`,
   version `2/3`, 3 events, HEAD 3, 3 slots preserved; no receipt invented. No deletion used.
2. `confirmedLateRecordWithMissingRetainedResultEventIsUnknownAndPreserved` — after one real late
   commit, only the retained-result dedupe identity row is removed in the isolated per-test
   ephemeral event table (test-seam manipulation), `store.find` is empty, admission → `UNKNOWN`
   ("confirmed record has no retained result event") with record, receipt, version `2/3`, event
   row, HEAD 3 and 3 slots preserved. No pre-existing environment touched.

Command on the frozen tree (absolute JDK 25.0.5 / Maven 3.10.1, offline):
`mvn -o test` → exit **0**, BUILD SUCCESS, `Tests run: 247, Failures: 0, Errors: 0, Skipped: 0`
(class 7/7, 4.213 s). Full stdout/stderr/exit captured before analysis in evidence.
`hashes-pre`/`hashes-post` over `src/test`, `src/main/resources`, `pom.xml` (41 files) and
`git-status-before`/`after` are byte-identical (diffs exit 0). Binding unchanged: HEAD
`cfc456f4f469d9558a78fda8ff9b770b00817315`, branch `feat/wi-acq-auth-001`, configured default
model, no override. Evidence: `evidence/p1b6c-edge-20261120T182454Z/`.

No product defect was found in the exercised paths, so no source fix was made. The
`DownstreamReply` seam is still non-cryptographic; the gateway remains unwired, `AwsRuntime`
inactive, and full G6 open. No commit, push, PR, merge, deploy, cloud/apply call, credential flow,
destructive data change or approved-specification edit occurred. Next concrete slice: an internal
test-only service-level branch behind the gateway (no public endpoint, no C4 claim); the legacy
`NOT_CONFIRMED` disposition stays UNKNOWN unless the approved text states the rule.

## 28. P1b7a — accepted 3/3 integrity evidence, located under AUTH-001 (2026-11-20)

Status: **ACCEPTED LOCAL ONLY.** The coordinator read the saved result and accepted the three
targeted integrity tests as local evidence. This section links that evidence; it does not move,
delete or duplicate it.

### Where the evidence actually is

The P1b7a run was written under the AUTH work item, not the commissioned OBS directory. Preserved
in place:

| Path | Run | Content / SHA-256 |
| --- | --- | --- |
| `docs/ai-sdlc/work-items/WI-ACQ-AUTH-001/evidence/p1b7a-20261120T134318Z/` | 2026-11-20T13:43:18Z | `RESULTS.md` `88bace57d9d9d354c97f208599952357ea5f5ae7ac59ceb1eb5d25d6d511fe92`; `PROGRESS.md` `f0aeae4ef744bfb43d0a30c4ce48d45a11ea1f90b9ddf892c3e04787b1ebb34d`; `EVIDENCE-MANIFEST.md` `7758a5883f44dccc7b43e2551bacaa55f6446554867da4313762fc36b2567d0d` |
| `docs/ai-sdlc/work-items/WI-ACQ-AUTH-001/evidence/p1b7a-20261120T184323Z/` | 2026-11-20T18:43:23Z | `BINDING.txt` `d1e1e75a943467236b8a5dbbe3ba03f5c14c157f5191b77d56025b5a1bfdf7f7`; `test-stdout.txt` `4a7da90a9aa3c7f44b987df79fc0b976009150689a8d386555ca682672668e03`; `test-exit.txt` `9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa` |

Both runs report the same accepted result: `compile` 0, `test-compile` 0, targeted
`DynamoEventStoreIntegrationTest` **3/3, 0 failures/errors/skips**, exit 0, on HEAD
`cfc456f4f469d9558a78fda8ff9b770b00817315`. The three tests are the conflict-marker write, the
idempotent repeat with no extra sequence/slot, and authorization-before-dedupe.

`docs/ai-sdlc/work-items/WI-ACQ-OBS-001/evidence/p1b7a-20261120T183930Z/` also exists from a
separate, partial OBS run: `compile` exit 0, `eventstore-test` 12/12 exit 0, and an **incomplete**
full-suite log with no exit file. It is preserved as-is, is not used as the accepted P1b7a result and
was not edited.

### Correction carried forward

- Routine remaining **local** verification (compile, meaningful targeted classes, hash capture) is
  already covered by the owner's local implementation authority; it does not need a separate
  authorization prompt. Push, PR, merge, deploy, cloud/apply, credentials, destructive changes to
  existing data, approved-specification edits and capacity defaults remain unauthorized.
- The AUTH P1b7a run used `-DfailIfNoSpecifiedTests=false` and an mtime `touch` to force a recompile.
  Those were acceptable for that recovery run but are **not** repeated: P1b7b (§29) hashes every
  main/test/resource file plus `pom.xml` before and after, uses class-scoped selectors that fail on a
  no-match, and does not touch mtime.

## 29. P1b7b — first-authorized collectedAt persistence/replay metadata (2026-11-20) — IMPLEMENTED, TESTED, UNWIRED

Status: **IMPLEMENTED AND VERIFIED ON THIS SNAPSHOT — 16 targeted store tests, 11 targeted consumer
tests and a 269/269 full suite, 0 F/E/S, exit 0. No runtime wiring; G6 remains open.**
Evidence: `evidence/p1b7b-20261120T184718Z/` (`BINDING.txt`, `MANIFEST.md`, `hashes-before.txt`,
`hashes-after-edit.txt`, `hashes-after-run.txt`, both hash diffs, `git-status-*`, all four
`*.stdout.log`/`*.stderr.log`/`*.exit` sets).

### Binding

Repository `/home/pazfernando/projects/acquiring-simulator`, branch `feat/wi-acq-auth-001`, HEAD
`cfc456f4f469d9558a78fda8ff9b770b00817315`, origin `github.com/pazfernando/acquiring-simulator.git`,
configured default `nan/deepseek-v4-flash`, no override. JDK 25.0.5 and Maven 3.10.1 used by absolute
path with `JAVA_HOME` pinned; Maven offline. Runtime inactive. No Git write, publication, cloud call,
apply or credential flow.

### Sourced rule

`observation-contract.md:17` puts `collectedAt` in the source wrapper, requires
`collectedAt == committedAt` for critical events, and says the **best-effort first authorized
collection is retained across redelivery**. `:21` requires dedupe metadata (hash, original
context/key/sequence, **no second payload**) retained while business links remain. `:77` (O8,
PROPOSED) bases seven-day history on the **original** `collectedAt`, not a callback/replay/read time.
This slice implements only that metadata retention; expiry/ranges/query API are P3.

### Delivered change (additive; no tracked file changed)

| File | SHA-256 after edit |
| --- | --- |
| `domain/event/StoredEvent.java` | `420d896335618f4bbee2864f4530970c074fe419135d5981b164e54dfdee4309` |
| `adapter/aws/DynamoEventStore.java` | `7fa3a17142fbc79aeacc94efc0bdc165260ad7f2757f67b843b3f71825687c7d` |
| `test/.../DynamoEventStoreIntegrationTest.java` | `3aa8e3ea7386534e59e3b3b4cb70b5b3d6b1a9fc0da8366dad6d240df3ba26a1` |

- `StoredEvent` gains one nullable `collectedAt` component. `null` means the metadata predates the
  field — explicit **unknown**, never "now".
- `DynamoEventStore` writes `collectedAt` on the dedupe row at first append and returns it from the
  strongly consistent `find`. No payload copy, no new attribute on the event or HEAD beyond what
  already existed, no sequence/context/integrity change.
- Replay and conflict paths return before writing, so the first collection time cannot be reset by a
  later delivery, a fresh store instance or a read. Authorization-before-dedupe is unchanged.

### Commands and actual results

| Command (absolute toolchain, offline, no `failIfNoSpecifiedTests`) | Exit | Result |
| --- | --- | --- |
| `mvn -o -q -DskipTests compile` | 0 | empty streams |
| `mvn -o -Dtest=DynamoEventStoreIntegrationTest test` | 0 | **16 tests, 0 F/E/S**, BUILD SUCCESS |
| `mvn -o -Dtest='DynamoAtomicClaimRecoveryIntegrationTest,DynamoLateEvidenceAdmissionGatewayIntegrationTest' test` | 0 | **11 tests, 0 F/E/S**, BUILD SUCCESS |
| `mvn -o test` | 0 | **269 tests, 0 F/E/S**, BUILD SUCCESS |

The full suite is justified because `StoredEvent` is shared by the claim, dispatch-intent, result,
network and late-evidence gateways. `hashes-after-edit` → `hashes-after-run` diff is empty and the
`git status` diff is empty (the three edited files are untracked from earlier slices; no tracked path
changed). The only full-suite stderr is JDK/Mockito/SQLite agent warnings, not an error.

### Behaviour proven (DynamoDB Local only) — 4 new tests

1. A best-effort redelivery with a later delivery time returns `EXACT_REPLAY`, keeps sequence 1, and
   the strong read/event row/dedupe row/HEAD all report the original `12:00:01Z`.
2. A critical event persists `collectedAt == committedAt`; a later redelivery still reads the
   original pair and only one event row exists.
3. A fresh `DynamoEventStore` (clock two days later) replays with the persisted first collection time;
   HEAD is not reset.
4. A legacy dedupe row with the attribute removed reads `null` before and after a replay; the event
   row keeps its original value and no backfill is invented.

### Exact remaining P1 / G6 checklist

| Item | State |
| --- | --- |
| G5 canonical contract, sanitizer, wrapper, manifest, dedupe identity | COMPLETE locally (P1a) |
| G7 EVENT table keys, `E#`/HEAD, sequence CAS, dedupe, integrity marker | COMPLETE locally (P1b1 + P1b7a evidence) |
| G5 `collectedAt` first-collection retention across redelivery/restart/read | **COMPLETE in this slice** (P1b7b) |
| G6 atomic claim, dispatch intent, verified result, network rejection, late evidence | Implemented as composers/gateways and service branches; each locally tested |
| G6 `AwsRuntime` activation (construct atomic gateways, retire legacy claim/send path) | **NOT STARTED** — runtime still builds the 4-arg legacy service |
| `CONTENT_CONFLICT` durable quarantine beyond the integrity marker; durable late-evidence quarantine/flag | PARTIAL — conflict marker exists; late contradiction is refuse-and-preserve |
| Legacy `NOT_CONFIRMED` row disposition and timeout-event vocabulary | Open — needs approved-text decision |
| Principal/context authorization tied to business principal before dedupe | EVENT-partition context check only |
| Named OBS-20/21/22 core tests | Not named; covered partially by transition/gateway tests |
| Expiry worker, ranges, query API, cursor (P3); Streams/WS/notifier (P4) | Not started by design |

### Next smallest slice

Keep the runtime inactive and take the next bounded gap that needs no invented vocabulary: wire the
already-implemented atomic claim/dispatch/result/network/late-evidence gateways into a single
coherent `AuthorizationService` construction path used by `AwsRuntime`, but only after separate
verification, or first close the remaining approved-text decision on the timeout/legacy
`NOT_CONFIRMED` disposition. Do not bundle runtime activation with new payment semantics.

### Non-claims

Local DynamoDB Local implementation and tests only — not QA, Security review or human acceptance. AWS
throttling, Streams, IAM, encryption and TTL timing remain UNVERIFIED. No capacity default, no
approved-specification edit, no expiry/query work, no commit, push, PR, merge, deploy, cloud/apply
call, credential flow, deletion or destructive change to existing data occurred.


## 30. P1 closure assessment — OBS-20/21/22 mapping (2026-11-20) — ASSESSMENT FIRST, TESTS PENDING

Status: **ASSESSMENT SAVED BEFORE AUTHORING. Binding unchanged; runtime inactive; no P1 phase marked
complete.** Evidence directory: `evidence/p1close-20261120T185404Z/` (`MAPPING.md`, `BINDING.txt`,
`hashes-before.txt` 132 entries, `git-status-before.txt` 83 entries).

### Already final from the prior slice (acknowledged, not recreated)

- P1b7b collection metadata: 16 store + 11 consumer targeted tests and **269/269** full suite, exit 0,
  with empty after-edit and after-run hash diffs (§29). Not rerun: the instruction is not to re-run a
  full suite on unchanged bytes.
- AUTH P1b7a/AUTH evidence link integrity re-verified this slice: all six recorded SHA-256 values
  recompute identically (`RESULTS.md 88bace57…`, `PROGRESS.md f0aeae4e…`,
  `EVIDENCE-MANIFEST.md 7758a588…`, `BINDING.txt d1e1e75a…`, `test-stdout.txt 4a7da90a…`,
  `test-exit.txt 9a271f2a…`). §28 needed no edit.

### Scenario mapping outcome (full table in `MAPPING.md`)

| Scenario | Verdict | Basis |
| --- | --- | --- |
| OBS-20 one payment + critical events atomically, APPROVED without awaiting callbacks, no duplicate payload | **NEW TEST WARRANTED** — no end-to-end run of the three real gateways through the service exists | Criterion asserted directly, not a rename |
| OBS-21 notification/ADOT failure → observation-only retry, no payment retry/rollback | **NOT EXPRESSIBLE NOW** — no notifier/ADOT component exists; simulating one would be feature expansion | Stays open for P4/P6; existing no-resend/retention tests cited |
| OBS-22 ambiguity after possible send → NOT_CONFIRMED, no resend | **ALREADY COVERED** — cite `DynamoResultGatewayIntegrationTest.ambiguousBeforeCommitStaysUnknownWithNoPartialResult` / `.strongReadFailureStaysUnknownAndIsNeverNoResult` and `ResultServiceWiringTest.unknownResultFabricatesNoFinancialOutcomeAndNoLegacyWrite` / `.notCommittedResultFabricatesNoFinancialOutcome` | No duplicate added |
| OBS-22 storage failure **before** send prevents dispatch | **NEW TEST WARRANTED** — service-level proof exists only with gateway doubles; adapter proof never runs through the service | Real transaction abort through the real adapters |

`UNAVAILABLE` as a response disposition is a C3 envelope concern (P2/G3) and is not invented here;
the service's conservative `NOT_CONFIRMED`/UNKNOWN shape is preserved.

### Principal-authorization boundary (no over-claim)

The existing principal authorization is a **context-only test seam**. It is not P5 IAM/JWT
authentication and not P2b C4 cryptographic carriage. This slice neither implements nor claims either.

### Executed: two acceptance tests, 2/2 green on one class (appended after the run)

New file only:
`src/test/java/com/acquiring/simulator/aws/ObsAcceptanceAtomicChainIntegrationTest.java`
SHA-256 `48be479e627f1827e2d2fa5d3013ac03b5e697dc3fe321f2b2c7117882eb5269`. No production file was
edited. `hashes-before.txt` → `hashes-after-run.txt` differs by exactly that one added line;
`git-status-before.txt` → `git-status-after.txt` by exactly that one untracked path.

| Attempt | Command (absolute JDK 25.0.5 / Maven 3.10.1, offline) | Exit | Result |
| --- | --- | --- | --- |
| compile | `mvn -o -q -DskipTests compile` | 0 | empty stdout/stderr |
| 1 | `mvn -o -Dtest=ObsAcceptanceAtomicChainIntegrationTest test` | 1 | **testCompile only** — wrong constructor arity in the new test (passed `DynamoResultGateway` where `IdentifierSource` belongs). Log preserved; no production change. |
| 2 | same selector after the test-only fix | 0 | **2 tests, 0 failures, 0 errors, 0 skipped, 5.943 s, BUILD SUCCESS**; stderr empty |

Test 1 (`obs20…`) drives the real `DynamoClaimGateway` → `DynamoDispatchIntentGateway` →
`DynamoResultGateway` through the 8-argument atomic service: `APPROVED` returned by the invocation
itself, one downstream call, no legacy claim/`markDispatched`, one `APPROVED`/`ISSUER` business
record, three critical events `E#000000000001..3` with distinct hashes, HEAD 3, slots 3/8, three
dedupe rows with no payload attribute, no journal-table evidence event, and a repeat observation read
that adds nothing.

Test 2 (`obs22…`) uses the explicit test-only budget `(1,1)` so the real `POSSIBLE_SEND` transaction
cannot commit after the claim consumes the slot: `NOT_CONFIRMED`, zero downstream calls, only the
claim event (sequence 1, HEAD 1, slots 1/1), record still `AWAITING` with null
`dispatchedAt`/`dispatchCorrelationId`/`outcomeDeterminedAt`, and no resend on a repeat read.

### Coverage limits and next-phase handoff

- The **269-test suite was not re-run**; it remains evidence for the pre-existing tree and does
  **not** include these two tests. No full-suite total is claimed for this tree.
- OBS-20's async backends (notifier/WS/query/ADOT) are structurally absent in this phase: the test
  proves the payment path has no dependency on them, **not** recovery from a failing notifier. That
  remainder and all of OBS-21 stay open for P4/P6.
- OBS-22 ambiguity/UNKNOWN/no-resend was already covered by
  `DynamoResultGatewayIntegrationTest.ambiguousBeforeCommitStaysUnknownWithNoPartialResult` /
  `.strongReadFailureStaysUnknownAndIsNeverNoResult` and
  `ResultServiceWiringTest.unknownResultFabricatesNoFinancialOutcomeAndNoLegacyWrite` /
  `.notCommittedResultFabricatesNoFinancialOutcome`; it was cited, not duplicated.
- Principal authorization remains the **context-only test seam**, not P5 IAM/JWT and not P2b C4
  cryptography.

### P1 leftovers carried into P2 (compact)

P1 codec/ACL handoff inputs still open: (1) `AwsRuntime` activation (construct the five atomic
gateways; retire the legacy claim/send path) — not started; (2) durable `CONTENT_CONFLICT` quarantine
and durable late-evidence quarantine flag — only the integrity/conflict marker exists; (3) legacy
`NOT_CONFIRMED` row disposition and timeout-event vocabulary — needs an approved-text decision;
(4) real-adapter end-to-end for the network-rejection and late-evidence branches (each branch is
tested at its own layer only); (5) OBS-21 observation-only retry — blocked on P4 notifier. G7 is
complete locally; G5 is complete locally except the quarantine gap; G6 remains **open** solely
because of items 1–4.

### P2a1a — strict header/bitmap parser (PARTIAL, 2026-11-20)

Delivered one parser-only primitive in `com.acquiring.simulator.protocol.iso8583`: strict
ASCII MTI (0100/0110) plus primary bitmap, 20-byte header, rejection of secondary bit and every
bit outside the P1 enabled set (DE38/DE39 structurally allowed, presence deferred). Three focused
tests over the approved packed-fixture prefix plus truncated/malformed bitmap and forbidden
secondary/DE2 cases passed locally (`mvn -o -Dtest=Iso8583HeaderParserTest test`, 3/3).
Evidence: `evidence/p2a1a-20261120T1905Z/`. This does not complete P1 or the codec; field
parsing, presence matrices, DE48 and domain admission remain open. Next slice: one DE field
length/alphabet primitive.

### P2a1b — strict field reader/cursor (PARTIAL, 2026-11-20)

Delivered one structural cursor primitive in the same package: `Iso8583FieldReader` with fixed
numeric, fixed uppercase-alphanumeric, LL numeric and LLL printable-ASCII reads, plus position,
remaining and exact-end checks; `Iso8583FieldException` carries the structural rule. LL/LLL
prefixes exclude themselves; reads never pad or truncate and advance only after validation.
`mvn -o -Dtest=Iso8583FieldReaderTest test`: **25 tests, 0 failures, 0 errors, 0 skipped**, exit 0,
after 95-source compile and 40-source test-compile. Tests cover DE32 1/11-digit bounds and
prefix/truncation/over-limit/non-decimal/non-numeric cases; DE42 exactly 15 via the approved
fixture walk (guards the historical DE32 misalignment), 14/16 width, lowercase/space/Unicode;
DE48 1/999 octets, zero/truncation/control/DEL/non-ASCII and offset consumption; cursor bounds
and trailing rejection. All source/test/resource/pom hashes were identical before and after the
run (141 files). Evidence: `evidence/p2a1b-20261120T191051Z/`. This does not complete P1 or the
codec: bitmap-driven field presence, DE48 canonical schema, DE38 rules, amount/date/domain
validation and admission remain open. Next slice: assembled structural scanner for all enabled
fields driven by the packed-fixture corpus.

### 31. P2a1c scanner verified; P2a2a DE48 request schema (PARTIAL, 2026-11-20)

**P2a1c** is accepted as previously run, not rerun: 94/94 `Iso8583ScannerTest`, 122/122
protocol regression, source/oracle hashes rechecked `OK` (`hash-reverify.txt`). The initial
attempt failed 2/94 on test expectations only (set-vs-order assertion; header-only body is a
FIELD-phase short body, not PRESENCE); corrected test, production unchanged, failure retained.
Fixture cases are **85**, not 94 JUnit tests: 31 ACCEPT + 12 scanner-scope rejects + 42
deferred (DE48 11, domain 8, admission 10, P4 5, binding 5, attestation 2, conflict 1).
Evidence: `evidence/p2a1c-20261120T191659Z/` (`EVIDENCE-MANIFEST.md`, `PROGRESS.md`).

**P2a2a** adds request-only `Iso8583De48RequestParser` / `Iso8583De48Request` /
`Iso8583De48Exception`: ordered canonical pairs, no terminal delimiter/spaces/escaping/
percent/nested encoding, known/unique/order/presence, per-key bounds, closed literals/enums,
`sent` 14 digits, `gen` 1..2147483647 without leading zeros; no normalization. 58/58 targeted
and 180/180 `Iso8583*Test`, exit 0, inputs byte-identical before/after. The 7 request schema
negatives in the corpus produce the exact rule; 25 other structurally accepted 0100 fixtures
parse, including 827- and 999-byte cases whose admission stays deferred. Not wired. Next slice:
P2a2b response canonical schema only; response packer, P4/DE38, domain and 826 admission stay
separate. Evidence: `evidence/p2a2a-20261120T192420Z/`.

### 32. P2a2b DE48 response canonical schema (PARTIAL, 2026-11-20)

**P2a2b** adds response-only `Iso8583De48ResponseParser` / `Iso8583De48Response`,
reusing the shared `Iso8583De48Exception` rule vocabulary (Javadoc widened to
request+response; no behavioural change). The parser requires the 16 request keys
echoed in order, optional `prior`, then mandatory
`actor,stage,result,reason,evidence,at`; it checks the full response sequence
itself rather than delegating the prefix to the request parser. Lexical rules
match the request parser: canonical semicolon pairs, no terminal delimiter/
spaces/escaping/percent/nested encoding, known/unique keys, per-key bounds,
alphabet, literals `v=1`/`op=A`, closed enums, `gen` form, and `sent`/`at` as
exactly 14 digits with no actual-time or Gregorian validation. No P4 matrix,
DE38/binding, domain, 826 admission, packer, HTTP or runtime wiring.

74/74 targeted and 254/254 `Iso8583*Test`, exit 0, inputs byte-identical
before/after (156 inputs). Response corpus: 41 structurally accepted 0110
fixtures = 22 schema-positive + 4 schema-negative (`N-wrong-op`→
`INVALID_LITERAL`/`op`; `N-missing-actor`/`N-missing-result`/`N-missing-evidence`
→`MISSING_REQUIRED_KEY`) + 15 schema-valid deferred (P4 5, binding 5,
attestation 2, conflict 1, domain 2). `CAP-response-3` is exactly 999 DE48 bytes
and remains schema-valid, not admission-checked. Evidence:
`evidence/p2a2b-20261120T193037Z/`. Next slice: P4 actor/stage/result matrix and
DE38/instruction binding, then domain Gregorian/amount/currency; response packer,
826 admission and runtime remain separate.

### 33. P2a3a P4 actor/stage/DE39/result/reason matrix (PARTIAL, 2026-11-20)

**Status: IMPLEMENTED AND LOCALLY VERIFIED; documentation-only finalization this
turn — no source edit, no test rerun, no new feature.** Delivered
`Iso8583P4MatrixValidator`, `Iso8583P4MatrixDecision`,
`Iso8583P4MatrixException` and `Iso8583P4MatrixValidatorTest`. Evidence:
`evidence/p2a3a-20261120T193639Z/` (`EVIDENCE-MANIFEST.md`, `PROGRESS.md`,
`BINDING.txt`, `source.sha256`, `oracle.sha256`, `hashes-*`, `git-status-*`,
attempt and final `*-stdout/stderr/exit`).

**Binding.** Repo `/home/pazfernando/projects/acquiring-simulator`, branch
`feat/wi-acq-auth-001`, HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`,
origin `github.com/pazfernando/acquiring-simulator.git`, configured model
`nan/deepseek-v4-flash`, no override, captured `2026-11-20T19:36:39Z`. No Git
write, publication, cloud/apply call, credential flow, deletion or spec/oracle
edit; runtime inactive.

**What the matrix accepts.** A pure syntactic check of one *claimed*
`(actor, stage, DE39, result, reason)` against the eleven approved rows, in this
fixed order: required components → supported DE39 `{00,05,30,91}` → permitted
actor/stage (`ISSUER/ISSUER_REPLIED`, `PROCESSOR/BEFORE_NETWORK`,
`NETWORK/BEFORE_ISSUER`, `NETWORK|PROCESSOR/POSSIBLE_ISSUER`) → DE39 permitted
for that pair → result fixed by DE39/stage → reason fixed by the tuple. The
eleven rows are the issuer `00` approval and `05` deliberate decline; processor
before-network and network before-issuer `30` `FORMAT|DISALLOWED`; network
before-issuer `91` `UNAVAILABLE`; network or processor possible-issuer `91`
`NOT_CONFIRMED` `UNCERTAIN|UNAVAILABLE`. Absence is not a row and never becomes
a manufactured response.

**Eligibility is not admission.** A successful call means only that the five
claimed values match an approved row. It is **not authentication**, not issuer
truth, not payment/authorization admission, not evidence trust. The actor text
is a caller claim; evidence-ID verification, DE38 presence/absence,
original-instruction/context/amount binding, durable processor receipt and the
financial outcome remain later layers. A network/processor claim of an issuer
code is rejected here; `00/05` are eligible only under a claimed `ISSUER` at
`ISSUER_REPLIED`.

**Corpus partition.** The 41 structurally accepted `0110` fixtures split as
**22 ACCEPT + 4 schema-negative + 5 P4-negative (`EVIDENCE_MATRIX`) + 10
later-layer** (instruction 3, auth-reference/DE38 2, attestation 2, conflict 1,
domain 2). The five P4 negatives and their exact first-failure rules:

| Fixture | Rule | Component |
| --- | --- | --- |
| `N-00-network` | `ACTOR_STAGE_NOT_PERMITTED` | `stage` |
| `N-05-processor` | `ACTOR_STAGE_NOT_PERMITTED` | `stage` |
| `N-30-issuer` | `DE39_NOT_PERMITTED_FOR_STAGE` | `de39` |
| `N-91-issuer` | `DE39_NOT_PERMITTED_FOR_STAGE` | `de39` |
| `N-unknown-code` | `DE39_UNSUPPORTED` | `de39` |

Diagnostics are fixed text, do not echo the rejected values and contain no
`=`/`;` delimiter.

**Source snapshot ↔ results.** `source.sha256`: validator
`2aa986da571753f4de21c1b3dab00849f8cbe11d1bc092747de3400eb8d1a771`, decision
`92b415935935825061f03a362c95e40e1f786fcdec26165b9c57c5f7d9dc2ffb`, exception
`a3da1f11ba557ad49fa9bf5345a741302dc8536903de02c3e67c17389f70633d`, test
`849f21be8561f4ecc8db3e77b52bae250e57e7aae8c9a6f715fe31ecc2dbf7b9`.
`hashes-before-final` = `hashes-after` (169 inputs, empty diff,
`HASH_DIFF_EXIT=0`). The earlier `hashes-before` differs at exactly one entry —
the test file `04dc119e…` → `849f21be…` — so the accepted **62/62** and
**316/316** logs are the `849f21be` snapshot while the earlier **29/29** and
**283/283** attempts are the superseded `04dc119e` snapshot (old bytes not
preserved beyond their hash). `316 = 25 FieldReader + 62 P4Matrix + 74
DE48Response + 58 DE48Request + 94 Scanner + 3 HeaderParser`. `git status`
before/after/final is identical (`STATUS_DIFF_EXIT=0`); `head-after` is
`cfc456f4…`; oracle recheck `OK` (`ORACLE_CHECK_EXIT=0`).

**Actual results (recorded, not rerun).** Attempt 1: `Iso8583P4MatrixValidatorTest`
29/29 and `Iso8583*Test` 283/283, exit 0. Attempt 2: 62/62 and 316/316, exit 0.
Final: 62/62 and 316/316, exit 0; 0 failures/errors/skips; stderr empty. **No
failing test/compile/regression attempt is recorded in this slice**; attempt 1
was green on the 29-test class subset, the class was then expanded test-only to
62, and attempt 2 plus the final rerun are green. The reported "delivery
failure" was the coordinator return/operation channel (aborted/no pending), not
a build or test failure; code and evidence were saved despite it.

**Trust limitations and non-claims.** Local JUnit evidence only. No
authentication, producer-principal attestation, evidence-ID resolution, DE38
binding, instruction/context/amount binding, durable receipt, conflict
admission or financial outcome; no C4 carriage, response packer, 826 admission,
domain/Gregorian/DE7 validation, HTTP route or runtime wiring; no full-codec,
full-suite, QA, Security or human-acceptance claim.

**Remaining.** Next smallest slice: instruction/context + exact
authorization-reference (DE38) binding over an **already-verified trusted-record
seam** — consume that record; invent no authentication. Rules
(`iso8583-profile.md:26/:97`): DE38 optional on approval, never mandatory without
real evidence; missing DE38 invalid only when the trusted record asserts a
reference the response omits; unsolicited/mismatching DE38 invalid; DE38 is not
a transaction ID or approval proof. Fixtures: `N-wrong-correlation`,
`N-wrong-context`, `N-mismatch-amount` (instruction) and `N-unsolicited-DE38`,
`N-missing-DE38-asserted` (auth reference). Then C4
attestation/carriage (`N-forged-actor`, `N-unresolvable-evidence`), domain
Gregorian/DE7/amount/currency (`N-response-DE7`, `N-response-invalid-date`),
conflict quarantine, response packer, 826 admission and runtime.

### 34. P2a3b evidence location — AUTH-001 folder retained, OBS pointer (2026-11-20)

P2a3b (response correlation/context echo + DE38 binding, 5/5) was saved under
`../WI-ACQ-AUTH-001/evidence/p2a3b-20261120T195014Z/` instead of this work
item's `evidence/`. The folder is **preserved in place**; nothing was moved,
copied or rewritten. This is the OBS-001 pointer only.

Exact existing artifacts and SHA-256 (no `EVIDENCE-MANIFEST.md` or
`integrity-files-before.sha256` exists in that folder; none is cited):

- `BINDING.txt` `47d7a3281f1984bf83813835df03d46e8d6c6a484af1fc7f23e3856aca5f22f8`
- `test-stdout.txt` `8d98d8bb3c68d72c2eec8254a6e61e1ea0cb02f0eb332346787eb3a2799e64a0`
- `test-stderr.txt` `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` (empty)
- `test-exit.txt` `9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa` (exit 0)
- `test-stdout-failure-1.txt` `ce72a740483662ab32b7bc2f55be282bd396b3b35311fbcf200ba48c45545e21`
- `test-exit-failure-1.txt` `4355a46b19d348dc2f57c046f8ef63d4538ebb936000f3c9ee954a27460dd865` (exit 1)
- `source-before.sha256` `20849bd4babfe0216bd989f064e062c86c1a41179de9b024522e82e14124d042`
- `source-after.sha256` `60e5172fb5314d30fecc5ad8dac2e85392df529fc4a5e346fafd4b6bf3117874`
- `integrity-files-after.sha256` `c5753ec99e8633df2ba5ee09c7da830e5539e1152adef2c290adc2791adae0ea`
- `git-status-final.txt` `fcbf8d0b0aa892dbf67ecef54956c1337af07a6235f913b8a342c464b14c7e8c`
- `head.txt` `1244ab5a8bb9a777450128221325968c13c9045f94b8fcf9bb40229a62f81ec7`

Precise subset: `Iso8583InstructionBindingValidator` compared the trusted
original-wire SHA-256 and the echoed `cid/aid/tid/att/acq/mid/term/pay/gen`,
then the trusted DE38 presence/value. `Tests run: 5, Failures: 0, Errors: 0`,
exit 0; the first failing attempt (raw `Map` where `Iso8583De48Response` was
required) is preserved. No production caller was wired; P4 stayed upstream;
the full P5 envelope was not covered.

### 35. P2a3c complete original-instruction echo binding (2026-11-20) — IMPLEMENTED AND LOCALLY VERIFIED

**Status: full remaining echo binding for one original instruction, tested
through the approved byte corpus. Not the full P5 envelope and not admission.**
Evidence: `evidence/p2a3c-20261120T195520Z/` (`EVIDENCE-MANIFEST.md`,
`PROGRESS.md`, `BINDING.txt`, before/after hashes, git-status, compile and test
logs).

**Binding.** Repo `/home/pazfernando/projects/acquiring-simulator`, branch
`feat/wi-acq-auth-001`, HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`,
origin `github.com/pazfernando/acquiring-simulator.git`, model
`nan/deepseek-v4-flash` with no override. No Git write, publication, cloud call,
credential flow, deletion or specification/oracle edit; runtime inactive.

**Delivered.** New `Iso8583PhysicalInstruction` holds the original physical
projection DE3/4/7/11/22/25/32/37/41/42/49. `Iso8583OriginalInstruction` now
carries `fixture/cvm/crypto/risk/sent/prior` plus that projection.
`Iso8583InstructionBindingValidator` consumes the scanned `0110` message and the
canonical DE48 echo and compares: trusted original-wire hash → DE48
`cid/aid/tid/att/acq/mid/term/pay/gen` → `fixture/cvm/crypto/risk/sent/prior` →
physical DE3/4/7/11/22/25/32/37/41/42/49 → DE38 presence/value. `prior` mirrors
the original exactly (omitted original forbids an echo; supplied original
requires an identical echo). The trusted hash remains caller-provided; no bytes
are re-encoded or hashed by the validator.

**Ordering.** P4 matrix stays upstream. Within this layer, any instruction echo
(including the new DE4 amount) fails before DE38. The explicit
`N-mismatch-amount` + unsolicited DE38 test proves the amount failure wins.

**Byte corpus.** The five approved binding negatives and two positives run
through scanner → DE48 response schema → P4 → binding, using the authoritative
`hex` octets and the explicit `evidence-fixtures.json` entry for the trusted
reference (no auth flag). `N-wrong-correlation` → `INSTRUCTION_FIELD_MISMATCH/cid`;
`N-wrong-context` → `.../acq`; `N-mismatch-amount` → `.../de4`;
`N-unsolicited-DE38` → `AUTHORIZATION_REFERENCE_UNSOLICITED/de38`;
`N-missing-DE38-asserted` → `AUTHORIZATION_REFERENCE_MISSING/de38`.
`V10-actual-DE38` binds `AB1234`; `V02-approved-response` binds absence. The
test rehashes the fixture's exact `original0100Hex` to the attested
`instructionSha256` and, for positives, the response octets to
`responseSha256`; the pipeline therefore covers actual byte messages, not only
projections.

**Actual results.** Compile attempt 1 failed (five malformed test helper calls),
preserved; compile 2 exit 0. Targeted
`Iso8583InstructionBindingValidatorTest` + `Iso8583InstructionBindingCorpusTest`:
15/15, exit 0. `Iso8583*Test` protocol regression once: 331/331, exit 0
(`331 = 316 prior + 15 new`), stderr empty. `hashes-before` = `hashes-after`
(178 inputs, empty diff); `git status` before/after/final identical; HEAD
unchanged.

**Not covered / separate seams.** Full P5 environment, producer/relay
principals, evidence identifier, attestation signature, `responseSha256`
binding, signing principal/key, transport receipt and durable processor receipt
are not declared covered. Domain amount/currency/Gregorian/DE7 consistency,
registry/mandate authorization, freshness, 826 admission, packer, C4 carriage,
HTTP route, production caller and runtime wiring remain deferred. P1 and G6
remain open.

**Next domain-validation slice.** Apply only equality/format-first domain
projections on the same byte corpus: Gregorian `sent` plus DE7 consistency
(`N-response-DE7`, `N-response-invalid-date`), then the amount/currency profile,
without admission or registry semantics.

### 36. P2a4 bounded pure domain validator (2026-11-20) — IMPLEMENTED AND LOCALLY VERIFIED

Evidence: `evidence/p2a4-20261120T200021Z/` (`EVIDENCE-MANIFEST.md`,
`PROGRESS.md`, `BINDING.txt`, before/after hashes, git status, compile and test
logs). Binding: branch `feat/wi-acq-auth-001`, HEAD
`cfc456f4f469d9558a78fda8ff9b770b00817315`, origin
`github.com/pazfernando/acquiring-simulator.git`, model
`nan/deepseek-v4-flash`, no override. No Git write, publication, cloud call,
credential flow, deletion or specification/vector edit; runtime inactive.

**Delivered.** `Iso8583DomainValidator` is one pure class (plus
`Iso8583DomainException` and `Iso8583DomainDecision`) applying the approved P6
domain order: DE3 `000000`/DE22 `051` or `071`/DE25 `00` → DE4
`100..999999` inclusive → DE49 `840` only → strict Gregorian `sent` → DE7
exactly the `sent` `MMDDhhmmss` projection. No rounding, truncation, padding,
nearest-year inference or normalization. Leap/century rules are deterministic
(`1900-02-29` rejects, `2000-02-29`/`2028-02-29` accept); year `0000` rejects as
not a Gregorian year; invalid months/days/hours/minutes/seconds reject.
Response DE7 is compared with the echoed original request `sent`, never with the
response `at`; `at` is not validated beyond its schema width and is never reused
as `sent`. The scanner/canonical schema stay upstream and the P2a3c equality
binding validator remains unchanged and downstream, so amount/currency/date/DE7
failures precede binding.

**Byte corpus.** `Iso8583DomainCorpusTest` rebuilds the exact `hex` bodies and
runs scanner → DE48 schema → domain → (responses) P4. All eight approved domain
negatives match their labels: `B-below-min`/`B-over-max` → `AMOUNT_OUT_OF_RANGE/
de4`; `B-non-usd` → `CURRENCY_UNSUPPORTED/de49`; `D-leap-invalid`/`D-bad-month`
and `N-response-invalid-date` → `INVALID_GREGORIAN_DATE/sent`; `D-DE7-mismatch`
and `N-response-DE7` → `DE7_MISMATCH/de7`. Positives: `B-min-amount`,
`B-max-amount`, `D-leap-valid`, `Y-2026`, `Y-2027`, `V02-approved-response`
(the last has `at` one second later than `sent`). `N-response-DE7` is the explicit
probe that DE7 follows `sent`, not `at`. Unit tests add profile-code, inclusive
boundary, leap/century/year-change, invalid-time and amount-before-date ordering
coverage.

**Actual results.** Compile exit 0. One targeted attempt errored because a test
helper passed the invalid value in the `sent` slot instead of the `at` slot; it
is preserved, production code was not changed, and the rerun passed 23/23,
exit 0. Protocol regression `Iso8583*Test` once: 354/354, exit 0
(`354 = 331 prior + 23 new`), stderr empty. Hashes: 178 before → 183 after, only
the five new files added; every pre-existing input byte-identical. Packed-vector
oracle rechecked against `review-manifest.json`. HEAD unchanged.

**Not covered / separate seams.** Freshness and 826 admission, registry/fixture/
mandate/route resolution, response packer, C4 carriage, HTTP route, production
caller, runtime wiring and the full P5 envelope remain deferred. P1 and G6 stay
open. Local JUnit/byte-fixture evidence only, not QA, Security or acceptance.

### 37. P2a5 real packer byte-exact oracle (2026-11-20) — IMPLEMENTED AND LOCALLY VERIFIED

Recorded from the already-saved run; **no test was rerun or artifact recreated
to write this entry.** Evidence:
`evidence/p2a5-20261120T200920Z/` (`EVIDENCE-MANIFEST.md`, `PROGRESS.md`,
`BINDING.txt`, before/after hashes, git-status, targeted/protocol logs).
Binding: branch `feat/wi-acq-auth-001`, HEAD
`cfc456f4f469d9558a78fda8ff9b770b00817315`, origin
`github.com/pazfernando/acquiring-simulator.git`, model
`nan/deepseek-v4-flash`, no override. No Git write, publication, cloud call,
credential flow, deletion or specification/vector edit; runtime inactive.

**Delivered.** `Iso8583Packer` emits body-only canonical bytes: MTI (4 ASCII
digits, profile-selected) + 16 uppercase hex primary bitmap + ascending data
elements. It enforces the P1 field set and presence/forbidden matrix, rejects
DE1/secondary, checks exact fixed widths, numeric/uppercase-alnum alphabets,
LL/LLL zero-padded length prefixes and `DE48 <= 999` structural bytes. It never
pads, trims, rounds, truncates, normalizes or rewrites DE48, and it encodes DE38
only when supplied (never mints one). Canonical DE48 schema, domain, P4,
binding, freshness, 826 admission, attestation and dispatch remain downstream.

**Oracle counts.** 85 approved vectors; `ACCEPT=31`; canonical
`EVIDENCE_MATRIX` responses `=5`; 36 bodies packed byte-exactly. Targeted
`Iso8583PackerTest`: **53/53**, exit `0` (31 + 5 parameterized + 17 unit).
Protocol `Iso8583*Test`: **407/407**, exit `0` (`354 prior + 53 new`), stderr
empty. All-input hashes 185/185 identical, diff exit `0`; HEAD unchanged.

**Source ↔ run.** The hashed packer source, exception, data-element table, test
and `packed-vectors.json` equal the recorded inputs; compiled classes predate
the saved run by seconds, so the 53/407 logs correspond to those exact sources.

**Not covered / separate seams.** Domain amount/currency/date/DE7,
registry/mandate/route/fixture resolution, freshness, 826 admission, P4/binding
admission, response packing, C4 carriage, HTTP route, production caller and
runtime wiring remain deferred. P1 and G6 stay open. Local JUnit/byte-fixture
evidence only, not QA, Security or acceptance.

**Next slice.** ONE pure NEW-request freshness validator over the approved
300-second `sent` window with an explicit trusted mode seam; authorized
historical replay/late evidence is not re-evaluated or re-encoded. The 826
exchange budget remains separate.

### 38. P2a6 pure NEW-request freshness window (2026-11-20) — IMPLEMENTED AND LOCALLY VERIFIED

Evidence: `evidence/p2a6-20261120T201546Z/` (`EVIDENCE-MANIFEST.md`,
`PROGRESS.md`, `BINDING.txt`, before/after hashes, git status, targeted
stdout/stderr/exit). Binding: branch `feat/wi-acq-auth-001`, HEAD
`cfc456f4f469d9558a78fda8ff9b770b00817315`, origin
`github.com/pazfernando/acquiring-simulator.git`, model
`nan/deepseek-v4-flash`, no override. Runtime inactive; no Git write,
publication, cloud call, credential flow, deletion or specification/vector edit.

**Delivered.** One pure `Iso8583FreshnessValidator.evaluate(sent, receivedAt,
mode)` using the approved P2 sentence: "New first dispatch sent must be
within 300 seconds of server receipt; authorized historical replay/late evidence
uses persisted request, not this freshness check." The full Gregorian `sent`
instant is compared with the supplied server receipt: distance `<= 300 s` is
`FRESH`, `> 300 s` is `STALE` with label `ADMISSION_FRESHNESS`. Exactly 300.000 s
is inside; 300.001 s and 301 s are outside; no extra clock-skew allowance is
added. `Iso8583AdmissionMode` is a trusted server-side enum, never a wire field.
`AUTHORIZED_HISTORICAL_REPLAY` and `LATE_EVIDENCE` return `NOT_APPLICABLE`
without reading or re-encoding the submitted timestamp, so a historic linked
replay/late evidence does not apply NEW freshness or dispatch again.

**Actual results.** `Iso8583FreshnessValidatorTest`: **5/5**, exit `0`, stderr
empty. Cases: actual `D-stale` hex (301 s before receipt) → `STALE`; exact
300 s `FRESH`; 301 s and 300.001 s `STALE`; cross-year `20261231235959` and
`20270101000001` against `2027-01-01T00:00:00Z` `FRESH` while
`20261231235459` `STALE`; replay/late with stale and null timestamps
`NOT_APPLICABLE`; missing mode/sent/receipt fails closed. Main recompiled 121
sources and tests 50; inputs 177 → 181 with **zero** changed/removed (diff exit
`1` only for the four additions).

**Not covered / separate seams.** 826 exchange budget,
registry/mandate/route/fixture resolution, authentication/attestation, P4 and
instruction/DE38 binding, duplicate/conflict resolution, response packing, C4,
HTTP route, production caller and runtime wiring remain deferred. No domain or
admission claim. P1 and G6 stay open. Local evidence only; not QA, Security or
acceptance.


### 39. P2a7 pure NEW-exchange 826-byte admission budget (2026-11-20) — IMPLEMENTED AND LOCALLY VERIFIED

Evidence: `evidence/p2a7-20261120T202112Z/` (`EVIDENCE-MANIFEST.md`,
`PROGRESS.md`, `BINDING.txt`, pre-edit/frozen/after hashes, git status,
targeted and protocol stdout/stderr/exit, toolchain). Binding: branch
`feat/wi-acq-auth-001`, HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`, origin
`github.com/pazfernando/acquiring-simulator.git`, model
`nan/deepseek-v4-flash`, no override. Runtime inactive; no Git write,
publication, cloud call, credential flow, deletion or specification/vector edit.

**Delivered.** One pure `Iso8583ExchangeBudgetValidator.evaluate(bytes, mode)`
for the approved P2a rule: structural parsing keeps DE48 `<=999`, NEW exchange
admission requires `<=826`, and `827..999` fails `ADMISSION_RESPONSE_CAPACITY`
before claim/dispatch. The 173-byte reserve is derived from the approved family,
not a magic capacity: 44 canonical tail-key bytes + 51 worst accepted P4
metadata bytes (`PROCESSOR` + `BEFORE_NETWORK` + `PROCESSOR_REJECTED` +
`DISALLOWED`, probed through the P4 matrix) + 64 evidence bytes + 14 `at`
digits. `999 - 173 = 826`. Per-key maxima would invent an unapproved 181-byte
reserve, so only accepted combinations count. Replay/late evidence returns
`NOT_APPLICABLE` without reading, re-encoding or resending; the mode is trusted
server-side, never on the wire. The validator takes only a byte count, so it
cannot drop `prior`/unknown keys, mint aliases, shorten references or truncate.

**Actual results.** `Iso8583ExchangeBudgetValidatorTest`: **7/7**, exit `0`,
stderr empty. Protocol `Iso8583*Test`: **419/419**, exit `0`, stderr empty (412
prior + 7 new). Eleven approved packed vectors exercised: `B-DE48-exact-999`
(999, structurally re-packs, NEW rejects), `CAP-request-826` (accepts),
`CAP-request-827` (rejects), `CAP-over-request-1..7` (all 827, 17 extension
keys and `prior` intact then reject), and `CAP-response-3` (826 prefix + exact
173-byte suffix = 999, parses and packs byte-exact). Main recompiled 123
sources and tests 51; pre-edit 181 → frozen pre-run 184 → after 184 with frozen
and after byte-identical and **zero** existing inputs changed.

**Not covered / separate seams.** Registry/mandate/route/fixture resolution,
authentication/attestation, freshness, P4 and instruction/DE38 binding,
duplicate/conflict resolution, response construction beyond structural packing,
C4 carriage, HTTP route, production caller and runtime wiring remain deferred.
P1 and G6 stay open. Local evidence only; not QA, Security or acceptance.

**Next slice.** ONE ordered pure codec pipeline evaluator (scanner → canonical
DE48 request → domain → freshness → 826 budget) returning the first failure
label; then the pure ACL principal/role boundary seam. No claim, dispatch,
HTTP or runtime in either slice.

### 40. P2a8 recovered completion — one ordered request pipeline (recorded 2026-11-20)

**Status: the P2a8 run was already completed and saved; this entry records it.
No test was rerun and no artifact recreated.** Evidence:
`evidence/p2a8-20261120T203052Z/` (`PROGRESS.md`, the new compact
`EVIDENCE-MANIFEST.md`, `BINDING.txt`, `delivered-files.sha256`, frozen/after
hashes, git status, `request-first-failure-mapping.tsv`,
`protocol-regression-stdout.txt`, the JUnit XML and `toolchain-version.txt`).

**Binding.** Branch `feat/wi-acq-auth-001`, HEAD
`cfc456f4f469d9558a78fda8ff9b770b00817315`, origin
`github.com/pazfernando/acquiring-simulator.git`, model
`nan/deepseek-v4-flash`, no override. No Git write, publication, cloud call,
credential flow, deletion or specification/vector edit.

**Delivered (already saved).** One pure
`Iso8583RequestValidationPipeline.evaluate(body, receivedAt, mode)` running
scanner → canonical DE48 request schema → domain → freshness → 826-byte budget
in the approved P6 order, with `Iso8583RequestValidation` and
`Iso8583RequestValidationException`. The envelope has no
accepted/admitted/authorized/authenticated flag. Replay/late modes return
`NOT_APPLICABLE` for freshness and budget and never re-encode the persisted
original.

**Recorded results.** `Iso8583RequestValidationPipelineTest` **53/53**
(`tests="53"`, 0 failures/errors/skips) and one combined `Iso8583*Test` protocol
run **472/472** (419 prior + 53 new), exit `0`, stderr empty. The 43-fixture
request corpus is 9 ACCEPT + 34 ordered negatives and all 43 rows match in
`request-first-failure-mapping.tsv`. The four delivered files still verify
against their saved SHA-256 list. The `hashes-frozen-pre-run`/`hashes-after`
pair is byte-identical and the P2a7-to-P2a8 diff contains only the four
additions.

**Recovery correction.** `PROGRESS.md` cites `ATTEMPTS.md`, which is absent from
the saved folder; that citation is not expanded or reconstructed. The compact
manifest records this explicitly. The run's evidence is unchanged.

**Not covered.** Registry/fixture/mandate/route resolution, authentication and
attestation, P4, instruction/DE38 binding, duplicate/conflict, claim, dispatch,
response construction, C4, HTTP/service caller and runtime wiring stay deferred.
P1 and G6 remain open. Local evidence only; not QA, Security review or human
acceptance.

### 41. P2c1 recovered completion — pure C1 channel ingress and exact amount (recorded 2026-11-20)

**Status: the P2c1 run was already completed and saved; this entry records it.
No test was rerun, no artifact recreated and no source or vector edited.**
Evidence: `evidence/p2c1-20261120T204141Z/` (the new compact
`EVIDENCE-MANIFEST.md`, `PROGRESS.md`, `BINDING.txt`, attempt-1 and final
`delivered-files*.sha256`, `hashes-before`/`frozen-attempt1`/`frozen-pre-run`,
`git-status-*`, `targeted-*`, `test-compile-*`, `toolchain-version.txt`).

**Binding.** Branch `feat/wi-acq-auth-001`, HEAD
`cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged), origin
`github.com/pazfernando/acquiring-simulator.git`, model
`nan/deepseek-v4-flash`, `model_override=none`, captured
`2026-11-20T20:41:41Z`. Temurin JDK 25.0.5. No Git write, publication, cloud
call, credential flow, deletion or specification/vector edit.

**Delivered (already saved).** One pure C1 ingress slice:
`ChannelRequestValidator` (4096-byte bound → strict UTF-8/pure ASCII → single
JSON object with unknown/duplicate-key rejection → required keys → declared
fields → typed request), `ChannelAuthorizationRequest`, `ChannelAmount`,
`ChannelErrorCode`, `ChannelRequestValidationException`, and the test class.
The closed schema rejects unknown, duplicate and null keys, numbers for strings
and string coercion for booleans, non-ASCII values and caller-owned
decision/route/actor/generation/PAN-shaped keys. The failure type carries only
the fixed error code, a stable rule label and a declared field name; it never
echoes caller values, the raw payload or the rejected amount. Amount conversion
is exact `integral*100 + cents` over `[0-9]{1,4}\.[0-9]{2}` with floor `1.00`
and ceiling `9999.99`: no float, no rounding mode, no truncation/padding/
normalization/locale; `toAmount` is exact scale-2 `BigDecimal`.

**Recorded results.** Attempt 1 (preserved) `Tests run: 20, Failures: 1`, exit
`1`; final `ChannelRequestValidatorTest` **20/20**, exit `0`, stderr empty. The
eight approved amount vectors `J1`–`J8` execute from their real JSON bodies; the
three `ACCEPT` cases also assert exact minor units, scale-2 plain string, the
12-digit DE4 projection and `noRounding=true`. No full-suite total (e.g.
"523 + 20") is claimed; only the targeted class ran.

**Failure/fix diagnostic.** Test-oracle defect, not a product code defect: the
attempt-1 assertion demanded field `paymentRef` for a body with a **raw**
non-ASCII byte, which `decodeAscii` correctly rejects as `NON_ASCII_BODY` with
`field = null`. The fix dropped the field argument on that body-level assertion;
the companion ASCII `\\u00e9` escape still yields `INVALID_REFERENCE` /
`paymentRef`. The five production classes are byte-identical across both
attempts — the only inter-attempt diff is `ChannelRequestValidatorTest.java`.
The attempt-1 test source is not preserved, so this rests on the saved failure
text, the line-184 location and the identical production bytes.

**Integrity.** `sha256sum -c hashes-frozen-pre-run.sha256` → **200/200 `OK`**
against the current tree, `delivered-files.sha256` → **6/6 `OK`**, `HEAD`
unchanged. `hashes-before` = 194 inputs; both frozen snapshots = 200 (6
additions). So the green 20/20 run exercised the exact current snapshot.
`pom.xml` is byte-identical before/after and already declares Jackson
`jackson-databind 2.20.1`, so no new dependency was added. Record note:
`channel-vectors.json` still says `"status": "PROPOSED not executed"` while the
test executes all eight; recorded, not edited.

**Not covered / next.** Registry/fixture/mandate/route resolution,
authentication/attestation, identity allocation/aliasing, P4, instruction/DE38
binding, duplicate/conflict, claim, dispatch, response construction, C4
carriage, HTTP transport, production caller and runtime wiring stay deferred.
Next: ONE bounded, server-resolved instruction mapper (validated typed request →
approved instruction fields, server-side resolution only; no real allocation,
no authentication, no client override). C4, HTTP and runtime remain outstanding.
P1 and G6 stay open. Local evidence only; not QA, Security or acceptance.

### 42. P2c2 recovery — pure server-resolved instruction mapper, targeted test (recorded 2026-11-20)

**Status: the prior P2c2 attempt aborted with no pending operation and no saved
result log; this recovery inspected that directory, authored the missing
targeted test (explicitly authorized) and ran only that class.** Evidence:
`evidence/p2c2-20261120T205321Z/` (`EVIDENCE-MANIFEST.md`, `PROGRESS.md`,
`BINDING-RECOVERY.txt`, prior `hashes-before`, new `hashes-after-edits` /
`hashes-after-run` / `delivered-files`, `targeted-*`, `head-after-run`,
`git-status-after-run`).

**Binding.** Branch `feat/wi-acq-auth-001`, HEAD
`cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged), origin
`github.com/pazfernando/acquiring-simulator.git`, model `nan/deepseek-v4-flash`,
`model_override=none`. Temurin JDK 25.0.5 and Maven 3.10.1 used via explicit
absolute `JAVA_HOME`/`PATH` (system `java` is absent). No Git write,
publication, cloud, credential, deletion, specification/vector edit or new
dependency.

**Delivered.** The existing `ServerResolvedInstruction`,
`ServerInstructionResolution`, `ServerResolvedInstructionMapper` and
`InstructionMappingException` were compiled unchanged; the new
`ServerResolvedInstructionMapperTest` holds exactly three cases: (1) one
exact-byte oracle comparing the full packed `0100` body to a literal
profile-composed ASCII string, (2) a trusted-context mismatch
(`CONTEXT_MISMATCH`, value-free message) and (3) a 948-byte DE48 new dispatch
rejected at `BUDGET` / `ADMISSION_RESPONSE_CAPACITY`.

**Recorded results.** `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`, exit
`0`, stderr empty, BUILD SUCCESS. Oracle assertion count: **1 exact-byte, 0
roundtrip-body**. Only this class ran. `hashes-after-run` is byte-identical to
`hashes-after-edits` (214 inputs), so the green run exercised the current
snapshot.

**Trust boundary / not covered.** Mapping only: no admission, authentication,
authorization, claim, dispatch, send, registry/fixture/mandate/route resolution,
identifier or alias allocation, counter increment, hashing, outcome, response
or evidence, DE38/DE39, C4, HTTP or runtime. C4, HTTP/service caller and runtime
stay deferred; P1 and G6 remain open. Local evidence only; not QA, Security
review or human acceptance.

### 43. P2b2a recovered completion — DIRECT Ed25519 verifier (recorded 2026-11-20)

**Status: the P2b2a run was already completed and saved; this entry records it. No test was
rerun, no artifact recreated and no source or vector edited.** Evidence:
`evidence/p2b2a-20261120T211750Z/` (manifest, progress, binding, source before/after, diff,
targeted logs, toolchain versions, delivered-file and evidence hashes).

- **Binding.** Branch `feat/wi-acq-auth-001`, HEAD
  `cfc456f4f469d9558a78fda8ff9b770b00817315` unchanged, origin
  `https://github.com/pazfernando/acquiring-simulator.git`, model `nan/deepseek-v4-flash`,
  no override, runtime inactive.
- **Delivered.** Real JDK `Signature("Ed25519")` DIRECT verification over the exact
  canonical attestation bytes; key resolution by
  `(environmentId, signingPrincipal, keyId)`; authenticated-sender, environment,
  outer/signed/registry key, P5 role-to-P4 row and acquirer-mandate binding; candidate body
  and canonical-attestation hash binding; distinct sanitized violations; and
  `C4VerifiedAttestation` with identity/type/hashes/scope only (no payment, receipt,
  `valid` or `verified` flag). Schema correction applied to the approved P1/P2 profile:
  RRN exactly 12 uppercase alphanumerics; authorizationId/transactionId/instructionAttemptId
  1–64; amountMinor 100..999999; authorizationReference null or exactly 6 uppercase
  alphanumerics.
- **Actual saved result.** `C4EvidenceVerifierTest,C4EvidenceAttestationSchemaTest,C4CarrierParserTest`
  → **42/42** green (18 verifier + 6 schema + 18 carrier regression), exit `0`, stderr
  empty, BUILD SUCCESS. This record does not rerun it.
- **Trust limits.** Ephemeral in-memory test keys only; no key custody/rotation, TLS/IAM
  principal mapping, external credentials, HTTP, storage admission, P6 binding or runtime.
  P1 and G6 remain open. Independent Security review, QA and human acceptance remain
  pending.
- **Freeze record correction.** The P2b2a saved folder is retained as evidence, but its
  before-edit hash list is not treated as a frozen run baseline for this phase. P2b2b
  applies the corrected post-edit freeze below.

### 44. P2b2b — RELAY Ed25519 verification extension (completed 2026-11-20)

**Status: implemented and locally verified. Local construction evidence only — not
independent QA, Security review, human acceptance, business admission or deployment.**
Evidence: `evidence/p2b2b-20261120T212353Z/` (`BINDING.txt`, frozen/after source hashes,
source-stability, targeted stdout/stderr/exit, summary, manifest, progress, delivered-file
and evidence hashes).

**Binding.** Branch `feat/wi-acq-auth-001`, HEAD
`cfc456f4f469d9558a78fda8ff9b770b00817315` unchanged, origin
`https://github.com/pazfernando/acquiring-simulator.git`, model `nan/deepseek-v4-flash`,
no override; Temurin JDK 25.0.5 and Maven 3.10.1; runtime inactive.

**Delivered.**

- `C4EvidenceVerifier` now dispatches DIRECT or RELAY. RELAY keeps the inner issuer
  attestation, key and signature unchanged and additionally verifies the configured
  `NETWORK-SIM` countersignature with the JDK Ed25519 provider over
  `C4RelayAttestation.signedBytes()` — the canonical relay object excluding `signature`.
- Relay binding: authenticated transport sender must equal the relay principal
  `NETWORK-SIM`; the relay key is resolved by
  `(environmentId, NETWORK-SIM, relay.keyId)` and its configured environment, principal,
  keyId, `NETWORK_SIM` role and acquirer mandate are rechecked; relay `responseSha256` must
  equal both the candidate body hash and the signed response hash; relay
  `attestationSha256` must equal both the candidate canonical-attestation hash and the
  canonical inner attestation hash.
- Inner binding: the issuer key is resolved by
  `(environmentId, signingPrincipal, issuer keyId)`; the role is constrained to
  `ISSUER_SIM`; the P4 row is constrained to issuer approval or deliberate decline; the
  issuer acquirer mandate is rechecked; the inner Ed25519 signature is verified over the
  exact canonical attestation bytes. The network cannot mint an issuer outcome or replace
  the issuer signature.
- `C4VerifiedAttestation` adds explicit
  `VerificationScope.RELAY_ED25519_AND_TRUST_BINDINGS_VERIFIED` and a `RelayBinding`
  (principal, keyId, receiptId, deliveredAt) for RELAY; DIRECT keeps
  `DIRECT_ED25519_AND_TRUST_BINDINGS_VERIFIED` with a null relay binding. There is no
  boolean `valid`/`verified`/payment/receipt flag and no raw signature, key or body in the
  result.
- `C4VerificationViolation` removes the obsolete `RELAY_UNSUPPORTED` and adds
  `RELAY_BINDING` for an absent or mismatched relay object; signature failures remain
  `BAD_SIGNATURE` with distinct sanitized fields `issuerSignature` and `relaySignature`.
- Tests: 16 new RELAY cases — valid approval and decline chains; inner-signature and
  relay-signature alteration; network signature substituted for the issuer signature;
  inner signer not `ISSUER_SIM`; relay signer not `NETWORK_SIM`; relay response-hash and
  attestation-hash mismatch; wrong authenticated sender; unknown relay key; registry key
  binding mismatch; wrong authenticated environment; relay mandate mismatch; relay
  canonical object excludes `signature` and binds receipt/time/both hashes; and the inner
  attestation plus both signatures remain unchanged after verification. The existing 17
  DIRECT cases are preserved; the obsolete RELAY-unsupported case was replaced.

**Actual result.** Command:

```
mvn -o -Dtest=C4EvidenceVerifierTest,C4CarrierParserTest test
```

- `Tests run: 51, Failures: 0, Errors: 0, Skipped: 0`, exit `0`, stderr empty, BUILD
  SUCCESS.
- `C4EvidenceVerifierTest`: 33 (17 DIRECT preserved + 16 RELAY).
- `C4CarrierParserTest`: 18 (carrier regression).
- No `failIfNoTests=false`; the selectors matched real tests. No failing iteration in this
  phase; the single targeted run passed. A pre-freeze `test-compile` also exited `0`.
- **Freeze.** `source-frozen-pre-run.sha256` and `source-after-run.sha256` are
  byte-identical over **229/229** inputs: every file under `src/main` and `src/test`
  (including `src/main/resources`), `pom.xml`, and the three C4 oracles
  (`vectors/carrier-vectors.json`, `channel-contract.md`, `iso8583-profile.md`). The green
  run therefore exercised the exact frozen source bytes.

**Crypto and trust limits.** Both signatures are verified with the JDK provider. Test keys
are ephemeral in-memory `KeyPairGenerator` output; no private key is written, logged,
serialized, exported or included in evidence. Key custody/rotation/revocation,
TLS/mTLS principal binding, IAM mapping, external credentials and production provisioning
are **not** implemented. No `instructionSha256` comparison against a persisted original
instruction, no P6 ISO/business binding, no duplicate/conflict resolution, no atomic
evidence+receipt+version+event admission, no late-delivery envelope/instruction binding
and no HTTP/service caller or runtime wiring exists. The result is cryptographic and trust
binding evidence only; it is not payment confirmation and not a processor receipt.

**Preserved / not performed.** No full suite, HTTP endpoint, runtime start, AWS/cloud call,
credential flow, Git write, publication, deletion, specification/vector edit or new
dependency. Pre-existing tracked and untracked work was preserved. P1 and G6 remain open.
Independent Security review, QA and human acceptance remain pending; this entry is not
self-approval.

**Next.** P6 original-instruction binding plus the C4 late-delivery envelope and
instruction-binding seam; then storage admission, HTTP/service caller, key custody and
runtime remain separate.

### 45. P2b3 recovered completion — C4 late-delivery envelope + verifier (recorded 2026-11-20)

**Status: the P2b3 targeted run was already completed and saved; this entry records it. No
test was rerun, no artifact recreated and no source or vector edited.** Evidence:
`evidence/p2b3-20261120T213542Z/` (`BINDING.txt`, `EVIDENCE-MANIFEST.md`, `PROGRESS.md`,
`run-provenance.md`, `chronology.tsv`, `surefire-*`, copied `test-stdout/stderr/exit`,
`hashes-current.sha256` + verify, `delivered-files.sha256` + verify, `git-status-*`).
Binding: branch `feat/wi-acq-auth-001`, HEAD
`cfc456f4f469d9558a78fda8ff9b770b00817315` unchanged, origin
`https://github.com/pazfernando/acquiring-simulator.git`, model
`nan/deepseek-v4-flash`, no override, runtime inactive.

**Recovered result.** Targeted invocation
`mvn -o -Dtest=C4LateDeliveryTest,C4EvidenceVerifierTest,C4CarrierParserTest,C4EvidenceAttestationSchemaTest test`:
`C4LateDeliveryTest` 23, `C4EvidenceVerifierTest` 33, `C4CarrierParserTest` 18,
`C4EvidenceAttestationSchemaTest` 6 — **80 run, 0 failures, 0 errors, 0 skipped**, exit `0`,
stderr empty, `BUILD SUCCESS` (targeted invocation only, not a full suite). Only
`C4LateDeliveryTest` (23) is new to the phase; the other 57 are preserved P2b1/P2b2
regressions in the same run.

**Provenance caveat (accepted with caveat).** No attested pre-run hash exists for this
phase; the current-byte hash list was captured during recovery. Correspondence between the
current source bytes and the bytes compiled for the 16:34 run is inferred from file mtimes
and the absence of later edits, not cryptographically attested. The 23-case chronology and
the copied stdout/stderr/exit are recorded in the manifest; the copies' hashes equal the
capture originals. This is local construction evidence only — not independent QA, Security
review, human acceptance, business admission, payment confirmation or processor receipt.

**Delivered (already present).** `C4LateDeliveryEnvelope`, `C4LateDeliveryParser`,
`C4LateDeliveryCandidate`, `C4LateDeliveryVerifier`, `C4VerifiedLateDelivery`,
`C4LateDeliveryViolation`, `C4LateDeliveryException` and `C4LateDeliveryTest`: closed
8192-byte late envelope, 4096-byte evidence and 2048-byte response-body bounds, canonical
base64/JSON, compression refusal, sender/relay/diagnostic-forward rules, and composition
of the real DIRECT/RELAY Ed25519 verifier. The late receipt id is a carriage label only.

**Not covered then.** `instructionSha256` vs a persisted original wire instruction, P6 ISO
parse/business binding, duplicate/conflict, atomic admission, HTTP, key custody and runtime
were absent; P1 and G6 stayed open. Independent Security review, QA and human acceptance
remain pending.

### 46. P2b4 — signed-P5 / P6 field binding over exact bytes (completed 2026-11-20)

**Status: implemented and locally verified. Local construction evidence only — not
independent QA, Security review, human acceptance, business admission or deployment.**
Evidence: `evidence/p2b4-20261120T214310Z/` (`BINDING.txt`, `EVIDENCE-MANIFEST.md`,
`PROGRESS.md`, frozen/after hashes, delivered-file verify, git status, targeted and C4
regression stdout/stderr/exit, `evidence.sha256`).

**Binding.** Branch `feat/wi-acq-auth-001`, HEAD
`cfc456f4f469d9558a78fda8ff9b770b00817315` unchanged, origin
`https://github.com/pazfernando/acquiring-simulator.git`, model `nan/deepseek-v4-flash`, no
override; Temurin JDK 25.0.5 / Maven 3.10.1 offline; runtime inactive. No Git write,
publication, cloud call, credential flow, deletion, specification/vector edit or new
dependency.

**Delivered.** One pure `C4P6SignedFieldBinder` (plus `C4P6BindingDecision`,
`C4P6BindingException`, `C4P6BindingViolation`) in the `channel` package. Inputs are an
already-produced `C4VerifiedAttestation` or `C4VerifiedLateDelivery` from the real
verifier, the exact signed `C4EvidenceAttestation` content whose canonical SHA-256 must
still equal the verified `canonicalAttestationSha256`, the current
`C4AuthenticatedTransportContext`, the immutable persisted original `0100` octets and the
exact received `0110` octets. No caller signature/verified flag and no arbitrary
trusted-evidence DTO is accepted; pairing a verified result with different signed content
fails `VERIFIED_BINDING`.

**What it binds.** Exact SHA-256 over the persisted original bytes equals the signed
`instructionSha256`; exact SHA-256 over the received response bytes equals the signed
`responseSha256`; then the approved P6 response order — domain (DE3/4/22/25/49 + Gregorian
`sent` + DE7) → P4 actor/stage/DE39/result/reason → original instruction/context echo and
exact DE38 — then the signed actor/stage/DE39/result/reason against the parsed response,
the signed `aid/tid/cid/att/acq/mid/term/pay/gen` and `DE4/DE49/DE11/DE37/DE7/sent`
against the persisted original projection, the signed `evidenceId` against the response
`evidence` echo, and the signed `availableAt` instant against the response `at` second
projection (sub-second values are rejected, not truncated). All 31 signed keys are bound;
`schemaVersion`/`profile` are fixed by `C4EvidenceAttestation` construction. Environment,
signing/relay principal, key id and acquirer reference are re-bound to the current trusted
context, so a different environment or sender is rejected rather than substituted.

**Recorded results (not rerun).** `C4P6SignedFieldBinderTest` **18/18**, exit `0`, stderr
empty, on the first attempt. Affected C4 regression once:
`C4EvidenceVerifierTest 33 + C4LateDeliveryTest 23 + C4CarrierParserTest 18 +
C4EvidenceAttestationSchemaTest 6` = **80/80**, exit `0`, stderr empty. Freeze
`hashes-frozen-pre-run` = `hashes-after` over **254** inputs (empty diff, exit `0`); the
five delivered files re-verify; `git status` before/after identical; HEAD unchanged. The
18 cases cover real Ed25519 DIRECT and RELAY positives, an actual DE38 positive, and single
boundary tampering of response bytes, original bytes, echo (`cid`), exact DE38, signed
result, signed amount, `availableAt` (including sub-second), signed reference, environment,
sender, and verified↔signed substitution, plus a no-confirmed-result assertion.

**Not covered / separate seams.** Registry/mandate re-resolution at binding time, key
custody/rotation/revocation, TLS/IAM principal mapping, durable processor receipt,
duplicate/conflict, lateness deadline, atomic evidence+receipt+version+event admission, the
financial outcome, HTTP, storage, production caller and runtime wiring remain deferred. The
decision carries signed claims only; no result is known or confirmed before the atomic
receipt transaction and no identifier is minted. P1 and G6 stay open; independent Security
review, QA and human acceptance remain pending.

**Next.** One atomic receipt integration seam over the bound decision and exact bytes
(conditional evidence+receipt+version+critical event commit, duplicate/conflict without
partial state), still local and without HTTP/runtime.

### 47. P2b5a — atomic normal verified-result receipt (incomplete record; recovered 2026-11-20)

**Status: incomplete original record; recovery separately verified. Local construction evidence
only — not independent QA, Security review, human acceptance or deployment.**

- Original OBS run: `evidence/p2b5a-20261120T214745Z/` — only `head-before.txt`, freeze hash,
  git status and run stdout/stderr/exit were written. `targeted-run1-exit.txt` is `1`; no
  results document was produced. Recorded as incomplete, not accepted completion.
- Recovery evidence, at the AUTH-001 location retained for this slice:
  `docs/ai-sdlc/work-items/WI-ACQ-AUTH-001/evidence/p2b5a-recovery-20261120T215327Z/`
  (`RESULTS.md`, `MANIFEST.md`, `PROGRESS.md`, hashes, run logs). Classification: reader
  type-mismatch defect in the persistence read seam, not a wrong test attribute or missing
  composer metadata.
- Recovery result: `EvidenceReceiptAtomicIntegrationTest` **4/4**, exit `0`.

### 48. P2b5b — normal signed receipt gateway recovery (completed 2026-11-20)

**Status: implemented and locally verified. Local construction evidence only.**
Evidence: `evidence/p2b5b-20261120T220425Z/` (`RESULTS.md`, `BINDING.txt`, freeze/post-edit
hashes, run 1/run 2 and diagnostic stdout/stderr/exit, `surefire-summary.txt`).

- Recovery of the aborted P2b5a attempt. Run 1 without source change: 10 tests, 1 failure
  (`declinedEd25519EvidenceCommits…` returned `UNKNOWN`, diagnosed as the test seeding
  `AUTH-001`/`TX-001`/`CORR-001` for the pinned `AUTH-002`/`TX-002`/`CORR-002` decline vector).
- Test-only identity fix; pinned fixtures were not edited and no assertion was weakened.
- Run 2: `SignedEvidenceReceiptGatewayIntegrationTest` **10/10**, 0 failures/errors, exit `0`.

### 49. P2b5c — durable signed-evidence conflict/quarantine (completed 2026-11-20)

**Status: implemented and locally verified. Local construction evidence only.**
Evidence: `evidence/p2b5c-20261120T221345Z/` (`RESULTS.md`, `MANIFEST.md`, `PROGRESS.md`,
`BINDING.txt`, `origin.txt`, freeze/after hashes, delivered-file hashes, git status diff,
surefire report, run stdout/stderr/exit).

- One sanitized `EVIDQ#…#QUARANTINE` marker for `REUSED_IDENTITY_DIFFERENT_CONTENT` and
  `CONTRADICTORY_DETERMINED_OUTCOME`; metadata only, written after trusted-context
  authorization, real C4 verification and real P6 binding; first witness kept; persistence
  failure cannot change the established outcome.
- `SignedEvidenceReceiptGatewayIntegrationTest` **15/15**, exit `0`.

### 50. P2b6a1 — late signed-evidence receipt composer (completed 2026-11-20)

**Status: implemented and locally verified. Local construction evidence only.**
Evidence: `evidence/p2b6a1-20261120T222414Z/` (`README.md`, edited/after hashes, head and git
status before/after, run stdout/stderr/exit, surefire report copy).

- One `LateSignedEvidenceReceiptComposer` that appends the existing normalized P5 receipt put in
  the SAME `TransactWriteItems` as the existing late composition; shared `signedReceipt(...)`,
  `requireReceiptSubmission(...)` and `receiptPut(...)` extracted with no normal-path behaviour
  change.
- `LateSignedEvidenceReceiptAtomicIntegrationTest` **2/2**, exit `0`.

### 51. P2b6b — signed late admission gateway (completed 2026-11-20)

**Status: implemented and locally verified. Local construction evidence only.**
Evidence: `evidence/p2b6b-20261120T223019Z/` (`RESULTS.md`, `MANIFEST.md`, `PROGRESS.md`,
`BINDING.txt`, freeze/after hashes, git status before/after, targeted and regression
stdout/stderr/exit, surefire reports, `wiring.txt`).

- One late gateway reusing the normal trust model: trusted context before storage, real C4
  DIRECT verify, strong original read, real P6 bind, envelope authorization after binding,
  evidence-time classification, P5 receipt dedupe, then one atomic late transaction.
- `LateSignedEvidenceReceiptGatewayIntegrationTest` **6/6**, exit `0`; justified shared-receipt
  regression `EvidenceReceiptAtomicIntegrationTest` **4/4**, exit `0`.
- On-time evidence delivered late returned `NORMAL_RESULT_ADMISSION` with no writes; true late
  committed once; replay preserved first receipt/late metadata/slots; contradiction quarantined;
  lost response recovered once; unreadable lookup stayed `UNKNOWN`.

### 52. P2b7 — internal signed-evidence service facade (completed 2026-11-20)

**Status: implemented and locally verified. Local construction evidence only — not independent
QA, Security review, human acceptance, business admission or deployment.**
Evidence: `evidence/p2b7-20261120T223613Z/` (`README.md`, `RESULTS.md`, `PROGRESS.md`,
`MANIFEST.md`, `BINDING.txt`, branch/head before/after, freeze/after hashes and diff, git status
before/after, run stdout/stderr/exit, surefire report, `wiring.txt`, `toolchain.txt`).

**Binding.** Branch `feat/wi-acq-auth-001`, HEAD
`cfc456f4f469d9558a78fda8ff9b770b00817315` unchanged, origin
`https://github.com/pazfernando/acquiring-simulator.git`, model `nan/deepseek-v4-flash`, no
override; Temurin JDK 25.0.5 / Maven 3.10.1 offline; runtime inactive. No Git write,
publication, cloud call, credential flow, deletion, specification/vector edit or new
dependency.

**Delivered (two new files only).** `SignedEvidenceAdmissionService` — one internal facade that
takes the already-authenticated `C4AuthenticatedTransportContext` and the exact
`C4LateDeliveryCandidate`/`C4LateDeliveryEnvelope`, re-hashes the carriage content, derives the
sender binding from the authenticated sender and envelope mode, and then delegates to the
existing normal or late signed receipt gateway. It owns no verification, storage, receipt,
business transition or conflict rule itself and accepts no verified/bound/late flag, no
caller-selected route and no unsigned fallback.

**Routing actually exercised.** True late evidence → late gateway once (`COMMITTED`; exact
redelivery `REPLAY`, first receipt/late metadata/slots preserved, no extra transaction).
On-time evidence delivered late → late classification `NORMAL_RESULT_ADMISSION` → normal signed
gateway invoked exactly once (`COMMITTED`, normal journal version, no late metadata). Tampered
Ed25519 proof → `REFUSED` with no writes; a forged body-hash label → facade `NONE` before either
gateway with no writes. Invalid attempts left the record `AWAITING`/`NONE`, version `1/2`, two
events, HEAD 2, slot 2, no receipt and no quarantine.

**Recorded result (not rerun).** `SignedEvidenceAdmissionServiceIntegrationTest` **3/3**,
0 failures/errors, exit `0`, against real DynamoDB Local (`amazon/dynamodb-local:2.6.1`), real
DIRECT verifier and real P6 binder. Freeze `hashes-before` = `hashes-after` over 42 inputs
(empty diff), HEAD unchanged, git status unchanged; `wiring.txt` shows no production reference
outside the facade and its test.

**Trust limits.** No HTTP/authenticated-transport integration, no sender authentication, no key
custody/rotation, no runtime activation, no AWS call, no capacity approval (test capacity only),
no downstream call, no send/resend/reversal. Diagnostic forwarding is refused by this facade
because the signed receipt gateways verify without the forwarding permission.

**Next.** HTTP/authenticated-transport integration handoff: bind a real internal endpoint or
authenticated transport adapter so it constructs `C4AuthenticatedTransportContext` and supplies
the exact envelope plus owner-approved deadline, with runtime activation, key custody and P1 G6
Security review still separate and unapproved.

### 53. P2b8 — internal payload v2/octet-stream transport primitive (completed 2026-11-20)

**Status: implemented and locally verified. Local construction evidence only — not independent
QA, Security review, human acceptance, runtime activation or deployment.**
Evidence: `evidence/p2b8-20261120T224049Z/` (`RESULTS.md`, `MANIFEST.md`, `PROGRESS.md`,
`BINDING.txt`, `branch.txt`, `origin.txt`, `toolchain.txt`, freeze/after hashes and diff,
git status before/after, targeted and regression stdout/stderr/exit, surefire copies).

**Binding.** Branch `feat/wi-acq-auth-001`, HEAD
`cfc456f4f469d9558a78fda8ff9b770b00817315` unchanged, origin
`https://github.com/pazfernando/acquiring-simulator.git`, model `nan/deepseek-v4-flash`, no
override; Temurin JDK 25.0.5 / Maven 3.10.1 offline. No Git write, publication, cloud call,
credential flow, deletion, specification/vector edit or new dependency.

**Delivered (nine new files only).** `HttpApiV2Wire`,
`HttpApiV2TransportViolation`, `HttpApiV2TransportException`, `HttpApiV2Request`,
`HttpApiV2RequestDecoder`, `HttpApiV2Response`, `HttpApiV2ResponseDecoder`,
`HttpApiV2ResponseEncoder` and `C4HttpCarriageBridge`. The
adapter accepts only payload v2 `POST /internal/sim-iso87a/v1/authorizations`, requires
`application/octet-stream` and `X-Simulator-Profile: SIM-ISO87A-v1`, refuses caller identity
headers, requires the platform `isBase64Encoded=true` flag with canonical padded RFC4648
base64, enforces a 1–2048-byte exact body and compression refusal, and retains the exact bytes.
The response encoder always emits base64 with `isBase64Encoded=true`; the bridge fixes the
normal 200 media/profile and exactly one `X-Simulator-Evidence` header carrying canonical
envelope bytes, and maps a decoded response to the existing `C4TransportResponse` seam.

**Trust boundary actually implemented.** No authentication. `C4HttpCarriageBridge` accepts a
`C4AuthenticatedTransportContext` supplied by the platform boundary and never constructs one
from a header, body, JWT claim or query parameter. **Exact missing mapping:** the C2
authenticated service-principal mapping does not exist in this repository. The only verified
platform authentication is the console C1 JWT authorizer in `ProcessorHandler`/`AwsRuntime`
(`requestContext.authorizer.jwt.claims.sub` → subject registry), and the signed-evidence
gateways still take the unbound `TrustedContextAuthorizer` seam. Private API Gateway
IAM/SigV4 service identity or mutual TLS remains the human architecture/Security gate stated in
`channel-contract.md` C2. No principal allowlist, unsigned fallback or new auth pattern was
invented.

**Commands and recorded results (not rerun).**
- `mvn -o -q -DskipTests compile` → exit `0`, empty stdout/stderr.
- `mvn -o -q -DskipTests test-compile` → exit `0`.
- `mvn -o -q -Dtest=HttpApiV2InternalRouteTest test` → exit `0`; Surefire **5/5**, 0
  failures/errors/skips.
- Relevant regression `mvn -o -q -Dtest=C4CarrierParserTest,C4LateDeliveryTest test` → exit
  `0`; **18/18** and **23/23**, 0 failures/errors/skips.

**Coverage exercised.** Exact 2048-byte request round-trip including non-text bytes;
non-canonical base64, empty/oversize body and non-base64 platform encoding refusal;
media/profile/compression/route/Content-Length refusal before any business or ISO call; forged
`X-Authenticated-Sender` refused on the request and unable to bind the response (correct
explicit context binds DIRECT, wrong context is refused by the existing C4 parser with
`AUTHENTICATED_SENDER`); exact response body, exactly one canonical evidence header, duplicate
evidence header refused by both decoder and encoder.

**Not implemented.** No HTTP endpoint/runtime activation, no production C2 principal mapping,
no key custody/rotation, no signature verification change, no late-delivery public endpoint or
financial worker, no ISO8583 call, storage write, send/new identifier/receipt, capacity
approval, deployment or AWS effect. P1 G6 Security, independent QA and human acceptance remain
open.

**Next.** Bind the missing platform C2 service-principal realization (IAM/SigV4 or mTLS) to
`C4AuthenticatedTransportContext` under the architecture/Security gate, then connect the
decoder/encoder to the approved internal route without letting the transport adapter decide
business outcome.

### 54. P2c4 — single full offline suite verification on the frozen tree (recorded 2026-11-20)

**Status: verification-only slice. One full offline suite was executed once and passed. Local
construction evidence only — not independent QA, Security review, human acceptance, runtime
activation or deployment.** Evidence: `evidence/p2c4-fullsuite-20261120T224632Z/`
(`RESULTS.md`, `MANIFEST.md`, `PROGRESS.md`, `BINDING.txt`, freeze file list,
`hashes-before/after` + empty diff + `sha256sum -c` output, git status before/after + empty
diff, unique `test-stdout/stderr/exit`, per-class and per-package Surefire summaries,
freshness check, 63 copied Surefire reports, `evidence.sha256`).

**Binding.** Branch `feat/wi-acq-auth-001`, HEAD
`cfc456f4f469d9558a78fda8ff9b770b00817315` unchanged, origin
`https://github.com/pazfernando/acquiring-simulator.git`, model `nan/deepseek-v4-flash`
configured default with no override; Temurin JDK 25.0.5 and Maven 3.10.1 at the fixed paths,
offline (`-o`). No source/test/resource/`pom.xml`/specification/vector edit, no rerun, no
Git write, publication, cloud call, credential flow, deletion or capacity default change.
The pre-existing modified/untracked work was preserved (working-tree status identical
immediately before and after the run). A later final snapshot adds two untracked files created
by the AI-SDLC Coordinator at 22:49:47Z (`delivery-status.md`,
`operation-a-completion-plan.md`); they are external to this slice, were not written or
modified here and are outside the 282-input freeze set.

**Command (exactly once).**

```
JAVA_HOME=/home/pazfernando/.local/opt/jdk-25 \
  /home/pazfernando/.local/opt/maven/bin/mvn -o -B test
```

**Actual aggregate.** Exit `0`; `BUILD SUCCESS`; total time `01:40 min`; **63 test classes,
959 tests run, 0 failures, 0 errors, 0 skipped**, `flakes=0` in all Surefire XML. Per package:
`com.acquiring.simulator` 48/2 classes; `.aws` 152/20; `.channel` 165/14; `.domain` 10/2;
`.domain.event` 61/10; `.protocol.iso8583` 523/15. The earlier targeted P2b8 results
(`HttpApiV2InternalRouteTest` 5/5 plus C4 regressions 18/18 and 23/23) were not recreated or
rerun; they are preserved in `evidence/p2b8-20261120T224049Z/`.

**Freeze correspondence.** 282 frozen inputs (all 194 files under `src/main`, all 68 under
`src/test`, `pom.xml`, 13 vector files, 6 approved contract inputs) hash identically before
and after the run (empty diff, `sha256sum -c` 282/282 `OK`), so the green run exercised
exactly the frozen bytes. HEAD unchanged; working tree unchanged; all 63 Surefire XML lie
inside the actual run window and the copies are hash-identical to `target/surefire-reports`.

**Interpretation limits.** Local construction verification only, against SQLite,
PostgreSQL/Testcontainers, DynamoDB Local and in-process fixtures. It does not verify AWS
service behavior, deployed IAM, production capacity/throttling, key custody or rotation,
TLS/mTLS or IAM/SigV4 principal binding, API Gateway wire behavior, JWT issuer/WebSocket
auth, ADOT compatibility or the end-to-end application. Offline mode means no network
resolution was exercised.

**C2 principal mapping — recorded open decision (not resolved here).** The exact approved
clause in `channel-contract.md` C2 is: "Require authenticated service principal with
route-specific permission and server context/fixture resolution; no trust in X headers for
identity. Mutual TLS or IAM-controlled ingress realization remains human architecture/Security
gate; candidate recommendation uses private API Gateway IAM-authorized HTTPS with SigV4
service identity." The owner decision records "single demo JWT principal and separate service
IAM". The **open decision** is therefore the ingress realization — mutual TLS versus private
API Gateway IAM-authorized SigV4 service identity — which remains an architecture/Security
gate. `C4HttpCarriageBridge` still only accepts a `C4AuthenticatedTransportContext` supplied
by the platform boundary. No auth was invented, no runtime was activated, and the console C1
JWT authorizer was not treated as service identity. This verification proceeded independently
of that gate.

**Remaining blockers.** C2 service-principal realization, P1 G6 atomic critical-path
durability with its Security review, independent QA and end-to-end application remain open;
human acceptance is not implied by this green suite.

### 55. T1 — requirement-to-evidence traceability matrix (2026-11-20)

**Status: documentation deliverable saved. No code, test, resource, specification, vector or
contract was created or edited; no test was run; nothing committed, staged, pushed or deployed.**
Deliverable: [requirement-traceability.md](requirement-traceability.md). Evidence:
`evidence/t1-traceability-20261120/`.

**Binding.** Branch `feat/wi-acq-auth-001`, HEAD
`cfc456f4f469d9558a78fda8ff9b770b00817315` unchanged, origin
`https://github.com/pazfernando/acquiring-simulator.git`, model `nan/deepseek-v4-flash`
configured default, no override. Pre-existing modified/untracked work (including the
Coordinator's ADR, plan, delivery status and Security directories) was preserved untouched.

**Commissioned pins verified at slice open.** ADR `d51bbf65…b0b39`, owner-decisions
`efef3d22…f18c`, requirements `49c5d412…59df`, channel-contract `495e1172…3bca`,
observation-contract `ca53e384…205a` — all matched. The completion plan matched its pin
`6e75d527…f2f` and was then appended by the AI-SDLC Coordinator at `2026-11-20T23:56:35Z`
to `cc522f02…672` (91 → 95 lines; a narrative "Execution resumed" section only, T1–T10 table
unchanged). The file was not edited by this slice; the change is disclosed in the deliverable
and in `BINDING.txt`, not silently absorbed.

**Delivered content.** One matrix mapping OBS-001 R01–R25, OBS-01–OBS-28 and 14 actually relevant
`WI-ACQ-AUTH-001` business rows to real `src/main` paths and to named test methods from the
already-recorded 959-test frozen run (`evidence/p2c4-fullsuite-20261120T224632Z`, **not rerun**).
Each row carries an explicit `implemented + developer-tested` / `partially implemented +
developer-tested` / `not implemented` status, a runtime-versus-test-only qualification, the
remaining gap, the completion task T1–T10 and `independent verification: PENDING`.

**Counts actually mapped** (`coverage-counts.txt`, recomputed from the document):

| Set | Rows | implemented+developer-tested | partial | not implemented |
| --- | --- | --- | --- | --- |
| OBS-001 R01–R25 | 25 | 13 | 9 | 3 |
| OBS-01–OBS-28 | 28 | 3 | 19 | 6 |
| Linked AUTH-001 rows | 14 | 13 | 1 | 0 |

End-to-end integrated rows: **0**. Independent verification: **0** (all PENDING). These are counts
of mapped rows, not of tests passed, and not a completion percentage. The AUTH-001 set is an
explicitly partial, relevance-filtered subset: no complete AUTH-001 matrix exists, and this slice
does not claim one.

**Concrete findings recorded, not fixed.** (1) `AwsRuntime.processor()` still builds the
four-argument legacy `AuthorizationService`, constructor-composing none of the atomic gateways and
no event store, so every P1/P2 capability is test-only construction until T3. (2) `AwsRuntime`
falls back to `RESPONSE_TIMEOUT_MS=2000` while the approved property is `PT30S` — a configuration
divergence requiring an explicit decision, recorded under AUTH-001 R20. (3) The ADR-selected mTLS +
OAuth 2.0 `private_key_jwt` ingress has no implementation; `C4HttpCarriageBridge` still only
accepts a platform-supplied `C4AuthenticatedTransportContext`. (4) One-week TTL/logical expiry,
history read/cursor, Streams/notifier/WebSocket, console, ADOT, Terraform and CI remain absent.

**Non-claims.** T1's matrix deliverable is produced; that does not close T1 as an independent
gate, does not make any requirement integrated, and is not QA, Security review or human acceptance.
The 959 green tests remain component evidence on one frozen commit. Redeploying any historical pass
against later source bytes is invalid until revalidated. Escalation to Fernando stays bounded: no
push, PR, deployment, credential flow or provider mutation was performed or is implied.

### 56. T3 prerequisite — AWS response-timeout default corrected to the approved 30 s (2026-11-20)

**Status: bounded source defect correction, IMPLEMENTED AND FOCUSED-TESTED. Not runtime
activation, not integration.** `AwsRuntime` still builds the legacy four-argument
`AuthorizationService`; no atomic gateway is constructed, no partial atomic path is wired, and no
`AwsRuntime` behaviour beyond the timeout value changed. No Git write, stage, commit, push, PR,
deploy, provider mutation, cloud call, credential change or deletion.

**Defect (previously recorded, now fixed).** `AwsRuntime.processor()` resolved
`RESPONSE_TIMEOUT_MS` with an inline default of `2000` ms, while approved AUTH-001 R20 states the
simulator default is **30 seconds from dispatch** and
`src/main/resources/application.properties` declares
`simulator.authorization.response-timeout=PT30S`. The AWS cold-start path therefore dispatched with
a 2 s expectation on the same code that the Spring path configured at 30 s. A probe reproducing the
exact pre-fix expression with the variable absent printed `2000 ms` vs approved `30000 ms` and
exited `1`: `evidence/t3-timeout-default-20261120/prefix-probe-{stdout,stderr,exit}.txt`.

**Correction.** One small configuration seam, `com.acquiring.simulator.config.ResponseTimeoutConfig`:

| Input | Behaviour |
| --- | --- |
| variable absent (`null`) | approved default `Duration.ofSeconds(30)` |
| positive whole milliseconds (`45000`) | preserved unchanged as the owner override (trimmed) |
| blank / whitespace only | `IllegalStateException` at cold start — no silent fallback |
| non-numeric, unit-suffixed or overflowing | `IllegalStateException`, cause preserved |
| zero or negative | `IllegalStateException` — never silently accepted |

`AwsRuntime.processor()` now calls `ResponseTimeoutConfig.resolve(System.getenv(RESPONSE_TIMEOUT_MS))`.
The duplicate window (`Duration.ofHours(24)`) and every other wiring line are untouched. The Spring
path, `SimulatorProperties.Authorization` validation, `application.properties`, the AUTH/OBS
specifications and all vectors are unchanged — the approved specification was not edited to justify
the existing code.

**Changed invalid-input behaviour, disclosed.** Pre-fix, a malformed or blank value raised a bare
`NumberFormatException` from `Long.parseLong`, and zero/negative was rejected later by the
`SimulatorProperties` record constructor. Post-fix all three fail closed in one place with an
`IllegalStateException` naming `RESPONSE_TIMEOUT_MS` and the observed value. Fail-closed means the
Lambda cold start fails before any authorization is evaluated, so a transport-timeout
misconfiguration cannot be converted into a decline; no evidence ordering, deadline-equality or
business-state rule was touched. This is a deliberate behaviour change, not a silent one.

**Focused verification** (`evidence/t3-timeout-default-20261120/`, JDK 25.0.5 Temurin, Maven 3.10.1,
offline `-o`): `mvn -o -B test -Dtest='ResponseTimeoutConfigTest,DeadlineDispositionTest,AwsTransportTest,AuthorizationCoreIntegrationTest'`
→ exit `0`, `BUILD SUCCESS`, **45 tests / 0 failures / 0 errors / 0 skipped**
(`ResponseTimeoutConfigTest` 6, `DeadlineDispositionTest` 9, `AwsTransportTest` 6,
`AuthorizationCoreIntegrationTest` 24). A post-fix probe loading `target/classes` printed
`30000 ms` and exited `0`. No broad suite was run: the edit changes one value resolution in one
cold-start factory, and the four classes above are the focused plus directly affected set.

**Freeze.** Build inputs frozen after the edits and before the tests: 265 paths (`pom.xml`, all of
`src`), `hashes-before.sha256` vs `hashes-after.sha256` identical, `sha256sum -c` 265/265 `OK`.
Working-tree snapshot unchanged across the run (`git-status-before.txt` = `git-status-after.txt`,
104 entries, freeze point captured after the edits). This slice adds or modifies exactly three
source paths — `AwsRuntime.java` (modified), `ResponseTimeoutConfig.java` and
`ResponseTimeoutConfigTest.java` (new) — plus this evidence directory and the two documentation
updates below. All pre-existing modified/untracked work, including the Coordinator's plan, status,
ADR and the Security `reviews/access-design-20261120` directory, was left untouched.

**Documentation updated.** `requirement-traceability.md`: the AUTH-001 R20 row now records the
correction and its test evidence, and the T2 gap-register clause reflects that the divergence is
fixed at source level. Neither edit claims integration, acceptance or independent verification; the
R20 row remains `PENDING`.

**Non-claims.** This is a local source correction with focused developer tests on one commit. It
does not prove AWS service behaviour, deadline persistence on the atomic path, runtime activation,
end-to-end application behaviour, independent QA, Security review or human acceptance.

### 57. T3 atomic runtime wiring + bounded verification run (2026-11-22)

**Status: IMPLEMENTED AND DEVELOPER-VERIFIED AT SOURCE LEVEL. `AwsRuntime.processor()` now builds
the atomic graph. NOT integrated, not deployed, not independently verified.** This section
supersedes every earlier statement that "`AwsRuntime` remains inactive", "no atomic gateway is
constructed" or "the runtime remains legacy"; those were accurate for their bytes and are not
rewritten.

**What changed (production).** One production file in this slice:
`src/main/java/com/acquiring/simulator/adapter/aws/AwsRuntime.java` (modified, +125/−0-ish).
`processor()` resolves the profile and the approved 30 s timeout as before, then delegates to a new
package-private `atomicService(...)` that constructs one graph: `DynamoEventStore` over
`EVENT_TABLE` with `OperationAManifest.manifest(capacity)` + `EventValidator`, the
`DynamoClaimGateway`, `DynamoDispatchIntentGateway` (with `SyntheticInstructionMaterializer`),
`DynamoResultGateway`, `DynamoNetworkResultGateway`, `LateEvidenceAdmissionGateway.over(...)` and
`IdentifierSource.randomHex()`. `ProcessorHandler`'s no-argument constructor is bound to
`AwsRuntime::processor`, so the deployed entry point now resolves to that graph. The legacy
four-argument `AuthorizationService` is no longer constructed on the AWS path; its only remaining
production construction is `LocalPresenterRuntime` (Spring/JDBC local regression harness).

**Configuration is fail-closed and never defaulted.** `EVENT_TABLE`, `ENVIRONMENT`,
`BOOT_INSTANCE_ID`, `CRITICAL_MAX_EVENTS_PER_OPERATION` and `CRITICAL_SLOT_BUDGET` must be present;
an absent or invalid value raises at cold start naming the exact variable, and a slot budget
smaller than the manifest requires is rejected. The O8 capacity numbers are unapproved and are
never chosen by the runtime.

**Test-side change (the whole slice's only test change).** One helper in the new
`AwsRuntimeAtomicWiringIntegrationTest.storeHead()`: treat DynamoDB Local's empty item map for an
absent HEAD as 0 (`item == null || item.isEmpty()`). No assertion weakened, removed or skipped. The
pre-fix red run is preserved (`fix-red-run1-*.txt`: 10 run / 1 error / exit 1).

**Diagnosed cause (recorded, not a production defect).** The earlier "dispatch intent requires an
undispatched `CLAIMED_NOT_SENT` claim" error was test-side: the earlier test revision handed the
gateway a claim read back *after* a completed authorization, and assertion helpers read the wrong
EVENT partition. `DynamoClaimGateway` correctly claims in the single AWAITING mode and persists
`committedJournalVersion = 1`; the in-memory undispatched claim is exactly what
`PossibleSendCriticalComposer` requires.

**Verification run** (`evidence/t3-atomic-wiring-20261122/`, JDK 25.0.5, Maven 3.10.1, offline `-o`,
HEAD `5bbd4eb`, model `nan/deepseek-v4-flash`): one invocation, 23 classes —
`AwsRuntimeAtomicWiringIntegrationTest` + all 20 `com.acquiring.simulator.aws.*Test` +
`AwsTransportTest`, `AuthorizationCoreIntegrationTest`, `ResponseTimeoutConfigTest` →
**192 tests / 0 failures / 0 errors / 0 skipped / exit 0 / BUILD SUCCESS / 01:15 min**. The three
classes that carried the fix re-verified at 6, 9 and 11 tests, matching the fix run. The full suite
was **not** run and is deferred; the 959-test baseline belongs to `cfc456f` and does not transfer to
these bytes.

**Freeze.** 292 inputs (211 `src/main` Java, 72 `src/test` Java, 8 `src/main/resources`, `pom.xml`)
hashed before and after: `verify-hashes-before.sha256` = `verify-hashes-after.sha256`, `diff` 0
bytes, `sha256sum -c` 292/292 OK, `git status` snapshots identical, HEAD unchanged. **Limitation
disclosed:** post-edit baseline — the freeze proves the run mutated nothing, not that these are
pre-edit bytes.

**What this proves.** The composition the production handler resolves to installs every atomic
gateway and no legacy branch is reachable from an atomic invocation; claim intent result network
and late evidence each commit inside one transaction; only a freshly committed intent sends; a
recovered intent never resends; a failed intent writes no event; a failed claim neither retries nor
falls back to the legacy claim; APPROVED/issuer-DECLINE/network-rejection/late-admission are all
served from persisted state.

**What remains unproven.** No integrated payment. Every green case is an in-process composition
against DynamoDB Local with `StubIssuerSimulator`; `processor()` itself is not executed by any test
(it needs process environment plus the `CONFIG#ACTIVE` registry row), so wiring equality is proven
by construction. Absent: the real authenticated boundary (API Gateway, mTLS, OAuth
`private_key_jwt`), real Lambda invocation, real issuer/network round trip, the mounted internal ISO
route, deadline persistence, timeout-observation vocabulary, history/query/cursor/expiry,
Streams/notifier/WebSocket, console, ADOT, Terraform, CI and independent QA/Security/acceptance.

**Documentation updated.** `requirement-traceability.md`: only the rows whose runtime reachability
genuinely changed (OBS-001 R05, R06, R07, R10, R11, R24 and OBS-20; AUTH-001 R03, R04, R09, R15,
R16, R20) plus the matrix preamble/gap-register wording. Every row remains
`independent verification: PENDING`. `delivery-status.md` still says "runtime remains legacy" and
was **not** edited here — it needs an owner update.

**Non-claims.** No Git write, stage, commit, push, PR, merge, deploy, release, publication, AWS
call, credential flow, provider mutation, approved-specification edit or invented capacity default
occurred. No onward delegation, no model override. Local implementation evidence is not independent
QA, Security review or human acceptance.

### 58. T3 full offline suite at current bytes (2026-11-22)

Bounded slice: one stale javadoc correction and the complete offline suite, run once. Evidence
[`evidence/t3-fullsuite-20261122/`](evidence/t3-fullsuite-20261122/RESULTS.md). Binding: branch
`feat/wi-acq-auth-001`, HEAD `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` unchanged, OpenJDK 25.0.5,
Maven 3.10.1 offline, model `nan/deepseek-v4-flash` with no override.

**Comment fix.** The late-evidence constructor of `AuthorizationService` still claimed it was not
Spring-wired and that `AwsRuntime` was unchanged. It now states that `AwsRuntime.atomicService`
binds that constructor for the Lambda runtime with the late-evidence gateway alongside the other
atomic gateways, and that the `@Autowired` constructor remains only the Spring/JDBC local harness
binding. Javadoc only: `comment-fix.diff` is three comment lines, no executable change.

**Suite.** `mvn -o -B test` once → **978 tests / 67 classes / 0 failures / 0 errors / 0 skipped /
exit 0 / BUILD SUCCESS / 01:48 min**, cross-checked against the 67 Surefire XMLs. This supersedes the
959-test `cfc456f` baseline for these bytes. `AwsRuntimeAtomicWiringIntegrationTest` ran 10 tests
green within it, so the previously in-process runtime composition claim now rests on a full-suite
run at the current tree. Disclosed: the first shell attempt returned exit 127 (`mvn: command not
found`) because the toolchain is not on the default `PATH`; it executed no test, and its outputs were
replaced by the real run's files.

**Freeze.** 292 inputs (211 `src/main` Java, 72 `src/test` Java, 8 `src/main/resources`, `pom.xml`)
hashed before and after: identical SHA-256, `diff` empty, HEAD unchanged. Limitation disclosed: the
baseline is the slice-start working tree (post-comment-fix, including pre-existing uncommitted
owner/coordinator edits), not the committed `5bbd4eb` bytes.

**Traceability.** No requirement or scenario status changed; only the evidence-basis rows were
corrected from the stale 959-test reuse to this 978-test run. Every row remains
`independent verification: PENDING`; integrated counts remain **0**.

**Remaining blocker.** T3 still needs the deployed journey: the mounted internal ISO route, the real
authenticated boundary (API Gateway, mTLS, OAuth `private_key_jwt`), a real Lambda invocation and a
real issuer/network round trip. `delivery-status.md` still describes an earlier failing run and was
not edited here; it needs an owner update.

**Non-claims.** No Git write, stage, commit, push, PR, merge, deploy, release, publication, AWS
call, credential flow, provider mutation, approved-specification edit or test weakening occurred. No
onward delegation, no model override. Local evidence is not independent QA, Security review or human
acceptance.

### 59. T4 console API boundary — verification run (2026-11-22)

Bounded slice: no feature, no new class. The `com.acquiring.simulator.console` package and its two
tests already existed on disk from an aborted run that recorded no result; this run executed them
and the atomic classes they delegate to. Evidence
[`evidence/t4-console-api-20261122/`](evidence/t4-console-api-20261122/RESULTS.md). Binding: branch
`feat/wi-acq-auth-001`, HEAD `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` unchanged, OpenJDK 25.0.5
(`JAVA_HOME=/home/pazfernando/.local/opt/jdk-25`), Maven 3.10.1 offline
(`/home/pazfernando/.local/opt/maven/bin/mvn`), Docker 29.1.3 with `amazon/dynamodb-local:2.6.1`,
model `nan/deepseek-v4-flash` with no override.

**Console run, exactly once.** `mvn -o -B test -Dtest=ConsoleAuthorizationRouteTest,ConsoleSubmissionAtomicIntegrationTest`
→ **16 tests / 0 failures / 0 errors / 0 skipped / exit 0 / BUILD SUCCESS**; 13 in
`ConsoleAuthorizationRouteTest`, 3 in `ConsoleSubmissionAtomicIntegrationTest`. Stderr is 0 bytes.
Because the run was green, no `console-analysis.md` exists and no case was weakened or skipped.

**Directly affected atomic run, once.** Nine claim/dispatch-intent/result wiring, gateway and
runtime-binding classes → **66 tests / 0 failures / 0 errors / 0 skipped / exit 0 / BUILD SUCCESS**.
Late-evidence and network-result classes were excluded deliberately: an approval does not execute
those ports.

**What the green result establishes, and only within the seams.** With a test-double decoder and a
fixed context registry, against DynamoDB Local, an authorized submission reaches
`AuthorizationService` exactly once: one downstream dispatch, one persisted `APPROVED` payment at
generation 1, three critical events (claim, possible-send, verified result), HEAD 3, and zero legacy
claim/dispatch/update/append/reject calls. A refused token produces 401 with zero dispatches, zero
claims, zero updates, zero appends, zero rejection audits, an empty event table and no record.
A duplicate submission replays the same reference without a second claim or send. The route-level
suite additionally pins the fixed order (transport → credential → rate-limit seam → media type →
closed C1 body → trusted context → exactly one service call) and refuses duplicated `Authorization`
headers, foreign contexts, unknown fields and oversized bodies before any business action.

**What it does not establish.** No token signature, JWKS or issuer truth (the decoder is a double);
no API Gateway, CloudFront, WAF, CORS, Keycloak or mTLS; no listener, HTML form, SPA, live timeline,
history read or numeric rate limit; `AwsRuntime.processor()` is not executed and no Lambda,
issuer/network round trip or mounted ISO route runs. A reached-service approval returns a
provisional `{authorizationReference, correlationId, outcome}` acknowledgement, not the closed C3
envelope.

**Freeze.** The 13 console slice files were hashed before and after: identical SHA-256,
`slice-hash-diff.txt`, `git-status-diff.txt` and `head-diff.txt` all empty. Limitation disclosed:
post-edit baseline — the console package and tests are untracked at HEAD and `AwsRuntime.java` /
`AuthorizationService.java` were already modified before the run, so this proves the run mutated
nothing in the slice, not that these are committed or pre-edit bytes.

**Traceability.** OBS-001 R01, R19 and OBS-23 had their implementation/evidence/runtime cells
corrected because the in-process console boundary genuinely changed what is recorded; no A/B/C
class changed (R01 stays C: there is still no UI, listener or history read), no integrated count
changed (still 0), and every row remains `independent verification: PENDING`.

**Distance to a browser-visible screen and a deployed submission.** What exists: a browser-shaped
HTTP request/response class, a token-policy seam, a closed C1 body parser, a fixed context registry
seam, a rate-limit seam and a single delegation into the atomic service, all developer-tested
in-process. A browser would still need a served SPA page with a POS form, an OIDC public client with
authorization code + PKCE, token handling outside browser storage and URLs, an HTTP API/CloudFront
route with a real JWT authorizer, inert rendering of the result and history, and the WebSocket
timeline for live updates — none of which exists here. A real deployed submission additionally needs
the API Gateway/CloudFront/WAF edge, Keycloak realm and JWKS, mTLS + `private_key_jwt` on
console→ACL (`svc-console`), the mounted internal ISO route, a real Lambda invocation with
environment/registry rows, a real issuer/network round trip, and the C3 response envelope instead of
the provisional acknowledgement.

**Non-claims.** No Git write, stage, commit, push, PR, merge, deploy, release, publication, AWS
call, credential flow, provider mutation, approved-specification edit or test weakening occurred. No
onward delegation, no model override. Local evidence is not independent QA, Security review or human
acceptance.

### 60. Continuation H1 — origin-verification gate + read-only history (2026-11-23)

Bounded slice: the N-02 application-side gate and the HTTP-only read route
`GET /console/v1/transactions/{id}/events`. Evidence
[`evidence/continuation-h1-20261123/`](evidence/continuation-h1-20261123/RESULTS.md). Binding: branch
`feat/wi-acq-auth-001`, HEAD `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` unchanged, OpenJDK 25.0.5
(`JAVA_HOME=/home/pazfernando/.local/opt/jdk-25`), Maven 3.10.1 offline, Docker 29.1.3 with
`amazon/dynamodb-local:2.6.1`, model `nan/deepseek-v4-flash` with no override.

**Delivered (console package, still untracked).** `ConsoleOriginVerificationGate`,
`ConsoleOriginDenial`, `ConsoleOriginDenialSink`, `StandardStreamOriginDenialSink`,
`ConsoleIntegrationEntryPoint`, `ConsoleRequestIdentity`, `ConsoleHistoryRoute`,
`ConsoleHistoryQuery`, `ConsoleHistoryCursorCodec`, and the additions to `ConsoleHttpRequest`.
The gate reads `EDGE_ORIGIN_VERIFY`, fails closed on absent/empty/short/bad-alphabet, compares in
constant time, classifies ABSENT/EMPTY/DUPLICATE/MISMATCH, runs as the first application step before
dispatch, `404`/`405`, body read, credential seam, rate limit and any service call, and answers with
the single opaque body `{"version":1,"error":"EDGE_ORIGIN_NOT_VERIFIED","action":null}` with no echo.
The history route does a strong HEAD then a bounded Query, freezes H/R/F, binds an opaque signed
cursor to principal/environment/mandate/generation, re-authenticates before and after the store read,
reports an explicit expired prefix and truthful gap ranges, and is HTTP-only with no payment
capability. No TTL/compaction, no projection, no transaction creation, no new dependency.

**Executed, in order** (`evidence/continuation-h1-20261123/`):

| Step | Exit | Observed |
| --- | --- | --- |
| `mvn -o -q -DskipTests compile` | 0 | empty stdout/stderr |
| test-compile attempt 2 | 1 | two test-source errors in `ConsoleHistoryRouteTest.java` — reassignment of the `final` local `rows` (line 137) and the undeclared `EXPIRED_TOKEN` reason (line 293) |
| test-compile attempt 3 | 0 | empty stdout/stderr |
| focused console classes, run 1 | 1 | **33 tests, 1 failure, 4 errors, 0 skipped** — the gate case wanted `401` and got `405`; four `CONTEXT_MISMATCH` errors from the test's append helper |
| same classes, run 2 | 0 | **33 tests, 0 failures, 0 errors, 0 skipped** — 4 `ConsoleHistoryRouteDynamoIntegrationTest`, 9 `ConsoleOriginVerificationGateTest`, 20 `ConsoleHistoryRouteTest` |
| existing console classes | 0 | **16 tests, 0 failures, 0 errors, 0 skipped** (13 `ConsoleAuthorizationRouteTest`, 3 `ConsoleSubmissionAtomicIntegrationTest`) |

**Red runs preserved; nothing deleted, disabled or weakened.** Both red artifacts are kept beside the
green one (`compile-attempt2-testcompile-stdout.txt`, `history-and-ov-tests-stdout.txt`, versus
`history-and-ov-tests-attempt2-stdout.txt`). Red and green report the same 33 cases across the same
three classes with `Skipped: 0` in both, so no case was deleted or disabled. The six red sites are
test-harness defects, not removed assertions: the gate fixture now builds the `POST` request the route
actually accepts, and the DynamoDB integration helper now appends with the collector-matching
`EventContext`. Chronology confines the corrections to test sources (latest `src/main` console file
`22:38:33`; red run finished `22:40:59`; green run started `22:41:31`; only test files written
between). Disclosed limitation: the red-revision sources are not preserved (untracked package, no
`git diff`), so non-relaxation rests on the failure signatures, not a byte diff.

**What this proves.** Within these seams — DynamoDB Local, a test-supplied cursor key, a test-double
decoder and a fixed context registry — the gate is first, fail-closed, constant-time, silent and
non-authenticating (the credential seam still answers `401`), and the history route pins a frozen
snapshot, bounds the read, reports gaps and expired prefixes truthfully, and refuses tampered, expired,
foreign and re-authorized-during-read requests with `400`/`403`/`404`/`405`/`410`/`503` and no
payload.

**What it does not prove.** No real issuer or JWKS; no CloudFront, WAF, deployed edge or Keycloak; no
cursor key sourcing; no server-side read-pass store, TTL, compaction or projection; no API Gateway
authorizer, Lambda invocation, mounted internal route, WebSocket, browser or UI. **N-02 is not closed
by these bytes alone.** The three slice ambiguities (malformed-pagination mapping, read-pass store,
cursor-key source) remain open in `RESULTS.md` §4.

**Traceability.** `requirement-traceability.md` moves only the rows whose status genuinely changed
(R01, R25, OBS-18, OBS-19: C → B) plus the count table and a new §12; A/B/C elsewhere is untouched,
integrated counts stay **0**, and every row remains `independent verification: PENDING`.

**Non-claims.** No Git write, stage, commit, push, PR, merge, deploy, release, publication, AWS call,
credential flow, provider mutation, approved-specification edit or test weakening occurred. No onward
delegation, no model override. Local evidence is not independent QA, Security review or human
acceptance.

### 61. Continuation H1 — QA-driven fixes (2026-11-23)

Independent QA returned **FAIL** for the H1 slice (`reviews/h1-qa-20261123/qa-report.md`, SHA-256
`2ac3c71662d725bf86a8db4adf86be8447f3405c5aaed6757fd8f6c0152e8844`). This step fixes the two
contract violations and the third advisory in the instructed order, and records the observations
rather than inventing behaviour. Evidence
[`evidence/continuation-h1-qa-fixes-20261123/`](evidence/continuation-h1-qa-fixes-20261123/RESULTS.md).
Binding: branch `feat/wi-acq-auth-001`, HEAD `5bbd4eb` unchanged, JDK 25.0.5, Maven 3.10.1 offline,
model `nan/deepseek-v4-flash` with no override.

- **QA-DEF-2 (MEDIUM).** Every history `503` now emits the pinned
  `{"version":1,"error":"AUTHORIZATION_UNAVAILABLE","action":null}` (initial registry outage,
  post-read registry outage, store `head` failure, read-range failure). Two existing assertions that
  encoded `UNAVAILABLE` were **changed because that value contradicted the adopted contract and the
  reviewed `historyErrors["503"]` fixture, not to make a red test green**; the implementation was
  corrected first. Added `serviceUnavailableBodyExactlyMatchesTheReviewedFixture` for an exact byte
  comparison.
- **QA-DEF-1 (LOW).** The verified token issuer is now carried on `ConsolePrincipal` (supplied by
  `DemoConsoleAuthorizer`), bound in the signed cursor payload, and compared on continuation and on
  the post-read re-authentication. Both per-page authentications are retained; a cursor minted under
  another issuer is refused (`403 CONTEXT_DENIED`, same signing key), while a token that expires
  during the read still returns `401` before disclosure.
- **QA-DEF-3 (LOW).** `AuthorizedContext` gains an optional trusted `terminalRef` (4-argument
  constructor retained for non-terminal callers). The cursor binds the fixture's
  `terminalRef: "TERMINAL-A"` when the registry supplies one, and a different terminal is refused.
- **Observations.** Mandate fingerprint vs named `registryMandateId`/`registryVersion` and the
  `?cursor=X&after=N` combination are **open questions for Requirements** and were not decided in
  code. `epoch` is settled for this HTTP-only read by D-6's "epoch when live" wording; the fixture's
  `epoch: null` is documentary. The pre-existing malformed-pagination mapping stays open as already
  disclosed in §60.
- **Executed.** `mvn -o -B -DskipTests test-compile` exit **0**. Focused console suites, one final
  run, exit **0**: **74 tests, 0 failures, 0 errors, 0 skipped** — `ConsoleHistoryRouteTest` 24
  (20 + 4 QA-fix tests), `ConsoleHistoryRouteDynamoIntegrationTest` 4,
  `ConsoleOriginVerificationGateTest` 9, `ConsoleAuthorizationRouteTest` 13,
  `ConsoleSubmissionAtomicIntegrationTest` 3, `ConsoleHandlerCompositionTest` 5,
  `ConsoleDevServerTest` 9, `DevStubTokenIssuerTest` 7. A first focused run (exit 1, 74/1 failure) is
  preserved: its only failure was a test fixture that reissued the context without the new terminal,
  so the terminal check fired before the generation check; the fixture was corrected and no
  assertion was relaxed.
- **What this does not prove.** No real JWKS/signature/issuer verification (K1), no deployed edge,
  CloudFront/WAF or Keycloak, no cursor-key custody, no server-side read-pass store, no listener,
  browser or UI. No payment path was touched. **N-02 is not closed by these bytes alone.** Every
  traceability row stays `independent verification: PENDING`.

**Non-claims.** No Git write, stage, commit, push, PR, merge, deploy, release, publication, AWS call,
credential flow, provider mutation, approved-specification edit or test weakening occurred. No onward
delegation, no model override. Local evidence is not independent QA, Security review or human
acceptance.

### 62. Continuation H1 — D-6 C-1.3 ordering and C-2 cursor combinations (2026-11-23)

Two code changes from the independent D-6 clarifications domain review
(`reviews/d6-clarifications-20261123/domain-review.md`, SHA-256
`d6a57e0ca4f0e22c838c8e397bd5d576b95e7f6fa9d2a880989ef6b9141464b9`, re-verified). The review is
advisory: this step implements its C-1.3 and C-2 directions only. The C-3 malformed-pagination
taxonomy and its two failure-order corrections are **not** implemented; the corrected text is
being authored separately. Evidence
[`evidence/continuation-h1-d6-c2-20261123/`](evidence/continuation-h1-d6-c2-20261123/RESULTS.md).
Binding: branch `feat/wi-acq-auth-001`, HEAD `5bbd4eb` unchanged, Temurin 25.0.5, Maven 3.10.1
offline, model `nan/deepseek-v4-flash` with no override.

- **C-1.3 / §7 ordering.** `ConsoleHistoryRoute.reauthenticateAfterRead` checked
  `fingerprint(mandateAfter)` (`410 RESYNC_REQUIRED`) before the context triple (`403
  CONTEXT_DENIED`), contradicting C-1.3's "checked first". Reordered so a context mismatch is
  `403` before a retired-generation `410`, on the post-read path; the pre-read continuation was
  already context-first and is unchanged. Both authentication calls, the `401`-before-disclosure
  rule and all other refusals are retained. A request that is both context-mismatched and
  generation-retired now returns `403`; one that is only generation-retired still returns `410`.
- **C-2 cursor combinations.** `queryOf` previously let a cursor win and silently ignored `after`.
  A cursor combined with `after` or with `snapshot` is now refused `400 INVALID_CURSOR` with a
  null action, after authentication and before mandate resolution or any store read. The registry
  and event store are never consulted.
- **Tests.** Five focused cases added to `ConsoleHistoryRouteTest` (24 → 29): continuation
  both-mismatch → `403`; post-read both-mismatch → `403` with the reader called once; post-read
  generation-only → `410`; `?cursor&after` and `?cursor&snapshot` → `400` with registry, head and
  reader calls all zero.
- **Executed.** `mvn -o -B -DskipTests test-compile` exit **0**. Focused console suites, single
  run, exit **0**: **79 tests, 0 failures, 0 errors, 0 skipped** — `ConsoleHistoryRouteTest` 29,
  `ConsoleHistoryRouteDynamoIntegrationTest` 4, `ConsoleOriginVerificationGateTest` 9,
  `ConsoleAuthorizationRouteTest` 13, `ConsoleSubmissionAtomicIntegrationTest` 3,
  `ConsoleHandlerCompositionTest` 5, `ConsoleDevServerTest` 9, `DevStubTokenIssuerTest` 7.
- **What this does not prove.** No real issuer/JWKS, deployed edge, CloudFront/WAF/Keycloak,
  cursor-key custody, server-side read-pass store, Lambda invocation, browser or UI. C-3 is open.
  **N-02 is not closed by these bytes.** Every traceability row keeps `independent verification:
  PENDING`; no A/B/C status or coverage count moves.

**Non-claims.** No Git write, stage, commit, push, PR, merge, deploy, release, publication, AWS call,
credential flow, provider mutation, approved-specification edit or test weakening occurred. No onward
delegation, no model override. Local evidence is not independent QA, Security review or human
acceptance.

### 63. Continuation H1 — D-6 C-3 malformed-pagination taxonomy and failure order (2026-11-23)

C-3 of the D-6 clarifications rev 2 is approved for adoption by the coordinator. The corrected text
(`design/contract-deltas/d6-clarifications-rev2-20261123/d6-clarifications.md`, SHA-256
`3aaac88a…9643a3`) and the independent domain re-check
(`reviews/d6-clarifications-rev2-20261123/domain-review.md`, SHA-256 `fde09a1f…d3d1ad7`) were both
re-verified with `sha256sum` before reading. The re-check is advisory; this step implements its C-3
direction only. Evidence
[`evidence/continuation-h1-d6-c3-20261123/`](evidence/continuation-h1-d6-c3-20261123/RESULTS.md).
Binding: branch `feat/wi-acq-auth-001`, HEAD `5bbd4eb` unchanged, Temurin 25.0.5, Maven 3.10.1
offline, model `nan/deepseek-v4-flash` with no override.

- **Fixed failure order.** The history route now refuses in this order and nothing after the `200`:
  `401` → request-form `400 INVALID_CURSOR` (syntactic C-3.1 a–f only) → resolution `503`/`403` →
  malformed-payload `400` (signature does not verify, or signed payload malformed) → retired-schema
  `410 RESYNC_REQUIRED` (after signature verification, before the context check) → expired `410` →
  context `403` → representation `410` → head `503`/`404` → `after` beyond the frozen head `400`
  (only after the head read) → `R`/`F` `410` → bound-`lastSequence` `400` → `200`. One code per
  failure class; `INVALID_CURSOR`/`CONTEXT_DENIED`/`NOT_FOUND`/`AUTHORIZATION_UNAVAILABLE` carry
  `action: null`, `RESYNC_REQUIRED` carries `action: RESYNC_REQUIRED`, matching
  `wire-fixtures.json.historyErrors`.
- **Codec.** `ConsoleHistoryCursorCodec` gained `CursorException.Kind.RETIRED`. `parse` judges
  structural malformation (`400`) before the retired schema/representation (`410`); a
  signature-valid payload with a non-current/absent `version` or a missing current-representation
  member is retired, per C-1.5. `open` keeps signature → parse → future-issued → expiry, so retired
  and expired both precede the route's context `403`. No payload member changed.
- **Route.** `RETIRED`/`EXPIRED` map to `410`; `INVALID` stays `400`. The step order is marked in
  code, and the C-3.4 early-refusal prohibition is explicit: `after > H` stays after `requireHead`
  and before any reader call, and `queryOf`/`parseAfter` are the syntactic-only form step that never
  reads the head. Unknown and repeated parameters remain refused in the form step.
- **Tests.** `ConsoleHistoryRouteTest` 29 → 40: resolution `503`/`403`, form-versus-resolution,
  unknown/repeat form parameters, malformed signed payload `400`, retired-schema `410` before a
  context `403` (forged signature-valid payload), expired `410` before a context mismatch, head `503`,
  `after > H` with `headCalls == 1`/`reader == 0`, unknown-target `404` on a continuation, and the
  bound `lastSequence` `400` after the head read.
- **Executed.** Compile exit **0**. Focused console suites, recorded run, exit **0**: **90 tests, 0
  failures, 0 errors, 0 skipped** — `ConsoleHistoryRouteTest` 40,
  `ConsoleHistoryRouteDynamoIntegrationTest` 4, `ConsoleOriginVerificationGateTest` 9,
  `ConsoleAuthorizationRouteTest` 13, `ConsoleSubmissionAtomicIntegrationTest` 3,
  `ConsoleHandlerCompositionTest` 5, `ConsoleDevServerTest` 9, `DevStubTokenIssuerTest` 7. An
  earlier green run (87 tests) preceded the last three stop tests; disclosed in `RESULTS.md` §5.
- **What this does not prove.** No real issuer/JWKS, deployed edge, CloudFront/WAF/Keycloak,
  cursor-key custody, server-side read-pass store, Lambda invocation, browser or UI. **N-02 is not
  closed by these bytes.** Every traceability row keeps `independent verification: PENDING`; no A/B/C
  status moves.

**Non-claims.** No Git write, stage, commit, push, PR, merge, deploy, release, publication, AWS call,
credential flow, provider mutation, approved-specification edit or test weakening occurred. No onward
delegation, no model override. Local evidence is not independent QA, Security review or human
acceptance.

### 64. Continuation K1 — real issuer decoder, JWKS key source and composition (2026-11-23)

The K1 package adds a real compact-JWS verification step behind the unchanged console policy core.
Evidence [`evidence/continuation-k1-20261123/`](evidence/continuation-k1-20261123/RESULTS.md).
Binding: branch `feat/wi-acq-auth-001`, HEAD `5bbd4eb` unchanged, Temurin 25.0.5, Maven 3.10.1
offline, model `nan/deepseek-v4-flash` with no override. No dependency was added; `pom.xml` is
unmodified.

- **Configuration.** `TrustedIssuerConfiguration` requires issuer, audience, JWKS URI, demo subject,
  demo client id, required and forbidden resource scopes, forbidden resource audience, the algorithm
  set and a lifetime ceiling. There is no literal issuer, default audience, built-in key, fallback
  subject or algorithm default: a blank or inconsistent value fails construction. Scopes and
  audiences must be disjoint, the algorithm set must be a subset of `RS256`/`RS384`/`RS512`, and the
  ceiling may not exceed 900 seconds. The JWKS URI must be `https` (loopback `http` only for local
  tests).
- **Key source seam.** `JwksSource` (current/refresh), `JsonWebKeySet`, `JwksParser` and the network
  adapter `HttpJwksSource` are separate from the verification logic, so a deployment can supply a
  managed source and tests can supply a non-network one. `HttpJwksSource` is only constructed from
  configuration, never from a literal, and only by `ConsoleTokenComposition`'s deployment factory.
- **Decoder.** `JwtConsoleTokenDecoder` fails closed in a fixed order: compact-JWS shape and JSON
  (`MALFORMED_TOKEN`), algorithm present and allowlisted before any key is consulted
  (`UNSUPPORTED_ALGORITHM`, which covers `none` and HMAC), non-empty `kid` (`UNKNOWN_KEY`), key
  lookup with at most one rotation refresh, signature verification, then issuer, audience,
  authorized party, scope, `exp`, `nbf`, `iat` and the lifetime ceiling. Claims are not read before
  the signature verifies. A key-source failure is `KEY_SOURCE_UNAVAILABLE`; there is no fallback to
  the stub, a cached authority or a trusted-caller path.
- **Rotation.** Lookup takes the current key set, refreshes once on an unknown `kid`, and retries.
  A newly rotated `kid` becomes usable after the refresh; a token signed by a retired key is refused
  because the refreshed set no longer contains it. No key is pinned by an earlier successful lookup.
- **Composition.** `ConsoleTokenComposition` builds the decoder and the unchanged
  `DemoConsoleAuthorizer` from one configuration, so claim order and the current-mandate
  intersection stay in one place. Both factory methods require an explicit configuration; there is
  no no-argument or default version. Composing the deployed Lambda business route remains a later,
  separate step.
- **Tests.** 127 tests asserted green in this pass, all with locally generated RSA keys and an
  in-memory key-source double: `JwtConsoleTokenDecoderTest` 25, `TrustedIssuerConfigurationTest` 7,
  `ConsoleTokenCompositionTest` 5, plus the focused console suites. The decoder tests exercise bad
  algorithm, missing `kid`, signature mismatch, wrong issuer, wrong audience, expired,
  not-yet-valid, missing scope, key-source outage, retired-key-after-rotation and the new-`kid`
  rotation path; the composition tests exercise the wrong-subject refusal through the policy core
  and show a refused token never reaches the business service.
- **Executed.** Compile exit **0** (no stdout/stderr). Three separate recorded invocations, each exit
  **0**: decoder group **32 tests**, composition **5 tests**, focused console suites **90 tests** — 0
  failures, 0 errors, 0 skipped throughout. No red run occurred.
- **What this does not prove.** Keys were generated locally; there was **no network JWKS fetch, no
  Keycloak round trip, no browser PKCE flow, no deployed composition and no AWS call**.
  `HttpJwksSource`/`JwksParser` are untested in this pass. `DevStubTokenIssuer` remains the
  explicitly labelled unsigned local double and is not authentication. Every traceability row keeps
  `independent verification: PENDING`; only the R19 and OBS-23 rows were updated in evidence and
  limitation text, with no A/B/C status move.
- **Open points, not resolved.** (1) A JWKS-unavailable condition currently surfaces as `401`
  `UNAUTHENTICATED` through the submission route, not a truthful `503`; a `503` needs a new route
  branch. (2) The dev stub is retained for the loopback harness. (3) The algorithm allowlist is
  RSA-only.

**Non-claims.** No Git write, stage, commit, push, PR, merge, deploy, release, publication, AWS call,
Keycloak call, credential flow, provider mutation, approved-specification edit, new dependency or
test weakening occurred. No onward delegation, no model override. Local evidence is not independent
QA, Security review or human acceptance.
