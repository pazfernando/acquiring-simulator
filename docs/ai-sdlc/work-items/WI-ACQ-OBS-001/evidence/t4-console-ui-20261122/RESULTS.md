# T4 console UI — local running screen evidence (recovery slice)

Date: 2026-11-23 (UTC). Work item: `WI-ACQ-OBS-001`. Task: owner-authorized local UI slice,
continued after an interruption; **no feature was rebuilt, restarted or re-scoped**.

Binding at start (before any write): branch `feat/wi-acq-auth-001`,
HEAD `5bbd4eb5d49be036e695e5f25a8c83c4f7d5ac14` — matches the expected HEAD (`binding.txt`).

## Running screen

- **URL: <http://127.0.0.1:8100/>** — see `RUNNING.md` for the exact launch command.
- Listener `127.0.0.1:8100`, process PID **1808708** (`server-process.txt`,
  `server-listener.txt`).
- Harness banner: `CONSOLE_DEV_READY http://127.0.0.1:8100/`, `CONSOLE_DEV_MODE dev-stub`.

## Compile

- `compile-command.txt`: `mvn -o -q -DskipTests compile` (offline, JAVA_HOME JDK 25).
- Exit **0**; stdout and stderr both empty (`compile-exit.txt`, `compile-stdout.log`,
  `compile-stderr.log`).
- No compilation blocker was found; no source edit was needed. The existing harness classes
  compiled and were used as found.

## Focused tests (run, not pending)

Command in `focused-tests-command.txt`; full output in `focused-tests-stdout.log` /
`focused-tests-stderr.log`; exit **0** (`focused-tests-exit.txt`).

```
ConsoleDevServerTest            Tests run: 8,  Failures: 0, Errors: 0, Skipped: 0
DevStubTokenIssuerTest          Tests run: 7,  Failures: 0, Errors: 0, Skipped: 0
ConsoleAuthorizationRouteTest   Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
Total: 28, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

Not run here (focused slice only): the DynamoDB-backed atomic integration suite and the full
suite. They were already covered by earlier evidence in this work item; this slice did not
re-run them.

## Probes against the running instance

`http-probe.txt`:

- `GET /` → **200** with CSP, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`,
  `Cache-Control: no-store`, `Content-Length: 9920`.
- `GET /config.json` → **200**, `mode: "dev-stub"`, `stub: true`, same-origin
  `tokenEndpoint: "/dev/v1/stub-token"`, trusted demo context
  `ACQ-SIM-001 / MERCH-SIM-001 / TERM-SIM-001 / USD`, `history.serverReadImplemented: false`.
- Seven static assets (CSS + six ES modules) → **200** with correct content types.

`journey.txt` (token bytes redacted; see `stub-token-response.json`):

- `POST /dev/v1/stub-token` → **200**, `stub: true`, `expiresIn: 900`.
- `POST /console/v1/authorizations` (`SIM-APP-001`, `10.00` USD) → **200**
  `{"authorizationReference":"AUTH-446d76ab9e3b444fb97ea8b4",`
  `"correlationId":"CORR-RECOV234715","outcome":"APPROVED"}`.
- Identical retry → **200**, same reference `AUTH-446d76ab9e3b444fb97ea8b4`, no second
  authorization.
- `Bearer devstub_invalid_token` → **401**, `disposition: "REJECTED"`,
  `error: "UNAUTHENTICATED"`, all business identifiers `null`.

`dom-contract-check.txt`:

- `node --check` passes for all six ES modules.
- All 19 `getElementById` ids referenced by the JS exist in `index.html` (0 missing).

## Slice freeze

The 13 slice files (page, CSS, six modules, five harness classes) hash identically to the
pre-existing `t4-console-ui-20261123/slice-hashes-before.sha256` set:
`slice-hash-diff-vs-20261123-before.txt` is empty (`diff` exit 0). No slice file was edited by
this slice; new paths are only this evidence directory. HEAD was not moved; nothing was staged,
committed, pushed or deployed.

## Honest limitations (do not overclaim)

- **No browser render/screenshot was captured by this session.** HTTP serving is proven; visual
  rendering is still the parent/human step.
- The credential is an **unsigned dev stub, not authentication**: no Keycloak/OIDC round trip,
  no issuer signature, no mTLS, no API Gateway, CloudFront, WAF, Lambda or real AWS call. Only
  the local DynamoDB endpoint `127.0.0.1:8000` is used.
- DynamoDB Local is in-memory, so rows die with that process.
- History is the current tab's in-memory list only; the server-side read is not implemented.
- The response is the route's provisional `{authorizationReference, correlationId, outcome}`
  acknowledgement, not the closed C3 envelope.
- Developer evidence on one working tree — not independent QA, Security review or human
  acceptance, and not a deployed payment. Port `8099` was left untouched.
