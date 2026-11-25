# Review brief — N-02 origin-verification contract delta (bounded)

Status: DRAFT / NOT NORMATIVE. Request for an independent documentary review of exactly these bytes. The requester assumes no approval and does not self-close N-02.

## Why this exists

Security's N-02 finding (`reviews/continuation-rev3-security-20261123/security-review.md`, SHA-256 `f5696ed917f009c7569cccbba5f42b431170d15a53ef06bdd38c10e61d53ba50`) is a confirmed IMPORTANT residual: the dedicated `api_fqdn` alias is publicly resolvable, so a caller can reach the console HTTP API without the CloudFront edge and evade the WAF rate bound. The owner selected Option B (origin-verification header) with four binding conditions, whose **condition (1) is that the check be specified in the contracts before construction, as a bounded contract delta**. This directory is that delta.

## Pinned inputs (SHA-256, repository-relative)

| Input | SHA-256 |
| --- | --- |
| `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/owner-decisions.md` | `d5208f2b6bd28047ce7394656b0d5de2fff717d68b004a6d204feeaa2443338e` |
| `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/WORK_ITEM.md` | `4134924093c077d206d0cdd755018cfd0add8767bc0ce84e440dc8ec713d0aff` |
| `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/handoffs/continuation-20261123/commission.md` | `f8936ea5b7c0727ee0f4aca9fcd3eeb9cc7df515a239a7168a7336ce4beee419` |
| `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/reviews/continuation-rev3-security-20261123/security-review.md` | `f5696ed917f009c7569cccbba5f42b431170d15a53ef06bdd38c10e61d53ba50` |
| `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/contract-deltas/continuation-rev3-20261123/contracts.md` | `33d5fb3c8ef6b4aa6baae3389c74032efb1faa6d809a3f09268910edf9ca1f10` |
| `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/contract-deltas/continuation-rev3-20261123/OUTPUTS.SHA256SUMS` (normative packet, 21 entries) | `2000bb004e68e6d2974bef3067baeae6774f1922a21dca4ea18fecf6944ab467` |
| `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/contract-deltas/continuation-rev3-20261123/acceptance-scenarios.md` | `1cc09f281f1a595b5f72c7b48b3e6364105fb783fd4f71edc1538a37d622076a` |
| `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/contract-deltas/continuation-rev3-dr301-20261123/supersession.md` | `72ba032f5e1264abec1077d238e6c5c943f489a65bc7cd437519052769fd8b17` |
| `infra/terraform/envs/stack/main.tf` | `35c1290139bfd052d880e7f50464470ada74b426b19b7aa3aa031265b03d51c4` |
| `infra/terraform/modules/console/main.tf` | `3875c53ad1d3176d846e877d406cc309b454ca7c13e25a36a73121776ff58cc7` |
| `infra/terraform/modules/console/variables.tf` | `104dee2e4b85c1b23e3e6bf950c22b38120b032d1cf502c2c1428df817f88599` |
| `infra/terraform/modules/api/main.tf` | `7ec90b86c5a327a8565d4b951d0f7c59b21cd210c4e727f3fc6c5ae8143356f9` |
| `infra/terraform/tools/policy_checks.py` | `bb45e05611b592ae61e08fb5ee9ffc87fc69effe9d8442db68ba8de2076e7b12` |
| `src/main/java/com/acquiring/simulator/console/ConsoleAuthorizationRoute.java` | `7c222e5be01ee03e1e29755e9b55aa02613dc8cd3dd86c8a4e3ff5b93d4d598d` |
| `src/main/java/com/acquiring/simulator/console/ConsoleHttpRequest.java` | `bf911d0c12fe49efcd724a09c07bb311d24b69875111737f3bcc1a5e99e81a6b` |
| `src/main/java/com/acquiring/simulator/console/ConsoleAuthorizer.java` | `969f4076c69b1818652a9c0734e0db8b1f6b49a3bc2841c22ccdd4861e848a02` |

Artifact under review: `contract-delta.md` in this directory, pinned by `OUTPUTS.SHA256SUMS`.

## What to check

1. **Condition (1) — specification completeness.** Does the delta fix, unambiguously, the fail-closed behaviour for absent, empty, duplicated and mismatched values, including the duplicate forms a payload-v2 integration can produce, and the exact status and body bytes? Is any branch left to implementation preference?
2. **Placement and independence.** Is the gate's position defensible as "before any business action, independent of the JWT authorizer" given that the platform authorizer necessarily runs first (§5.4)? Does the delta avoid claiming the gate is an authentication control, and does it correctly bound what it protects against an authenticated direct caller?
3. **Error-contract correctness.** Is reusing the D-6 `{version,error,action}` shape while refusing the C3 envelope correct against the pinned C3 bytes and the closed error enumeration? Does the 403-with-a-new-code choice withhold any information an attacker should not have, and is the "one code for four reasons" rule consistent with observable-denial retest criteria?
4. **Name choice.** Is `X-Origin-Verify` genuinely outside the CloudFront custom-header denylist and genuinely free of application semantics, against the header inventory in the repository? Is binding the authored local the right move versus introducing a different name?
5. **Custody.** Are the generation, entropy, alphabet, storage and exclusion rules complete? Is the delta honest that two provider-owned runtime copies are unavoidable, and is bounding them by IAM sufficient, or does this fail Security condition (2)?
6. **Assertions.** Is check 18's existing coverage correctly stated, and are the four required additions plus the reporting defect correctly identified? Is any assertion missing that would let a violating stack pass?
7. **Observability.** Does the denial record and metric make direct-path denials observable without creating a value-disclosure path, and are the fields minimal and safe given caller-controlled values?
8. **Retest criteria.** Do §10.1 and the OV cases cover the retest criteria Security named, including the N-03 interaction, and is OV-03 (platform 401 before the gate) correct rather than a defect?
9. **Unproven list.** Is anything claimed as established that is not, and is anything material missing from §12?
10. **Scope discipline.** Is the WebSocket adjacent surface correctly excluded and flagged rather than absorbed or ignored?

## Explicitly out of scope

Do not reopen or re-derive: D1–D9/A1/A2/R1/R2, G1–G5, C3 golden bytes, payment/retention semantics, the rev3 N-01/N-03..N-07 dispositions, DR3-01, the owner's K-1/K-2/K-3/P-1/D-D/D-A/CA-1 decisions, SR2-01/02, NV-05/AD-01, D-5 or the public-surface evidence list. Option A versus Option B is an owner decision already made; review only whether this delta implements Option B's conditions faithfully. No runtime, browser, issuer, IAM, Terraform plan/apply, deployment or QA evidence is claimed anywhere in this directory.

## Requested output

1. Verdict per condition (1)–(4): SATISFIED / PARTIAL / NOT SATISFIED, with basis.
2. Any new finding, numbered `N02D-nn`, with severity.
3. At most three direct questions, only where a human decision is genuinely required.
