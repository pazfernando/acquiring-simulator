# Terraform and delivery-pipeline authoring — DevOps report (2026-11-23)

Status: **AUTHORED AND LOCALLY VALIDATED. NOT APPLIED.** Native Terraform now exists under `infra/terraform/`, and GitHub Actions definitions exist under `.github/workflows/`. No AWS account was contacted, no credential or login flow was used, no remote backend was initialized, no state was created or changed, no plan was run against a backend, no pipeline was executed, no push/PR/merge occurred, and no application code or shared document was edited. The legacy SAM template is preserved byte-for-byte.

Author: ai-sdlc-devops. Date: 2026-11-23 (America/Bogota; commands ran 2026-11-24 UTC). Runtime observed: native OpenClaw subagent `ai-sdlc-devops`, session `agent:ai-sdlc-devops:subagent:5341e3e9-7ad7-4871-94d9-288388da03e4`; model metadata `nan/deepseek-v4-flash`, the configured default, **no model override**, no fallback. No onward delegation. No Git/`gh`/provider command was executed.

Basis read: role `AGENTS.md`; shared `GLOSSARY.md`, `PROJECT_EXECUTION.md` (infrastructure/automation defaults), `VERIFICATION_DELIVERY.md`; ADR `adr-operation-a-access-and-environment.md`; owner decisions K-1/K-2/K-3/P-1/CA-1; `design-rev2.md`, `design-rev4.md`, `configuration-schema.md`, `d5-ws-issuance.md`; the prior `design/deployment-continuation-20261123/devops-report.md`; repository `AGENTS.md`.

**Authority correction to the prior report.** The earlier readiness report recorded deployment authorization as "NOT GRANTED". That wording is corrected here: the owner continuation of 2026-11-23 **does authorize pursuing** real authentication, server history, independent reviews and deployment, and it authorizes this IaC/CI authoring. What does **not** exist yet is a concrete deployment destination and the gate evidence that must accompany it (account, environment, domains/certificates, custody, artifact identities, OIDC subject, cost statement, human approval of the realized environment). Provisioning therefore remains gated by inputs and approvals, not by an absence of scope authorization.

---

## 1. Input integrity

Handoff manifest `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/handoffs/continuation-20261123/next-slices.json` — expected `50d1cfa18ed7c9ae822f4fef1ae989f1eada7536aaedde1d1b6ce2715b917b5f` — **MATCH**, verified before any write.

| Manifest | Entries | Result |
| --- | --- | --- |
| `handoffs/continuation-20261123/inputs.json` | 11 | 11/11 MATCH |
| `handoffs/continuation-20261123/next-slices.json` | 5 | 5/5 MATCH |

Full recomputation is in `commands/07-protected-inputs.txt`. Additional preserved hashes:

| Artifact | SHA-256 | Change |
| --- | --- | --- |
| `infra/template.yaml` (legacy SAM) | `f7637650095ea5166aee9e3d41dbad3d033324c45d48268c70d2bb58d62c1abe` | none (identical to prior record) |
| `design/access-realization/design-rev4.md` | `3926423e9f42f6949647e5904271b6f6818b977301b66e81ffcf334f10c5dc56` | none |
| `design/contract-deltas/continuation-rev2-20261123/` | — | not modified (Requirements-owned) |

Binding: `/home/pazfernando/projects/acquiring-simulator`, branch `feat/wi-acq-auth-001`, historical HEAD `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` recorded as supplied. **No Git command was executed**, so no revision is independently attested here; conclusions attach to the hashed bytes above and to the authored files listed in §3.

---

## 2. What "authored" means in this report

Three levels are kept strictly separate:

1. **Authored** — configuration files exist in the repository.
2. **Locally validated** — a command was executed on this host and its exact output recorded (§6).
3. **Remotely executed** — nothing. No plan, apply, pipeline run, realm import or deployment happened.

There is no artifact digest/version for deployment (NOT BUILT), no environment identity (NOT PROVISIONED), and no pipeline run URL (NONE).

---

## 3. Authored artifacts

### 3.1 Terraform

