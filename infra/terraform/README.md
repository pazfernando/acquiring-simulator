# Infrastructure as code — acquiring simulator

Status: **authored, locally validated, never applied.** No AWS account was
contacted, no Terraform state was created or changed, no plan was run against a
backend, and no pipeline was executed. Deployment authority persists from the
owner continuation of 2026-11-23, but a concrete destination (account,
environment, domains, certificates, artifact identities, OIDC subject) and the
approval gates are still required before anything is provisioned.

Owner-selected direction: native Terraform HCL, remote state in S3, region
`us-east-2`, GitHub Actions OIDC (no long-lived keys). The legacy
`infra/template.yaml` SAM template is preserved unchanged as historical
provenance; it is not the target topology and is not deployable as such.

## Layout

```text
infra/terraform/
  bootstrap/            # one-time, separate root, local state: S3 state bucket + CMK
  modules/
    network/            # VPC, 2 public + 2 private subnets, SGs, endpoints, flow logs
    keycloak/           # single Fargate task, Aurora Serverless v2, ALB path allowlist, WAF
    console/            # private S3 + OAC + CloudFront (us-east-1 cert) + WAF + CSP
    api/                # HTTP API + JWT authorizer + console Lambda
    mtls/               # REST API, mTLS custom domain, Lambda authorizer (TTL 0)
    ws/                 # WebSocket API, $connect authorizer (TTL 0), notifier
    data/               # journal, events, issuer truth, registry, connections, assertion replay
    observability/      # 7-day logs, alarms
    identity/           # GitHub OIDC provider, exact-sub deploy role, runtime guardrail
  envs/stack/           # per-environment wiring; every unresolved value is required
  tools/policy_checks.py
```

## Component mapping (design rev4)

| Rev4 component | Where |
| --- | --- |
| Static SPA + CloudFront + private S3 (OAC) + WAF + ACM + headers/CSP | `modules/console` |
| HTTP API + JWT authorizer + console Lambda | `modules/api` |
| Console → ACL mTLS boundary (custom domain, truststore pin) | `modules/mtls` (`mtls_acl`) |
| ACL → processor mTLS boundary | `modules/mtls` (`mtls_processor`) |
| WebSocket API + `$connect` authorizer + custom domain + notifier | `modules/ws` |
| Keycloak single task + Aurora + ALB path allowlist + operator path | `modules/keycloak` |
| Registry / journal / event / connection / assertion tables | `modules/data` |
| Per-function roles, explicit deny, OIDC provider + deploy role | `modules/identity` |
| Observations: 7-day retention, alarms | `modules/observability`, plus log groups in each module |
| S3 state: versioning, SSE-KMS, BPA, `use_lockfile`, explicit bootstrap | `bootstrap/`, `envs/stack/backend.tf` |

The legacy Processor → Network → Issuer function chain remains in
`infra/template.yaml` and is **not** recreated here; recording that parity gap
is deliberate rather than duplicating application packaging.

## Backend bootstrap (human, once, separate from the stack)

The S3 backend cannot create its own bucket. Run `bootstrap/` first with an
administrative principal:

```bash
cd infra/terraform/bootstrap
terraform init
terraform apply \
  -var "aws_account_id=<12-digit account>" \
  -var "state_bucket_name=<globally unique bucket>" \
  -var "trusted_principal_arns=[\"arn:aws:iam::<account>:role/<bootstrap-admin>\",\"arn:aws:iam::<account>:role/acq-sim-<env>-github-deploy\"]"
```

Bootstrap state is local (`bootstrap/terraform.tfstate`) and is never
committed; `*.tfstate` is ignored. The bucket and CMK are `prevent_destroy`.
Then initialize the stack root with non-secret backend values:

```bash
cd infra/terraform/envs/stack
terraform init \
  -backend-config="bucket=<state bucket>" \
  -backend-config="key=acq-sim/<env>/stack/terraform.tfstate" \
  -backend-config="region=us-east-2" \
  -backend-config="encrypt=true" \
  -backend-config="use_lockfile=true" \
  -backend-config="kms_key_id=<state CMK arn>"
```

State is environment-separated by key, encrypted with the dedicated CMK, and
locked with S3-native `use_lockfile`. No `dynamodb_table` (deprecated).

## Required inputs (fail closed)

Every value below is a required variable or a documented deployment input. None
has a fabricated default. `terraform plan` fails rather than improvising.

