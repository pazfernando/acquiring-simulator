# WI-ACQ-OBS-001 — Independent functional-guide merge and sequence review

Date: 2026-11-19, America/Bogota.
Reviewer: Acquiring Domain Reviewer; payment-behavior-review skill.
Execution identity: GPT-6 per system context; default configured execution, no override. Exact provider model identifier for this execution was not exposed.

## Verdict and precise scope

**PASS — documentation merge and sequence-domain semantics ONLY.**

Current findings: **0 HIGH / 0 MEDIUM / 0 LOW**. No in-scope fixable business-semantic defect was found in the exact merged guide and revised sequence sources identified below.

The complete local snapshot of the Drive0.5.2 guide is preserved, including Section13; appended14 Channel Test Interface and15 Cross-cutting Observability remain distinct. The sequences preserve required business/event persistence before the single financial invocation and recovery without payment re-execution. Current approved single-demo JWT, presenter screen-sharing and seven-day functional history are represented without extending scope or changing authentication assumptions.

This is not architecture/construction approval, independent QA, Mermaid rendering validation, provider/Drive publication, deployed security verification, runtime codec/crypto proof or compliance certification. Existing implementation/preflight/Security/QA/construction gates remain outstanding. No new owner decision is recorded.

## Prior review preserved before replacement

The outgoing domain-review.md independently matched **295c1a3601dd099f172a40d27b73e4b3** and was copied byte-for-byte before this replacement to:

revisions/domain-review-md5-295c1a3601dd099f172a40d27b73e4b3.md

That prior report remains the review of the20-file M1/M2/L1 manifest. This new report concerns the guide/sequence documentation delta, not fresh approval of the entire implementation contract.

## Exact reviewed manifest

The coordinator's later format-only steering superseded the four originally supplied sequence hashes after replacing Mermaid-note semicolons with commas. I read and pinned the actual revised bytes, not the obsolete sequence identities. The guide and merge-notes hashes still match the original task.

Paths below are relative to docs/ai-sdlc/:

| Artifact | Independently computed MD5 |
| --- | --- |
| sources/acquiring_simulator_functional_guide.md | db00cd4ef3c57166b18f188b3db9fa8c |
| work-items/WI-ACQ-OBS-001/sequence-flow.md | 38e39163d272292ad405f2f8f4618594 |
| work-items/WI-ACQ-OBS-001/sequence-flow.mmd | 689d6bad9a50af41246b4550c4e92d03 |
| work-items/WI-ACQ-OBS-001/sequence-recovery.md | d9d77db0d7121aee8d204329f823a439 |
| work-items/WI-ACQ-OBS-001/sequence-recovery.mmd | a64edb1e04653de3fce707a865b17fd3 |
| work-items/WI-ACQ-OBS-001/guide-merge-notes.md | 7606099dff4b2fc3c077e60754da484c |
| work-items/WI-ACQ-OBS-001/owner-decisions.md | 144186943990aaa585283b9625a16b95 |
| work-items/WI-ACQ-OBS-001/observation-contract.md | 3bdbfc869917e7b9e916c7e2c732af6f |
| work-items/WI-ACQ-OBS-001/channel-contract.md | facfed73dde2986e3a4b33b5fe12d7b7 |
| work-items/WI-ACQ-OBS-001/iso8583-profile.md | 5cdfbff1a4ea9f92eb8036d57e2e1ac1 |
| work-items/WI-ACQ-OBS-001/revisions/guide-drive-baseline-md5-69ca7e39cc0cfd595eb19c28c5087c15.md | 69ca7e39cc0cfd595eb19c28c5087c15 |
| work-items/WI-ACQ-OBS-001/revisions/guide-local-baseline-md5-703fd59f66a3d097147eee79141e2524.md | 703fd59f66a3d097147eee79141e2524 |

The new canonical guide calls itself0.5.3 LOCAL MERGED DRAFT, publication pending. That is not a claim of a published upstream Drive version. The Drive baseline was read as supplied local evidence with its verified identity, not newly fetched through a provider.

Configured MCP is incomplete under runtime policy. No MCP-backed source fetch/publication succeeded or is claimed.

## Independent merge assessment

Compared the full canonical merged guide directly with the complete preserved0.5.2 baseline rather than relying on guide-merge-notes' conclusion.

- Before the appended Section14, the substantive baseline body from Status onward agrees exactly, apart from append-boundary blank lines. The only original top metadata changes are Version0.5.2 ->0.5.3 local merged draft and Date2026-11-16 ->2026-11-19.
- Section13 from its heading through the final architecture-evolution paragraph is unchanged in full. NET/ISS/PROC/REV/INQ rows, evidence contract and architecture note are retained; Section13 was not renumbered or overwritten.
- Numbered section headings occur once in order1–15. Section14 is the Channel Test Interface; Section15 is the transversal observability responsibility.
- The complete0.5.2 body was not replaced by a shortened A-only guide. Broader B/C examples remain reference catalog, while new Section14 explicitly prevents their presence from commissioning those operations now.
- The local0.5.1 baseline remains independently hash-resolvable. This review verifies the stronger immediate merge invariant that the complete selected0.5.2 body survives unchanged; it does not claim a fresh remote Drive readback.

Section13's broader priority observations, including reversal/inquiry examples, must continue to be read with the approved current A-only work-item scope. The appended qualification does not delete the source evidence or invent a future wire profile.

## Per-issue review

