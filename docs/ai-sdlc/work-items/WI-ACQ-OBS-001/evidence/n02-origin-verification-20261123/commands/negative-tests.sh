#!/usr/bin/env bash
# Negative tests for the N-02 policy assertions (check 18 and the extended
# check 12). Each case mutates a disposable copy of the authored tree and must
# make tools/policy_checks.py fail with the expected check id. The authored tree
# is never modified. No cloud, backend or credential access.
set -u

REPO=/home/pazfernando/projects/acquiring-simulator
PY=/home/pazfernando/.local/venvs/tfchecks/bin/python
BASE=$(mktemp -d /tmp/n02-negtest.XXXXXX)
STATUS=0

prepare() {
  rm -rf "$BASE/tree"
  mkdir -p "$BASE/tree"
  cp -a "$REPO/infra" "$BASE/tree/infra"
  cp -a "$REPO/.github" "$BASE/tree/.github"
  rm -rf "$BASE/tree/infra/terraform/.terraform" \
         "$BASE/tree/infra/terraform/envs/stack/.terraform" \
         "$BASE/tree/infra/terraform/bootstrap/.terraform"
}

run_case() {
  local name="$1" expected="$2"
  local out
  out=$("$PY" "$BASE/tree/infra/terraform/tools/policy_checks.py" 2>&1)
  local rc=$?
  echo "### CASE: $name"
  echo "    expected violation: $expected"
  echo "    exit: $rc"
  if [ "$rc" -eq 0 ]; then
    echo "    RESULT: NEGATIVE TEST FAILED (suite passed)"
    STATUS=1
  elif grep -q "FAIL  $expected" <<<"$out"; then
    grep "FAIL  $expected" <<<"$out" | sed 's/^/    /'
    echo "    RESULT: NEGATIVE TEST PASSED (assertion fired)"
  else
    echo "    RESULT: NEGATIVE TEST FAILED (wrong or missing violation)"
    grep "FAIL" <<<"$out" | sed 's/^/    /'
    STATUS=1
  fi
  echo
}

mutate() { # file, python-expression on text variable `t`
  local file="$1" expr="$2"
  "$PY" - "$BASE/tree/$file" "$expr" <<'PY'
import sys, pathlib
path = pathlib.Path(sys.argv[1])
t = path.read_text()
exec(sys.argv[2])
path.write_text(t)
PY
}

# 0. Unmutated copy must pass (control).
prepare
out=$("$PY" "$BASE/tree/infra/terraform/tools/policy_checks.py" 2>&1)
echo "### CONTROL: unmutated copy"
echo "    exit: $?"
tail -1 <<<"$out" | sed 's/^/    /'
grep -q "All policy checks passed." <<<"$out" && echo "    RESULT: CONTROL PASSED" || { echo "    RESULT: CONTROL FAILED"; STATUS=1; }
echo

# 1. Literal value instead of the generated result.
prepare
mutate infra/terraform/envs/stack/main.tf \
  't = t.replace("origin_verify_secret = random_password.origin_verify.result", "origin_verify_secret = \"aB3dE5fG7hI9jK1lM3nO5pQ7rS9tU1vW3xY5zA7bC9dE1fG3\"")'
run_case "literal value in the module call" "origin-verify-literal"

# 2. Reserved header name (X-Amz- prefix, denylisted for custom origin headers).
prepare
mutate infra/terraform/modules/console/main.tf \
  't = t.replace("origin_verify_header = \"X-Origin-Verify\"", "origin_verify_header = \"X-Amz-Origin-Verify\"")'
run_case "reserved X-Amz- header name" "origin-verify-denied-name"

# 3. Output exposing the custody value.
prepare
cat >> "$BASE/tree/infra/terraform/envs/stack/outputs.tf" <<'EOF'

output "leaked" {
  value     = random_password.origin_verify.result
  sensitive = true
}
EOF
run_case "output exposes the generated value" "origin-verify-output"

# 4. Header added to the API Gateway access-log format.
prepare
mutate infra/terraform/modules/api/main.tf \
  't = t.replace("      integrationErr = \"$context.integrationErrorMessage\"", "      integrationErr = \"$context.integrationErrorMessage\"\n      verifyHeader   = \"$context.request.header.x-origin-verify\"")'
