# Owner decisions — WI-ACQ-OBS-001

## D1 — Private profile and HTTPS boundary: APPROVED

- Owner: Fernando Paz.
- Date: 2026-11-18, America/Bogota.
- Evidence: coordinator asked whether to adopt SIM-ISO87A-v1 based on jPOS as the private ISO8583 profile between the channel anti-corruption layer and simulator over HTTPS, explicitly excluding construction and real-network compatibility approval. Fernando answered: "ok de acuerdo, continua".
- Reviewed selection bundle: domain-review.md MD5 355fb90c0d48266c53e41b23ef7526af; iso8583-profile.md MD5 30137e38553cce26b16e6d26bfa8d02c; channel-contract.md MD5 bf8f7fa281b4849f727bbc41466b6d64; decision-proposal.md MD5 c7cae7b0791e76706966f65812389fdc.
- Scope: authorization-only private wire baseline and HTTPS boundary, including reviewed supplements P2a/P3a/P5/P6 and C3; common instrumentation remains required for every implemented/future capability, with future operation profiles separately specified.
- Not approved by this answer: D2 failure/admission policy, D3 observation/access/storage/retention architecture, production readiness, cryptographic implementation, Security/QA, deployment or construction.
- Technical corrections C1–C3 remain team work and require independent review of new revisions. Selecting the family/boundary does not self-approve those corrections.

## D2 — Durable trace admission and failure policy
PENDING; present as one clear question after technical harmonization and review.

## D3 — Observation architecture and access/retention
PENDING; separate subsequent decision, not inherited from D1.

## D2 response and asynchronous-export qualification

Fernando answered "ok de acuerdo" to D2, immediately specifying asynchronous trace delivery and requesting ADOT/CloudWatch/DynamoDB research supported by Context7. Record this qualification together with the assent: no automatic payment resend/reversal remains accepted direction; do not infer approval of a synchronous separate trace-store dependency. Proposed reconciliation is in adot-observability-research.md; adoption of that amendment and detailed D3 remain pending. No construction approval.

## D2 amendment — asynchronous telemetry and console projection: APPROVED

- Owner: Fernando Paz; date 2026-11-18 America/Bogota.
- Evidence: coordinator asked: "¿Aprobamos esta separación, dejando el envío de telemetría y la actualización del historial asíncronos, sin depender de ellos para procesar el pago?" Fernando answered "ok".
- Adopted direction: technical telemetry exports asynchronously (ADOT/OTel); functional history is an asynchronous projection, not a copy of all CloudWatch logs. Neither telemetry backend nor console projection availability by itself blocks payment processing. Required authoritative business persistence remains on the transaction path, including critical pending notifications committed atomically with the business change as proposed in adot-observability-research.md (MD5 848a73f6194e4d912c9eabc26f429bfa).
- Proposed realization explained to owner: business journal/pending events -> DynamoDB Streams -> idempotent projection Lambda -> history table -> HTML. This establishes the selected direction; exact recovery/capacity contracts, runtime compatibility, IAM, retention and full D3 architecture still need specification/review/owner disposition.
- Supersedes the earlier separate synchronous trace-store admission prerequisite in O3/O3a for this delta. Does not remove mandatory business claim/evidence persistence, change payment outcomes or authorize automatic resend/reversal after possible send.
- Not approval of construction, deployment, independent Security/QA, cryptographic success or unreviewed detailed architecture. Update contracts/scenarios and independently review this changed revision before downstream gates. Earlier D2 pending/qualification entries above remain historical.

## D3 revision — console without users and WebSocket direction, 2026-11-19

Owner: Fernando Paz. Literal corrections: "acceso restringido no porque no vamos a manejar usuarios, solo es una interfaz para enviar transacciones y que sea mas visible a un cliente"; "lo de ADOT estoy de acuerdo". After the coordinator proposed one queryable DynamoDB event store, Streams for notification rather than duplicate history projection, API Gateway/Lambda reads and API Gateway WebSocket push/reconnect catch-up, owner replied: "ok probemos, haz un acticity diagram de este flujo y continua".

