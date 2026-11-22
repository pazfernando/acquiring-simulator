# Operation A access realization rev2 — bounded compatibility pass (delta)

Status: **DOCUMENTARY COMPATIBILITY ASSESSMENT, SECOND PASS**. It answers one bounded question: *do the six changed positions in [design-rev2.md](design-rev2.md) fit the code, configuration and pinned contracts in this checkout, and what source-level work do they imply?* It is not approval, not a Security verdict, not QA, not construction authorization and not a deployment plan. It does not reopen D-A, D-D, CA-1 or any owner decision, and it does not re-assess the v1 areas rev2 left unchanged.

This pass supersedes nothing. [compatibility-assessment.md](compatibility-assessment.md) (SHA-256 `4886106c…`) remains the v1 assessment; it is an input here, not rewritten. Revision 1 `design.md` (`83e4ea14…`) is preserved and superseded for topology questions.

Author role: Implementation compatibility pass (delta). Date: 2026-11-20 (America/Bogota, evening).

Runtime observed: native OpenClaw session; session model metadata `nan/deepseek-v4-flash`; configured default; **no model override applied**; the session does not expose a provider-qualified identifier, and none is invented. No delegation, no subagent.

Scope of activity: read-only inspection of documents, `infra/template.yaml`, `src/main`, `src/test`, `pom.xml` and the locally cached `aws-lambda-java-core` jar, plus local `sha256sum`/`grep`/`find` and Python 3.12 stdlib arithmetic. No file outside this artifact and `evidence-rev2-assessment/` was written. No code, adapter, IaC, ADR, requirements, contract, WORK_ITEM, owner-decision, review or status file was edited. No Git/GitHub command, no AWS/provider/Keycloak API call, no probe, no login, no credential flow, no `terraform plan/apply`, no `sam` command, no remote pipeline, no deployment and no publication. `java`, `mvn`, `terraform`, `sam` and `jq` are **not installed** in this environment, so **the suite was not run** and no test result is claimed here. The `aws` CLI exists on `PATH` and was **not invoked** in any form.

## 0. Binding, hashes and what was verified

| Item | Expected | Observed | Result |
| --- | --- | --- | --- |
| Branch / HEAD | Implementation binding | `feat/wi-acq-auth-001` / `cfc456f4f469d9558a78fda8ff9b770b00817315` | taken from the recorded binding; no Git command run |
| `design-rev2.md` | `6b151618…f3873c`, 49,235 B | same | **MATCH** — the proposal under assessment |
| `evidence-rev2/input-identities-rev2.json` | `8fe8adac…bea42` | same | **MATCH** |
| `evidence-rev2/official-source-excerpts-rev2.json` | `adebdd07…8ddfc` | same | **MATCH** |
| `evidence-rev2/revision-validation.json` | `7b720c0d…6652` | same | **MATCH** |
| `design.md` (v1) | `83e4ea14…829f4` | same | **MATCH**, untouched; superseded only as a draft |
| `configuration-schema.md` (v1) | `1f497daa…1bb2` | same | **MATCH**, untouched |
| `compatibility-assessment.md` (v1 pass) | `4886106c…36ac` | same | **MATCH**, not rewritten |
| `owner-decisions.md` | rev2 records `fd040124…ae27`, 16,582 B | same | **MATCH** — read as authority for D-A/CA-1/D-D, not edited |
| `channel-contract.md` | rev2 records `495e1172…bca` | same | **MATCH** — C6 stale per CA-5; C4 late rule cited |
| `observation-contract.md` | rev2 records `ca53e384…a205a` | same | **MATCH** — no-public-late-endpoint rule cited |

Binding caveat: the working tree is **dirty** (pre-existing modified and untracked files). No whole-tree freeze and no clean-HEAD claim is made. Every statement below is about the files as read at the identities in [`evidence-rev2-assessment/source-identities.json`](evidence-rev2-assessment/source-identities.json).

Owner decisions treated as **context only, not reopened**: D-A resolved to self-managed Keycloak ("autogestionado"); CA-1 resolved to internal-only late evidence; D-D accepted the WebSocket transport residual. Rev2's realisation of these is the object of this pass.

## 1. Keycloak client shapes and token acquisition (relay, console API, WebSocket authorizer)

**What the checkout holds today.**

