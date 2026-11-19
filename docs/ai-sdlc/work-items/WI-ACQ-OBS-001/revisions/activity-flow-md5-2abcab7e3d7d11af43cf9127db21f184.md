# Console observability activity flow

Status: candidate direction accepted for continued specification; detailed contracts and independent review pending. Not a deployed system or construction approval.

```mermaid
flowchart TD
    START([Start]) --> AUTH[Single demo user JWT; backend service IAM remains separate]
    AUTH --> OPEN[Open authenticated WebSocket transport]
    OPEN --> SUBMIT[User submits POS transaction once through HTTP API]
    SUBMIT --> ACL[ACL validates and translates to private ISO 8583 over HTTPS]
    ACL --> CLAIM[Atomically persist CLAIMED_NOT_SENT and critical event in DynamoDB]
    CLAIM --> GATE{Atomic POSSIBLE_SEND and critical event persisted?}
    GATE -->|No| FAIL[Report unavailable or uncertainty; no automatic payment resend]
    GATE -->|Yes| SEND[Invoke supported operation once]
    SEND --> SAVE[Atomically save subsequent business transitions and critical events]
    SAVE --> RESULT[Return known outcome or explicit uncertainty]
    SUBMIT --> ID[Receive actual transactionId; existing history already has an ID]
    ID --> SUB[Install subscription routing and generation; buffer incoming frames]
    SUB --> ACK[Receive subscription ACK and fixed source head]
    ACK --> QUERY[HTTP API Lambda queries same event table up to fixed head]
    QUERY --> RETAIN{Retained history and valid cursor?}
    RETAIN -->|Yes| MERGE[Merge bounded live buffer; order by sourceSequence and deduplicate]
    RETAIN -->|No| GAP[Show truncation or gap; restart bounded catch-up]
    GAP --> QUERY
    MERGE --> DISPLAY[Render known tasks, outcome and observation gaps]
    CLAIM --> STREAM[DynamoDB Streams detects event rows]
    GATE -.-> STREAM
    SAVE --> STREAM
    STREAM --> NOTIFY[Lambda notifier sends through API Gateway WebSocket]
    NOTIFY --> DELIVERY{Current browser subscription reachable?}
    DELIVERY -->|Yes, may duplicate| MERGE
    DELIVERY -->|No or callback fails| KEEP[Event remains queryable; no durable per-browser queue]
    KEEP --> RECOVER[Reconnect or detect heartbeat gap; renew JWT if required]
    RECOVER --> SUB
    DISPLAY --> WAIT[Wait for next frame or user action; no one-second polling]
    SEND -.-> HOOK[Best-effort intermediate task hook]
    HOOK -.-> EVENT[Sanitized event in same table if write succeeds]
    EVENT --> STREAM
    HOOK -.-> LOSS[Lost observation produces explicit completeness gap]
    SEND -.-> ADOT[Async ADOT technical telemetry; independent of functional history]
```

## Interpretation
- AWS business persistence is DynamoDB per Operation A AWS architecture update, not the earlier local SQL baseline. Business state and critical sanitized event are written atomically; consultable event rows are not copied into a second history table.
- Streams signals changes to a notification Lambda; API Gateway owns WebSocket connections. Backend connection/subscription records are technical routing state, not console user accounts. Lambda invocation/notifications can repeat; browser deduplication and retained-event catch-up are mandatory.
- Subscribe before catch-up and merge buffered live events by stable event identity; do not assume per-transaction total ordering from Streams across different items. Exact gap/cursor protocol is delegated for specification.
- Streams retention is 24 hours; the event store is the source for reconnect and longer replay. WebSocket delivery is not durable acknowledgement or proof of payment outcome. Notification outages do not gate payment. No automatic payment resend/reversal.
- Intermediate observations are distinct from atomic critical transitions; no full per-task durability is claimed. ADOT export is separate and can be sampled/dropped.
- Latest owner direction retains JWT with a single demo user, superseding earlier no-login wording. No multi-user management. Exact browser/API/WebSocket JWT issuance and validation remain under specification; existing service IAM is not removed. Seven-day functional retention and quantitative capacity/SLO limits remain proposed.
- References: [AWS Streams](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Streams.html), [Lambda stream processing](https://docs.aws.amazon.com/lambda/latest/dg/with-ddb.html), [WebSocket callbacks](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-how-to-call-websocket-api-connections.html). Verified AWS/Context7 in the preceding conversation turn; no runtime validation implied.

## ISO security clarification
ISO8583 defines the message structure, not a universal transport or an unauthenticated trust model. IP allowlisting restricts network origin but does not provide cryptographic peer authentication. TLS/mTLS, private connectivity/VPN and message authentication codes are distinct profile/connection choices. D1 selected ISO payload over HTTPS; no raw TCP switch connection has been approved. Browser JWT identity and backend service identity are separate.

Verified references (2026-11-19): jPOS GenericSSLSocketFactory documents TLS client authentication and server certificate validation: https://jpos.org/doc/javadoc/org.jpos.jpos/org/jpos/iso/GenericSSLSocketFactory.html . Context7 /websites/jpos corroborates separate packager/channel configuration and MAC field support; not proof of required scheme security or deployed behavior.