Accepted direction for continued specification: no console users/login/roles; async ADOT remains accepted; one queryable functional event store, Streams-triggered notifications, WebSocket live delivery, API catch-up. This is not blanket architecture/construction/deployment approval or approval to remove existing service IAM/JWT controls without a reviewed replacement boundary. Seven-day retention and prior numeric capacity/SLO proposals have not been individually approved. Previous restricted-console/polling D3 question was not accepted.

Repository evidence resolves the coordinator's earlier PostgreSQL conditional: WI-ACQ-AUTH-001/aws-architecture-update.md selects DynamoDB business persistence for AWS; PostgreSQL/SQLite are local regression harnesses. Candidate now uses atomic DynamoDB business transition plus sanitized critical event, with the event itself queryable. No second duplicate history projection/outbox payload is required. Exact delivery/replay markers, retention, indexes and no-login boundary remain specification/review work. No changes to payment resend/reversal prohibition or evidence authority.

## D3 subsequent steering — single demo user JWT and Drive diagram, 2026-11-19

Fernando: "ese diagrama deberia estar en la documentacion del drive y podriamos mantener lo de jwt pweo con un usuario unico, ademas segun entiendo desde nuestra capa anticorruption hasta el switch, al hablar iso 8583 no hay autenticacion.. es IP based?"

Supersedes the preceding no-login direction: retain JWT with a single demo user/principal; no multi-user management. JWT expiry/issuance and API/WebSocket authentication require specification, not a hard-coded permanent token in HTML. Explicit authority to publish the activity diagram in the product's existing Drive documentation, without broader publication/deployment authority. The ISO/IP assertion is a question, NOT approval to remove service authentication or change D1 HTTPS to raw TCP.

## D3 exposure and retention dispositions — 2026-11-19

Owner: Fernando Paz. Exposure question: whether the client would open the console in their own browser or Fernando would present by screen-sharing. Answer: "yo se la muestro". Selected presenter-operated demonstration with screen-sharing, single demo JWT principal retained; no direct client access or remote-public hosting commissioned. This does not itself choose or authorize deployment of backend resources.

Separate retention question: "¿Conservamos el historial de transacciones durante siete días?" Answer: "si". Functional history retention of seven days APPROVED. This applies to viewing/reviewing previous demonstrations, not automatic payment re-execution. Contract details retain original-event-time expiry with explicit truncated history; required business/evidence/identity references are not permission to delete financial records after seven days. Numeric capacity/SLO proposals, full architecture/construction, deployments, QA/Security and runtime compatibility are not approved by this answer.

Reviewed candidate: domain-review.md MD5 295c1a3601dd099f172a40d27b73e4b3; review-manifest.json MD5 71f3d3247a7847cbc673c0657a7c4059. PASS is specification corrections only. Earlier exposure/retention pending statements remain historical.

## Architecture approval and technical-preflight commission — 2026-11-19

Owner: Fernando Paz. Exact instruction: "Apruebo esta arquitectura con las decisiones acordadas. Continúa con las comprobaciones técnicas previas a implementar."

Architecture APPROVED for Operation A only with the recorded owner decisions: private SIM-ISO87A-v1 over HTTPS, single demo JWT principal and separate service IAM, presenter-operated screen-share, one queryable DynamoDB functional event source with atomic business/critical-event persistence, asynchronous Streams/WebSocket notification and HTTP catch-up, asynchronous ADOT, seven-day functional history. Existing payment semantics, idempotency and no automatic payment redispatch/reversal remain. No B/C, live payment networks, multi-user administration or direct client/public hosting.

This approval supersedes historical full-architecture-PENDING labels for this agreed design. It does not invent approval of proposed numeric capacities/SLOs/cost ceilings; validate and report those separately. Current authorization is technical preflight BEFORE implementation, not implementation, cloud resource creation, deployment, new login/credential flows, push/PR/merge, independent Security/QA approval or acceptance. Existing current-version publication authority does not extend to this new work.

