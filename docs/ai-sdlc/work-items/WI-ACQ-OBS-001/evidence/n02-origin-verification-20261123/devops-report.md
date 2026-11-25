# DevOps report — N-02 closure (owner Option B: CloudFront origin-verification header)

Work item: `WI-ACQ-OBS-001`. Date: 2026-11-23 (America/Bogota; command run at
2026-11-24T03:11Z). Author: ai-sdlc-devops (subagent run
`agent:ai-sdlc-devops:subagent:12995beb-a3e3-4f33-9e8a-1f5bd232c82f`).

Runtime model: **`nan/deepseek-v4-flash`**, the configured default. No model
override was requested, no fallback was activated, and none is invented here.

## 1. Authority, pins and scope

| Input | Pin recomputed by me | Result |
| --- | --- | --- |
| `reviews/continuation-rev3-security-20261123/security-review.md` | `f5696ed917f009c7569cccbba5f42b431170d15a53ef06bdd38c10e61d53ba50` | **matches** the assignment pin |
| `owner-decisions.md` §"N-02 closure (Option B) and normative adoption — 2026-11-23" | read in full; Fernando Paz, verbatim "adelante con lo recomendado" | binding conditions carried |
| `design/contract-deltas/n02-origin-verification-20261123/` (Requirements, **DRAFT / not normative**) | read-only input, discovered mid-run | used to align identifiers and the four extra assertions; not treated as approved |

Owner conditions implemented: (1) the boundary check is contract-first —
Requirements authored the delta in parallel; (2) the value is Terraform-generated,
never a literal/tfvars/CI value, with a policy assertion; (3) the header name
avoids the CloudFront denylist and any semantic header; (4) denials are logged
and observable — the application-side record and metric are Implementation's
deliverable, and this report states exactly what remains.

Write scope used: `infra/terraform/` (all edits), `.github/workflows/` (inspected
only — **no change needed or made**), and the new evidence directory
`docs/ai-sdlc/work-items/WI-ACQ-OBS-001/evidence/n02-origin-verification-20261123/`.
No application code, contract, contract delta, `WORK_ITEM.md`, `owner-decisions.md`
or reviewed report was modified. Application code, `src/`, `console-ui/` and
`docs/ai-sdlc/work-items/.../design/` were read only where needed for the header
inventory.

## 2. What is authored

### 2.1 Value generation (one generator, stack root)

`envs/stack/main.tf`:

```hcl
resource "random_password" "origin_verify" {
  length      = 48
  upper       = true
  lower       = true
  numeric     = true
  special     = false # alphanumeric keeps it an RFC 7230 token: safe in a header
  min_upper   = 8
  min_lower   = 8
  min_numeric = 8
}
```

48 alphanumeric characters with ≥8 of each class ≈ 285 bits, fixed length, closed
token-safe alphabet, no punctuation. It is created in the stack root, not in a
reusable module, so no second instantiation can produce a diverging value.
`hashicorp/random` is pinned exactly (`= 3.9.1`) in `envs/stack/versions.tf` and
its release hashes are recorded in `envs/stack/.terraform.lock.hcl` (intended
lock-file change, see §7).

### 2.2 Header injection (CloudFront `/api/*` origin only)

`modules/console/main.tf`: `local.origin_verify_header = "X-Origin-Verify"`, and
inside the existing `origin` block that carries `custom_origin_config`:

```hcl
custom_header {
  name  = local.origin_verify_header
  value = var.origin_verify_secret
}
```

Exactly one custom header on the custom origin. It is **not** added by the
viewer-request CloudFront Function, a WAF rule, the response-headers policy or the
application; policy check 18 fails if the name appears in any CloudFront Function
source or if the `/api/*` behaviour gains a `function_association`.

### 2.3 Value custody and delivery

- CloudFront consumer: `modules/console` variable `origin_verify_secret`,
  `sensitive = true`, no default, validated `length >= 32` and RFC 7230
  token-safe.
- Console Lambda consumer: `modules/api` variable `edge_origin_verify`,
  `sensitive = true`, no default, same validation; mapped into the function
  environment as `EDGE_ORIGIN_VERIFY`.
- Both are fed from the single `random_password.origin_verify.result` in
  `envs/stack/main.tf`. No tfvars, no CI variable, no SSM parameter, no Secrets
  Manager copy, no artifact, no output.

