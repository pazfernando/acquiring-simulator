package com.acquiring.simulator.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EventSanitizerTest {

    @Test void ordinaryReferencesAreAdmitted() {
        assertThatCode(() -> EventSanitizer.requireSafe("transactionId", "AUTH-001")).doesNotThrowAnyException();
        assertThatCode(() -> EventSanitizer.requireSafe("evidenceRef", "journal/AUTH-001/v3"))
                .doesNotThrowAnyException();
    }

    @Test void compactJwtIsRejectedWithoutEchoingTheToken() {
        String token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJERU1PLVVTRVIifQ.c2lnbmF0dXJl";

        assertThatThrownBy(() -> EventSanitizer.requireSafe("reason", token))
                .isInstanceOfSatisfying(EventValidationException.class, e -> {
                    assertThat(e.violation()).isEqualTo(EventViolation.PROHIBITED_CONTENT);
                    assertThat(e.getMessage()).doesNotContain(token);
                });
    }

    @Test void bearerCredentialAndPemMaterialAreRejected() {
        assertThatThrownBy(() -> EventSanitizer.requireSafe("reason", "Bearer abcdef123456"))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.PROHIBITED_CONTENT));
        assertThatThrownBy(() -> EventSanitizer.requireSafe("reason", "-----BEGIN PRIVATE KEY-----"))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.PROHIBITED_CONTENT));
    }

    @Test void panShapedDigitRunIsRejected() {
        assertThatThrownBy(() -> EventSanitizer.requireSafe("evidenceRef", "4111111111111111"))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.PROHIBITED_CONTENT));
    }

    @Test void pinBlockWordingIsRejected() {
        assertThatThrownBy(() -> EventSanitizer.requireSafe("reason", "pin block captured"))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.PROHIBITED_CONTENT));
    }

    @Test void controlCharactersAndNonAsciiTextAreRejected() {
        assertThatThrownBy(() -> EventSanitizer.requireSafe("reason", "line\nbreak"))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.PROHIBITED_CONTENT));
        assertThatThrownBy(() -> EventSanitizer.requireSafe("reason", "aprobadó"))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.PROHIBITED_CONTENT));
    }
}
