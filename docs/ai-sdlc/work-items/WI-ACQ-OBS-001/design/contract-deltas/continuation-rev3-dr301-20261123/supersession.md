# Supersession note — DR3-01 old-prior fixture correction

Status: DRAFT / NOT NORMATIVE. Documentary correction only. No independent review closure, no design approval and no construction authorization is claimed or implied. Owner continuation of 2026-11-23 remains the authority; this note neither requests nor records new permission.

## Exactly what this directory replaces

| Item | Exact identity | Disposition |
| --- | --- | --- |
| File replaced as the C3 old-prior oracle set | `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/contract-deltas/continuation-rev3-20261123/old-prior-fixtures.json`, SHA-256 `f07439b6d93d4002209db34b9bf9871c7ee1c381a8b5be2b5a0ac0cc324b345c` (21-entry packet manifest `2000bb004e68e6d2974bef3067baeae6774f1922a21dca4ea18fecf6944ab467`) | Superseded **only** by `old-prior-fixtures.json` in this directory, SHA-256 `1d3f1406b05d95f87e18f37b33eea3bb855ad5c80488f92891dcc132b5f3d5f5`. The reviewed file itself is untouched. |
| Cases whose adversarial precondition was defective (DR3-01) | `after24h-different-data`, `after7d-different-data` | Retained **verbatim** in the corrected file as valid but non-discriminating regression oracles. They are superseded **as the proof of prior-reference precedence**: each submitted amount `10.01` while both `original.canonicalEqualityData.amount` and `newHead.canonicalEqualityData.amount` stayed `10.00`, so a latest-head-first implementation also returned the expected `409 PAYLOAD_CONFLICT`. |
| Cases added to close DR3-01 | (new, no predecessor) `after24h-different-data-head-match`, `after7d-different-data-head-match` | In the corrected file only. These are the authoritative different-data oracles. |

No other file, case, golden byte, contract clause, disposition, finding, manifest entry or approval record is superseded, amended or withdrawn by this directory.

## What changed and what did not

- Every one of the ten inherited cases is reproduced with byte-identical case text; the only difference outside the added block is the file's own `status` line, which now declares this supersession.
- `expected` for both new cases is the existing reviewed C3 `409` `PAYLOAD_CONFLICT` envelope, unchanged: same `httpStatus`, same `body` object, and the same 317-byte canonical `bodyUtf8` golden shared by both boundaries (`asOf` `2026-11-18T12:00:05Z`). Historical C3 response golden bytes are not modified, re-serialized or extended.
- `responseClockInjected` is retained as the existing fixture compatibility device that keeps those historical `asOf` strings stable. Admission still uses the case `now`; the injected clock must not be frozen in production.
- The only material change is the adversarial precondition in the two added cases: `newHead.canonicalEqualityData` now equals the submitted canonical projection (amount `10.01`) while the pinned original stays `10.00`, so the newer head is the tempting-but-wrong record.
- `continuation-rev3-20261123/` was read only. Its `OUTPUTS.SHA256SUMS` re-verified 21/21 OK after this directory was written.

## Effective reading rule

Until an independent reviewer accepts these bytes in `re-review-brief.md` scope:

- `continuation-rev3-dr301-20261123/old-prior-fixtures.json` governs the C3 old-prior reference oracles.
- The two new cases add two cases to the previously reviewed count of ten, for twelve total; prior counts of "10 isolated old-prior cases" in the rev3 packet remain historically accurate for that packet and are not silently rewritten.
- Everything else continues to be read from the reviewed rev3 packet exactly as recorded there.

This note does not close DR2-02, G-5 or DR3-01. Only an independent review of these exact bytes, followed by QA execution, can do that.
