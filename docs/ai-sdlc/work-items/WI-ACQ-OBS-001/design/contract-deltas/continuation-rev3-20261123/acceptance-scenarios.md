# Exact revision2 acceptance scenarios
DRAFT; documentary expected outcomes, NOT execution results. Given/When/Then below each runs in isolated state. All cases assert automaticPaymentPosts=0; explicit authorized initial submission may have ONE original financial send where stated. Payment operations B/C, reversal, inquiry, refund and 3DS remain out of scope. Review exact hash before execution.

|ID|Given|When|Then|
|---|---|---|---|
|OBS-23|HTTP subject DEMO-USER has current ACQ-A mandate|tamper body/target/cursor to ACQ-B or issuer route|403 no foreign data/dispatch for scope violation; issuer diagnostics404; no human admin|
|OBS-24|idle authenticated EPOCH-001 and retained source|revoke subject mandate or fail registry; then frame/page/fanout/idle tick|deny every disclosure and close<=30s; service mandate revoked separately blocks downstream; no payment mutation|
|G5-01|W26 cached ACK A2; source now3|repeat exact K/T then query|SAME GEN/ACK A2; NEW snapshot H3; no second route|
|G5-02|W27 installed K/T TX-001|reuse K with other transaction|REQUEST_CONFLICT; original route intact; no other data|
|G5-03|W28 concurrent fresh request IDs same active subscriptionId|conditional route claims race|first commit wins; loser SUBSCRIPTION_CONFLICT; no redirect|
|G5-04|W30 removed generation then fresh replacement|replay removed K or wrong-generation unsubscribe|REQUEST_RETIRED or STALE_SUBSCRIPTION; replacement unchanged|
|G5-05|W31 pending bound tuple before ACK|matching frame then wrong epoch/tuple ACK then matching ACK|render none before valid ACK; discard foreign/old; merge retained source once|
|G5-06|PAYMENT gen1 firstReceipt T, confirmed; linked prior exists|at T+24h send explicit linked retry even with newer gen2 head|resolve gen1 first; exact original refs/result, new attempt link, zero additional send|
|G5-07|gen1 unresolved; payload older than7d; newer gen2 shares business key|valid internal late original proof arrives|original instruction/hash/version v CAS only gen1; receipt+business+critical event atomic; gen2 unchanged; old payload not resurrected; new event original commit time|
|G5-08|G5-07 first atomic admission committed|duplicate proof then contradictory proof then delayed timeout|duplicate zero version/event/window; contradiction quarantine; confirmation monotonic|
|G5-09|original v read but concurrent version changes|late-proof CAS fails|reload SAME original and re-evaluate proof; never newest key head; no dispatch|
|G5-10|cursor minted100s after JWT, nominal expiry later|JWT expires mid-pass while cursor valid|401 before next data; old page stays last-observed only; new epoch makes NEW H/R/F|
|G5-11|page fixed H3/R1|expiry changes R2 during read|410 exact safe error; no stale complete; new snapshot reports expired ranges|
|G5-12|HTTP-only history valid token, WS unavailable|GET initial then next page|read generation READ-1, fixed H/R/F, same source; no socket prerequisite; zero financial side effect|
|G5-13|new claim and required journal/event transaction unavailable|explicit valid submit|atomic failure leaves no claim/event/send; contrast notifier-only outage permits original payment|
|G5-14|durable confirmation readable then source/notifier fails|read/retry result|200 committed APPROVED/DECLINED/PROCESSOR_REJECTED; PARTIAL/UNKNOWN observations as C3; no downgrade to financial UNKNOWN|
|G5-15|authoritative CLAIMED_NOT_SENT after crash|read-only retry and history|503 null/NOT_DISPATCHED on submission/replay;200 history; no resumed sender|
|G5-16|readable POSSIBLE_SEND no confirmation|evaluate before then at dispatch+30s|202 NOT_CONFIRMED/PENDING active; deadline200 NOT_CONFIRMED/COMPLETED initial or REPLAY retry; no resend|
|G5-17|possible-send independently known this invocation, journal unavailable|respond|503 NOT_CONFIRMED/PENDING; unreadable restart with no independent proof503 null/UNAVAILABLE; never infer unsent|
|G5-18|mandateRef points to immutable exact body hash/current context|rebind payload/header/client/environment, expire/revoke or deny store read|fail closed before claim/disclosure; safe same-body authorized replay remains possible|
|K1-01|two optional mapper scopes with no default audience|obtain HTTP then WS tokens on pinned issuer|exact disjoint resource aud/scopes, same azp/sub,<=900s, no refresh; no service secret|
|K1-02|PKCE flow started|wrong/missing/reused state/nonce/code or invalid verifier|no session/token acceptance; login_required triggers restricted interactive flow without payment POST|
|K1-03|signed service token svc-console-ws matching WS aud/scope|connect; then try HTTP token at WS and WS token at HTTP|deny all wrong classes; no audience relaxation|
|K1-04|valid current WS token|missing/foreign Origin or expired/revoked binding; inspect echo/logs|401 connect or close as appropriate; fixed protocol only; no bearer at rest|
|K1-05|HTTP/WS invalid signature/kid/iss/aud/azp/sub/scope/iat/nbf/exp|exercise each mutation independently|deny; unknown key/registry outage fail closed; no fallback stub|
|R2-01|current token or cursor remaining<=5s|client requests next-page action|client pauses and marks interrupted; actual server expiry always denies; no completeness claim|
|R1-01|payload logically expired but TTL not deleted|HTTP query, notifier and Streams redelivery run|filter payload everywhere; expired ranges/dedupe persist; no renewal/send; required business evidence retained|

## Suite disposition
W01/W07/W08/W09/W10/W13/W19/W20/W23/W25 replaced in full in websocket-vectors.json; all other W cases carry forward exact expectations, including ACK identity, no speculative transaction and W32 optional WS/W33 invalid HTTP denial. Original H01–H26 response fixtures are copied byte-identically; all 26 expected status/body oracles remain mandatory. Original OBS01–22/25–28 retain business expectations; OBS23/24 replaced above. Historical service/loopback wording is NOT a valid current oracle. Quota/SLO numbers remain candidate assumptions, not accepted guarantees. Actual Keycloak signatures, HTTP cursor signing, DynamoDB Local execution, provider routing and browser capture remain unexecuted. No claim of expanded executed counts.

## Rev3 authoritative setup and added cases
All inherited cases remain mandatory. Apply setup-overlays.json to H01–26 and W17 without changing response bytes. K1-01/02/W20 use browser-flow.md for BOTH flows (parent never navigates); no inherited same-window interpretation survives. Added browser negatives include wrong event.source despite correct origin, reused state/code, stale epoch, nonce/verifier mismatch, popup/COOP failure, logout/reload/bfcache and deadline/child closure; all reject without installs/claims/sends. old-prior-fixtures.json fixes 5 invalid-prior types at after24h and after7d, newer matching local head ignored. late-original-fixtures.json fixes commit/duplicate/contradiction/CAS/new-head-misbinding/expired-payload oracles at both boundaries. Documentary cases only; not tests executed.
