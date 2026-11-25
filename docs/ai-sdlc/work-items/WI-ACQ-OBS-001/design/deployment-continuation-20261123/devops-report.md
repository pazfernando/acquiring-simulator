# Deployment readiness — continuation 2026-11-23 (DevOps)

Status: **DOCUMENTARY READINESS REPORT.** No infrastructure was provisioned, no pipeline was run, no credential flow was started, no Keycloak or AWS API was called, and no application, contract or review file was modified. Everything below separates *what exists as a file*, *what was validated locally*, and *what was never executed remotely* — and in this slice nothing was executed remotely.

Author: ai-sdlc-devops. Date: 2026-11-23 (America/Bogota). Runtime observed: native OpenClaw subagent `ai-sdlc-devops`, session `agent:ai-sdlc-devops:subagent:d7dfcdfb-487e-4f67-8e40-ae62f7cf0b99`; model metadata `nan/deepseek-v4-flash`, the configured default, **no model override**, no fallback invoked. No onward delegation. No shell AWS login; the local `aws` CLI was never invoked.

Read role/shared basis: `/home/pazfernando/.openclaw/workspace/ai-sdlc-devops/AGENTS.md`, `shared/ai-sdlc/GLOSSARY.md`, `PROJECT_EXECUTION.md` (infrastructure/automation defaults), `VERIFICATION_DELIVERY.md`.

---

## 0. Why this report exists

The owner continuation of 2026-11-23 authorizes pursuing real authentication, server-side history, independent reviews and deployment. That authorization is **not** an approval of unresolved contract changes, and it is not a waiver of gates. Deployment still requires a concrete destination and configuration.

This report therefore answers four questions with evidence instead of intent:

1. What infrastructure artifact actually exists in the repository today, and what is missing?
2. Is the Keycloak realm/PKCE/client/WebSocket issuance shape coherent enough to build, and what proof is missing before it may be called reconciled?
3. Which exact inputs remain unknown, and who must supply them (never invented here)?
4. What is the ordered gate sequence from today's state to a deployable, independently verified environment?

---

## 1. Input integrity — pinned sources

### 1.1 Commission manifest (recomputed with `sha256sum`)

Paths relative to the repository root `/home/pazfernando/projects/acquiring-simulator`. **11/11 MATCH, 0 mismatch, 0 missing.**

| Input | SHA-256 | Result |
| --- | --- | --- |
| `docs/ai-sdlc/PROJECT.md` | `8584a2ed39a3ac548f303bf41bceb8e5e9df3fd3ec678289bdb128560643710e` | MATCH |
| `docs/ai-sdlc/sources/acquiring_simulator_functional_guide.md` | `90a798c7e2a1de362993cb4974d306497d7de3567c163a3511c07b194a5ae094` | MATCH |
| `.../WI-ACQ-OBS-001/WORK_ITEM.md` | `400a40d94197459b890de1c987381f385610b0b6053f6c7bcb2e8cd79c4673b2` | MATCH |
| `.../WI-ACQ-OBS-001/owner-decisions.md` | `c73ed0aad9dd6ba563755e3b4242f2b25563381fd3a67260608cb8d767429bd6` | MATCH |
| `.../WI-ACQ-OBS-001/requirements.md` | `49c5d412e3705c320a2f78b190555f49e7e0fa4662cff9329d259f89e2b459df` | MATCH |
| `.../WI-ACQ-OBS-001/observation-contract.md` | `ca53e384a46d24b3c1119b3e6ba804a9a9d4caac049a6b0c55a40c72842a205a` | MATCH |
| `.../WI-ACQ-OBS-001/acceptance-scenarios.md` | `1586898418c09dcf092d567a4155b4524a0bc34500bf4ea76ce8f76465aa7733` | MATCH |
| `.../WI-ACQ-OBS-001/channel-contract.md` | `495e1172e76ab9ba81f8a8026e7efec25baa6e4fff0d754c0a4c92bd38ea3bca` | MATCH |
| `.../design/access-realization/design-rev4.md` | `3926423e9f42f6949647e5904271b6f6818b977301b66e81ffcf334f10c5dc56` | MATCH |
| `.../design/contract-deltas/operation-a-contract-delta.md` | `6b1e525f9071ca7f6117386da0ac8a0bb8f30ee050f7a75963e254e22d08c822` | MATCH |
| `.../reviews/access-design-rev4-20261122/security-review.md` | `c418f93a70fc41fc282ba5c2ea5c28714d14337f02c7d58ee4ecb97c8d123ed2` | MATCH |

