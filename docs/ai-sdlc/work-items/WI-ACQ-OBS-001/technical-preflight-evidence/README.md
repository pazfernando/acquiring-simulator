# Technical preflight — re-execution evidence — WI-ACQ-OBS-001 — 2026-11-19

Status: bounded re-execution of the **existing** ISO probes plus a local shell-quoting check. Captured stdout/stderr/exit codes were written to this directory **before** analysis. These files are diagnostic evidence, **not product code**, **not** a codec implementation, and **not** QA, Security or scheme-conformance evidence.

Authority: `../owner-decisions.md`, "Architecture approval and technical-preflight commission" (2026-11-19), as extended by the coordinator instruction for this turn. The turn was restricted to local/offline execution: no AWS calls, no DynamoDB runs, no production changes, no Git writes, no deployment or publication.

## Binding observed

| Field | Value |
| --- | --- |
| Repository | `/home/pazfernando/projects/acquiring-simulator` |
| Branch | `feat/wi-acq-auth-001` |
| HEAD | `cfc456f4f469d9558a78fda8ff9b770b00817315` |
| Model | provider `nan`, `deepseek-v4-flash` — configured default, no override |
| JDK | `/home/pazfernando/.local/opt/jdk-25` (`javac 25.0.5`, `java 25.0.5`, Temurin) |
| Python | see `python-version.txt` |
| Run time | `2026-11-19T22:02:16Z` (`run-metadata.txt`, America/Bogota 17:02) |

The probe sources and fixtures were read from `../evidence/preflight-2026-11-19/` and were not modified. Their hashes at run time:

- `IsoProbe.java` `d782b052dd937a29e767cc3562f959669ae3436101b3431d1e19090d026d6615`
- `packed-fixtures.tsv` `88528ecadbf6c41676ea211cd8dbfe07e170555d64ba7340174678752ac7cc3d`
- `iso_probe.py` `a895a6a3fd42c921b0d9d3d917e7e7a517c462e8251f03ca24bf5a29ca16eaf5`

## Exact commands

```bash
cd docs/ai-sdlc/work-items/WI-ACQ-OBS-001/technical-preflight-evidence
SRC=../evidence/preflight-2026-11-19
CLS=$(mktemp -d /tmp/opencode/javaprobe-rerun-XXXXXX)

/home/pazfernando/.local/opt/jdk-25/bin/javac -version
/home/pazfernando/.local/opt/jdk-25/bin/java  -version
sha256sum $SRC/IsoProbe.java $SRC/packed-fixtures.tsv $SRC/iso_probe.py

/home/pazfernando/.local/opt/jdk-25/bin/javac -d "$CLS" $SRC/IsoProbe.java \
  > java-compile-stdout.txt 2> java-compile-stderr.txt; echo $? > java-compile-exit.txt
/home/pazfernando/.local/opt/jdk-25/bin/java -cp "$CLS" IsoProbe $SRC/packed-fixtures.tsv \
  > java-rerun-stdout.txt 2> java-rerun-stderr.txt; echo $? > java-rerun-exit.txt

python3 $SRC/iso_probe.py $SRC/packed-fixtures.tsv \
  > python-rerun-stdout.txt 2> python-rerun-stderr.txt; echo $? > python-rerun-exit.txt

bash /tmp/opencode/token-quoting-check.sh > token-quoting-check.txt
```

`token-quoting-check.sh` reproduces the quoting of `ddb-probe.sh` lines 27 and 65:

```bash
#!/usr/bin/env bash
set -u
T="acq-preflight-1795124996-2796042"
V=tok-preflight-'$T';  printf 'p4_2_token=%s len=%d\n' "$V" "${#V}"
V2=tok-dup-'$T';       printf 'p4_4_token=%s len=%d\n' "$V2" "${#V2}"
```

## Results

| File | SHA-256 | Content / result |
| --- | --- | --- |
| `java-version.txt` | `2bb6c29e…aa360399` | `javac 25.0.5`; `openjdk 25.0.5 2026-10-20 LTS` |
| `java-compile-exit.txt` | `9a271f2a…ab86aa` | `0` |
| `java-compile-stdout.txt` | empty | — |
| `java-compile-stderr.txt` | empty | — |
| `java-rerun-exit.txt` | `9a271f2a…ab86aa` | `0` |
| `java-rerun-stdout.txt` | `4cdcc829…e9da3df` | 5 lines, identical to `../evidence/preflight-2026-11-19/java-probe-stdout.txt` |
| `java-rerun-stderr.txt` | empty | — |
| `python-version.txt` | `5b3e43dc…acc366` | `Python 3.12.3` |
| `python-rerun-exit.txt` | `9a271f2a…ab86aa` | `0` |
| `python-rerun-stdout.txt` | `e5172023…508056a` | identical to `../evidence/preflight-2026-11-19/python-probe-stdout.txt` |
| `python-rerun-stderr.txt` | empty | — |
| `input-sha256.txt` | `4dd7528b…260188` | hashes of the three inputs used |
| `run-metadata.txt` | `8100faa6…1c71c3` | UTC time, cwd, branch, HEAD, temp classpath |
| `token-quoting-check.txt` | `9c649ba2…beabb1` | `p4_2_token=tok-preflight-$T len=16`; `p4_4_token=tok-dup-$T len=10` |

Java summary (verbatim):

```text
fixtures=85
ACCEPT parsed+decoded: 31/31
structural-layer negatives detected: 24/24
non-structural negatives deferred to business layers: 30
problems=0
```

**No DE42 mismatch occurred.** DE42 is declared fixed `an` width 15 in both probes, and both accepted all 31 ACCEPT vectors. The preserved outputs were reproduced byte-for-byte, so the earlier DE42 resolution is reproducible from the current probe source; the pre-correction source was not preserved, so its historical root cause cannot be recomputed here.

## Known limits (do not read as PASS)

- Python is not a strict mirror of the Java probe: DE32 is typed `an` in Python and `n` in Java, and the canonical charset check uses `isalnum`; with these fixtures both produce the same summary. Agreement is output-level, not a proof of equivalence.
- Python's per-vector list prints nine `FAIL` labels for `ADMISSION_*` cases not implemented by either probe (`B-DE48-exact-999`, `CAP-over-request-1..7`, `D-stale`); its own summary excludes them as deferred and reports `problems=0`. This is a display inconsistency, not a profile contradiction.
- Structural parsing of fixtures is not a production codec, not scheme conformance, and does not cover capacity, freshness or admission behavior.
- No DynamoDB command was executed in this turn.