| Path | Contents | Resource blocks |
| --- | --- | --- |
| `infra/terraform/bootstrap/` | Separate root: state bucket, CMK, versioning, SSE-KMS, Block Public Access, TLS-only/principal-allowlist policy, lifecycle, optional access-log bucket | 14 |
| `infra/terraform/modules/network/` | VPC, 2 public + 2 private subnets, IGW, optional NAT, route tables, 4 SGs, S3 gateway + 7 interface endpoints, flow logs | 28 |
| `infra/terraform/modules/keycloak/` | Aurora Serverless v2 cluster + instance, ECS cluster/task/service, execution and task roles, log group, ALB, deny-by-default listener with five allowlist rules, regional WAF + logging | 21 |
| `infra/terraform/modules/console/` | Private S3 bucket, OAC, CloudFront distribution, cache/origin-request/response-headers policies, bucket policy, `config.json`, CloudFront-scope WAF + logging | 17 |
| `infra/terraform/modules/api/` | Console Lambda + role, HTTP API, JWT authorizer, `/api/{proxy+}` route, stage with throttling and credential-free access logs | 12 |
| `infra/terraform/modules/mtls/` | REST API (default endpoint disabled), REQUEST authorizer with TTL 0, proxy method/integration, stage/access logs, mTLS custom domain with pinned truststore, base-path mapping, invoke permissions | 18 |
| `infra/terraform/modules/ws/` | WS API, `$connect` REQUEST authorizer (TTL 0), `$connect`/`$disconnect` MOCK routes, notifier Lambda with `ManageConnections` scoped to one API/stage, custom domain + mapping | 21 |
| `infra/terraform/modules/data/` | Six on-demand tables (`journal`, `events`, `issuer-truth`, `registry`, `connections`, `assertion-replay`), `pk`/`sk`, SSE, optional PITR, parameterized TTL | 1 (for_each over 6) |
| `infra/terraform/modules/identity/` | GitHub OIDC provider, exact-sub deploy role, scoped state access, documented service-management policy with explicit denies, runtime guardrail policy | 5 |
| `infra/terraform/modules/observability/` | API 5XX/4XX, Lambda errors, DynamoDB throttles, ALB 5XX/unhealthy, ECS running-task alarms | 7 |
| `infra/terraform/envs/stack/` | Per-environment wiring, partial S3 backend, two provider configs (`us-east-2` + `us-east-1` alias), Route 53 records | 6 |

Supporting files: `infra/terraform/README.md`, `.tflint.hcl`, `.gitignore`, `tools/policy_checks.py`, and the generated `.terraform.lock.hcl` for both roots (AWS provider only, identical hash).

### 3.2 Delivery pipelines

| File | Jobs | Triggers | Executed? |
| --- | --- | --- | --- |
| `.github/workflows/ci.yml` | `build-test` (Maven verify + artifact SHA-256 + artifact upload), `secret-scan` (gitleaks), `iac-and-dependency-scan` (Trivy fs + config), `codeql` (Java SAST) | pull request; push to non-main branches | **No** |
| `.github/workflows/terraform.yml` | `static` (fmt, validate both roots, tflint, policy checks), `plan` (manual, OIDC, `terraform-plan` environment), `apply` (manual `APPLY`, `terraform-apply` environment, plan+apply in one gated job) | PR paths + manual dispatch | **No** |

Every `uses:` is pinned to a 40-hex commit SHA. `id-token: write` appears only in the `plan` and `apply` jobs. No `pull_request_target`, no long-lived AWS keys, no automatic apply. The apply path is unreachable from a pull request.

---

## 4. Design decisions and pinned versions

| Choice | Version/decision | Justification |
| --- | --- | --- |
| Terraform CLI | **1.17.2** (official SHA256SUMS verified) | Current stable; satisfies `use_lockfile` (≥1.10) and matches the CI pin |
| AWS provider | **6.74.0** exactly, in every root and module | Current stable at authoring; exact pins prevent silent drift |
| tflint | **0.66.0**, AWS ruleset **0.50.0** | Both verified from official release checksums; `terraform` preset + AWS rules |
| actionlint | **1.7.12**, asset digest from the GitHub release API | Workflow syntax/expression gate; verified SHA-256 |
| python-hcl2 | **7.3.1** in a user venv | Structural parsing for the policy gate; no system changes |
| State locking | S3 `use_lockfile = true`; no `dynamodb_table` | Owner default; DynamoDB locking is deprecated |
| Region layout | Estate `us-east-2`; CloudFront certificate via `aws.us_east_1` alias | Corrects the rev4 §7.2 region statement identified previously |
| Keycloak DB password | `manage_master_user_password = true`; task refers to the secret by ARN and JSON key | No secret value authored, planned or stored in state |
| Keycloak administration | ECS Exec through SSM; no public `/admin` path | Matches the path-allowlist and operator-path design |
| Fail-closed size | Every unknown is a required variable or validated input | Plan fails rather than inventing account/domain/artifact values |

No capacity or quota is presented as an owner decision: CPU/memory/ACU/rate values are module defaults explicitly labelled technical proposals for the G3 cost statement.

---

## 5. Fail-closed inputs still required before any plan/apply

No value below exists in the repository, in a plan, or in state. `terraform plan` cannot succeed until they are supplied.