| Group | Inputs |
| --- | --- |
| Account/region/environment | `aws_account_id`, `environment`, `availability_zones` (region defaults to `us-east-2` and is validated) |
| Domains/DNS | `console_fqdn`, `api_fqdn`, `auth_fqdn`, `ws_fqdn`, `acl_fqdn`, `processor_fqdn`, `dns_zone_id`, `console_bucket_name` |
| Certificates | `console_certificate_arn` (us-east-1), `api_certificate_arn`, `auth_certificate_arn`, `ws_certificate_arn`, `acl_certificate_arn`, `processor_certificate_arn` (us-east-2) |
| State | `state_bucket_arn`, `state_kms_key_arn` |
| OIDC | `github_oidc_subject` (exact, no wildcard), `create_oidc_provider`, `existing_oidc_provider_arn` |
| Audiences/identity | `console_audience`, `acl_audience`, `simulator_audience`, `ws_audience`, `demo_subject` |
| Artifacts (built by CI, recorded by digest) | `console_api_artifact`, `mtls_authorizer_artifact`, `ws_authorizer_artifact`, `ws_notifier_artifact` (bucket, key, S3 version, base64 SHA-256) |
| Backends | `acl_backend_lambda_invoke_arn`, `acl_backend_lambda_name`, `processor_backend_lambda_invoke_arn`, `processor_backend_lambda_name` |
| mTLS custody | `acl_truststore_uri`, `acl_truststore_version`, `processor_truststore_uri`, `processor_truststore_version` (exact S3 version, never `latest`) |
| Keycloak | `keycloak_image_digest` (`@sha256:` required), optional `bootstrap_admin_secret_arn` |
| Operations | optional `alarm_topic_arn`; sizing/proposal defaults are variables and are confirmed in the cost statement |

Not authored here because the design is open: the Keycloak realm import
artifact and client/key registration (blocked on D-5 / G0). No realm JSON is
invented, and no bootstrap admin credential is stored in this tree.

## Validation (local, no cloud)

Pinned tools, installed user-locally with checksum verification:

- Terraform **1.17.2** (official `releases.hashicorp.com` SHA256SUMS)
- tflint **0.66.0** + AWS ruleset **0.50.0** (official release checksums)
- actionlint **1.7.12** (GitHub release asset digest)
- python-hcl2 **7.3.1** in a user venv

Provider pins: `hashicorp/aws` `= 6.74.0` and — since the N-02 closure —
`hashicorp/random` `= 3.9.1`, both recorded in `envs/stack/.terraform.lock.hcl`.

```bash
cd infra/terraform
terraform fmt -recursive -check -diff
(cd bootstrap && terraform init -backend=false && terraform validate)
(cd envs/stack && terraform init -backend=false && terraform validate)
tflint --init && tflint --recursive
python3 tools/policy_checks.py   # needs python-hcl2
actionlint ../../.github/workflows/*.yml
```

`terraform plan` is intentionally not part of local validation: it needs real
inputs and credentials, and this slice is forbidden from contacting AWS.

## Security invariants encoded here

- State: private, SSE-KMS, versioned, Block Public Access, TLS-only policy,
  principal allowlist, `prevent_destroy`; bootstrap separate.
- No secret value, private key or account literal in Terraform, plans or logs.
  Keycloak's DB password is generated by Secrets Manager and referenced by ARN.
- Every request-based authorizer has `authorizer_result_ttl_in_seconds = 0`.
- mTLS domains pin an exact truststore version; REST APIs disable the default
  `execute-api` endpoint, so the custom domain is the only ingress.
- The console HTTP API and the WebSocket API also disable their default
  `execute-api` endpoints. The CloudFront `/api/*` origin is a dedicated
  regional custom domain (`api_fqdn`), never the shared execute-api host.
- The distribution has no `custom_error_response` blocks: API 400/401/403/404/
  410/503 status and JSON bodies reach the viewer unchanged. SPA deep links are
  resolved by a viewer-request CloudFront Function scoped to non-`/api/*`
  extensionless paths.
- Journal, event, issuer-truth and registry tables carry
  `deletion_protection_enabled = true`; only connections and assertion-replay
  records are disposable.
- The console role has no standalone `dynamodb:PutItem` on the event table;
  events are appended only through the atomic `TransactWriteItems`.
- The issuer WAF inspects headers and URIs only (never request bodies), and
  redacts `Authorization`, `Cookie` and the OAuth flow query parameters.
- Response-header CSP is delivered by CloudFront, not baked into HTML; WAF and
  API access-log formats exclude `Authorization`, `Sec-WebSocket-Protocol` and
  the origin-verification header `X-Origin-Verify`.
- **Origin verification (N-02, owner Option B):** CloudFront adds a fixed
  `X-Origin-Verify` header to every `/api/*` origin request, whose value is a
  Terraform-generated `random_password` (sensitive, no literal, no tfvars, no CI
  variable, no output, redacted in WAF logs). CloudFront overwrites a
  viewer-supplied header of the same name, so direct-to-origin requests cannot
  impersonate the edge.
- Internet ingress is HTTPS/443 only; Keycloak admin is reachable only through
  an audited ECS Exec/SSM session, never a public `/admin` path.