- The only HTTP surface is one SAM `AWS::Serverless::HttpApi` with a native JWT authorizer `TestJwt` (identity source `$request.header.Authorization`; issuer/audience from stack parameters) and one route, `POST /authorizations` (`infra/template.yaml:5-10,47-60,79-86`).
- `ProcessorHandler` accepts only payload `2.0`, `POST`, `rawPath=/authorizations`, and reads exactly one identity input: `requestContext.authorizer.jwt.claims.sub` (`ProcessorHandler.java:30-35`). It then resolves `CALLER#<sub>` through `AwsRuntime.caller` (`AwsRuntime.java:28-33,43`).
- The resolved registry type is `AuthorizedContext(acquirerRef, merchantRef, callerId, permittedRoutes)` (`AuthorizedContext.java:9-13`). It has **no** `client_id`, environment, credential status, certificate fingerprint, scope, audience or `c4KeyIds`.
- A case-insensitive search over `src/main` and `infra/` for `client_id`, `oauth`, `oidc`, `client_assertion`, `private_key_jwt`, `jwks`, `bearer` and `token` returns no match relevant to client authentication or token acquisition. `pom.xml` declares no JOSE/JWT library and **no `software.amazon.awssdk:ssm` artifact** (only `dynamodb` and `lambda`). There is no HTTP client for a token endpoint and no relay component.
- `AwsRuntime.env(name)` returns the environment **value** directly (`AwsRuntime.java:23-27`); the "env holds the parameter *name*" indirection that rev2 §9.4 relies on has no counterpart in code.

**Assessment.**

1. **The checkout has a caller plane, not a client plane.** Keycloak's confidential-client identity — `client_id`, the registered public key/`kid`, the RS256 `client_assertion`, the audience mapper and the granted scopes — has no type, field or store here. "Can the code hold that client identity?" **No**; it can hold a Gateway-validated subject and nothing else. Registration of three clients in a realm is configuration the code cannot read, and `AuthorizedContext` cannot represent it without a new registry type.
2. **Token acquisition does not exist and is not a configuration switch.** Realising rev2 §2.7 needs, at minimum: (i) an RS256 JOSE signer for the `private_key_jwt` assertion; (ii) an HTTP client for the token endpoint plus a bounded cache keyed by client/scope with expiry; (iii) assertion-key custody — `ssm:GetParameter` + `kms:Decrypt` or an operator-provisioned local file; (iv) clock/skew and `jti` replay handling. None is present, and the missing SSM dependency means even §9.4's custody shape is a new dependency, not a wiring change. The presenter-local relay is more absolute: **there is no relay process at all**, so its demo (PKCE) and service (Signed JWT) tokens have no holder.
3. **The present authorizer cannot be extended into the role rev2 gives it; it can only be supplemented or left in place where a bearer token is the whole identity.**
   - For **S2/S3** (mTLS + service token + demo token, cert↔client binding, scope), a native JWT authorizer cannot see or hash the leaf certificate, cannot compare token `client_id` to a certificate-bound client, and cannot check `scope`. A **Lambda REQUEST authorizer** is required. The native authorizer could remain as a secondary issuer/audience gate, but it adds no binding the request authorizer would not already enforce.
   - For **S6 `$connect`**, a Lambda **REQUEST** authorizer on the WebSocket API is required; an HTTP API JWT authorizer is not a WebSocket JWT authorizer. The pinned contract already warns against assuming otherwise (`observation-contract.md:38`), and rev2 §8 S6 names the Lambda authorizer correctly.
   - Native `TestJwt` survives only where a bearer token alone is the identity and no scope or demo separation is needed — i.e. nowhere new in rev2. Because it is the `DefaultAuthorizer`, any future route on this `HttpApi` inherits it unless the route overrides `Auth`; there is exactly one route today, so no such override exists.
4. **No hidden credential path is invented by rev2 — the gap is total absence, not a fictional shortcut.** §2.6 and §9.4 describe custody honestly (parameter store, operator file, rotation, not-before revocation). The incompatibility is that none of the described custody has a consumer: no signer, no SSM read, no token cache, no client record, and no code that compares a token's `client_id` or `scope`. The design is realisable only after a new credential subsystem exists.
5. **Scope and audience enforcement remain absent in the application.** `ProcessorHandler` reads only `sub`; no code reads `aud` or `scope`. Rev2 §1 now correctly records app-side revalidation as *required work* rather than existing behaviour (a correction of v1, credited), but that also means a valid Keycloak token with the right audience would be accepted for any route the handler serves until that work is done.

