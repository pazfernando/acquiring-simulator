# DevOps report — Terraform/CI corrections rev3 (rev2 Security N-02..N-07)

Date: 2026-11-23 (America/Bogota). Run `bd967d8e-f9de-4691-afc9-d9a8bad13f6b`,
session `agent:ai-sdlc-devops:subagent:55e7b78e-6b1f-4c81-8677-61ccebbcfc1d`.
Observed runtime model: **nan/deepseek-v4-flash** (agent configured default, no
override, no fallback). Provider-qualified identifier not exposed and not
inferred.

Status: **corrections authored and locally validated; nothing planned, applied,
deployed or reviewed independently.** No AWS API call, no credential flow, no
Git or provider command, no onward delegation, no application/contract edit.

## 1. Commission and binding

- Continuation authority: `handoffs/continuation-20261123/commission.md`
  (Fernando, 2026-11-23: continue authentication, server history, independent
  reviews and deployment) plus the rev3 correction dispatch. No generic
  reauthorization requested or inferred.
- Historical Implementation binding `feat/wi-acq-auth-001` @
  `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` is **not freshly attested**: Git is
  outside this role's contract. The corrections attach to exact file bytes, not
  to a commit.
- Reviewed inputs (recomputed here, `commands/07-scope-and-inputs.txt`):
  - `reviews/continuation-rev2-security-20261123/security-review.md`
    `760ca956a980bf31224b7552d9d3a901b6be4881f8c9caec7f8630fbb89b260d` — MATCH
  - `reviews/continuation-rev2-domain-20261123/domain-review.md`
    `9dabe22497b5379b9c5808da18b02dcdf76a7048ffc14611c15c6a6e9b73a8c1` — MATCH
  - `inputs.json` 11/11 pins MATCH; `next-slices.json` 5/5 MATCH
  - Reviewed candidate packet `design/contract-deltas/continuation-rev2-20261123/`
    14/14 entries OK; `OUTPUTS.SHA256SUMS` =
    `a3bbb25da508aa890d0c1bcc6b6d143dc36b922d2bd07ffaff5f2d9fc46ce48c`

## 2. Write scope actually used

Only the exclusive paths: `infra/terraform/**`, `.github/workflows/terraform.yml`
and `evidence/terraform-corrections-rev3-20261123/`. Sixteen source files:

`infra/terraform/modules/{api,console,data,keycloak,ws}/{main,variables,outputs}.tf`
(only the files a finding required), `infra/terraform/envs/stack/{main,dns,outputs,variables}.tf`,
`infra/terraform/tools/policy_checks.py`, `infra/terraform/README.md`,
`.github/workflows/terraform.yml`.

Untouched: all normative sources and contracts (`requirements.md`,
`observation-contract.md`, `acceptance-scenarios.md`, `channel-contract.md`,
`owner-decisions.md`, `design/**`, `reviews/**`), application code (`src/`,
`pom.xml`, `console-ui/`), and the legacy SAM artifact
`infra/template.yaml` (`f7637650095ea5166aee9e3d41dbad3d033324c45d48268c70d2bb58d62c1abe`,
unchanged). Unresolved destination values were preserved, not invented: the only
new inputs are `api_fqdn` and `api_certificate_arn`, both required and failing
closed.

## 3. Corrections

### N-02 (IMPORTANT) — execute-api bypass of the edge WAF

Authored:

1. `modules/api`: `disable_execute_api_endpoint = true` on the console HTTP API.
2. New dedicated regional custom domain `api_fqdn` (TLS 1.2, supplied
   certificate) plus `aws_apigatewayv2_api_mapping` to `$default`; outputs
   `origin_domain_name`, `regional_domain_name`, `regional_zone_id`,
   `api_endpoint_disabled`.
3. `envs/stack`: CloudFront `/api/*` origin is now
   `module.api.origin_domain_name`; Route 53 alias record for `api_fqdn`;
   `api_fqdn`/`api_certificate_arn` are new required inputs; the old
   `console_api_endpoint` output that advertised the execute-api host is
   replaced by `console_api_origin` + `console_api_endpoint_disabled`.
4. `modules/console`: origin variable documented and validated to reject an
   `execute-api` hostname.
