# Operation A — access realization, revision 3 (provenance, effective restriction, rotation minima)

Status: **PROPOSAL, revision 3 — correction pass**. Documentary only. Not approved architecture, not IaC, not deployment, not a Security verdict, not acceptance, and it closes nothing: the independent review's verdict on rev2 is **FAIL** with SR2-01 and SR2-02 open and SR2-03/SR2-04 advisory, and this document is the input to a further review, not its outcome.

Author: ai-sdlc-devops. Date: 2026-11-22 (America/Bogota). Runtime observed: native OpenClaw subagent (`ai-sdlc-devops`); session model metadata `nan/deepseek-v4-flash`; configured default; **no model override**; provider-qualified identifier not exposed and not invented. No delegation.

Earlier artifacts are preserved untouched: `design.md`, `configuration-schema.md`, `design-rev2.md` and `evidence-rev2/` were not edited; this revision adds `design-rev3.md` and `evidence-rev3/` only.

## 0. Inputs, integrity and what this pass corrects

| Input | Expected | Observed | Result |
| --- | --- | --- | --- |
| `reviews/access-design-rev2-20261122/security-review.md` (commissioned) | `b74e5a686525a1c2ceff6713e23e938f4e54f0538a9587240ce8307d1dc38120` | same | **MATCH** |
| `design/access-realization/design-rev2.md` | `6b15161875e702ebae74e4f76b4b94cae9ffbbb94d6a9fcd42aee563b8f3873c` | same | **MATCH**, untouched |
| `design/access-realization/compatibility-assessment-rev2.md` | `2f7cfe054aae7d908948288b6e67799aef7c864e1acaeb67c7444e63c432b930` | same | **MATCH** |
| `design/access-realization/evidence-rev2-assessment/manifest.json` | `48b8b1faffeb2fb9db7272e834ccb4921607fda3d327859cb68f766a018c2454` | same | **MATCH** |
| `reviews/access-design-rev2-20261121/security-review.md` | `6b964f0520daa181edc96f1553c5fb990000113ed3c28560bdfc67cace310a31` | same | **MATCH**, supplementary only; not used as authority |
| `owner-decisions.md` | — | `0368976f5b1efc557aa0729a59054023ca1a2761e5b1a141544746e5b4073f29` | read; matches the same identity recorded in the commissioned review |
| `evidence/t1-traceability-20261120/BINDING.txt` | — | `e4951cc2…` | branch `feat/wi-acq-auth-001`, HEAD `cfc456f4…`; dirty tree per Implementation evidence |

The supplementary 20261121 review differs from the commissioned one on material points (it conflated issuer and assertion key caching, and asserted a cold-start caller cache that the current source contradicts). Only the commissioned review and the current source are treated as authority here; the divergences are recorded in §6 and in the evidence file.

### 0.1 What changed, and why

