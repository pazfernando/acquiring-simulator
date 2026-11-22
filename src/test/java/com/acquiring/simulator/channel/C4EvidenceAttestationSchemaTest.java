package com.acquiring.simulator.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.junit.jupiter.api.Test;

/**
 * Schema bounds that must agree with the approved P1/P2 profile before any signature is
 * verified: DE37 is 12 uppercase letters/digits, aid/tid/att are 1–64, amountMinor is
 * 100–999999 and optional DE38 is exactly six uppercase letters/digits.
 */
class C4EvidenceAttestationSchemaTest {

    private static final String HEX = C4EvidenceFixtures.bodySha256();

    @Test
    void acceptsUppercaseAlphanumericRrn() {
        C4EvidenceAttestation attestation = C4EvidenceFixtures.attestationWithBounds(
                "RRN123456789", "AUTH-001", "TX-001", "ATT-001", null, 1000, HEX);

        assertThat(attestation.RRN()).isEqualTo("RRN123456789");
        assertThat(attestation.RRN()).hasSize(12);
    }

    @Test
    void acceptsDigitOnlyRrnForBackwardCompatibility() {
        C4EvidenceAttestation attestation = C4EvidenceFixtures.attestationWithBounds(
                "000000000001", "AUTH-001", "TX-001", "ATT-001", null, 1000, HEX);

        assertThat(attestation.RRN()).isEqualTo("000000000001");
    }

    @Test
    void rejectsLowercasePunctuatedOrWrongWidthRrn() {
        assertRrnRejected("rrn123456789");
        assertRrnRejected("RRN-12345678");
        assertRrnRejected("RRN12345678");
        assertRrnRejected("RRN1234567890");
    }

    @Test
    void authorizationTransactionAndAttemptAreBoundedTo64() {
        String exactly64 = "A".repeat(64);

        C4EvidenceAttestation accepted = C4EvidenceFixtures.attestationWithBounds(
                "RRN123456789", exactly64, exactly64, exactly64, null, 1000, HEX);
        assertThat(accepted.authorizationId()).hasSize(64);
        assertThat(accepted.transactionId()).hasSize(64);
        assertThat(accepted.instructionAttemptId()).hasSize(64);

        assertIdRejected("authorizationId", "A".repeat(65), "TX-001", "ATT-001");
        assertIdRejected("transactionId", "AUTH-001", "A".repeat(65), "ATT-001");
        assertIdRejected("instructionAttemptId", "AUTH-001", "TX-001", "A".repeat(65));
    }

    @Test
    void amountMustStayInsideProfileMinorUnits() {
        C4EvidenceAttestation minimum = C4EvidenceFixtures.attestationWithBounds(
                "RRN123456789", "AUTH-001", "TX-001", "ATT-001", null, 100, HEX);
        C4EvidenceAttestation maximum = C4EvidenceFixtures.attestationWithBounds(
                "RRN123456789", "AUTH-001", "TX-001", "ATT-001", null, 999_999, HEX);

        assertThat(minimum.amountMinor()).isEqualTo(100);
        assertThat(maximum.amountMinor()).isEqualTo(999_999);
        assertAmountRejected(99);
        assertAmountRejected(1_000_000);
    }

    @Test
    void authorizationReferenceIsNullOrExactlySixUppercaseAlphanumeric() {
        assertThat(C4EvidenceFixtures.attestationWithBounds(
                "RRN123456789", "AUTH-001", "TX-001", "ATT-001", null, 1000, HEX)
                .authorizationReference()).isNull();

        assertThat(C4EvidenceFixtures.attestationWithBounds(
                "RRN123456789", "AUTH-001", "TX-001", "ATT-001", "ABC123", 1000, HEX)
                .authorizationReference()).isEqualTo("ABC123");

        assertReferenceRejected("abc123");
        assertReferenceRejected("ABC12");
        assertReferenceRejected("ABC1234");
        assertReferenceRejected("ABC-12");
    }

    private static void assertRrnRejected(String rrn) {
        C4CarrierException error = catchThrowableOfType(
                () -> C4EvidenceFixtures.attestationWithBounds(
                        rrn, "AUTH-001", "TX-001", "ATT-001", null, 1000, HEX),
                C4CarrierException.class);
        assertThat(error).as("RRN %s", rrn).isNotNull();
        assertThat(error.violation()).isEqualTo(C4CarrierViolation.CARRIER_SCHEMA);
        assertThat(error.field()).isEqualTo("RRN");
    }

    private static void assertIdRejected(
            String field, String authorizationId, String transactionId, String attemptId) {
        C4CarrierException error = catchThrowableOfType(
                () -> C4EvidenceFixtures.attestationWithBounds(
                        "RRN123456789", authorizationId, transactionId, attemptId,
                        null, 1000, HEX),
                C4CarrierException.class);
        assertThat(error).as(field).isNotNull();
        assertThat(error.violation()).isEqualTo(C4CarrierViolation.CARRIER_SCHEMA);
        assertThat(error.field()).isEqualTo(field);
    }

    private static void assertAmountRejected(int amountMinor) {
        C4CarrierException error = catchThrowableOfType(
                () -> C4EvidenceFixtures.attestationWithBounds(
                        "RRN123456789", "AUTH-001", "TX-001", "ATT-001", null,
                        amountMinor, HEX),
                C4CarrierException.class);
        assertThat(error).as("amountMinor %s", amountMinor).isNotNull();
        assertThat(error.violation()).isEqualTo(C4CarrierViolation.CARRIER_SCHEMA);
        assertThat(error.field()).isEqualTo("amountMinor");
    }

    private static void assertReferenceRejected(String reference) {
        C4CarrierException error = catchThrowableOfType(
                () -> C4EvidenceFixtures.attestationWithBounds(
                        "RRN123456789", "AUTH-001", "TX-001", "ATT-001", reference,
                        1000, HEX),
                C4CarrierException.class);
        assertThat(error).as("authorizationReference %s", reference).isNotNull();
        assertThat(error.violation()).isEqualTo(C4CarrierViolation.CARRIER_SCHEMA);
        assertThat(error.field()).isEqualTo("authorizationReference");
    }
}
