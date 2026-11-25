#!/usr/bin/env python3
"""Static policy checks for the acquiring-simulator Terraform tree.

Implements the assertions from design configuration-schema section 9 plus the
hard constraints in the deployment-continuation report section 6. It does not
call any cloud API and does not need Terraform credentials.

Exit code 0 = every check passed; 1 = at least one violation; 2 = tool error.
"""

from __future__ import annotations

import json
import pathlib
import re
import sys

import hcl2

ROOT = pathlib.Path(__file__).resolve().parents[1]  # infra/terraform
FAILURES: list[str] = []
CHECKS: list[str] = []

# N-02 closure (owner Option B): one fixed header name, one place.
ORIGIN_VERIFY_HEADER = "X-Origin-Verify"
# Headers CloudFront refuses to add as origin custom headers (AWS developer
# guide, "Custom headers that CloudFront can't add to origin requests"), plus
# the credential headers this tree must never forward as a control value.
CF_DENIED_HEADERS = {
    "cache-control",
    "connection",
    "content-length",
    "cookie",
    "host",
    "if-match",
    "if-modified-since",
    "if-none-match",
    "if-range",
    "if-unmodified-since",
    "max-forwards",
    "pragma",
    "proxy-authenticate",
    "proxy-authorization",
    "proxy-connection",
    "range",
    "request-range",
    "te",
    "trailer",
    "transfer-encoding",
    "upgrade",
    "via",
    "x-real-ip",
    "authorization",
}
# Header names whose values must never appear in an authored log format.
TOKEN_HEADERS = ("authorization", "sec-websocket-protocol", ORIGIN_VERIFY_HEADER.lower())
# Variables that carry the origin-verification value, and the only expressions
# allowed to feed them (the single root generator, and the module inputs).
CUSTODY_VARS = ("origin_verify_secret", "edge_origin_verify")
ALLOWED_CUSTODY_SOURCES = (
    "random_password.origin_verify.result",
    "var.origin_verify_secret",
    "var.edge_origin_verify",
)


def as_list(value):
    """hcl2 returns a single block as a dict and repeated blocks as a list."""
    if value is None:
        return []
    if isinstance(value, list):
        return value
    return [value]


def fail(check: str, detail: str) -> None:
    FAILURES.append(f"{check}: {detail}")


def ok(check: str) -> None:
    CHECKS.append(check)


def walk(node, path=""):
    """Yield (path, value) for every dict/list pair, recursively."""
    if isinstance(node, dict):
        for key, value in node.items():
            yield f"{path}.{key}", value
            yield from walk(value, f"{path}.{key}")
    elif isinstance(node, list):
        for index, value in enumerate(node):
            yield from walk(value, f"{path}[{index}]")


def blocks(parsed, kind):
    """Yield (type, name, body) for resource/data blocks."""
    for block in parsed.get(kind, []) or []:
        for type_name, named in block.items():
            for name, body in named.items():
                yield type_name, name, body


def resources(parsed):
    return blocks(parsed, "resource")


def datas(parsed):
    return blocks(parsed, "data")


def load_all():
    files = {}
    for path in sorted(ROOT.rglob("*.tf")):
        if ".terraform" in path.parts:
            continue
        try:
            files[path] = hcl2.loads(path.read_text())
        except Exception as exc:  # noqa: BLE001
            fail("parse", f"{path.relative_to(ROOT)}: {exc}")
    return files


