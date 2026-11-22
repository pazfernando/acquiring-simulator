# Access and environment configuration schema (configuration-only)

Status: **PROPOSAL / schema only**. No values are deployed or applied. Every identifier below is a parameter or a placeholder; no account ID, tenant, ARN, domain, thumbprint, key or secret value is invented. Companion to [design.md](design.md).

Regions: `us-east-2` default (owner direction). Environment names in examples are illustrative only (`dev`); a real environment name is a deployment input.

## 1. Rules the schema enforces

1. **Nothing secret is authored in Terraform.** No `aws_ssm_parameter` resource carries key material, no `output` exposes a private key, no `local_file` writes one, and no `data "aws_ssm_parameter"` with `with_decryption = true` is read into configuration (that would place plaintext in state).
2. **Terraform state never contains secrets.** State is the only artifact Terraform writes; it must hold resource identifiers only.
3. **CI never reads a private key into an artifact.** If the GitHub-Secrets path is used at all, the key travels only as a masked environment variable into a single step that writes it to the parameter store; `set -x`, `echo`, job summaries and artifact upload are forbidden in that step.
4. **Runtime processes read secrets from the parameter store at cold start**, cache them in memory, and never log or return them.
5. **Public material is configuration, not a secret**: JWKS documents, certificate bodies, truststore bundles, key ids, client ids, audiences, scopes.
6. **Region is fixed by variable**, not hard-coded, and defaults to `us-east-2`; the state bucket and all resources live in the same region.
7. **Separate state per environment**, never a shared key.

## 2. GitHub Variables (non-secret)

Repository-level by default; environment-level overrides where an environment differs. Names are proposals.

| Variable | Scope | Format / allowed values | Consumer | Notes |
| --- | --- | --- | --- | --- |
| `AWS_REGION` | repo | `us-east-2` (default) | all jobs | not a secret; changing it changes the deployment target |
| `AWS_ACCOUNT_ID` | repo | 12 digits, parameterized | OIDC role ARN construction, backend `allowed_account_ids` | never assume a value |
| `AWS_ROLE_ARN` | env | `arn:aws:iam::<account>:role/<role>` | `aws-actions/configure-aws-credentials` | per environment |
| `ENVIRONMENT` | env | `[a-z0-9-]{2,16}` | naming, registry, `env` claims | one value per environment |
| `TF_STATE_BUCKET` | env | bucket name | backend | created by bootstrap, not by this stack |
| `TF_STATE_KEY` | env | `acq-sim/<env>/network/terraform.tfstate` style path | backend | one key per stack per environment |
| `TF_STATE_LOCK` | env | `true` | backend `use_lockfile` | S3 native locking; see §7 |
| `TF_VERSION` | repo | e.g. `1.10.x` | CI | must support `use_lockfile` |
| `OIDC_ISSUER_URL` | env | `https://<tenant>/` | authorizers, JWKS discovery | trailing-slash form depends on provider |
| `OIDC_TOKEN_URL` | env | `https://<tenant>/oauth/token` | clients (assertion `aud`) | exact string, provider-specific |
| `OIDC_JWKS_URI` | env | `https://<tenant>/.well-known/jwks.json` | authorizers | |
| `OAUTH_CLIENT_ID_<PURPOSE>` | env | opaque id | `svc-console-relay`, `svc-console-ws`, `svc-acl`, `svc-evidence`, `svc-evidence-diagnostic` | one variable per client |
| `OAUTH_AUDIENCE_CONSOLE` | env | resource-server identifier URL | relay/client requests, authorizer `audience` | distinct from every other API |
| `OAUTH_AUDIENCE_SIM` | env | resource-server identifier URL | ACL | |
| `OAUTH_AUDIENCE_WS` | env | resource-server identifier URL | relay WS | |
| `OAUTH_AUDIENCE_EVIDENCE` | env | resource-server identifier URL | late admission | |
| `OAUTH_SCOPES_<PURPOSE>` | env | space-separated allowlist | per-client | `console.write console.read`, `ws.observe`, `sim.iso87a.authorize`, `evidence.late`, `evidence.diagnostic`, `console.demo` |
| `DEMO_SUBJECT` | env | single subject string | relay, authorizers | exactly one; no wildcard |
| `DEMO_AUDIENCE` | env | console audience | relay | equals `OAUTH_AUDIENCE_CONSOLE` |
| `MTLS_TRUSTSTORE_URI` | env | `s3://<bucket>/<key>` | REST/HTTP custom domain | public certs only |
| `MTLS_TRUSTSTORE_VERSION` | env | S3 version id | custom domain | pin; never `latest` |
| `CUSTOM_DOMAIN_CONSOLE` | env | FQDN | HTTP API mapping | DNS is a deployment input |
| `CUSTOM_DOMAIN_SIM` | env | FQDN | REST API mapping | |
| `CUSTOM_DOMAIN_EVIDENCE` | env | FQDN | REST API mapping | |
| `CUSTOM_DOMAIN_WS` | env | FQDN | WebSocket API mapping | |
| `ACM_CERT_ARN_<DOMAIN>` | env | ACM ARN | custom domains | Regional |
| `C4_PUBLIC_KEY_IDS` | env | comma list of `keyId` | registry, verifiers | public identifiers |
| `REGISTRY_TABLE` | env | table name | authorizers, application | |
| `ASSERTION_REPLAY_TABLE` | env | table name | token clients | |
| `HISTORY_TTL_DAYS` | env | `7` | DynamoDB TTL | value owner-approved; per-record applicability is the NV-05 delta, not this schema |
| `GITHUB_SUBJECT_FORMAT` | repo | `classic` \| `immutable` | OIDC trust policy | must match the repository's actual token format (unverified) |