Sensitivity: `random_password.result` is `sensitive: true` in the pinned provider
schema (recorded in `commands/08-provider-schema.txt`). The AWS provider does not
mark `custom_header.value` sensitive, so redaction in plan/apply output comes from
the sensitive variable propagating through the expression — that is the mechanism
relied on, stated plainly rather than assumed.

**Deviation from the owner's literal wording, recorded and not glossed over.**
The owner decision says the value "exists only in encrypted remote state". The
enforcement point cannot compare a value it does not have, and the Requirements
delta (draft) fixes the delivery as `EDGE_ORIGIN_VERIFY` with two provider-owned
runtime copies declared unavoidable (§8.2) and bounded by IAM least privilege on
`cloudfront:GetDistributionConfig` and `lambda:GetFunctionConfiguration`. I
implemented the contract-consistent wiring because the alternative — authoring a
header nothing can verify — would leave the finding unclosed while claiming it
closed. This widening of the custody wording needs explicit acknowledgment by the
Security re-reviewer and, if the wording is treated as binding, by the owner.

IAM bounding is **not** authored here, deliberately: the deployment role must
read distribution and function configuration to plan/apply (§8.2 of the delta),
so a deny-scoped policy inside this tree would break the pipeline rather than
bound the value. The real bound is account/permission-set isolation of the deploy
role and break-glass access; that is an identity/operations decision recorded as
a blocker in §7, not silently claimed.

### 2.4 Leak exclusion

| Surface | State authored |
| --- | --- |
| CloudFront standard/real-time logs | Not enabled (pre-existing bucket-ACL conflict, unchanged); no field can carry request headers. |
| API Gateway access log | Closed explicit `$context` field list; contains no request-header field of any kind and no custody value. |
| WAF logs (CLOUDFRONT scope) | Viewer-side only, so the injected value never reaches them; header name `x-origin-verify` redacted anyway as defence in depth against a future forwarding change. |
| Application / Lambda | `EDGE_ORIGIN_VERIFY` is function configuration, never a log field; no authored log format references it. |
| Outputs / tfvars / CI / artifacts | None. |
| Responses | The console module never echoes the value. |

### 2.5 Machine assertions (`tools/policy_checks.py`)

Check 12 extended: any authored `jsonencode` log format containing
`Authorization`, `Sec-WebSocket-Protocol` or `x-origin-verify` fails, and any
`$context.request.header.*` field fails.

Check 18 added (PASS line now emitted unconditionally, the reporting defect the
Requirements delta flagged is fixed):

| Sub-check | Assertion |
| --- | --- |
| `origin-verify-name` / `origin-verify-denied-name` | Fixed local equals `X-Origin-Verify`; rejects the full CloudFront denylist, `X-Amz-`/`X-Edge-` prefixes and `X-Real-Ip`. |
| `origin-verify-header` / `origin-verify-header-name` | Exactly one `custom_header` on the custom origin, name from the fixed local. |
| `origin-verify-value-source` / `origin-verify-not-generated` / `origin-verify-generator` / `origin-verify-entropy` | Value expression is `var.origin_verify_secret`; fed by `random_password.origin_verify.result`; `length >= 32`; `special = false`. |
| `origin-verify-sensitive` / `origin-verify-default` / `origin-verify-literal` / `origin-verify-output` / `origin-verify-ci` | Both consumer variables sensitive with no default; no literal assignment; no output; no workflow reference. |
| `origin-verify-waf-redaction` | WAF logging redacts `x-origin-verify`. |
| `origin-verify-env` *(added for the contract delta)* | `modules/api.edge_origin_verify` sensitive and default-free; the console Lambda maps `EDGE_ORIGIN_VERIFY` from it; the stack root feeds it from the same generator. |
| `origin-verify-cache-disabled` *(added)* | The `/api/*` behaviour resolves an authored cache policy with `default_ttl = 0` and `max_ttl = 0`. |
| `origin-verify-single-origin` *(added)* | Exactly one CloudFront origin resolves `var.http_api_domain_name`, and `module.api.origin_domain_name` is wired exactly once. |
| `origin-verify-not-in-function` *(added)* | The header name appears in no CloudFront Function source, and `/api/*` has no `function_association`. |

### 2.6 Hygiene

New `infra/terraform/.gitignore` (`.terraform/`, `*.tfstate*`, `*.tfplan`,
`crash.log`) so local state cannot be committed. The repository root `.gitignore`
was out of scope and still does not cover Terraform state; that gap is recorded in
§7 rather than edited.