- GitHub OIDC trust is `StringEquals` on one exact `sub`; `id-token: write`
  appears only in the gated plan/apply jobs.
- CI pins Actions by 40-hex commit SHA and scans dependencies, secrets, IaC and
  Java with Trivy, gitleaks and CodeQL.

## Rev3 corrections (rev2 Security N-02..N-07)

| Finding | Correction in this tree |
| --- | --- |
| N-02 (SEC-I1) origin/WAF bypass | Console HTTP API `disable_execute_api_endpoint = true`; dedicated regional custom domain (`api_fqdn` + `api_certificate_arn`) with API mapping and Route 53 alias; CloudFront `/api/*` origin = that domain (`module.api.origin_domain_name`), not execute-api. Policy check 13 enforces both. |
| N-03 (SEC-I2) edge 403/404 masking | Distribution-wide `custom_error_response` removed; SPA deep links handled by a `viewer-request` CloudFront Function on the static behavior only. Policy check 14. |
| N-04 (SEC-I3) WS default endpoint | `disable_execute_api_endpoint = true` verified present in the pinned provider schema (hashicorp/aws 6.74.0) and set on the WebSocket API; notifier callbacks move to the custom domain (stage omitted, per the API Gateway `@connections` custom-domain rule). |
| N-05 (SEC-I4) issuer WAF body exposure | No body-inspecting rule; the whole query string is redacted (the provider supports `query_string`, not per-argument redaction), covering OAuth `code`/`session_code`. Policy check 16 fails any future `field_to_match { body }`. |
| N-06 (SEC-I5) event direct append | `dynamodb:PutItem` removed from the console role's event statement; `TransactWriteItems` added. Policy check 15. |
| N-07 (SEC-I6) table deletion | Deletion protection enabled on journal/events/issuer_truth/registry; PITR stays an explicit deployment/cost input recorded against NV-05. Policy check 17. |

**Residual (N-02, not closed by configuration alone):** the dedicated custom
domain is still publicly resolvable, so a caller that resolves `api_fqdn`
directly reaches the API without traversing the CloudFront edge WAF/response
headers. The following were checked and are *not* available for a console HTTP
API: AWS WAF association (the pinned WAF documentation lists API Gateway REST
APIs only, not HTTP APIs), and CloudFront origin mutual TLS (the pinned
provider schema for `aws_cloudfront_distribution` has no origin client
certificate field). Remaining compensating controls: the route keeps its JWT
authorizer (issuer + audience; no anonymous path), the stage keeps per-stage
throttling, and the API custom domain is dedicated to console traffic only.
The owner selected the origin-verification header (Option B) on 2026-11-23; see
the next section for what that closes in configuration and what it does not.

## N-02 closure — CloudFront origin verification (owner Option B)

Owner decision: `owner-decisions.md` §"N-02 closure (Option B) and normative
adoption — 2026-11-23" (verbatim "adelante con lo recomendado"). Security
analysis: `reviews/continuation-rev3-security-20261123/security-review.md` §4,
N-02. Selected option: **B** — keep the HTTP API and its native JWT authorizer,
add a high-entropy origin-verification header at the edge, and have the console
API boundary reject any request whose header is absent or mismatched.

### Authored configuration

| Concern | Where | Shape |
| --- | --- | --- |
| Value generation | `envs/stack/main.tf` | `random_password.origin_verify`: 48 alphanumeric characters, `special = false`, at least 8 upper, 8 lower and 8 numeric (≈285 bits). No variable, no default, no tfvars, no CI variable. |
| Value custody | `envs/stack/main.tf` → `modules/console`, `modules/api` | One generator, two consumer inputs. CloudFront receives it as the `sensitive = true` variable `origin_verify_secret`; the console Lambda receives the same value as `EDGE_ORIGIN_VERIFY` from the `sensitive = true` variable `edge_origin_verify`. `random_password.result` is sensitive in the provider schema and sensitivity propagates, so plan/apply prints `(sensitive value)`. Durable copy: encrypted remote state. Two provider-owned runtime copies are unavoidable (distribution configuration, function configuration) and are recorded as a residual in the contract delta §8.2 and in the DevOps report. |
| Header injection | `modules/console/main.tf` | The `/api/*` origin adds exactly one `custom_header` whose name is the fixed `local.origin_verify_header` = **`X-Origin-Verify`**. Never added by a CloudFront Function, WAF rule, headers policy or the application. |
| Leak exclusion | `modules/console/main.tf`, `modules/api/main.tf` | No CloudFront standard log is configured; WAF logging redacts `x-origin-verify` (plus `authorization`, `sec-websocket-protocol`); the API stage access-log format logs `$context` fields only and no request header at all; the value is not in any output, tfvars, CI variable, SSM parameter or artifact. Environment-variable copies are provider-owned runtime state, never logged. |
| Machine assertion | `tools/policy_checks.py` check 18 (plus the extended check 12) | Fails on: a denylisted or drifted header name, a value that is not `random_password.origin_verify.result`, a literal or other value source, a default on either consumer variable, a non-sensitive variable, an output exposing it, the header in an authored log format, a missing WAF redaction, a CI variable, generation under 32 characters or with special characters enabled, a missing/incorrect `EDGE_ORIGIN_VERIFY` mapping, caching enabled on `/api/*`, more than one origin resolving `api_fqdn`, or the header name appearing in CloudFront Function source. |