run_case "header in an authored log format" "log-token-leak"

# 5. WAF logging redaction removed.
prepare
mutate infra/terraform/modules/console/main.tf \
  't = t.replace("""  redacted_fields {
    single_header {
      name = "x-origin-verify"
    }
  }
""", "")'
run_case "WAF redaction of the header removed" "origin-verify-waf-redaction"

# 6. CI variable carrying the value.
prepare
"$PY" - "$BASE/tree/.github/workflows/terraform.yml" <<'PY'
import sys, pathlib
p = pathlib.Path(sys.argv[1]); t = p.read_text()
t = t.replace("  TF_VAR_api_fqdn: ${{ vars.API_FQDN }}",
              "  TF_VAR_api_fqdn: ${{ vars.API_FQDN }}\n  TF_VAR_origin_verify_secret: ${{ vars.ORIGIN_VERIFY }}")
p.write_text(t)
PY
run_case "value carried as a CI variable" "origin-verify-ci"

# 7. Custom origin header removed entirely.
prepare
mutate infra/terraform/modules/console/main.tf \
  't = t.replace("""    custom_header {
      name  = local.origin_verify_header
      value = var.origin_verify_secret
    }
""", "")'
run_case "origin verification header removed" "origin-verify-header"

# 8. Module variable gains a default.
prepare
mutate infra/terraform/modules/console/variables.tf \
  't = t.replace("  sensitive   = true\n", "  sensitive   = true\n  default     = \"aB3dE5fG7hI9jK1lM3nO5pQ7rS9tU1vW3xY5zA7bC9dE1fG3\"\n")'
run_case "module variable defaulted" "origin-verify-default"

# 9. Entropy below the bound.
prepare
mutate infra/terraform/envs/stack/main.tf 't = t.replace("  length      = 48", "  length      = 16")'
run_case "generated length under 32" "origin-verify-entropy"

# 10. Special characters enabled (not a token-safe header value).
prepare
mutate infra/terraform/envs/stack/main.tf 't = t.replace("  special     = false", "  special     = true")'
run_case "special characters enabled" "origin-verify-entropy"

# 11. The console Lambda loses the EDGE_ORIGIN_VERIFY mapping (contract §9.1
#     origin-verify-env).
prepare
mutate infra/terraform/modules/api/main.tf \
  't = t.replace("      EDGE_ORIGIN_VERIFY = var.edge_origin_verify\n", "")'
run_case "EDGE_ORIGIN_VERIFY mapping removed" "origin-verify-env"

# 12. The expected-value variable stops being sensitive.
prepare
mutate infra/terraform/modules/api/variables.tf 't = t.replace("  sensitive   = true", "  sensitive   = false")'
run_case "expected-value variable not sensitive" "origin-verify-env"

# 13. Caching re-enabled on /api/*.
prepare
mutate infra/terraform/modules/console/main.tf \
  't = t.replace("""  name        = "${var.name_prefix}-console-api-disabled"
  comment     = "No caching for console API responses"
  default_ttl = 0
  max_ttl     = 0""", """  name        = "${var.name_prefix}-console-api-disabled"
  comment     = "No caching for console API responses"
  default_ttl = 0
  max_ttl     = 86400""")'
run_case "caching enabled on /api/*" "origin-verify-cache-disabled"

# 14. A second CloudFront origin resolves api_fqdn.
prepare
mutate infra/terraform/modules/console/main.tf \
  't = t.replace("""  origin {
    origin_id   = local.api_origin_id""", """  origin {
    origin_id   = "api-duplicate"
    domain_name = var.http_api_domain_name
  }

  origin {
    origin_id   = local.api_origin_id""")'
run_case "second origin resolves api_fqdn" "origin-verify-single-origin"

# 15. The header name reaches a viewer-side CloudFront Function.
prepare
mutate infra/terraform/modules/console/main.tf \
  't = t.replace("var uri = request.uri;", "var uri = request.uri; // x-origin-verify")'
run_case "header name in CloudFront Function source" "origin-verify-not-in-function"

rm -rf "$BASE"
echo "AGGREGATE: $([ "$STATUS" -eq 0 ] && echo ALL-NEGATIVE-TESTS-PASSED || echo FAILURES-PRESENT)"
exit "$STATUS"