Commission and manifest identities: `commission.md` `f8936ea5b7c0727ee0f4aca9fcd3eeb9cc7df515a239a7168a7336ce4beee419`; `inputs.json` `3f5027ee12585f2a088790e0cbdf28d5151d902679eb0ff70e5420c7f720c846` (identical to the declared manifest hash).

### 1.2 Additional sources read and pinned (not in the manifest, hashed here for traceability)

| Source | SHA-256 | Role in this report |
| --- | --- | --- |
| `design/access-realization/design-rev3.md` | `cdd65729ec04708e9bd96626d29980d616fa32261620825987c9eb2cc7d63d2a` | carried SR2 answers referenced by rev4 |
| `design/access-realization/d5-ws-issuance.md` | `c7ef28ef0279d94e9896d5c7a003b62864edebbd69847867449888f2e45a5b7b` | D-5 issuance proposal reconciled in §3 |
| `design/access-realization/configuration-schema.md` | `1f497daade1a8429458e2cc40047adf326a97f2fb6636d583e1bf2e930bf1bb2` | variable/secret names and open placeholders |
| `design/access-realization/design-rev2.md` | `6b15161875e702ebae74e4f76b4b94cae9ffbbb94d6a9fcd42aee563b8f3873c` | Keycloak hosting profile, realm/client tables, state backend |
| `infra/template.yaml` | `f7637650095ea5166aee9e3d41dbad3d033324c45d48268c70d2bb58d62c1abe` | the only infrastructure artifact that exists |
| `console-ui/js/token/oidc-pkce-client.js` | `b302ddb96acccc8615fe68587bc761b7b90e70d5d91cdbe605ec5729953b8da2` | real-PKCE branch (unexecuted) |
| `adr-operation-a-access-and-environment.md` | read | owner-selected access direction, region, OIDC, on-demand |

### 1.3 Binding and revision

Binding: `/home/pazfernando/projects/acquiring-simulator`, expected branch `feat/wi-acq-auth-001`, last Implementation-observed HEAD `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14`, dirty working tree (8 tracked modifications, 24 untracked entries per the Implementation continuation preflight).

**This role ran no Git command.** All Git/provider inspection routes through Implementation in OpenCode ACP per the operating contract, so the revision above is recorded as supplied, not independently attested here. Conclusions in this report attach to the hashed document bytes in §1, not to a verified commit.

---

## 2. Existing versus missing infrastructure (filesystem only)

### 2.1 What exists

| Artifact | Path | Observed nature | Deployment status |
| --- | --- | --- | --- |
| CloudFormation/SAM template | `infra/template.yaml` (3 787 B) | Operation A legacy: HTTP API with a single JWT authorizer, three Java 25 ARM64 Lambdas (Processor → Mastercard → Issuer), two on-demand DynamoDB tables, scoped IAM, throttling, 7-day log groups | **Not deployed, not deployable as the target topology.** No state backend, no environment separation, no console, no WebSocket, no Keycloak, no mTLS |
| Fixture renderer | `scripts/render_aws_fixtures.py` | Offline `CONFIG#ACTIVE/PROFILE` + `CALLER#<subject>/PROFILE` renderer; writes nothing to AWS | Authoring tool only |
| SPA (local harness) | `console-ui/` (7 files, ~32 KB JS) | Purchase form, result/task timeline, history, PKCE module with a **refused-if-no-PKCE** `oidc` branch and a clearly-labelled **unsigned dev stub** | Local harness. Header banner states it is not authentication and not connected to AWS |
| Build layout | `pom.xml`, `target/lambda/` | Spring Boot parent 4.1.2, `java.version` 25, AWS SDK v2 2.57.3, artifact `acquiring-simulator:0.0.1-SNAPSHOT`; `target/lambda` holds classes/resources + `lib/` for a Lambda `CodeUri` | Local build output present; **no artifact digest recorded for a deployment** |
| Test harness | `target/lambda/application-local.properties`, `-prod.properties` | SQLite local / PostgreSQL legacy regression shape; both explicitly non-deployable | Not an environment |

