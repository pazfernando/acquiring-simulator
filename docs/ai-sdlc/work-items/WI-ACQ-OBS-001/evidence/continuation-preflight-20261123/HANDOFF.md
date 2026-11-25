# Ready handoff — real Keycloak auth & server history (2026-11-23)

Status: **READY HANDOFF — blocked on approvals; no code written.** Authoritative state is the
commission and pinned inputs; this document adds no business rule and names no decided behavior that
is not already recorded. Every hash below is SHA-256 of the current bytes at
HEAD `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14`, branch `feat/wi-acq-auth-001`.

## 1. Binding
- Checkout/realpath: `/home/pazfernando/projects/acquiring-simulator` (git toplevel identical)
- Branch / HEAD: `feat/wi-acq-auth-001` / `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` — MATCH expected
- Runtime/model: OpenCode ACP (`OPENCODE_CLIENT=acp`), `nan/deepseek-v4-flash`, default, no override
- Inputs `inputs.json` = `3f5027ee12585f2a088790e0cbdf28d5151d902679eb0ff70e5420c7f720c846`; 11/11 input hashes OK
- Snapshot: `BINDING.txt`; preflight: `PROGRESS.md`

## 2. Approved vs proposed — REAL KEYCLOAK INTEGRATION

### Approved (owner-recorded, binding direction)
| Item | Source (hash) |
| --- | --- |
| Self-managed Keycloak as `private_key_jwt` issuer (D-A) | `owner-decisions.md` `c73ed0aa…bd6`, D-A section (lines 92–99) |
| One Keycloak task, 900s existing-token window (K-1) | same file, lines 119–121 |
| Disposable / rebuild-not-repair, no SLA (K-2) | same file, lines 123–127 |
| Presentation in cloud, **no presenter relay** (K-3) | same file, lines 129–136 |
| Publicly reachable, single-demo-login gate + attempt rate limiting (P-1) | same file, lines 138–142 |
| Non-mTLS WS accepted, notification-only (D-D) | same file, lines 82–86 |
| Single demo JWT principal; service IAM separate | `requirements.md` `49c5d412…59df` R25; 2026-11-19 architecture approval |
| Seven-day functional history retention | `owner-decisions.md` line 50 |

These are **direction**, not permission to build or deploy. Owner record itself says "not
implementation, new credential flow, provisioning, deployment or login."

### Proposed / non-normative (NOT authority to implement)
| Item | Where | State |
| --- | --- | --- |
| Access realization rev4 (clients, PKCE/state/nonce, memory-only tokens, refresh vs re-login, 900s/8h caps) | `design/access-realization/design-rev4.md` `3926423e…dc56` | Proposal; numeric/config detail not owner-approved |
| Integrated contract-delta bundle (D-1…D-9, A-1/A-2, R-1/R-2) | `design/contract-deltas/operation-a-contract-delta.md` `6b1e525f…c822` | **DRAFT / NOT NORMATIVE**: 10 PROPOSED NORMATIVE CHANGE, 1 OPEN CONFLICT (D-5), 2 SOURCED |
| D-5: `svc-console-ws` vs `console-demo` WS-audience/scope issuance, authorizer allowlist | same, D-5 | **OPEN CONFLICT** |
| D-2 OIDC hardening; D-8 `console.dispatch` migration + G-4 mandate-binding table | same, D-2/D-8 | PROPOSED |
| D-7 edge/CSP/CORS/rate numbers | same, D-7 | PROPOSED |
| Domain disposition of bundle | `reviews/contract-delta-20261122/domain-review.md` `81c258c8…5f7d22` | **CONDITIONAL** (not normative incorporation) |
| Fresh continuation domain re-review | `reviews/continuation-domain-20261123/progress.md` | **IN PROGRESS** (stub only) |
| Security rev4 | `reviews/access-design-rev4-20261122/security-review.md` `c418f93a…3ed2` | **PASS with conditions, design-delta authoring only**; not implementation/deploy/credential authority |
| Keycloak hosting, realm/client config, key custody, rebuild | DevOps design + Security | Not authored/approved here; separate authorization |

### Security/domain gates still open (selected)
D-5 issuer/client/grant/authorizer reconciliation; G-4 trusted demo-mandate propagation; PKCE/
state/nonce/code-reuse on the pinned Keycloak release; audience separation console/WS/service;
refresh rotation/logout; no service material in bundle; public routing/CSP/CORS/rate evidence;
config-secret separation and rebuild verification. (Security rev4 §5 items 1–9; domain §8 items 1–6.)

