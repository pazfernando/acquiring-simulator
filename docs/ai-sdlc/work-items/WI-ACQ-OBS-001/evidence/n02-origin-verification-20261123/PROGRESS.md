# PROGRESS — N-02 closure (Option B: CloudFront origin-verification header)

Work item: WI-ACQ-OBS-001. Owner: Fernando Paz. Started 2026-11-23 (America/Bogota).

Runtime model for this run: `nan/deepseek-v4-flash`, the configured default
(primary). No model override was requested and no fallback was needed.

## Inputs read before any write

- `owner-decisions.md` §"N-02 closure (Option B) and normative adoption — 2026-11-23" (binding conditions).
- `reviews/continuation-rev3-security-20261123/security-review.md`, pin recomputed first:
  `f5696ed917f009c7569cccbba5f42b431170d15a53ef06bdd38c10e61d53ba50` — **matches** the pin in the assignment.
- Current authored tree: `infra/terraform/{modules/console,modules/api,envs/stack,tools/policy_checks.py}`,
  `infra/terraform/README.md`, `.github/workflows/{ci,terraform}.yml`.

## Plan of record

1. Write evidence directory scaffolding and this PROGRESS.md (this file).
2. Author Terraform value generation + CloudFront custom origin header with a fixed,
   denylist-safe name; document the name and the value custody in `infra/terraform/README.md`.
3. Record vendor evidence that CloudFront overwrites a viewer-supplied header of the same name.
4. Add leak exclusions (CloudFront/API Gateway/WAF/log formats) and policy assertions that fail
   on a literal, a tfvars value, a CI variable, a log format, or an output.
5. Run the local suite: fmt (both roots), init -backend=false + validate (both roots), tflint,
   actionlint, full policy suite, and a tree scan for state/plan/tfvars/secret literals.
6. Write `devops-report.md`, `CHANGED-FILES.sha256`, `SHA256SUMS`.

## Status log

- [x] Inputs verified (Security review pin recomputed: match).
- [x] Authoring: generator, custom header, WAF redaction, `EDGE_ORIGIN_VERIFY` delivery, check 18 (+4 contract-delta assertions), extended check 12, README, `.gitignore`.
- [x] Local validation: fmt (both roots), init -backend=false + validate (both roots), tflint, actionlint, 18/18 policy PASS, 15/15 negative mutations, leak scan, provider schema, vendor doc.
- [x] Report (`devops-report.md`), `CHANGED-FILES.sha256`, `SHA256SUMS`.

## Deviations recorded

- One read-only `git rev-parse HEAD` / `git status --porcelain` was executed before
  re-reading the constraint that Git operations go through Coordinator. No Git
  mutation, commit or push; disclosed in the report.
- The Requirements delta (draft, discovered mid-run) fixes `EDGE_ORIGIN_VERIFY` as
  the delivery mechanism, which adds two provider-owned runtime copies to the
  owner's "state-only" wording. Implemented for coherence, disclosed as a
  reviewer/owner acknowledgment item.
