# Pending coordinator dispatches — WI-ACQ-OBS-001 (Operation A)

Updated 2026-11-20 21:10 America/Bogota. Both running tasks have landed on disk; the Coordinator
verified each hash directly because two return channels aborted after writing.

## Delivered, awaiting follow-up

1. Retention draft (Requirements run `8231d336-f11f-4268-8ff9-3fcfcc5f4530`):
   `design/retention/retention-delta.md` — `ff5fbba0a8a92f37af971b3b8d308230277e3cace4d29156f0acf8d9555fe6af`,
   plus `design/retention/evidence/` (official snapshots, contract excerpts, vectors,
   `source-close-check.json`, `SHA256SUMS`). Approved sources verified unchanged afterwards.
2. Second compatibility pass (Implementation run `362b84c3-73b2-4cd6-a2fe-4c2776820b92`):
   `design/access-realization/compatibility-assessment-rev2.md` —
   `2f7cfe054aae7d908948288b6e67799aef7c864e1acaeb67c7444e63c432b930`.
   **Incomplete:** `evidence-rev2-assessment/` was never written; the document's evidence
   references do not resolve.

## To dispatch next (full turn required; child-completion turns lack dispatch authority)

1. **Implementation** — write only `evidence-rev2-assessment/` for the existing assessment:
   per-file SHA-256, verified input hashes, explicit unverified list. No rewrite of the document
   unless a recorded hash is wrong, in which case report it.
2. **ai-sdlc-acquiring-domain** — independent review of the exact retention draft above plus its
   evidence hashes. Must judge U1/U2, the record matrix and the no-resurrection rules, claim no
   closure, and not mutate approved files. Note for the reviewer: `WI-ACQ-AUTH-001/requirements.md`
   R20 (approved, HD-02) already fixes 30 seconds from dispatch, and A07 repeats it, so U2 is
   resolved by precedence over the older AWS note; U1 remains a genuine business decision only if
   records must eventually be destroyed.
3. **ai-sdlc-security** — independent re-review of rev2 against exact hashes (listed below), after
   the quota-sharing Requirements run has settled.

### Security review inputs

- `design/access-realization/design-rev2.md` — `6b15161875e702ebae74e4f76b4b94cae9ffbbb94d6a9fcd42aee563b8f3873c`
- `evidence-rev2/input-identities-rev2.json` — `8fe8adac9762540a58cc89bb60c8f1202533691ff9d4f8645a21d9e9da3bea42`
- `evidence-rev2/official-source-excerpts-rev2.json` — `adebdd076cb09c42f09dfb08855c1873cbfd6505be178c2005a6c42337e8ddfc`
- `evidence-rev2/revision-validation.json` — `7b720c0de1d20755c9bbcd69b3aff2595d1df79724667d3bc6d6a222a4e56652`
- `compatibility-assessment.md` — `4886106c0afbf9ded1736ef3c0756c2bc8c738c58c61457faa0a635ecdd836ac`
- `compatibility-assessment.md` evidence `manifest.json` — `2a4c400d752184424b5704a3cafd05abfc0060ffb443991fc12168f272d99dfe`
- `compatibility-assessment-rev2.md` — `2f7cfe054aae7d908948288b6e67799aef7c864e1acaeb67c7444e63c432b930`
- `owner-decisions.md` as authority overlay (D-D accepted, D-A self-managed Keycloak, CA-1 internal)
- earlier review `reviews/access-design-20261120/security-review.md` — `0d71ea007e56b12d9873df14126c22640c3abc68052b42db1f47b0a9688d218e`

## Defaults adopted (owner may override)

- K-1: one Keycloak task, accepting that an outage blocks new authentication.
- K-2: owner accountable with a DevOps runbook, no on-call commitment or SLA.
- K-3: presenter-local relay key accepted within the approved presenter-local boundary.
