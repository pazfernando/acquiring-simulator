# ADR — Operation A simulator access and environment

Date: 2026-11-20. Decision owner: Fernando Paz.
Status: owner-selected architecture direction; detailed scenario mapping and independent Security review pending. Not deployment approval.

## Context and decision

Fernando supplied the following dispositions in the direct Coordinator conversation on 2026-11-20. These supersede the completion plan's earlier D1–D4 recommendations and its blanket wait for all four inputs.

1. Use external and internal API Gateway boundaries as appropriate to each scenario, with mTLS and OAuth 2.0 using `private_key_jwt`. The previous recommendation to select IAM/SigV4 for application service ingress is not adopted. Record each scenario and its rationale in this ADR before constructing its affected boundary.
2. Do not require business capacity quotas or a monthly spending ceiling now. Use on-demand/serverless billing where supported. Retain data for one week through DynamoDB TTL and equivalent expiry where applicable. The previously proposed 8/100000/20000/50/10 values and USD 50 budget are NOT approved defaults or prerequisites.
3. AWS account configuration comes from GitHub variables; use GitHub Actions OIDC and region `us-east-2`. Keep ordinary configuration in GitHub variables and sensitive values in GitHub Secrets. Do not store long-lived AWS access keys: the authorized design is OIDC role assumption. Exact identifiers and secret values remain deployment inputs, not prerequisites for parameterized local construction.
4. The existing `ai-sdlc-security` specialist owns independent Security review. The prior statement that no Security specialist exists is superseded. Availability must be checked when commissioning; review has not passed merely because the agent exists.

## Required implementation detail and evidence

- Map each external/internal caller and API scenario to its Gateway API type, exposure/network route, TLS termination, trusted caller identity, OAuth issuer/token endpoint, allowed audience/scope and role/mandate. Verify supported feature combinations; do not silently substitute a different access mechanism.
- `private_key_jwt` authenticates an OAuth client at the token endpoint; it is not itself the API access token. Document client assertion validation and access-token validation separately, plus how certificate identity, OAuth client and payment mandate are bound. C4 evidence signatures remain a separate control.
- Keep the single-demo-user login separate from service authentication. WebSocket support must be checked separately rather than assumed identical to HTTP.
- Do not remove protocol payload bounds, validation, atomicity, idempotency, no-resend rules or AWS service quotas. On-demand is billing, not unlimited capacity or zero cost. Replace obsolete proposed business-budget prerequisites through a traceable contract/configuration change and appropriate review, not by weakening runtime safeguards silently.
- DynamoDB TTL deletion is asynchronous. Enforce seven-day logical history expiry on reads; map TTL to each applicable record type and equivalent store. Before expiring business/evidence/identity records, preserve duplicate/no-resend and retained-evidence guarantees; resolve actual semantic conflicts through Requirements and independent domain review. Do not assume every table can be expired identically.
- Define GitHub variable/secret names, environment scoping, least-privilege OIDC trust and S3 Terraform backend inputs without publishing secret values. Region default is `us-east-2`, not the previously recommended `us-east-1`.
- Security reviews the concrete access/key design independently; QA reviews application behavior. Neither is replaced by developer tests.

## Execution status

The architecture direction above is selected. API-type compatibility, concrete OAuth provider selection/configuration, certificate/key custody, TTL record mapping and actual deployment remain unverified. Specialists own routine design and checks; ask Fernando only if a genuine unsupported combination or material choice cannot be resolved within this direction. Existing local application/Terraform/CI authority continues. No push, remote pipeline run, credential flow or deployment is authorized by this ADR.
