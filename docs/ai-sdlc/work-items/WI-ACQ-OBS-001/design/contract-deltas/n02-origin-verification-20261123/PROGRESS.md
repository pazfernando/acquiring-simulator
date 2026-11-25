# N-02 origin-verification contract delta — progress

Status: COMPLETE (documentary delta authored and locally verified). Independent re-review pending; N-02 is NOT closed by this packet and no approval is self-granted.

## Authority and scope

- Owner instruction: "adelante con lo recomendado" (Fernando Paz, 2026-11-23), recorded in `owner-decisions.md` section "N-02 closure (Option B) and normative adoption — 2026-11-23". That record selects the CloudFront origin-verification header (Option B) and carries four binding conditions; it states N-02 stays OPEN until this bounded contract delta is authored and independently re-reviewed.
- Independently reviewed basis: `reviews/continuation-rev3-security-20261123/security-review.md`, SHA-256 `f5696ed917f009c7569cccbba5f42b431170d15a53ef06bdd38c10e61d53ba50` (recomputed before use; matches the commission and `owner-decisions.md`). Its N-02 section names the four conditions and the retest criteria this delta must specify.
- Normative contract revision this delta extends: `design/contract-deltas/continuation-rev3-20261123/` (manifest `2000bb004e68e6d2974bef3067baeae6774f1922a21dca4ea18fecf6944ab467`) as amended by `continuation-rev3-dr301-20261123/` (supersession note `72ba032f5e1264abec1077d238e6c5c943f489a65bc7cd437519052769fd8b17`).
- Boundary honored: writes only in this new directory. `owner-decisions.md`, `WORK_ITEM.md`, other packets, historical C3 golden bytes and all application/IaC files were read, not modified. No Git or provider command, no cloud or credential effect, no onward delegation.

## Deliverables

| File | Purpose |
| --- | --- |
| `contract-delta.md` | The delta itself: fixed header name, value form, fail-closed decision rule, status/error body, pipeline placement, N-03 interaction, custody rules and policy assertions, denial observability, Given/When/Then cases, unproven list. |
| `review-brief.md` | Short bounded re-review request with pinned inputs and explicit non-scope. |
| `RUN.md` | Observed runtime/model that produced these bytes, commands, results and limits. |
| `INPUTS.SHA256SUMS` | Repository-relative input pins, recomputed in this run. |
| `OUTPUTS.SHA256SUMS` | Packet-relative output pins (excludes itself). |

## What was verified while authoring

- Security review pin recomputed: `f5696ed9…3ba50` — match.
- Normative packet manifest recomputed: 21/21 OK; DR3-01 supersession note pin — match.
- Read the exact bytes that constrain this delta: `owner-decisions.md` (N-02 section), `WORK_ITEM.md` (adoption status), `contracts.md` (D-6/R-2 error-body shape, payment/retention non-regression), `acceptance-scenarios.md` (case style and counters), the console boundary classes (`ConsoleAuthorizationRoute`, `ConsoleHttpRequest`, `ConsoleAuthorizer`, `ConsoleRateLimitHook`), and the authored IaC (`envs/stack/main.tf`, `modules/console/{main,variables}.tf`, `modules/api/main.tf`, `tools/policy_checks.py`).
- CloudFront denylist and the origin-header overwrite rule re-verified against the AWS developer guide on 2026-11-23 22:06 America/Bogota (fetch recorded in `RUN.md`); quote used in `contract-delta.md` §4.

## Files not authored (reported as gaps, not hidden)

- No application code, no Terraform, no CI change and no test was written. This packet is contract text only.
- `tools/policy_checks.py` already carries check 18, authored by DevOps ahead of this delta. It asserts the fixed header name, the denylist classes, the single custom header on the custom origin, the value source `var.origin_verify_secret` fed by `random_password.origin_verify.result`, entropy (`length >= 32`, `special = false`), `sensitive = true`, no default, no output, no literal assignment, no CI reference and WAF redaction. One reporting defect in that authored check is reported, not fixed here: its PASS line is gated on the global `not FAILURES`, so an unrelated failure hides the N-02 pass line. Four assertions are still missing and are named as required additions in the delta: the `EDGE_ORIGIN_VERIFY` wiring, the caching-disabled cache policy, single-origin mapping, and the absence of the name in CloudFront Function source.
- `modules/api` has no variable carrying the expected value and the console Lambda has no `EDGE_ORIGIN_VERIFY` environment entry. That wiring is construction, still gated on this delta's review, and is the reason check 18 cannot yet assert it.

## Concurrent authored state

The Terraform tree changed while this delta was authored (DevOps activity at 22:07–22:08 local). Pins were recomputed after that activity; `RUN.md` records the observed drift and re-confirm of the claims this packet depends on. If the tree moves again before review, the reviewer must re-pin.

## Next handoff

Independent re-review of exactly the pinned bytes in `review-brief.md` (domain or Security reviewer, not the author), then DevOps completing check 18 and the runtime wiring, then QA execution of the OV cases on the deployed stack. Security's FAIL verdict stands until that re-review closes N-02.
