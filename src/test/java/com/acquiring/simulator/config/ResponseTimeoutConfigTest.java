package com.acquiring.simulator.config;

import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused guard for the T3 prerequisite defect: the AWS cold-start path resolved its response
 * timeout to 2000 ms when {@code RESPONSE_TIMEOUT_MS} was absent, while AUTH-001 R20 and
 * {@code application.properties} approve 30 seconds from dispatch.
 *
 * <p>These are configuration tests only. They do not evaluate an authorization, do not activate
 * {@code AwsRuntime} and do not prove the deadline or evidence-ordering rules.</p>
 */
class ResponseTimeoutConfigTest {

    @Test
    void absentSettingResolvesToTheApprovedThirtySecondDefault() {
        assertThat(ResponseTimeoutConfig.resolve(null)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void approvedDefaultMatchesTheDeclaredSpringProperty() throws Exception {
        var properties = new Properties();
        try (var in = getClass().getResourceAsStream("/application.properties")) {
            assertThat(in).as("application.properties on the classpath").isNotNull();
            properties.load(in);
        }

        assertThat(properties.getProperty("simulator.authorization.response-timeout"))
                .isEqualTo("PT30S");
        assertThat(Duration.parse(properties.getProperty("simulator.authorization.response-timeout")))
                .isEqualTo(ResponseTimeoutConfig.APPROVED_DEFAULT_RESPONSE_TIMEOUT);
    }

    @Test
    void validExplicitSettingIsPreserved() {
        assertThat(ResponseTimeoutConfig.resolve("45000")).isEqualTo(Duration.ofMillis(45_000));
        assertThat(ResponseTimeoutConfig.resolve("30000")).isEqualTo(Duration.ofSeconds(30));
        assertThat(ResponseTimeoutConfig.resolve("1")).isEqualTo(Duration.ofMillis(1));
        assertThat(ResponseTimeoutConfig.resolve(" 45000 ")).isEqualTo(Duration.ofMillis(45_000));
    }

    @Test
    void zeroOrNegativeSettingIsRejectedInsteadOfSilentlyAccepted() {
        for (String raw : new String[] {"0", "-1", "-30000"}) {
            assertThatThrownBy(() -> ResponseTimeoutConfig.resolve(raw))
                    .as("raw value '%s'", raw)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RESPONSE_TIMEOUT_MS")
                    .hasMessageContaining("positive");
        }
    }

    @Test
    void malformedSettingIsRejectedInsteadOfSilentlyAccepted() {
        for (String raw : new String[] {"abc", "30s", "30.5", "1e4", "99999999999999999999"}) {
            assertThatThrownBy(() -> ResponseTimeoutConfig.resolve(raw))
                    .as("raw value '%s'", raw)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RESPONSE_TIMEOUT_MS");
        }
    }

    @Test
    void blankSettingIsRejectedInsteadOfFallingBackSilently() {
        for (String raw : new String[] {"", "   ", "\t"}) {
            assertThatThrownBy(() -> ResponseTimeoutConfig.resolve(raw))
                    .as("raw value '%s'", raw)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RESPONSE_TIMEOUT_MS");
        }
    }
}
