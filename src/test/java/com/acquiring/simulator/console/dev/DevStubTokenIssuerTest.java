package com.acquiring.simulator.console.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.console.ConsoleAuthenticationException;
import com.acquiring.simulator.console.ConsolePrincipal;
import com.acquiring.simulator.console.ConsoleTokenClaims;
import com.acquiring.simulator.console.DemoConsoleAuthorizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The development token seam, with no Docker, no network and no AWS.
 *
 * <p>These tests prove that the handle is opaque, that the same claim policy the route uses accepts
 * it, and that an expired or unknown handle is refused. They do <strong>not</strong> prove any
 * signature, issuer or key: there is none, and the class under test says so.</p>
 */
class DevStubTokenIssuerTest {

    private static final String ISSUER = "https://auth.dev.invalid/realms/console-dev-stub";
    private static final String AUDIENCE = "console-api";
    private static final String SUBJECT = "demo-user";
    private static final String CLIENT = "console-demo";
    private static final String SCOPE = "console.demo";
    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");

    private final MutableClock clock = new MutableClock(NOW);
    private final DevStubTokenIssuer issuer =
            new DevStubTokenIssuer(ISSUER, AUDIENCE, SUBJECT, CLIENT, SCOPE, clock);

    @Test
    void issuedHandleIsOpaqueAndNotAJwt() {
        DevStubTokenIssuer.IssuedToken issued = issuer.issue();

        assertThat(issued.accessToken()).startsWith("devstub_");
        assertThat(issued.accessToken()).doesNotContain(".");
        assertThat(issued.expiresInSeconds()).isEqualTo(issuer.lifetimeSeconds());

        ConsoleTokenClaims claims = issuer.decoder().decode(issued.accessToken());
        assertThat(claims.issuer()).isEqualTo(ISSUER);
        assertThat(claims.audience()).containsExactly(AUDIENCE);
        assertThat(claims.subject()).isEqualTo(SUBJECT);
        assertThat(claims.authorizedParty()).isEqualTo(CLIENT);
        assertThat(claims.scopes()).containsExactly(SCOPE);
        assertThat(claims.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void eachIssueReturnsADistinctHandle() {
        assertThat(issuer.issue().accessToken())
                .isNotEqualTo(issuer.issue().accessToken());
        assertThat(issuer.outstandingTokens()).isEqualTo(2);
    }

    @Test
    void unknownHandleIsRefusedRatherThanGuessed() {
        assertThatThrownBy(() -> issuer.decoder().decode("devstub_not-issued-here"))
                .isInstanceOf(ConsoleAuthenticationException.class)
                .extracting(error -> ((ConsoleAuthenticationException) error).reason())
                .isEqualTo(ConsoleAuthenticationException.Reason.MALFORMED_TOKEN);
    }

    @Test
    void expiredHandleIsRefusedAndPurged() {
        DevStubTokenIssuer.IssuedToken issued =
                issuer.issue(NOW.minus(Duration.ofMinutes(16)));

        assertThatThrownBy(() -> issuer.decoder().decode(issued.accessToken()))
                .isInstanceOf(ConsoleAuthenticationException.class)
                .extracting(error -> ((ConsoleAuthenticationException) error).reason())
                .isEqualTo(ConsoleAuthenticationException.Reason.EXPIRED);
        assertThat(issuer.outstandingTokens()).isZero();
    }

    @Test
    void theRealClaimPolicyAcceptsTheHandle() throws Exception {
        DevStubTokenIssuer.IssuedToken issued = issuer.issue();
        DemoConsoleAuthorizer authorizer = new DemoConsoleAuthorizer(
                ISSUER, AUDIENCE, SUBJECT, CLIENT, SCOPE, issuer.decoder());

        ConsolePrincipal principal = authorizer.authorize(
                "Bearer " + issued.accessToken(), NOW);

        assertThat(principal.subject()).isEqualTo(SUBJECT);
        assertThat(principal.authorizedParty()).isEqualTo(CLIENT);
        assertThat(principal.scopes()).containsExactly(SCOPE);
    }

    @Test
    void theRealClaimPolicyRefusesAnExpiredHandle() {
        DevStubTokenIssuer.IssuedToken issued =
                issuer.issue(NOW.minus(Duration.ofMinutes(16)));
        DemoConsoleAuthorizer authorizer = new DemoConsoleAuthorizer(
                ISSUER, AUDIENCE, SUBJECT, CLIENT, SCOPE, issuer.decoder());

        assertThatThrownBy(() -> authorizer.authorize("Bearer " + issued.accessToken(), NOW))
                .isInstanceOf(ConsoleAuthenticationException.class)
                .extracting(error -> ((ConsoleAuthenticationException) error).reason())
                .isEqualTo(ConsoleAuthenticationException.Reason.EXPIRED);
    }

    @Test
    void toStringNeverRendersTheHandle() {
        DevStubTokenIssuer.IssuedToken issued = issuer.issue();

        assertThat(issued.toString()).doesNotContain(issued.accessToken());
        assertThat(issued.toString()).contains("<redacted>");
    }

    /** Clock whose instant a test can move without sleeping. */
    private static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
