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
