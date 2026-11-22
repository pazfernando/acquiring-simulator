# WI-ACQ-OBS-001 — Independent M1/M2/L1 correction re-review

Date: 2026-11-19, America/Bogota.
Reviewer: Acquiring Domain Reviewer; payment-behavior-review skill.
Execution identity: GPT-6 per system context; default execution, no override. Exact provider model ID for this execution was not exposed.

## Verdict and gates

**PASS — M1/M2/L1 specification correction review and separate exposure/retention decision-readiness only.**

**M1 CLOSED / M2 CLOSED / L1 CLOSED.**
Current new findings: **0 HIGH / 0 MEDIUM / 0 LOW**. No in-scope technical correction remains necessary before presenting the next bounded owner choice.

**Exposure and retention are separately decision-ready, not approved.** The next single decision may choose presenter-local screen-share versus a separately reviewed direct client-access boundary. Explain that screen-share is not direct client browser access and loopback-only is a proposal, not the owner's already selected requirement. Retention may be addressed subsequently as its own choice; do not bundle it or numerical/SLO approval into exposure assent.

**BLOCKED — construction.** Exposure/retention and remaining architecture/construction dispositions are outstanding. Existing issuer/JWT/IAM/WebSocket/Java25/ADOT/key/cursor/expiry/cost/security preflight remains unproven; independent Security review and downstream QA remain unavailable/unexecuted. This PASS is not a construction, runtime, codec/crypto, delivery, QA or compliance PASS.

D1 private HTTPS profile, approved async direction and ONE demo JWT principal remain the recorded choices. No multiuser management, unauthenticated/IP-only substitute, production compatibility, deployment or broader architecture approval is inferred.

## Exact review identities and complete manifest verification

Independently recomputed vectors/review-manifest.json MD5:
**71f3d3247a7847cbc673c0657a7c4059** — matches the task.

All **20 entries** independently matched their absolute/relative target identities, **byte counts, MD5 and SHA256**. This closes the previous limitation in full Requirements-receipt comparison: the new complete file manifest was available and verified directly, not merely trusted because the coordinator verified it. The manifest excludes its own recursive identity and the review file, appropriately.

Additional authority identities independently matched:

- owner-decisions.md: **0dbe1d3a1ee8e34ee416fc8ea4f15918**.
- revisions/domain-review-md5-1bfa8629a5321434515b51a4050f90d5.md: **1bfa8629a5321434515b51a4050f90d5**.

| Manifest entry, relative to this directory | Verified MD5 |
| --- | --- |
| acceptance-scenarios.md | 82f176aff457e6da2a987db3cb21cbc4 |
| activity-flow.md | 2f8f752b6cad11c77f6f2136cc6c0914 |
| activity-flow.mmd | 8e83226e66f6ce401fe6526ed60b14b0 |
| channel-contract.md | facfed73dde2986e3a4b33b5fe12d7b7 |
| decision-proposal.md | a51afad8cd4113a4a7970f07dd4da895 |
| iso8583-profile.md | 5cdfbff1a4ea9f92eb8036d57e2e1ac1 |
| observation-contract.md | 3bdbfc869917e7b9e916c7e2c732af6f |
| requirements.md | 7d09de60dcc5a328dab6e8a1d1fe874b |
| vectors/README.md | 4f4afdd7f1ef52da3f6b999eb257259a |
| vectors/async-projection-vectors.json | ed5cc0ad72a15421b51ca8e0584c9820 |
| vectors/carrier-vectors.json | 1609b5bee77ed4fa42c1decbaf490069 |
| vectors/channel-vectors.json | 9c148fec0100ab3927e8c8e73d899102 |
| vectors/documentation-validation.json | 3b4a785a5afefeeb1c30f4f7abb6273c |
| vectors/event-vectors.json | 4490b56092524c2e1d0ad8def4236d59 |
| vectors/evidence-fixtures.json | ff16106f14f443360501a841c1af9ae0 |
| vectors/no-response.bin | d41d8cd98f00b204e9800998ecf8427e |
| vectors/packed-vectors.json | e0a111ab43dd54377ca68a52a99b4a97 |
| vectors/response-vectors.json | 599fc03c6baaaad6dc1ca0e5d2cbdd79 |
| vectors/state-vectors.json | a832d8611362938fa5731dd0df3c3c9c |
| vectors/websocket-vectors.json | 4ccf40e89ef9873efeebbc49974a604b |

