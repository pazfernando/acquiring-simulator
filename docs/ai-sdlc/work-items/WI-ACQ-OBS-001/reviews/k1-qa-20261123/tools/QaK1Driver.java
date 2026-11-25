package com.acquiring.simulator.console.jwt;

import com.acquiring.simulator.console.ConsoleAuthenticationException;
import com.acquiring.simulator.console.ConsoleAuthenticationException.Reason;
import com.acquiring.simulator.console.ConsoleAuthorizer;
import com.acquiring.simulator.console.ConsoleAuthorizationRoute;
import com.acquiring.simulator.console.ConsoleHttpRequest;
import com.acquiring.simulator.console.ConsoleHttpResponse;
import com.acquiring.simulator.console.ConsolePrincipal;
import com.acquiring.simulator.console.ConsoleRateLimitHook;
import com.acquiring.simulator.console.ConsoleTokenClaims;
import com.acquiring.simulator.console.ConsoleTokenDecoder;
import com.acquiring.simulator.console.dev.DevStubTokenIssuer;
import com.acquiring.simulator.domain.AuthorizationService;
import com.acquiring.simulator.domain.model.AuthorizationResult;
import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.model.Outcome;
import com.acquiring.simulator.domain.model.PurchaseRequest;
import com.acquiring.simulator.domain.model.Route;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Independent QA driver for slice K1 (2026-11-23). QA-owned file, not production code.
 *
 * <p>All keys are generated locally in this JVM. The only HTTP is a loopback
 * {@code 127.0.0.1} fixture server used to exercise {@link HttpJwksSource}; no external
 * network, issuer or browser is contacted.</p>
 */
public final class QaK1Driver {