### 2.2 What does not exist

Verified by `find` over the work tree excluding `.git` and `target`:

- **Zero Terraform files.** No `*.tf`, `*.tfvars`, `*.hcl` anywhere in the repository. The owner infrastructure default (native Terraform HCL with remote S3 state) is therefore **entirely unauthored**, not merely incomplete. This is the single largest gap between the approved direction and the repository.
- **Zero CI definitions.** No `.github/` directory, no workflow file, no pipeline configuration of any kind. Dependency scanning, secret scanning, SAST, build/test automation, artifact digesting and the gated deploy stage are all unwritten.
- No container build for Keycloak (no `Dockerfile`, no image digest pin).
- No realm import artifact, no client registration material, no key registration procedure file.
- No backend bootstrap definition (state bucket, CMK, versioning, Block Public Access, lockfile usage).
- No truststore, client-certificate issuance path, or custom-domain/mTLS resources.
- No DNS zone, certificate or WAF definitions.

### 2.3 Component provenance against the approved design rev4

| Rev4 component | Existing artifact | Gap |
| --- | --- | --- |
| Static SPA + CloudFront + private S3 (OAC) + WAF + ACM + response-header CSP | `console-ui/` source only | Hosting, certificate, WAF rules, headers policy, `/config.json` runtime config artifact |
| HTTP API with JWT authorizer + console Lambda | SAM template has a JWT authorizer on one route (`/authorizations`), no console route, no `console.demo` scope check | New API/route/authorizer, console Lambda packaging, same-origin CloudFront `/api/*` behavior |
| Console API → ACL (mTLS custom domain + `svc-console` OAuth) | none | Custom domain with truststore, mutual-TLS verification, client assertion keys |
| ACL → processor (mTLS + `svc-acl`) | none | as above, plus the C2 byte contract adapter |
| Processor → network-sim → issuer (IAM only) | `template.yaml` three-function chain with scoped `lambda:InvokeFunction` | Function-per-sender separation for evidence admission (SR2-01), explicit deny policies (SR2-02) |
| Evidence admission (one function per delivering principal) | none | Three functions/handlers + execution roles + registry sender mapping |
| Registry / journal / event / connection tables, TTL, 7-day logical expiry | Two generic tables only | Table set, keys, TTL mapping, PITR decision, retention realization (NV-05 open) |
| WebSocket API + `$connect` authorizer + custom domain + `@connections` notifier | none | Whole path, including the D-5 authorizer rules |
| Keycloak (single task, Aurora Serverless v2, private subnets, ALB with path allowlist, SSM operator path, ACM TLS) | none | Whole component; a genuinely always-running cost item |
| Streams → notifier pipeline | none | Stream, event-source mapping, notifier Lambda, retry/DLQ bounds |
| ADOT/telemetry packaging | none | Layer/build, Java 25 compatibility still unproven |
| S3 Terraform state: versioning, SSE-KMS, Block Public Access, `use_lockfile`, `allowed_account_ids`, explicit bootstrap | none | Entire backend + bootstrap procedure |
| GitHub OIDC trust (exact `sub`, no wildcard, `id-token: write` only on deploy) | none | Provider, role, trust policy, environment scoping |
| Observability: structured logs, 7-day retention, alarms, correlation | SAM template creates three 7-day log groups only | Alarms, dashboards, log-field exclusions (`Authorization`, `Sec-WebSocket-Protocol`) |