## 3. GitHub Secrets (sensitive)

Names are proposals; values never appear in this repository, its artifacts or logs.

| Secret | Scope | Content | Consumer | Rotation |
| --- | --- | --- | --- | --- |
| `OAUTH_KEY_<CLIENT>` | environment | PKCS#8 private key for `private_key_jwt` | deploy step → parameter store | new key + issuer credential overlap ≥24 h |
| `TLS_KEY_<CLIENT>` | environment | client private key | deploy step → parameter store | ≤90 d |
| `TLS_CERT_<CLIENT>` | environment | client certificate (public, kept beside key) | deploy step → parameter store | with key |
| `C4_SIGNING_KEY_<PRINCIPAL>` | environment | Ed25519 private key | deploy step → parameter store | new `keyId`, dual-verify window |

Explicitly **not** secrets: the demo password is never stored in the repo or CI (the single demo user is provisioned out-of-band at the tenant); AWS access keys are not used at all (OIDC only).

## 4. Runtime injection contract

| Process | Non-secret env | Secret source | IAM required |
| --- | --- | --- | --- |
| Console HTTP integration | `REGISTRY_TABLE`, `OIDC_ISSUER_URL`, `OIDC_JWKS_URI`, audiences, scopes, `ENVIRONMENT` | none (verifies tokens only) | `dynamodb:GetItem` on registry |
| Authorizers | same + `MTLS_TRUSTSTORE_VERSION` (for diagnostics only) | none | `dynamodb:GetItem` on registry, `logs:*` scoped |
| ACL client | `OIDC_TOKEN_URL`, `OAUTH_CLIENT_ID_ACL`, `OAUTH_AUDIENCE_SIM`, `OAUTH_SCOPES_ACL`, `PARAM_OAUTH_KEY_ACL`, `PARAM_TLS_KEY_ACL` | `ssm:GetParameter` (name only in env) + `kms:Decrypt` via SSM | scoped to the exact parameter ARNs |
| Relay | as above for console/WS | as above | as above |
| Evidence verifier | `C4_PUBLIC_KEY_IDS`, `REGISTRY_TABLE` | none (public keys only) | registry read |

Contract rules:

- The environment variable holds a **parameter name**, never key bytes.
- `ssm:GetParameter` resources are enumerated per parameter; no `arn:aws:ssm:*:*:parameter/*`.
- `kms:Decrypt` is restricted with `kms:ViaService = ssm.<region>.amazonaws.com` and the specific key.
- Secrets are read once per cold start and held in process memory; never written to `/tmp`, never included in exception messages, never returned by a handler.
- Rotation is picked up on the next cold start or via a version check; the design does not require a redeploy to rotate.

## 5. Parameter naming (parameter store)

```
/acq-sim/<env>/oauth/<client>/private-key        SecureString (CMK)
/acq-sim/<env>/tls/<client>/private-key          SecureString (CMK)
/acq-sim/<env>/tls/<client>/certificate          String (public)
/acq-sim/<env>/c4/<principal>/private-key        SecureString (CMK)
/acq-sim/<env>/registry/version                  String
```

The CMK is created by bootstrap with a key policy limited to the deploy role and the runtime roles; rotation enabled.

## 6. Terraform backend schema (parameterized, not applied)

```hcl
terraform {
  required_version = ">= 1.10.0"          # use_lockfile
  backend "s3" {
    bucket       = "acq-sim-tfstate-<account>-<env>"
    key          = "acq-sim/<env>/network/terraform.tfstate"
    region       = "us-east-2"
    encrypt      = true
    use_lockfile = true                    # S3-native locking
    # allowed_account_ids supplied via -backend-config or env, not hard-coded here
  }
}
```

