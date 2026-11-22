# Functional guide merge and sequence documentation receipt

Status: local documentation merge only, publication/review pending. No new architecture approval, construction, Drive cleanup/publication or runtime/QA claim.

## Immutable inputs
- Original canonical local0.5.1: revisions/guide-local-baseline-md5-703fd59f66a3d097147eee79141e2524.md, MD5 703fd59f66a3d097147eee79141e2524.
- Complete Drive0.5.2 readback: revisions/guide-drive-baseline-md5-69ca7e39cc0cfd595eb19c28c5087c15.md, MD5 69ca7e39cc0cfd595eb19c28c5087c15. Source /tmp/acquiring-guide-drive-baseline.md unchanged.
- Existing import0.5.2 at ../../sources/imports/2026-11-18/acquiring_simulator_functional_guide.md remains unchanged; old WI source identities not relabeled.
- Owner authority owner-decisions.md144186943990aaa585283b9625a16b95 selects one demoJWT, presenter screen-share and seven-day functional history. Activity2f8f752b6cad11c77f6f2136cc6c0914 and observation3bdbfc869917e7b9e916c7e2c732af6f read, not modified. Later owner decisions supersede their stale pending exposure/retention labels; no broader construction permission inferred.

## Conflict assessment
Compared shared sections1–12 substantively after explicit Markdown-format normalization: 413 normalized lines equal, ZERO conflicting business statements detected. Raw diff retained at revisions/guide-overlap-local-703fd59f66a3d097147eee79141e2524-vs-drive-69ca7e39cc0cfd595eb19c28c5087c15.diff, MD5 cabc9d6ecb2fe0d51fcac4eb468b952b. Differences are version metadata, escaped numbered headings/URL display, table alignment, trailing line breaks/blank lines/nbsp and equivalent reference-link formatting. No local-only business rule was dropped by taking the complete Drive baseline.
Drive0.5.2 adds Section13 Network and issuer failure scenarios/addendum. It is preserved IN FULL, including NET/ISS/PROC/REV/INQ cases, evidence contract and architecture evolution note. No replacement/re-numbering as new Section13. The broader catalog does not commission B/C or automatic reversal in current A-only WI; new Section14 states that boundary without deleting source text.

## Exact authored changes
Canonical guide now0.5.3 (LOCAL MERGED DRAFT, not a published upstream version), date2026-11-19; all Drive body content retained, only top version/date explicitly changed, then APPENDED14 Channel Test Interface (channel-interface test) and15 Cross-cutting Observability.
Section14 explains purpose, single demo JWT/screen-share, current synthetic A use and distinct outcomes, no automatic retries, optional live/history UI, approved7days and links WI/sequence. Section15 makes observability TRANSVERSAL to all implemented/future flows, mandatory common hook/manifest, critical-vs-best-effort-vs-sampled evidence, honest gaps/retention/privacy and no financial authority from telemetry.
sequence-flow.md/.mmd is the main payment panel; sequence-recovery.md/.mmd is the independent notification/history/catch-up panel. Split to preserve readability. They document existing gates/evidence/selected same-source direction, not new wire or architecture rules. Original activity stays historical local, byte-unchanged. Drive cleanup/publishing belongs to coordinator.

## Performed checks and limits
See revisions/guide-sequence-document-checks.json. Both Markdown Mermaid blocks equal corresponding .mmd bytes; sequence control nesting and participant references checked; main contains one financial invocation after mandatory claim/possible-send atomic gates; recovery has none; route install/ACK observedA then strong fixedH>=A order checked. Sections1–15 unique/ordered, Section13 retained, local link paths resolve. No Mermaid render, PNG, API/auth/JWT/transport/transaction/ADOT/codec/crypto/QA or independent review performed. Configured MCP is incomplete; prior supplied Drive bytes read locally, no MCP-backed fetch/publication claimed.