Preflight must verify actual runtime/default model, checkout/branch/HEAD and existing changes; inspect supported provider capabilities without exposing credentials; assess JWT/provider, IAM/WebSocket authentication/callbacks, Java25/ADOT, ISO codec/contract feasibility, atomic event append/cursor/expiry, reconnect/no-financial-replay and bounded capacity/cost. Run only safe local/offline checks or existing authenticated read-only inspections; no provision/deploy/send or production code changes. Missing credentials/access are explicit unverified dependencies, not reasons to initiate login or bypass controls.

Approved candidate identities (owner-decisions.md is the authority overlay for historical pending labels):
- requirements.md: SHA-256 49c5d412e3705c320a2f78b190555f49e7e0fa4662cff9329d259f89e2b459df
- acceptance-scenarios.md: SHA-256 1586898418c09dcf092d567a4155b4524a0bc34500bf4ea76ce8f76465aa7733
- iso8583-profile.md: SHA-256 de02c533536dcb6e87e4c6e93357efb9efa60c085a59f6f63bbfbb8c0e1fe481
- channel-contract.md: SHA-256 495e1172e76ab9ba81f8a8026e7efec25baa6e4fff0d754c0a4c92bd38ea3bca
- observation-contract.md: SHA-256 ca53e384a46d24b3c1119b3e6ba804a9a9d4caac049a6b0c55a40c72842a205a
- decision-proposal.md: SHA-256 becbabcdf9038c16bf5a0040051ea606db556d22bba44ddf2321b8f3fe219166
- activity-flow.md: SHA-256 7f09731a50190b6295233f965a312afaf308daefa291b5c9cc4125edfb3c67bd
- activity-flow.mmd: SHA-256 e2fbb1aab2845ef7197c02c2912c158a83a79a3f4454d859d62eb51c388393ce
- domain-review.md: SHA-256 73adba20fd70969bf8bb9f23ebde5c5d16aec69fd4694ac32984008b76ee0ca2
- vectors/review-manifest.json: SHA-256 dac6fc14fa5919c6d5b1d89730252424d08edecad5c7b0f05d9dfcc6eb5082dc

Independent Security and QA remain unavailable/pending; preflight is not their replacement.

## Simulator access, on-demand retention, GitHub environment and Security — 2026-11-20

Owner: Fernando Paz, direct Coordinator conversation. The owner selected external/internal API Gateway as appropriate, mTLS + OAuth 2.0 (`private_key_jwt`), requiring a scenario ADR; deferred business capacity/cost limits in favor of on-demand/serverless and one-week TTL where applicable; selected GitHub variables/Secrets with OIDC and `us-east-2`; and identified the existing ai-sdlc-security agent as reviewer. See [the decision ADR](adr-operation-a-access-and-environment.md). This overrides previous conflicting service-ingress recommendations and unavailable-reviewer statements, not the independent review requirement or deployment gates. Detailed compatibility is not yet verified; no deployment or static-credential flow is approved here.

## WebSocket transport assurance and identity provider — 2026-11-20 (evening)

Owner: Fernando Paz, direct Coordinator conversation after the independent early Security review and the DevOps access proposal (`design/access-realization/design.md`, SHA-256 `83e4ea14b3959dcc6d8535aee0f0dd715783764e8922a72b6c0c3454a40829f4`).

**D-D — WebSocket transport assurance: ACCEPTED as proposed.** Exact instruction: "si lo acepto". The owner accepts that the WebSocket notification path carries no mTLS, because AWS documents no native mTLS for WebSocket APIs, and accepts instead: OAuth access token at `$connect`, per-frame revalidation, lease of min(JWT expiry, 30 minutes), and server-side revocation closing the connection at the next frame or heartbeat. Scope limits that remain mandatory: this boundary may only deliver notifications and catch-up; it must never send, retry, reverse or re-price a payment, and losing the WebSocket must not block an otherwise authorized HTTP payment. The residual risk is recorded, not removed, and does not close Security NV-03; Security still reviews the realized path.

