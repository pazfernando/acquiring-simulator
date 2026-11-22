# P2a8 compact manifest — one ordered request-validation pipeline

Status: **RECOVERED RECORD ONLY. No test was rerun, no artifact recreated or
rewritten, and no source edited to produce this manifest.**

- Work item: WI-ACQ-OBS-001 (protocol slice).
- Phase: P2a8 — one pure ordered `0100` request pipeline (scanner → canonical
  DE48 request schema → domain → freshness → 826-byte budget). Not admission,
  authentication, attestation or a financial outcome.
- Run folder: `p2a8-20261120T203052Z`.
- Repo: `/home/pazfernando/projects/acquiring-simulator`.
- Branch: `feat/wi-acq-auth-001`.
- HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged; no Git write).
- Origin: `https://github.com/pazfernando/acquiring-simulator.git`.
- Model: provider `nan`, model `deepseek-v4-flash`; `model_override=none`.
- Toolchain recorded: Temurin JDK 25.0.5, Apache Maven 3.10.1
  (`toolchain-version.txt`).

## Why this file exists

`PROGRESS.md` was saved, but this folder had no `EVIDENCE-MANIFEST.md`. This
manifest is the compact recovery record for the already-completed run. It adds
provenance and an integrity inventory; it does not certify a new execution.

## Actual results already recorded in the saved artifacts

| Artifact | Observed |
| --- | --- |
| `TEST-...Iso8583RequestValidationPipelineTest.xml` | `tests="53"`, failures 0, errors 0, skipped 0 |
| `protocol-regression-stdout.txt` | `Tests run: 472, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS`; `protocol-regression-exit.txt` = exit `0` |
| `protocol-regression-stderr.txt` | empty (`e3b0c442…b855`) |
| `request-first-failure-mapping.tsv` | 43 fixture rows + header; all `match=true` |
| `delivered-files.sha256` | 4 files; re-checked against the working tree now, all `OK` |
| `hashes-frozen-vs-after.diff` | empty; exit `0` |
| `hashes-p2a7-vs-p2a8.diff` | only the four added files (exit `1`, additions only) |
| `head-after.txt` / `head-final.txt` | the binding HEAD above |

The 472/472 protocol total is the saved P2a7 419 plus 53 new. The pipeline class
itself is 53/53 inside that same run. `PROGRESS.md` states 43 request fixtures =
9 ACCEPT + 34 ordered negatives; that is consistent with the mapping file.

## Integrity inventory (SHA-256, as found)

```
d302a5207e99e5f59f3a872a5a9a18a856c6f990c778dce32936607da308fc9c  236     BINDING.txt
3af2f92007a01a006d3edfa279b6f3eff3fea30798025614d961250d466daf3a  633     delivered-files.sha256
fcbf8d0b0aa892dbf67ecef54956c1337af07a6235f913b8a342c464b14c7e8c  6282    git-status-after.txt
fcbf8d0b0aa892dbf67ecef54956c1337af07a6235f913b8a342c464b14c7e8c  6282    git-status-before.txt
fcbf8d0b0aa892dbf67ecef54956c1337af07a6235f913b8a342c464b14c7e8c  6282    git-status-final.txt
fcbf8d0b0aa892dbf67ecef54956c1337af07a6235f913b8a342c464b14c7e8c  6282    git-status-frozen-pre-run.txt
75de4d69cb2218938fc255953b0b9eef1eb174fb7df143b088cdfd7aa39b4867  26827   hashes-after.sha256
75de4d69cb2218938fc255953b0b9eef1eb174fb7df143b088cdfd7aa39b4867  26827   hashes-frozen-pre-run.sha256
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  0       hashes-frozen-vs-after.diff
9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa  2       hashes-frozen-vs-after-exit.txt
a65199fe1507b2243c1d781a1a74f828ae324df3fd99578fab5a2883cb3165a3  669     hashes-p2a7-vs-p2a8.diff
4355a46b19d348dc2f57c046f8ef63d4538ebb936000f3c9ee954a27460dd865  2       hashes-p2a7-vs-p2a8-exit.txt
1244ab5a8bb9a777450128221325968c13c9045f94b8fcf9bb40229a62f81ec7  41      head-after.txt
1244ab5a8bb9a777450128221325968c13c9045f94b8fcf9bb40229a62f81ec7  41      head-final.txt
790b2676690be064696cb1b16a8b12473d2e16086fabf314a167efa9ba221187  5447    PROGRESS.md
9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa  2       protocol-regression-exit.txt
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  0       protocol-regression-stderr.txt
f2edcc8a989a45aff705502956bc70678c3db5cf1ef404fe1875350e57aaea20  5220    protocol-regression-stdout.txt
a84fa43fed67c8f66bd713ec9c11632adc7109c0e0c55d0972592388e0c7dbb5  2237    request-first-failure-mapping.tsv
2512091311380de082f9e744b70455f839b51e959c708f0afb4c4ca689951c5d  41289   TEST-com.acquiring.simulator.protocol.iso8583.Iso8583RequestValidationPipelineTest.xml
3438c2c34b7e0c93bb420e292ae9ec0fc809808b108fd929309f47f195af7360  393     toolchain-version.txt
```

This manifest file is intentionally **not** listed in its own inventory.

## Record corrections (no artifact altered)

- `PROGRESS.md` cites `ATTEMPTS.md` for the initial pre-final attempts. That
  file is **absent** from the saved folder. The attempts narrative in
  `PROGRESS.md` is therefore the only surviving record; it is not expanded here.
- `PROGRESS.md` also cites `EVIDENCE-MANIFEST.md`, which did not exist before
  this recovery record.
- No `git status` output records the four delivered files as modified because
  they were untracked additions; that is consistent with the saved status files.

## Boundary

Local JUnit and byte-fixture evidence only. P2a8 is **not** admission,
authorization, authentication, attestation, registry/mandate/route resolution,
duplicate/conflict handling, claim, dispatch, response construction, C4
carriage, HTTP/runtime behaviour, independent QA, Security review or human
acceptance. P1 and G6 remain open. No Git write, publication, cloud call,
credential flow, deletion or specification/vector edit was performed by this
recovery record.