| # | Correction | rev2 said | rev3 says | Why |
| --- | --- | --- | --- | --- |
| C-1 | Late-evidence sender provenance (**SR2-01**) | identity derived from the Lambda invocation context's caller role; one admission function | **one admission function per delivering principal**, distinct execution role, no shared handler; sender id resolved from the function's own non-forgeable identity through the server-side registry | `Context.getInvokedFunctionArn()` is the *target* function ARN; a shared handler cannot see the caller role, and a role ARN cannot pass the 64-char profile-alphabet sender field |
| C-2 | Invocation restriction (**SR2-02**) | resource-policy allowlist "denies every unlisted role"; `SourceArn`/`SourceAccount` as direct-invoke provenance | explicit **Deny** statements plus per-producer function identity, identity-policy narrowing, role-trust and boundary/SCP analysis, alias/qualified-ARN handling, and an explicit statement that "no public HTTP route" ≠ "no externally possible SDK invoke" | same-account identity and resource allows combine by union; absence from an allow list is not a deny; source-context keys are service-broker controls |
| C-3 | Rotation overlap (**SR2-03**) | "an overlap window" | numeric minima **per key class**: assertion keys ≥24 h, issuer signing keys ≥24 h (48 h recommended), TLS truststore ≥24 h plus validity margin, C4 evidence keys ≥7 days verify-window (floor 24 h only with a confirmed short late horizon) | each class converges on a different horizon; a blanket 24 h was both dropped in prose and still present in the carried schema |
| C-4 | Presenter relay wording | "no evidence or payment authority attached to that client" | `console.write` **can initiate an authorized synthetic payment** through mTLS + demo token + mandate checks; it has **no C4 signing** and **no direct issuer-decision** authority | the credential genuinely can start a payment; denying that in prose understated the blast radius |
| C-5 | WebSocket mid-pass and reconnect | "cursor coincides with the lease"; "every reconnect re-mints" | cursor can outlive the socket; explicit mid-pass termination and two compliant continuations, plus bounded reconnect/issuance/frame rates | a 900 s lease turns long passes and reconnect storms into a self-inflicted DoS surface if left undefined |
| C-6 | `ACL_AUTH` semantics | "signs the instruction-side attestation" | `ACL_AUTH` is a **transport/client identity with no evidence-signing authority** | the enum's javadoc and the verifier's row map withhold signing from `ACL_AUTH` |
| C-7 | Registry caching claim | "current code caches the registry at cold start with no TTL" | current HTTP path performs a **strongly consistent read per matched request**; the missing pieces are WS revalidation and status/version fields | `AwsRuntime.registry(...)` issues `GetItem … consistentRead(true)` on each call; the static holder caches the DynamoDB client, not the caller row |
| C-8 | Payload margin | "all-maxima" rows 503/738/1 288/3 606/4 156 | keep the **bound (8 192 B) and the conservative guard (6 964 B)**; the five "all-maxima" figures are withdrawn as fixture-sized; achievable maxima recomputed at 5 684/5 940 B; the header question is ~4 KB, not ~1.7 KB | Implementation's independent recomputation showed the five figures were below the empty-value floor of the key set |
| C-9 | Diagnostic forwarding reachability | listed as a realized S5i element | forwarding is honoured only by a path that performs `verifyForwarded`; the signed admission facade refuses diagnostic senders, so end-to-end support is **not claimed** until a composition is chosen | a permission that exists but cannot be reached through the only facade is not a working control |
| C-10 | Cursor/lease equality claim | "coincides exactly with the lease" | same nominal TTL, **not** the same instant; re-mint at every reconnect and at any pass boundary where the cursor is at or near expiry | mint time and connect time differ |

K-1, K-2 and K-3 are being put to Fernando now. This revision is written **under the recommended defaults** (single Fargate task; product owner accountable with a DevOps-authored runbook and no on-call; presenter-local relay key accepted with narrow scopes and rotation) and says nothing that depends on the alternatives.

## 1. SR2-01 — structurally exclusive sender provenance for late evidence

### 1.1 The defect, restated exactly

A shared admission function cannot learn its caller's IAM role: `Context.getInvokedFunctionArn()` returns the **invoked function's** ARN, `getIdentity()` is mobile Cognito identity and `getClientContext()` is client-supplied. `GetCallerIdentity` inside the handler returns the handler's own execution role. Additionally, a role ARN contains `/` and is frequently longer than 64 characters, so it cannot populate `C4AuthenticatedTransportContext.authenticatedSender`, which enforces 1–64 characters of `[A-Za-z0-9._:-]`. The penetration point is therefore structural, not a missing parameter.

### 1.2 Options evaluated

| Option | How it would work | Assessment |
| --- | --- | --- |
| **A. One admission function per delivering principal, own execution role, no shared handler (chosen)** | The function's own ARN is the provenance. Each function has a dedicated handler class, a dedicated execution role, and a registry record mapping function name → short sender id → role/keys/mandate | **Chosen.** Provenance is a platform fact that the caller cannot select, cannot spoof in a payload, and cannot obtain from another function's code path; mapping to the short id is a server-side registry lookup; blast radius per role is one function |
| B. One function, resource policy allowlist plus explicit deny | A single handler that "knows" the sender from policy configuration | Rejected as the primary mechanism: even with a perfect deny list, the surviving caller set still contains more than one sender, and the handler cannot tell which one invoked it. A deny list narrows *who may call*, never *who did call* |
| C. Authenticating broker | A broker authenticates producers and forwards a trusted identity to one admission function | Rejected: the broker must itself authenticate the producer, which reproduces the same problem one hop earlier, and adds a new always-on or invoked component plus its own credentials. It buys nothing over A for two senders |
| D. Genuinely private network path (interface endpoint, PrivateLink-style) | Keep one function and restrict network origin | Rejected as primary: Lambda `Invoke` is an AWS HTTPS API; a network path does not create identity, and a stolen permitted credential can invoke from outside any VPC. Also unverified for this workload. Useful only as defence in depth |
| E. Caller-produced signature on the late path | Producer signs a delivery token with a second credential | Rejected: it re-introduces a credential on the path that CA-1 deliberately kept credential-free, duplicates the C4 signature, and would tempt implementations to trust a payload field |