**Provenance note on the SAM template.** The completion plan records the recommendation to keep `infra/template.yaml` as historical/reference infrastructure until Terraform reaches parity, then mark it non-deployable rather than delete it. That recommendation is respected: this report marks it **superseded for deployment, preserved for provenance**, and identifies no overlap that would justify deleting it.

### 2.4 Two deployment-blocking documentary deltas found while reconciling artifacts

1. **CSP mismatch between the local harness and the approved deployment policy.** `console-ui/index.html` carries `default-src 'none'; connect-src 'self'; form-action 'none'`. Rev4 §2.4 requires `connect-src 'self' https://auth.<domain> https://ws.<domain>` and `form-action 'self' https://auth.<domain>`. Served unchanged, the local CSP would block the OIDC redirect and the socket. The deployment build therefore needs a deployment CSP variant (served by CloudFront response headers per rev4, and not baked into the HTML as an immutable literal). This is an Implementation-side build change; DevOps does not edit application files.
2. **ACM region statement in rev4 §7.2.** The table records `CONSOLE_CERT_ARN` as "ACM ARN (us-east-2 for CloudFront)". AWS's documented requirement is that a CloudFront viewer certificate lives in **`us-east-1`**, while the rest of the estate remains in `us-east-2`. This must be corrected in the next design pass and reflected in the Terraform provider alias (`aws.us_east_1` for the certificate). Recorded here as an ADVISORY documentary defect; it does not change the owner's `us-east-2` selection.

---

## 3. Keycloak realm / PKCE / client / WebSocket D-5 issuance reconciliation

### 3.1 The three texts that must agree, and how they differ today

| Text | Client set | WebSocket credential |
| --- | --- | --- |
| `design-rev2.md` §2.5 (pre-relay-removal) | `console-demo` (public PKCE), `svc-console-relay`, `svc-console-ws`, `svc-acl` | relay holds a service token and sets `Authorization` + `X-Demo-JWT` |
| `design-rev4.md` §7.3 (relay removed) | `console-demo`, `svc-console`, `svc-console-ws` (latent), `svc-acl` | demo token in `Sec-WebSocket-Protocol`, exact `Origin` |
| `d5-ws-issuance.md` (D-5 addendum) | `console-demo` with **optional** scopes `console-api` and `ws-observe`; `svc-console`; `svc-acl`; **`svc-console-ws` retired (deleted)** | second, disjoint-audience demo token via `prompt=none`; `$connect` allowlists `azp=console-demo` and the single demo `sub` |

Rev4 §7.3 still lists `svc-console-ws`; the D-5 addendum retires it. Security review rev4 (ADV-2) already flagged the ambiguity and required the contract delta to resolve it rather than leave it implicit. The domain review of the contract-delta bundle agreed D-5 is a client-and-issuance-table problem, not a business-rule problem.

### 3.2 Proposed canonical shape (one coherent configuration, for Requirements/Security confirmation)

