# Component provenance map — Terraform authoring 2026-11-23

Basis: `design/access-realization/design-rev4.md` (SHA-256 `3926423e9f42f6949647e5904271b6f6818b977301b66e81ffcf334f10c5dc56`) and the prior readiness report `design/deployment-continuation-20261123/devops-report.md` §6.

Status vocabulary: **AUTHORED** = file exists and is valid HCL; **VALIDATED** = included in `terraform validate` / `tflint` / policy checks; **NOT EXECUTED** = no cloud call, no plan/apply. Every row is NOT EXECUTED.

| Rev4 component | Terraform location | Representative resources | Status | Notes / gap |
| --- | --- | --- | --- | --- |
| Private SPA bucket + OAC | `modules/console/main.tf` | `aws_s3_bucket.console`, public access block, ownership, versioning, SSE, `aws_cloudfront_origin_access_control` | VALIDATED | Bucket name is a required input |
| CloudFront distribution + viewer cert | `modules/console/main.tf` | `aws_cloudfront_distribution.console`, cache/origin-request policies, `viewer_certificate.acm_certificate_arn` | VALIDATED | Uses `aws.us_east_1` provider alias; certificate ARN is a required input |
| Response-header CSP / security headers | `modules/console/main.tf` | `aws_cloudfront_response_headers_policy.console` | VALIDATED | CSP interpolates `console`/`auth`/`ws` FQDNs; no `unsafe-inline` |
| Edge WAF + rate bound | `modules/console/main.tf` | `aws_wafv2_web_acl.console`, rate rule on `/api/*`, logging with redacted `authorization` / `sec-websocket-protocol` | VALIDATED | Redaction support at apply is unverified (no cloud) |
| `/config.json` runtime config object | `modules/console/main.tf` | `aws_s3_object.config` | VALIDATED | Public values only: issuer, client id, audiences, WS URL |
| HTTP API + JWT authorizer | `modules/api/main.tf` | `aws_apigatewayv2_api`, `aws_apigatewayv2_authorizer` (JWT), route `ANY /api/{proxy+}` | VALIDATED | Default execute-api endpoint remains reachable as the CloudFront origin; documented residual |
| Console Lambda + role | `modules/api/main.tf` | `aws_lambda_function.console`, `aws_iam_role.console`, scoped DynamoDB/log policy | VALIDATED | Artifact identity (bucket/key/version/SHA-256) is a required input |
| API access logs without credentials | `modules/api/main.tf` | `aws_apigatewayv2_stage` `access_log_settings` | VALIDATED | Format contains no header fields at all |
| Console → ACL mTLS boundary | `modules/mtls/main.tf` (`mtls_acl`) | `aws_api_gateway_rest_api`, `aws_api_gateway_authorizer` (REQUEST, TTL 0), `aws_api_gateway_domain_name` with `mutual_tls_authentication` | VALIDATED | Truststore URI + exact S3 version required; default execute-api endpoint disabled |
| ACL → processor mTLS boundary | `modules/mtls/main.tf` (`mtls_processor`) | second module instance, scope `sim.iso87a.authorize` | VALIDATED | Backend Lambda invoke ARN/name required |
| mTLS resource policy | `modules/mtls/main.tf` | `aws_lambda_permission.backend`, `aws_lambda_permission.authorizer` | VALIDATED | Backend remains an application deliverable |
| WebSocket API + `$connect` authorizer | `modules/ws/main.tf` | `aws_apigatewayv2_api` (WEBSOCKET), `aws_apigatewayv2_authorizer` (REQUEST, TTL 0), routes `$connect`/`$disconnect` | VALIDATED | MOCK integrations; runtime behaviour at apply unverified |
| WS custom domain | `modules/ws/main.tf` | `aws_apigatewayv2_domain_name`, `aws_apigatewayv2_api_mapping` | VALIDATED | Certificate ARN required; no provider-level flag to disable the default WS endpoint (gap, not silently claimed) |
| Connection records table | `modules/data/main.tf` | `aws_dynamodb_table.this["connections"]` | VALIDATED | TTL attribute optional; record shape is application-owned |
| Notifier + `@connections` scope | `modules/ws/main.tf` | `aws_lambda_function.notifier`, `execute-api:ManageConnections` limited to this API/stage | VALIDATED | Notifier artifact required |
| Keycloak single task | `modules/keycloak/main.tf` | `aws_ecs_cluster`, `aws_ecs_task_definition`, `aws_ecs_service` (desired 1), `enable_execute_command` | VALIDATED | Image digest required (`@sha256:`); ECR mirror/NAT is a deployment input |
| Keycloak database | `modules/keycloak/main.tf` | `aws_rds_cluster` (Aurora PostgreSQL, Serverless v2), `manage_master_user_password`, one `db.serverless` instance | VALIDATED | No password authored; backup retention is not a recovery promise (K-2) |
| Issuer ALB + path allowlist | `modules/keycloak/main.tf` | `aws_lb`, `aws_lb_listener` (deny-by-default 403), five allowlist rules, WAF association | VALIDATED | `/admin/*`, `/realms/master/*`, `/health*`, `/metrics` are not allowlisted |
| Issuer WAF rate limit | `modules/keycloak/main.tf` | `aws_wafv2_web_acl.keycloak` (REGIONAL), `/realms/` rate rule, logging with redacted headers | VALIDATED | Broader than the proposed token/login-only scope (covers all `/realms/`); recorded |
| Registry / journal / events / issuer truth / assertion replay | `modules/data/main.tf` | six `aws_dynamodb_table` resources with `pk`/`sk` | VALIDATED | TTL attributes are parameters; NV-05 mapping not invented |
| Evidence admission (one function per principal) | not authored | — | GAP | Requires application packaging and SR2-01 handler split; no application code in this slice |
| Legacy processor → network → issuer chain | `infra/template.yaml` (preserved) | — | GAP | Terraform parity deliberately not claimed; SAM bytes unchanged (`f7637650…`) |
| Per-function roles / explicit deny | `modules/identity/main.tf` + module roles | `aws_iam_policy.runtime_guardrail` (deny outside Lambda context, cross-account, security-service tampering), scoped module policies | VALIDATED | Broad create permissions are documented and deny-guarded; G3 should tighten |
| GitHub OIDC provider + deploy role | `modules/identity/main.tf` | `aws_iam_openid_connect_provider`, `aws_iam_role.deploy` with exact `sub` via `StringEquals` | VALIDATED | Subject string is a required input; immutable form unverified |
| S3 state backend | `bootstrap/main.tf`, `envs/stack/backend.tf` | versioned bucket, SSE-KMS CMK, BPA, TLS-only policy, principal allowlist, `use_lockfile`, `prevent_destroy` | VALIDATED | Bootstrap is a separate root with local state; no `dynamodb_table` |
| Observability | `modules/observability/main.tf` + per-module log groups | 7-day retention, API 5XX/4XX, Lambda errors, DynamoDB throttles, ALB 5XX/unhealthy, ECS task count | VALIDATED | No notification contact until `alarm_topic_arn` is supplied |
| DNS records | `envs/stack/dns.tf` | Route 53 alias records for console, auth, ws, ACL, processor | VALIDATED | Requires one hosted zone id input |
| Realm / client / key registration | not authored | — | GAP | D-5 remains open; no realm JSON or secret is invented |
| CloudFront standard request logs | not authored | — | GAP | Bucket-ACL conflict with BucketOwnerEnforced; WAF logging covers request visibility |

## Preserved artifacts

| Artifact | SHA-256 | State |
| --- | --- | --- |
| `infra/template.yaml` (legacy SAM) | `f7637650095ea5166aee9e3d41dbad3d033324c45d48268c70d2bb58d62c1abe` | unchanged |
| `design/access-realization/design-rev4.md` | `3926423e9f42f6949647e5904271b6f6818b977301b66e81ffcf334f10c5dc56` | unchanged |
| `handoffs/continuation-20261123/next-slices.json` | `50d1cfa18ed7c9ae822f4fef1ae989f1eada7536aaedde1d1b6ce2715b917b5f` | unchanged |
| `design/contract-deltas/continuation-rev2-20261123/` | — | not read for edits, not modified (Requirements-owned) |