Option A also resolves the second half of the defect: the sender id becomes a short, stable, registry-assigned label, never an ARN.

### 1.3 Concrete realization

Three functions, derived from the **sending principals**, not from the role enum:

| Function | Handler | Execution role | Registry sender id | C4 role | Permitted mode | Key ids | Diagnostic permission |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `late-admit-processor` | processor admission handler only | `role/late-admit-processor-exec` | `SND-PROC-01` | `PROCESSOR_AUTH` | `DIRECT` only | processor key ids | no |
| `late-admit-network` | network admission handler only | `role/late-admit-network-exec` | `SND-NET-01` | `NETWORK_SIM` | `RELAY` only | network key ids | no |
| `late-forward-diagnostic` | forwarding handler only (no decision path import) | `role/late-forward-diag-exec` | `SND-DIAG-01` | **none** | forward-unchanged only | none (never signs) | `diagnosticForwardPermitted(env, SND-DIAG-01, acquirerRef)` |

`ISSUER_SIM` has **no delivery function**: the issuer attestation arrives nested inside the network's RELAY envelope and is verified as the inner signature. If a future direct issuer delivery is ever specified, it gets its own function and its own sender id — the rule is "one function per sender", never "one function per role enum".

### 1.4 Resolution order inside the handler (no payload identity, ever)

1. Read the function's **own** identity: `System.getenv("AWS_LAMBDA_FUNCTION_NAME")` cross-checked against `context.getFunctionName()`. This is platform-provided and not caller-controllable.
2. Registry lookup `ENV#<env>#SENDER#<functionName>` → `{senderId, role, allowedModes, allowedKeyIds, diagnosticScope?, version, status}`. Unknown function name, blank sender id, `status != ACTIVE` → **deny before parsing the payload**.
3. Reject if the envelope's `mode` is not in `allowedModes` (a `RELAY` envelope at the processor function is refused, and vice versa).
4. Build `C4AuthenticatedTransportContext(environmentId, senderId)` — the sender id is at most 32 characters of `[A-Z0-9-]`, so it passes the existing 64-character profile-alphabet check without widening that field.
5. Verify the C4 envelope unchanged: bounds → canonical encodings → `keyId` in `allowedKeyIds` → Ed25519 signature → `instructionSha256`/`responseSha256` → role/P4 row → business-version correlation.
6. Only then admit (P5/P6), with the atomic gateways **still unwired** (CA-4), so no deployed-atomic claim is made.
7. Any payload field named `sender`, `principal`, `role` or `functionArn` is ignored; if a future contract adds one, it is rejected as an unknown key by the closed parser.

The `authenticatedSender` value is thus a **registry-assigned label for a platform-fixed function identity**, which is the narrowest form of "transport-authenticated sender" that this runtime can actually supply.

### 1.5 Denial cases that must be tested (obligations, not executed)

1. Invoke `late-admit-processor` as the network role → denied by policy; if it were somehow permitted, the function's registry row still yields `PROCESSOR_AUTH` and a RELAY envelope is refused by mode.
2. Invoke `late-admit-network` as the processor role → denied; a DIRECT envelope would be refused by mode even if invoked.
3. Invoke with a **qualified** ARN (version or alias) when the policy names the unqualified ARN → denied; and with an alias created after the fact → denied by the qualifier rules in §2.
4. Invoke from a second same-account role that has its own identity `Allow` → denied by the explicit Deny in the function policy (this is the SR2-02 case).
5. Invoke from a foreign account → denied (principal account condition + deny).
6. Invoke the diagnostic function with a decision-bearing envelope → refused; forward an unchanged proof → accepted with no outcome authority.
7. Payload claiming `sender=SND-NET-01` while invoking `late-admit-processor` → the claim is ignored; the envelope's relay structure fails processor verification.
8. Processor function receiving a `RELAY` envelope, or network function receiving `DIRECT` → refused before signature work.
9. `keyId` not in the function's allowed set → refused; `keyId` of another principal → refused.
10. Altered body, mismatched hashes, missing signature, expired envelope → refused before admission.
11. No handler execution on any denied invocation (verified by absence of logs/metrics for the request id).
12. Attempt to reach either function over HTTP or a URL → no route and no function URL exists; proven by deployed inventory, not by reading one template.

## 2. SR2-02 — an effective restriction that actually holds

### 2.1 The evaluation fact

Same-account policy evaluation combines identity-based and resource-based **allows by union**. A resource policy that lists one principal does not deny another principal that is allowed by its own identity policy. Any design that reads "not in the allow list" as "denied" is wrong, and rev2's §4.2(1) did exactly that.

