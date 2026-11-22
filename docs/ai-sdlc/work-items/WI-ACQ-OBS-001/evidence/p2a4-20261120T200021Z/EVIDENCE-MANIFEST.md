# P2a4 evidence manifest — bounded pure domain validator

- Work item: WI-ACQ-OBS-001 (protocol slice).
- Phase: P2a4 — one pure domain validator for amount/currency/profile code plus
  strict Gregorian `sent`/DE7, with byte-oracle tests. Not admission.
- Date: 2026-11-20T20:00:21Z (start); evidence folder `p2a4-20261120T200021Z`.
- Repo: `/home/pazfernando/projects/acquiring-simulator`.
- Branch: `feat/wi-acq-auth-001`.
- HEAD: `cfc456f4f469d9558a78fda8ff9b770b00817315` (unchanged; no Git write).
- Origin: `https://github.com/pazfernando/acquiring-simulator.git`.
- Model: provider `nan`, model `deepseek-v4-flash`; no override.
- Toolchain: JDK 25.0.5 `/home/pazfernando/.local/opt/jdk-25`; Maven 3.10.1
  `/home/pazfernando/.local/opt/maven/bin/mvn`.
- Runtime: inactive. No service start, AWS call, HTTP, storage change,
  credential flow, publication or deletion.

## Approved inputs preserved

| Input | SHA-256 |
| --- | --- |
| `iso8583-profile.md` (approved) | `de02c533536dcb6e87e4c6e93357efb9efa60c085a59f6f63bbfbb8c0e1fe481` |
| `vectors/packed-vectors.json` (oracle) | `800bc07148cf21d2c6b2f5711a5ca97d7119c32855b7d7826088dddcf47c7ff0` |
| `vectors/evidence-fixtures.json` | `800bb86177abb93b4445bee7e127b8caa14b20525a1a4f51a9df8b45774783c3` |
| `requirements.md` | `49c5d412e3705c320a2f78b190555f49e7e0fa4662cff9329d259f89e2b459df` |
| `owner-decisions.md` | `5450836cabbf73bd27ba32d862154dc5caa0c5b4ddfc9209a7033a0eff43cb5b` |

The packed-vector oracle was rechecked against `vectors/review-manifest.json`
(`files[16]`): SHA-256 matches. No specification, vector, profile or fixture was
edited.

## Delivered scope

One pure validator:

| Rule (first failure order) | Component | Approved value |
| --- | --- | --- |
| `PROFILE_CODE_UNSUPPORTED` | `de3` | exactly `000000` |
| `PROFILE_CODE_UNSUPPORTED` | `de22` | `051` or `071` |
| `PROFILE_CODE_UNSUPPORTED` | `de25` | exactly `00` |
| `AMOUNT_OUT_OF_RANGE` | `de4` | `100..999999` minor units inclusive |
| `CURRENCY_UNSUPPORTED` | `de49` | exactly `840`; every other currency rejects |
| `INVALID_GREGORIAN_DATE` | `sent` | strict Gregorian `YYYYMMDDhhmmss`, leap days, no year zero |
| `DE7_MISMATCH` | `de7` | exactly `sent` positions 5–14 (`MMDDhhmmss`) |

- No rounding, truncation, padding or date/time normalization. Month/day/hour/
  minute/second bounds are deterministic; `1900-02-29` rejects, `2000-02-29`
  and `2028-02-29` accept. `0000` as a Gregorian year rejects rather than being
  shifted.
- Response `DE7` is compared with the echoed original request `sent`, never with
  the response `at` availability time. `at` is not validated beyond the schema's
  14-digit form because no approved domain rule mandates more; it is never
  reused as `sent`.
- The structural scanner and canonical DE48 schema remain upstream. The domain
  phase runs before instruction binding, so amount/currency/date/DE7 failures
  win over later echo/DE38 mismatch. The equality binding validator is
  unchanged and remains a separate downstream layer.
- No registry/fixture/route/mandate resolution, freshness, 826 admission,
  response packing, C4 carriage, HTTP, runtime or financial admission.

## Files changed

Application (new):

| File | SHA-256 |
| --- | --- |
| `Iso8583DomainValidator.java` | `472d189cc0d857c58fc0a4f76f64d49920722575546e92985d253e8000c737bc` |
| `Iso8583DomainException.java` | `405da1b627ddef40f8823a5fe9d6e2cbcf018d49b14f1eaca9a0237d8e9ca8da` |
| `Iso8583DomainDecision.java` | `30fc358948b824d932ee67acf57688c9875f125ec79fa2e65a3cf5ac737083df` |

Tests (new):

| File | SHA-256 |
| --- | --- |
| `Iso8583DomainValidatorTest.java` | `3dbbae45b6fc9a4b92adfba1c9d8423d726b1c9173f082d57656fa8d7ab95e48` |
| `Iso8583DomainCorpusTest.java` | `b9bc5d99cf995ae7a8b804d37536f8a1c102dc764eff6e56be0dffe1c868b0fa` |

