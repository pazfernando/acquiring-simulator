# Continuation construction slice H1 — progress log (started 2026-11-23)

This file is written first and updated incrementally so an interruption cannot erase the analysis.
It records what was observed, in order. It is developer evidence only: not independent QA, not
Security review, not human acceptance.

## 1. Binding verification (before any write)

| Check | Expected | Observed | Match |
| --- | --- | --- | --- |
| `realpath .` | `/home/pazfernando/projects/acquiring-simulator` | `/home/pazfernando/projects/acquiring-simulator` | yes |
| `git rev-parse --show-toplevel` | same | `/home/pazfernando/projects/acquiring-simulator` | yes |
| branch | `feat/wi-acq-auth-001` | `feat/wi-acq-auth-001` | yes |
| HEAD | `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` | `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` | yes |
| dirty ownership | existing continuation work, preserved | see §2 | yes |

No divergence. The working tree was dirty on entry with pre-existing continuation work (modified
`WORK_ITEM.md`, `delivery-status.md`, `implementation-progress.md`, `owner-decisions.md`,
`requirement-traceability.md`, `security-review.md`, `AwsRuntime.java`, `AuthorizationService.java`,
and many untracked design/evidence/infra/console files). None of it was reverted or reformatted.

## 2. Inputs read (and their observed digests)

| Input | Observed SHA-256 | Expected | Match |
| --- | --- | --- | --- |
| `handoffs/continuation-20261123/construction-handoff.md` | (read; coordinator dispatch) | n/a | read |
| `design/contract-deltas/n02-origin-verification-20261123/contract-delta.md` | `159599755766d54942c5ae7a95bef35a7d611b7dff4e2a66b9dc83ef9fa0f3ad` | `159599755766d54942c5ae7a95bef35a7d611b7dff4e2a66b9dc83ef9fa0f3ad` | yes |
| `design/contract-deltas/continuation-rev3-20261123/OUTPUTS.SHA256SUMS` | `2000bb004e68e6d2974bef3067baeae6774f1922a21dca4ea18fecf6944ab467` | `2000bb004e68e6d2974bef3067baeae6774f1922a21dca4ea18fecf6944ab467` | yes |

Gate rules taken from N-02 §5–7 and the OV oracle suite; read protocol/claim rules from the rev3
packet (`README.md`, `contracts.md` D-6/R-2, `construction-handoff.md` H1, and the O6 page fixtures in
`vectors/websocket-vectors.json`). Existing console package and `AwsRuntime` reviewed before adding
anything.

## 3. Scope actually implemented (and nothing beyond)

1. Origin-verification gate: `EDGE_ORIGIN_VERIFY` from configuration, init fails on absent/empty/
   short/bad-alphabet, constant-time byte-exact comparison, classification ABSENT/EMPTY/DUPLICATE/
   MISMATCH, first application step before dispatch/404/405/body/credential/rate-limit/service,
   opaque `403` body `{"version":1,"error":"EDGE_ORIGIN_NOT_VERIFIED","action":null}`, no echo.
2. Read-only history `GET /console/v1/transactions/{id}/events`: strong HEAD then bounded Query,
   fixed H/R/F, opaque signed cursor bound to principal/environment/mandate/generation, re-auth
   before and after the store read, explicit expired prefix and truthful gaps, HTTP-only.

No TTL/compaction, no projection, no transaction creation, no financial capability, no new
dependency, no Git write/push/PR/merge/deploy, no AWS or Keycloak call.

## 4. Status log

- [x] Binding verified (§1).
- [x] Contract inputs read (§2).
- [x] Evidence directory and this file created before code.
- [x] Origin-verification gate implemented.
- [x] History route implemented.
- [x] Focused tests run; exact counts/exits recorded in `RESULTS.md`.
- [x] `implementation-progress.md` and genuine traceability rows updated; independent verification
      kept PENDING.

The four ticks above mean "implemented and locally executed in this slice", not accepted: the run is
developer evidence and every traceability row stays `independent verification: PENDING`.

## 5. Open ambiguities (recorded, not silently resolved; all three unchanged by the recorded run)

1. **Malformed pagination input has no named D-6 error code.** D-6 names `INVALID_CURSOR` only for an
   unknown/tampered cursor. The route validates `snapshot`/`after` defensively; the provisional
   mapping used is recorded in `RESULTS.md` and flagged for reviewer confirmation rather than
   presented as contract text.
2. **Server-side read-pass store.** D-6 says the read-pass identity is stored server-side. This slice
   adds no table/projection, so generation continuity and retirement are derived from the signed
   cursor plus the re-resolved mandate fingerprint and `expiryRevision`. Any durable read-pass store
   remains future work; it is not claimed as implemented.
3. **Cursor signing key source.** No env-var name is fixed in the delta. The codec takes an injected
   key (`keyId` + secret bytes) as a required collaborator; runtime sourcing is left to the later
   deployed composition rather than invented here.