### 2.2 Stacked controls, each doing a distinct job

| Layer | Control | What it actually guarantees |
| --- | --- | --- |
| 1. Identity | Each producer role's policy grants `lambda:InvokeFunction` on exactly one function ARN, unqualified, no wildcard, no `lambda:InvokeAsync`, **conditioned on `ArnEquals {"lambda:SourceFunctionArn": "<producer function ARN>"}`** | Limits the producer to one target, and — unlike a plain grant — the permission does not apply when the same role's credentials are used outside a Lambda execution environment |
| 2. Resource **Deny** | Each admission function policy contains `Deny` for `lambda:InvokeFunction` to `Principal: "*"` with `ArnNotEquals {"aws:PrincipalArn": "<allowed role ARN>"}`, plus `StringNotEquals {"aws:PrincipalAccount": "<account>"}` | Overrides **every** identity allow, including same-account roles the design does not know about; this is the layer that closes the union gap |
| 3. Structure | One function and one execution role per sender, distinct handler classes, no shared handler import | Even a policy mistake cannot produce sender confusion: a compromiser of function A cannot make A report sender B |
| 4. Role trust | Execution roles trust only `lambda.amazonaws.com` with `aws:SourceAccount`/`aws:SourceArn` conditions naming the specific function | Prevents creating a new function that assumes an existing admission role |
| 5. Boundaries/SCP | Permissions boundary on producer and admission roles limited to logs plus the enumerated DynamoDB/SSM actions; if the account belongs to an Organization, an SCP denying `lambda:InvokeFunction` on these ARNs except from the enumerated roles | A boundary is an **intersection** (documented), so it cannot be bypassed by a later identity grant. The SCP case is weaker than it looks: the documented intersection applies to requests against resources **without** a resource-based policy, and these functions do have one, so SCP interaction must be verified before it is relied on. SCPs do not constrain a management account |
| 6. Inventory | CI plus a later authorized IAM inventory: enumerate every principal with `lambda:InvokeFunction` on these ARNs and assert the set equals the intended set; Access Analyzer for external access | Detects drift that policy review alone misses |
| 7. Rate/shape | Reserved concurrency on each admission function; envelope bounds before parse; delivery-receipt dedupe | Bounds abuse volume even by an authorized principal |

### 2.3 Qualified versus unqualified ARNs and aliases

- IAM authorizes a **qualified** ARN (`function:name:version`, `function:name:alias`) as a distinct resource from the unqualified one. A grant for the unqualified ARN does not authorize invoking an alias, and vice versa.
- Therefore: **no versions or aliases are published for the admission functions**; the code invokes the unqualified ARN; CI asserts the template declares no `AutoPublishAlias`, no `AWS::Lambda::Alias`, and no `AWS::Lambda::Version` for these functions.
- If a qualified invocation is ever required, the deny in layer 2 must be replicated for each qualified ARN (a resource-policy Deny is attached to the function and the qualifier is part of the authorization resource — the exact propagation must be verified on deployment, not assumed), and the allow must name the same qualifier. Until verified, the answer is "not supported".
- Aliases are client-selected targets: an alias is never used as a security boundary, because the caller chooses it.

### 2.4 "No public HTTP route" versus "no externally possible SDK invoke"

These are different claims and rev2 blurred them.

- **True and verifiable now:** the SAM template has no late-evidence route, no function URL and no API mapping; the design adds none. There is no anonymous internet submission path.
- **Not true, and not claimed:** `lambda:InvokeFunction` is an AWS API. A principal holding valid temporary credentials for the permitted role can reach the API from outside the account's network, from a laptop, or from another cloud. No VPC placement, endpoint policy or missing DNS record changes that.
- **What narrows it materially:** Lambda injects `lambda:SourceFunctionArn` into the request context of AWS API calls made from within a function's execution environment, and that key is usable in **identity-based policies or SCPs** — not in resource policies — with the unqualified function ARN (`https://docs.aws.amazon.com/lambda/latest/dg/permissions-source-function-arn.html`, fetched 2026-11-22T23:45:32Z). Conditioning the producer's invoke grant on `ArnEquals {"lambda:SourceFunctionArn": "<producer function ARN>"}` therefore means a stolen producer credential used from a laptop, a CI runner or another cloud **does not satisfy the grant** and is denied. The unqualified-ARN restriction also aligns with the no-alias rule in §2.3.
- **Residual after that control:** code running inside a Lambda function that has assumed the producer role, or compromise of the producer function itself, can still invoke — and then it is genuinely that sender, limited to its own identity, and still bound by the C4 signature, dedupe and business-version correlation. The residual is abuse of the admission resource by the compromised producer, not impersonation of another sender and not fabrication of an outcome. That residual is stated, not waived.
- Controls that would narrow network origin further (`aws:SourceVpce`, VPC endpoint policies, `aws:SourceIp`) are recorded as **unverified** for `lambda:InvokeFunction` and are not relied on. `aws:SourceArn`/`aws:SourceAccount` are service-principal context keys — the IAM global-keys page states `aws:SourceAccount` is only available when the call is made directly by an AWS service principal — so using them as direct-invoke provenance was rev2's error and is removed.
- The `lambda:SourceFunctionArn` behaviour is documented but must be **confirmed on the deployed function pair** (the specific `lambda:InvokeFunction` action, and behaviour when the producer is invoked through a version or an alias), because the documentation states the key does not support versions or aliases. Until confirmed, the condition is a design requirement, not a demonstrated control.