**Verdict: compatible in direction, not supported by the checkout.** It needs a client/credential registry, an assertion signer, a token-acquisition and cache path, key custody, a relay component, and a request authorizer split (mTLS hops and `$connect`). No pinned contract is contradicted by the *shape* of the Keycloak clients; what is contradicted is the implicit availability of any of it.

## 2. Internal-only late evidence (rev2 §4/§8 S5i)

**What exists, and what does not.**

- The SAM template defines exactly three functions — `Processor`, `Mastercard`, `Issuer` — sharing one artifact (`Globals.Function.CodeUri: ../target/lambda`) and carrying **identity-policy statements only** (`infra/template.yaml:61-113`). There is **no** `AWS::Lambda::Permission`, no resource-based policy, no `AWS:SourceArn`/`aws:SourceAccount` condition, no function URL and no `VpcConfig`.
- There is no late-evidence function and no handler that accepts a Lambda invocation event for late delivery. `C4LateDeliveryParser.parse` has **no caller in `src/main`** (only tests). `SignedEvidenceAdmissionService` is referenced only by its own declaration. `LateEvidenceAdmissionGateway` is constructed only by tests; `AwsRuntime.processor()` installs none of the atomic or late gateways (`AwsRuntime.java:34-42`, CA-4).
- There is **no production `C4KeyRegistry` implementation**: the port is a functional interface with a default-deny diagnostic permission (`C4KeyRegistry.java:14-38`); every implementation is a test double.

**Nothing already assumes an HTTP late route — confirmed.** The only late-delivery gateway states it in its own javadoc ("There is no HTTP route, no public endpoint, no notifier and no service/runtime wiring here", `LateEvidenceAdmissionGateway.java:31-38`); the C4/HTTP bridge states "No public late-delivery endpoint" (`C4HttpCarriageBridge.java:32-34`); the signed facade is "Not production-wired … no HTTP route" (`SignedEvidenceAdmissionService.java:48-52`); and the pinned contract says "Late evidence uses same internal admission; no public late endpoint" (`observation-contract.md:29`, `channel-contract.md:42`). The late parser is transport-agnostic (`byte[]`) and assumes no carriage. So S5i's *placement* is consistent with both code and contract; the incompatibility is in the invoke mechanics.

**Assessment.**

1. **Packaging supports reuse of the artifact, not the path.** A new function could point its handler at a class in the same `target/lambda` artifact, but no such class exists and no function resource declares it. "Supported by packaging" is therefore true only at the jar level; the invoke path itself is unbuilt.
2. **The resource policy is absent and its described form needs care.** Today's grants are identity policies on the caller, with no source condition (v1 assessment §4 item 1 stands). Rev2 §4.1 proposes a resource-based policy "naming only the enumerated producer role ARNs, with `aws:SourceArn` (or `aws:SourceAccount` plus principal) conditions". For a **direct IAM invoke**, the caller is the principal to name; `aws:SourceArn`/`aws:SourceAccount` are the condition keys used when a *service* invokes on a caller's behalf, and are not generally present on a direct `lambda:InvokeFunction`. Rev2 itself lists "the exact `SourceArn`/`SourceAccount` form" as unverified (§12 item 11). The policy should name the producer role principals; source conditions are additional only if the invoke is brokered. This is a wording/realisation risk, not a contradiction with the code.
3. **"Sender identity from the invocation context" cannot be derived from the Lambda context as described.** The `Context` interface in the locally cached `aws-lambda-java-core` 1.2.3 jar exposes `getAwsRequestId`, `getLogGroupName`, `getLogStreamName`, `getFunctionName`, `getFunctionVersion`, `getInvokedFunctionArn`, `getIdentity` (Cognito), `getClientContext`, `getRemainingTimeInMillis`, `getMemoryLimitInMB` and `getLogger`. `getInvokedFunctionArn` is the **invoked function's own ARN**, not the caller's. A single shared admission function therefore cannot distinguish the processor from the network simulator at runtime by reading its invocation context; both invocations look identical inside the handler. Rev2 §4.1's "calling principal's ARN mapped through the registry" presumes a fact the runtime does not supply.
   - Realisable options are structural, and none is stated in rev2: **one admission function (or alias/version) per producer role**, so function identity implies the sender; or a broker that itself authenticates and passes a trusted identity; or a caller-produced signature (which would re-introduce a credential on the late path and must not be a payload field the verifier trusts). Choosing one is a design decision, not wiring.