### Header name: why `X-Origin-Verify`

CloudFront refuses to add a documented set of headers as origin custom headers:
`Cache-Control`, `Connection`, `Content-Length`, `Cookie`, `Host`, `If-Match`,
`If-Modified-Since`, `If-None-Match`, `If-Range`, `If-Unmodified-Since`,
`Max-Forwards`, `Pragma`, `Proxy-Authenticate`, `Proxy-Authorization`,
`Proxy-Connection`, `Range`, `Request-Range`, `TE`, `Trailer`,
`Transfer-Encoding`, `Upgrade`, `Via`, anything beginning with `X-Amz-` or
`X-Edge-`, and `X-Real-Ip`. `X-Origin-Verify` is outside that list, is not a
header any component in this tree treats semantically (`Authorization`,
`Host`, `Origin`, `Cookie`, `Content-Type`, `Sec-WebSocket-Protocol` are the
only ones that carry meaning), and is the name AWS uses in its own
bypass-prevention description. `tools/policy_checks.py` encodes the denylist,
so a future rename that collides with it fails the suite.

### Override semantics (vendor-documented)

AWS developer guide, *Add custom headers to origin requests*:
"If the header names and values that you specify are not already present in the
viewer request, CloudFront adds them to the origin request. If a header is
present, CloudFront overwrites the header value before forwarding the request
to the origin." The same page documents the intended use case: configuring the
origin to respond only when it receives a header that CloudFront added, which
"prevent[s] users from bypassing CloudFront and accessing your content directly
on the origin". A viewer-supplied `X-Origin-Verify` therefore cannot impersonate
the edge. Evidence (URL, fetch time, page digest and provider-schema extract)
is recorded in `evidence/n02-origin-verification-20261123/`.

### Interface for Implementation (must match exactly)

- Request header name on the origin: **`X-Origin-Verify`** (header names are
  case-insensitive; CloudFront and the HTTP API surface it lowercase to the
  Lambda, but occurrence detection must be case-insensitive and the value
  comparison byte-exact and case-sensitive).
- Expected value in the console Lambda environment: **`EDGE_ORIGIN_VERIFY`**,
  fed from the single `random_password.origin_verify` in the stack root. Read
  once at initialization; a missing, empty or malformed value must fail
  initialization rather than serve requests.
- Refusal contract (Requirements-owned delta, draft): HTTP `403` with body
  `{"version":1,"error":"EDGE_ORIGIN_NOT_VERIFIED","action":null}`; one
  structured record `console_origin_verification_denied` plus the
  `ConsoleOriginVerificationDenied` metric. This module authors only the value
  and the header; it does not author or claim the application behaviour.
- Nothing in the console module logs, forwards or returns the value, and the
  `/api/*` behavior keeps a caching-disabled policy and no CloudFront Function,
  so the header is never observable at a viewer-side evaluation point.

### Kept intact by this change

Every existing shape is unchanged: `disable_execute_api_endpoint = true` on the
HTTP and WebSocket APIs, the dedicated regional custom-domain origin
(`api_fqdn`, never `execute-api`), fail-closed required inputs with no
fabricated defaults, the JWT authorizer with no anonymous path, stage
throttling, the response-header policy, and policy checks 1–17 (backend, lock
hygiene, secret literals, authorizer TTL, mTLS pins, OIDC subject, IAM shape,
committed-state absence, provider pins, ingress, Lambda runtime, log formats,
default endpoints, error pass-through, event atomicity, issuer WAF, deletion
protection).

## Known gaps (explicit)

1. Keycloak realm/client/issuance configuration is not authored while D-5 is open.
2. CloudFront standard request logs are not enabled (bucket-ACL conflict);
   request visibility comes from WAF logs with credential headers redacted.
3. The legacy SAM function chain is not yet reproduced in Terraform.
4. Keycloak image delivery (private ECR mirror or NAT) is a deployment input.
5. No alarm notification contact exists until `alarm_topic_arn` is supplied.
6. `aurora_engine_version` defaults to the major version `17`; a minor pin is a
   deployment input for reproducibility.
