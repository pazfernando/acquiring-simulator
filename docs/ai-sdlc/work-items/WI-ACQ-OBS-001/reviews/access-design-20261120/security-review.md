# Operation A — independent early access-design review

## Verdict and scope

**BLOCKED for design-readiness of the changed access/expiry boundaries. Early documentary review completed; final implementation Security review remains PENDING.** The mandatory scenario realization, issuer/client/certificate binding, WebSocket realization, key custody and per-record expiry evidence are not yet supplied. No runtime vulnerability is established by this report. No blanket PASS, deployment permission, risk acceptance, PCI claim or certification is granted.

Reviewer: ai-sdlc-security. Date: 2026-11-20 (America/Bogota; evidence fetched around 23:57 UTC). Runtime: native OpenClaw subagent / Codex assistant, not Implementation's OpenCode ACP. Observed session model metadata: **gpt-6.1-sol**, configured default, no task override; system identifies the assistant family as GPT-6. A provider-qualified model identifier is not exposed by session metadata, so none is invented. Session: agent:ai-sdlc-security:subagent:6bb33f55-2f1a-4e02-8d5c-ea72a20d187c. No delegation.

The security-audit skill was applied as focused, source-first guidance. Its full-code-audit/report workflow is not invoked: the explicit commission limits this to one early design review, forbids onward delegation, and assigns this specific report path. Read-only document/configuration inspection and official documentation research only; no target code, fixtures, builds, tests, scanners, deployed endpoints or credential flows executed.

Fernando selected mTLS + OAuth2 private_key_jwt, external/internal Gateway per scenario, GitHub OIDC + Variables/Secrets, us-east-2, on-demand billing without new business quotas/budget gates, and one-week expiry where applicable. These are preserved. Historical IAM/SigV4 service-ingress recommendations are superseded, not silently edited. AWS workload IAM for DynamoDB, Lambda invocation and callback management remains distinct from application client authentication.

## Exact reviewed inputs and binding

Repository: /home/pazfernando/projects/acquiring-simulator. Implementation-supplied branch feat/wi-acq-auth-001, HEAD cfc456f4f469d9558a78fda8ff9b770b00817315, origin https://github.com/pazfernando/acquiring-simulator.git.

Current evidence read through the authorized handoff route: evidence/t1-traceability-20261120/BINDING.txt (utc_start 2026-11-20T23:57:19Z) and git-status-before.txt. The tree is **dirty**, including preexisting application changes; this is not a review of clean HEAD alone. Earlier baseline: evidence/p2c4-fullsuite-20261120T224632Z/BINDING.txt. The supplied 959-test/282-frozen-input evidence is developer evidence, not Security acceptance; the current T1 binding explicitly reuses the suite without rerunning it. No Git/provider command was executed by Security. Parent assigned exclusive ownership of this report and its own adjacent evidence; all other files remained read-only.

All six commissioned SHA-256 values were independently recomputed and matched:

| OBS-001 input | SHA-256 |
| --- | --- |
| adr-operation-a-access-and-environment.md | d51bbf650aca74e82351dacf0e45d0f14c992044b9ff246bc7b8b281920b0b39 |
| owner-decisions.md | efef3d2272dd54130b404fae43ec90643bc42c5dd4701cc1b6b4e49d4ea0f18c |
| requirements.md | 49c5d412e3705c320a2f78b190555f49e7e0fa4662cff9329d259f89e2b459df |
| channel-contract.md | 495e1172e76ab9ba81f8a8026e7efec25baa6e4fff0d754c0a4c92bd38ea3bca |
| observation-contract.md | ca53e384a46d24b3c1119b3e6ba804a9a9d4caac049a6b0c55a40c72842a205a |
| domain-review.md | 73adba20fd70969bf8bb9f23ebde5c5d16aec69fd4694ac32984008b76ee0ca2 |

The last item is existing specification review, **not review of this access delta**. See [input identities](evidence/input-identities.json) for 16 inspected input identities, including repository AGENTS/PROJECT/README, both work items, AUTH-001 AWS update, infra/template.yaml and binding records. Role AGENTS/SOUL/USER/IDENTITY and shared README/PROJECT_EXECUTION/VERIFICATION_DELIVERY were also read. Latest owner ADR is the precedence overlay for stale IAM, exposure, quota and approval labels in pinned source contracts.