    static final ObjectMapper JSON = new ObjectMapper();
    static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");
    static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);
    static final String ISSUER = "https://auth.sim.test/realms/sim";
    static final String AUDIENCE = "console-api";
    static final String WS_AUDIENCE = "ws-api";
    static final String SUBJECT = "demo-user";
    static final String CLIENT = "console-demo";
    static final String SCOPE = "console.demo";
    static final String WS_SCOPE = "ws.observe";

    static final KeyPair KEY_A = rsa();
    static final KeyPair KEY_B = rsa();
    static final KeyPair KEY_C = rsa();

    static int passed;
    static int failed;
    static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        configurationChecks();
        decoderAcceptanceChecks();
        decoderRefusalChecks();
        rotationChecks();
        noVerificationSkipSweep();
        keySourceOutageChecks();
        jwksParserChecks();
        loopbackHttpJwksSourceChecks();
        compositionAndRouteChecks();
        devStubIsolationChecks();
        productionCompositionChecks();

        System.out.println();
        System.out.println("QA driver summary: " + passed + " PASS, " + failed + " FAIL");
        if (!failures.isEmpty()) {
            System.out.println("FAILED CHECKS:");
            failures.forEach(f -> System.out.println("  FAIL " + f));
        }
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ---------------------------------------------------------------- configuration

    static void configurationChecks() {
        TrustedIssuerConfiguration base = configuration();

        check("C1 configuration exposes exactly the explicit values", 
                ISSUER.equals(base.issuer()) && AUDIENCE.equals(base.audience())
                        && SUBJECT.equals(base.demoSubject()) && CLIENT.equals(base.demoClientId())
                        && SCOPE.equals(base.requiredScope()) && WS_SCOPE.equals(base.forbiddenScope())
                        && WS_AUDIENCE.equals(base.forbiddenAudience())
                        && Set.of("RS256").equals(base.allowedAlgorithms())
                        && Duration.ofSeconds(900).equals(base.maxTokenLifetime()));

        expectIllegal("C2 blank issuer fails construction", () -> new TrustedIssuerConfiguration(
                " ", AUDIENCE, URI.create(ISSUER + "/certs"), SUBJECT, CLIENT, SCOPE,
                WS_SCOPE, WS_AUDIENCE, Set.of("RS256"), Duration.ofSeconds(900)));
        expectIllegal("C3 configured ES256 fails construction", () -> new TrustedIssuerConfiguration(
                ISSUER, AUDIENCE, URI.create(ISSUER + "/certs"), SUBJECT, CLIENT, SCOPE,
                WS_SCOPE, WS_AUDIENCE, Set.of("RS256", "ES256"), Duration.ofSeconds(900)));
        expectIllegal("C4 lifetime 901s exceeds the 900s ceiling", () ->
                new TrustedIssuerConfiguration(ISSUER, AUDIENCE, URI.create(ISSUER + "/certs"),
                        SUBJECT, CLIENT, SCOPE, WS_SCOPE, WS_AUDIENCE, Set.of("RS256"),
                        Duration.ofSeconds(901)));
        expectIllegal("C5 identical required/forbidden scopes fail construction", () ->
                new TrustedIssuerConfiguration(ISSUER, AUDIENCE, URI.create(ISSUER + "/certs"),
                        SUBJECT, CLIENT, SCOPE, SCOPE, WS_AUDIENCE, Set.of("RS256"),
                        Duration.ofSeconds(900)));
        expectIllegal("C6 identical required/forbidden audiences fail construction", () ->
                new TrustedIssuerConfiguration(ISSUER, AUDIENCE, URI.create(ISSUER + "/certs"),
                        SUBJECT, CLIENT, SCOPE, WS_SCOPE, AUDIENCE, Set.of("RS256"),
                        Duration.ofSeconds(900)));
        expectIllegal("C7 non-loopback plain-http jwksUri fails construction", () ->
                new TrustedIssuerConfiguration(ISSUER, AUDIENCE, URI.create("http://auth.sim.test/certs"),
                        SUBJECT, CLIENT, SCOPE, WS_SCOPE, WS_AUDIENCE, Set.of("RS256"),
                        Duration.ofSeconds(900)));
        check("C8 loopback http jwksUri is accepted for local use",
                canConstruct(URI.create("http://127.0.0.1:8199/certs")));
    }

    // ---------------------------------------------------------------- decoder acceptance

    static void decoderAcceptanceChecks() {
        JwtConsoleTokenDecoder decoder = decoder(keySet("key-a", KEY_A));

        ConsoleTokenClaims claims = decodeOf(decoder, token(KEY_A, "key-a", defaultClaims()));
        check("D1 valid RS256 token accepted with the verified claim shape",
                ISSUER.equals(claims.issuer())
                        && List.of(AUDIENCE).equals(claims.audience())
                        && SUBJECT.equals(claims.subject())
                        && CLIENT.equals(claims.authorizedParty())
                        && List.of(SCOPE).equals(claims.scopes())
                        && NOW.minusSeconds(60).equals(claims.issuedAt())
                        && NOW.minusSeconds(60).equals(claims.notBefore())
                        && NOW.plusSeconds(600).equals(claims.expiresAt()));

        Map<String, Object> stringAud = defaultClaims();
        stringAud.put("aud", AUDIENCE);
        ConsoleTokenClaims stringAudClaims = decodeOf(decoder, token(KEY_A, "key-a", stringAud));
        check("D2 single-string aud accepted and normalised to a one-element list",
                List.of(AUDIENCE).equals(stringAudClaims.audience()));

        Map<String, Object> exactCeiling = defaultClaims();
        exactCeiling.remove("nbf");
        exactCeiling.put("iat", NOW.minusSeconds(60).getEpochSecond());
        exactCeiling.put("exp", NOW.plusSeconds(840).getEpochSecond());
        check("D3 lifetime of exactly 900 seconds accepted",
                decodeOf(decoder, token(KEY_A, "key-a", exactCeiling)) != null);

        JwtConsoleTokenDecoder rs512Decoder = new JwtConsoleTokenDecoder(
                new TrustedIssuerConfiguration(ISSUER, AUDIENCE, URI.create(ISSUER + "/certs"),
                        SUBJECT, CLIENT, SCOPE, WS_SCOPE, WS_AUDIENCE,
                        Set.of("RS256", "RS384", "RS512"), Duration.ofSeconds(900)),
                new FixedSource(keySet("key-a", KEY_A)), FIXED);
        check("D4 RS512 accepted when configured",
                decodeOf(rs512Decoder, token(KEY_A, "key-a", defaultClaims(), "RS512")) != null);

        JwtConsoleTokenDecoder rs384Decoder = new JwtConsoleTokenDecoder(
                new TrustedIssuerConfiguration(ISSUER, AUDIENCE, URI.create(ISSUER + "/certs"),
                        SUBJECT, CLIENT, SCOPE, WS_SCOPE, WS_AUDIENCE,
                        Set.of("RS256", "RS384"), Duration.ofSeconds(900)),
                new FixedSource(keySet("key-a", KEY_A)), FIXED);
        check("D5 RS384 accepted when configured",
                decodeOf(rs384Decoder, token(KEY_A, "key-a", defaultClaims(), "RS384")) != null);
    }

    // ---------------------------------------------------------------- decoder refusals

    static void decoderRefusalChecks() {
        CountingSource source = new CountingSource(keySet("key-a", KEY_A));
        JwtConsoleTokenDecoder decoder = new JwtConsoleTokenDecoder(configuration(), source, FIXED);

        refuse("E2.1 absent alg refused before any key consult", decoder, source,
                token(KEY_A, "key-a", defaultClaims(), null), Reason.UNSUPPORTED_ALGORITHM, 0, 0);
        refuse("E2.2 alg none with a non-empty signature refused, no key consult", decoder, source,
                tokenWithHeader(algHeader("none", "key-a"), defaultClaims(),
                        new byte[]{1, 2, 3}),
                Reason.UNSUPPORTED_ALGORITHM, 0, 0);
        check("E2.2b alg none with an empty signature segment refused (malformed shape)",
                reasonOf(decoder, tokenWithHeader(algHeader("none", "key-a"),
                        defaultClaims(), new byte[0])) != null);

        refuse("E2.3 HMAC HS256 with a genuine HMAC signature refused, no key consult", decoder,
                source, hmacToken("HS256", defaultClaims()), Reason.UNSUPPORTED_ALGORITHM, 0, 0);
        refuse("E2.4 HMAC HS384 with a genuine HMAC signature refused", decoder, source,
                hmacToken("HS384", defaultClaims()), Reason.UNSUPPORTED_ALGORITHM, 0, 0);
        refuse("E2.5 HMAC HS512 with a genuine HMAC signature refused", decoder, source,
                hmacToken("HS512", defaultClaims()), Reason.UNSUPPORTED_ALGORITHM, 0, 0);

        refuse("E2.6 ES256 refused", decoder, source,
                token(KEY_A, "key-a", defaultClaims(), "ES256"), Reason.UNSUPPORTED_ALGORITHM, 0, 0);
        refuse("E2.7 PS256 (valid RSA alg, not configured) refused", decoder, source,
                token(KEY_A, "key-a", defaultClaims(), "PS256"), Reason.UNSUPPORTED_ALGORITHM, 0, 0);

        refuse("E2.8 missing kid refused, no key consult", decoder, source,
                token(KEY_A, null, defaultClaims()), Reason.UNKNOWN_KEY, 0, 0);
        refuse("E2.9 blank kid refused", decoder, source,
                token(KEY_A, "  ", defaultClaims()), Reason.UNKNOWN_KEY, 0, 0);

        refuse("E2.10 signature by a different key than the named kid refused", decoder, source,
                token(KEY_B, "key-a", defaultClaims()), Reason.BAD_SIGNATURE, 1, 0);

        Map<String, Object> tampered = defaultClaims();
        tampered.put("sub", "someone-else");
        String original = token(KEY_A, "key-a", defaultClaims());
        refuse("E2.11 tampered payload refused although header/signature bytes intact", decoder,
                source, swapPayload(original, tampered), Reason.BAD_SIGNATURE, 1, 0);

        ObjectNode retypedHeader = algHeader("RS256", "key-a");
        retypedHeader.put("typ", "JWE");
        refuse("E2.12 modified header with the original signature refused", decoder, source,
                headerSwapped(original, retypedHeader), Reason.BAD_SIGNATURE, 1, 0);

        Map<String, Object> wrongIss = defaultClaims();
        wrongIss.put("iss", "https://other.test/realms/sim");
        refuse("E2.13 wrong issuer refused after signature verification", decoder, source,
                token(KEY_A, "key-a", wrongIss), Reason.WRONG_ISSUER, 1, 0);
        Map<String, Object> noIss = defaultClaims();
        noIss.remove("iss");
        refuse("E2.14 missing issuer refused", decoder, source,
                token(KEY_A, "key-a", noIss), Reason.WRONG_ISSUER, 1, 0);

        Map<String, Object> wrongAud = defaultClaims();
        wrongAud.put("aud", List.of("other-api"));
        refuse("E2.15 single wrong audience refused", decoder, source,
                token(KEY_A, "key-a", wrongAud), Reason.WRONG_AUDIENCE, 1, 0);
        Map<String, Object> dualAud = defaultClaims();
        dualAud.put("aud", List.of(AUDIENCE, WS_AUDIENCE));
        refuse("E2.16 dual-resource audience [console, ws] refused", decoder, source,
                token(KEY_A, "key-a", dualAud), Reason.WRONG_AUDIENCE, 1, 0);
        Map<String, Object> mixedAud = defaultClaims();
        mixedAud.put("aud", List.of(AUDIENCE, "other-api"));
        refuse("E2.17 audience [console, other] refused", decoder, source,
                token(KEY_A, "key-a", mixedAud), Reason.WRONG_AUDIENCE, 1, 0);
        Map<String, Object> duplicateAud = defaultClaims();
        duplicateAud.put("aud", List.of(AUDIENCE, AUDIENCE));
        refuse("E2.18 duplicated audience refused", decoder, source,
                token(KEY_A, "key-a", duplicateAud), Reason.WRONG_AUDIENCE, 1, 0);

        Map<String, Object> wrongAzp = defaultClaims();
        wrongAzp.put("azp", "svc-console");
        refuse("E2.19 wrong authorized party refused", decoder, source,
                token(KEY_A, "key-a", wrongAzp), Reason.WRONG_AUTHORIZED_PARTY, 1, 0);

        Map<String, Object> noScope = defaultClaims();
        noScope.remove("scope");
        refuse("E2.20 missing scope refused", decoder, source,
                token(KEY_A, "key-a", noScope), Reason.MISSING_SCOPE, 1, 0);
        Map<String, Object> openidOnly = defaultClaims();
        openidOnly.put("scope", "openid");
        refuse("E2.21 openid-only scope refused", decoder, source,
                token(KEY_A, "key-a", openidOnly), Reason.MISSING_SCOPE, 1, 0);

        Map<String, Object> wsScope = defaultClaims();
        wsScope.put("scope", SCOPE + " " + WS_SCOPE);
        refuse("E2.22 token carrying the WS resource scope refused (disjoint, not narrowed)",
                decoder, source, token(KEY_A, "key-a", wsScope), Reason.WRONG_SCOPE, 1, 0);

        Map<String, Object> expiredAtNow = defaultClaims();
        expiredAtNow.put("exp", NOW.getEpochSecond());
        refuse("E2.23 token with exp == now refused", decoder, source,
                token(KEY_A, "key-a", expiredAtNow), Reason.EXPIRED, 1, 0);
        Map<String, Object> expiredPast = defaultClaims();
        expiredPast.put("exp", NOW.minusSeconds(1).getEpochSecond());
        refuse("E2.24 token expired one second ago refused", decoder, source,
                token(KEY_A, "key-a", expiredPast), Reason.EXPIRED, 1, 0);

        Map<String, Object> futureNbf = defaultClaims();
        futureNbf.put("nbf", NOW.plusSeconds(60).getEpochSecond());
        refuse("E2.25 future nbf refused", decoder, source,
                token(KEY_A, "key-a", futureNbf), Reason.NOT_YET_VALID, 1, 0);
        Map<String, Object> futureIat = defaultClaims();
        futureIat.put("iat", NOW.plusSeconds(60).getEpochSecond());
        futureIat.remove("nbf");
        refuse("E2.26 future iat refused", decoder, source,
                token(KEY_A, "key-a", futureIat), Reason.NOT_YET_VALID, 1, 0);

        Map<String, Object> noExp = defaultClaims();
        noExp.remove("exp");
        refuse("E2.27 missing exp refused", decoder, source,
                token(KEY_A, "key-a", noExp), Reason.MALFORMED_TOKEN, 1, 0);
        Map<String, Object> noIat = defaultClaims();
        noIat.remove("iat");
        refuse("E2.28 missing iat refused", decoder, source,
                token(KEY_A, "key-a", noIat), Reason.MALFORMED_TOKEN, 1, 0);

        Map<String, Object> overCeiling = defaultClaims();
        overCeiling.remove("nbf");
        overCeiling.put("iat", NOW.getEpochSecond());
        overCeiling.put("exp", NOW.plusSeconds(901).getEpochSecond());
        refuse("E2.29 lifetime 901s refused", decoder, source,
                token(KEY_A, "key-a", overCeiling), Reason.TOKEN_LIFETIME_EXCEEDED, 1, 0);

        Map<String, Object> zeroLifetime = defaultClaims();
        zeroLifetime.remove("nbf");
        zeroLifetime.put("iat", NOW.getEpochSecond());
        zeroLifetime.put("exp", NOW.getEpochSecond());
        refuse("E2.30 zero lifetime token refused (exp==iat==now is caught by the expiry check first)",
                decoder, source, token(KEY_A, "key-a", zeroLifetime), Reason.EXPIRED, 1, 0);
        Map<String, Object> negativeLifetime = defaultClaims();
        negativeLifetime.remove("nbf");
        negativeLifetime.put("iat", NOW.getEpochSecond());
        negativeLifetime.put("exp", NOW.minusSeconds(10).getEpochSecond());
        refuse("E2.31 negative lifetime token refused (exp before iat)", decoder, source,
                token(KEY_A, "key-a", negativeLifetime), Reason.EXPIRED, 1, 0);

        List<String> malformed = List.of("not-a-jwt", "a.b", "a.b.c.d", ".", "..",
                "aaa.bbb.ccc", token(KEY_A, "key-a", defaultClaims()) + ".",
                token(KEY_A, "key-a", defaultClaims()).split("\\.", -1)[0] + "."
                        + new String(new char[16300]).replace('\0', 'x') + ".c");
        boolean allMalformedRefused = true;
        for (String candidate : malformed) {
            if (reasonOf(decoder, candidate) != Reason.MALFORMED_TOKEN) {
                allMalformedRefused = false;
            }
        }
        check("E2.32 malformed compact shapes all refused as MALFORMED_TOKEN", allMalformedRefused);
        check("E2.33 null token refused",
                reasonOf(decoder, null) == Reason.MALFORMED_TOKEN);
        check("E2.34 blank token refused",
                reasonOf(decoder, "   ") == Reason.MALFORMED_TOKEN);

        check("E2.35 token above the 16 KiB input limit refused",
                reasonOf(decoder, "x".repeat(16385)) == Reason.MALFORMED_TOKEN);

        ObjectNode critHeader = algHeader("RS256", "key-a");
        critHeader.putArray("crit").add("exp");
        refuse("E2.36 non-empty crit header refused", decoder, source,
                tokenWithHeader(critHeader, defaultClaims(),
                        sign(KEY_A, critHeader, defaultClaims())),
                Reason.MALFORMED_TOKEN, 0, 0);

        ObjectNode jwkHeader = algHeader("RS256", "key-a");
        jwkHeader.set("jwk", jwkOf(KEY_C.getPublic(), "attacker"));
        jwkHeader.put("jku", "https://attacker.test/keys");
        refuse("E2.37 header jwk/jku never selected: attacker-signed token with embedded jwk refused",
                decoder, source, tokenWithHeader(jwkHeader, defaultClaims(),
                        sign(KEY_C, jwkHeader, defaultClaims())), Reason.BAD_SIGNATURE, 1, 0);
    }

    // ---------------------------------------------------------------- rotation

    static void rotationChecks() {
        CountingSource rotationSource = new CountingSource(keySet("key-a", KEY_A),
                keySet("key-b", KEY_B));
        JwtConsoleTokenDecoder rotationDecoder =
                new JwtConsoleTokenDecoder(configuration(), rotationSource, FIXED);
        ConsoleTokenClaims rotated = decodeOf(rotationDecoder,
                token(KEY_B, "key-b", defaultClaims()));
        check("R1 new kid accepted after exactly one refresh (1 current + 1 refresh)",
                rotated != null && rotationSource.currentCalls == 1
                        && rotationSource.refreshCalls == 1);

        CountingSource retiredSource = new CountingSource(keySet("key-b", KEY_B),
                keySet("key-b", KEY_B));
        JwtConsoleTokenDecoder retiredDecoder =
                new JwtConsoleTokenDecoder(configuration(), retiredSource, FIXED);
        refuse("R2 retired kid refused after exactly one refresh", retiredDecoder, retiredSource,
                token(KEY_A, "key-a", defaultClaims()), Reason.UNKNOWN_KEY, 1, 1);

        CountingSource pinSource = new CountingSource(keySet("key-a", KEY_A));
        JwtConsoleTokenDecoder pinDecoder = new JwtConsoleTokenDecoder(configuration(), pinSource, FIXED);
        decodeOf(pinDecoder, token(KEY_A, "key-a", defaultClaims()));
        pinSource.replaceSet(keySet("key-b", KEY_B));
        check("R3 no key pinned: after the source rotates away key-a, a key-a token is refused",
                reasonOf(pinDecoder, token(KEY_A, "key-a", defaultClaims()))
                        == Reason.UNKNOWN_KEY);
        check("R4 no key pinned: rotation away does not invalidate the still-current key-b",
                pinSource.replaceSet(keySet("key-b", KEY_B))
                        && decodeOf(pinDecoder, token(KEY_B, "key-b", defaultClaims())) != null);

        CountingSource downgradeSource = new CountingSource(keySet("key-a", KEY_A), null);
        JwtConsoleTokenDecoder downgradeDecoder =
                new JwtConsoleTokenDecoder(configuration(), downgradeSource, FIXED);
        decodeOf(downgradeDecoder, token(KEY_A, "key-a", defaultClaims()));
        downgradeSource.replaceSet(keySet("key-a", KEY_A));
        decodeOf(downgradeDecoder, token(KEY_A, "key-a", defaultClaims()));
        check("R5 successful lookups do not bypass re-resolution (current consulted on every decode)",
                downgradeSource.currentCalls == 2 && downgradeSource.refreshCalls == 0);
    }

    // ---------------------------------------------------------------- no-skip sweep

    static void noVerificationSkipSweep() {
        CountingSource source = new CountingSource(keySet("key-a", KEY_A));
        JwtConsoleTokenDecoder decoder = new JwtConsoleTokenDecoder(configuration(), source, FIXED);
        List<String> mustRefuse = new ArrayList<>();
        mustRefuse.add(token(KEY_C, "attacker", trustedClaims())); // attacker key, unknown kid
        mustRefuse.add(tokenWithHeader(algHeader("none", null), trustedClaims(), new byte[0]));
        mustRefuse.add(hmacToken("HS256", trustedClaims()));
        mustRefuse.add(token(KEY_C, "key-a", trustedClaims())); // right kid, wrong key
        mustRefuse.add(token(KEY_A, "key-a", defaultClaims()) + "x");
        mustRefuse.add(" " + token(KEY_A, "key-a", defaultClaims()));
        mustRefuse.add(token(KEY_A, "key-a", defaultClaims(), "RS256").toUpperCase());

        boolean allRefused = true;
        List<String> leaks = new ArrayList<>();
        for (String candidate : mustRefuse) {
            try {
                ConsoleTokenClaims leaked = decoder.decode(candidate, NOW);
                if (leaked != null) {
                    allRefused = false;
                    leaks.add(candidate.substring(0, Math.min(40, candidate.length())));
                }
            } catch (ConsoleAuthenticationException refused) {
                // expected
            } catch (RuntimeException unexpected) {
                allRefused = false;
                leaks.add("unexpected " + unexpected.getClass().getSimpleName());
            }
        }
        check("X1 no input produces claims without verification (" + mustRefuse.size()
                + " adversarial inputs)", allRefused && leaks.isEmpty());
    }

    // ---------------------------------------------------------------- key source outage

    static void keySourceOutageChecks() {
        CountingSource broken = new CountingSource(keySet("key-a", KEY_A));
        broken.failCurrent = true;
        JwtConsoleTokenDecoder brokenDecoder =
                new JwtConsoleTokenDecoder(configuration(), broken, FIXED);
        refuse("K1 current() outage refused as KEY_SOURCE_UNAVAILABLE", brokenDecoder, broken,
                token(KEY_A, "key-a", defaultClaims()), Reason.KEY_SOURCE_UNAVAILABLE, 1, 0);

        CountingSource brokenRefresh = new CountingSource(keySet("key-b", KEY_B), null);
        brokenRefresh.failRefresh = true;
        JwtConsoleTokenDecoder brokenRefreshDecoder =
                new JwtConsoleTokenDecoder(configuration(), brokenRefresh, FIXED);
        refuse("K2 refresh() outage refused as KEY_SOURCE_UNAVAILABLE", brokenRefreshDecoder,
                brokenRefresh, token(KEY_A, "key-a", defaultClaims()),
                Reason.KEY_SOURCE_UNAVAILABLE, 1, 1);

        CountingSource throwing = new CountingSource(keySet("key-a", KEY_A)) {
            @Override public JsonWebKeySet current() {
                currentCalls++;
                throw new IllegalStateException("unexpected source crash");
            }
        };
        JwtConsoleTokenDecoder throwingDecoder =
                new JwtConsoleTokenDecoder(configuration(), throwing, FIXED);
        check("K3 a source that fails unexpectedly still fails closed",
                reasonOf(throwingDecoder, token(KEY_A, "key-a", defaultClaims()))
                        == Reason.KEY_SOURCE_UNAVAILABLE);
    }

    // ---------------------------------------------------------------- JwksParser

    static void jwksParserChecks() {
        JsonWebKeySet parsed = JwksParser.parse(jwksDocument("parser-a", KEY_A.getPublic()));
        check("P1 parser accepts a valid RSA sig key and resolves it for verification",
                parsed.size() == 1 && decodeOf(decoder(parsed), token(KEY_A, "parser-a", defaultClaims())) != null);

        ObjectNode mixed = JSON.createObjectNode();
        var keys = mixed.putArray("keys");
        ObjectNode ec = keys.addObject();
        ec.put("kty", "EC").put("kid", "ec-1").put("crv", "P-256");
        ObjectNode enc = keys.addObject();
        enc.put("kty", "RSA").put("kid", "enc-1").put("use", "enc");
        ObjectNode sig = keys.addObject();
        sig.put("kty", "RSA").put("kid", "sig-1").put("use", "sig");
        RSAPublicKey rsa = (RSAPublicKey) KEY_A.getPublic();
        sig.put("n", b64(rsa.getModulus())).put("e", b64(rsa.getPublicExponent()));
        ObjectNode missing = keys.addObject();
        missing.put("kty", "RSA").put("kid", "no-key");
        JsonWebKeySet filtered = JwksParser.parse(mixed.toString());
        check("P2 parser keeps only RSA sig keys and skips EC/enc/incomplete keys",
                filtered.size() == 1 && filtered.key("sig-1") != null);

        expectJwksFailure("P3 document without a keys array refused", "{\"keys\":1}");
        expectJwksFailure("P4 duplicate kid refused", duplicateKidDocument());
        expectJwksFailure("P5 non-JSON document refused", "not json");
        expectJwksFailure("P6 blank document refused", "   ");
        expectJwksFailure("P7 RSA key shorter than 2048 bits refused", shortKeyDocument());

        JsonWebKeySet ecOnly = JwksParser.parse("{\"keys\":[{\"kty\":\"EC\",\"kid\":\"e\"}]}");
        check("P8 an EC-only set is empty and the decoder refuses on it",
                ecOnly.size() == 0 && reasonOf(decoder(ecOnly), token(KEY_A, "e", defaultClaims()))
                        == Reason.UNKNOWN_KEY);
    }

    // ---------------------------------------------------------------- HttpJwksSource over loopback

    static void loopbackHttpJwksSourceChecks() throws IOException {
        AtomicReference<String> body = new AtomicReference<>(jwksDocument("srv-a", KEY_A.getPublic()));
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/certs", exchange -> {
            hits.incrementAndGet();
            respond(exchange, 200, body.get());
        });
        server.createContext("/redirect", exchange ->
                respond(exchange, 302, null, "/certs"));
        server.createContext("/denied", exchange -> respond(exchange, 403, "{}"));
        server.createContext("/broken", exchange -> respond(exchange, 200, "not json"));
        byte[] big = new byte[70 * 1024];
        server.createContext("/big", exchange -> respond(exchange, 200,
                new String(big, StandardCharsets.US_ASCII)));
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        try {
            String root = "http://127.0.0.1:" + server.getAddress().getPort();

            HttpJwksSource source = new HttpJwksSource(URI.create(root + "/certs"));
            JsonWebKeySet first = source.current();
            int afterFirst = hits.get();
            source.current();
            check("H1 loopback JWKS served and parsed; current() served from cache (1 request)",
                    first.size() == 1 && first.key("srv-a") != null && hits.get() == afterFirst);

            body.set(jwksDocument("srv-b", KEY_B.getPublic()));
            JsonWebKeySet refreshed = source.refresh();
            check("H2 refresh re-fetches and publishes the rotated key id",
                    refreshed.key("srv-b") != null && refreshed.key("srv-a") == null);
            check("H3 the refreshed set makes a rotated token verifiable",
                    decodeOf(refreshable(source), token(KEY_B, "srv-b", defaultClaims())) != null);

            expectJwksFailure("H4 non-200 status is a failure",
                    () -> new HttpJwksSource(URI.create(root + "/denied")).current());
            expectJwksFailure("H5 redirects are never followed",
                    () -> new HttpJwksSource(URI.create(root + "/redirect")).current());
            expectJwksFailure("H6 non-JSON body refused",
                    () -> new HttpJwksSource(URI.create(root + "/broken")).current());
            expectJwksFailure("H7 oversized document refused",
                    () -> new HttpJwksSource(URI.create(root + "/big")).current());
        } finally {
            server.stop(0);
        }
    }

    // ---------------------------------------------------------------- composition / route

    static void compositionAndRouteChecks() {
        CountingSource source = new CountingSource(keySet("key-a", KEY_A));
        ConsoleAuthorizer authorizer = composition(configuration(), source);

        ConsolePrincipal principal = authorizer.authorize("Bearer " + token(KEY_A, "key-a", defaultClaims()), NOW);
        check("F1 composition accepts a valid token and yields the verified principal",
                ISSUER.equals(principal.issuer()) && SUBJECT.equals(principal.subject())
                        && CLIENT.equals(principal.authorizedParty())
                        && principal.audience().contains(AUDIENCE)
                        && principal.scopes().contains(SCOPE));

        ConsolePrincipal stringAudPrincipal = authorizer.authorize(
                "Bearer " + token(KEY_A, "key-a", withClaim("aud", AUDIENCE)), NOW);
        check("F2 single-string aud token authorized through the composition",
                stringAudPrincipal.subject().equals(SUBJECT));

        RecordingService service = new RecordingService();
        ConsoleAuthorizationRoute route = route(authorizer, service);
        ConsoleHttpResponse ok = route.handle(request("Bearer " + token(KEY_A, "key-a", defaultClaims())));
        check("F3 composed route accepts the valid token and calls the service exactly once",
                ok.status() == 200 && service.calls == 1
                        && !new String(ok.body(), StandardCharsets.UTF_8)
                                .contains(token(KEY_A, "key-a", defaultClaims())));

        ConsoleHttpResponse unknownKid = route.handle(request("Bearer " + token(KEY_C, "attacker", trustedClaims())));
        check("F4 attacker-signed token: 401 UNAUTHENTICATED, service never called",
                unknownKid.status() == 401
                        && new String(unknownKid.body(), StandardCharsets.UTF_8).contains("UNAUTHENTICATED")
                        && service.calls == 1);

        CountingSource outage = new CountingSource(keySet("key-a", KEY_A));
        outage.failCurrent = true;
        ConsoleAuthorizer outageAuthorizer = composition(configuration(), outage);
        ConsoleHttpResponse outageResponse = route(outageAuthorizer, service)
                .handle(request("Bearer " + token(KEY_A, "key-a", defaultClaims())));
        check("F5 key-source outage at the route: authentication refusal (401), service never called",
                outageResponse.status() == 401
                        && new String(outageResponse.body(), StandardCharsets.UTF_8)
                                .contains("UNAUTHENTICATED")
                        && service.calls == 1);

        ConsoleHttpResponse wsAud = route(authorizer, service).handle(request("Bearer "
                + token(KEY_A, "key-a", withClaim("aud", List.of(WS_AUDIENCE)))));
        check("F6 WS-audience token: 401, service never called",
                wsAud.status() == 401 && service.calls == 1);

        ConsoleHttpResponse wsScope = route(authorizer, service).handle(request("Bearer "
                + token(KEY_A, "key-a", withClaim("scope", SCOPE + " " + WS_SCOPE))));
        check("F7 WS-scope token: 403 CONTEXT_DENIED, service never called",
                wsScope.status() == 403
                        && new String(wsScope.body(), StandardCharsets.UTF_8).contains("CONTEXT_DENIED")
                        && service.calls == 1);

        ConsoleHttpResponse wrongSubject = route(authorizer, service).handle(request("Bearer "
                + token(KEY_A, "key-a", withClaim("sub", "someone-else"))));
        check("F8 subject other than the configured demo subject: 401, service never called",
                wrongSubject.status() == 401 && service.calls == 1);

        ConsoleHttpResponse expired = route(authorizer, service).handle(request("Bearer "
                + token(KEY_A, "key-a", withClaim("exp", NOW.minusSeconds(30).getEpochSecond()))));
        check("F9 expired token at the route: 401, service never called",
                expired.status() == 401 && service.calls == 1);

        ConsoleHttpResponse dualHeader = route(authorizer, service).handle(request(
                "Bearer " + token(KEY_A, "key-a", defaultClaims()),
                "Bearer " + token(KEY_A, "key-a", defaultClaims())));
        check("F10 duplicated Authorization header refused, service never called",
                dualHeader.status() == 401 && service.calls == 1);

        Map<String, Object> noSub = defaultClaims();
        noSub.remove("sub");
        ConsoleHttpResponse missingSubject = route(authorizer, service)
                .handle(request("Bearer " + token(KEY_A, "key-a", noSub)));
        check("F11 verified token without the configured demo subject: refused by the policy core, "
                        + "service never called",
                missingSubject.status() == 401 && service.calls == 1);
    }

    // ---------------------------------------------------------------- dev stub isolation

    static void devStubIsolationChecks() {
        DevStubTokenIssuer stub = new DevStubTokenIssuer(ISSUER, AUDIENCE, SUBJECT, CLIENT,
                SCOPE, FIXED);
        DevStubTokenIssuer.IssuedToken handle = stub.issue();
        ConsoleAuthorizer realAuthorizer = composition(configuration(),
                new CountingSource(keySet("key-a", KEY_A)));

        check("S1 the stub handle is refused by the real verifier",
                reasonOfAuthorizer(realAuthorizer, handle.accessToken()) == Reason.MALFORMED_TOKEN);
        check("S2 a real verified JWT is refused by the stub decoder seam",
                reasonOfStub(stub.decoder(), token(KEY_A, "key-a", defaultClaims()))
                        == Reason.MALFORMED_TOKEN);
        check("S3 the stub decoder resolves its own handle (seam works locally only)",
                stub.decoder().decode(handle.accessToken(), NOW) != null);
        check("S4 the stub handle is opaque, not a JWT",
                !handle.accessToken().contains("."));
    }

    // ---------------------------------------------------------------- production composition

    @SuppressWarnings("unchecked")
    static void productionCompositionChecks() throws Exception {
        Class<?> runtime = Class.forName("com.acquiring.simulator.adapter.aws.ConsoleRuntime");
        Method deployed = runtime.getDeclaredMethod("deployedApplication");
        deployed.setAccessible(true);
        Function<ConsoleHttpRequest, ConsoleHttpResponse> application =
                (Function<ConsoleHttpRequest, ConsoleHttpResponse>) deployed.invoke(null);

        ConsoleHttpResponse submission = application.apply(
                new ConsoleHttpRequest("POST", "/console/v1/authorizations",
                        Map.of("Authorization", List.of("Bearer devstub_whatever")), new byte[0]));
        ConsoleHttpResponse history = application.apply(
                new ConsoleHttpRequest("GET", "/console/v1/transactions/TX-1/events?snapshot=new",
                        Map.of(), new byte[0]));
        ConsoleHttpResponse other = application.apply(
                new ConsoleHttpRequest("POST", "/console/v1/anything-else", Map.of(), new byte[0]));
        check("G1 deployed composition answers 503 for the business routes regardless of credential",
                submission.status() == 503 && history.status() == 503
                        && submission.body().length == 0 && history.body().length == 0);
        check("G2 deployed composition answers 404 for unknown paths", other.status() == 404);

        Method entry = runtime.getDeclaredMethod("consoleEntryPoint", Function.class);
        entry.setAccessible(true);
        boolean initFailsClosed = false;
        try {
            entry.invoke(null, (Function<String, String>) name -> null);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            initFailsClosed = failure.getCause() instanceof IllegalStateException;
        }
        check("G3 production entry-point composition fails closed without its required environment",
                initFailsClosed);

        Method composed = runtime.getDeclaredMethod("compose", Function.class, Function.class,
                com.acquiring.simulator.console.ConsoleOriginDenialSink.class, Clock.class);
        check("G4 production compose() signature takes no authorizer/decoder/registry/service argument",
                composed.getParameterCount() == 4
                        && !composed.toString().contains("ConsoleAuthorizer"));
    }

    // ---------------------------------------------------------------- helpers

    static Map<String, Object> defaultClaims() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", ISSUER);
        claims.put("aud", List.of(AUDIENCE));
        claims.put("sub", SUBJECT);
        claims.put("azp", CLIENT);
        claims.put("scope", SCOPE);
        claims.put("iat", NOW.minusSeconds(60).getEpochSecond());
        claims.put("nbf", NOW.minusSeconds(60).getEpochSecond());
        claims.put("exp", NOW.plusSeconds(600).getEpochSecond());
        return claims;
    }

    static Map<String, Object> trustedClaims() {
        return defaultClaims();
    }

    static Map<String, Object> withClaim(String name, Object value) {
        Map<String, Object> claims = defaultClaims();
        claims.put(name, value);
        return claims;
    }

    static KeyPair rsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    static TrustedIssuerConfiguration configuration() {
        return new TrustedIssuerConfiguration(ISSUER, AUDIENCE, URI.create(ISSUER + "/certs"),
                SUBJECT, CLIENT, SCOPE, WS_SCOPE, WS_AUDIENCE, Set.of("RS256"),
                Duration.ofSeconds(900));
    }

    static ConsoleAuthorizer composition(TrustedIssuerConfiguration config, JwksSource source) {
        return ConsoleTokenComposition.authorizer(config, source, FIXED);
    }

    static JwtConsoleTokenDecoder decoder(JsonWebKeySet set) {
        return new JwtConsoleTokenDecoder(configuration(), new FixedSource(set), FIXED);
    }

    static JwtConsoleTokenDecoder decoder(JwksSource source) {
        return new JwtConsoleTokenDecoder(configuration(), source, FIXED);
    }

    static JwtConsoleTokenDecoder refreshable(HttpJwksSource source) {
        return new JwtConsoleTokenDecoder(configuration(), source, FIXED);
    }

    static ObjectNode algHeader(String alg, String kid) {
        ObjectNode header = JSON.createObjectNode();
        if (alg != null) {
            header.put("alg", alg);
        }
        header.put("typ", "JWT");
        if (kid != null) {
            header.put("kid", kid);
        }
        return header;
    }

    static String token(KeyPair key, String kid, Map<String, Object> claims) {
        return token(key, kid, claims, "RS256");
    }

    static String token(KeyPair key, String kid, Map<String, Object> claims, String alg) {
        ObjectNode header = algHeader(alg, kid);
        byte[] signature;
        try {
            signature = switch (alg) {
                case "RS256", "RS384", "RS512" -> sign(key, header, claims);
                // The decoder must refuse these before any verification, so the signature
                // bytes only need to exist (or be absent for a missing alg).
                case null, default -> (alg == null ? "missing-alg" : alg)
                        .getBytes(StandardCharsets.UTF_8);
            };
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
        return tokenWithHeader(key, header, claims, signature);
    }

    static String tokenWithHeader(KeyPair key, ObjectNode header, Map<String, Object> claims,
            byte[] signature) {
        return encode(header) + "." + encode(JSON.valueToTree(claims)) + "." + encode(signature);
    }

    static String tokenWithHeader(ObjectNode header, Map<String, Object> claims, byte[] signature) {
        return encode(header) + "." + encode(JSON.valueToTree(claims)) + "." + encode(signature);
    }

    static byte[] sign(KeyPair key, ObjectNode header, Map<String, Object> claims) {
        try {
            String algorithm = header.path("alg").asText("RS256");
            String jdk = switch (algorithm) {
                case "RS256" -> "SHA256withRSA";
                case "RS384" -> "SHA384withRSA";
                case "RS512" -> "SHA512withRSA";
                default -> throw new IllegalStateException("not an RSA alg: " + algorithm);
            };
            Signature signer = Signature.getInstance(jdk);
            signer.initSign(key.getPrivate());
            signer.update((encode(header) + "." + encode(JSON.valueToTree(claims)))
                    .getBytes(StandardCharsets.US_ASCII));
            return signer.sign();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    static String hmacToken(String alg, Map<String, Object> claims) {
        try {
            ObjectNode header = JSON.createObjectNode();
            header.put("alg", alg);
            header.put("kid", "key-a");
            header.put("typ", "JWT");
            String signingInput = encode(header) + "." + encode(JSON.valueToTree(claims));
            String macName = "HmacSHA" + alg.substring(2);
            Mac mac = Mac.getInstance(macName);
            mac.init(new SecretKeySpec("independent-qa-secret".getBytes(StandardCharsets.UTF_8), macName));
            return signingInput + "." + encode(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    static String swapPayload(String token, Map<String, Object> claims) {
        String[] parts = token.split("\\.", -1);
        return parts[0] + "." + encode(JSON.valueToTree(claims)) + "." + parts[2];
    }

    static String headerSwapped(String token, ObjectNode header) {
        String[] parts = token.split("\\.", -1);
        return encode(header) + "." + parts[1] + "." + parts[2];
    }

    static JsonWebKeySet keySet(String kid, KeyPair pair) {
        return new JsonWebKeySet(Map.of(kid, pair.getPublic()));
    }

    static JsonWebKeySet keySet(String kid, java.security.PublicKey key) {
        return new JsonWebKeySet(Map.of(kid, key));
    }

    static String jwksDocument(String kid, java.security.PublicKey key) {
        ObjectNode root = JSON.createObjectNode();
        root.putArray("keys").add(jwkOf(key, kid));
        return root.toString();
    }

    static ObjectNode jwkOf(java.security.PublicKey key, String kid) {
        RSAPublicKey rsa = (RSAPublicKey) key;
        ObjectNode jwk = JSON.createObjectNode();
        jwk.put("kty", "RSA");
        jwk.put("kid", kid);
        jwk.put("use", "sig");
        jwk.put("n", b64(rsa.getModulus()));
        jwk.put("e", b64(rsa.getPublicExponent()));
        return jwk;
    }

    static String duplicateKidDocument() {
        ObjectNode root = JSON.createObjectNode();
        var keys = root.putArray("keys");
        RSAPublicKey rsa = (RSAPublicKey) KEY_A.getPublic();
        for (int i = 0; i < 2; i++) {
            ObjectNode jwk = keys.addObject();
            jwk.put("kty", "RSA").put("kid", "dup").put("use", "sig")
                    .put("n", b64(rsa.getModulus())).put("e", b64(rsa.getPublicExponent()));
        }
        return root.toString();
    }

    static String shortKeyDocument() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(1024);
            RSAPublicKey small = (RSAPublicKey) generator.generateKeyPair().getPublic();
            ObjectNode root = JSON.createObjectNode();
            ObjectNode jwk = root.putArray("keys").addObject();
            jwk.put("kty", "RSA").put("kid", "small").put("use", "sig")
                    .put("n", b64(small.getModulus())).put("e", b64(small.getPublicExponent()));
            return root.toString();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    static String b64(java.math.BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return encode(trimmed);
        }
        return encode(bytes);
    }

    static String encode(JsonNode node) {
        try {
            return encode(JSON.writeValueAsBytes(node));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    static ConsoleAuthorizationRoute route(ConsoleAuthorizer authorizer, RecordingService service) {
        AuthorizedContext context = new AuthorizedContext("ACQ-SIM-001", "MERCH-SIM-001",
                "SUB-DEMO", Set.of(Route.DOMESTIC));
        return new ConsoleAuthorizationRoute(authorizer, principal -> context, service,
                ConsoleRateLimitHook.PERMISSIVE, FIXED);
    }

    static ConsoleHttpRequest request(String... authorization) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", List.of("application/json"));
        headers.put("Authorization", List.of(authorization));
        String json = "{\"contractVersion\":\"1\",\"operation\":\"A\","
                + "\"correlationId\":\"CORR-QA-K1\",\"acquirerRef\":\"ACQ-SIM-001\","
                + "\"merchantRef\":\"MERCH-SIM-001\",\"terminalRef\":\"TERM-SIM-001\","
                + "\"paymentRef\":\"PURCHASE-QA-K1\",\"credentialFixtureRef\":\"SIM-APP-001\","
                + "\"amount\":\"10.00\",\"currency\":\"USD\",\"entryMode\":\"simulated-contact\","
                + "\"cvm\":\"simulated-verified\",\"cryptogramPresent\":true}";
        return new ConsoleHttpRequest("POST", "/console/v1/authorizations", headers,
                json.getBytes(StandardCharsets.US_ASCII));
    }

    static final class RecordingService extends AuthorizationService {
        int calls;
        RecordingService() {
            super(null, null, null, Clock.systemUTC());
        }
        @Override
        public AuthorizationResult authorize(AuthorizedContext context, PurchaseRequest request) {
            calls++;
            return AuthorizationResult.of(Outcome.APPROVED, "AUTH-QA-K1");
        }
    }

    static final class FixedSource implements JwksSource {
        private final JsonWebKeySet set;
        FixedSource(JsonWebKeySet set) { this.set = set; }
        @Override public JsonWebKeySet current() { return set; }
        @Override public JsonWebKeySet refresh() { return set; }
    }

    static class CountingSource implements JwksSource {
        JsonWebKeySet currentSet;
        final JsonWebKeySet refreshSet;
        boolean failCurrent;
        boolean failRefresh;
        int currentCalls;
        int refreshCalls;
        CountingSource(JsonWebKeySet set) { this(set, null); }
        CountingSource(JsonWebKeySet set, JsonWebKeySet refreshSet) {
            this.currentSet = set;
            this.refreshSet = refreshSet;
        }
        boolean replaceSet(JsonWebKeySet replacement) {
            this.currentSet = replacement;
            return true;
        }
        @Override public JsonWebKeySet current() {
            currentCalls++;
            if (failCurrent) {
                throw new JwksSourceException("qa fixture outage");
            }
            return currentSet;
        }
        @Override public JsonWebKeySet refresh() {
            refreshCalls++;
            if (failRefresh) {
                throw new JwksSourceException("qa fixture outage");
            }
            if (refreshSet != null) {
                currentSet = refreshSet;
            }
            return currentSet;
        }
    }

    static ConsoleTokenClaims decodeOf(JwtConsoleTokenDecoder decoder, String token) {
        try {
            return decoder.decode(token, NOW);
        } catch (ConsoleAuthenticationException refused) {
            throw new AssertionError("expected acceptance, got " + refused.reason());
        }
    }

    static Reason reasonOf(JwtConsoleTokenDecoder decoder, String token) {
        try {
            decoder.decode(token, NOW);
            throw new AssertionError("expected refusal for "
                    + (token == null ? "null" : token.substring(0, Math.min(30, token.length()))));
        } catch (ConsoleAuthenticationException refused) {
            return refused.reason();
        }
    }

    static Reason reasonOfAuthorizer(ConsoleAuthorizer authorizer, String token) {
        try {
            authorizer.authorize("Bearer " + token, NOW);
            throw new AssertionError("expected refusal");
        } catch (ConsoleAuthenticationException refused) {
            return refused.reason();
        }
    }

    static Reason reasonOfStub(ConsoleTokenDecoder stubDecoder, String token) {
        try {
            stubDecoder.decode(token, NOW);
            throw new AssertionError("expected refusal");
        } catch (ConsoleAuthenticationException refused) {
            return refused.reason();
        }
    }

    static void refuse(String name, JwtConsoleTokenDecoder decoder, CountingSource source,
            String token, Reason expected, int expectedCurrentDelta, int expectedRefreshDelta) {
        int currentBefore = source.currentCalls;
        int refreshBefore = source.refreshCalls;
        Reason actual = reasonOf(decoder, token);
        check(name + " [reason=" + actual + ", currentDelta="
                        + (source.currentCalls - currentBefore) + ", refreshDelta="
                        + (source.refreshCalls - refreshBefore) + "]",
                actual == expected
                        && source.currentCalls - currentBefore == expectedCurrentDelta
                        && source.refreshCalls - refreshBefore == expectedRefreshDelta);
    }

    static void expectIllegal(String name, Runnable attempt) {
        try {
            attempt.run();
            check(name, false);
        } catch (IllegalArgumentException expected) {
            check(name, true);
        }
    }

    static void expectJwksFailure(String name, String document) {
        try {
            JwksParser.parse(document);
            check(name, false);
        } catch (JwksSourceException expected) {
            check(name, true);
        }
    }

    static void expectJwksFailure(String name, Runnable attempt) {
        try {
            attempt.run();
            check(name, false);
        } catch (JwksSourceException expected) {
            check(name, true);
        }
    }

    static boolean canConstruct(URI jwksUri) {
        try {
            new TrustedIssuerConfiguration(ISSUER, AUDIENCE, jwksUri, SUBJECT, CLIENT, SCOPE,
                    WS_SCOPE, WS_AUDIENCE, Set.of("RS256"), Duration.ofSeconds(900));
            return true;
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }

    static void respond(HttpExchange exchange, int status, String body) throws IOException {
        respond(exchange, status, body, null);
    }

    static void respond(HttpExchange exchange, int status, String body, String location)
            throws IOException {
        if (location != null) {
            exchange.getResponseHeaders().add("Location", location);
        }
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } else {
            exchange.close();
        }
    }

    static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("PASS " + name);
        } else {
            failed++;
            failures.add(name);
            System.out.println("FAIL " + name);
        }
    }
}
