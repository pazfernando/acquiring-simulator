# Operation A — current status and completion backlog

Start with [current delivery status and traceability](work-items/WI-ACQ-OBS-001/delivery-status.md) and the [actionable completion plan](work-items/WI-ACQ-OBS-001/operation-a-completion-plan.md). Historical work-item paragraphs and specification headers retain their original approval context; later owner decisions and dated implementation evidence must be considered before inferring current status. Component test counts are not product acceptance.

## Product context and split

Product routing context: [PRODUCT_CONTEXT.md](PRODUCT_CONTEXT.md) (schema v1 declaration). Product terminology: [GLOSSARY.md](GLOSSARY.md). Data-safety and simulation boundaries: [PRODUCT_CONSTRAINTS.md](PRODUCT_CONSTRAINTS.md). Reusable team procedure remains in the shared AI-SDLC workspace. These links do not grant approval, permissions or release authority.

The review status of functional guide v0.5.3 is **unresolved**: the statement in the next section that no independent review of 0.5.3 exists conflicts with `work-items/WI-ACQ-OBS-001/WORK_ITEM.md`, which records that v0.5.3 passed independent documentation review. Both are preserved as recorded evidence. This split does not resolve which is current, does not recertify any approval and does not change any work item, review, manifest or functional guide. Each new work item must select its exact source revision and approval basis; dependent new implementation stays blocked until then.

# Current implementation update

For the Operation A AWS path, read `work-items/WI-ACQ-AUTH-001/aws-architecture-update.md` and `../aws-operation-a.md`. The current working functional source is `sources/acquiring_simulator_functional_guide.md`, **version 0.5.3 — local merged draft dated 2026-11-19, publication pending**. Upstream guide v0.5.2 and consolidated AWS mapping v0.3.0 remain pinned as historical imports under `sources/imports/2026-11-18/`. Version 0.5.1 remains the baseline against which the Operation A requirements were drafted and independently reviewed; no independent review of 0.5.3 exists yet. Where 0.5.3 differs on Operation A semantics, the reviewed 0.5.1 baseline governs until the difference is reviewed. Previous local approval hashes are historical evidence and are not rewritten. No new independent review or deployment approval is claimed.

# Product specification and decision record

This repository is the source of truth for the acquiring simulator's product specifications, reviews, owner decisions and architecture decisions. Start implementation from the checked-out revision of `work-items/WI-ACQ-AUTH-001/WORK_ITEM.md`, its linked requirements, approved `architecture-decision.md` and `PROJECT.md`. The shared AI-SDLC workspace contains reusable team procedure, not a second copy of these product decisions.

`sources/acquiring_simulator_functional_guide.md` is the product-local copy of the supplied functional guide. Preserve the approval scope and exact content identities recorded in the work item; moving a file does not constitute a new approval. Confirm unresolved QA/Security/acceptance gates independently.

## Migration from the agent workspace

On 2026-11-18, the product record and complete work-item directory were first copied to the repository with their original bytes preserved. Immutable `revisions/` and the approved requirements, scenarios, profile, review and architecture retained their original hashes; the mutable `PROJECT.md` and `WORK_ITEM.md` were then updated to point to product-local documents. Former locations:

- `/home/pazfernando/.openclaw/workspace/shared/ai-sdlc/projects/acquiring-simulator/PROJECT.md` to `PROJECT.md`
- `/home/pazfernando/.openclaw/workspace/shared/ai-sdlc/work-items/acquiring-simulator/WI-ACQ-AUTH-001/` to `work-items/WI-ACQ-AUTH-001/`

Historical documents can still contain their original absolute paths, status at the time of writing and MD5 references. Those strings are historical evidence, not live file locations or current status. Resolve a historical path by replacing the old directory prefix with this repository's `docs/ai-sdlc/work-items/WI-ACQ-AUTH-001/`; do not rewrite sealed revisions or alter hashes to make them look current. Use current Git revisions and the latest owner decisions for new handoffs.

No remote, publication, QA, Security sign-off or human acceptance is implied by this migration.

### Remaining workspace artifacts (2026-11-22)

- [Original simulator-specific agent pack and functional guide](sources/imports/agent-workspace/) are historical imports with their original bytes. The guide in that directory does not replace the current product-local guide or alter the reviewed baseline.
- [Coordinator dispatch note for WI-ACQ-OBS-001](work-items/WI-ACQ-OBS-001/revisions/pending-dispatch-20261120.md) is a point-in-time record, not the current task list or a new approval.
- [Project-bound OpenCode ACP preflight](tools/opencode-acp-preflight.py) is retained as a historical diagnostic. It was not executed during migration and is not the current setup procedure.
