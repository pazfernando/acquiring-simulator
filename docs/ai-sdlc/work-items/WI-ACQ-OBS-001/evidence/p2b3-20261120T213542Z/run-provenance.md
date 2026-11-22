# P2b3 run provenance

## What exists for the 2026-11-20T16:34:34 local run

| artifact | location | fact |
|---|---|---|
| Maven stdout | `/tmp/opencode/p2b3-run/stdout.txt` (copied to `test-stdout.txt`) | 2710 bytes, `BUILD SUCCESS`, finished `2026-11-20T16:34:34-05:00` |
| Maven stderr | `/tmp/opencode/p2b3-run/stderr.txt` (copied to `test-stderr.txt`) | 0 bytes |
| exit code | `/tmp/opencode/p2b3-run/exit.txt` (copied to `test-exit.txt`) | `0` |
| Surefire XML + summary | `target/surefire-reports/` | 4 class reports, all `tests/errors/skipped/failures/flakes` consistent with stdout |
| targeted property | Surefire XML `<property name="test">` | `C4LateDeliveryTest,C4EvidenceVerifierTest,C4CarrierParserTest,C4EvidenceAttestationSchemaTest` |

## Correspondence checks performed (recovery-time)

- stdout `Finished at: 2026-11-20T16:34:34-05:00` matches Surefire file mtimes
  `16:34:34.212` (LateDelivery txt) .. `16:34:34.504` (Verifier xml).
- Per-class counts and timings in stdout equal the Surefire summaries exactly
  (23 / 0.503 s, 6 / 0.031 s, 18 / 0.086 s, 33 / 0.173 s), aggregate 80.
- Command recorded in `test-command.txt`; stdout shows only those four classes ran.
- Compiled classes precede the run: `target/classes/...C4LateDelivery*.class` at
  `16:33:27`, `target/test-classes/...C4LateDeliveryTest.class` at `16:34:24`, Surefire
  at `16:34:34`; test source at `16:34:10`. No later source edit was observed.

## What is NOT attested

- There is **no attested pre-run hash** of the inputs. `hashes-current.sha256` was
  computed during this recovery, after the run. Byte equality between current sources
  and the bytes compiled for the run is **inferred** from mtimes and the absence of
  later edits, not proven by a pre-run manifest.
- The capture directory `/tmp/opencode/p2b3-run` is outside the repository and is not
  itself signed or hashed at production time. It is copied byte-for-byte here; the
  copies' SHA-256 values are recorded in `evidence.sha256`.
- This was a targeted run, not a full regression. No aggregate full-suite result is
  claimed.

## Consequence for the result label

`exit 0` and `BUILD SUCCESS` are supported by `test-stdout.txt` + `test-exit.txt` for
this specific invocation. They are **not** a claim of full-suite green, independent QA,
Security review or human acceptance.
