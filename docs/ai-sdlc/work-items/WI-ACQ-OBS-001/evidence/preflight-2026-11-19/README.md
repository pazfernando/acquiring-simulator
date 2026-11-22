# Technical preflight evidence — WI-ACQ-OBS-001 — 2026-11-19

Status: preserved diagnostic artifacts from the interrupted technical preflight, moved out of `/tmp/opencode` so they survive host cleanup. These are **not product code**, **not** a codec implementation, and **not** QA, Security or scheme-conformance evidence.

Authority: `../../owner-decisions.md`, "Architecture approval and technical-preflight commission" (2026-11-19). Bounded by the same evidence constraints as `../../technical-preflight.md`.

## Contents

| File | SHA-256 | Role |
| --- | --- | --- |
| `gen-fixtures.py` | `0be6f4a1772a7a4494eb58faaa105fce3b6a02701296eed59e4383a97b703ff2` | Extracts `vectors/packed-vectors.json` into a probe TSV. |
| `packed-fixtures.tsv` | `88528ecadbf6c41676ea211cd8dbfe07e170555d64ba7340174678752ac7cc3d` | 85 fixture rows (id, expectation, first failure, length, sha256, hex, decoded fields, extension). |
| `IsoProbe.java` | `d782b052dd937a29e767cc3562f959669ae3436101b3431d1e19090d026d6615` | Disposable pure-JDK structural probe. **Corrected** during this turn (see below). |
| `java-probe-stdout.txt` | `4cdcc829068486abd17e947ef92154d7198440b5699498363a410bb94e9da3df` | Captured Java probe output after correction. |
| `iso_probe.py` | `a895a6a3fd42c921b0d9d3d917e7e7a517c462e8251f03ca24bf5a29ca16eaf5` | Python cross-check probe. |
| `python-probe-stdout.txt` | `e51720234cac2bf75d179d226fa450d00f5bef85c7c9dc069b8a162e3508056a` | Captured Python probe output. |
| `ddb-probe.sh` | `c40ad7b296bf452ee2d1006327aa89126b53f20fdc7e5e02f6af0b99f5fb2066` | DynamoDB Local probe script. **Unchanged and known-defective** (see `../../technical-preflight.md` §3). |
| `ddb-tx40.json` | `faad75e923fdc9ef7d2bab52df065d2e622f3eb54b64daa4fe13aff0ced26710` | 40-action transaction payload (execution input, not result). |
| `ddb-tx101.json` | `c688d5fd8a68b1936d6368de00cf63a85ee60fd428482e5d31704fa8058e5e53` | 101-action transaction payload (execution input, not result). |

`ddb-big.json` (413 KB) was intentionally not copied; its SHA-256 `697475b7…35b0a137` is recorded in `../../technical-preflight.md`. It targets a different, earlier table (`acq-preflight`) and is not needed to reproduce a test.

## DE42 discrepancy — RESOLVED

The preflight recorded an unresolved Java-vs-Python divergence around DE42. Root cause was a **probe defect**, not a profile or vector defect:

1. `IsoProbe.java` declared DE32 as fixed `an` width 11. The approved profile (`../../iso8583-profile.md` P1) declares DE32 as `LL n1–11`. Reading DE32 as fixed 11 consumed 2 extra bytes and shifted DE37/DE41/DE42, producing `PARSER_ALPHABET DE42 not uppercase AN`.
2. After correcting DE32 to `LL n1–11`, a second latent probe bug surfaced: `parsed.fields.get(48)` used an `Integer` key against a `Map<String,String>`, causing a `NullPointerException` only reachable for CAP-response vectors.

Both defects were in the disposable probe. Neither the profile document nor `vectors/packed-vectors.json` was modified. After correction:

```text
fixtures=85
ACCEPT parsed+decoded: 31/31
structural-layer negatives detected: 24/24
non-structural negatives deferred to business layers: 30
problems=0
```

Python cross-check agrees on every ACCEPT vector (31 parsed/decoded) and on all parser-class negatives.

### Remaining probe-scope limitation (not a profile contradiction)

The Python probe does not evaluate `ADMISSION_RESPONSE_CAPACITY` for ids named `CAP-over-request-*` (its capacity branch matches only the `CAP-request-` prefix), and the Java classifier treats some capacity cases as deferred. Capacity and freshness classification belong to admission/business layers (P4/P5), which **neither probe implements**. Treat both probe outputs as structural alignment evidence only.

## Explicit non-claims

- No production ISO codec exists or was selected by this evidence.
- No jPOS or third-party codec dependency was fetched or executed.
- No DynamoDB check has a captured PASS/FAIL result. The two script-defect claims previously recorded in `../../technical-preflight.md` §3 were **withdrawn** on 2026-11-19 follow-up re-inspection (conditional-insert semantics and token quoting); DynamoDB outcomes remain unverified.
- No JSON schema, JWT, HTTPS/WebSocket, ADOT, IAM or AWS service behavior was verified.
- Reproducing these files requires JDK 25 at `/home/pazfernando/.local/opt/jdk-25`; the probe is timing-independent and offline.

Re-execution evidence for the Java and Python probes lives in `../../technical-preflight-evidence/` (2026-11-19); its stdout hashes are identical to the preserved files here.
