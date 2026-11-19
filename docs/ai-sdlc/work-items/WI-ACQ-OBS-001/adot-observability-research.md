# Asynchronous observability: ADOT, CloudWatch and functional history

Date: 2026-11-18 America/Bogota. Status: researched architecture amendment PROPOSED, not implemented or approved.

## Owner steering
Fernando replied to D2: "ok de acuerdo" and immediately qualified that trace delivery should be asynchronous; requested a double-check of ADOT for Lambda, CloudWatch reuse versus DynamoDB, best practices and Context7 support. Do not treat this as unqualified approval of all prior O3/O4 mechanics. No automatic payment resend/reversal remains the agreed direction; the exact trace-blocking dependency needs reconciliation with this steering before construction.

## Verified sources

1. AWS ADOT/X-Ray: https://docs.aws.amazon.com/xray/latest/devguide/xray-services-adot.html — ADOT instruments/collects/exports metrics and traces; Lambda layers integrate with X-Ray. ADOT is not a durable application journal or automatic functional-task manifest.
2. Upstream OTel Lambda Collector extension: https://github.com/open-telemetry/opentelemetry-lambda/blob/main/collector/README.md — explicitly supports asynchronous telemetry export from a separate Lambda extension. This confirms the mechanism, not identical flush behavior for every ADOT layer/language/version. The exact Java 25 runtime/layer/architecture compatibility and flush strategy remain preflight tasks; no deployment benchmark or compatibility claim made.
3. AWS Lambda Telemetry API: https://docs.aws.amazon.com/lambda/latest/dg/telemetry-api.html — extensions receive telemetry independently; batching/buffers and lifecycle limits apply, drops are possible. Logs and metrics still go to CloudWatch; traces to X-Ray when enabled. Async is not zero latency/resource overhead, durable delivery or guaranteed task completeness.
4. CloudWatch log subscriptions: https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/Subscriptions.html — near-real-time export usually under three minutes after ingestion, at-least-once with possible duplicates. Retryable errors retried up to 24 hours; nonretryable failures can disable forwarding and skip logs. This does NOT support asserting a two-second UI visibility SLO for a CloudWatch-derived projection.
5. AWS transactional outbox guidance: https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html — atomic business update plus durable event/outbox avoids unsafe dual writes; CDC with DynamoDB Streams is a documented option. Consumer deduplication is required.
6. Context7 consulted via resolved library `/websites/aws_amazon_lambda_dg`: Telemetry API buffer/lifecycle/drop behavior and DynamoDB stream consumption. Confirmed at-least-once Lambda event processing, partial batch failure controls, finite stream retention (24 hours), and need for idempotent consumers. These are documentation findings, not successful runtime tests.

## Distinguish three records

- **Business journal:** authoritative authorization/idempotency/dispatch/evidence state, already required for safe payment processing. An observability event cannot replace verified durable business evidence.
- **Technical telemetry:** spans, metrics and sanitized logs. ADOT/OTel export asynchronously to configured AWS backends; CloudWatch Logs is not synonymous with the X-Ray trace store. Sampling/drops mean telemetry cannot prove all tasks or determine payment outcomes.
- **Functional console history:** authorized task timeline with event IDs, operation/attempt correlation, safe statuses and observation gaps, not raw span/log duplication. A query projection may use DynamoDB for predictable per-transaction/context reads and pagination. Its write/availability is not itself a payment prerequisite in the amendment below.

## Recommended amendment for review

1. Keep the synchronous minimum limited to the required business journal and, for critical committed business transitions, an atomically persisted sanitized outbox notification. This is durable local admission, NOT synchronous export to ADOT/CloudWatch or the console trace table. If the authoritative claim/evidence transaction cannot commit, apply the existing conservative business failure policy.
2. Export technical telemetry via configured ADOT/OpenTelemetry collector asynchronously. Correlate traceId/spanId with operationId/attemptId and safe tenant-scoped identifiers. Redact at source; no raw ISO, tokens or credentials in any backend.
3. Project committed functional events asynchronously: business journal/outbox -> DynamoDB Streams -> idempotent consumer Lambda -> dedicated console-history DynamoDB table -> authorized read API -> HTML. This is a proposed AWS realization, not a mandate to add SQS/EventBridge as well. Retain durable outbox data separately from the 24-hour stream window; define replay/checkpoint/failure destination, backlog limits and alarms before construction. Consumer retries can repeat projection writes, NEVER execute payments.
4. A failure of ADOT, CloudWatch delivery, the projector or read-model table does not by itself stop new payments. UI shows lag/incompleteness. A failure of mandatory business storage may stop them. Finite durable outbox capacity may cause explicit backpressure; separate this from availability of the observability backend and define limits.
5. Business changes alone do not capture every validation/start/terminal/intermediate task. Every capability still needs explicit common task instrumentation. Specify which task facts are durable producer events and which technical observations may be lost. Starts before authentication/journal creation and crashes before durable event writes must show gaps, not fabricated reconstructed tasks. Do not promise a fully durable trace of every instruction at zero synchronous cost.
6. Original D2 fail-closed rule on a SEPARATE trace-admission store would require amendment: replace that dependency with business-journal/atomic-outbox admission. Preserve no automatic payment resend/reversal after possible send, known-result/uncertainty distinction and no trace-only financial evidence. D1 ISO/HTTPS is unchanged.

## Alternative: recover console events from CloudWatch

Possible pipeline: structured allowlisted function logs -> CloudWatch Logs subscription -> consumer Lambda -> console projection DynamoDB. It reuses log transport but adds extraction/deduplication and has weaker latency/delivery guarantees. Good for a best-effort diagnostic timeline if owner accepts that delay/loss; not the recommendation for the currently proposed seconds-level task UI. Direct browser CloudWatch queries would also require an authorized backend and correct tenant isolation; do not expose broad log credentials.

## Decisions and limits

Recommend the split approach above; not copying every log into DynamoDB. Exact outbox/task producer contracts, freshness/recovery objectives, retention, IAM/diagnostic visibility, runtime compatibility and independent domain/Security review remain pending. Original two-second UI target is proposed, not guaranteed by Streams or documentation. No benchmark, code, configuration, deployment, external write or runtime validation occurred.

Single next owner question: adopt asynchronous technical export and functional-history projection while keeping only the business journal/critical outbox admission on the transaction path? Approval would amend D2's separate trace-store blocking dependency, not approve full D3 or construction.