### 2.5 Required evidence before this control can be called implemented

A static target→principal→key matrix; a policy-evaluation trace for the same-account "other role" case; confirmation that `aws:PrincipalArn` in a Lambda resource policy resolves to the role ARN for assumed-role sessions (documented for the condition key generally, to be confirmed on a Lambda resource policy); confirmation of qualified-ARN behaviour; confirmation that `lambda:SourceFunctionArn` is present for a Lambda-to-Lambda `Invoke` from the producer function and absent when the same credentials are used outside a function; and an IAM inventory of invoke-capable principals. None of this exists yet.

## 3. SR2-03 — rotation overlap, restored per key class

Rev2's replacement paragraph said only "an overlap window", while the carried v1 files still demanded ≥24 h. That ambiguity is removed: the following minima are explicit, class-specific, and **supersede** v1's single blanket 24 h for assertion keys while still satisfying it as a floor everywhere.

| Key class | What has to converge before the old key can go | Minimum overlap | Rationale (not copied from v1) |
| --- | --- | --- | --- |
| **Client assertion keys registered at Keycloak** | The new public key must be accepted for the client, and every signer instance must be signing with it, before the old key is removed | **≥24 h** | Convergence is driven by deployment/restart of the signing services, not by a cache. 24 h covers at least one full deploy cycle plus a business day of observation. If the deployed release cannot hold two assertion keys for one client (unverified), the replacement is **a second client** (`svc-x-v2`) with the new key, both enabled ≥24 h, cut over, then the old client disabled — the numeric minimum is preserved rather than quietly shortened |
| **Keycloak issuer signing keys** (access tokens verified at the Gateway or authorizer) | New public key must be in the issuer JWKS and cached by every verifier; every token signed by the old key must have expired | **≥24 h** (48 h recommended) | Gateway caches issuer keys for up to 2 h on a best-effort basis; max token lifetime is 900 s; clock skew 60 s. The theoretical floor is ~2.5 h, so 24 h is a margin for cache-refresh failure and repeated deployments, not an arbitrary number. Removing a key from JWKS is not revocation of already-issued tokens |
| **TLS truststore / client certificates** (mTLS custom domains) | New CA/certificate trusted by the truststore version in use; clients presenting it; old certificate still trusted during cutover | **≥24 h**, and renew with **≥30 days** validity remaining | The truststore version is pinned and changes only when the domain is updated; an overlap shorter than a deploy/rollback cycle risks a self-inflicted outage. API Gateway does not check revocation, so revocation is a registry action, not a truststore action |
| **C4 Ed25519 evidence keys** | Signing switches immediately; **verification** must keep accepting the old key for as long as a legitimate late delivery can still arrive | **≥7 days verify-window** (floor 24 h only if Requirements confirms a shorter late-delivery horizon) | Evidence is verified at admission and may be re-verified during audit; delivery can be delayed by retries or outage. The horizon is a business/retention question (NV-05), so this is a proposal aligned to the seven-day functional retention and marked for Requirements confirmation |

Precedence and general rules:

- No rotation shortens a class below its stated minimum; shortening any of them requires a measured result and an independent review, not a prose edit.
- Overlap is not a substitute for revocation: a compromised key is removed immediately and the registry marks the client/principal `REVOKED`; the overlap applies to planned rotation only.
- Warm instances that keep signing with a retired key are the failure mode; each rotation runbook must show the old key no longer used by any signer before removal, and the deployment must be a rolling restart with verification, not in-place mutation.
- Each class is measured independently: assertion-key rotation is proven by a token exchange per client; issuer-key rotation by a normal request during and after the change; TLS rotation by a handshake with old and new certificates; C4 rotation by accepting an old-key delivery inside the window and rejecting it after.

