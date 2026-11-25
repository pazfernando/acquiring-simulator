# T4 console UI — RUNNING (local harness)

Status: **running now** on IPv4 loopback.

- **URL: <http://127.0.0.1:8100/>**
- Listener: `127.0.0.1:8100` (only loopback; see `server-listener.txt`)
- Process: PID **1808708**, owned by this local UI task session
  (`com.acquiring.simulator.console.dev.ConsoleDevHarness --port=8100`)
- Started at: 2026-11-23T23:47Z (UTC) from
  `/home/pazfernando/projects/acquiring-simulator`
- Binding verified before writes: branch `feat/wi-acq-auth-001`,
  HEAD `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` (`binding.txt`).

## Exact launch command

```
cd /home/pazfernando/projects/acquiring-simulator
JAVA_HOME=/home/pazfernando/.local/opt/jdk-25 \
/home/pazfernando/.local/opt/jdk-25/bin/java \
  -cp "target/classes:$(cat target/classpath.txt)" \
  com.acquiring.simulator.console.dev.ConsoleDevHarness --port=8100
```

Harness banner (`server-stdout.log`):

```
CONSOLE_DEV_READY http://127.0.0.1:8100/
CONSOLE_DEV_MODE dev-stub (NOT authentication; no Keycloak, no AWS, no relay)
CONSOLE_DEV_CONTEXT acq=ACQ-SIM-001 merchant=MERCH-SIM-001 terminal=TERM-SIM-001 currency=USD
CONSOLE_DEV_DYNAMODB http://127.0.0.1:8000 journal=console-dev-journal events=console-dev-events
```

## Compile evidence

- Command: `compile-command.txt`
- Result: exit **0**, stdout empty, stderr empty (`compile-exit.txt`,
  `compile-stdout.log`, `compile-stderr.log`), OpenJDK 25.0.5.
- No source was rebuilt or edited by this slice; the existing harness and UI files were used
  as found.

## Explicit limitations (do not overclaim)

- **No browser validation was performed by this session.** The URL is served and probe-checked
  over HTTP; a human/parent screenshot is still the visual check.
- **The credential is an unsigned dev stub, not authentication.** There is no Keycloak, issuer
  signature, OIDC round trip, mTLS, API Gateway, CloudFront, WAF, Lambda invocation or AWS call
  (only the local DynamoDB endpoint on `127.0.0.1:8000`).
- **DynamoDB Local is in-memory** for this run; rows die with that process.
- **History is this tab's in-memory list only**; the server-side history read is not
  implemented (`history.serverReadImplemented: false`).
- The response is the route's provisional
  `{authorizationReference, correlationId, outcome}` acknowledgement, not the closed C3 envelope.
- This is developer evidence on one working tree — not independent QA, Security review or human
  acceptance, and not a deployed or integrated payment.
- Port `8099` was left untouched: an older `LocalPresenterRuntime` JVM still owns it.