| Group | Exact missing inputs |
| --- | --- |
| Account / environment | `aws_account_id` (12 digits), `environment`, two `availability_zones` |
| DNS / hosts | `console_fqdn`, `auth_fqdn`, `ws_fqdn`, `acl_fqdn`, `processor_fqdn`, `dns_zone_id`, `console_bucket_name` |
| Certificates | `console_certificate_arn` (us-east-1), `auth_certificate_arn`, `ws_certificate_arn`, `acl_certificate_arn`, `processor_certificate_arn` (us-east-2) |
| State | `state_bucket_arn`, `state_kms_key_arn` (outputs of the bootstrap root), backend `bucket`/`key`/`kms_key_id` |
| OIDC | `github_oidc_subject` (exact classic or immutable form — unverified), whether to create the provider, existing provider ARN if not |
| Audiences / identity | `console_audience`, `acl_audience`, `simulator_audience`, `ws_audience`, `demo_subject` |
| Artifacts | `console_api_artifact`, `mtls_authorizer_artifact`, `ws_authorizer_artifact`, `ws_notifier_artifact` — each with bucket, key, exact S3 version and base64 SHA-256, produced by a real build |
| Service backends | ACL and processor backend Lambda invoke ARNs and names |
| mTLS custody | Two truststore S3 URIs and exact object versions (the bundle content itself is an owner/custody input) |
| Keycloak | `keycloak_image_digest` (mirrored ECR digest), optional one-time bootstrap admin secret ARN, whether private egress needs NAT |
| Realm / clients | Realm import artifact and `console-demo`/`svc-console`/`svc-acl` registration — **not authored while D-5 is open** |
| Operations | `alarm_topic_arn` (no contact invented), cost statement, named approver |

A Keycloak realm is not created by Terraform here. Authoring a realm JSON now would encode unapproved D-5 mechanics; that remains blocked on Requirements adoption and Security confirmation.

---

## 6. Validation executed (local, no cloud)

Tool installation was user-local under `~/.local`, with checksum verification; no system or global configuration was changed.

| # | Command | Result |
| --- | --- | --- |
| 1 | `terraform fmt -recursive -check -diff` | exit 0 (after one formatting pass) |
| 2 | `terraform init -backend=false` + `terraform validate` in `bootstrap/` | init 0, validate 0 — "configuration is valid" |
| 3 | `terraform init -backend=false` + `terraform validate` in `envs/stack/` | init 0, validate 0 — "configuration is valid" |
| 4 | `tflint --init` then `tflint --recursive --format compact` | exit 0, zero issues |
| 5 | `python infra/terraform/tools/policy_checks.py` | 12/12 policy checks PASS, exit 0 |
| 6 | `actionlint .github/workflows/ci.yml terraform.yml` | exit 0 |
| 7 | Secret/static scan (`grep` for key patterns, account literals) | no matches; no `*.tfstate`, `*.tfplan` or `*.tfvars` present |
| 8 | Protected-input recomputation | 11/11 and 5/5 MATCH; SAM hash unchanged |

Raw outputs: `commands/00-tool-versions.txt` through `commands/07-protected-inputs.txt`.

Policy checks executed (structural HCL parsing, not string-only): S3 backend lockfile/encryption and no legacy lock table; no secret literal, SSM plaintext value, decrypt-in-state or Lambda URL; authorizer TTL 0 on request authorizers; mTLS domains pinned and REST default endpoint disabled; OIDC `StringEquals` with the exact subject variable; no IAM `Allow` with `Action: "*"`; no committed state/vars; exact provider pins and `allowed_account_ids`; internet ingress limited to 443; Lambda runtime/architecture pinned; access-log formats free of `Authorization` and `Sec-WebSocket-Protocol`.

### Checks deliberately not executed (truthful gaps)

| Check | Why not | Consequence |
| --- | --- | --- |
| `terraform plan` (any backend) | Needs real inputs and AWS credentials; forbidden in this slice | Resource graph is type-validated but not planned; provider-side semantic errors remain possible |
| `terraform apply` / bootstrap apply | Requires provisioning authorization and a destination | Nothing provisioned |
| Trivy / gitleaks / CodeQL workflow runs | Authored only; pipelines cannot be run here | CI scan coverage is configured, not demonstrated |
| tflint AWS deep rules requiring credentials | Not configured; static rules only | Some deprecated-argument rules may surface in CI |
| Realm/client issuance proofs (D-5 ten-item list) | Requires a running issuer | D-5 remains open |
| CloudFront/WAF log-redaction read-back | Requires deployment | Redaction is configured but unproven |
| `terraform destroy` rehearsal | Requires a provisioned environment | Rollback is specified, not exercised |

Local validation is not independent QA or Security review, and it does not authorize deployment.

---

## 7. Scan coverage summary

