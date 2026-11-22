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
