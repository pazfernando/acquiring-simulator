# Deployment inputs — what is blocked and who can supply it

Prepared 2026-11-23. The infrastructure, contracts and code are authored; `terraform plan` cannot succeed until the fail-closed inputs below exist. Each row states whether the value can be derived by the team or must come from Fernando.

## Owner-only inputs (cannot be derived or invented)

| Input | Why only the owner | Recommendation |
| --- | --- | --- |
| AWS account ID (12 digits) and confirmation it is a single account | Billing, blast radius and OIDC trust belong to the account owner | Reuse the account already used for the earlier simulator work; confirm which one |
| Base domain and DNS hosted zone (with delegation authority for the four records) | Domain ownership is a legal/administrative asset | Any domain Fernando controls; if none is dedicated, a subdomain of an existing one |
| Alarm notification destination (email or topic) | Contact data is personal | A mailbox Fernando is willing to receive infrastructure alarms on |
| Named approver for the apply, and explicit cost acceptance | Release authority and spend | Approver: Fernando. Cost statement comes from DevOps before the apply |
| Environment name | Appears in the realm name, OIDC subject and state keys | `demo` |

## Derivable by the team (no owner input needed, listed for transparency)

- The five FQDNs as subdomains of the chosen base domain: `console.`, `auth.`, `ws.`, `api.`, plus the two mTLS service domains.
- ACM certificate issuance and DNS validation once the zone exists; console/viewer certificate in `us-east-1`, the rest in `us-east-2`.
- Terraform state bucket, key prefix and KMS key: created by the bootstrap root, names derived from the environment.
- GitHub OIDC subject for this repository and environment.
- Audiences for console, ACL, simulator and socket, and the single demo subject identifier.
- Keycloak image digest: a pinned official release digest, chosen and recorded by DevOps.
- mTLS truststore contents: a simulator-owned CA generated and custodied per the design.
- Lambda artifact digests: produced by the CI build, never hand-entered.
- Realm import: authored after the D-5 issuance matrix is confirmed on a running issuer.

## Consequence

Nothing else is missing from the code, contracts or infrastructure. Deployment becomes executable when the five owner-only values above exist, the remaining independent reviews on the H1/K1 cuts close, and the G3 gate records the approver and cost statement.