### 2.7 Documentation

`infra/terraform/README.md` now carries: the authored configuration table, the
denylist justification for `X-Origin-Verify`, the vendor-documented overwrite
semantics with the cited wording, the exact Implementation interface (`X-Origin-Verify`,
`EDGE_ORIGIN_VERIFY`, 403 `EDGE_ORIGIN_NOT_VERIFIED`, denial record and metric
names), and the explicit list of shapes kept intact.

### 2.8 Deliberately not changed

`disable_execute_api_endpoint = true` on the HTTP and WS APIs; the dedicated
regional custom-domain origin and Route 53 alias; every fail-closed required
input and its validation; the JWT authorizer and no-anonymous-path property; stage
throttling; the response-headers policy; the removal of `custom_error_response`
and the SPA viewer-request function; and policy checks 1–17, which still pass
unchanged. `.github/workflows/` is untouched and still parses (actionlint).

## 3. What is locally validated (evidence: `commands/`)

| # | Command | Result |
| --- | --- | --- |
| 00 | tool versions, environment, model | Terraform 1.17.2, tflint 0.66.0, actionlint 1.7.12, python-hcl2 7.3.1 |
| 01 | `terraform fmt -recursive -check -diff` | exit 0, no diff |
| 02 | `bootstrap`: `init -backend=false` + `validate` | `Success! The configuration is valid.` |
| 03 | `envs/stack`: `init -backend=false` + `validate` | `Success! The configuration is valid.` |
| 04 | `tflint --init && tflint --recursive --format compact` | exit 0, no findings |
| 05 | `actionlint .github/workflows/*.yml` | exit 0 |
| 06 | `python tools/policy_checks.py` | 18/18 PASS, exit 0 |
| 07 | `commands/negative-tests.sh` (15 mutations + control) | control passes; every mutation fails with the expected check id |
| 08 | `terraform providers schema -json` extract | `custom_header` = set of required `{name, value}`; `random_password.result` sensitive |
| 09 | vendor doc capture (URL, digest, quoted passages) | override semantics + denylist + anti-bypass use case confirmed |
| 10 | leak scan over the whole checkout | no state/plan/tfvars, no `.terraform`, no literal, no CI variable, no key/account literal |

Negative tests prove the assertions bite: literal value, reserved `X-Amz-` name,
output exposure, header in a log format, removed WAF redaction, CI variable,
removed custom header, defaulted variable, length 16, `special = true`, removed
`EDGE_ORIGIN_VERIFY` mapping, non-sensitive variable, caching re-enabled, second
origin resolving `api_fqdn`, header name in CloudFront Function source.

## 4. Deliberately unproven until deployment

Nothing below is executed, and none of it may be read as passing by absence:

1. That CloudFront actually injects the header on origin requests and overwrites a
   viewer-supplied value at runtime (documented behaviour, not observed).
2. That the HTTP API forwards the header and the duplicate/coalescing form
   unmodified to the Lambda.
3. That `plan`/`apply` are clean, that the value prints as `(sensitive value)`, and
   that the state stays encrypted (no backend was contacted; no plan was run).
4. That no log format anywhere contains the value, verified by read-back of real
   records (CloudFront, WAF, API Gateway, Lambda, ADOT).
5. That the denial record and `ConsoleOriginVerificationDenied` metric are emitted
   and visible — the metric filter and alarm are **not authored** (§6, item 3).
6. That the boundary check is present, fail-closed and unbypassable — Implementation's
   `OV-*` behavioural assertions.
7. Coordinated rotation behaviour (a rotation is a total console outage between the
   two updates; no automation exists).
8. That no second path to the console API exists.
9. The adjacent WebSocket direct-path surface, which the owner's N-02 decision does
   not cover (the Requirements delta records it as an open item).

## 5. Retest criteria Security will use

Mapped to the Security review's N-02 retest criteria and the Requirements delta
§10.1. All are deployment-gate executions:

1. Direct request to the retired `execute-api` host is refused.
2. Direct request to `api_fqdn` with a valid token and **no** `X-Origin-Verify` →
   `403` with body `{"version":1,"error":"EDGE_ORIGIN_NOT_VERIFIED","action":null}`,
   one `console_origin_verification_denied` record, and the value absent from every
   log format.