Identity checks establish reviewed bytes, not implementation correctness. Configured MCP is incomplete under runtime policy; no MCP-backed verification or fresh remote publication/runtime success is claimed.

## Independent finding closure

| Finding | Disposition | Evidence and assessment |
| --- | --- | --- |
| M1 subscription retry/conflict ambiguity | CLOSED | O5a/C8 define K=(trusted connectionId,connectionEpoch,requestId), immutable target T including subscription/environment/acquirer/merchant/transaction, unique active subscriptionId, conditional install, cached ACK and generation. Changed target is REQUEST_CONFLICT; another requestId claiming an active subscriptionId is SUBSCRIPTION_CONFLICT. Removal requires fresh requestId and generation comparison; retired requests cannot reactivate routes. W26–W31 specify lost ACK, retarget conflict, concurrent conflict, stale auth epoch, explicit removal/fresh replacement and pre-ACK buffering. |
| M2 WebSocket prerequisite and head ambiguity | CLOSED | Diagram explicitly enables HTTP action independently of optional WebSocket transport, including OFFLINE -> explicit SUBMIT. Failed HTTP authentication instead goes to AUTHFAIL. ACK reports observed A after routing install; a separate strong read selects H>=A, fixed for that snapshot's pages. SEND is one financial downstream invocation for a claimed operation, preceded by both atomic gates; original-key replay has no SEND. W01/W26 A2/H3 and W32/W33 distinguish socket failure from HTTP auth failure. |
| L1 JWT example time encoding | CLOSED | W25 iat/exp are numeric epoch seconds. Human-readable UTC timestamps now live outside claims; the values independently match 2026-11-19T12:00/12:15/12:30Z. Expiry remains min(JWTexp,lease), renewal uses new connection/generation, no token fallback or automatic financial POST. |

Closure refers to the exact specification defects, not deployed transaction/authentication/graph execution.

### M1 challenge: immutable request binding is not a payment key

Identical K/T while active returns the same cached ACK/generation/observed A even if the source advances. Crash after routing install but before ACK preserves the reserved generation rather than allocating another subscription. A changed field cannot silently retarget an old buffer; a shared subscription ID cannot be won twice by concurrent requests.

Action is immutable in the shared control request namespace. Explicit unsubscribe has fresh control requestId and generation comparison; exact repeat removal returns the tombstone ACK, wrong generation cannot remove a replacement, and retired original subscribe cannot reinstall a route. The proposed 100-control-identity cap retains keys/tombstones through the valid epoch instead of silently evicting an active idempotency binding. Reconnect creates a new auth epoch, not a new authorization instruction.

The client matches current socket/ready epoch/requestId/full target before accepting ACK generation. Pre-ACK frames may be buffered only for the pending binding; **none render**. After matching ACK, mismatched generations are discarded and the fixed source snapshot/contiguous merge supplies missing records. An old ACK for the same transaction under a previous epoch is still ignored.

These controls correct the prior UI-routing ambiguity without changing payment identity, original references, no-redispatch or tenant-mandate rules.

### M2 challenge: optional observation path cannot become a financial recovery path

The revised Mermaid sources are independently **byte-equal**. They show authenticated HTTP readiness and a distinct best-effort socket attempt; OFFLINE progression requires an explicit viewer action and does not automatically submit. Successful HTTP can return actual transactionId without live transport; existing history can use its known ID without another submission.

ACK A and snapshot H are separately labeled. Lost ACK retry can return A2 while the fresh strong snapshot is H3; this is intentional. Snapshot H stays fixed while pages arrive, with frames beyond H buffered/merged under existing finite-buffer/retention rules. Repeated ACK does not reset rendered coverage.

Separate source-graph reachability inspection found **no path to SEND** from RECOVER, SUB, ACK, GAP, QUERY, NOTIFY or HISTORY. Explicit viewer SUBMIT is the financial-action entry, not notification/reconnection. KEY replay bypasses SEND. CLAIMED_NOT_SENT and POSSIBLE_SEND+critical event gates remain ahead of the one downstream invocation.

This is documentary graph inspection only, not a Mermaid renderer/browser test or proof that future handlers implement the graph. PNG/Drive remain older unless synchronized by the coordinator; no rendered/publication currency is claimed.