4. **Even if the caller identity were available, the code's transport record cannot carry it.** `C4AuthenticatedTransportContext.authenticatedSender` is 1–64 characters of the profile alphabet `[A-Za-z0-9._:-]` (`C4AuthenticatedTransportContext.java:12-19`, `C4CarrierSchema.java:22,32-39`). An IAM ARN contains `/` and is normally longer than 64 characters, so it is rejected before use. A short principal id must be mapped first — and no mapping from invocation identity to such an id exists.
5. **CA-3 widening is necessary but not sufficient.** Rev2 correctly defers the shape of `C4AuthenticatedTransportContext`. Widening it can carry `clientId`, registry version and permitted contexts, but it cannot manufacture a caller fact the runtime never observed.
6. **What must exist before the invoke can be wired** (each independently reviewable, none started):
   - a late-evidence Lambda resource plus a handler that reads only the invocation payload, with no route, URL or DNS name;
   - a resource-based policy naming producer principals (and source/account conditions only in the brokered case);
   - a production `C4KeyRegistry` adapter mapping `(environment, principal, keyId)` → role, public key and acquirer mandates, plus the diagnostic permission where used;
   - an invocation-identity → short principal-id mapping, with per-producer function identity if a single handler cannot see the caller;
   - a boundary that constructs the widened transport context from invocation facts, and the verifier/composer updates in one reviewed change;
   - the caller-supplied inputs `LateEvidenceAdmissionGateway` refuses to invent: deadline, `CriticalCapacity`, `environment` and `instanceId` (`LateEvidenceAdmissionGateway.java:167-187`), plus the original-instruction reader and context authorizer used by the signed gateway;
   - a decision on which composition is the admission path — `SignedEvidenceAdmissionService` (verifies C4, **refuses diagnostic forwarding**) or `LateEvidenceAdmissionGateway` (takes a `DownstreamReply` and does **no** C4 verification);
   - the atomic gateways wired with an explicit capacity policy (CA-4).

**Verdict: the internal-only placement is correct and confirmed unconflicted; the invoke path is not supported today, and the specific claim that sender identity is derived from the invocation context is not implementable on a shared function with the standard Lambda context plus the current 64-character transport-sender field.**

## 3. Lease correction and the event-store cursor/range port

**The arithmetic is now right.** With a 900-second token minted before connect, `min(jwtExp, now+900 s)` is `jwtExp`, and the 30-minute term is dead. Rev2 deletes it rather than renaming it, and leaves the token lifetime at 900 s. Two pinned texts survive the change differently:

- `channel-contract.md:52` says the connection stores `expiry=min(JWTexp,lease)`. The *expression* is unaffected: with a technical lease above 15 minutes, the minimum is still `jwtExp`. No contradiction.
- `observation-contract.md:43` still pins a **30-minute technical lease** ("technical lease30min refreshed by heartbeat/valid activity"), and the recorded D-D text still names "min(JWT expiry, 30 minutes)" (`owner-decisions.md:86`). Rev2 deletes the 30-minute term and sets `WS_LEASE_SECONDS=900` (§9.2) but CA-5 lists only C6 and O4 as stale. The outcome is compatible (`min(jwtExp, 30 min) = jwtExp`), so this is a stale pinned number, not a behavioural conflict — but the contract delta is now incomplete. The owner's correction is not reopened here.

**The port does not exist.**

- `EventStore` exposes exactly three operations: `append`, `find` (strongly consistent dedupe) and `head` (strongly consistent partition HEAD) (`EventStore.java:16-31`). There is no range, after-sequence, page or cursor operation.
- `DynamoEventStore` states the omission explicitly: "Out of scope here: slot capacity enforcement, retention/expiry, Streams, **query cursors** and notification" (`DynamoEventStore.java:45`).
- The **data layout would support** such a read: event rows use zero-padded twelve-digit `E#<sourceSequence>` sort keys, lexical order equals source order, and HEAD is a sibling row in the same partition (`EventTableKeys.java:10-35`). `EventHead` already carries `lastSequence`, `firstRetainedSequence`, `expiryRevision`, `latestKnownBusinessVersion` and `lastCollectedAt` (`EventHead.java:7-13`). So the *metadata* exists; the **read path and cursor do not**.
- `AuthorizationService.history(id)` is not that path: it reads the journal repository (`repository.eventsFor`), not the event table (`AuthorizationService.java:478-481`). It cannot serve event-source catch-up, gap ranges or sequence bounds.

