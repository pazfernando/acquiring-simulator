# Run record — what produced these bytes

## Observed runtime

- Agent: `ai-sdlc-requirements` (Requirements Analyst), subagent of the AI-SDLC coordinator, depth 1.
- Session: `agent:ai-sdlc-requirements:subagent:a841b8a9-c000-4c25-b657-9ec00e0aa59b`.
- **Observed runtime model: `nan/deepseek-v4-flash`** (provider `nan`, model `deepseek-v4-flash`). The session's configured default is `openai/gpt-6.1-sol`; this run executed on the fallback identity reported by the runtime. No model override was applied by this agent, and no other model contributed to these bytes.
- Reasoning mode: off (runtime default). No hidden reasoning was relied on for a factual claim that the evidence below does not support.
- Host: local Gateway host `fernando-OC`; checkout `/home/pazfernando/projects/acquiring-simulator`; branch/HEAD **not attested** by this run (no Git command was run, by boundary).
- An earlier attempt at this delta is not evidenced in this session. No partial artifact from another model existed to salvage, and nothing here was reconstructed from a different baseline. No claim is made about why the primary model was or was not used.

## Commands actually run (all read-only, local)

| # | Command (abridged) | Result |
| --- | --- | --- |
| 1 | `sha256sum …/reviews/continuation-rev3-security-20261123/security-review.md` | `f5696ed917f009c7569cccbba5f42b431170d15a53ef06bdd38c10e61d53ba50` — matches the commission and `owner-decisions.md` |
| 2 | `find`/`ls` over the work item and `design/` | packet, review and handoff layout confirmed |
| 3 | reads of `PROJECT.md`, `WORK_ITEM.md`, `owner-decisions.md` (N-02 section), `commission.md`, `security-review.md` (full), `contracts.md`, `README.md`, `acceptance-scenarios.md` | authority and constraints established from primary text |
| 4 | `sha256sum` over the 19 input files pinned in `INPUTS.SHA256SUMS` | all pins recomputed in this run |
| 5 | `sha256sum -c continuation-rev3-20261123/OUTPUTS.SHA256SUMS` and `sha256sum -c continuation-rev3-dr301-20261123/OUTPUTS.SHA256SUMS` | rev3 **21/21 OK**; DR3-01 packet 4/4 OK (its manifest excludes itself) |
| 6 | reads of `ConsoleAuthorizationRoute`, `ConsoleHttpRequest`, `ConsoleAuthorizer`, `ConsoleRateLimitHook`, `HttpApiV2Wire` | existing route order, duplicate-header discipline, identity-assertion refusal list |
| 7 | reads/greps over `envs/stack/main.tf`, `modules/console/{main,variables}.tf`, `modules/api/main.tf`, `tools/policy_checks.py` | authored Option B state: header local, custom header, `random_password`, sensitive validation, WAF redaction, check 18 and its gaps |
| 8 | `grep -rin "origin-verify\|origin_verify\|origin verify"` (excluding `target/` and this directory) | name-inventory claim in §4.2 of the delta |
| 9 | `python3 tools/policy_checks.py` | **failed**: `ModuleNotFoundError: No module named 'hcl2'`. Not installed; no dependency change made. Check 18 was therefore **read**, not re-executed, by this run. |
| 10 | `web_fetch` of the AWS CloudFront developer guide, "Add custom headers to origin requests" (2026-11-24T03:06Z) | denylist list and the overwrite sentence quoted in the delta §4.1 re-verified from the vendor page |
| 11 | targeted reads of `aws_cloudfront_function.spa_fallback` source and `aws_cloudfront_cache_policy.api_disabled` | the function returns `/api` and `/api/*` untouched; the API cache policy sets `default_ttl = max_ttl = min_ttl = 0` and is attached to the `/api/*` behaviour. Both claims in the delta §6.3 are byte-verified. |

## Concurrent authored state — read this before trusting a pin

This delta was authored while DevOps was editing the same Terraform tree. Observed mtimes during the run (2026-11-23, America/Bogota): `envs/stack/main.tf` 22:07:38, `modules/console/main.tf` 22:07:30, `tools/policy_checks.py` 22:08:26. Comparing digests with this run's first read: `envs/stack/main.tf` and `modules/console/main.tf` were **content-identical** (same digest both times); `tools/policy_checks.py` **did** change after that read (`313c327d…d2a5` → `bb45e056…7b12`). The pins in `INPUTS.SHA256SUMS` are the post-change bytes, verified immediately before finalization.

What was re-checked after the drift and is unchanged in substance: the fixed local `origin_verify_header = "X-Origin-Verify"`, the single `custom_header` on the custom origin with value `var.origin_verify_secret`, the WAF redaction of `x-origin-verify`, the `random_password` shape (length 48, `special = false`), and check 18's sub-assertions including its PASS line being gated on the global `not FAILURES`. `modules/api` still has **no** expected-value variable and the console Lambda still has **no** `EDGE_ORIGIN_VERIFY` entry, so the four required additions in §9.1 of the delta remain required.

Consequence for review: pins are only valid for the bytes they name. If DevOps edits the tree again before review, the reviewer must re-pin and confirm the delta's "authored" claims still hold; a moved pin is not, by itself, a delta defect, but a changed semantic under a claimed-authored assertion is.

## Limits

- Documentary work only. No application code, Terraform, workflow, test or fixture was written or executed in this run beyond the read-only checks above. No `terraform plan/apply`, no cloud call, no credential flow, no browser, no Git/provider command, no onward delegation.
- The CloudFront behaviour this control depends on (custom header added on origin requests; a viewer-supplied same-name header overwritten) is **vendor-documented, not observed**. The delta states this in §12 rather than treating it as proven.
- API Gateway HTTP API payload-v2 duplicate-header coalescing is assumed from the platform's documented behaviour; the delta therefore requires comma detection in addition to occurrence counting, and lists runtime confirmation as unproven.
- The repository's secret-scanning, CI and policy-check executions are configured-not-demonstrated from this run's perspective; nothing here should be read as a green pipeline.
- This packet does not close N-02. Security's FAIL verdict stands until independent re-review of this delta and the changed revisions.

## Outputs produced by this run

`PROGRESS.md`, `contract-delta.md`, `review-brief.md`, `RUN.md`, `INPUTS.SHA256SUMS`, `OUTPUTS.SHA256SUMS` — all in this directory, nothing else touched.