### L1 independently checked NumericDate values

| Example claim | Epoch seconds | Independently decoded UTC |
| --- | --- | --- |
| initial iat | 1795089600 | 2026-11-19T12:00:00Z |
| initial exp / renewed iat | 1795090500 | 2026-11-19T12:15:00Z |
| renewed exp | 1795091400 | 2026-11-19T12:30:00Z |

All are integers. These are unsigned example claims, not an issued token or real signature/issuer/audience verification. Existing-provider grant support and WebSocket JWT/IAM integration remain preflight; examples cannot authorize a fallback or permanent browser token.

## Preserved financial and observation constraints

Compared corrected requirements/channel/observation sections with preserved preceding revisions. Removed ambiguous subscribe prose was replaced by stricter binding/ordering rules; no core financial control was relaxed. The profile and financial packet/proof fixtures remain the previously reviewed identities.

- Business journal, verified evidence and critical consultable event still commit atomically before a known result. An unadmitted proof, task, sampled span, source event or browser green state is not new issuer confirmation.
- Event table remains the one consultable payload source. Streams signals notifications; no second history projection or per-browser durable pending payload backlog was added.
- Failed mandatory predispatch storage prevents send when positively unsent; ambiguity after POSSIBLE_SEND remains uncertain. Read/reconnect/notification repair never resumes a financial invocation.
- Original business key, prior precedence, amount/currency scope, generation/window, current mandate and no automatic reversal/resend remain. A new unlinked generation after the existing window is not safe recovery of an earlier unknown.
- Source sequence reflects committed append order, not arbitrary cross-producer causality or stream arrival. Fixed snapshot/cursor, finite buffers, explicit gaps, heartbeat recovery and original-time expiry remain necessary.
- Technical control tombstones/connection metadata are not additional user management, payment state or exposure authority. Single demo identity does not permit foreign acquiring data or issuer diagnostics.
- Common hook remains mandatory for every present/future implemented capability; task delivery best effort and sampled ADOT remain honestly qualified. Future operations are not commissioned merely to satisfy a hook.

No reversal/refund/hold-release conflation, generic inquiry/network-management mapping, mandatory CP 3DS, stand-in/partial approval or real scheme conformance was introduced.

## Counts, decision readiness and remaining work

Independently recounted25 rule rows: **10 SOURCED +12 DESIGN ASSUMPTION +1 APPROVED SELECTION +2 APPROVED DIRECTION**. Detailed exposure/retention/runtime/capacity/SLO realization remains qualified, not silently absorbed into the direction labels.

Inventory arithmetic now agrees at **255**: 28 scenarios +85 packets +35 states +8 channel +5 canonical-event +12 carrier +26 response +23 source-semantic +33 WebSocket cases. The eight additions W26–W33 expand the previous247. The41 evidence fixtures and5 source examples remain supporting data, not extra cases or tests. W26 request/ACK equality, W29/W31 empty pre-ACK rendering and W25 NumericDate values were inspected independently; all WS cases retain zero automatic payment POSTs. Future W32's one expected SEND belongs to one explicit authorized request, not a reconnect.

**Exposure: separately decision-ready.** Present it as an actual choice, explaining local presenter screen-share versus direct client browser reachability. Neither the owner's single JWT instruction nor this review chooses loopback access for them.

**Retention: separately decision-ready afterward.** Explain proposed seven-day original-time payload expiry, offline/unnotified truncation, retained identity/expiry metadata and financial links, critical future-evidence headroom and finite capacity. Do not present the proposed numbers as measured, already approved or free of long-lived storage cost.

No in-scope fixable M1/M2/L1 defect remains. Remaining work is owner exposure/retention disposition; human architecture/construction authority; issuer/JWT/IAM/callback/cursor/expiry/key/cost and Java25/ADOT compatibility preflight; triggered independent Security review; independent QA and acceptance at their respective gates. Coordinator should separately synchronize current diagram rendering/Drive before claiming that visual is current. None of that work was performed or approved by this document.

Only domain-review.md authored. No other file, fixture, owner/work-item record, diagram/PNG/Drive, code or configuration modified. No runtime/crypto/codec/transaction/QA pass, approvals, commit, external send, publication or deployment claimed.