| Element | Proposed value | Rationale |
| --- | --- | --- |
| Realm | one realm per environment, `acq-<env>` | a token from another environment must fail issuer/audience checks (§2.4 of rev2) |
| `console-demo` | public client, authorization code + **PKCE S256**, no secret, exact redirect `https://console.<domain>/auth/callback`, access token ≤900 s | browser-only; nothing secret can be embedded |
| `console-api` (optional client scope) | Audience mapper → console API; scope value `console.demo` | audience must be **scope-gated**, not a client default, or the two-token split is cosmetic |
| `ws-observe` (optional client scope) | Audience mapper → WS API; scope value `ws.observe` | disjoint audience for the header-borne token |
| `svc-console` | confidential, `private_key_jwt` (Signed JWT RS256, fixed `kid`), no client secret | console API → ACL hop |
| `svc-acl` | confidential, `private_key_jwt` RS256 | ACL → processor hop |
| `svc-console-ws` | **deleted**; key and registry row removed | no consumer; identity count back to one subject |
| Demo subject | one configured subject; `sub` equality enforced at `$connect` and at the console authorizer | the only identity in the system |
| `$connect` validation | signature/JWKS, `iss`, `aud`=WS API, `scope` contains `ws.observe`, `azp` ∈ {`console-demo`}, `sub` = demo subject, `exp/nbf/iat` skew, lifetime ≤900 s, exact `Origin`, authorizer cache **0**, echo only the fixed protocol identifier | nine ordered checks; the `azp`+`sub` allowlist — not the audience — is what stops a re-scoped service client |
| Connection record | `{connectionId, epoch, sub, azp, tokenClass=demo-ws, environment, registryVersion, tokenExpiry, leaseExpiry}`; no service-binding field | revocation closes the socket ≤1 heartbeat |
| Logging | exclude `Sec-WebSocket-Protocol` and `Authorization` from CloudFront/API Gateway/WAF log formats; CI asserts the field list | otherwise the bearer credential is persisted at rest |
| IAM | only the notifier role holds `execute-api:ManageConnections` on this API/stage; CI assertion | callback capability separated by credential system |

### 3.3 Two superseded details that must not be carried into the realm

- **Realm SSL mode "External requests"** existed in rev2 solely to permit a loopback HTTP callback from the relay. With the public HTTPS redirect, no loopback URI exists; the realm should require TLS for all external requests and must not retain a loopback exception as dead-but-usable configuration.
- **`--hostname https://auth.<domain>` as a full URL** stays valid, as does the ALB path allowlist (`/realms/<realm>/protocol/openid-connect/*`, `/resources/*`, `/.well-known/*` deny-by-default for `/admin/*`, `/realms/master/*`, `/metrics`, `/health*`).

### 3.4 Evidence needed before issuance may be called reconciled

All eight items below require a **running, pinned Keycloak**. None was executed here, and none can be produced without an authorized issuer run.

| # | Claim to prove | Method (proposed) | Gate |
| --- | --- | --- | --- |
| 1 | Optional scope Audience mapper yields **disjoint** `aud` per token | decode both tokens; assert aud sets do not overlap | G4 |
| 2 | `prompt=none` + PKCE returns a code silently with a live SSO session, and `login_required` cleanly without | two automated authorize runs | G4 |
| 3 | `azp=console-demo` and the demo `sub` appear on the second-authorize token | token inspection | G4 |
| 4 | API Gateway `$connect` receives `Sec-WebSocket-Protocol` intact | handshake through the deployed custom domain; authorizer log record | G4 |
| 5 | JWT fits the subprotocol token grammar and the stack's header length limit | oversized/edge-size handshake cases | G4 |
| 6 | CloudFront / API Gateway / WAF logging can exclude subprotocol and `Authorization` | configure and read back the actual log format; if not provable, take the 30 s single-use ticket fallback (option B) | G4 |
| 7 | Refresh-with-narrowed-scope behaves as expected (only if the silent redirect proves unusable) | token-endpoint probe | G4, conditional |
| 8 | Browser behaviour when the SSO session expires mid-presentation (redirect, state preservation, no token loss) | scripted browser session | G4 |
| 9 | `state` and `nonce` are mandatory in the SPA and code reuse is rejected (ADV-1) | negative cases: replayed code, mismatched `state`, missing `nonce` | G4 |
| 10 | Refresh-token rotation with reuse detection, and issuer-side revocation on logout/session cap (ADV-6) | stolen-refresh-token case after access expiry | G4 |

**Decision rule if claim 1 fails:** the fallback order is (a) a one-time ticket endpoint (rev4 §3.2 option B, which reintroduces an authenticated endpoint), then (b) a single combined-scope token — explicitly **worse**, because the loggable header token would then be payment-capable. Option (b) requires an explicit Security disposition, not a DevOps default.

