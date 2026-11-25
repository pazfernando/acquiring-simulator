# Re-review brief — DR3-01 correction (bounded)

Status: DRAFT / NOT NORMATIVE. Request for an independent documentary re-review of exactly these bytes. No approval, closure or construction clearance is presumed by the requester.

## Why this exists

`reviews/continuation-rev3-domain-20261123/domain-review.md` (SHA-256 `154b6346544bfb760bb6bf1c5cefd63dcc6f09ab064151a7165972007b67c2e4`) records **DR3-01 (MEDIUM)**: in the reviewed `old-prior-fixtures.json`, `after24h-different-data` and `after7d-different-data` submit amount `10.01` while **both** `original.canonicalEqualityData.amount` and `newHead.canonicalEqualityData.amount` stay `10.00`. A latest-head-first implementation therefore also returns the expected `409 PAYLOAD_CONFLICT` and passes without proving prior-reference precedence.

## Requested scope

Review `continuation-rev3-dr301-20261123/old-prior-fixtures.json` (SHA-256 `1d3f1406b05d95f87e18f37b33eea3bb855ad5c80488f92891dcc132b5f3d5f5`) plus `supersession.md` in the same directory. Return:

1. DR3-01 disposition: CLOSED / PARTIAL / NOT CLOSED, with the exact basis.
2. Any new finding, numbered DR3-01-Nn, with severity.
3. At most three direct questions if a human decision is genuinely required. No financial-rule question is expected.

## Pinned inputs (SHA-256)

| Input (repository-relative) | SHA-256 |
| --- | --- |
| `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/reviews/continuation-rev3-domain-20261123/domain-review.md` | `154b6346544bfb760bb6bf1c5cefd63dcc6f09ab064151a7165972007b67c2e4` |
| `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/contract-deltas/continuation-rev3-20261123/old-prior-fixtures.json` (reviewed predecessor) | `f07439b6d93d4002209db34b9bf9871c7ee1c381a8b5be2b5a0ac0cc324b345c` |
| `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/contract-deltas/continuation-rev3-20261123/OUTPUTS.SHA256SUMS` (reviewed packet manifest, 21 entries) | `2000bb004e68e6d2974bef3067baeae6774f1922a21dca4ea18fecf6944ab467` |
| `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/contract-deltas/continuation-rev3-20261123/contracts.md` (payment/retention non-regression, prior-reference-first) | `33d5fb3c8ef6b4aa6baae3389c74032efb1faa6d809a3f09268910edf9ca1f10` |
| `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/contract-deltas/continuation-rev3-20261123/response-vectors.json` (historical H01–H26 envelopes) | `38acdad4c10259e9194f4c0f2544e4337c8b3d236583fe784dd446dd8b169ca4` |
| `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/contract-deltas/continuation-rev3-20261123/late-original-fixtures.json` (expiry/late-admission owner of that branch) | `5b156fdfe570761e41529497b8f231c41e5c727acf038fd1f0bd7486950fc328` |
| `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/contract-deltas/continuation-rev3-20261123/acceptance-scenarios.md` ("newer matching local head ignored") | `1cc09f281f1a595b5f72c7b48b3e6364105fb783fd4f71edc1538a37d622076a` |
| `docs/ai-sdlc/sources/acquiring_simulator_functional_guide.md` §13 PROC-01/NET-03, line-level basis that a repeated instruction must not produce a second purchase | `90a798c7e2a1de362993cb4974d306497d7de3567c163a3511c07b194a5ae094` |
| Corrected artifact under review | `1d3f1406b05d95f87e18f37b33eea3bb855ad5c80488f92891dcc132b5f3d5f5` |

The requester also verified: reviewer packet manifest 21/21 OK; ten inherited cases byte-identical (removing the inserted block and restoring the `status` line reproduces the predecessor byte-for-byte); the two `expected` blocks byte-equal to the reviewed `10.00`-head cases; new `bodyUtf8` canonical under sorted-key compact serialization.

## What to check

1. **Adversary actually bites.** For each new case, confirm the *only* canonical difference between pinned original and newer head is `amount` (`10.00` vs `10.01`), and that the head canonical projection equals the submission's canonical projection. Then confirm a latest-head-first implementation cannot satisfy the oracle: with a head match it must not emit `409 PAYLOAD_CONFLICT` with `attemptLinks: 0`, `newClaims: 0`, `newHeadUnchanged: true` — it would either coalesce/replay against the head or claim/link a new attempt, failing status, error, or attempt/claim oracles.
2. **Identity separation.** Original `AUTH-001`/`TX-001`/`paymentGeneration 1` versus head `AUTH-NEW-HEAD`/`TX-NEW-HEAD`/`paymentGeneration 2`; the submitted `priorAuthorizationId` is `AUTH-001`, not the head.
3. **Golden reuse.** `httpStatus 409`, the `body` object and the 317-byte `bodyUtf8` are identical to the reviewed different-data cases; `asOf` stays the injected historical `2026-11-18T12:00:05Z`; no historical C3 response byte was edited.
4. **No disclosure and zero effect.** `authorizationId`, `transactionId`, `originalCorrelationId`, `outcome`, `businessVersion` are `null`; `originalDisclosure: false`; `newClaims`, `financialSends`, `automaticPaymentPosts`, `attemptLinks` are all `0`; `newHeadUnchanged: true`.
5. **Boundary pairing.** The after-24h case uses `now 2026-11-19T12:00:05Z`; the after-7d case uses `now 2026-11-25T12:00:05Z`; both keep the same injected response clock. Confirm each remains inside its intended window semantics as the reviewed packet defined them.
6. **Supersession precision.** Confirm `supersession.md` names exactly one replaced file and exactly the two defective case IDs, adds exactly two case IDs, and claims nothing else replaced. Confirm the reviewed directory's files and manifest are unchanged.
7. **Isolation.** Each new case carries its own `resetScope` and complete inline state, so it depends on no shared setup and on no other case's result.

## Explicitly out of scope

Do not reopen or re-derive: G1–G4 documentary closures; DR2-01/DR2-03; late-original commit/duplicate/contradiction/CAS/new-head/expired-payload oracles; H01–H26 and setup overlays; Security N-02 through N-07 and SR2/C-01..C-04; NV05/AD01 retention mapping; D-5/D-6/R-2 mechanics. Those keep their recorded dispositions and owners. No runtime, browser, issuer, IAM, Terraform, deployment or QA execution is claimed anywhere in this directory; QA must still execute the branch.

One inherited semantic is deliberately left unchanged and may be raised as a **separate** finding, not as DR3-01: the after-7d case still expects `409 PAYLOAD_CONFLICT` even though the original payload's collection window has closed. The corrected artifact does not alter that expectation; `late-original-fixtures.json` owns expiry/late-admission behavior, and the contracts state that payload expiry never removes required journal/evidence or original links.
