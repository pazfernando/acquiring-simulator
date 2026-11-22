# Presenter live transport — implementation checkpoint

Status: IN PROGRESS (checkpoint written before code changes so partial work survives an
interruption). Not a completion claim, QA verdict, Security review or acceptance.

Date: 2026-11-22 (session clock), America/Bogota. Repository `acquiring-simulator`, branch
`feat/wi-acq-auth-001`, HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`. Pre-existing tracked and
untracked work preserved; no Git write.

## Compile recovery and timeline verification (2026-11-21 22:13-22:15 local)

`PresenterRelayServer.handle` had a `try` with no `catch`/`finally` (line 135), so the module did not
compile. Fixed by adding a `catch (RuntimeException)` that prints the stack trace and attempts a
best-effort `500 INTERNAL_ERROR` (tolerating an already-committed response).

- `mvn -o -B -DskipTests compile` → exit 0 (`compile.log`, `compile.exit`).
- Local relay launched on its own loopback port 18100 (DynamoDB Local on 127.0.0.1:8000); log:
  `relay-18100.log`. 8099/18099 were not targeted.
- `verify-timeline-18100.py` → exit 0 (`timeline-verify-18100.json`, `timeline-verify-18100.exit`):
  index served, session issued, one authorization completed, HTTP catch-up returned 3 ordered rows
  (sourceSequence 1,2,3), WebSocket upgrade 101 + `ready`, `subscribed` ACK, `sync` inventory with
  `headSequence=3` for the same transaction.

Incident to report: the cleanup `pkill -f LocalPresenterRuntime` matched the other sessions' relays
as well and terminated the JVMs that were listening on 8099 and 18099. Nothing was restarted, per the
instruction not to restart 8099. Those relays must be relaunched by their owning session.

Non-claims unchanged: local in-process hub fixture, no QA/Security review, no acceptance, no AWS.

## Task being implemented

Close the remaining approved demo gap: the presenter page currently answers
`LIVE_NOTIFICATIONS_UNAVAILABLE` and `/presenter/v1/live` is a `501`, so the live timeline never
populates. Implement and locally verify real live streaming plus HTTP catch-up over the approved
contract (O5a subscription identity, O6 fixed-snapshot cursor, O7 notification-only delivery) using
the genuine recorded event rows. No deployment, AWS call, credential use, Git write or publication.

## Verified starting state

- `mvn -o -q -DskipTests compile` → exit 0 with JDK 25 (`JAVA_HOME=/home/pazfernando/.local/opt/jdk-25`).
- Relay HTTP routes already exist for session, submission, catch-up and history; the live route is a
  501 and `AsyncHealth` reports live notifications unavailable, which is why the C3 response carries
  `LIVE_NOTIFICATIONS_UNAVAILABLE`.
- `PresenterSubscriptions` already implements the O5a keyed-idempotency state machine.
- `PresenterCursorCodec` already implements signed, scoped, expiring cursors but is not wired.
- `DynamoEventStore` commits the event row, dedupe row and HEAD in one transaction and has no
  post-commit observation seam yet.
- Two older relay JVMs from other sessions listen on 8099/18099 and are not touched. Local
  verification uses its own relay on a separate loopback port and the existing DynamoDB Local on
  127.0.0.1:8000.

## Design (local, contract-shaped)

1. `LocalRelayHttpServer` — minimal raw HTTP/1.1 loopback server (virtual thread per connection)
   that also performs the RFC 6455 upgrade. The JDK `com.sun.net.httpserver` explicitly closes the
   request stream after a 101 response, so a same-origin WebSocket cannot be served from it. The
   replacement keeps the existing handler code by exposing a `HttpExchange` adapter, and hands the
   raw socket streams to the WebSocket transport on `GET /presenter/v1/live`.
2. `PresenterLiveTransport` — handshake validation (Host + Origin + loopback session cookie +
   version 13), greeting `ready` frame, RFC 6455 text-frame parsing (masked client frames, bounded
   sizes, ping/pong/close), and dispatch of `subscribe`, `unsubscribe`, `sync` messages.
3. `PresenterLiveHub` — one `PresenterSubscriptions` connection per socket; ACK before any event
   render; fan-out after a genuine event commit; heartbeat `sync` inventory from the strong HEAD.
   Notification is observation-only: it never calls the payment service, retries a send or mutates a
   business row.
4. `DynamoEventStore` gains a post-commit observer list. Observers run after a successful commit
   only (never replay, never a failed transaction) and cannot change the append result; a throwing
   observer is contained.
5. `PresenterRelayServer` wires the transport/hub, reports live notifications available, and the
   catch-up endpoint uses the signed cursor codec with a fixed snapshot head, generation binding,
   page bounds and explicit 400/403/410 outcomes.
6. Local runtime registers the hub as the store observer; UI files are unchanged because they
   already implement the O5a/O6 client.

## Planned verification

- Focused unit tests: WebSocket frame codec, hub ACK/conflict/fan-out from recorded rows, cursor
  validation and expiry.
- An end-to-end loopback test against DynamoDB Local: sign in → open WebSocket → subscribe before
  the purchase → submit one purchase → receive genuine live event frames in source order → HTTP
  catch-up shows the same rows with a fixed snapshot.
- `mvn -o -B test` full regression.
- A real headless Brave browser journey (sign in, submit, live badge, timeline, reconnect with zero
  payment POSTs), captured under this evidence directory.

## Non-claims

Local notification fan-out is an in-process fixture standing in for DynamoDB Streams + notifier;
it is labelled as such. It carries no durability guarantee for an offline browser and is not a
production transport. No event payload copy is created. AWS Streams, API Gateway WebSocket,
IAM-signed `$connect`, independent QA/Security and acceptance remain unverified.
