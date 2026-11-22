# P2c1 progress — pure C1 channel ingress + exact amount (recorded 2026-11-20)

**Status: the P2c1 run was already completed and saved; this entry records it.
No test was rerun, no artifact recreated, and no source or vector edited.**

Evidence: `evidence/p2c1-20261120T204141Z/` (`EVIDENCE-MANIFEST.md`,
`BINDING.txt`, `delivered-files*.sha256`, `hashes-*`, `git-status-*`,
`targeted-*`, `test-compile-*`, `toolchain-version.txt`).

## Binding

Branch `feat/wi-acq-auth-001`, HEAD
`cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged), origin
`github.com/pazfernando/acquiring-simulator.git`, model `nan/deepseek-v4-flash`,
`model_override=none`, captured `2026-11-20T20:41:41Z`. Temurin JDK 25.0.5.
Runtime inactive: no Git write, publication, cloud call, credential flow,
deletion or specification/vector edit.

## Delivered (already saved)

One pure C1 channel ingress slice: `ChannelRequestValidator`,
`ChannelAuthorizationRequest`, `ChannelAmount`, `ChannelErrorCode`,
`ChannelRequestValidationException`, and `ChannelRequestValidatorTest`.

The validator runs, in one fixed order: 4096-byte body bound → strict UTF-8/pure
ASCII → single JSON object with unknown/duplicate-key rejection → required keys
→ declared fields (literals, reference alphabet/bounds, amount, entry mode, CVM,
boolean) → a typed `ChannelAuthorizationRequest`. First failure is reported once
as a safe `ChannelErrorCode` + stable rule label + declared field; caller values,
the raw payload and the rejected amount are never echoed.

Amount conversion is exact: `[0-9]{1,4}\.[0-9]{2}` parsed with `long`
(`integral*100 + cents`), floor `1.00`, ceiling `9999.99`; no float, no rounding
mode, no truncation/padding/normalization/locale. `toAmount` returns exact
scale-2 `BigDecimal`. The channel reads are backed by the pre-existing
`jackson-databind 2.20.1` already declared at HEAD; `pom.xml` is byte-identical
before and after, so no new dependency was introduced.

## Recorded results

- Attempt 1 (preserved): `Tests run: 20, Failures: 1`, exit `1` — failure at
  `rejectsReferenceAlphabetBoundsAndNonAsciiValues:184` (`expected "paymentRef"
  but was null`).
- Final: `ChannelRequestValidatorTest` **20/20**, exit `0`, stderr empty.
  Twelve methods, one parameterized over the eight approved amount vectors
  `J1`–`J8`; the three `ACCEPT` cases also check exact minor units, scale-2
  plain string, the 12-digit DE4 projection and `noRounding=true`.
- No full-suite total is claimed; only this targeted class was run.

## Failure/fix diagnostic

The attempt-1 failure was a **test-oracle defect, not a product code defect**.
The failing line asserted field `paymentRef` for a body carrying a **raw**
non-ASCII byte. `decodeAscii` correctly rejects that as `NON_ASCII_BODY` with
`field = null` (naming a field would require decoding caller bytes). The fix
dropped the field argument on that one assertion; the companion ASCII `\\u00e9`
escape case still correctly yields `INVALID_REFERENCE` with `paymentRef`. The five
production classes are byte-identical between attempt 1 and the green run — the
only inter-attempt diff is the test file. The attempt-1 test source is not
preserved, so this rests on the saved failure text, the line-184 location and the
identical production bytes.

## Integrity (recomputed after the run, no rerun)

`sha256sum -c hashes-frozen-pre-run.sha256` → **200 / 200 `OK`** against the
current tree; `delivered-files.sha256` → **6 / 6 `OK`**; `HEAD` unchanged. The
frozen-pre-run snapshot (15:45:08) is therefore the exact snapshot the green
20/20 run (15:45:16) exercised. `hashes-before` has 194 inputs; both frozen
snapshots have 200 (6 additions). Attempt 1 is preserved and not overwritten.

Record note: `channel-vectors.json` still says `"status": "PROPOSED not
executed"` even though the test executes all eight cases; recorded, not edited.

## Not covered / next bounded slice

Registry/fixture/mandate/route resolution, authentication and attestation,
identity allocation/aliasing, P4, instruction/DE38 binding, duplicate/conflict
resolution, claim, dispatch, response construction, C4 carriage, HTTP transport,
production caller and runtime wiring remain deferred.

Next: **ONE bounded, server-resolved instruction mapper** — map an already
validated typed request to the approved instruction fields using server-side
resolution only, with **no real identifier allocation, no authentication and no
client-supplied override**. It must not add HTTP, claim, dispatch or runtime
wiring. C4, HTTP and runtime stay outstanding. P1 and G6 remain open. Local
evidence only; not QA, Security review or human acceptance.
