# Review brief — D-6 clarifications (bounded)

For an independent acquiring-domain review, then human approval. Read
`d6-clarifications.md` only; it is four clauses plus their justification, the fixture
implications and a recorded impact table. It does not restate D-6.

## What is being decided, in one line each

1. **C-1 — mandate identity.** For a registry with no mandate identifier and no version counter, the
   `mandateFingerprint` digest is the normative representation of D-6's "registry mandate
   identity/version"; `registryMandateId`/`registryVersion` become mandatory only when the registry
   exposes a counter, and a signature-valid pre-migration cursor is then a `410`, not a `400`.
2. **C-2 — `cursor` plus `after`.** Rejected as malformed (`400 INVALID_CURSOR`), not accepted with a
   precedence rule; no parameter is silently ignored.
3. **C-3 — malformed pagination.** One code for all unusable pagination input: `400` with
   `INVALID_CURSOR` and `action: null`, including unknown parameters, repeats, bad `snapshot`, bad
   `after` and `after` beyond the frozen head; plus a fixed, observable failure order.
4. **C-4 — `epoch`.** The HTTP-only cursor omits the member; an explicit `"epoch": null` is not
   required and must not be emitted; a live read must carry and compare it.

## What the reviewer should test the draft against

- D-6's exact wording in `design/contract-deltas/continuation-rev3-20261123/contracts.md` (the binding
  list, the six-code error set, "never a client-asserted authority", "reveals no tokens/keys").
- `wire-fixtures.json` `cursorUnsignedHarnessBinding` and `historyErrors`, read together with the
  packet's own "documentary, not usable cursors" header.
- The two evidence records named in §1 of the delta, and the QA report observations, to confirm the
  four points are the four points and nothing is smuggled in.
- Whether C-3's fixed failure order can disclose anything a caller does not already know, and whether
  C-2's rejection is preferable to a documented precedence for any reason the draft missed.

## Three questions for the reviewer

1. Is the "authority, not row identity" argument in C-1 sound for a read-pass generation, or does any
   D-6 or D-8 obligation actually require the registry row identity to be bound?
2. Does C-1.5's `410` for a signature-valid stale-schema cursor conflict with D-6's "unknown/tampered =
   400" in a way that would let a client distinguish states it should not distinguish?
3. Is `400 INVALID_CURSOR` for `after` beyond the frozen head defensible as "unusable input", or does
   it belong with `410 RESYNC_REQUIRED` because the condition means "your snapshot is behind"?

## Explicitly out of scope

No change to error bodies, TTL, `H`/`R`/`F`, response schema, margins, retention, payment semantics,
the server-side read-pass store, cursor-key sourcing or issuer verification. No code, test, fixture or
IaC edit accompanies this draft; §7 records what an implementer would have to change.

## Exact bytes under review

Repository `/home/pazfernando/projects/acquiring-simulator`, directory
`docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/contract-deltas/d6-clarifications-20261123/`,
packet-relative paths:

- `d6-clarifications.md` — SHA-256 `9bb8ca333e7a8b1f4f2e7d9072dfbc00c57b0648b1e62d2aa6161dbfd8bc0237`
- `OUTPUTS.SHA256SUMS` — authoritative for the final set; verify it first, then this brief.

Adopted packet manifest under review as input:
`continuation-rev3-20261123/OUTPUTS.SHA256SUMS` = `2000bb004e68e6d2974bef3067baeae6774f1922a21dca4ea18fecf6944ab467`
(21/21 entries verified before drafting).

A reviewer verdict, a Security verdict and human acceptance are separate and none is implied here.