No `pom.xml`, resource, vector, oracle, profile or specification file was
edited. `hashes-before.sha256` (178 inputs) and `hashes-after.sha256` (183
inputs) differ only by these five added files (`hashes-diff.txt`,
`HASH_DIFF_EXIT=1` for the additions); every pre-existing input is byte-identical.

## Byte-corpus oracle

`Iso8583DomainCorpusTest` rebuilds each body from its authoritative `hex`
octets and runs scanner → canonical DE48 schema → domain profile → (responses)
P4. Binding is downstream and is not reached once the domain fails.

| Fixture | Label | Rule | Component |
| --- | --- | --- | --- |
| `B-below-min` | `DOMAIN_AMOUNT` | `AMOUNT_OUT_OF_RANGE` | `de4` |
| `B-over-max` | `DOMAIN_AMOUNT` | `AMOUNT_OUT_OF_RANGE` | `de4` |
| `B-non-usd` | `DOMAIN_CURRENCY` | `CURRENCY_UNSUPPORTED` | `de49` |
| `D-leap-invalid` | `DOMAIN_DATE` | `INVALID_GREGORIAN_DATE` | `sent` |
| `D-bad-month` | `DOMAIN_DATE` | `INVALID_GREGORIAN_DATE` | `sent` |
| `D-DE7-mismatch` | `DOMAIN_DE7` | `DE7_MISMATCH` | `de7` |
| `N-response-DE7` | `DOMAIN_DE7` | `DE7_MISMATCH` | `de7` |
| `N-response-invalid-date` | `DOMAIN_DATE` | `INVALID_GREGORIAN_DATE` | `sent` |

Positive byte cases: `B-min-amount`, `B-max-amount` (inclusive boundaries),
`D-leap-valid`, `Y-2026`, `Y-2027` (leap/year change) and
`V02-approved-response` (its `at` is later than `sent` and is not used for DE7).

`N-response-DE7` is the key oracle: `sent=20261118120000`, response `DE7` and
`at` are both `...120001`; the response is rejected at DE7 because DE7 must
follow the original `sent`, not `at`.

## Commands and actual results

| Attempt | Command | Exit | Result |
| --- | --- | --- | --- |
| Compile 1 | `mvn -o -DskipTests test-compile` | 0 | `BUILD SUCCESS`. Logs `compile-stdout.txt`, `compile-stderr.txt` (empty), `compile-exit.txt`. |
| Targeted 1 | domain test pair | 1 | 1 error: a test helper passed the invalid value in the `sent` slot instead of the `at` slot. Logs `targeted-*-failure-1.txt`; preserved. Production code unchanged. |
| Targeted 2 | same pair | 0 | `Tests run: 23, Failures: 0, Errors: 0, Skipped: 0` (14 corpus + 9 unit). Logs `targeted-stdout.txt`, `targeted-stderr.txt` (empty), `targeted-exit.txt`. |
| Protocol regression | `mvn -o -Dtest='Iso8583*Test' -DfailIfNoTests=false test` | 0 | `Tests run: 354, Failures: 0, Errors: 0, Skipped: 0` (`354 = 331 prior + 23 new`). Logs `protocol-regression-*`; stderr empty. Run once after the edits; no full suite. |

`java-version.txt`, `mvn-version.txt`, `head-after.txt` and `git-status-final.txt`
(`--untracked-files=all`) are included. Default `git-status-before.txt` and
`git-status-after.txt` are identical because the whole
`protocol/iso8583` parent directory is still untracked; the hash manifests
are the authoritative before/after proof. HEAD is unchanged.

## Fixture-class counts

- Domain negatives exercised: **8** (6 request: 2 amount, 1 currency, 2 date,
  1 DE7; 2 response: 1 DE7, 1 date), all from actual `hex`.
- Domain positives exercised: **6** (2 amount boundaries, 1 leap, 2 year
  change, 1 response with later `at`).
- Targeted tests: **23**; protocol regression: **354**.

## Deferred / separate seams

- Freshness (`D-stale`, `ADMISSION_FRESHNESS`) and the 826-byte exchange budget
  (`B-DE48-exact-999`, `CAP-request-827`, `CAP-over-request-*`).
- Statutory/registry fixture, acquirer mandate, merchant/terminal association,
  one permitted route and issuer scenario resolution.
- Response packer, C4 attestation/carriage, HTTP route, production caller,
  runtime wiring and durable claims.
- Full P5 environment/principal/evidence identity/attestation signature and
  conditional business conflict/duplicate admission.

## Non-claims

Local JUnit and byte-fixture evidence only. This is not authentication, issuer
truth, registry authorization, QA, Security review or human acceptance. P1 and
G6 remain open; runtime stays inactive.