D-5 remains **OPEN** until Requirements normative adoption, independent domain review and a Security confirmation against a deployed issuer. This section is a proposal, not a closure.

---

## 4. Exact unresolved inputs

Reused recorded facts only. Nothing here is guessed, and no value is invented to make a plan look complete.

| Input | State | Recorded source | What must be supplied | Blocks |
| --- | --- | --- | --- | --- |
| AWS account ID(s) | **NOT RECORDED** | ADR: "comes from GitHub variables" | 12-digit account; confirmation that the simulator uses one account | apply / OIDC trust / backend |
| Region | **RECORDED**: `us-east-2` | ADR 2026-11-20; configuration-schema §5 | confirmation only; plus the `us-east-1` certificate alias for CloudFront | authoring (needs both) |
| Environment name(s) | **NOT RECORDED** (`dev` is illustrative) | configuration-schema §0/§10 | real environment name; it appears in the OIDC `sub` and the realm name `acq-<env>` | apply, OIDC trust |
| Base domain, DNS zone, registrar | **NOT RECORDED** | configuration-schema §10 lists "the four FQDNs and their DNS zones" | domain, hosted-zone ID, delegation authority | certificates, custom domains, console origin |
| FQDNs | **NOT RECORDED** — all four: `console.`, `ws.`, `auth.`, plus the mTLS service domain(s) | rev4 §2.1/§7.2; configuration-schema | exact names | ACM, CSP, `Origin`, redirect URI |
| Terraform state bucket / key / CMK | **NOT RECORDED** | rev2 §9.5; PROJECT_EXECUTION defaults | bucket name, key prefix, KMS key id, backend region, `allowed_account_ids` | remote state, first apply |
| Terraform version on the runner | **NOT RECORDED**; `use_lockfile` needs a recent release and **no Terraform binary exists locally** | rev2 §9.5 open items | pinned version installed and recorded | local validate, CI |
| GitHub OIDC `sub` form | **PARTIALLY RECORDED**: provider/`aud`/no-wildcard/exact-`repo:…:environment:…` shape; the immutable-ID form is **unverified** | rev2 §9.5 | exact subject string as GitHub emits it for this repository | deploy role trust |
| Repository visibility / plan capabilities | **UNVERIFIED** | PROJECT.md | read-only verification via Implementation | OIDC/environment policy |
| Keycloak image digest / release | **NOT SELECTED** | rev2 §2.8 pinning; K-2 rebuild model | digest-pinned official image; the release the rebuild model will target | Keycloak task, D-5 proofs |
| Keycloak DB credential, bootstrap admin, demo subject + password | **NOT RECORDED** | rev4 §4.3 custody obligations | custody location (parameter store / secret), provisioning procedure, demo credential delivery | G4 |
| Client-certificate issuance authority, truststore CA and bundle | **NOT RECORDED** | configuration-schema §10 | CA/issuance owner and bundle | mTLS hops S2/S3 |
| Lambda artifact identity | **NOT BUILT for deployment**; only local `target/lambda` | `docs/aws-operation-a.md` | build in CI, digest/version recorded, environment binding | deploy |
| Cost position | **NO CEILING by owner decision**; on-demand; Keycloak acknowledged as always-running | ADR D2; K-1/K-2 | no input required, but a plan-time cost statement is still owed at G3 | nothing now |
| Deployment authorization | **NOT GRANTED** for provisioning | commission: "deployment execution still requires verified gates and concrete destination/configuration" | explicit scoped approval naming account/region/env after G3 evidence | any apply |
| Publication/merge | **push authorized 2026-11-19 for the task branch; PR/merge NOT authorized** | PROJECT.md | separate approval for any PR/merge | remote pipeline |

Two facts are recorded and must **not** be re-asked: commit identity `Fernando Paz <paz.fernando@gmail.com>` (repository-scoped), and the single-demo-user / public-console direction (K-1, K-2, K-3, P-1).