**D-A — OAuth/OIDC issuer: NOT YET RESOLVED.** The owner proposed a self-managed Keycloak, or Cognito in its basic tier, preferring no paid third-party SaaS. The Coordinator corrected one factual premise before recording a decision: the AWS Cognito limitation is a capability limit, not a pricing-tier limit. Cognito user pools and app clients support only `client_secret_basic` and `client_secret_post` for confidential-client authentication; `private_key_jwt` is not supported at any tier, so Cognito cannot implement the owner-approved `private_key_jwt` contract. The viable options are therefore self-managed Keycloak (compatible; adds an always-running container plus a database, unlike the on-demand serverless remainder) or a managed `private_key_jwt`-capable tenant in its free tier. No issuer is approved until the owner selects one; this record does not authorize provisioning, deployment, a new credential flow or a new external dependency.

## D-A resolved: self-managed Keycloak — 2026-11-20 (evening)

Owner: Fernando Paz, direct Coordinator conversation. Exact instruction: "autogestionado". The OAuth 2.0 / OIDC issuer for the approved `private_key_jwt` client authentication is therefore a **self-managed Keycloak**, not a managed third-party tenant and not Cognito (unsupported: only `client_secret_basic`/`client_secret_post` at every tier).

Consequences accepted by this selection, recorded so they are not discovered later as surprises:
- Keycloak requires an always-running compute component and a database, unlike the on-demand serverless remainder; this is a recurring cost and an operational surface that must be designed, secured, backed up and patched.
- Keycloak is not part of the approved architecture's original deployment description; adding it is an infrastructure component introduced by this decision, and defining its hosting, network placement, TLS, realm/client configuration, key custody and rotation remains a design task for DevOps, with a separate authorization before any provisioning.
- The demo-user JWT, the OAuth service tokens and the C4 Ed25519 evidence key remain separate planes; Keycloak does not replace the C4 evidence key and its tokens do not authorize an evidence signature.

No provisioning, deployment, credential creation, realm export or login flow is authorized by this record.

## CA-1 resolved: late evidence stays internal — 2026-11-20 (evening)

Owner: Fernando Paz, direct Coordinator conversation. Exact instruction: "si lo confirmo", after the Coordinator explained late evidence as the processor's signed proof arriving after the approved 30-second deadline, which corrects the recorded outcome without resending or reversing the payment.

Decision: late evidence is admitted **only through the internal path**. No public late-evidence endpoint is created. The approved rule in `observation-contract.md` ("Late evidence uses same internal admission; no public late endpoint/automatic financial recovery") therefore stands unchanged, and the DevOps proposal's scenario S5 public authenticated route must be withdrawn in its next revision.

Consequences that remain mandatory: the simulated processor/issuer components that can deliver late evidence run inside the AWS account and reach admission through an internal invoke or private path; no internet client, relay or browser can submit late evidence; proof still requires the existing Ed25519 signature, sender binding and business-version correlation before it is admitted; admission remains atomic with the business and critical event; a late result never authorizes a resend, retry or reversal. Owner instruction does not authorize provisioning, deployment, a public route elsewhere, or relaxation of any verification rule.

## Review execution model — Security re-review of access design rev2, 2026-11-22

Owner: Fernando Paz. Exact instruction: "debe ir con gtp 6.1 sol y si falla como failback el de deepsek".

Decision: the independent Security re-review of `design-rev2.md` and `compatibility-assessment-rev2.md` runs on **`openai/gpt-6.1-sol`**, the model on which the earlier successful Security review executed. Two prior attempts on the agent's resolved default (`nan/glm5.3-flash`) ended in provider LLM timeouts and wrote nothing. If the gpt-6.1-sol attempt fails, the Coordinator may retry once on `nan/deepseek-v4-flash` as the owner-approved fallback.

Scope of this decision: execution model for this review only. It is an explicit owner-authorized model selection recorded because the standing rule forbids the Coordinator from substituting models on its own; it does not change any agent's configured default, does not authorize model changes for other reviews, and does not imply any security verdict. A review produced under the fallback must record which model executed it, and any difference in depth must be visible in the verdict.