## 4. Presenter relay capability — accurate wording

`svc-console-relay` holds `console.write`. That scope **can initiate an authorized synthetic payment**: it calls the console HTTP route over mTLS with the demo token and the fixed synthetic mandate, and if every check passes the processor will create a claim and dispatch. Saying it has "no payment authority" was wrong and is withdrawn.

What the relay key can and cannot do, stated precisely:

| Can | Cannot |
| --- | --- |
| Submit a validated synthetic authorization through the mTLS + demo-token + mandate path, subject to the existing idempotency, claim and no-resend rules | Sign C4 evidence: no Ed25519 key, no `keyId`, no attestation authority |
| Read console detail/history for its permitted synthetic context | Decide or observe issuer truth directly; it has no issuer-diagnostics route and no issuer table access |
| Open an observation socket within `ws.observe` | Change a business outcome, resend, reverse, or bypass the mandate |

Consequences treated as real: the relay key is a **payment-initiation credential** and is handled with that severity — narrow scopes, per-demo rotation, operator-only file permissions, immediate registry revocation on loss, and no reuse outside the console audience. The Keycloak client is not privileged beyond the three scopes; the mandate still comes from the simulator registry.

## 5. WebSocket obligations — mid-pass, re-synchronization, reconnect and rate control

The owner-accepted no-mTLS decision is unchanged. What follows only makes the shorter lease operable without turning expiry into a denial-of-service lever.

### 5.1 Corrected facts

- Effective lease = `min(jwtExp, connectTime + 900 s)` = `jwtExp`, ≤900 s. The connection also ends no later than the **demo** token's expiry, and immediately when the registry marks the client, subject or mandate invalid.
- The cursor has a nominal 900 s TTL minted at issue and **may outlive the socket**; the rev2 sentence "coincides exactly with the lease" is withdrawn. What is true is that every reconnect re-mints, because the socket is gone before a stale cursor would be reused.

### 5.2 Mid-pass behaviour

A catch-up pass that outlives one token window must not continue on an expiring cursor, and must not silently extend the old snapshot.

1. Before each page, compare the cursor's remaining lifetime with a conservative estimate of one page round trip; when remaining lifetime is inside that margin, **stop after the current page** and mark the pass interrupted (visible in the UI as lag, not as completeness).
2. Continuation has exactly two compliant shapes:
   - **Default, requires no new contract:** abandon the pass and request a **new snapshot** (`snapshot=new`) from the verified contiguous watermark; the existing initial-call semantics apply, bounds ≤100 events/256 KiB per page and ≤1000 events/1 MiB per pass, and the new pass reports its own `snapshotHead`, `expiryRevision`, `firstRetainedSequence`, gaps and truncation. Lag and any skipped range are displayed.
   - **Optional, requires a contract delta:** an explicitly reviewed continuation operation that re-mints a cursor for the **same** snapshot generation at the last committed contiguous sequence. It must carry the same `H`/`R` binding, must not transplant a snapshot across generations, and must return `410 RESYNC_REQUIRED` semantics when revision or generation changed. This is authored by Requirements, not invented here; until then only the default applies.
3. Never: extend an old snapshot's `H`, reuse a cursor after a generation change, treat a partial pass as `completeThroughSnapshot`, or convert any of this into a payment retry.

### 5.3 Reconnect and issuance bounds (DoS controls)

| Control | Proposal |
| --- | --- |
| Reconnect jitter | 1/2/5/10/30 s exponential with full jitter (O6 already proposes these values); no tight loop |
| Token acquisition | Single-flight per client: one in-flight token request per client, others wait; no request per reconnect attempt |
| Issuer failure backoff | Exponential with cap; after N consecutive failures show session-expired/limited and stop; never bypass the issuer |
| Connection attempts | ≤6 per minute per client; excess refused locally at the relay before any handshake |
| Frames | ≤10 subscribe/unsubscribe per minute per connection; sync/heartbeat ≤1 per 30 s per connection; oversized or unknown frames dropped without closing a healthy connection |
| Server fanout | Existing bounded buffers (≤4096 B frames, ≤1000 events/1 MiB client buffer); disconnect and require resync rather than queue |
| Concurrency | Reserved concurrency on the `$connect` authorizer and the WS integration so an expiry-aligned reconnect wave cannot starve payment-path functions |
| Alignment | Jitter the server-side close by a few seconds per connection when many were opened together, so a cohort does not expire in the same second |
| Accounting | Count reconnect attempts, token requests, refusals and authorizer throttles; a wave is visible, not silent |