**What is missing for catch-up longer than one token window** (rev2 §3 requires re-minting mid-pass; the mechanism is undefined):

1. **A range/after-sequence read port and adapter** — strongly consistent, paged, bounded by page/pass limits, over the event partition, with the HEAD snapshot fixed for the pass.
2. **A signed cursor type and codec** — mint/verify, binding `demoSubject`/service/environment/context/transaction/generation/`H`/`R`/last sequence, 15-minute expiry, and the 400/403/410 dispositions C7 pins (`channel-contract.md:57`). No cursor type exists anywhere in `src/`.
3. **Page and pass enforcement** — C7's ≤100 events/256 KiB per page and ≤1000 events/1 MiB per pass are text only; no constants or model exist.
4. **Expiry/gap production** — `expiredRanges`, `gapRanges`, `retentionTruncated` need retention/expiry behaviour that `DynamoEventStore` explicitly excludes. `firstRetainedSequence`/`expiryRevision` exist on HEAD but nothing advances or reconciles them for a reader.
5. **The catch-up route** — `GET /console/v1/transactions/{id}/events` exists in no handler and no SAM route.
6. **A mid-pass re-mint mechanism** — who mints the replacement cursor, against which live authentication, at which page boundary, and how the pass continues without a second snapshot. C7 has no wall-clock bound on a pass, so a slow pass necessarily crosses 900 s; rev2 states the requirement but no contract or code hook expresses it.
7. **Live/catch-up merge semantics** — dedupe and merge contiguous `sourceSequence`, generation/epoch filtering and the bounded buffer are specified in C7/O5 but absent from the domain and adapter.

**One internal imprecision.** Rev2 says the cursor "now coincides exactly with the lease" (§3) while also saying the lease is "typically less because the token is minted before the connect". With `CURSOR_TTL_SECONDS=900` and lease ≤900 s, the cursor can outlive the lease, not coincide with it. Because every reconnect re-mints, the practical consequence is confined to long passes — but the sentence should not be read as an equality proof.

**Verdict: no conflict with existing code, because the read path is absent and explicitly out of scope; the lease correction is arithmetically sound and does not need a new port by itself, but rev2's reconnect story does, and the mid-pass re-mint it requires has neither contract text nor code.**

## 4. Role mapping onto `C4PrincipalRole` (CA-2)

The code defines exactly four values: `ISSUER_SIM`, `PROCESSOR_AUTH`, `NETWORK_SIM`, `ACL_AUTH` (`C4PrincipalRole.java:11-27`). Rev2 §5 maps v1's names onto them. Checked one by one against the verifier's P5→P4 row map (`C4EvidenceVerifier.java:266-317`):

| Rev2 mapping | Code reality | Valid? |
| --- | --- | --- |
| `ACL` → `ACL_AUTH`, "channel ACL signs the instruction-side attestation" | `ACL_AUTH` is "Channel authentication role; **has no evidence-signing authority**" (`C4PrincipalRole.java:25-26`); the row map returns `null` for `ACL_AUTH` and its javadoc says "ACL-AUTH never signs evidence" (`C4EvidenceVerifier.java:266-278`) | **No** — see below |
| `PROCESSOR` → `PROCESSOR_AUTH` | Processor rejection and possible-issuer uncertainty rows (`:290-301`) | Yes |
| `NETWORK` → `NETWORK_SIM` | Network rejection, non-forwarding and uncertainty rows (`:303-317`) | Yes |
| `RELAY` / `RELAY_EVIDENCE` → `NETWORK_SIM` | Relay object principal is fixed `NETWORK-SIM` (`C4RelayAttestation.java:28-29`); the relay key must resolve to `NETWORK_SIM` and countersign without replacing the issuer signature (`C4EvidenceVerifier.java:204-225`) | Yes |
| — (implicit in v1) → `ISSUER_SIM` | Inner issuer key must resolve to `ISSUER_SIM` with an issuer P4 row (`C4EvidenceVerifier.java:183-202`) | Yes |
| `DIAGNOSTIC` → **no role**, scoped permission | `diagnosticForwardPermitted` is a separate default-deny permission that confers no role, key, signature or outcome authority (`C4KeyRegistry.java:24-38`) | Yes |

