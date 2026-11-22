# P2b8 — internal payload v2 / octet-stream transport primitive: actual results

UTC: 2026-11-20T22:40:49Z start; 22:44Z targeted run; 22:46Z relevant regression; 22:49Z finish.
Model: DeepSeek V4 Flash (provider `nan`), configured default, no override.
Binding: `/home/pazfernando/projects/acquiring-simulator`, branch `feat/wi-acq-auth-001`,
HEAD `cfc456f4f469d9558a78fda8ff9b770b00817315`
(origin `github.com/pazfernando/acquiring-simulator.git`), default configuration.
`head-before.txt` == `head-after.txt` never rewritten; no commit, push or PR.
`existing-files.diff` is empty (exit 0): every file that existed before the edit has the same
SHA-256 after the edit. `added-files.txt` lists the 10 new files only (9 main, 1 test).

## Architecture inputs read before writes

- `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/channel-contract.md` C2 (private octet-stream
  boundary, payload v2 flags, media/profile/size/compression rules, authenticated service
  principal gate) and C4 (one `X-Simulator-Evidence` header, canonical base64, exact body and
  hash binding, no public late endpoint).
- Existing trusted ingress actually present: `ProcessorHandler` / `AwsRuntime` console C1 JWT
  authorizer (`requestContext.authorizer.jwt.claims.sub` → subject registry) and the unbound
  `SignedEvidenceReceiptGateway.TrustedContextAuthorizer` seam. No C2 service-principal
  mapping exists.

## Delivered (new files only)

Main:
- `HttpApiV2Wire` — strict event/base64/header/compression/bound helpers.
- `HttpApiV2TransportViolation`, `HttpApiV2TransportException` — sanitized refusals.
- `HttpApiV2Request`, `HttpApiV2RequestDecoder` — exact payload v2 octet-stream request.
- `HttpApiV2Response`, `HttpApiV2ResponseDecoder`, `HttpApiV2ResponseEncoder` — exact response.
- `C4HttpCarriageBridge` — normal 200 carriage with exactly one evidence header and mapping to
  the existing `C4TransportResponse` seam.

Test:
- `HttpApiV2InternalRouteTest` — 5 focused tests.

No existing production or test file was edited. No specification, vector, `pom.xml` or resource
file was edited.

## Commands and outcomes (logs captured before analysis)

```
JAVA_HOME=/home/pazfernando/.local/opt/jdk-25 \
  /home/pazfernando/.local/opt/maven/bin/mvn -o -q -DskipTests compile
```
- exit **0** (`compile-exit.txt`), empty stdout/stderr.

```
JAVA_HOME=/home/pazfernando/.local/opt/jdk-25 \
  /home/pazfernando/.local/opt/maven/bin/mvn -o -q -DskipTests test-compile
```
- exit **0** (`test-compile-exit.txt`).

```
JAVA_HOME=/home/pazfernando/.local/opt/jdk-25 \
  /home/pazfernando/.local/opt/maven/bin/mvn -o -q \
  -Dtest=HttpApiV2InternalRouteTest test
```
- exit **0** (`targeted-exit.txt`), stderr empty.
- Surefire: **Tests run: 5, Failures: 0, Errors: 0, Skipped: 0**, 0.334 s.
- XML: `tests="5" errors="0" skipped="0" failures="0" flakes="0"`.

```
JAVA_HOME=/home/pazfernando/.local/opt/jdk-25 \
  /home/pazfernando/.local/opt/maven/bin/mvn -o -q \
  -Dtest=C4CarrierParserTest,C4LateDeliveryTest test
```
- exit **0** (`regression-exit.txt`), stderr empty.
- `C4CarrierParserTest` **18/18**; `C4LateDeliveryTest` **23/23**; 0 failures/errors/skips.

## Exact coverage (five tests)

1. `retainsExactOctetStreamBytesAcrossPayloadV2RequestDecoding`
   - 2048-byte body containing every byte value, base64 payload v2 event → exact decoded bytes,
     approved media/profile, body not exposed in `toString`.
2. `refusesNonCanonicalBase64BoundsAndUnsupportedEncoding`
   - `isBase64Encoded=false` → `UNSUPPORTED_ENCODING`; `AB==` → `BASE64_NONCANONICAL`;
     2049 bytes → `BODY_SIZE`; empty body → `BODY_SIZE`.
3. `refusesWrongMediaProfileCompressionAndRouteBeforeBusinessAdmission`
   - `application/json` → `UNSUPPORTED_MEDIA`; wrong profile → `UNSUPPORTED_PROFILE`;
     `Content-Encoding: gzip` and gzip magic → `COMPRESSION`; wrong path/method → `ROUTE`;
     mismatched `Content-Length` → `CONTENT_LENGTH_MISMATCH`. All refusals are pure decoder
     outcomes before any business or ISO call.
4. `forgedIdentityHeadersCannotCreateTrustedSender`
   - Request `X-Authenticated-Sender` → `IDENTITY_ASSERTION`.
   - Response forged identity header is inert; the explicit `context(ISSUER)` binds
     `DIRECT_SENDER_MATCHED`, while `context(ATTACKER)` is refused by the real C4 parser with
     `AUTHENTICATED_SENDER`. The header cannot create trust.
5. `c4CarriageKeepsExactBodyAndExactlyOneCanonicalEvidenceHeader`
   - Normal 200 carries the exact body and one evidence header whose base64 decodes to the exact
     canonical envelope bytes; `C4CarrierParser` reads the exact body hash and sender binding.
     A case-variant duplicate evidence header is refused by the response decoder and by the
     encoder.

## Trust boundary and exact missing mapping

No authentication was implemented. `C4HttpCarriageBridge` accepts a
`C4AuthenticatedTransportContext` supplied by the platform boundary; it never derives one from
a header, body, JWT claim or query parameter. The exact missing mapping is the **C2
authenticated service-principal realization** (private API Gateway IAM/SigV4 service identity
or mutual TLS) that must construct that context. It does not exist in the repository today:
only the console C1 JWT authorizer exists, and the signed-evidence gateways still take the
unbound `TrustedContextAuthorizer` seam. No principal allowlist, unsigned fallback or new auth
pattern was invented. The C2 ingress realization remains a human architecture/Security gate.

## Not implemented / not claimed

No HTTP endpoint or runtime activation, no production principal mapping, no key custody or
rotation, no signature verification change, no public late-delivery endpoint, no ISO8583
parse/pack, no storage write, no send, no new identifier or receipt allocation, no result
fabrication, no capacity approval, no AWS call and no deployment. Independent QA, Security
review, P1 G6 Security and human acceptance remain open.