## Actors, assets and trust boundaries

| Boundary | Lower-trust actor/input | Assets and required trusted decision |
| --- | --- | --- |
| Presenter browser → local relay | Browser requests, hostile web origins, selected synthetic context | Demo session; exact Host/Origin + CSRF policy and loopback binding; browser never holds service private keys |
| Relay/ACL → OAuth issuer and Gateway | OAuth assertions, access tokens, client certificate | Registered service identity and bounded acquirer/merchant/terminal/environment mandate; reject identity from arbitrary headers |
| ACL → processor → simulated network/issuer | ISO bytes, replies and late evidence | At-most-one financial dispatch, durable business state, instruction identity, distinct issuer/processor knowledge |
| C4 producer/relay → evidence admission | Signed sidecar and exact response bytes | Evidence-signing key, authenticated sender role, response/instruction hashes, atomic result admission |
| Demo reader/WebSocket → event source | Transaction IDs, cursors, subscriptions, reconnects | Only permitted sanitized history; no issuer truth, cross-context subscriptions or financial send |
| GitHub runner → AWS/state/runtime | Workflow code, OIDC claims, artifacts and configuration | Scoped AWS role, Terraform state, deployment artifact and application keys; no authority from an arbitrary repository/ref |
| Expiry/Streams/notifier → retained journal/history | Delayed deletion, stale stream payload, duplicate callback | Honest truncated history and retained no-resend/evidence identities; notification is never financial authority |

Only synthetic credentials/fixture IDs are allowed. Raw ISO, JWTs/assertions, C4 sidecars/signatures, private keys and sensitive bodies must not enter browser output, application/event logs, ADOT or failure destinations. One demo principal is not permission to read every synthetic acquirer.

## Official compatibility facts

Sources were fetched read-only, not inferred from memory. See [dated excerpts and fetch status](evidence/official-source-excerpts.json).