| Layer | Tool | Where | Demonstrated? |
| --- | --- | --- | --- |
| Java dependencies | Trivy fs (`vuln`) | `ci.yml` | No (configured) |
| Secrets in repository | gitleaks | `ci.yml` | No (needs `GITLEAKS_LICENSE` for org repos) |
| IaC misconfiguration | Trivy config + tflint + custom policy script | `ci.yml` / local | Local tflint + policy: yes; Trivy: no |
| Java SAST | CodeQL (`java-kotlin`) | `ci.yml` | No (configured) |
| Secret literals in IaC | custom grep + policy script | local | Yes |
| Terraform format/type validity | `fmt`, `validate` | local + CI | Yes (local) |
| Workflow syntax/expressions | actionlint | local | Yes |
| Runtime/daemon scanning | none | — | Missing (out of scope) |

Scanner versions: Terraform 1.17.2, tflint 0.66.0 (+ AWS ruleset 0.50.0), actionlint 1.7.12, python-hcl2 7.3.1. Trivy, gitleaks and CodeQL versions are pinned by action commit SHA, not by a locally executed version.

No security exception is requested or implied by any gap.

---

## 8. Deployment authority, remaining gates and rollback

| Gate | State after this slice |
| --- | --- |
| G0 contract deltas normative / D-5 | **Open** (Requirements slice separate; D-5 still needs Security confirmation against a running issuer) |
| G1 Security design gate | **Open**; no finding closed here |
| G2 IaC authored + local validation | **Substantially met**: authoring + fmt/validate/lint/policy evidence; plan/apply still unexecuted |
| G3 Deployment authorization | **Scope authorized; concrete destination not supplied**; cost statement and named approval still required |
| G4 Provision/bootstrap/realm | **Not started**; blocked by inputs and G3 |
| G5 Independent QA + Security | **Not commissioned** |
| G6 Human acceptance | Not reached |
| G7 Release/rebuild disposition | Procedure belongs to the disposable model; not exercised |

**Rollback / cleanup (designed, not exercised).** Destroy in reverse dependency order per environment (`envs/stack` destroy removes DNS, CloudFront, APIs, Lambdas, WAF, ECS/Aurora, tables, network). The state bucket and CMK are deliberately `prevent_destroy` and survive stack teardown; removing them is a separate human decision. Keycloak is rebuilt from configuration with a fresh database (K-2), so there is no restore path to exercise. Application artifacts are immutable S3 objects; rollback is a redeploy of the prior recorded digest, not an in-place mutation. No cleanup ran.

---

## 9. Corrections and findings carried from this pass

1. **Deployment-authorization wording corrected** (§ opening): scope authorization exists; destination and gates are what is missing.
2. **mTLS default-endpoint control placement**: the REST API's `disable_execute_api_endpoint` is the enforceable switch; the provider does not expose an equivalent flag on the custom-domain resource, so the policy gate asserts the API-level control. The WebSocket default endpoint has no provider-level disable flag — recorded as a residual, not silently claimed.
3. **CloudFront standard logs** are not enabled: legacy CloudFront logging uses bucket ACLs, which conflicts with `BucketOwnerEnforced` on the private console bucket. WAF logging (with credential headers redacted) is enabled instead. This is a visibility gap for cache-level requests and is stated as such.
4. **Issuer WAF scope** is broader than the proposal: the rate rule covers all `/realms/` paths rather than only token and login-action paths. This is a stronger abuse bound with a small chance of throttling legitimate JWKS bursts; it is a configurable technical choice.
5. **Provider breadth in the deploy role** is documented: create/list APIs do not accept ARN-scoped resources, so the role uses `Resource: "*"` for a named service list, bounded by explicit denies (no user/access-key creation, no organizations, no state-bucket or state-key deletion). Tightening this is a G3 task, not a silent omission.
6. **Legacy SAM parity** is not claimed: the Processor → Network → Issuer chain remains SAM-only and preserved.

---

## 10. What this does and does not establish

**Establishes:** a coherent, buildable native-Terraform configuration covering the rev4 component set; local type/format/lint/policy validation with recorded outputs; pinned tools and providers; fail-closed inputs; CI definitions with pinned actions, credential-free PR checks and human-gated plan/apply; a preserved legacy artifact.

**Does not establish:** that the configuration applies successfully; that any AWS resource exists; that scans pass; that the Keycloak realm/clients work (D-5); that the console, mTLS or WebSocket paths behave; that Security or QA accepted anything; or that deployment is authorized beyond the recorded scope.

---

## 11. Output inventory

| File | Purpose |
| --- | --- |
| `PROGRESS.md` | Written first; execution log |
| `devops-report.md` | This report |
| `component-provenance.md` | Rev4 component → Terraform map, gaps |
| `SHA256SUMS` | Hashes of this directory's files (excluding itself) |
| `commands/*.txt` | Exact command outputs for every local check |

No `WORK_ITEM.md`, design/contract/review document, application source, legacy SAM template or other specialist's artifact was modified. The Requirements-owned `continuation-rev2-20261123` directory was not touched.