These are technical bounds, not business quotas: none of them gates or retries a payment, and a refused socket never changes an outcome.

### 5.4 Revocation obligation left open by rev2 (recorded, not solved)

Per-frame revalidation needs a **status-bearing** registry record and a server-side scheduler for idle connections. The current HTTP path already performs a strongly consistent read per matched request, which is the right starting point; what is missing is the status/version fields, the WS revalidation path, and a scheduler that checks idle sockets. Until those exist, "closed at the next heartbeat" is a requirement, not a delivered property, and Security's ≤30 s bound must be tested immediately before and after the cache/heartbeat boundary, with a silent-client case and a registry-failure case.

## 6. Carry-forward corrections (SR2-04 and the review's other precision points)

- **`ACL_AUTH` is not a signing principal.** It authenticates the channel client as a transport peer and has no evidence-signing row. The channel ACL therefore does **not** sign evidence; the instruction-side attestation is the processor's. The rev2 sentence is withdrawn; the mapping keeps `ACL → ACL_AUTH` only as a transport identity.
- **Registry reads are already strongly consistent per request.** `AwsRuntime.registry(...)` issues `GetItem` with `consistentRead(true)` on each call; the static holder caches the SDK client, not caller data. The missing WS work is the revalidation path and status fields, not a cache TTL. The same correction applies to the earlier assessments' cold-start-cache claim, which this document does not repeat.
- **Payload margin numbers.** The bound stays 8 192 B and the conservative guard stays 6 964 B (cap-conditional and exact). The five "all-maxima" rows are withdrawn: recomputation shows they are below the empty-value floor of the 31-key attestation. Achievable maxima are 5 684 B (no escaping) and 5 940 B (escaping maxima), so the real headroom is ~2.5 KB, not ~1.2 KB; the guard remains the stricter check and is kept deliberately. The normal-response **single-header** question is ~4 KB at true maxima, roughly twice the figure rev2 used — still unverified against any published quota, and still the only unverified size constraint. Platform headers remain additive.
- **Diagnostic forwarding reachability.** The permission is honoured only where `verifyForwarded` runs; the signed admission facade refuses diagnostic senders, and the gateway that takes a pre-verified reply performs no C4 verification. The design therefore routes diagnostic forwarding through `late-forward-diagnostic` → forwarding composition, and explicitly does **not** claim that `SignedEvidenceAdmissionService` admits diagnostic senders. Choosing the composition is required work.
- **Cursor/lease equality.** Withdrawn as an equality claim; see §5.1.
- **Pinned-text staleness (AD-01).** Beyond C6/O4, `observation-contract.md` still carries a 30-minute technical lease and the recorded D-D text names `min(JWT expiry, 30 minutes)`. The outcome is numerically compatible (`min(jwtExp,30 min) = jwtExp`), but the contract delta must cover the lease number, the cursor/lease distinction and the retention cursor implications — through Requirements, not by editing pinned text here.

## 7. Carried forward unchanged

- **Keycloak hosting profile** (rev2 §2): ECS Fargate single task + Aurora Serverless v2 PostgreSQL 17.x, private subnets, public ALB with path/host allowlist and WAF, admin via SSM port forwarding, realm per environment, four clients, Signed JWT/RS256, no client secrets, explicit audience mappers, 900 s tokens, out-of-band keys, backup/patch/availability ownership. K-1/K-2/K-3 defaults applied.
- **Internal-only late evidence**: no public route, no function URL, no OAuth credential on the evidence path.
- **Role vocabulary**: `ISSUER_SIM`, `PROCESSOR_AUTH`, `NETWORK_SIM`, `ACL_AUTH` with the §6 correction; `DIAGNOSTIC` remains a scoped permission, never a role.
- **Payload margin control**: 8 192 B bound, 6 964 B guard, two-number CI check (cap-conditional container and declared-max envelope), no silent field additions.
- **OIDC and state-backend schema** (rev2 §9.5): `token.actions.githubusercontent.com`, `aud=sts.amazonaws.com`, exact `sub`, no wildcards, `id-token: write` on the deploy job only, pinned actions; S3 backend in `us-east-2` with `use_lockfile`, versioning, SSE-KMS, Block Public Access, `allowed_account_ids`, explicit bootstrap.
- **Unverified list** carried from rev2 §12 with additions in §8.3.

## 8. Finding → section map, and what remains open

### 8.1 Commissioned findings