5. Policy check 13 fails any `aws_apigatewayv2_api` without
   `disable_execute_api_endpoint = true`, and fails if the root wires the origin
   to `module.api.api_endpoint`.

### N-03 (IMPORTANT) — distribution-wide 403/404 → 200 index.html

The two `custom_error_response` blocks are removed. CloudFront error mapping is
distribution-wide and cannot be scoped to one behavior, so SPA deep links are
now resolved before the origin request by `aws_cloudfront_function.spa_fallback`
(`cloudfront-js-2.0`, viewer-request) associated only with the static behavior:
`/api` and `/api/*` are returned untouched, extensionless paths map to
`/index.html`, and asset paths keep their real origin 403/404 instead of a fake
200. Policy check 14 fails any `custom_error_response` and any missing fallback.

### N-04 (ADVISORY) — WebSocket default endpoint

Verified against the pinned provider schema (hashicorp/aws **6.74.0**):
`aws_apigatewayv2_api.disable_execute_api_endpoint` exists
(`commands/06-provider-schema.txt`). It is now `true` on the WebSocket API, so
`wss://{api-id}.execute-api...` is no longer an ingress; the `$connect`
authorizer (token + exact Origin, TTL 0) is unchanged. Because the notifier used
the default host for `@connections`, `WS_MANAGEMENT_ENDPOINT` moved to
`https://<ws_fqdn>` with the stage omitted, following the vendor rule recorded in
the same evidence file. This runtime behaviour is a deployment-gate check, not a
verified fact.

### N-05 (ADVISORY) — issuer WAF body exposure

No rule inspects the request body; that is now stated in the web ACL and
asserted. Logging redaction was extended with whole-`query_string` redaction
(the pinned resource supports only `method`, `query_string`, `single_header`,
`uri_path` — individual query arguments are not supported, so the whole query is
redacted, which is stricter). Policy check 16 fails any future
`field_to_match { body }` and any missing query redaction.

### N-06 (ADVISORY) — console role could append events non-atomically

`EventReadAppend` is now `dynamodb:GetItem`, `dynamodb:Query`,
`dynamodb:TransactWriteItems`; the standalone `PutItem` on the event table is
removed. The journal table keeps its legitimate single-table `PutItem`. Policy
check 15 fails a `PutItem` grant on events or a missing `TransactWriteItems`.

### N-07 (ADVISORY) — durable table deletion

`modules/data` now carries a per-table `protect` flag driving
`deletion_protection_enabled`: journal, events, issuer_truth and registry are
protected; connections and assertion_replay stay disposable. The PITR choice is
recorded explicitly against NV-05 in the variable description: it remains a
deployment/cost input (default `false`), not silently changed. Keycloak's Aurora
cluster and ALB remain `deletion_protection = false` deliberately (K-2
disposable issuer). Policy check 17 enforces both directions.

## 4. Verification executed (local, no cloud)

Raw outputs: `commands/00-tool-versions.txt` … `commands/07-scope-and-inputs.txt`.

| # | Command | Result |
| --- | --- | --- |
| 1 | `terraform fmt -recursive -check -diff` | exit 0, no diff |
| 2 | `terraform init -backend=false` + `terraform validate` in `bootstrap/` | init 0, "configuration is valid" |
| 3 | `terraform init -backend=false` + `terraform validate` in `envs/stack/` | init 0, "configuration is valid" |
| 4 | `tflint --init` + `tflint --recursive --format compact` | exit 0, zero issues |
| 5 | `python infra/terraform/tools/policy_checks.py` (python-hcl2 7.3.1) | **17/17 PASS**, exit 0 |
| 6 | `actionlint .github/workflows/{ci,terraform}.yml` | exit 0 |
| 7 | `terraform providers schema -json` in a scratch root pinned to 6.74.0 | schema SHA-256 `39fbf1a3…02da`; attribute probes recorded |

Policy checks are structural (HCL parse), not string-only: backend lockfile and
encryption; no secret literal/SSM value/decrypt-in-state; REQUEST authorizer TTL
0; mTLS truststore pins and REST endpoint disablement; OIDC `StringEquals` exact
subject; no `Allow` with `Action: "*"`; no committed state/vars; exact provider
pins + `allowed_account_ids`; ingress 443-only; Lambda runtime/architecture
pins; credential-free access-log formats; plus the six new assertions above.