| Issue | Disposition | Evidence and assessment |
| --- | --- | --- |
| Drive0.5.2 baseline/Section13 preservation | CLOSED | Direct whole-body comparison and exact Section13 comparison agree; no replaced business text. |
| Section14 versus15 responsibility | CLOSED | Channel demonstration is a test interface, not a fourth financial operation. Observability is explicitly transversal across every implemented/future capability and not authorization's final step. |
| Current owner decisions | CLOSED for documentation representation | Current owner record includes “yo se la muestro” and separate seven-day “si”; single demo JWT retained. New text reflects those choices without claiming deployment/architecture approval. |
| Payment persistence/send ordering | CLOSED for sequence semantics | Claim+critical event then POSSIBLE_SEND+critical event both precede the single processor-to-switch financial invocation. Failed/unreadable gates exit without automatic resend. |
| Processor/network/issuer truth | CLOSED, preserved | Explicit network rejection differs from issuer decision; delivered verified evidence must be durably admitted; no remote issuer-table/span/task substitution. |
| Existing-key replay | CLOSED, preserved | Replay break returns original reference/latest known result or uncertainty and exits before financial invocation. |
| Recovery remains nonfinancial | CLOSED for sequence semantics | Subscription, query, notification retry, head sync, renewal and reconnect paths contain no payment POST/financial invocation. |
| ACK/snapshot/buffering | CLOSED, preserved | Install routing before observed A, match request/target/epoch/generation ACK, no pre-ACK rendering; independently acquire fixed H>=A and page/merge from same source. |
| Retention and history meaning | CLOSED, preserved | Approved seven-day functional history uses original time/truncation, not renewed by reconnect; no permission to purge financial evidence/identity or retry an authorization after seven days. |
| Source equality after format correction | CLOSED | Each Markdown Mermaid block equals its corresponding .mmd bytes exactly; semicolons are absent inside the revised Mermaid blocks. Renderer success is not inferred. |

CLOSED denotes the bounded documentation obligation, not implemented or approved realization.

## Financial sequence challenge

The main sequence has a single financial P->S invocation, after two explicit durable gates. Authentication failure, validation/reference rejection, matching replay and failed/unreadable claim/gate are early exits. Their break semantics prevent interpreting each block as another dispatch.

Network and issuer are separate actors. Network rejection before forwarding is not an issuer decline. Missing/invalid/late response is not automatically declined; valid late evidence belongs to the existing correlated admission contract, not a second purchase. Verified response is known only if the receipt/business version/critical event transaction succeeds. Persistence failure after possible send cannot be repaired by treating volatile reply, telemetry or a source observation as financial authority. Readable prior confirmation continues to have precedence under the referenced channel contract.

The line describing uncertainty recording “when persistence is available” is important: the diagram does not promise that an unavailable journal can durably record every failure. It returns truthful uncertainty without inventing a successful commit. Detailed current-invocation versus unreadable-restart JSON semantics remain the existing reviewed contract.

HTTP authorization does not await live WebSocket/notifier/ADOT availability; mandatory business/event persistence remains required. The diagram does not add a PostgreSQL write, duplicate history materialization or new network protocol. D1 remains the private ISO payload over HTTPS, with separate service identity and proof controls. IP allowlisting alone is not relabeled authentication.

## Recovery sequence challenge

The notification panel contains no issuer participant and no financial invocation. Its database writes concern technical subscription binding; queries read the same retained event source. A callback acceptance or displayed recorded approval is not a newly verified issuer result.

Pre-ACK arrival is buffered without rendering. ACK observed head A is distinct from the new strong fixed snapshot H>=A. Finite pages, dedupe/contiguous source merge, expiry/gap metadata and the existing O5a/C8 conflict/removal protocol remain normative; the sequence simplifies them without replacing their constraints.

WebSocket unavailability allows an optional explicit HTTP history read. Reauthentication changes the connection epoch; repeat subscribe/ACK/catch-up never repeats payment. Silent loss uses head sync/source recovery, not a financial status inquiry or authorization retry. Streams24h is notification transport retention; retained source/seven-day functional history is the recovery window, with explicit truncation and no unlimited offline guarantee.

The panel is conceptual: its bounded page loop is not a claim that DynamoDB Query provides an implemented transactional snapshot. Cursor/expiry race handling and JWT/IAM integration remain preflight/verification work.

## Approved decisions versus technical assumptions

Source authority was read independently from owner-decisions.md, not inferred from stale pending labels in the unchanged observation document.

- APPROVED: D1 private profile/HTTPS selection; asynchronous telemetry/notification direction; single demo JWT principal without multiuser management; presenter-operated screen-sharing; seven-day functional history.
- Still proposed/unverified: exact token/session/relay/provider integration, service IAM and key/callback realization, numerical capacity/SLOs, cursor/expiry implementation, Java25/ADOT compatibility, backend provisioning/exposure, human architecture/construction disposition and Security/QA results.
- The new sections do not authorize public or direct client access, deployment, permanent tokens in HTML, disabled service authentication or raw TCP/IP-only connectivity.
- Critical events, best-effort intermediate observations and sampled technical telemetry retain different durability/evidence classes. Hook/manifest remains required for all implemented/future capabilities, but no false durable-every-task/zero-cost claim is added.
- No new operation B/C, automatic reversal/resend, refund, capture/settlement, stand-in/partial approval, generic inquiry MTI or CP 3DS requirement is commissioned by the guide merge.

No new human question or field-design decision is necessary to accept this bounded documentation review.

## Limits and handoff

The coordinator may treat these exact sources as independently domain-reviewed documentation and proceed with separately authorized documentation synchronization. This report performs no synchronization or provider operation and does not approve publication, construction or implementation. Any further substantive edits require review of the changed bytes; use the post-format hashes above, not the initial sequence hashes or an older PNG/Drive copy.

No Mermaid rendering, PNG/browser inspection, actual JWT/transport/storage/ADOT operation, transaction, codec/crypto test or independent QA was executed. No provider/Git operation occurred. The prior review archive and this domain-review.md are the only authored files. Guide, diagrams, notes, owner decisions, work-item records, code and configuration were not modified.
