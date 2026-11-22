# P2a6 evidence manifest — pure NEW-request freshness window

- Work item: WI-ACQ-OBS-001 (protocol slice).
- Phase: P2a6 — one pure validator for the approved 300-second NEW-request
  freshness rule, plus deterministic tests. Not admission.
- Run start: `2026-11-20T20:15:46Z`; evidence folder
  `p2a6-20261120T201546Z`.
- Repo: `/home/pazfernando/projects/acquiring-simulator`.
- Branch: `feat/wi-acq-auth-001`.
- HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged; no Git write).
- Origin: `https://github.com/pazfernando/acquiring-simulator.git`.
- Model: provider `nan`, model `deepseek-v4-flash`; `model_override=none`.
- Toolchain: JDK 25.0.5 and Maven 3.10.1 (same local prefixes as P2a4/P2a5).
- Runtime: inactive. No service start, AWS call, HTTP, storage change,
  credential flow, publication, deletion or specification/vector edit.

## Approved rule applied

Exact source (`iso8583-profile.md`, P2): **“New first dispatch sent must be
within 300 seconds of server receipt; authorized historical replay/late evidence
uses persisted request, not this freshness check.”**

- Distance is computed on the full Gregorian `sent` instant and the supplied
  server receipt instant: `|receipt - sent| <= 300 s` is `FRESH`.
- Exactly `300.000 s` is inside (`FRESH`); `300.001 s` and `301 s` are `STALE`.
- No padding, truncation, nearest-year inference or extra clock-skew allowance
  is added. The cross-year example is two full instants, not a month/day
  comparison.
- `ADMISSION_FRESHNESS` is the first-failure label taken from the approved
  `D-stale` vector.
- The mode is a trusted server-side enum, **never decoded from the wire**. For
  `AUTHORIZED_HISTORICAL_REPLAY` and `LATE_EVIDENCE` the validator returns
  `NOT_APPLICABLE` without reading `sent`/`receivedAt`; the persisted original
  request stays authoritative and is not re-encoded or re-dispatched.

## Delivered files (4 new; no pre-existing input edited)

| File | SHA-256 |
| --- | --- |
| `Iso8583AdmissionMode.java` | `498621629ab1bb107162493893cdff810011c0bb683d38f2204376581dccefcd` |
| `Iso8583FreshnessDecision.java` | `23747f3b49d9960b2afa3cb036a0e3cf9674a58d992a412b11feedca2c9bc789` |
| `Iso8583FreshnessValidator.java` | `ab5c60a9ce6c9361590b11182c13772f526977e908f687374dfac9cfbb4e1068` |
| `Iso8583FreshnessValidatorTest.java` | `78e99766ee0d8a82a3897e0081092fc75c0eb0484527a39ee787f6c27030f9db` |

No existing source, profile, vector, fixture or test was modified. Canonical
DE48 schema, domain, P4, binding, registry/mandate/route resolution, 826
admission, response packing, C4, HTTP and runtime remain untouched.

## Deterministic tests (actual)

`Iso8583FreshnessValidatorTest`: **5/5**, failures 0, errors 0, skipped 0,
exit `0`, stderr empty.

1. `rejectsApprovedDStaleFromItsActualHex` — reads `D-stale` `hex`, scans it,
   parses the canonical DE48 request, extracts `sent=20261118115459`, uses the
   vector's `receivedAt=2026-11-18T12:00:00Z`, and gets `STALE` with
   `ADMISSION_FRESHNESS` (301 s before receipt).
2. `acceptsExactlyThreeHundredSecondsAndRejectsJustOutside` — exact 300 s
   `FRESH`; 301 s and 300.001 s `STALE`.
3. `usesTheFullGregorianInstantAcrossTheYearBoundary` — `20261231235959` and
   `20270101000001` against `2027-01-01T00:00:00Z` are `FRESH`;
   `20261231235459` is `STALE` (no year-wrap bug).
4. `doesNotApplyNewFreshnessToAuthorizedReplayOrLateEvidence` — stale value and
   null timestamp both `NOT_APPLICABLE` in the two non-new modes, proving the
   validator does not re-read or re-evaluate the original identity.
5. `requiresATrustedModeAndNewDispatchInputs` — missing mode/sent/receipt fails
   closed.

No full protocol regression was run this turn; P2a5’s saved 407/407 remains the
last full protocol run and is unchanged by these additive files.

## Hash and status correspondence (before edits → after target test)

- `hashes-before.sha256`: 177 inputs; `hashes-after.sha256`: 181 inputs.
- `hashes-changed-or-removed.txt`: **empty**, exit `0` — every pre-existing
  input is byte-identical.
- `hashes-diff.txt` exit `1` only because it lists the four **added** files; no
  `<` (modified/removed) line exists.
- `git-status-before.txt` → `git-status-after.txt` → `git-status-final.txt`;
  HEAD after equals the binding above.
- `targeted-stdout.txt` records main recompilation of 121 sources (including the
  new classes), test recompilation of 50 sources and the 5/5 run;
  `targeted-stderr.txt` empty; `targeted-exit.txt = 0`.

## Boundary

Local JUnit/byte-fixture evidence only. This is **not** authentication,
attestation/crypto verification, registry/fixture/mandate/route resolution,
domain re-validation, P4/binding admission, 826 admission, duplicate/conflict
resolution, HTTP/runtime behaviour, independent QA, Security review or human
acceptance. P1 and G6 remain open. No Git write or publication was performed.
