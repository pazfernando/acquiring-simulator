# Product repository instructions for coding agents

Start in this repository. Read `docs/ai-sdlc/README.md` and the selected `docs/ai-sdlc/work-items/<id>/WORK_ITEM.md`; follow its checked-out requirements, acceptance scenarios, approved architecture decision and owner decisions. Verify document revisions and approval evidence before implementing. Do not use the former agent-workspace work-item paths as live specifications. Historical snapshots under `revisions/` are evidence, not current instructions.

Follow the repository's code and test conventions. Report actual test results, unverified paths and the branch/commit used. A local implementation commit is not independent QA, Security review or human acceptance. Do not push, open a PR, merge, deploy or release without explicit scoped human authorization.

## Diagram presentation convention

In current product Markdown, present diagrams as a rendered PNG first. Keep the exact Mermaid source in an editable `.mmd` file beside the image and link it as the source of truth; do not embed a Mermaid code block as the primary view. The PNG must be rendered from the same exact `.mmd` source, and the Markdown image/source links must resolve relative to their file. When extracting diagram source from a guide, copy it byte-for-byte and do not change diagram semantics. Historical baselines, imports, revisions, signed/pinned manifests and fixture evidence are exempt: preserve their original bytes and Mermaid content. External publication or Drive synchronization requires separate authority and is not implied by a local PNG export.