Tool provenance: Terraform 1.17.2, tflint 0.66.0 (+ AWS ruleset 0.50.0),
actionlint 1.7.12 installed user-locally under `~/.local` with release checksum
verification; python-hcl2 7.3.1 in a user venv. Binary hashes are in
`commands/00-tool-versions.txt`.

**Network disclosure:** `terraform init -backend=false` downloaded the
hashicorp/aws 6.74.0 provider plugin from the public Terraform registry into a
local `.terraform` cache (removed afterwards) so `validate` and the schema probes
could run. That is an artifact download, not an AWS API call: **no plan, no
apply, no provider/cloud API request, no credentials, no state access**. No
registry/provider substitution was made — the lock file already pinned 6.74.0.

## 5. Residual analysis — N-02 is not fully closable by configuration

After the corrections, the only remaining path around the CloudFront edge WAF is
a caller that resolves `api_fqdn` directly. Two candidate closures were checked
and are **not available** in this topology:

| Candidate closure | Check result |
| --- | --- |
| Regional WAF on the HTTP API stage | AWS WAF's supported-resource list names API Gateway **REST API** only (docs fetched 2026-11-23); HTTP APIs are not attachable. |
| Mutual TLS between CloudFront and the API custom domain | HTTP API custom domains support `mutual_tls_authentication` (schema-verified), but the pinned `aws_cloudfront_distribution` origin has no client-certificate/mTLS field (`commands/06-provider-schema.txt`). CloudFront could not present a certificate. |
| Application-level origin-verify header | CloudFront can inject a static origin header (`custom_header` is schema-verified), but enforcement must live in the K1 authorizer/application. That needs a new authorizer artifact and a contract revision — outside this correction scope and forbidden here (no application edits). |

Compensating controls in the authored state: the route keeps its JWT authorizer
(issuer + audience, no anonymous path), stage throttling stays, and the domain is
dedicated to console traffic. Consequence of the residual: an authenticated
caller could evade the per-IP `/api/*` edge rate bound. This is a **material
security decision**, so it is reported for owner disposition rather than
self-accepted; Option A (convert the console API to a REST API to gain stage WAF)
and Option B (K1-enforced origin-verify header) are the viable closures.

## 6. What this establishes and what it does not

**Establishes:** the six findings are corrected in exact authored bytes; the
tree still formats, type-validates in both roots, lints clean, passes 17/17
structural policy checks and actionlint; unsupported-provider claims were
checked against the pinned schema instead of assumed; unresolved destination
inputs remain unresolved and fail closed; legacy artifacts are byte-identical.

**Does not establish:** that any resource exists or plans cleanly; that WAF,
CloudFront Function, custom-domain `@connections` callbacks, deletion
protection or log redaction behave at runtime; that scans (Trivy, gitleaks,
CodeQL) pass; that the N-02 residual is accepted; independent Security/Domain
re-review of these bytes; QA; deployment authorization. No security exception or
approval is implied.

Known pre-existing gap, unchanged and out of scope: the `terraform.yml` env block
maps only domain-type inputs to `TF_VAR_*`; certificate/artifact/audience values
are documented as required variables but not mapped, so a plan fails closed
until that wiring is completed at deployment preparation.

## 7. Next handoff

1. Coordinator: route these exact bytes (`CHANGED-FILES.sha256`) to an
   independent Security re-review for N-02..N-07 disposition; DR2-01..03/N-01
   belong to the Requirements rev3 packet and are not claimed here.
2. Owner decision required for the N-02 residual (Option A, Option B, or an
   explicitly recorded acceptance with the bypass documented).
3. Deployment stays blocked on concrete destination inputs (account,
   environment, `api_fqdn` + certificate, zone, artifact digests, truststore
   custody, OIDC subject, Keycloak image digest), a cost statement with a named
   approver, and passed independent QA/Security on the implemented revision.

## 8. Output inventory

- `CHANGED-FILES.sha256` (16 repo-relative entries) SHA-256
  `27223b5b71ea4f00532aa2b62370fd369c04cc26c423c1687de327a3df7b98f4`.
- `commands/00`–`07` raw tool output, `finding-matrix.md`, this report and
  `PROGRESS.md` are listed in the adjacent `SHA256SUMS` sidecar (which excludes
  its own digest).