| Finding | Where answered | Status after this document |
| --- | --- | --- |
| **SR2-01** (IMPORTANT) sender provenance | §1 (options, chosen mechanism, mapping, 12 denial cases) | **OPEN** — design defect answered; implemented/executed evidence absent; no closure claimed |
| **SR2-02** (IMPORTANT) allowlist is not a deny; private claim | §2 (stacked controls, deny semantics, qualified ARNs, HTTP-vs-SDK distinction) | **OPEN** — answered in design; policy evaluation and inventory not executed |
| **SR2-03** (ADVISORY) rotation overlap | §3 (numeric minima per class, replacement for dual-key limitation, precedence) | **OPEN** — answered; no rotation exercised |
| **SR2-04** (ADVISORY) analytical corrections / overlay gaps | §6 (cache claim, ACL_AUTH, payload rows, diagnostic reachability, cursor, pinned-text staleness) | **OPEN** — corrections made here; contract delta and reviewers' own documents remain theirs |
| Review's relay-wording defect | §4 | **OPEN** — wording corrected; custody severity restated |
| Review's WS availability/DoS and mid-pass gap | §5 | **OPEN** — obligations defined; no implementation or measurement |
| NV-01 / NV-02 / NV-03 / NV-04 | rev2 §§2, 4, 8 plus this §1–§5 | unchanged: **PARTIAL**, none closed |
| NV-05 / AD-01 | outside this commission; `design/retention/`, Requirements | unchanged |

### 8.2 What this revision does not claim

No finding is closed, no risk is accepted on the owner's behalf, no gate exception is requested, and no deployed behaviour — atomicity, revocation latency, rotation overlap, or absence of an external invoke path — is asserted.

### 8.3 Unverified items (rev2 list plus additions)

Carried: Keycloak default token lifespan and per-client overrides; deployed signature-algorithm allowlist; advertised `token_endpoint_auth_method`; not-before semantics and JWKS convergence; ALB-to-Keycloak proxy-header trust; SSM port forwarding availability; Aurora/Fargate sizing and cost; boot-time realm import on upgrade; Keycloak version pin; presenter key file protection; Lambda policy limits; absence of an internet-reachable evidence path (design assertion only).

Added by this revision:

1. Whether `aws:PrincipalArn` in a Lambda resource policy evaluates to the role ARN (not the STS session ARN) for assumed-role sessions — documented for the condition key generally, but the explicit deny depends on it here.
2. Whether a resource-policy Deny propagates to qualified ARNs (versions/aliases) or must be replicated.
3. Whether `lambda:SourceFunctionArn` is present on a Lambda-to-Lambda `Invoke` and absent when the same role's credentials are used outside a function; and how it behaves when the producer is invoked with a version or alias (documentation says it does not support them).
4. Whether the account belongs to an AWS Organization, and whether an SCP's intersection semantics apply given that these functions carry resource policies.
4. Whether the deployed Keycloak release accepts two assertion keys for one client concurrently, or whether the two-client replacement is required.
5. The late-delivery horizon that sets the C4 verify-window (needs Requirements/NV-05 confirmation).
6. Whether any `lambda:InvokeFunction` condition key restricts network origin beyond the function-context condition (no `aws:SourceVpce`/`aws:SourceIp` claim is made).
7. Measured reconnect/token-request rates under a 900 s cohort expiry.
8. Whether the optional same-snapshot cursor continuation is ever specified; until then only new-snapshot restart applies.

## 9. Decisions

K-1 (single task vs HA), K-2 (operations ownership) and K-3 (presenter-local key) are with Fernando now; this revision is written under the recommended defaults and does not depend on their outcome except where stated (§2.8 of rev2 for availability consequences, §4 here for relay-key handling).

**No new owner decision is requested.** SR2-01/SR2-02 are design defects with a design answer; SR2-03 is a restored control; SR2-04 is analytical correction. If the dual-key limitation in §3 turns out to be real and a per-client rotation outage is unacceptable, that becomes a measurable trade for review, not an owner question.

## 10. Boundaries observed in this pass

Read-only inspection of the commissioned review, rev2, the rev2 compatibility assessment, its manifest, owner decisions, the binding and four source files (`AwsRuntime`, `C4AuthenticatedTransportContext`, `C4CarrierSchema`, `C4PrincipalRole` naming). No Git/provider command, no AWS/Keycloak API call, no probe, no credential flow, no login, no deployment, no pipeline run, no publication, no test execution. No approved file, review, assessment or earlier proposal was modified. No onward delegation. Only `design-rev3.md` and `evidence-rev3/` were written.