Partial configuration via `-backend-config` or `TF_CLI_ARGS`; credentials come from the OIDC-assumed role in the environment, never from files in the repository.

**Locking note.** Terraform's S3 backend documents `use_lockfile` for state locking and documents DynamoDB-based locking as **deprecated and slated for removal** (`https://developer.hashicorp.com/terraform/language/backend/s3`, fetched 2026-11-21T00:04:24Z). The schema therefore requires `use_lockfile = true` and records `dynamodb_table` only as a legacy fallback for Terraform versions that lack lockfile support. This is a correction to any plan text that still assumes a DynamoDB lock table.

## 7. Bootstrap (separate root, configuration only)

Bootstrap cannot be part of the stack that uses the backend — the backend must exist before `terraform init`. Schema:

| Resource | Required properties |
| --- | --- |
| State bucket | `us-east-2`; versioning enabled; SSE-KMS with a dedicated CMK; Block Public Access all four settings; bucket policy denying non-TLS and non-expected-principal access; `ObjectOwnership=BucketOwnerEnforced` |
| State CMK | rotation enabled; policy limited to bootstrap admin, deploy roles, and the Terraform execution role |
| Lifecycle | noncurrent version expiry (proposal; owner may change) — never expire current state |
| Access logging | optional; if enabled, a separate bucket; never the state bucket itself |
| Locking | none to create — `use_lockfile` writes `<key>.tflock` in the same bucket; the deploy role needs `s3:GetObject`/`PutObject`/`DeleteObject` on `*.tflock` only |

Bootstrap state (if any) is kept in a separate prefix with its own key; it is never merged into the environment stack state.

## 8. GitHub OIDC trust schema (parameterized)

Provider: `https://token.actions.githubusercontent.com`; audience `sts.amazonaws.com` (`https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws`, fetched 2026-11-21T00:00Z).

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "Federated": "arn:aws:iam::<account>:oidc-provider/token.actions.githubusercontent.com" },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": {
        "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
        "token.actions.githubusercontent.com:sub": "repo:<owner>/<repo>:environment:<env>"
      }
    }
  }]
}
```

- `sub` must be an **exact** match; no `StringLike` wildcards, no `repo:<owner>/<repo>:*`.
- If the repository uses immutable subject claims (documented for repositories created after 2026-07-15), the `sub` is `repo:<owner>@<id>/<repo>@<id>:environment:<env>`. The format is **unverified** for this repository and is a deployment input (`GITHUB_SUBJECT_FORMAT`); a mismatch fails closed at `AssumeRoleWithWebIdentity`.
- Prefer the `environment:` form; attach environment protection rules (required reviewers, branch restriction) so the environment cannot be entered from an arbitrary branch.
- `id-token: write` is granted **only** to the deploy job; `pull_request` workflows receive no AWS credentials; `pull_request_target` is not used with secrets.
- Actions are pinned by full commit SHA; the AWS credentials action is pinned, not `@main`.

Role permissions (least privilege, per environment): Terraform state bucket access limited to the environment prefix; resource creation limited to the simulator's resource types; `iam:PassRole` only for the specific runtime roles; no `*` resources.

## 9. CI static validation rules (design-level, to be implemented later)

These are assertions a future pipeline should enforce; none is executed here.

| Rule | Purpose |
| --- | --- |
| `use_lockfile = true` present in every backend block | prevents silent lock loss |
| no `dynamodb_table` in new backend blocks | avoids deprecated mechanism |
| no `aws_ssm_parameter` with `value`/`value_wo` containing key material | keeps secrets out of state |
| no `output` whose value references a private key parameter | keeps secrets out of logs/state consumers |
| no `data "aws_ssm_parameter"` with `with_decryption = true` in root config | keeps plaintext out of state |
| default `execute-api` endpoint disabled on every mTLS custom domain | closes the bypass the AWS page warns about |
| every `aws_apigatewayv2_authorizer` on a service route has `authorizer_result_ttl_in_seconds = 0` | prevents allow reuse across certificates |
| no `aws_lambda_function_url` resource | no alternate ingress |
| OIDC trust `sub` contains no `*` | prevents repo/ref wildcard trust |
| `id-token: write` appears only in the deploy job | limits credential issuance |
| workflow `uses:` entries pinned to 40-hex SHAs | supply-chain integrity |
| no secret-like strings in `*.tfvars` committed files | stops accidental authoring |

## 10. Open placeholders (must be supplied before any apply)

`<account>`, `<owner>/<repo>` actual subject form, `<tenant>` and tenant region, ACM certificate ARNs, the four FQDNs and their DNS zones, truststore CA choice and bundle, client certificate issuance authority, CMK ids, Terraform version available on the runner, and the environment name(s). None is guessed here; none is required to review this design.