**Determination:** implementing real Keycloak authentication now would build D-2/D-5/D-7/D-8
proposed deltas and an unsettled D-5 → forbidden by the commission ("do NOT implement unreviewed
public access deltas"). **Blocked.**

## 3. Approved vs proposed — SERVER HISTORY

### Approved (direction / retention)
| Item | Source |
| --- | --- |
| Single queryable event store; Streams/notifier/WS live + HTTP initial/history/reconnect reads of the **same** source; no 1s polling (R25 APPROVED DIRECTION; numeric/retention are PROPOSED) | `requirements.md` R25 |
| Seven-day functional-history retention from original `collectedAt`; no auto payment re-execution | `owner-decisions.md` line 50 |
| Atomic business + critical consultable event, no dual write / no duplicate history projection (R24; O3) | `requirements.md` R24; `observation-contract.md` `ca53e384…205a` O3 |
| Non-destructive preservation of journal/evidence/no-resend/alias identities | R-1 (sourced); `retention-delta.md` `d11ead64…fd76` |

### Proposed / non-normative
| Item | Where | State |
| --- | --- | --- |
| Read endpoint `GET /console/v1/transactions/{id}/events`; fixed snapshot H/R/F; page 100/256KiB; pass 1000/1MiB; 15-min signed cursor; 400/403/410 `RESYNC_REQUIRED` | `observation-contract.md` O6 | **PROPOSED realization/limits** (doc status: "PROPOSED") |
| Seven-day logical expiry, `expiryRevision`, expired ranges, TTL mapping | O8; `retention-delta.md` | PROPOSED; NV-05/AD-01 OPEN; correction pending review |
| Cursor rebind from relay SERVICE scope → demo subject + trusted environment | contract delta D-6 | PROPOSED, unreviewed |
| Browser-direct public read topology (R19/R25 replacement) | contract delta D-4 | PROPOSED, unreviewed |
| Cursor lifetime vs socket lease, mid-pass stop/margin | contract delta R-2 | PROPOSED, unreviewed |
| P3 "Console read model" construction | `implementation-progress.md` §6 (G9/P3) | Planned; **no independent approval for construction** |

### Current code state (verified)
- New browser-direct console package has **submission only**:
  `ConsoleAuthorizationRoute.PATH = POST /console/v1/authorizations` — no history route.
- New UI `console-ui/js/session-history.js` `35b100fa…e5e4` is **tab-memory only**, explicitly
  "NOT the server read model"; states the approved-shape read is not implemented. **Preserve as test-only.**
- A **tracked legacy** `presenter/` relay already implements an events read
  (`PresenterRelayServer.handleEvents`, ~lines 564–637; `readRange(...100)`, snapshot H/R/F, 400)
  and `PresenterCursorCodec` `c80ea7fb…f863`. It is **superseded by K-3** (relay removed) and is
  bound to loopback relay sessions/service JWT — **not reusable as-is** for the public browser-direct
  surface.
- Event store/append side exists (`DynamoEventStore` `14a744a5…c0d5`); read model (G9) is a gap.

**Determination:** the read contract's binding, cursor lifetime, page bounds and error realization
are proposed and one binding (D-6) is under proposed change. Any implementation now would choose
unreviewed detail. **No safe already-approved independent slice → no code; ready handoff.**

## 4. Smallest executable coding handoff (after gates)

Execute only when (all mandatory):
1. Independent domain disposition of the exact delta bundle is **final** (continuation-domain review
   complete), and D-5 is reconciled (issuer/client/audience/authorizer matrix).
2. Security implementation conditions for the public surface are accepted (not just design-delta).
3. Explicit construction authority for the read model (P3) and, for browser-direct Keycloak, the
   D-2/D-5/D-7/D-8 contracts are normative.
4. For any deployment: reviewed concrete environment + passed gates (separate DevOps/owner step).

### Slice H1 (smallest, server history) — recommended first coding step once approved
- Add `com.acquiring.simulator.console.ConsoleHistoryRoute` serving only GET
  `/console/v1/transactions/{id}/events` (no list, no WS) over the existing `DynamoEventStore`.
- Reuse the *semantics* (not the relay session) of `PresenterRelayServer.handleEvents`:
  strong `head()` → fixed `snapshotHead` H, `expiryRevision` R, `firstRetainedSequence` F;
  bounded page; `coveredThrough`; `expiredRanges`/`gapRanges`; opaque cursor; 400 invalid,
  403 scope, 410 `RESYNC_REQUIRED`.
- Bind the cursor/AuthN to the **approved** binding only (per final D-6 disposition — do not
  pre-adopt subject+environment before it is normative).
- Tests: DynamoDB Local (`amazon/dynamodb-local:2.6.1`) cases for fixed H, pagination, tampered
  cursor 400, wrong scope 403, expired/revision-changed 410, expiry boundary, and no financial side
  effect. No payment path change.
- Files/anchors to touch (current hashes): `ConsoleAuthorizationRoute.java` `7c222e5b…598d`,
  `ConsoleHttpRequest.java` `bf911d0c…a6b`, `ConsoleHttpResponse.java` `47fab70e…84c6`,
  `AwsRuntime.java` `23251bf4…a491`, `pom.xml` `b47fb547…a93c`; add route + test classes.
- **Do not** reuse `presenter/` relay binding; copy only pure read/format logic with a note.

### Slice K1 (real Keycloak) — only after §4 gates 1–3
- Implement `ConsoleTokenDecoder` with real JWT signature/JWKS/issuer verification (no stub), plus
  the D-2 public-client PKCE/state/nonce flow and D-5 audience/client/authorizer rules exactly as
  finally approved.
- Keep the existing `DemoConsoleAuthorizer` claim-order policy (`874bc95d…78de`) as the policy core;
  replace only the decoder and wire the approved client/audience/subject config.
- Wire a real Keycloak realm/client from DevOps-authored config; no secrets in bundle/state/logs.
- Tests: forged/expired/wrong-iss/aud/azp/sub/scope negatives; code reuse/state/nonce negatives;
  no browser service secret; no token in storage/URL/logs.

## 5. Non-claims / limits
- No implementation, no contract acceptance, no D-5 resolution, no finding closure, no deployment.
- No Git publication/merge; no login/credential flow; no cloud apply; no destructive change.
- Local tests green are developer evidence only, not independent QA/Security/acceptance.
- Onward delegation not performed; DevOps/domain keep their own report directories.