---

## 5. Gate sequence to a verified deployment

Ordered; each gate names its owner and the evidence that closes it. Nothing here is passed today beyond what is stated.

| Gate | Requirement | Owner | Current state |
| --- | --- | --- | --- |
| **G0 — Contract deltas normative** | Requirements authors D-1…D-9 **plus** ADV-8 additions (requirements preconditions/narrow-demo paragraphs, W-scenario loopback bindings) and ADV-1/ADV-2/ADV-6 authorization details; independent domain review; D-5 reconciled | Requirements + acquiring-domain | Deltas exist as a reviewed proposal (CONDITIONAL on the earlier bundle); **not normative**; D-5 OPEN |
| **G1 — Security design gate** | Rev4 review conditions satisfied: contract deltas authored, D-5 confirmed, ADV-2 retired client resolved | ai-sdlc-security | Rev4 delta: **PASS with conditions**; SR2-01/02 OPEN, SR2-03/04 OPEN/ADVISORY, NV-01..04 PARTIAL. No finding closed |
| **G2 — IaC authored + local validation** | Terraform for the full component set, explicit backend bootstrap, pinned providers, `fmt`/`validate`, lint/policy scan, no secrets, no state committed, plan requires inputs | DevOps (authoring); Implementation executes Git | **Not started**: zero Terraform files exist |
| **G3 — Deployment authorization** | Concrete plan, account/region/env/domain/state identity, artifact digest, cost statement, rollback/cleanup, named approver | Fernando (owner) | **Not requested yet**; requires §4 inputs |
| **G4 — Provision, bootstrap, realm import** | Backend bootstrap first (the backend cannot create its own bucket), then apply; realm import, client/key registration, and the 10 issuance proofs of §3.4 | DevOps + Implementation (authorized execution) | Blocked by G2/G3 |
| **G5 — Independent implementation verification** | QA derives expected results from approved criteria and runs them against the exact artifact/environment; Security reviews the realized boundaries (SR2-01/02 negatives, IAM inventory, revocation, log hygiene) | QA + Security | Not commissioned for an environment; browser evidence today is local-stub only |
| **G6 — Human acceptance** | Fernando accepts the demonstrator on the verified environment | Fernando | Not reached |
| **G7 — Release/rollback disposition** | Rebuild-not-repair procedure exercised; cleanup route for the disposable environment recorded | DevOps + owner | Procedure designed, never exercised |

Cross-cutting rules that stay in force: a plan or a deployment receipt is not a usable environment; a PASS from one gate never substitutes for another; changed interfaces/dependencies/environment invalidate affected evidence and require re-verification rather than reconfirmation.

---

## 6. Bounded next IaC handoff (for a later, separately authorized DevOps pass)

Scope: **author native Terraform and CI definitions only**. No apply, no bootstrap execution, no realm creation, no credential creation, no pipeline run.

Proposed layout (paths only; content in the next pass):

```text
infra/terraform/
  bootstrap/            # S3 state bucket + KMS CMK + versioning/BPA/lockfile prerequisites; own local state, applied once by a human
  modules/network/      # VPC, private subnets, security groups, endpoints as needed
  modules/keycloak/     # single task, Aurora Serverless v2, internal ALB + ACM, path allowlist, SSM operator path
  modules/console/      # private S3 + OAC + CloudFront (+ us-east-1 cert alias), WAF, response-headers CSP, /config.json object
  modules/api/          # HTTP API, JWT authorizer (scope/sub/aud), console Lambda, route throttles, access-log field exclusions
  modules/mtls/         # custom domains, truststores, default-endpoint disable, authorizer cache 0
  modules/ws/           # WebSocket API, $connect authorizer, custom domain, connections table, notifier + ManageConnections scope
  modules/data/         # registry, journal, event store, connection tables; TTL mapping; 7-day logical expiry inputs
  modules/observability/# log groups (7-day), alarms, structured-log field policy
  modules/identity/     # per-function roles, explicit-deny policies, OIDC provider + deploy role (exact sub, no wildcard)
  envs/<env>/           # wiring + variables; every unresolved input declared, none defaulted to a invented value
```