**The `ACL_AUTH` row is wrong, and it is the only wrong one.** `ACL_AUTH` exists to authenticate the channel client as a transport peer; it has no attestation row and can never produce a `C4VerifiedAttestation`. Rev2's phrase "signs the instruction-side attestation" describes a signing capability the enum and verifier deliberately withhold. The correct statement is: the ACL is a **transport/client identity**, not a C4 signing principal; v1's `ACL` name does not map onto a signing role at all. This matters because rev2 uses the mapping table to argue the vocabulary is fully aligned — three of four values carry signing rows, one does not, and the design should say so rather than assign it a signature.

**`DIAGNOSTIC` is correctly demoted, and nothing else still treats it as a role.** The registry javadoc is explicit, the default is deny, and the three tests rev2 cites exist and pin the behaviour (`C4LateDeliveryTest.java:355,378,402`). A scan of the other work-item documents finds only the unrelated console "issuer diagnostic" permission concept, never a C4 `DIAGNOSTIC` role. Rev2 §5, §8 and §9 are internally consistent on this point.

**But the permission is unreachable through the only composition facade.** `SignedEvidenceAdmissionService.admit` refuses any sender that is not the DIRECT signer or relay principal and states "diagnostic forwarding is not admitted by signed receipt admission" (`SignedEvidenceAdmissionService.java:142-151`); `LateSignedEvidenceReceiptGateway.admit` calls `C4EvidenceVerifier.verify`, not `verifyForwarded` (`LateSignedEvidenceReceiptGateway.java:238-246`). Only the standalone `C4LateDeliveryVerifier` and the package-private `verifyForwarded` implement the permission (`C4LateDeliveryVerifier.java:57-87`). Rev2 §4.1 lists "Diagnostic delivery" as a realised S5i element, so the design must either route diagnostic forwarding through `LateEvidenceAdmissionGateway` (which performs no C4 verification and takes an already-verified `DownstreamReply`) or change the signed facade. As written, the permission is defined and tested but not reachable end-to-end.

**Verdict: CA-2 is mostly resolved; correct `ACL_AUTH` to a non-signing transport identity, and state which admission path honours the diagnostic permission.**

## 5. Payload margin — independent arithmetic

**Method.** Recomputed offline from the record constructors and the canonical emitter — no Java executed, no fixture signed, no Gateway or network observed. Fields and maxima are taken from `C4EvidenceAttestation.java:71-106` (31 keys), `C4RelayAttestation.java:36-47`, `C4CarrierParser.java:41-59` and `C4LateDeliveryParser.java:36-45`; serialization follows `C4CarrierCanonicalJson.java:19-103` (compact separators, declared key order, `null` relay when absent). The script and full field table are in [`evidence-rev2-assessment/payload-arithmetic-rev2.json`](evidence-rev2-assessment/payload-arithmetic-rev2.json).

**The 6,964 figure reproduces exactly.** The late envelope is `{"deliveryReceiptId":"<64>","evidence":<json>,"responseBodyBase64":"<2732>","version":1}`. With the inner evidence at its 4,096-byte cap:

| Component | Bytes |
| --- | --- |
| `{"deliveryReceiptId":"` + 64-char id + `","evidence":` | 99 |
| inner evidence (canonical JSON at the 4,096 cap) | 4,096 |
| `,"responseBodyBase64":"` + base64(2,048 B) + `","version":1}` | 2,769 |
| **Total** | **6,964** |

base64(2,048) = 2,732 characters, and the late body is not inflated because the evidence is nested JSON. So rev2 §7's headline row is arithmetically correct as a **cap-conditional** bound: 6,964 ≤ 8,192, margin 1,228 B.

**The rows that support it are not declared-bounds maxima.** Recomputing the same fields at their declared maxima gives materially different numbers:

