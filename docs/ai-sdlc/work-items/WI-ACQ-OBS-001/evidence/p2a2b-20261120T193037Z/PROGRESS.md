# P2a2b progress — strict DE48 response canonical parser (verified, unwired)

Status: **IMPLEMENTED AND LOCALLY TESTED 2026-11-20.** Response canonical
schema only; P4 matrix, DE38/binding, domain, admission, packer and runtime
integration remain open.

## Added

- `Iso8583De48ResponseParser` — full canonical response parser.
- `Iso8583De48Response` — immutable ordered map, byte-preserving values.
- `Iso8583De48ResponseParserTest` — 74 JUnit tests: 22 corpus-positive
  responses, 4 corpus schema negatives, 15 later-layer response fixtures,
  focused canonical/order/bounds/enum cases.
- `Iso8583De48Exception` — Javadoc widened to the shared request+response
  grammar; rule codes and behaviour unchanged.

## Verified

- `mvn -o -Dtest=Iso8583De48ResponseParserTest test`: **74/74**, exit 0,
  stderr empty.
- `mvn -o -Dtest='Iso8583*Test' test`: **254/254**, exit 0, stderr empty
  (25 reader + 74 response + 58 request + 94 scanner + 3 header).
- All 156 source/resource/pom/oracle inputs byte-identical before and after
  the recorded runs (`hashes-diff.txt` empty).
- Approved profile SHA-256 remains `de02c533…0e1fe481`; packed vectors remain
  `800bc071…f47c7ff0`.

## Corpus counts (exact)

41 structurally accepted 0110 fixtures were checked:

| Class | Count | Behaviour at this layer |
| --- | --- | --- |
| `ACCEPT` schema-positive | 22 | parsed; no business outcome claimed |
| schema negative | 4 | exact canonical rule asserted |
| later-layer deferred | 15 | parsed; verdict owned by P4/binding/attestation/conflict/domain |

Schema negatives: `N-wrong-op`→`INVALID_LITERAL` (`op`); `N-missing-actor`,
`N-missing-result`, `N-missing-evidence`→`MISSING_REQUIRED_KEY`.
Deferred: P4 5, binding 5 (instruction 3 + auth-reference 2), attestation 2,
conflict/quarantine 1, domain 2 (DE7 1, Gregorian date 1).
`CAP-response-3` is exactly 999 DE48 octets and parses; 827/826 exchange
admission is not evaluated.

## Scope

Required order is the 16 echoed request keys, optional `prior`, then mandatory
`actor,stage,result,reason,evidence,at`, all six required. The parser checks the
complete sequence itself and does **not** delegate the prefix to the request
parser. Literals `v=1`/`op=A`; enums for `cvm`/`crypto`/`risk` and
`actor`/`stage`/`result`/`reason`; `evidence` 1–64; `sent`/`at` exactly 14
digits. `at` is syntactic only: no actual-time, Gregorian, DE7 or ordering
claim. No normalization, no P4 consistency decision, no DE38 binding, no
amount/currency, no 826 budget, no response packer, no HTTP/C4, no runtime
wiring.

## Deferred from this slice

P4 actor/stage/result matrix, DE38 trusted-evidence binding, original
instruction/context binding, attestation, business conflict/quarantine, domain
Gregorian date/DE7/amount/currency, freshness, 826-byte admission, response
packer, C4 carriage, HTTP/envelope integration and runtime activation.

## Next slice (one)

**P4 actor/stage/result decision matrix + DE38/instruction binding** over the
already-parsed response, followed by domain Gregorian/amount/currency. Response
packer, 826 admission and runtime remain separate.

No Git write, publication, cloud call, credential use, deletion or spec edit was
performed.
