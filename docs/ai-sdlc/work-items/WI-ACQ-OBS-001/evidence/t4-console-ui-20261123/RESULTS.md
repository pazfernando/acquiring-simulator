# T4 console UI — running screen (local harness), 2026-11-23

**Purpose.** Give the parent/owner a running, browser-reachable screen URL for the T4 console UI
slice, plus the probe evidence behind it. This run did **not** author, edit or re-run the UI slice:
the page, form, renderer, history view, token seam and loopback harness were already on disk from
the preceding run (timestamps 18:38–18:40 on 2026-11-23). This run only launched the harness and
verified what it serves.

## Running screen

- **URL: <http://127.0.0.1:8100/>** (IPv4 loopback only; no other interface is bound)
- Listener: `127.0.0.1:8100`, process `1800813`
  (`com.acquiring.simulator.console.dev.ConsoleDevHarness --port=8100`)
- Harness banner: `CONSOLE_DEV_READY http://127.0.0.1:8100/`, `CONSOLE_DEV_MODE dev-stub`
- Binding: branch `feat/wi-acq-auth-001`, HEAD `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14`,
  OpenJDK 25.0.5, DynamoDB Local already running at `http://127.0.0.1:8000` (in-memory).
- Port 8099 was **not** touched: the older `LocalPresenterRuntime` JVM from a previous session
  still owns it.

Reproduce:

```
cd /home/pazfernando/projects/acquiring-simulator
JAVA_HOME=/home/pazfernando/.local/opt/jdk-25 \
/home/pazfernando/.local/opt/jdk-25/bin/java \
  -cp "target/classes:$(cat target/classpath.txt)" \
  com.acquiring.simulator.console.dev.ConsoleDevHarness --port=8100
```

## Verified on the running instance

1. `GET /` → **200** with the page CSP (`default-src 'none'; script-src 'self'; ...`),
   `X-Frame-Options: DENY`, `Cache-Control: no-store` — see `http-probe.txt`.
2. `GET /config.json` → **200**, `mode: "dev-stub"`, same-origin `tokenEndpoint
   "/dev/v1/stub-token"`, trusted demo context `ACQ-SIM-001 / MERCH-SIM-001 / TERM-SIM-001 / USD`,
   and `history.serverReadImplemented: false`.
3. All seven static assets (`styles.css`, `app.js`, `form.js`, `render.js`, `session-history.js`,
   `oidc-pkce-client.js`, `dev-stub-provider.js`) → **200**.
4. `POST /dev/v1/stub-token` → **200**, `stub: true`, `expiresIn: 900` (token bytes redacted in
   `stub-token-response.json`).
5. `POST /console/v1/authorizations` with `SIM-APP-001`, amount `10.00`, USD →
   **200** `{"authorizationReference":"AUTH-66cf37eadcda4a938fd3124d","correlationId":
   "CORR-UIDEMO0001","outcome":"APPROVED"}`.
6. Identical retry → **200** with the **same** `authorizationReference` and outcome (replay, no
   second reference).
7. Refused token (`Bearer devstub_invalid_token`) → **401** with `disposition: "REJECTED"`,
   `error: "UNAUTHENTICATED"` and null transaction/authorization/attempt fields.
8. DOM contract check: all 19 `getElementById` ids referenced by `app.js` exist in `index.html`
   (PASS, `dom-contract-check.txt`).
9. `node --check` syntax-checks the six ES modules (all pass).

Full journey transcript: `journey.txt`.

## What this does and does not establish

**Does:** the console screen is actually served over loopback by the harness, its configuration and
module graph load with HTTP 200, and the same-origin journey works against DynamoDB Local — one
authorized synthetic submission reaches the atomic route, returns a provisional acknowledgement,
and an identical retry replays the same reference while a refused token produces 401 with no
business identifiers.

**Does not:** no real browser render was captured here. `brave-browser --headless` could not
produce a DOM dump in this environment (three attempts timed out with no output; see
`headless-brave.log`), so a human/visual screenshot is still the parent's step. There is no
Keycloak, API Gateway, CloudFront, WAF, mTLS, Lambda invocation or real issuer round trip; the
credential is an unsigned dev stub and **not authentication**. The response is the route's
provisional `{authorizationReference, correlationId, outcome}` acknowledgement, not the closed C3
envelope. The history panel is this tab's in-memory list only: the server-side history read is not
implemented. DynamoDB Local is in-memory, so this harness's journal/event rows die with that
process. This is developer evidence on one working tree — not independent QA, Security review or
human acceptance, and not a deployed or integrated payment.

## Freeze

The 13 slice files (page, CSS, six modules, five harness classes) were hashed before and after:
`slice-hash-diff.txt` is empty (identical SHA-256). No slice file was edited; the only new paths are
in this evidence directory. HEAD was not moved and nothing was staged, committed or pushed.
