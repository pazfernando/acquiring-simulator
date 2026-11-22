# P2a5 progress — real packer verified byte-exact (recorded, not rerun)

Status: **DELIVERED AND LOCALLY VERIFIED by the saved p2a5 run; this file is a
compact record written afterwards, not a fresh execution.**

## What was delivered

`Iso8583Packer` turns one already-validated structural field map into the exact
SIM-ISO87A-v1 body: MTI + uppercase primary bitmap + ascending data elements.
It enforces the profile P1 declarations, the scanner presence matrix, exact
fixed widths, numeric/uppercase-alnum alphabets, LL/LLL zero-padded prefixes and
the structural `DE48 <= 999` bound. It never pads, trims, rounds, truncates,
normalizes aliases or rewrites DE48, and it never mints DE38. DE48 key=value
schema, domain rules, P4, binding, freshness, 826 admission, attestation and
dispatch stay downstream.

## Oracle and counts

- Approved corpus: 85 vectors; `ACCEPT=31`; canonical `EVIDENCE_MATRIX`
  responses `=5`; 36 bodies packed byte-exactly.
- Targeted `Iso8583PackerTest`: **53/53**, failures 0, errors 0, exit `0`
  (31 + 5 parameterized + 17 unit tests).
- Protocol `Iso8583*Test`: **407/407**, exit `0` (`407 = 354 prior + 53 new`).
- stderr empty for both; all-input hashes 185/185 identical, diff exit `0`;
  HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315` unchanged.

## Source ↔ run

The hashed `Iso8583Packer.java`, `Iso8583PackException.java`,
`Iso8583DataElement.java`, `Iso8583PackerTest.java` and
`packed-vectors.json` are the exact inputs recorded in
`hashes-before.sha256` = `hashes-after.sha256`. Their compiled classes predate
the saved run by seconds, so the 53/407 logs correspond to these source hashes.
No test was rerun to produce this record.

## Not covered

Domain amount/currency/date/DE7, registry/fixture/mandate/route resolution,
freshness, 826 admission, P4/binding admission, response packing, C4 carriage,
HTTP route, production caller, runtime wiring and the full P5 envelope remain
separate. P1 and G6 stay open. Local evidence only; not QA, Security or
acceptance.

## Next slice

ONE small pure NEW-request freshness validator: `sent` within the approved
300-second window of server receipt, applied only to a server-supplied
`NEW_FIRST_DISPATCH` mode. Authorized historical replay / late evidence uses the
persisted request and must not be re-evaluated or re-encoded. The 826 exchange
budget remains the next separate slice.
