# Product documentation errata

This file records **current corrections** for the acquiring simulator. It exists so that stale or ambiguous statements can be corrected without rewriting approval-bearing historical records. It does not amend approvals, hashes, sealed revisions, reviews, manifests or functional guide bytes, and it grants no scope.

Each entry states what the historical record says, the exact verified evidence, the active correction and the preserved scope.

## E-001 — WI-ACQ-AUTH-001 source path now resolves to v0.5.3, not v0.5.1

**Historical record (unchanged).** `work-items/WI-ACQ-AUTH-001/WORK_ITEM.md` line 9 cites `../../sources/acquiring_simulator_functional_guide.md` (from the work-item directory) as "Functional Process Guide v0.5.1 … md5 703fd59f66a3d097147eee79141e2524".

**Verified now.**

| Item | Path | Identity |
|---|---|---|
| Repository path named by the work item | `docs/ai-sdlc/sources/acquiring_simulator_functional_guide.md` | v0.5.3; MD5 `d59bfa43d0be030f1c80fac8c549edc2`; SHA-256 `90a798c7e2a1de362993cb4974d306497d7de3567c163a3511c07b194a5ae094` |
| Preserved v0.5.1 bytes (product import) | `docs/ai-sdlc/sources/imports/agent-workspace/acquiring_simulator_functional_guide.md` | MD5 `703fd59f66a3d097147eee79141e2524` (matches the recorded value); SHA-256 `7f8d8e5df007116b44d29b35b3e0d2058757565631ee0f9518e3612c6b535f4b` |
| Identical preserved v0.5.1 copy | `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/revisions/guide-local-baseline-md5-703fd59f66a3d097147eee79141e2524.md` | MD5 `703fd59f66a3d097147eee79141e2524` |

**Active correction.** For the reviewed v0.5.1 baseline, use the preserved product import path above. The work-item line is not rewritten because it is approval-bearing. Its recorded MD5 still identifies the correct bytes at the preserved location; only the cited path is stale.

**Preserved scope.** The v0.5.1 approval scope remains limited to the original pinned `WI-ACQ-AUTH-001` bundle. Nothing here promotes v0.5.3 or extends approval.

## E-002 — v0.5.3: bounded documentation review exists; domain requirements approval does not

The two current statements are reconcilable only when the review type and exact bytes are distinguished. They are not evidence of a domain requirements approval.

**What exists — a bounded independent documentation review.**

- Artifact: `work-items/WI-ACQ-OBS-001/domain-review.md`.
- Artifact MD5 `0ce3d9fc2f2eea5dcc2dc1cba9bcdb56` (matches the review MD5 recorded at `WI-ACQ-OBS-001/WORK_ITEM.md` line 210).
- Verdict: **PASS — documentation merge and sequence-domain semantics ONLY**; 0 HIGH / 0 MEDIUM / 0 LOW. It states it is not architecture/construction approval, independent QA, provider publication, deployed security verification or compliance certification.
- Exact reviewed inputs (from that report's manifest):
  - guide `sources/acquiring_simulator_functional_guide.md` MD5 `db00cd4ef3c57166b18f188b3db9fa8c`;
  - `sequence-flow.md` MD5 `38e39163d272292ad405f2f8f4618594`, `sequence-flow.mmd` MD5 `689d6bad9a50af41246b4550c4e92d03`;
  - `sequence-recovery.md` MD5 `d9d77db0d7121aee8d204329f823a439`, `sequence-recovery.mmd` MD5 `a64edb1e04653de3fce707a865b17fd3`.

**Why the current bytes are not that reviewed revision.**

- Current guide: MD5 `d59bfa43d0be030f1c80fac8c549edc2`, SHA-256 `90a798c7e2a1de362993cb4974d306497d7de3567c163a3511c07b194a5ae094`.
- A later presentation-only PNG-first pass recorded guide MD5 `b2587503824327ed39e1bbba16375a17` (`WI-ACQ-OBS-001/WORK_ITEM.md` line 234) and stated that the reviewed/published hash `db00cd4…` predates it.
- Current `sequence-flow.md` MD5 `6823d8b94db1542431e86702493dbd3d` and `sequence-recovery.md` MD5 `8650a0da009b1006a1666058a0b3b9c5` also differ from the reviewed Markdown bytes. The `.mmd` files still match their reviewed hashes.

**What the later reviews are.** `reviews/retention-20261121`, `reviews/contract-delta-20261122` and `reviews/continuation-rev3-dr301-20261123` pin the current guide SHA-256 `90a798c7…` as an input, but they are bounded delta reviews of retention, contract deltas and a specific correction. They are not a standalone documentation review of v0.5.3 and not a domain requirements approval. `reviews/retention-20261121/domain-review.md` says the earlier bounded documentation PASS "is a prior bounded documentation PASS, not retention implementation approval".

**What does not exist.** No independent domain **requirements** approval of v0.5.3 as a whole or of its Operation A semantic delta. The approved Operation A requirements remain tied to the reviewed v0.5.1 baseline (MD5 `703fd59f…`).

**Reading the two statements.** `WI-ACQ-OBS-001/WORK_ITEM.md` is correct for the bounded documentation/sequence scope on the earlier exact bytes. `docs/ai-sdlc/README.md` is correct for requirements approval; read literally as "no review of any kind", it would conflict with the documented bounded PASS. The active README wording has been qualified to name the review type, and the reviewed v0.5.1 baseline continues to govern where v0.5.3 differs on Operation A semantics.

**Remaining precise human decision (not resolved here).** Fernando decides whether to:

1. commission an independent domain requirements review of the current v0.5.3 bytes, or of a defined delta against v0.5.1; and
2. adopt v0.5.3 (or a reviewed successor) as the Operation A functional baseline.

Until that decision is recorded, no approval is claimed and dependent new implementation remains blocked on exact source/approval selection.