def main() -> int:
    files = load_all()
    if not files:
        print("FAIL: no Terraform files found")
        return 2

    # 1. Backend hardening: every s3 backend has use_lockfile and encrypt.
    for path, parsed in files.items():
        for tf in parsed.get("terraform", []) or []:
            for backend in tf.get("backend", []) or []:
                for kind, body in backend.items():
                    if kind != "s3":
                        fail("backend-type", f"{path.name}: unexpected backend {kind}")
                        continue
                    if body.get("use_lockfile") is not True:
                        fail("backend-lockfile", f"{path.relative_to(ROOT)}: use_lockfile must be true")
                    if body.get("encrypt") is not True:
                        fail("backend-encrypt", f"{path.relative_to(ROOT)}: encrypt must be true")
                    if "dynamodb_table" in body:
                        fail("backend-legacy-lock", f"{path.relative_to(ROOT)}: deprecated dynamodb_table present")
    ok("backend: s3 + use_lockfile + encrypt, no dynamodb_table")

    raw_text = {p: p.read_text() for p in files}

    # 2. No deprecated DynamoDB locking anywhere.
    for path, text in raw_text.items():
        if re.search(r"\bdynamodb_table\s*=", text):
            fail("legacy-lock", f"{path.relative_to(ROOT)}: dynamodb_table assignment")
    ok("no deprecated DynamoDB lock table")

    # 3. No secret material authored into parameters or outputs.
    secret_like = re.compile(r"(BEGIN [A-Z ]*PRIVATE KEY|AKIA[0-9A-Z]{16}|client_secret\s*=\s*\"[^\"]+\")")
    for path, text in raw_text.items():
        if secret_like.search(text):
            fail("secret-literal", f"{path.relative_to(ROOT)}: secret-like literal")
        for parsed in [files[path]]:
            for type_name, name, body in resources(parsed):
                if type_name == "aws_ssm_parameter":
                    for key in ("value", "value_wo"):
                        val = body.get(key)
                        if isinstance(val, str) and "${" not in val and val.strip():
                            fail("ssm-secret-value", f"{name}.{key} authors a literal value")
                if type_name == "aws_lambda_function_url":
                    fail("lambda-url", f"{name}: alternate ingress resource")
            for type_name, name, body in datas(parsed):
                if type_name == "aws_ssm_parameter" and body.get("with_decryption") is True:
                    fail("ssm-decrypt-in-state", f"{name}: with_decryption = true")
    ok("no secret literal, SSM plaintext value, decrypt-in-state or Lambda URL")

    # 4. Authorizer caching must be zero on both API types.
    for path, parsed in files.items():
        for type_name, name, body in resources(parsed):
            if type_name in ("aws_apigatewayv2_authorizer", "aws_api_gateway_authorizer"):
                # TTL 0 is required for request-based authorizers. A JWT
                # authorizer has no result cache to disable.
                if body.get("authorizer_type") == "REQUEST" or type_name == "aws_api_gateway_authorizer":
                    if body.get("authorizer_result_ttl_in_seconds") != 0:
                        fail("authorizer-ttl", f"{name}: authorizer_result_ttl_in_seconds must be 0")
    ok("every service authorizer has result TTL 0")

    # 5. mTLS custom domains must carry a pinned truststore, and the backing
    # REST API must disable its default execute-api endpoint so the custom
    # domain is the only ingress.
    domain_count = 0
    mtls_rest_apis = 0
    for path, parsed in files.items():
        for type_name, name, body in resources(parsed):
            if type_name == "aws_api_gateway_rest_api":
                mtls_rest_apis += 1
                if body.get("disable_execute_api_endpoint") is not True:
                    fail("mtls-default-endpoint", f"{name}: disable_execute_api_endpoint must be true")
            if type_name == "aws_api_gateway_domain_name":
                domain_count += 1
                mtls = body.get("mutual_tls_authentication")
                if not mtls:
                    fail("mtls-missing", f"{name}: mutual_tls_authentication missing")
                else:
                    block = mtls[0] if isinstance(mtls, list) else mtls
                    if "truststore_version" not in block:
                        fail("mtls-version", f"{name}: truststore_version must be an explicit input")
                    version = block.get("truststore_version")
                    if isinstance(version, str) and version.lower() == "latest":
                        fail("mtls-latest", f"{name}: truststore_version must not be latest")
    if domain_count == 0:
        fail("mtls-none", "no REST mTLS custom domain found")
    ok(f"mTLS domains checked ({domain_count}); default execute-api endpoint disabled on {mtls_rest_apis} REST APIs")

    # 6. OIDC trust must be exact-string and wildcard-free; trust policies use StringEquals.
    sub_docs = 0
    for path, parsed in files.items():
        for type_name, name, body in datas(parsed):
            if type_name != "aws_iam_policy_document":
                continue
            statements = body.get("statement", []) or []
            sub_statements = [
                st for st in statements
                if any(str(c.get("variable", "")).endswith(":sub") for c in (st.get("condition", []) or []))
            ]
            if not sub_statements:
                continue
            sub_docs += 1
            for st in sub_statements:
                conditions = st.get("condition", []) or []
                tests = [c.get("test") for c in conditions]
                if "StringLike" in tests or "StringEqualsIgnoreCase" in tests:
                    fail("oidc-condition", f"{name}: sub condition must be StringEquals")
                if "StringEquals" not in tests:
                    fail("oidc-condition", f"{name}: sub condition must use StringEquals")
                if "github_oidc_subject" not in json.dumps(conditions):
                    fail("oidc-subject", f"{name}: sub condition must use the exact subject variable")
    if sub_docs == 0:
        fail("oidc-subject", "no OIDC sub trust condition found")
    ok(f"OIDC trust: StringEquals with exact subject variable ({sub_docs} policy document(s))")

    # 7. No Allow statement with Action "*".
    for path, parsed in files.items():
        for type_name, name, body in datas(parsed):
            if type_name != "aws_iam_policy_document":
                continue
            for st in body.get("statement", []) or []:
                if st.get("effect") == "Allow" and "*" in (st.get("actions") or []):
                    fail("iam-wildcard-action", f"{name}: Allow with Action \"*\"")
    for path, text in raw_text.items():
        if re.search(r'resources\s*=\s*\[\s*"\*"\s*\]', text) and "runtime_guardrail" not in text:
            if "Deny" not in text and "ViaService" not in text and "SimulatorServiceManagement" not in text:
                fail("iam-wildcard-resource", f"{path.relative_to(ROOT)}: Resource \"*\" without Deny")
    ok("no IAM Allow Action \"*\"; Resource \"*\" only in documented, conditioned or deny-guarded policies")

    # 8. No committed tfvars/state/plan files.
    forbidden = [
        p for p in ROOT.rglob("*")
        if p.is_file() and (
            p.suffix in (".tfstate", ".tfplan")
            or p.name.endswith(".tfstate.backup")
            or (p.suffix == ".tfvars")
        )
    ]
    for p in forbidden:
        fail("committed-state-or-vars", str(p.relative_to(ROOT)))
    ok("no *.tfstate, *.tfplan or *.tfvars in the tree")

    # 9. Providers pinned exactly and account allow-list present in every root provider.
    for path in [ROOT / "bootstrap" / "versions.tf", ROOT / "envs" / "stack" / "versions.tf"]:
        parsed = hcl2.loads(path.read_text())
        for tf in parsed.get("terraform", []) or []:
            for required in tf.get("required_providers", []) or []:
                for source, body in required.items():
                    version = str(body.get("version", ""))
                    if not version.startswith("="):
                        fail("provider-pin", f"{path.name}: {source} must be pinned exactly")
        providers = parsed.get("provider", []) or []
        if not providers:
            fail("provider-block", f"{path.name}: no provider block")
        for provider in providers:
            for name, body in provider.items():
                if body.get("allowed_account_ids") != ["${var.aws_account_id}"]:
                    text = json.dumps(body)
                    if "allowed_account_ids" not in text:
                        fail("account-guard", f"{path.name}: provider {name} lacks allowed_account_ids")
    ok("exactly pinned providers; every root provider has allowed_account_ids")

    # 10. Ingress: only 443 may be open to 0.0.0.0/0.
    for path, parsed in files.items():
        for type_name, name, body in resources(parsed):
            if type_name == "aws_vpc_security_group_ingress_rule":
                if body.get("cidr_ipv4") == "0.0.0.0/0":
                    if body.get("from_port") not in (443, 80):
                        fail("ingress-wide", f"{name}: {body.get('from_port')} open to the internet")
                    if body.get("from_port") == 80:
                        fail("ingress-http", f"{name}: plain HTTP open to the internet")
    ok("internet ingress restricted to 443")

    # 11. Every Lambda has an explicit runtime and architecture (no provider default drift).
    for path, parsed in files.items():
        for type_name, name, body in resources(parsed):
            if type_name == "aws_lambda_function":
                if "runtime" not in body:
                    fail("lambda-runtime", f"{name}: runtime not pinned")
                if "architectures" not in body:
                    fail("lambda-arch", f"{name}: architectures not set")
    ok("every Lambda pins runtime and architectures")

    # 12. Response/log formats never print credentials: assert the header names
    # are absent from access-log format blocks and present as redactions where
    # redaction is supported. Includes the N-02 origin-verification header.
    for path, text in raw_text.items():
        for match in re.finditer(r"format\s*=\s*jsonencode\(\{(.*?)\}\)", text, re.S):
            body = match.group(1).lower()
            for header in TOKEN_HEADERS:
                if header in body:
                    fail("log-token-leak", f"{path.relative_to(ROOT)}: credential/custody field '{header}' in access log format")
        # CloudFormation/API Gateway also supports per-header log fields; this
        # tree logs no request header at all, so any header reference is a leak.
        if re.search(r"\$context\.request\.headers?\.", text):
            fail("log-header-leak", f"{path.relative_to(ROOT)}: access log format reads a request header")
    ok("access-log formats exclude Authorization, Sec-WebSocket-Protocol and X-Origin-Verify, and log no request header")

    # 13. N-02 (rev2 Security SEC-I1): no API may keep its default execute-api
    # endpoint, and the CloudFront /api/* origin must be the dedicated custom
    # domain rather than the execute-api hostname.
    http_apis = 0
    for path, parsed in files.items():
        for type_name, name, body in resources(parsed):
            if type_name == "aws_apigatewayv2_api":
                http_apis += 1
                if body.get("disable_execute_api_endpoint") is not True:
                    fail("api-default-endpoint", f"{name}: disable_execute_api_endpoint must be true")
    root_main = raw_text.get(ROOT / "envs" / "stack" / "main.tf", "")
    if "module.api.api_endpoint" in root_main:
        fail("origin-execute-api", "envs/stack/main.tf: CloudFront origin must not use module.api.api_endpoint")
    if "module.api.origin_domain_name" not in root_main:
        fail("origin-custom-domain", "envs/stack/main.tf: CloudFront origin must use module.api.origin_domain_name")
    api_domains = sum(
        1
        for path, parsed in files.items()
        for type_name, name, body in resources(parsed)
        if type_name == "aws_apigatewayv2_domain_name"
    )
    if api_domains == 0:
        fail("origin-domain-missing", "no aws_apigatewayv2_domain_name for the console API origin")
    ok(
        f"every HTTP/WS API disables the default endpoint ({http_apis}); "
        f"CloudFront API origin is the dedicated custom domain ({api_domains} domain(s))"
    )

    # 14. N-03 (rev2 Security SEC-I2): no distribution-wide error rewriting.
    # custom_error_response cannot be scoped to one cache behavior, so it would
    # mask API 401/403/404 JSON bodies. SPA deep links are handled by the
    # viewer-request function instead.
    for path, parsed in files.items():
        for type_name, name, body in resources(parsed):
            if type_name == "aws_cloudfront_distribution" and body.get("custom_error_response"):
                fail("edge-error-rewrite", f"{name}: custom_error_response rewrites API errors distribution-wide")
    console_main = raw_text.get(ROOT / "modules" / "console" / "main.tf", "")
    if "aws_cloudfront_function" not in console_main or "function_association" not in console_main:
        fail("spa-fallback", "modules/console: SPA deep-link fallback function/association missing")
    ok("no CloudFront custom_error_response; API status/body pass through; SPA fallback is a viewer-request function")

    # 15. N-06 (rev2 Security SEC-I5): the append-only event source has no
    # standalone write path; events are written only in the atomic transaction.
    event_checked = 0
    for path, parsed in files.items():
        for type_name, name, body in datas(parsed):
            if type_name != "aws_iam_policy_document" or name != "console":
                continue
            for statement in body.get("statement", []) or []:
                if statement.get("sid") != "EventReadAppend":
                    continue
                event_checked += 1
                actions = statement.get("actions") or []
                if "dynamodb:PutItem" in actions:
                    fail("event-direct-append", "console role grants PutItem on the event table")
                if "dynamodb:TransactWriteItems" not in actions:
                    fail("event-atomic-write", "console role lacks TransactWriteItems on the event table")
    if event_checked == 0:
        fail("event-statement-missing", "console role has no EventReadAppend statement")
    ok("console role writes events only through TransactWriteItems (no standalone PutItem)")

    # 16. N-05 (rev2 Security SEC-I4): the issuer WAF must not inspect request
    # bodies (Keycloak login credentials are form-posted), and OAuth flow query
    # parameters stay redacted in its logs.
    keycloak_main = raw_text.get(ROOT / "modules" / "keycloak" / "main.tf", "")
    if re.search(r"field_to_match\s*\{\s*body", keycloak_main):
        fail("issuer-waf-body", "issuer WAF inspects the request body; credentials could reach logs")
    if not re.search(r"redacted_fields\s*\{\s*query_string\s*\{", keycloak_main):
        fail("issuer-waf-redaction", "issuer WAF logging does not redact query strings (OAuth code/session_code)")
    ok("issuer WAF has no body inspection and redacts OAuth flow query parameters")

    # 17. N-07 (rev2 Security SEC-I6): durable tables carry deletion protection.
    durable = {"journal", "events", "issuer_truth", "registry"}
    disposable = {"connections", "assertion_replay"}
    seen_tables = set()
    for path, parsed in files.items():
        for local_block in parsed.get("locals", []) or []:
            tables = local_block.get("tables")
            if not isinstance(tables, dict):
                continue
            for table_name, spec in tables.items():
                seen_tables.add(table_name)
                if not isinstance(spec, dict):
                    continue
                if table_name in durable and spec.get("protect") is not True:
                    fail("table-deletion-protection", f"{table_name}: durable table lacks deletion protection")
                if table_name in disposable and spec.get("protect") is not False:
                    fail("table-disposable", f"{table_name}: disposable table must not be protected")
    missing = durable - seen_tables
    if missing:
        fail("table-missing", f"durable tables not authored: {sorted(missing)}")
    ok("journal/events/issuer_truth/registry are deletion-protected; connections/assertion_replay stay disposable")

    # 18. N-02 closure (owner Option B, 2026-11-23): CloudFront must add exactly
    # one Terraform-generated origin-verification header to /api/* origin
    # requests, the name must avoid the CloudFront denylist and stay fixed, and
    # the value must not exist as a literal, a tfvars/CI value, an output or a
    # log field.
    console_path = ROOT / "modules" / "console" / "main.tf"
    console_parsed = files.get(console_path)
    header_name = None
    header_value_expr = None
    api_origins = 0
    if console_parsed is None:
        fail("origin-verify-source", "modules/console/main.tf is missing")
    else:
        for local_block in console_parsed.get("locals", []) or []:
            if "origin_verify_header" in local_block:
                header_name = local_block["origin_verify_header"]
        for type_name, name, body in resources(console_parsed):
            if type_name != "aws_cloudfront_distribution":
                continue
            for origin in as_list(body.get("origin")):
                if not isinstance(origin, dict):
                    continue
                if "custom_origin_config" not in origin:
                    continue
                api_origins += 1
                headers = as_list(origin.get("custom_header"))
                if len(headers) != 1:
                    fail(
                        "origin-verify-header",
                        f"{name}: the /api/* origin must add exactly one custom header, found {len(headers)}",
                    )
                    continue
                header = headers[0] if isinstance(headers[0], dict) else {}
                header_value_expr = header.get("value")
                if header.get("name") != "${local.origin_verify_header}":
                    fail(
                        "origin-verify-header-name",
                        f"{name}: custom header name must be the fixed local.origin_verify_header",
                    )
    if api_origins == 0:
        fail("origin-verify-header", "no CloudFront custom origin with a custom header found")

    if not header_name:
        fail("origin-verify-name", "modules/console: local.origin_verify_header is not authored")
    else:
        lowered = str(header_name).strip().lower()
        if lowered != ORIGIN_VERIFY_HEADER.lower():
            fail(
                "origin-verify-name",
                f"header name is '{header_name}'; the documented interface name is '{ORIGIN_VERIFY_HEADER}'",
            )
        if lowered in CF_DENIED_HEADERS:
            fail("origin-verify-denied-name", f"'{header_name}' is a header CloudFront cannot add to an origin")
        if lowered.startswith(("x-amz-", "x-edge-")) or lowered == "x-real-ip":
            fail("origin-verify-denied-name", f"'{header_name}' matches a CloudFront-reserved prefix")

    if header_value_expr != "${var.origin_verify_secret}":
        fail(
            "origin-verify-value-source",
            f"origin custom header value is '{header_value_expr}'; it must be var.origin_verify_secret",
        )

    stack_main_parsed = files.get(ROOT / "envs" / "stack" / "main.tf")
    stack_main_text = raw_text.get(ROOT / "envs" / "stack" / "main.tf", "")
    generated = bool(
        re.search(r"origin_verify_secret\s*=\s*random_password\.origin_verify\.result", stack_main_text)
    )
    random_ok = False
    if stack_main_parsed is None:
        fail("origin-verify-source", "envs/stack/main.tf is missing")
    for type_name, name, body in resources(stack_main_parsed or {}):
        if type_name != "random_password":
            continue
        length = body.get("length")
        if not isinstance(length, (int, float)) or int(length) < 32:
            fail("origin-verify-entropy", f"{name}: length must be at least 32 (found {length!r})")
        elif body.get("special") is not False:
            fail("origin-verify-entropy", f"{name}: special characters must stay disabled (token-safe header value)")
        else:
            random_ok = True
    if not generated:
        fail(
            "origin-verify-not-generated",
            "envs/stack: module.console must receive random_password.origin_verify.result",
        )
    if not random_ok:
        fail("origin-verify-generator", "envs/stack: no adequate random_password origin_verify resource")

    # A caller-supplied or authored literal is the failure mode the owner
    # condition forbids: no default, no assignment of a literal string, no
    # output, no CI variable, no log field. Both custody variables are covered
    # (console module and api module).
    for path, parsed in files.items():
        for variable in parsed.get("variable", []) or []:
            for name, body in variable.items():
                if name not in CUSTODY_VARS:
                    continue
                if "default" in body:
                    fail("origin-verify-default", f"{path.relative_to(ROOT)}: {name} must not have a default")
                if body.get("sensitive") is not True:
                    fail("origin-verify-sensitive", f"{path.relative_to(ROOT)}: {name} must be sensitive")
        for output in parsed.get("output", []) or []:
            for name, body in output.items():
                text = json.dumps(body).lower()
                if "origin_verify" in text or "random_password" in text:
                    fail("origin-verify-output", f"{path.relative_to(ROOT)}: output {name} exposes the custody value")
    for path, text in raw_text.items():
        for custody in CUSTODY_VARS:
            if re.search(rf'{custody}\s*=\s*"', text):
                fail("origin-verify-literal", f"{path.relative_to(ROOT)}: {custody} assigned a literal")
            for match in re.finditer(rf"{custody}\s*=\s*([^\n#]+)", text):
                assigned = match.group(1).strip()
                if assigned not in ALLOWED_CUSTODY_SOURCES:
                    fail(
                        "origin-verify-source",
                        f"{path.relative_to(ROOT)}: unexpected {custody} value source '{assigned}'",
                    )

    workflows = sorted((ROOT.parents[1] / ".github" / "workflows").glob("*.yml"))
    for workflow in workflows:
        text = workflow.read_text()
        if re.search(r"origin[_-]?verify|x-origin-verify", text, re.I):
            fail("origin-verify-ci", f".github/workflows/{workflow.name}: the custody value must not be a CI variable")

    # The WAF log destination must redact the header as defence in depth.
    redactions = set()
    if console_parsed is not None:
        for type_name, name, body in resources(console_parsed):
            if type_name != "aws_wafv2_web_acl_logging_configuration":
                continue
            for field in as_list(body.get("redacted_fields")):
                if not isinstance(field, dict):
                    continue
                single = field.get("single_header")
                single = single[0] if isinstance(single, list) and single else single
                if isinstance(single, dict) and single.get("name"):
                    redactions.add(str(single["name"]).strip().strip('"').lower())
    if ORIGIN_VERIFY_HEADER.lower() not in redactions:
        fail(
            "origin-verify-waf-redaction",
            f"WAF logging must redact '{ORIGIN_VERIFY_HEADER.lower()}' (found {sorted(redactions)})",
        )

    # 18f. Contract delta §9.1 additions.
    # origin-verify-env: modules/api declares the expected-value variable as
    # sensitive, the console Lambda maps EDGE_ORIGIN_VERIFY from it, and the
    # stack root feeds that variable from the single root generator.
    api_parsed = files.get(ROOT / "modules" / "api" / "main.tf") or {}
    api_vars_parsed = files.get(ROOT / "modules" / "api" / "variables.tf") or {}
    api_var_ok = False
    for variable in api_vars_parsed.get("variable", []) or []:
        for name, body in variable.items():
            if name != "edge_origin_verify":
                continue
            if body.get("sensitive") is not True:
                fail("origin-verify-env", "modules/api: edge_origin_verify must be sensitive")
            elif "default" in body:
                fail("origin-verify-env", "modules/api: edge_origin_verify must not have a default")
            else:
                api_var_ok = True
    env_mapped = False
    for type_name, name, body in resources(api_parsed):
        if type_name != "aws_lambda_function":
            continue
        for environment in as_list(body.get("environment")):
            if not isinstance(environment, dict):
                continue
            variables = environment.get("variables")
            variables = variables if isinstance(variables, dict) else {}
            if variables.get("EDGE_ORIGIN_VERIFY") == "${var.edge_origin_verify}":
                env_mapped = True
    if not api_var_ok:
        fail("origin-verify-env", "modules/api: no sensitive, default-free edge_origin_verify variable")
    if not env_mapped:
        fail("origin-verify-env", "modules/api: console Lambda must map EDGE_ORIGIN_VERIFY from var.edge_origin_verify")
    if not re.search(r"edge_origin_verify\s*=\s*random_password\.origin_verify\.result", stack_main_text):
        fail("origin-verify-env", "envs/stack: module.api must receive the same random_password result")

    # origin-verify-cache-disabled: the /api/* behaviour keeps a zero-TTL cache
    # policy, so a boundary refusal is never cached for another viewer.
    api_cache_ok = False
    for type_name, name, body in resources(console_parsed or {}):
        if type_name != "aws_cloudfront_distribution":
            continue
        for behavior in as_list(body.get("ordered_cache_behavior")):
            if not isinstance(behavior, dict) or behavior.get("path_pattern") != "/api/*":
                continue
            policy_id = str(behavior.get("cache_policy_id", ""))
            cached_ref = re.search(r"aws_cloudfront_cache_policy\.([A-Za-z0-9_-]+)\.id", policy_id)
            if not cached_ref:
                fail("origin-verify-cache-disabled", f"{name}: /api/* cache policy is not an authored cache policy")
                continue
            target = cached_ref.group(1)
            for ptype, pname, pbody in resources(console_parsed or {}):
                if ptype != "aws_cloudfront_cache_policy" or pname != target:
                    continue
                if pbody.get("default_ttl") == 0 and pbody.get("max_ttl") == 0:
                    api_cache_ok = True
                else:
                    fail(
                        "origin-verify-cache-disabled",
                        f"{pname}: default_ttl/max_ttl must both be 0 for /api/* (found {pbody.get('default_ttl')}/{pbody.get('max_ttl')})",
                    )
            # A viewer-request function on /api/* could observe the header.
            if behavior.get("function_association"):
                fail(
                    "origin-verify-not-in-function",
                    f"{name}: /api/* behavior must not run a CloudFront Function",
                )
    if not api_cache_ok:
        fail("origin-verify-cache-disabled", "no zero-TTL cache policy resolved for the /api/* behavior")

    # origin-verify-single-origin: exactly one origin resolves api_fqdn, and the
    # stack root wires it once.
    origin_matches = 0
    for type_name, name, body in resources(console_parsed or {}):
        if type_name != "aws_cloudfront_distribution":
            continue
        for origin in as_list(body.get("origin")):
            if isinstance(origin, dict) and origin.get("domain_name") == "${var.http_api_domain_name}":
                origin_matches += 1
    if origin_matches != 1:
        fail(
            "origin-verify-single-origin",
            f"exactly one CloudFront origin must resolve api_fqdn (found {origin_matches})",
        )
    if len(re.findall(r"module\.api\.origin_domain_name", stack_main_text)) != 1:
        fail("origin-verify-single-origin", "envs/stack: module.api.origin_domain_name must be wired exactly once")

    # origin-verify-not-in-function: the name may never be added at a viewer-side
    # evaluation point, where the value would be observable in function logs.
    for type_name, name, body in resources(console_parsed or {}):
        if type_name != "aws_cloudfront_function":
            continue
        code = str(body.get("code") or "")
        if ORIGIN_VERIFY_HEADER.lower() in code.lower():
            fail(
                "origin-verify-not-in-function",
                f"{name}: CloudFront Function source references '{ORIGIN_VERIFY_HEADER}'",
            )

    ok(
        f"N-02 origin verification: '{ORIGIN_VERIFY_HEADER}' added once by CloudFront on /api/* from the single "
        "random_password, no cache, no function, state/env only, sensitive, no default, not in "
        "tfvars/CI/logs/outputs, WAF-redacted"
    )

    print("POLICY CHECKS")
    for check in CHECKS:
        print(f"  PASS  {check}")
    if FAILURES:
        print("\nVIOLATIONS")
        for item in FAILURES:
            print(f"  FAIL  {item}")
        print(f"\n{len(FAILURES)} violation(s)")
        return 1
    print("\nAll policy checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