| Artifact | Rev2 §7 / v1 evidence | Independent recomputation (no escaping) | Independent recomputation (escaping maxima) |
| --- | --- | --- | --- |
| Attestation, 31 keys at max | 503 B | **2,084 B** | **2,340 B** |
| DIRECT envelope | 738 B / 984 chars | **2,319 B / 3,092** | **2,575 B / 3,436** |
| RELAY envelope | 1,288 B / 1,720 chars | **2,816 B / 3,756** | **3,072 B / 4,096** |
| Late envelope, DIRECT max | 3,606 B | **5,187 B** | **5,443 B** |
| Late envelope, RELAY max | 4,156 B | **5,684 B** | **5,940 B** |
| Late envelope, inner evidence at 4,096 cap | 6,964 B | **6,964 B** | **6,964 B** |

The 503-byte claim is not merely optimistic — it is impossible. Serializing the 31 declared keys with **every value empty** already costs **505 bytes** of key names, quotes, colons and commas. No all-maxima attestation can be smaller than its own empty-value floor. The 503/738/1,288/3,606/4,156 chain therefore describes a sample/fixture-sized envelope, not the declared field maxima; only the 6,964 row depends solely on the 4,096 cap and is exact.

**Consequences.**

1. **6,964 remains a safe guard, but it is not the achievable maximum.** Under the declared field maxima the inner envelope tops out at 2,816 B (3,072 B with escaping), so the largest achievable late delivery is **5,684 B** (**5,940 B** with escaping), leaving 2,508 B (2,252 B) to the 8,192 bound — not 1,228 B. Rev2's instruction to check against 6,964 is conservative and should be kept; the claim that 4,156 is the "all-maxima" case is understated by about 1.5 KB.
2. **The proposed CI check needs a defined target.** §7.4 proposes recomputing "from the declared field bounds" and failing if the bound is exceeded. Recomputing from those bounds yields 5,684/5,940, not 6,964; an implementation that hard-codes 6,964 would not be deriving it from the bounds, and one that derives it must say it is testing the *cap-conditional container* and ignoring per-field maxima. Otherwise the guard and the summary row disagree by construction.
3. **Escaping is a real shape variable.** `PRINTABLE_ASCII` is `[\x20-\x7e]`, which includes `"` and `\`; those values are escaped and cost two bytes each. The assumption "printable ASCII, so no JSON escaping inflates the value" is false for `actor`, `stage`, `result` and `reason`. Identifier fields are safe — the profile alphabet excludes both characters. A semantically verifiable attestation uses fixed P4 literals, so the escaping variant is a shape maximum rather than a verifiable-payload maximum; either way the payload fits.
4. **The normal-response header remains the unverified constraint — and it is materially tighter than rev2 says.** Independent totals for the RELAY normal response (Content-Type + X-Simulator-Profile + one `X-Simulator-Evidence`), using the emitter's `name + 2 + value + 2` rule (`HttpApiV2Wire.java:218-226`):

| Case | Evidence header value | Total app header bytes |
| --- | --- | --- |
| No-escaping declared maxima | 3,756 chars | 3,856 B |
| Escaping maxima | 4,096 chars | 4,196 B |
| Rev2 §7 "tight case" | 1,720 chars | 1,818 B |

Both totals stay under the app's 8,192-byte total-header bound, so the *aggregate* is not the problem. The **single-header value** is 3.8–4.1 KB at true maxima, roughly 2.2–2.4× the 1,720-char figure rev2 treats as the tight case. The per-header quota is still not asserted by any source in the reviewed evidence, so "the normal-response header remains the unverified constraint" is correct — but the unverified question is now "is a ~4 KB single header accepted?", not "is a ~1.7 KB single header accepted?". The late path is body-carried, so the header quota does not apply to it.
5. **Platform headers stay additive.** The app-side 8,192 check counts only headers the application writes; a deployed response adds at least a request id. With 3,856–4,196 B used, the ~4 KB slack absorbs that; a response near the ceiling would not.

**Correction to my own prior pass.** The v1 assessment restated 503/738/1,288/3,606/4,156 from `evidence/payload-size-calculations.json` without testing them against the constructor bounds. This pass withdraws those five figures and keeps only the 6,964 row. The v1 file is preserved and not edited; this is the corrected arithmetic.

**Verdict: the payload fits every bound; 6,964 is exact but unachievable under the declared field maxima (achievable 5,684/5,940); the normal-response single-header quota is the sole unverified constraint and is twice as tight as rev2 states.**

## 6. New contradictions introduced by rev2