## K-1 resolved: one Keycloak instance — 2026-11-22

Owner: Fernando Paz, direct Coordinator conversation. Exact instruction: "solo una ya que es un simulador". The self-managed Keycloak runs as a single task. Consequence accepted: an outage or restart of that task blocks new token issuance, and therefore new authentications and new payment initiations, until it recovers; access tokens already issued remain valid until their 900-second expiry. Existing sessions that already hold a valid token keep working within that window. No high-availability pair, no multi-AZ failover and no availability SLA are commissioned.

K-2 (operations ownership) and K-3 (presenter-local relay key) remain OPEN; the Coordinator is explaining them in plain language before they are recorded.

## K-2 and K-3 resolved; presentation moves to the cloud — 2026-11-22

Owner: Fernando Paz, direct Coordinator conversation.

**K-2 (operations ownership) — resolved as "no maintenance commitment".** Exact instruction: "no vamos a entrar en eso del mantenimiento, recuerda que es un simulador, asi que es una infraestructura efimera que podemos reproducirla y en algun momento sera con una nueva version de keycloak". The Keycloak deployment is treated as disposable infrastructure: no on-call, no SLA, no long-lived patching obligation. The accepted operating model is rebuild-and-redeploy, including migrating to a newer Keycloak release when needed, rather than in-place maintenance. Backups are therefore not a recovery promise; the recovery path is recreation from configuration. This does not authorize deletion of anything now, does not remove the obligation to keep secrets out of state and logs, and does not close any Security finding.

**K-3 (presenter-local relay key) — SUPERSEDED, not merely declined.** Exact instruction: "la presentacion es en la nube nada local... local solo es parace hacer pruebas tdd y bbd... no necesitamos ningun relay". The presenter-local relay is removed from the design entirely. Local execution is limited to tests (TDD/BDD) and the database used by those tests. The presentation runs in the cloud.

Consequences that follow, stated so they are not discovered later:
- The earlier decision that placed the presenter demo on a local screen share with a loopback relay is reversed for presentation. The question of a remotely reachable console, previously deferred as "later separately reviewed", is now the selected direction and must be designed and independently reviewed on its own terms.
- With no relay, the browser cannot obtain a service identity from a local component. The console therefore has to authenticate the browser directly with the approved single demo-user JWT, and the mTLS plus OAuth service identity remains only where it was approved for service-to-service hops (relay replacement aside: console to ACL, ACL to processor, evidence admission).
- No browser-held service secret is permitted, which is a constraint on the design, not a preference.
- The reachability of the hosted presentation is NOT yet decided: whether it is publicly reachable on the internet with the demo login, or restricted by network or origin controls, is an open owner decision recorded below as P-1.
- The service principal previously bound to the relay (`svc-console-relay`, which held `console.write`) loses its consumer and must be removed or re-scoped by the next design revision.

## P-1 — reachability of the cloud-hosted presentation: RESOLVED 2026-11-22

Exact instruction: "lo primero", choosing the first option put to him: **publicly reachable from the internet with the single demo-user login as the only gate, plus attempt rate limiting.** Accepted consequence: anyone who obtains the demo credential can reach the console and observe the synthetic history of that demo context; the token is the only gate, so its protection, expiry, rate limiting and the absence of any browser-side service secret become the load-bearing controls. Restricted network or origin allowlisting is NOT adopted. This authorizes design of a public console surface, not deployment.

Superseded open question recorded for trace: The options to put to him are: publicly reachable with the single demo-user login as the only gate, or reachable only from a restricted origin or network while still hosted in AWS. This decision determines the Security review scope for the console surface and must be resolved before the access design is finalized. No public hosting is authorized by this record.

## Atomic runtime wiring authorized — 2026-11-22

Owner: Fernando Paz, direct Coordinator conversation. Exact instruction: "ok continua", after the Coordinator explained that the new claim/dispatch/result/late-evidence/event-store machinery is written and tested but the AWS runtime still builds the legacy service, and asked whether to wire it and make it the active local path.

