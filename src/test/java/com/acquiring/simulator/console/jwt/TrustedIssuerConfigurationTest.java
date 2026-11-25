package com.acquiring.simulator.console.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The configuration surface rejects every trusting default at construction time.
 *
 * <p>No value is read from the environment here and no issuer is contacted; these are pure
 * validation tests over explicit inputs.</p>
 */
class TrustedIssuerConfigurationTest {

    @Test
    void acceptsTheFullyExplicitConfiguration() {
        TrustedIssuerConfiguration configuration = JwtTestSupport.configuration();

        assertThat(configuration.issuer()).isEqualTo(JwtTestSupport.ISSUER);
        assertThat(configuration.audience()).isEqualTo(JwtTestSupport.AUDIENCE);
        assertThat(configuration.allowedAlgorithms()).containsExactly("RS256");
        assertThat(configuration.maxTokenLifetime()).isEqualTo(Duration.ofSeconds(900));
    }

    @Test
    void refusesABlankRequiredValue() {
        assertThatThrownBy(() -> configuration("", JwtTestSupport.AUDIENCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("issuer");
        assertThatThrownBy(() -> configuration(JwtTestSupport.ISSUER, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audience");
    }

    @Test
    void refusesAnAlgorithmItCannotVerify() {
        assertThatThrownBy(() -> new TrustedIssuerConfiguration(
                JwtTestSupport.ISSUER, JwtTestSupport.AUDIENCE,
                URI.create("https://auth.sim.test/certs"),
                JwtTestSupport.SUBJECT, JwtTestSupport.CLIENT, JwtTestSupport.SCOPE,
                JwtTestSupport.WS_SCOPE, JwtTestSupport.WS_AUDIENCE,
                Set.of("HS256"), Duration.ofSeconds(900)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported verification algorithm");
    }

    @Test
    void refusesALifetimeAboveTheNineHundredSecondCeiling() {
        assertThatThrownBy(() -> new TrustedIssuerConfiguration(
                JwtTestSupport.ISSUER, JwtTestSupport.AUDIENCE,
                URI.create("https://auth.sim.test/certs"),
                JwtTestSupport.SUBJECT, JwtTestSupport.CLIENT, JwtTestSupport.SCOPE,
                JwtTestSupport.WS_SCOPE, JwtTestSupport.WS_AUDIENCE,
                Set.of("RS256"), Duration.ofSeconds(901)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("900 second");
    }

    @Test
    void refusesOverlappingResourceClasses() {
        assertThatThrownBy(() -> new TrustedIssuerConfiguration(
                JwtTestSupport.ISSUER, JwtTestSupport.AUDIENCE,
                URI.create("https://auth.sim.test/certs"),
                JwtTestSupport.SUBJECT, JwtTestSupport.CLIENT, JwtTestSupport.SCOPE,
                JwtTestSupport.SCOPE, JwtTestSupport.WS_AUDIENCE,
                Set.of("RS256"), Duration.ofSeconds(900)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scopes must be disjoint");
        assertThatThrownBy(() -> new TrustedIssuerConfiguration(
                JwtTestSupport.ISSUER, JwtTestSupport.AUDIENCE,
                URI.create("https://auth.sim.test/certs"),
                JwtTestSupport.SUBJECT, JwtTestSupport.CLIENT, JwtTestSupport.SCOPE,
                JwtTestSupport.WS_SCOPE, JwtTestSupport.AUDIENCE,
                Set.of("RS256"), Duration.ofSeconds(900)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audiences must be disjoint");
    }

    @Test
    void refusesAPlainHttpKeySourceOutsideLoopback() {
        assertThatThrownBy(() -> new TrustedIssuerConfiguration(
                JwtTestSupport.ISSUER, JwtTestSupport.AUDIENCE,
                URI.create("http://auth.sim.test/certs"),
                JwtTestSupport.SUBJECT, JwtTestSupport.CLIENT, JwtTestSupport.SCOPE,
                JwtTestSupport.WS_SCOPE, JwtTestSupport.WS_AUDIENCE,
                Set.of("RS256"), Duration.ofSeconds(900)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jwksUri must be https");
    }

    @Test
    void requiresANonEmptyAlgorithmSet() {
        assertThatThrownBy(() -> new TrustedIssuerConfiguration(
                JwtTestSupport.ISSUER, JwtTestSupport.AUDIENCE,
                URI.create("https://auth.sim.test/certs"),
                JwtTestSupport.SUBJECT, JwtTestSupport.CLIENT, JwtTestSupport.SCOPE,
                JwtTestSupport.WS_SCOPE, JwtTestSupport.WS_AUDIENCE,
                Set.of(), Duration.ofSeconds(900)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one verification algorithm");
    }

    private static TrustedIssuerConfiguration configuration(String issuer, String audience) {
        return new TrustedIssuerConfiguration(
                issuer, audience, URI.create("https://auth.sim.test/certs"),
                JwtTestSupport.SUBJECT, JwtTestSupport.CLIENT, JwtTestSupport.SCOPE,
                JwtTestSupport.WS_SCOPE, JwtTestSupport.WS_AUDIENCE,
                Set.of("RS256"), Duration.ofSeconds(900));
    }
}
