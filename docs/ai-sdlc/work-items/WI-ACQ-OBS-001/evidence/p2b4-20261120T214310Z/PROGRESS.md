# P2b4 progress — signed-P5 / P6 field binding (local construction evidence)

Status: **implemented and locally verified in one targeted run plus one affected C4
regression.** This is local construction evidence, not independent QA, Security review,
human acceptance, business admission, payment confirmation or a deployment.

## Delivered (new files only)

- `C4P6SignedFieldBinder` — one pure binder in the `channel` package.
- `C4P6BindingDecision` — bound signed claims + explicit binding scope, no confirmed flag.
- `C4P6BindingException` — fixed sanitized category, at most one component and an optional
  fixed lower-layer rule constant.
- `C4P6BindingViolation` — closed failure vocabulary.
- `C4P6SignedFieldBinderTest` — 18 cases (below).

No existing production or test file was edited; `pom.xml` is untouched and no dependency
was added.

## Inputs (no caller trust flag, no arbitrary trusted DTO)

- `C4VerifiedAttestation` or `C4VerifiedLateDelivery` produced by the real
  `C4EvidenceVerifier` / `C4LateDeliveryVerifier`.
- The exact signed `C4EvidenceAttestation` content whose canonical SHA-256 must still equal
  the verified `canonicalAttestationSha256`; identity fields must still equal the verified
  record. Pairing a verified result with different signed content is rejected.
- The current trusted `C4AuthenticatedTransportContext` (environment + authenticated
  sender), never a client value.
- The immutable persisted original `0100` octets and the exact received `0110` octets.

The binder never re-encodes anything; it hashes the exact byte arrays with SHA-256.

## Bound fields and order

After the signature/trust result exists:

1. exact persisted original hash == signed `instructionSha256`;
2. exact received response hash == signed `responseSha256`;
3. approved P6 response order: domain (DE3/4/22/25/49 + Gregorian sent + DE7) → P4
   actor/stage/DE39/result/reason → original instruction/context echo + exact DE38;
4. signed tuple vs parsed response (`DE39`, actor, stage, result, reason);
5. signed identity/context/generation vs the persisted original (`aid, tid, cid, att, acq,
   mid, term, pay, gen`) and amount/currency/STAN/RRN/DE7/sent vs its physical projection
   (`DE4, DE49, DE11, DE37, DE7, sent`);
6. signed `evidenceId` vs the response `evidence` echo;
7. signed `availableAt` instant vs the response `at` second projection (sub-second values
   are rejected, not truncated).

All 31 signed keys are bound: `schemaVersion` and `profile` are fixed literals enforced by
`C4EvidenceAttestation` construction; the other 29 are compared as above. The verified
relay binding is used only to check the RELAY principal against the current sender and is
not compared to the persisted instruction (it is carriage).

Environment, signing principal, relay principal, key id and acquirer reference are re-bound
to the current trusted context; a different environment or sender is rejected rather than
substituted. The registry mandate is **not** re-resolved here, but the acquirer reference
it was checked against cannot change.

## Actual results (recorded, not rerun)

| Invocation | Result | Exit |
| --- | --- | --- |
| `-Dtest=C4P6SignedFieldBinderTest test` | 18 run, 0 failures, 0 errors, 0 skipped | 0 |
| `-Dtest=C4EvidenceVerifierTest,C4LateDeliveryTest,C4CarrierParserTest,C4EvidenceAttestationSchemaTest test` | 80 run (33 + 23 + 18 + 6), 0/0/0 | 0 |

Both stderr files are empty. No failing attempt exists in this slice.

## Freeze

`hashes-frozen-pre-run.sha256` and `hashes-after.sha256` are byte-identical over **254**
inputs: every file under `src/main` and `src/test`, `pom.xml`, the OBS `vectors/`
directory and the `channel-contract.md` / `iso8583-profile.md` oracles. The green runs
therefore exercised the exact frozen bytes. `delivered-files.sha256` re-verifies the five
new files; `git status` before/after is identical; HEAD is unchanged.

## Not covered / separate seams

Registry/mandate re-resolution at binding time, key custody/rotation/revocation, TLS/IAM
principal mapping, durable processor receipt, duplicate/conflict resolution, lateness
deadline, atomic evidence+receipt+version+event admission, the financial outcome, HTTP,
storage, a production caller and runtime wiring remain deferred. The decision carries the
signed claims, not a confirmed result; no result is known before the atomic receipt
transaction and no identifier is minted. P1 and G6 remain open. Independent Security
review, QA and human acceptance remain pending.

## Next smallest slice

Atomic receipt integration seam: take the bound decision plus the exact verified bytes and
perform one conditional transaction that writes normalized evidence, processor receipt,
business version and the critical consultable event together (duplicate/conflict detected,
no partial state), still local and without HTTP/runtime.
