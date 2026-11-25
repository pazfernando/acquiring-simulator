# Smallest construction-ready interfaces (review gate retained)
No code authorized by this packet alone. Coordinator first commissions independent acquiring-domain and Security review of OUTPUTS.SHA256SUMS/exact contracts, identity-map, scenarios and fixtures; no onward delegation from this role. Incorporate only approved corrections, preserving existing direction/continuation authority. Missing review is not a request for generic reauthorization.

## H1 — read-only server history
Suggested Java interfaces (candidate signatures; no application changes here):
`HistoryPage read(VerifiedDemoPrincipal principal, TrustedEnvironment environment, TransactionId target, HistoryQuery query, Instant now)`;
`HistoryQuery = NewSnapshot(afterSequence) | Continue(opaqueCursor)`;
`EventSnapshot headAndQueryAuthorized(TargetScope scope, FixedSnapshot snapshot, PageBounds bounds)`;
`CursorBinding verifyCursor(String cursor, VerifiedDemoPrincipal principal, TrustedEnvironment env, CurrentMandate mandate, Instant now)`.
Current mandate recheck before final disclosure; absence/unavailability returns exact wire error, never empty success. Existing DynamoEventStore is only source; strong HEAD/base Query. No payment gateway/dispatch dependency in route; no financial IAM capability. Initial read can allocate read generation without WS. Do not reuse presenter relay auth/session. Preserve source wrapper/event bytes/hash and O6 page shape. Tests: fixed H with concurrent append; overlap; 400 tamper;403 subject/environment/target;401 expiry;410 cursor/generation/R; expiry-between-read-and-response; source hole versus explicit expired range; HTTP-only WS down; seven-day logical filter; zero payment invocations. Run DynamoDB Local pinned2.6.1 plus route contract tests; retain exact commands/results in Implementation evidence, not inferred here.

## K1 — real issuer decoder and client flow
`VerifiedDemoPrincipal decodeAndVerify(String accessToken, ResourceClass expected, TrustedIssuerConfig config, Instant now)` with ResourceClass HTTP_CONSOLE or WS_OBSERVE;
`CurrentMandate authorize(VerifiedDemoPrincipal principal, TrustedEnvironment env, TargetScope scope)`;
`WsBinding connect(VerifiedDemoPrincipal principal, ExactOrigin origin, Instant now)`.
Keep existing DemoConsoleAuthorizer policy core; replace stub decoder with real signature/JWKS/issuer checks, strict disjoint audience/client/scope rules. Separate pure claim checks from key/registry availability; never fall back to stub or cached authority on failure. SPA uses no-secret PKCE with independent state/nonce per flow; no refresh in K1; no purchase replay on callback. DevOps supplies pinned issuer config/release; no invented production subject or URL.
Tests: all K1 scenarios plus real pinned issuer PKCE happy path and login_required, disjoint optional scopes, both-scope request denial, replayed code/state/nonce, wrong resource/class/service token, rotation/unknown kid, issuer logout/revocation, no storage/log/token echo; deployed-domain WS carriage/redaction and idle close. Independent QA and triggered Security implementation review attach exact changed hashes/environment after construction. Deployment follows required gates and concrete configured authorized destination; authored proposal is not provider proof.
