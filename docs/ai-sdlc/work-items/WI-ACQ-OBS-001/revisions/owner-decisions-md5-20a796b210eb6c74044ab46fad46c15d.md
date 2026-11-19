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
