# C4CarrierParserTest result provenance

## What this evidence is

A read-only, sanitized capture of an **already-produced** targeted Maven/Surefire run.
This recovery did **not** rerun the test.

- Original run id: `2026-11-20T16-10-53_008-jvmRun1`
  (booter jar `surefirebooter-20261120161053125_3.jar`).
- Surefire artifacts: `target/surefire-reports/com.acquiring.simulator.channel.C4CarrierParserTest.txt`
  and `target/surefire-reports/TEST-com.acquiring.simulator.channel.C4CarrierParserTest.xml`.
- Surefire suite: `tests=18 errors=0 skipped=0 failures=0 flakes=0 time=0.377s`.
- `test` system property recorded in the XML = `C4CarrierParserTest`, so this was a
  targeted class run, not the full suite.
- JVM: Temurin `25.0.5+8-LTS`, `java.home=/home/pazfernando/.local/opt/jdk-25`,
  class file version 69.

## Provenance strength (honest limits)

- **No stdout/stderr and no exit-code file** exist for the 16:10 run. The intended
  capture script `/tmp/opencode/p2b1-evidence.sh` (mtime 16:11:26) was written but its
  output directory was never created, so it aborted before writing anything.
  Therefore "BUILD SUCCESS"/exit 0 is **not** independently captured.
- The pass result rests on Surefire's own completed-suite artifacts: the XML is only
  written after a completed run, its attributes say 18/0/0/0, and it contains no
  `<failure>`, `<error>` or `<skipped>` children (18 `<testcase>` elements).
  This is credible but **weaker** than stdout + exit code.
- No raw Maven transcript, HTTP header, response body, signature/proof material or
  secret is copied here. Only counts, case names, ids and category labels are retained.

## Current-bytes-to-run chronology

| time (local -05:00) | event |
|---|---|
| 16:08:07–16:09:40 | 10 `C4*` main sources last modified |
| 16:09:52.770 | `C4CarrierParser.class` compiled |
| 16:10:33.083 | `C4CarrierParserTest.java` last modified |
| 16:10:51.972 | `C4CarrierParserTest.class` compiled |
| 16:10:53.008 | Surefire JVM run starts |
| 16:10:54.129 | surefire `.txt` written |
| 16:10:54.156 | surefire `.xml` written |
| 21:13Z (recovery) | current bytes hashed, git status captured |

All run inputs predate the run start, and no `C4*` file was modified after the run.
**Limitation:** hashes in `hashes-current.sha256` were taken during recovery, not before
the run. Byte-for-byte equality with the run-time inputs is inferred from unchanged
mtimes and the absence of later edits, not cryptographically attested. There is no
pre-run hash to compare against.

## Scope boundary (not signature verification)

The parser performs strict outer-envelope shape checks, canonical base64 checks,
body-hash binding, canonical attestation hashing and DIRECT/RELAY structural sender
binding. It **does not** verify an Ed25519 signature, resolve a key, check P5 role/
mandate or authenticate transport. Its output is explicitly
`UNVERIFIED_CANDIDATE` with `KeyResolution.UNRESOLVED`; the `K07` case keeps the
unresolved `keyId` (`UNKNOWN`) rather than trusting it. Passing these 18 tests is
**not** authentication and **not** admission.