Authorization: Implementation may wire the atomic gateways into `AwsRuntime` and make the atomic path the one that serves locally, with focused tests, under these limits:
- Local construction and local test execution only. No AWS call, no deployment, no pipeline run, no credential, no Git write, no publication.
- Existing approved semantics must be preserved exactly: single claim per attempt, no fallback to the legacy claim, send permitted only on a committed dispatch intent, REPLAY and RECOVERED are knowledge and never a resend, atomic event composition with business and critical event, evidence ordering and the 30-second deadline unchanged, late admission without resend or reversal.
- No invented capacity, quota, slot count or cost limit; whatever capacity input the wiring needs must be explicit and reported rather than chosen silently.
- Failures must be preserved as evidence, not retried into a green result.
- If a prerequisite turns out to be missing, report the exact gap instead of activating a partial path.

This authorizes the wiring and local activation only. It does not authorize deployment, a public endpoint, credentials, or any change to the approved architecture, and it does not close any Security finding.

## N-02 closure (Option B) and normative adoption — 2026-11-23

Owner: Fernando Paz, direct Coordinator conversation. Exact instruction: "adelante con lo recomendado", answering a two-part question that named the recommended option for each part.

**Decision 1 — closure of Security finding N-02 (edge rate-bound bypass).** The console API will be protected by a CloudFront **origin-verification header**: CloudFront adds a high-entropy value to the origin request and the console API boundary rejects any request whose header is absent or mismatched, failing closed. Mandatory conditions carried from the independent Security review, all binding: (1) the check is specified in the contracts before construction, as a bounded contract delta; (2) the value is Terraform-generated, exists only in encrypted remote state, never in tfvars, CI variables, artifacts or any log format, with a policy assertion; (3) the header name avoids the CloudFront custom-header denylist and any header the application treats semantically; (4) denials on the direct path are logged and observable. Options not selected: converting the console API to REST with a regional WAF (structurally stronger but forces application-owned token validation at the authentication boundary), and accepting the residual (would void the edge rate bound as an effective control).

**Decision 2 — normative adoption of the reviewed contracts.** The candidate packet `design/contract-deltas/continuation-rev3-20261123/` (OUTPUTS.SHA256SUMS `2000bb004e68e6d2974bef3067baeae6774f1922a21dca4ea18fecf6944ab467`), as amended by the bounded correction `design/contract-deltas/continuation-rev3-dr301-20261123/` (`old-prior-fixtures.json` `1d3f1406b05d95f87e18f37b33eea3bb855ad5c80488f92891dcc132b5f3d5f5`, supersession note `72ba032f5e1264abec1077d238e6c5c943f489a65bc7cd437519052769fd8b17`), is adopted as the normative product contract revision for Operation A scope. Evidence of review: domain review `reviews/continuation-rev3-domain-20261123/domain-review.md` `154b6346544bfb760bb6bf1c5cefd63dcc6f09ab064151a7165972007b67c2e4` (CONDITIONAL, DR2-01/03 closed, DR2-02 partial) and bounded re-review `reviews/continuation-rev3-dr301-domain-20261123/domain-review.md` `df5ad1a5979888387ea4b6b25813ee8cd5c2c9643664d505d29894199b70c5d0` (PASS, DR3-01 CLOSED).

**What this adoption does not do.** It does not waive or close the independent Security verdict of FAIL on `reviews/continuation-rev3-security-20261123/security-review.md` (`f5696ed917f009c7569cccbba5f42b431170d15a53ef06bdd38c10e61d53ba50`); that verdict clears only when N-02 is closed by the selected option and re-reviewed. It does not close SR2-01/02, NV-05/AD-01, the D-5 issuer evidence list or the public-surface evidence list. It does not authorize deployment: concrete destination inputs (account, environment, domains, certificates, state backend and custody, artifact and image digests, OIDC subject, cost statement, named approver) and the remaining verification gates are still required. It authorizes construction of the bounded slices defined in `construction-handoff.md` (H1 read-only history and K1 authentication seams) under those gates.