Hard constraints for that pass (from PROJECT_EXECUTION defaults and the carried findings):

- State is private, encrypted, versioned, Block Public Access, `use_lockfile`, `allowed_account_ids`, environment-separated keys; **bootstrap is a separate explicit step**.
- No secret in state, plan, artifact, image, task definition, log or static bundle; no `data "aws_ssm_parameter"` with decryption in root config; no output referencing a private key.
- `authorizer_result_ttl_in_seconds = 0` on every service authorizer; no `aws_lambda_function_url`; default `execute-api` endpoint disabled on every mTLS custom domain.
- CI: `uses:` pinned to 40-hex SHAs, `id-token: write` only on the deploy job, OIDC `sub` exact, deploy stage human-gated and never auto-run.
- Every §4 unknown stays a required variable/declared input; the plan must fail closed rather than default.
- Preserve `infra/template.yaml` as historical reference and record a component-by-component supersession map once Terraform parity exists.

Exit criteria for that pass: `terraform fmt -check` and `terraform validate` (pinned version) with recorded output; a lint/policy scan with a pinned tool and version; a static check that no credential, account number, key or secret literal appears in the tree; a written list of remaining inputs; and an explicit statement that no plan was applied.

---

## 7. Actual commands, results and exclusions

Executed (read-only, local):

| Command | Purpose | Result |
| --- | --- | --- |
| `python3` SHA-256 recomputation over `inputs.json` | input integrity | 11/11 MATCH |
| `sha256sum` over design/infra/console files | pin additional sources | hashes recorded in §1.2 |
| `find … -name '*.tf' -o -name '*.yml' -o -name 'Dockerfile*'` | IaC/CI inventory | only `infra/template.yaml` |
| `ls .github` | CI presence | does not exist |
| `grep`/`sed` over design-rev2/rev4/D-5/owner-decisions/WORK_ITEM/delivery-status | reconstruct design state and gates | cited inline |
| `command -v terraform tflint checkov trivy java mvn jq yq aws docker` | local tool availability | terraform/tflint/checkov/trivy/java/mvn/jq/yq **NOT FOUND**; `aws`, `docker`, `node`, `python3` present but **not invoked against any cloud** |

Not executed, deliberately: no Git/`gh`/provider command; no `aws` API call or login; no Keycloak call or login; no credential flow; no Terraform init/plan/apply (no binary, and no authority); no build or test run (not this commission); no pipeline run; no provisioning; no deployment; no publication; no external message; no file written outside this directory.

Local-only limitation to state plainly: because Terraform is not installed on this host, even the future `fmt`/`validate` evidence cannot be produced here without a pinning/installation step, which is an authoring-environment decision for the next pass.

---

## 8. Summary of readiness

- **Ready:** the documentary basis is complete and internally hashed; the deployment topology, the client/issuer shape and the gate order are each specified in a way that can be built without further owner discovery.
- **Not ready:** there is **no Terraform and no CI at all**, the contract deltas are proposals rather than normative text, D-5 remains open, the four FQDNs/account/environment/state identities are unrecorded, and the Keycloak issuance proofs require a running issuer.
- **Therefore:** deployment is correctly described as *preparable but not yet executable*. The next productive step is G0 + G2 in parallel (Requirements authors the deltas; DevOps authors Terraform/CI against declared inputs), with G3 authorization requested only after G2 exit criteria and the §4 input set are complete.

## 9. Output inventory

This directory contains `PROGRESS.md` (written first) and this `devops-report.md`. No approved file, contract, review, application source or `WORK_ITEM.md` was modified. Content hashes for these two files are recorded in the adjacent `SHA256SUMS` sidecar, which excludes its own digest.
