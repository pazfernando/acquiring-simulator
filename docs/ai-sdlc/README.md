# Product specification and decision record

This repository is the source of truth for the acquiring simulator's product specifications, reviews, owner decisions and architecture decisions. Start implementation from the checked-out revision of `work-items/WI-ACQ-AUTH-001/WORK_ITEM.md`, its linked requirements, approved `architecture-decision.md` and `PROJECT.md`. The shared AI-SDLC workspace contains reusable team procedure, not a second copy of these product decisions.

`sources/acquiring_simulator_functional_guide.md` is the product-local copy of the supplied functional guide. Preserve the approval scope and exact content identities recorded in the work item; moving a file does not constitute a new approval. Confirm unresolved QA/Security/acceptance gates independently.

## Migration from the agent workspace

On 2026-11-18, the product record and complete work-item directory (including immutable `revisions/`) were moved byte-for-byte from:

- `/home/pazfernando/.openclaw/workspace/shared/ai-sdlc/projects/acquiring-simulator/PROJECT.md` to `PROJECT.md`
- `/home/pazfernando/.openclaw/workspace/shared/ai-sdlc/work-items/acquiring-simulator/WI-ACQ-AUTH-001/` to `work-items/WI-ACQ-AUTH-001/`

Historical documents can still contain their original absolute paths, status at the time of writing and MD5 references. Those strings are historical evidence, not live file locations or current status. Resolve a historical path by replacing the old directory prefix with this repository's `docs/ai-sdlc/work-items/WI-ACQ-AUTH-001/`; do not rewrite sealed revisions or alter hashes to make them look current. Use current Git revisions and the latest owner decisions for new handoffs.

No remote, publication, QA, Security sign-off or human acceptance is implied by this migration.