3. Forged unauthenticated request through the edge still returns the contract 401
   JSON and is not rewritten to `index.html` (N-03 preservation).
4. Authenticated direct-path traffic reaches no business action, data or disclosure.
5. Read-back of CloudFront, WAF, API Gateway, application and ADOT logs finds no
   copy of the value; `terraform output` exposes nothing.
6. The metric is emitted on a denial and is visible without the value.
7. Edge path with a viewer-forged value behaves normally (CloudFront overwrote it);
   the anti-bypass proof.
8. `EDGE_ORIGIN_VERIFY` unset presents a platform 5xx and never a fail-open 2xx.

## 6. Open items I did not author (recorded, not silently omitted)

1. **IAM bounding of the two runtime copies** (§2.3). Needs an identity/operations
   decision; a deny inside the deployment role would break plan/apply.
2. **`ConsoleOriginVerificationDenied` metric filter and alarm.** A log metric
   filter on the console Lambda log group with pattern
   `{ $.event = "console_origin_verification_denied" }`, dimension `reason`, is the
   natural IaC form, but it depends on Implementation's frozen JSON log shape and
   the alarm threshold is a deployment input (§10 of the delta forbids inventing
   one). Not authored, so that no check looks green while matching nothing.
3. **Root `.gitignore`** does not cover `*.tfstate`; only the new
   `infra/terraform/.gitignore` does. Root file outside my write scope.
4. **`bootstrap/.terraform.lock.hcl`** still pins only `aws`; unchanged because
   bootstrap needs no new provider.

## 7. Limitations and blockers

- **Local-only.** No `plan`, no `apply`, no backend, no AWS or Keycloak call, no
  credential flow, no push. `terraform init -backend=false` downloaded provider
  plugins from the public registry only.
- **Process deviation, disclosed.** Before re-reading the role constraint that all
  Git operations go through Coordinator, I ran one read-only `git rev-parse HEAD`
  and one `git status --porcelain` in the external checkout. HEAD was
  `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14`, matching the historical binding in
  the Security review. No Git mutation, no commit, no push, no provider API call
  was made, and no later Git command was run. Branch/commit evidence for handoff
  must be obtained by Coordinator through the configured coding runtime.
- **Lock file churn.** `envs/stack/.terraform.lock.hcl` gained the `random 3.9.1`
  entries for this platform. Reviewers should confirm the hashes are committed for
  the CI platform (linux/amd64) and accept per-platform hash additions.
- **Pin drift against the Requirements delta.** The delta's `INPUTS.SHA256SUMS`
  pins the Terraform bytes it read while this run was still editing them, so a
  reviewer must re-pin against the bytes listed in `CHANGED-FILES.sha256` (all
  ten verify) rather than trust the delta's "authored" column as current.
- **Model/runtime.** `nan/deepseek-v4-flash`, the configured default; recorded
  because the assignment asked which model produced the work.
- **Blocking item for closure.** The custody widening in §2.3 requires reviewer or
  owner acknowledgment; until then the tree implements the contract delta's
  delivery mechanism, and the owner's "state-only" wording is exceeded by two
  provider-owned copies.

## 8. Changed files

Product repository (all under `infra/terraform/`):

- `modules/console/main.tf` — fixed header local, `custom_header`, WAF redaction, comments.
- `modules/console/variables.tf` — `origin_verify_secret` (sensitive, no default, validated).
- `modules/api/main.tf` — `EDGE_ORIGIN_VERIFY` environment entry.
- `modules/api/variables.tf` — `edge_origin_verify` (sensitive, no default, validated).
- `envs/stack/main.tf` — `random_password.origin_verify` and the two consumer wirings.
- `envs/stack/versions.tf` — `hashicorp/random` pinned `= 3.9.1`.
- `envs/stack/.terraform.lock.hcl` — random 3.9.1 release hashes (generated by `init`, reviewed).
- `tools/policy_checks.py` — check 12 extension, check 18 with nine sub-check groups, helpers.
- `README.md` — N-02 closure section, interface, custody and residual documentation.
- `.gitignore` — new; keeps state/plans/provider dirs out of the tree.

Evidence (this directory): `PROGRESS.md`, `devops-report.md`,
`CHANGED-FILES.sha256`, `SHA256SUMS`, and `commands/00`–`10` including the
negative-test harness and the captured vendor page.

`.github/workflows/` — unchanged.
