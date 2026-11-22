# P2c1 compact manifest — pure C1 channel ingress validator and exact amount

Status: **RECOVERY / INTEGRITY FINALIZATION RECORD ONLY.** No test was rerun, no
artifact rewritten, no source or vector edited, and no research performed to
produce this manifest.

- Work item: WI-ACQ-OBS-001 (channel-ingress slice feeding the protocol work).
- Phase: P2c1 — pure C1 channel JSON ingress + typed request + exact amount
  conversion. Not admission, authentication, allocation, routing, ISO
  projection, claim, dispatch, history or a financial outcome.
- Run folder: `p2c1-20261120T204141Z`.
- Delivered files (6): 5 production classes + 1 test class.
- Repo: `/home/pazfernando/projects/acquiring-simulator`.
- Branch: `feat/wi-acq-auth-001`.
- HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged; no Git write).
- Origin: `https://github.com/pazfernando/acquiring-simulator.git`.
- Model: provider `nan`, model `deepseek-v4-flash`; `model_override=none`.
- Binding captured: `2026-11-20T20:41:41Z` (`BINDING.txt`).
- Toolchain recorded: Temurin OpenJDK 25.0.5 (`toolchain-version.txt`; the file
  also shows the environment's `JAVA_HOME` warning line, preserved verbatim).

## Why this file exists

The run folder was saved but carried no compact manifest or progress record in
its own folder. This manifest adds provenance and an integrity inventory for the
already-completed run. It does not certify a new execution.

## Delivered surface (exact)

| File | Role |
| --- | --- |
| `channel/ChannelRequestValidator.java` | Pure C1 ingress: size → strict ASCII/UTF-8 → JSON structure (duplicate/unknown) → required keys → declared fields → typed request |
| `channel/ChannelAuthorizationRequest.java` | Immutable typed record; amount carried as exact `long` minor units, `amount()` is the exact 2-decimal projection |
| `channel/ChannelAmount.java` | Exact string ⇄ minor-unit conversion; no float, no rounding mode |
| `channel/ChannelErrorCode.java` | Only the two pre-transport codes `INVALID_INPUT`, `BODY_TOO_LARGE` |
| `channel/ChannelRequestValidationException.java` | First-failure label + safe code; message built only from fixed code/rule/declared field |
| `test/.../ChannelRequestValidatorTest.java` | 12 methods (1 parameterized over 8 amount vectors) = 20 executed tests |

## Actual results already recorded in the saved artifacts

| Artifact | Observed |
| --- | --- |
| `targeted-stdout.txt` / `targeted-exit.txt` | `Tests run: 20, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS`; exit `0` |
| `targeted-stderr.txt` | empty (`e3b0c442…b855`) |
| `targeted-stdout-attempt1.txt` / `targeted-exit-attempt1.txt` | `Tests run: 20, Failures: 1`; exit `1` (the preserved first attempt) |
| `test-compile-stdout.txt` / `test-compile-stderr.txt` | both empty (0 bytes), as saved |
| `delivered-files.sha256` | 6 files; re-checked against the working tree now, all `OK` |

**No full-suite claim.** This run executed only the targeted
`ChannelRequestValidatorTest` class (20 tests). No aggregate such as "523 + 20"
or any other whole-project total is asserted here.

## Chronology (local times, America/New_York, from saved artifact mtimes)

| Time | Artifact / event |
| --- | --- |
| 15:41:41 | `BINDING.txt`, toolchain, `git-status-before.txt`, `hashes-before.sha256` (194 inputs; the 6 channel files absent) |
| 15:44:30 | `test-compile-stdout/stderr` (empty) |
| 15:44:43 | `hashes-frozen-attempt1.sha256` (200 inputs), `delivered-files-attempt1.sha256`, `git-status-attempt1.txt`, `head-frozen-pre-run.txt` |
| 15:44:47–51 | attempt 1 target run: `FAILURE`, `Tests run: 20, Failures: 1`, exit `1` |
| 15:45:08 | test-only fix applied; `hashes-frozen-pre-run.sha256` (200), `delivered-files.sha256`, `git-status-frozen-pre-run.txt`, `targeted-stderr.txt` |
| 15:45:16 | final target run: `Tests run: 20, Failures: 0`, exit `0` |

Chronology is consistent: before → edit → frozen attempt1 → failing run → test
fix → frozen pre-run → green run. Attempt 1 is preserved, not overwritten.

## Integrity — recomputed now

- `hashes-before.sha256`: 194 inputs (channel slice not yet present).
- `hashes-frozen-attempt1.sha256`: 200 inputs (6 additions).
- `hashes-frozen-pre-run.sha256`: 200 inputs (6 additions).
- Recompute against the current tree:
  `sha256sum -c hashes-frozen-pre-run.sha256` → **200 / 200 `OK`**, no missing
  path, no changed path.
- `hashes-frozen-attempt1` vs `hashes-frozen-pre-run`: **exactly one** line
  differs — `ChannelRequestValidatorTest.java`
  (`0995470a…` → `7386ee2c…`). The five production sources are byte-identical
  across both attempts.
- `delivered-files.sha256`: **6 / 6 `OK`** against the current tree.
- `head-frozen-pre-run.txt` = current `git rev-parse HEAD` =
  `cfc456f4f469d9558a78fda8ff9b770b00817315`.
- `git-status-attempt1.txt` and `git-status-frozen-pre-run.txt` are byte-identical
  (the 6 files are untracked additions, so `git status` does not name them).

**Snapshot question answered.** The green 20/20 run covers the exact current
snapshot: the frozen-pre-run manifest (taken 15:45:08, just before the 15:45:16
run) re-verifies 200/200 against the tree now, so the inputs exercised are the
inputs present now. The failing attempt-1 snapshot differed only in the test
class.

## Failure/fix diagnostic — test-oracle defect, not a code defect

- Attempt 1 failure:
  `ChannelRequestValidatorTest.rejectsReferenceAlphabetBoundsAndNonAsciiValues:184 → failureOf:274`,
  `expected: "paymentRef" but was: null`.
- Line 184 in the fixed file is the raw-non-ASCII assertion
  (`"PAY-001\u00e9"` as a real `é` character in the body), now asserted
  **without** a field argument. In attempt 1 the same assertion passed
  `"paymentRef"` to the field-checking helper.
- By design, `ChannelRequestValidator.decodeAscii` rejects any raw non-ASCII
  byte (and any decoded char `> 0x7F`) as `NON_ASCII_BODY` with `field = null`,
  because naming a field would require decoding caller bytes before the field
  is known. The product behaviour is correct.
- The companion assertion that stays ASCII on the wire (`\\u00e9` escape) does
  decode to a non-approved reference character and is correctly
  `INVALID_REFERENCE` with field `paymentRef`.
- The fix changed **only the test** (drop the field argument on the body-level
  case). The five production classes are byte-identical between attempt 1 and
  the final run, so this was a test-expectation defect, not a product code
  defect.
- Limit: the attempt-1 test source was not preserved; this conclusion rests on
  the saved failure text, the line-184 location and the byte-identical
  production files.

## Behaviour recorded (what the code actually does)

**C1 closed JSON.** Max body 4096 bytes checked before parsing; strict UTF-8
then pure-ASCII; exactly one root object; trailing content rejected; unknown key
→ `UNKNOWN_FIELD` (key never echoed); duplicate key → `DUPLICATE_FIELD` with the
declared field name; missing → `MISSING_FIELD`; `null` → `NULL_FIELD`; wrong
type → `WRONG_TYPE` (`amount` → `CHANNEL_TYPE_AMOUNT`); no string→boolean and no
number→string coercion; approved literals only (`contractVersion "1"`,
`operation "A"`, `currency "USD"`, two `entryMode`, two `cvm`, optional
`risk` `pass`/`reject` defaulting to `PASS`); reference alphabet
`[A-Za-z0-9._:-]` with bounds 64 (`correlationId`, `credentialFixtureRef`,
`priorAuthorizationId`) and 128 (acquirer/merchant/terminal/payment); first
failure reported once.

**No leaked payload.** The exception message is assembled only from the fixed
error code, the fixed rule label and, for declared fields, the local field name.
Tests assert the message does not contain caller values (`APPROVED`, `DOMESTIC`,
`ISSUER`, a PAN, `decision/route/actor/generation/pan`) and does not contain the
rejected amount text.

**Exact amount, no rounding.** `ChannelAmount.toMinorUnits` accepts only
`[0-9]{1,4}\.[0-9]{2}` (leading zero disallowed when more than one integral
digit, so `00.99`/`01.00` are schema-invalid), parses the two explicit digits
with `long` arithmetic as `integral*100 + cents`, and applies the approved floor
`1.00` (`100`) and structural ceiling `9999.99` (`999999`). No `double`/`float`,
no `BigDecimal` rounding mode, no truncation, padding, normalization or locale
parsing. `toAmount` returns `BigDecimal.valueOf(minor, 2)` (exact, scale 2).
Below-floor is `DOMAIN_AMOUNT` (existing HD-01 rule), not a new channel rule.

**Eight amount oracle cases (all executed from the real JSON bodies).**
`J1` `1.00` ACCEPT, `J2` `9999.99` ACCEPT, `J3` `10.01` ACCEPT (each also checks
exact minor units, scale-2 plain string, the 12-digit DE4 projection and the
`noRounding` flag); `J4` `0.99` `DOMAIN_AMOUNT`; `J5` `10000.00`, `J6` `1.001`,
`J7` `01.00` → `CHANNEL_SCHEMA_AMOUNT`; `J8` `1.0` as a JSON number →
`CHANNEL_TYPE_AMOUNT`.

**Dependency provenance.** JSON parsing uses Jackson `jackson-databind 2.20.1`
(and `jackson-datatype-jsr310 2.20.1`), already declared in `pom.xml` at the
base HEAD and unchanged: `pom.xml` SHA-256 is identical before and after
(`b47fb547ab28628b78e4241986638b9379cc4263e1762218192264bb1bc7a93c`), and the
same two dependencies are present in `git show HEAD:pom.xml`. No new third-party
dependency was introduced for this slice.

## Record notes (not corrected — no spec/vector edit)

- `vectors/channel-vectors.json` still declares `"status": "PROPOSED not
  executed"`, yet the test reads and executes all eight cases. This metadata
  mismatch is recorded here, not edited.
- `test-compile-stdout.txt` and `test-compile-stderr.txt` are both empty; the
  compile outcome is therefore evidenced only by the subsequent successful
  `testCompile` phase inside `targeted-stdout.txt`.
- Attempt 1 is preserved in full; the final artifact set does not overwrite it.

## Integrity inventory of this folder (SHA-256, as found)

```
bfeb0f09e1d8283ced8e53104394d5471142aa36a1e7a9df4776c0a176a804d9  270     BINDING.txt
e96e9a4f836fed1ccdb7125579238dfbfff5f1c9572ad313e14485973eb8b7c6  847     delivered-files-attempt1.sha256
ba6e81d048755e8e939c20822ee1f2fe3cdc25a94743e025a49ab3fe4ea7cbba  847     delivered-files.sha256
0e7370c62ffdf9686c2d02f26850cdea238c43fc66d2a1103be97c22b77d446c  6382    git-status-attempt1.txt
fcbf8d0b0aa892dbf67ecef54956c1337af07a6235f913b8a342c464b14c7e8c  6282    git-status-before.txt
0e7370c62ffdf9686c2d02f26850cdea238c43fc66d2a1103be97c22b77d446c  6382    git-status-frozen-pre-run.txt
7e14f4eaa7c51cb4a2526fc375bf861dba3be19e47506e867eeb10c3382d40cf  27723   hashes-before.sha256
1375e2eae47735dd04b19aa6ef3d536eedba28a95ad44c128f82f92a7dd8e5c0  28570   hashes-frozen-attempt1.sha256
1d22b33b218855f4e006aa9ba47a412f97820f45e8e569c21f4e1e14c4ad4554  28570   hashes-frozen-pre-run.sha256
1244ab5a8bb9a777450128221325968c13c9045f94b8fcf9bb40229a62f81ec7  41      head-frozen-pre-run.txt
4355a46b19d348dc2f57c046f8ef63d4538ebb936000f3c9ee954a27460dd865  2       targeted-exit-attempt1.txt
9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa  2       targeted-exit.txt
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  0       targeted-stderr-attempt1.txt
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  0       targeted-stderr.txt
ec86176160189ee82d514173da487fa9f16d4400d143cd2c5b5c8703fe59bdf7  3533    targeted-stdout-attempt1.txt
8b3bc274b5f03bc0a42b4b0c693d0fa49e3023a39e3b82825c4fb3aef9c38e87  2679    targeted-stdout.txt
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  0       test-compile-stderr.txt
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  0       test-compile-stdout.txt
1d83fe606d17d1fd7671f098535cc0fb2430aa20a343738fbe8cdc5199c17504  308     toolchain-version.txt
```

This manifest and `PROGRESS.md` are intentionally **not** listed in their own
inventory.

## Boundary

Local JUnit evidence only. P2c1 is **not** admission, authentication,
authorization, identity allocation/aliasing, registry/fixture/mandate/route
resolution, ISO construction/projection, duplicate/conflict resolution, claim,
dispatch, history, C4 carriage, HTTP transport or runtime wiring. No full-suite
total is claimed. P1 and G6 remain open. No Git write, publication, cloud call,
credential flow, deletion or specification/vector edit was performed by this
record.
