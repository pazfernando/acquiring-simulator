# Presenter web demo — implementation checkpoint

Status: IN PROGRESS. This is an early checkpoint written before code changes so that partial work
survives an interrupted run. It records binding, verified inputs, scope, gates and the intended
design. It is not a completion claim, QA verdict, Security review or acceptance.

Date written: 2026-11-22 (session clock), America/Bogota.
Task: implement the responsive private presenter browser UI for WI-ACQ-OBS-001 (T6 console slice:
sign-in/session state, closed-C1 purchase form, clear result/reference, live timeline,
reconnect/catch-up without resubmission, seven-day history/detail).

## Repository binding verified

- Working directory: `/home/pazfernando/projects/acquiring-simulator`
- Branch: `feat/wi-acq-auth-001`
- HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315` (matches the expected binding)
- Pre-existing working-tree changes preserved, not reverted: `AGENTS.md`, `docs/ai-sdlc/README.md`,
  the functional guide, WI-ACQ-OBS-001 `WORK_ITEM.md`/`activity-flow.md`/`domain-review.md`/
  `owner-decisions.md`, `docs/aws-operation-a.md`, `AwsRuntime.java`,
  `DynamoAuthorizationRepository.java`, `AuthorizationService.java`, plus untracked design/evidence
  directories. This run adds new files only; it does not rewrite those modified files.

## Verified governing inputs (SHA-256 recomputed in this session)

| Document | Expected | Observed | Result |
| --- | --- | --- | --- |
| `requirements.md` | `49c5d412e3705c320a2f78b190555f49e7e0fa4662cff9329d259f89e2b459df` | same | MATCH |
| `owner-decisions.md` | `fd040124319a946d28230aeb3994fb77bac82c00cb59e58b78df9e23531bae27` | same | MATCH |
| `acceptance-scenarios.md` | `1586898418c09dcf092d567a4155b4524a0bc34500bf4ea76ce8f76465aa7733` | same | MATCH |
| `channel-contract.md` | `495e1172e76ab9ba81f8a8026e7efec25baa6e4fff0d754c0a4c92bd38ea3bca` | same | MATCH |
| `observation-contract.md` | `ca53e384a46d24b3c1119b3e6ba804a9a9d4caac049a6b0c55a40c72842a205a` | same | MATCH |

`WORK_ITEM.md` is currently `3bd2fff44ae04c900d7629f4cb2f2c527187fceb67db33e649f0ebb5a791682c`
(the coordinator-supplied `49c5d412…` identity names `requirements.md`, verified above). The Work
Item remains the evolving receipt; the approved requirement identities above are the operative ones.

## Scope adopted (and explicitly out of scope)

In scope, local only:

- Loopback relay bound to `127.0.0.1` serving a private presenter page, session state via an
  HttpOnly `SameSite=Strict` opaque local session cookie (no JWT in browser storage, URLs, HTML,
  logs or event frames).
- Closed C1 POS form: exactly the `channel-contract.md` C1 fields, validated server-side by the
  existing `ChannelRequestValidator`; no caller-supplied identity, decision, route or fault.
- Submission through the existing `AuthorizationService` atomic path (`DynamoClaimGateway`,
  `DynamoDispatchIntentGateway`, verified `SignedEvidenceReceiptGateway`) against local DynamoDB,
  with the closed `C3ResponseBuilder` producing the HTTP/JSON result.
- Live timeline over a local WebSocket transport implementing the O5a subscription identity rules
  and O6 catch-up semantics, plus HTTP catch-up against the same event table.
- Reconnect/catch-up that never issues a payment POST; explicit counters exposed for verification.
- Seven-day history/detail read model over the same event table, with journal authority labelling,
  bounded pages, signed cursors, explicit expired/gap ranges.
- Inert rendering of every server-supplied string; CSP; no third-party assets.

Out of scope / not authorized by this run:

- No deployment, AWS resource creation, remote execution, publication, credentials, push, PR,
  merge or any Git write.
- No production authentication bypass. The demo user/JWT issuer does not exist locally, so the
  relay uses an **explicit local test-only session fixture** that refuses to start without a
  configured demo access code and loopback binding. It never pretends to be the approved issuer.
- No activation of unapproved identity or production paths: the AWS runtime (`AwsRuntime`), the
  public channel route, `LateEvidence` public path and any deployment configuration are untouched.
- No Operations B/C, no live scheme connectivity, no multiuser administration.

## Missing real dependencies and the fixture policy

| Real dependency | Status | Handling |
| --- | --- | --- |
| Single demo-user JWT issuer (Keycloak `private_key_jwt`) | Not provisioned; provisioning unauthorized | Local test-only session fixture; response and UI label `identityMode=LOCAL_TEST_FIXTURE`; demo mode + access code required; no token issuance claim |
| API Gateway WebSocket + notifier Lambda | Not deployed | Local WebSocket transport in the relay, documented as a local transport fixture; frame schema and O5a/O6 semantics preserved |
| DynamoDB Streams | Not available locally | In-process fan-out after observed appends, labelled `LOCAL_STREAMS_FIXTURE`; no durable per-client backlog; reconnect always uses HTTP catch-up |
| Ed25519 evidence key custody | Unapproved | Ephemeral in-memory key generated at relay start; never persisted or logged; clearly a local fixture |

## Design (local relay)

- `com.acquiring.simulator.presenter` (new):
  - `PresenterRelayServer` — JDK `HttpServer` on loopback, strict Host/Origin, CSRF header,
    static assets, security headers, no-store.
  - session registry + local fixture identity; submission service; history/detail read model;
    cursor codec; subscription registry; WebSocket hub; minimal RFC 6455 framing.
  - local composition root that reuses the existing atomic adapters and the signed-evidence
    gateway, plus a local simulated issuer that reads the durable `0100` and signs the correlated
    `0110`.
- `com.acquiring.simulator.adapter.aws.DynamoEventHistoryReader` (+ a read-only port) — reads the
  same event rows (no second payload copy) and the same HEAD.
- Static UI under `src/main/resources/presenter/` (`index.html`, `app.css`, `app.js`).

Contract mapping choices (documented, not invented decisions):

- `authorizationId` = journal record id; `transactionId` = channel payment reference;
  `attemptId` = `ATT-<authorizationId>` (same convention as the existing critical composers);
  `correlationId` = caller-requested correlation (echoed, not trusted);
  `originalCorrelationId` = the durable dispatch correlation.
- Business version = the row's explicit committed journal version; when absent the response is an
  honest `UNAVAILABLE`/503 rather than an invented version.

## Verification plan

1. Focused new unit tests for session/access policy, cursor tamper/scope/expiry, subscription
   identity/conflict rules, seven-day expiry/gap computation and WebSocket framing.
2. A local end-to-end test (DynamoDB Local): sign in, submit one purchase, assert APPROVED with one
   simulated send and one durable receipt, duplicate submit without resend, event timeline,
   reconnect catch-up with zero submissions, Seven-day history listing/detail.
3. Real browser journey with headless Chromium/Playwright: sign-in, submit, result, live timeline,
   disconnect/reconnect, history/detail; capture assertions as evidence.
4. Full local regression (`mvn -o -B test`) to confirm no existing behavior broke.

## Constraints carried from the task

- Preserve existing changes and the 968-test baseline; add tests, do not silently modify evidence.
- No credentials/tokens in browser storage/URLs; render supplied strings inertly; synthetic
  transactions only.
- Every claim in `RESULTS.md` must distinguish real-backed, local-fixture and unverified paths.
