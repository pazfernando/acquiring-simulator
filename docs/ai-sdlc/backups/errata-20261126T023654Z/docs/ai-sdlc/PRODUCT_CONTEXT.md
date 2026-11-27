# Product context — Acquiring Processor Simulator

This is the product-local routing context for the acquiring simulator. It is a documentary contract consumed by agents; it is not automatically loaded runtime configuration, and it grants no approval, permissions or release authority. Paths are repository-relative and resolve from the repository root, not from an agent working directory. The shared AI-SDLC workspace owns reusable team procedure; this repository owns product rules, vocabulary and work items.

Product work starts from `PROJECT.md` (execution facts), the selected `work-items/<id>/WORK_ITEM.md` (human scope and selected source revisions), the approved specifications/ADRs named there, and the functional sources indexed below. A listed source is evidence, not approval.

```json
{
  "schema_version": 1,
  "product": "acquiring-simulator",
  "domain": "acquiring-payments",
  "requirements_review": {
    "required": true,
    "agent": "ai-sdlc-acquiring-domain",
    "rationale": "Fernando Paz confirmed retaining ai-sdlc-acquiring-domain as the independent acquiring/payment specialist and approved this product-context split on 2026-11-26. Every new requirements revision and every revised requirements revision requires that reviewer's independent verdict (PASS, otherwise CONDITIONAL/BLOCKED with correction and renewed review)."
  },
  "functional_sources": [
    {
      "path": "docs/ai-sdlc/sources/acquiring_simulator_functional_guide.md",
      "status": "draft — local merged v0.5.3 dated 2026-11-19, publication pending; its independent-review status is contested between current documents (unresolved, see below)",
      "purpose": "Current working functional guide: sections 1–13 business process plus appended section 14 Channel Test Interface and section 15 Cross-cutting Observability. Working draft, not an approved baseline for dependent construction."
    },
    {
      "path": "docs/ai-sdlc/sources/imports/2026-11-18/acquiring_simulator_functional_guide.md",
      "status": "historical import — pinned upstream guide v0.5.2, SHA-256 5832d317cd79111aa2172c22f317902ce98125d8e93a3ef05b5c9b224d695e08; immutable evidence",
      "purpose": "Supplied upstream guide retained byte-for-byte for the 2026-11-18 AWS mapping. Historical evidence, not current instruction."
    },
    {
      "path": "docs/ai-sdlc/sources/imports/agent-workspace/acquiring_simulator_functional_guide.md",
      "status": "reviewed baseline — v0.5.1, MD5 703fd59f66a3d097147eee79141e2524; reviewed only for the original pinned WI-ACQ-AUTH-001 bundle",
      "purpose": "Baseline against which the Operation A requirements were drafted and independently reviewed. Its approval scope is limited to that pinned bundle and does not extend to later drafts."
    },
    {
      "path": "docs/ai-sdlc/sources/imports/2026-11-18/software_components_aws.md",
      "status": "historical design input — consolidated AWS mapping v0.3.0, SHA-256 1d5b648c13698060895788da8c60f8f3340d5ffbf5844244fb2ef30290a6befb; not deployment evidence",
      "purpose": "Proposed AWS component mapping used for the Operation A AWS path. Component mappings and diagrams are design evidence, never proof of deployment."
    },
    {
      "path": "docs/ai-sdlc/sources/imports/2026-11-18/manifest.json",
      "status": "historical import manifest — retrieved date, Drive URLs and SHA-256 pins",
      "purpose": "Records source identity for the pinned 2026-11-18 import so later revisions can be distinguished from historical bytes."
    }
  ],
  "skills": [
    {
      "name": "payment-behavior-review",
      "roles": [
        "ai-sdlc-requirements",
        "ai-sdlc-acquiring-domain",
        "ai-sdlc-qa"
      ],
      "purpose": "Payment-semantics and test-coverage review for acquiring/payment work only, always against this product's selected functional sources. Availability was checked read-only on 2026-11-26 with the supported discovery command for each declared role; see the prose note below."
    }
  ],
  "glossary": "docs/ai-sdlc/GLOSSARY.md",
  "constraints": "docs/ai-sdlc/PRODUCT_CONSTRAINTS.md"
}
```

## Source status and unresolved selection

The review status of functional guide v0.5.3 is **contradicted by current product documents**, and this context deliberately does not resolve it:

- `docs/ai-sdlc/README.md` states that version 0.5.1 remains the reviewed baseline and that "no independent review of 0.5.3 exists yet", so the reviewed 0.5.1 baseline governs where 0.5.3 differs on Operation A semantics.
- `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/WORK_ITEM.md` states that functional guide 0.5.3 and its replacement sequence diagrams "passed independent documentation review", with later Drive synchronization.

Both statements are recorded evidence about the same document. This context does not decide source authority, does not recertify any approval, and does not edit the work items, reviews, manifests or functional guides. The exact source revision and approval selection must be made explicitly in each new work item before dependent construction. Routing and evidence gathering may proceed; new implementation that depends on Operation A or observability semantics remains blocked until that selection is recorded and any required review covers the exact revision.

The reviewed 0.5.1 baseline's approval scope is limited to the original pinned `WI-ACQ-AUTH-001` bundle. The pinned 0.5.2 import and AWS mapping are historical evidence. None of these statuses is upgraded by being listed here.

## Availability evidence (read-only)

On 2026-11-26 the configured reviewer `ai-sdlc-acquiring-domain` was present in agent configuration with model `openai/gpt-6.1-sol`, and `ai-sdlc-coordinator` was granted delegation to `ai-sdlc-requirements`, `ai-sdlc-acquiring-domain`, `ai-sdlc-implementation`, `ai-sdlc-qa`, `ai-sdlc-devops` and `ai-sdlc-security`.

The skill `payment-behavior-review` was checked with the supported read-only discovery command for each declared consumer. For `ai-sdlc-requirements`, `ai-sdlc-acquiring-domain` and `ai-sdlc-qa` it was reported eligible, model-visible and command-visible, not disabled, not blocked, not agent-filtered and with no missing requirements. No skill was installed, authenticated or reconfigured to obtain this evidence. A path on disk alone was not treated as proof.

## Boundaries

Team procedure, reviewer verdicts and delivery gates remain in the shared workspace documents and `VERIFICATION_DELIVERY.md`. Data-safety, prohibited external targets and simulation boundaries for this product are owned by `docs/ai-sdlc/PRODUCT_CONSTRAINTS.md`. Historical runtime provenance is recorded there as provenance, not as active generic policy.