1. [REST mTLS](https://docs.aws.amazon.com/apigateway/latest/developerguide/rest-api-mutual-tls.html) requires a Regional custom domain and truststore; it explicitly says mTLS is not supported for private APIs. Default execute-api invocation must be disabled to enforce the custom-domain mTLS path. Gateway does not itself check certificate revocation.
2. [HTTP API mTLS](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-mutual-tls.html) also requires a custom domain/truststore and disabling the default endpoint. Calling an endpoint “internal” does not make its network private.
3. [Private APIs](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-private-apis.html) are REST APIs callable through a VPC interface endpoint, with resource/VPC-endpoint policy restrictions. Private backend integration and private ingress are different properties. **Do not claim a native PRIVATE REST + Gateway-terminated mTLS combination.**
4. [HTTP JWT authorizers](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-jwt-authorizer.html) validate RSA-based JWT algorithms, issuer, audience/client_id and configured route scopes. A valid JWT signature alone does not distinguish access tokens from ID tokens; requiring API-specific audiences/scopes matters. The documented checks do not by themselves establish certificate-to-client or payment-mandate binding.
5. [WebSocket authorizers](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-websocket-api-lambda-auth.html) use a Lambda authorizer only on $connect; changing that authorizer does not revoke existing connections. [WebSocket custom domains](https://docs.aws.amazon.com/apigateway/latest/developerguide/websocket-api-custom-domain-names.html) document TLS, but the retrieved material does **not establish the required mTLS feature combination**. Native WebSocket mTLS/private ingress support remains UNVERIFIED here, not a confirmed unsupported-feature finding.
6. [RFC7523 §2.2/§3](https://www.rfc-editor.org/rfc/rfc7523) separates token-endpoint client assertion authentication from the access token. [RFC8705 §3](https://www.rfc-editor.org/rfc/rfc8705#section-3) defines certificate-bound tokens; using mTLS and private_key_jwt together does not automatically implement that binding.
7. [DynamoDB TTL](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/TTL.html) deletes asynchronously, typically within a few days, and uses numeric epoch seconds. Physical TTL is not seven-day logical access control.
8. [GitHub OIDC for AWS](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws) requires audience/subject trust restrictions; environment subjects differ from branch subjects. Current docs also describe immutable owner/repository IDs in some subject formats. Actual repository subject format is a deployment input to verify, not a hard-coded assumption.

RFC text/HTML fetches were truncated; only retrieved sections support these narrow statements. AWS/GitHub pages used for the listed feature constraints were not truncated. Documentation compatibility is not deployed interoperability evidence.

## Findings and missing design evidence

Classification follows the shared contract. **Confirmed unresolved vulnerability findings: 0 BLOCKER, 0 IMPORTANT.** Absence of a concrete design/provider/topology is not proof of a reachable vulnerability. NV items below are source-grounded **needs-validation**, without vulnerability severity. Their delivery priority is IMPORTANT: missing mandatory evidence blocks affected boundary readiness. One confirmed documentation advisory follows. No prior Security report is claimed closed.

### NV-01 — scenario topology and alternate ingress (delivery priority IMPORTANT)

**Evidence:** ADR lines 10/17/27 explicitly require a scenario map and leave compatibility unverified. Channel C2 still recommends private Gateway IAM and an HTTP v2 wrapper; C6/O4/O5 retain IAM-signing assumptions, now overridden. infra/template.yaml:47–58 provides only baseline HTTP JWT configuration, not the new scenario realization.

**Boundary/abuse hypothesis/impact:** A service or internet caller could reach an alternate default endpoint, or an “internal” public Regional API could be misrepresented as network-private. Conversely, choosing native PRIVATE REST plus Gateway mTLS would make the selected combination unbuildable. Neither deployed condition was observed.

**Minimal action:** DevOps + Implementation must enumerate each row in the scenario table below, label public authenticated versus network-private ingress accurately, specify TLS termination and every invocable alternate path, and disable/bind bypass paths. Prefer Regional mTLS HTTP/REST where that matches the scenario. If actual network-private ingress is mandatory, produce a concrete compatible TLS termination/identity-forwarding design; do not replace mTLS with IAM silently. Escalate to Coordinator only an irreducible architecture conflict, not routine API/authorizer choices.

**Retest/closure:** Reviewed route/domain/API-type matrix, parameterized IaC/static assertions rejecting default endpoint bypass and broad invoke permissions, plus later separately authorized handshake negatives (missing/untrusted/expired/disabled certificate, default hostname, direct function URL). Verify exact decoded ISO body and C4 header preservation for the selected adapter. Residual: DNS/certificate issuance/private connectivity are unexecuted deployment checks.

### NV-02 — token endpoint, certificate/client/mandate and C4 key binding (delivery priority IMPORTANT)

**Evidence:** ADR line 18 mandates separate assertion/token checks and binding; line 27 leaves issuer/provider/key custody unverified. C4 correctly requires DIRECT/RELAY sender identity and distinct Ed25519 signatures but leaves TLS/IAM mapping/key custody unresolved. A truststore CA chain alone identifies trusted certificates, not the permitted payment client/mandate.

**Boundary/abuse hypothesis/impact:** One registered service's certificate plus another client's bearer token, an ID token or client assertion misused at the API could cross acquiring scopes; treating transport authenticity as C4 evidence could fabricate processor-known confirmation. This is not reproduced.

**Minimal action:** Select a provider demonstrably supporting private_key_jwt. Publish its token endpoint, client registration/public JWKS, grant, permitted signing algorithms/kid and bounded assertion lifetime. Check iss/sub/client registration, exact agreed token-endpoint audience, signature, time claims and replay rejection (short-lived jti cache is a practical implementation choice, not an assertion that RFC makes jti mandatory). API must instead validate the issued access token's signature/issuer/API audience/expiry/scopes and registered client claim. Never accept the assertion as the API bearer.

Map trusted request-context certificate identity to OAuth client and environment/acquirer/merchant/terminal/route/role registry at the final authorization point. If RFC8705 binding is selected, validate cnf.x5t#S256 against the actual peer certificate; otherwise define an explicit equally checkable registry binding. Do not claim native HTTP JWT authorizer alone enforces it. Use authorizer/backend validation and a cache key/TTL that cannot reuse an allow across certificates, tokens, scopes or expiry/revocation.

Keep demo-user issuer/audience/subject distinct from service identity and from simulated payment issuer signing identity. C4 still verifies real Ed25519 signatures, exact instructionSha256/responseSha256, DIRECT or RELAY mandate, and atomic evidence admission; TLS/OAuth does not replace it. Reject spoofable certificate/identity headers; only trusted ingress context may supply those facts.

**Retest/closure:** Provider capability evidence; local negative vectors for assertion replay/wrong audience/algorithm/kid/expiry; token/client/certificate swap, wrong scope and cross-acquirer denial; demo JWT/ID token/assertion rejected as service access token; sender/signing-role swap, altered ISO bytes, forged or missing C4 sidecar rejected before confirmation. No rejection after possible-send may trigger resend or fabricate NOT_DISPATCHED. Residual: live provider issuance, key rotation and deployed context propagation unverified.

### NV-03 — WebSocket service authentication and lifecycle (delivery priority IMPORTANT)

**Evidence:** ADR line 19 requires separate compatibility review. C6 and O4/O5 assume IAM-signing + X-Demo-JWT, superseded for the new application-ingress direction; O5a/O6 already specify valuable epoch/generation/context/cursor bindings.

**Boundary/abuse hypothesis/impact:** A connected service may retain access after credentials/mandate expiry, subscribe to another context, or connect through an unchecked alternate route if HTTP authentication assumptions are copied to WebSocket. No deployed bypass is shown.

**Minimal action:** Define relay→WebSocket handshake and TLS termination, OAuth service access token plus separate demo token transport without URL tokens, $connect validation, and how mTLS identity is delivered over that path. Do not invent native HTTP JWT or mTLS support for WebSocket. Preserve application-level expiry/mandate checks for subscribe/query/fanout and explicit close/invalidation, because $connect authorizer is not per-message reauthentication. Retain new epoch on renewal, cursor scope/generation checks, loopback Host/Origin/CSRF controls, no token logging and observation-only reconnect. AWS IAM for notifier @connections is an AWS resource permission, not the rejected service-ingress replacement.

**Retest/closure:** Official feature mapping for the selected topology; missing either identity rejected at connect, expiry/revocation suppresses subsequent query/subscribe/fanout, stale epoch/cursor and foreign transaction denied, old callbacks ignored, failed WebSocket does not block otherwise-authorized explicit HTTP payment, and reconnect/heartbeat never sends payment. Residual: handshake/header propagation and callback behavior unexecuted.

### NV-04 — key custody, GitHub OIDC and Terraform state (delivery priority IMPORTANT)

**Evidence:** ADR lines 12/22/27 select GitHub OIDC/Secrets but leave custody/environment inputs unverified. No .github/workflows directory was present in this checkout inspection. No Terraform/OIDC implementation is certified by the baseline SAM excerpt.

**Boundary/abuse hypothesis/impact:** A workflow outside the intended repository/ref/environment could assume a broad AWS role, or assertion/TLS/C4 signing keys could enter state/build artifacts/browser bundles. Actual trust policy and key flow were not supplied; no secret leak is alleged.

**Minimal action:** DevOps supplies a configuration-only schema: Variables AWS_ACCOUNT_ID, AWS_REGION=us-east-2, AWS_ROLE_ARN, environment, issuer/token/JWKS URLs, OAuth client IDs/audiences/scopes, custom domains, truststore URI/version, and TF_STATE_BUCKET/KEY/REGION plus supported locking input. Values remain parameterized. Secrets hold private TLS/OAuth/C4 signing material only where required; public certs/JWKS are not private keys. Keep key roles separate, define load-to-runtime mechanism, access owner, rotation overlap, revocation and cleanup. OIDC provides AWS credentials, not application client keys. Do not generate private keys in Terraform or expose them through state/outputs/plan logs. If runtime injection cannot be implemented within selected GitHub storage, return the precise gap rather than assume a new secret service is approved.

Scope role trust to exact federated provider, aud=sts.amazonaws.com and actual verified repository subject/ref or environment format. An environment subject needs branch/tag/environment restrictions as well. Pin workflow actions, limit id-token:write to the intended job, keep untrusted PR code away from credential-bearing jobs, and scope AWS resource/PassRole/state permissions. State backend: private encrypted versioned S3, environment-separated keys, supported locking, explicit bootstrap; no secret-bearing plan/state upload as a public artifact. Author configuration locally; do not run remote pipeline/deployment here.

**Retest/closure:** Static trust/policy/workflow review with wrong repo/ref/environment/audience negative cases; secret-to-runtime data-flow inventory proving no private bytes in Git/artifacts/state/logs/browser; parameterized us-east-2 backend and runtime roles. Later owner-authorized OIDC assumption/rotation check remains unexecuted. Residual: repository subject format, role IDs, runtime secret delivery and backend provisioning unverified.

### NV-05 — one-week TTL without loss of no-resend/evidence identity (delivery priority IMPORTANT)

**Evidence:** ADR line 21 explicitly leaves record mapping unresolved. Observation O8 prohibits purging journal/evidence/heads/aliases and requires expiry ranges/revision/dedupe durability before payload deletion. AUTH AWS update retains the 24-hour generation policy; a seven-day history window does not redefine it.

**Boundary/abuse hypothesis/impact:** Uniform TTL could delete POSSIBLE_SEND/alias/evidence identity and allow an old financial instruction to be sent anew, or expose expired history while physical deletion lags. A replayed stream row could resurrect expired history. No such implementation is established.

**Minimal action:** Requirements + Domain specify record-by-record applicability: history payload seven-day logical expiry from original collection/commit; physical TTL only after required expiry-range/dedupe metadata is durable; query/notification filter expired payloads. Connection/subscription/cursor/assertion replay metadata uses its actual shorter validity horizon where appropriate, not an automatic week. Journal/evidence/generation-head/alias/issuer-instruction identities survive whenever required by no-resend, prior-reference and retained-evidence rules, retaining compact non-payload tombstones if suitable. Do not extend history from retry/read/callback. Preserve intentional 24-hour generation semantics; test exact instruction versus a genuinely new generation separately. A real semantic conflict needs domain resolution, not silently deleting all tables or adding owner business quotas.

**Retest/closure:** Reviewed record matrix and clock-driven local cases: just-before/after seven days with delayed physical deletion; late C4 evidence after payload expiry; duplicate/prior-reference request after expiry/restart; stream duplicate after TTL; cursor expiry-revision change; unresolved POSSIBLE_SEND identity retained; intended 24-hour new generation unchanged; no reconnect/notification/expiry worker financial dispatch. Residual: deletion timing is asynchronous and cannot be certified by local clock tests.

### AD-01 — historical contracts must remain explicitly overlaid (ADVISORY; confirmed documentation condition)

**Evidence:** Pinned requirements R19 and C2/C6/O4/O5 still describe IAM service ingress; O8 proposes numeric business limits. Latest ADR explicitly rejects these defaults. This is expected historical content, not proof of a deployed flaw.

**Impact/boundary:** An implementer reading a pinned contract without its overlay could construct the wrong service-auth mechanism or preserve unapproved business gates.

**Minimal mitigation:** Coordinator links this exact ADR precedence at each implementation handoff; Requirements authors a traceable revised contract/delta for changed authentication and expiry without rewriting sealed inputs. Remove obsolete business-budget prerequisites through the scoped change; retain protocol bounds, atomicity, idempotency, no-resend and AWS quotas.

**Retest:** Traceability identifies superseded clauses and replacement scenarios; no new implementation acceptance based on the old domain PASS. Status: OPEN advisory. Residual: downstream documents need routine harmonization, not renewed owner approval of unchanged selected direction.

## Required scenario realization and acceptance rows

These are concrete team actions, not newly approved architecture or executed tests. Each row must include API type, actual network exposure, TLS peer/termination, trusted identity source, issuer/token endpoint, API audience/scopes, mandate and expected deny outcome.

| Scenario | Minimal realization/check |
| --- | --- |
| Browser → presenter relay | Loopback only; Host/Origin/CSRF + opaque session; no browser service keys; validated single demo subject |
| Relay → console HTTP POST/read | mTLS service path + issued OAuth access token + separate demo-user context; route and business-context authorization; disable alternative ingress |
| ACL → processor ISO C2 | Choose Regional HTTP/REST mTLS realization or explicitly compatible private topology; exact binary adapter/body limit; no claim that private REST natively supports mTLS |
| Processor → network → issuer | Distinct client/role/mandate per hop; no raw public issuer truth; do not assume Lambda SDK invocation supplies TLS/OAuth application identity |
| DIRECT/RELAY and late C4 admission | Trusted sender binding + independent Ed25519 proof/hashes/role + atomic journal/event admission, no unsigned confirmation |
| Relay → WebSocket and HTTP catch-up | Verified WS-specific TLS/OAuth realization + single demo subject; $connect, expiry/fanout/subscription/cursor checks; reconnect observation-only |
| Notifier → Gateway callback / data stores | Least-privilege AWS resource IAM; no financial-send capability; sanitized source pointers and expired-event suppression |
| GitHub → AWS/state | Exact OIDC trust subject/audience/job/environment, us-east-2, scoped state/artifact/key flow; no remote execution yet |

## Commands, actual results and exclusions

- read tools: role/shared documents, repository/work-item/contract/ADR/binding sources and bounded baseline infrastructure excerpt; completed as documented above.
- rg --files: unavailable (rg not installed); used find/grep as fallback. find .github/workflows: directory absent, not a clean scan result.
- wc -l / nl -ba / grep -nE / find: document sizing, ADR line references and bounded file/config discovery only.
- sha256sum docs/ai-sdlc/work-items/WI-ACQ-OBS-001/{adr-operation-a-access-and-environment,owner-decisions,requirements,channel-contract,observation-contract,domain-review}.md: six identities MATCH.
- Trusted reviewer Python stdlib pathlib/hashlib/json script: recorded 16 input identities in evidence/input-identities.json. No imported target code or fixture evaluation.
- web_fetch/web_search: public official AWS/GitHub/RFC documentation only; URLs/status/truncation/excerpts recorded beside this report. No cloud API calls.
- No test execution, target-controlled process, service start, network probing, Git/gh/provider action, key retrieval, login, deployment or external send.
- DevOps SAST/DAST/dependency/secret scans, tool versions/coverage, SBOM/artifact integrity, actual Terraform/CI validation and deployed authorizer/TLS behavior: NOT supplied or executed for this new delta. Early design review may proceed without scans; implementation acceptance cannot.
- All main/src/infra content and others' review/evidence remained read-only. Current T1 traceability was being produced; this report does not attest its final whole-tree source/test coverage. Final input validation detected a T1 BINDING.txt append only: re-read evidence retained the same branch/HEAD/remote and reused-suite result, adding utc_close=2026-11-20T23:59:17Z and a Coordinator plan-document update. The six commissioned design identities stayed unchanged. Opening and closing binding hashes are retained separately in review-validation.json; this expected concurrent evidence append is not concealed as a stable whole-tree freeze.
- Configured MCP is incomplete (inherited tools omitted by policy). No MCP-backed work succeeded or is claimed. Direct file inspection and public web documentation succeeded.

## Exact next team task and exit condition

Coordinator commissions **DevOps + Implementation scenario design**, not cloud execution: fill ADR scenario rows using NV-01–04, select a demonstrably compatible private_key_jwt issuer and WebSocket/TLS realization, and supply configuration/key-flow/OIDC/state schemas. Implementation performs every Git/provider operation through its approved OpenCode ACP route and returns fresh binding/source identities. Requirements + acquiring-domain supply NV-05 record-expiry/no-resend delta and refreshed contract traceability. These are routine work within Fernando's selected direction; no new owner approval question is needed unless an actual incompatible mandatory topology/material choice remains.

Then commission an independent Security **design re-review** against exact changed artifacts. Closure of missing design evidence does not grant final implementation PASS: implementation review later requires exact source/artifact/CI revisions, applicable scan evidence and independently executed scoped checks. QA, Security implementation review and human acceptance remain separate and pending. Only the designated human may accept residual risks, with owner/date/finding/revision recorded; this review waives none.
