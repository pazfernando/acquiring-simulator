package com.acquiring.simulator.protocol.iso8583;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.protocol.iso8583.Iso8583InstructionBindingException.Rule;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pure equality oracle for the instruction-binding slice.
 *
 * <p>Covers exactly the written scope: the complete echoed
 * identity/context/metadata set, the physical elements DE3/4/7/11/22/25/32/37/
 * 41/42/49, the trusted original-wire hash check and the trusted DE38
 * presence/value check. It intentionally does not cover amount/currency/domain
 * validity, the full P5 envelope (environment/principals/evidence identity/
 * attestation signature) or admission; those remain later slices.</p>
 */
class Iso8583InstructionBindingValidatorTest {

    private static final String WIRE_HASH =
            "90e6043c6394927f5dada913ee0109ac05a1f8e96b8c4577d4157009f654e84b";

    private static final Iso8583PhysicalInstruction PHYSICAL =
            new Iso8583PhysicalInstruction(
                    "000000",
                    1000,
                    "1118120000",
                    "000001",
                    "051",
                    "00",
                    "1",
                    "000000000001",
                    "TERM0001",
                    "MERCH0000000001",
                    "840");

    private static final Iso8583OriginalInstruction ORIGINAL =
            original(null);

    @Test
    void acceptsExactEchoAndAllowsAbsenceWhenTheTrustedRecordAssertsNoReference() {
        Iso8583InstructionBindingDecision decision = Iso8583InstructionBindingValidator.validate(
                ORIGINAL,
                new Iso8583TrustedEvidenceRecord(WIRE_HASH, null),
                message(null),
                new Iso8583De48Response(echo()));

        assertThat(decision.authorizationId()).isEqualTo("AUTH-001");
        assertThat(decision.hasAuthorizationReference()).isFalse();
        assertThat(decision.authorizationReference()).isNull();

        Iso8583InstructionBindingDecision emptyDe38 =
                Iso8583InstructionBindingValidator.validate(
                        ORIGINAL,
                        new Iso8583TrustedEvidenceRecord(WIRE_HASH, null),
                        message(""),
                        new Iso8583De48Response(echo()));
        assertThat(emptyDe38.hasAuthorizationReference()).isFalse();
    }

    @Test
    void rejectsWrongCorrelationAndEachWrongContextEcho() {
        Map<String, String> wrongCorrelation = echo();
        wrongCorrelation.put("cid", "CORR-X");
        assertFails(Rule.INSTRUCTION_FIELD_MISMATCH, "cid", wrongCorrelation, message(null));

        Map<String, String> wrongAcquirer = echo();
        wrongAcquirer.put("acq", "ACQ-B");
        assertFails(Rule.INSTRUCTION_FIELD_MISMATCH, "acq", wrongAcquirer, message(null));

        Map<String, String> wrongGeneration = echo();
        wrongGeneration.put("gen", "2");
        assertFails(Rule.INSTRUCTION_FIELD_MISMATCH, "gen", wrongGeneration, message(null));
    }

    @Test
    void rejectsTrustedHashMismatchAndMalformedTrustedRecord() {
        Map<String, String> wrongCorrelation = echo();
        wrongCorrelation.put("cid", "CORR-X");
        assertThatThrownBy(() -> Iso8583InstructionBindingValidator.validate(
                ORIGINAL,
                new Iso8583TrustedEvidenceRecord("b".repeat(64), null),
                message("ZZ9999"),
                new Iso8583De48Response(wrongCorrelation)))
                .isInstanceOf(Iso8583InstructionBindingException.class)
                .satisfies(thrown -> {
                    Iso8583InstructionBindingException failure =
                            (Iso8583InstructionBindingException) thrown;
                    assertThat(failure.rule()).isEqualTo(Rule.INSTRUCTION_HASH_MISMATCH);
                    assertThat(failure.component()).isEqualTo("originalWireSha256");
                });

        assertFailsWith(
                Rule.MALFORMED_TRUSTED_RECORD,
                "trustedRecord.instructionSha256",
                new Iso8583TrustedEvidenceRecord(WIRE_HASH.toUpperCase(), null),
                echo(),
                message(null));
        assertFailsWith(
                Rule.MALFORMED_TRUSTED_RECORD,
                "trustedRecord.authorizationReference",
                new Iso8583TrustedEvidenceRecord(WIRE_HASH, "ab1234"),
                echo(),
                message("ab1234"));
    }

    @Test
    void enforcesTheTrustedDe38ReferenceWithoutMintingAValue() {
        assertFailsWith(
                Rule.AUTHORIZATION_REFERENCE_MISSING,
                "de38",
                new Iso8583TrustedEvidenceRecord(WIRE_HASH, "AB1234"),
                echo(),
                message(null));
        assertFailsWith(
                Rule.AUTHORIZATION_REFERENCE_MISMATCH,
                "de38",
                new Iso8583TrustedEvidenceRecord(WIRE_HASH, "AB1234"),
                echo(),
                message("ZZ9999"));
        assertFailsWith(
                Rule.AUTHORIZATION_REFERENCE_UNSOLICITED,
                "de38",
                new Iso8583TrustedEvidenceRecord(WIRE_HASH, null),
                echo(),
                message("ZZ9999"));

        Iso8583InstructionBindingDecision decision = Iso8583InstructionBindingValidator.validate(
                ORIGINAL,
                new Iso8583TrustedEvidenceRecord(WIRE_HASH, "AB1234"),
                message("AB1234"),
                new Iso8583De48Response(echo()));
        assertThat(decision.authorizationReference()).isEqualTo("AB1234");
    }

    @Test
    void rejectsEachRemainingMetadataAndPhysicalEcho() {
        assertMetadataFails("fixture", "SIM-DEC-001");
        assertMetadataFails("cvm", "NOT_REQUIRED");
        assertMetadataFails("crypto", "0");
        assertMetadataFails("risk", "REJECT");
        assertMetadataFails("sent", "20261118120001");

        assertPhysicalFails(3, "000001");
        assertPhysicalFails(4, "000000001001");
        assertPhysicalFails(7, "1118120001");
        assertPhysicalFails(11, "000002");
        assertPhysicalFails(22, "071");
        assertPhysicalFails(25, "01");
        assertPhysicalFails(32, "2");
        assertPhysicalFails(37, "000000000002");
        assertPhysicalFails(41, "TERM0002");
        assertPhysicalFails(42, "MERCH0000000002");
        assertPhysicalFails(49, "484");
    }

    @Test
    void requiresPriorEchoToMirrorTheOriginal() {
        Map<String, String> unsolicited = echo();
        unsolicited.put("prior", "AUTH-000");
        assertFails(Rule.INSTRUCTION_FIELD_MISMATCH, "prior", unsolicited, message(null));

        Iso8583OriginalInstruction withPrior = original("AUTH-000");
        assertFailsWith(
                Rule.REQUIRED_ECHO_MISSING,
                "prior",
                withPrior,
                new Iso8583TrustedEvidenceRecord(WIRE_HASH, null),
                echo(),
                message(null));

        Map<String, String> mismatched = echo();
        mismatched.put("prior", "AUTH-001");
        assertFailsWith(
                Rule.INSTRUCTION_FIELD_MISMATCH,
                "prior",
                withPrior,
                new Iso8583TrustedEvidenceRecord(WIRE_HASH, null),
                mismatched,
                message(null));

        Map<String, String> exact = echo();
        exact.put("prior", "AUTH-000");
        Iso8583InstructionBindingDecision decision = Iso8583InstructionBindingValidator.validate(
                withPrior,
                new Iso8583TrustedEvidenceRecord(WIRE_HASH, null),
                message(null),
                new Iso8583De48Response(exact));
        assertThat(decision.authorizationId()).isEqualTo("AUTH-001");
    }

    @Test
    void reportsAmountMismatchBeforeAnInvalidAuthorizationReference() {
        Map<Integer, String> wrongAmount = physical();
        wrongAmount.put(4, "000000001001");
        assertFailsWith(
                Rule.INSTRUCTION_FIELD_MISMATCH,
                "de4",
                new Iso8583TrustedEvidenceRecord(WIRE_HASH, "AB1234"),
                echo(),
                message(wrongAmount, "ZZ9999"));
    }

    @Test
    void reportsTheFirstFailureInInstructionThenDe38Order() {
        Map<String, String> wrongCorrelation = echo();
        wrongCorrelation.put("cid", "CORR-X");
        assertFails(
                Rule.INSTRUCTION_FIELD_MISMATCH, "cid", wrongCorrelation, message("ZZ9999"));

        Map<String, String> missingEcho = echo();
        missingEcho.remove("tid");
        assertFails(Rule.REQUIRED_ECHO_MISSING, "tid", missingEcho, message(null));

        assertThatThrownBy(() -> Iso8583InstructionBindingValidator.validate(
                ORIGINAL,
                new Iso8583TrustedEvidenceRecord(WIRE_HASH, "AB1234"),
                message(null),
                null))
                .isInstanceOf(Iso8583InstructionBindingException.class)
                .satisfies(thrown -> assertThat(
                        ((Iso8583InstructionBindingException) thrown).rule())
                        .isEqualTo(Rule.MISSING_TRUSTED_RECORD));
    }

    // --- Helpers ---------------------------------------------------------------

    private static Iso8583OriginalInstruction original(String prior) {
        return new Iso8583OriginalInstruction(
                WIRE_HASH,
                "AUTH-001",
                "TX-001",
                "CORR-001",
                "ATT-001",
                "ACQ-A",
                "MERCHANT-A",
                "TERMINAL-A",
                "PAY-001",
                1,
                "SIM-APP-001",
                "VERIFIED",
                "1",
                "PASS",
                "20261118120000",
                prior,
                PHYSICAL);
    }

    private static Map<String, String> echo() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("v", "1");
        values.put("op", "A");
        values.put("aid", "AUTH-001");
        values.put("cid", "CORR-001");
        values.put("tid", "TX-001");
        values.put("att", "ATT-001");
        values.put("acq", "ACQ-A");
        values.put("mid", "MERCHANT-A");
        values.put("term", "TERMINAL-A");
        values.put("pay", "PAY-001");
        values.put("gen", "1");
        values.put("fixture", "SIM-APP-001");
        values.put("cvm", "VERIFIED");
        values.put("crypto", "1");
        values.put("risk", "PASS");
        values.put("sent", "20261118120000");
        values.put("actor", "ISSUER");
        values.put("stage", "ISSUER_REPLIED");
        values.put("result", "APPROVED");
        values.put("reason", "APPROVED");
        values.put("evidence", "EVID-001");
        values.put("at", "20261118120001");
        return values;
    }

    private static Map<Integer, String> physical() {
        Map<Integer, String> fields = new LinkedHashMap<>();
        fields.put(3, "000000");
        fields.put(4, "000000001000");
        fields.put(7, "1118120000");
        fields.put(11, "000001");
        fields.put(22, "051");
        fields.put(25, "00");
        fields.put(32, "1");
        fields.put(37, "000000000001");
        fields.put(39, "00");
        fields.put(41, "TERM0001");
        fields.put(42, "MERCH0000000001");
        fields.put(49, "840");
        return fields;
    }

    private static Iso8583Message message(String de38) {
        return message(physical(), de38);
    }

    private static Iso8583Message message(Map<Integer, String> fields, String de38) {
        Map<Integer, String> accepted = new LinkedHashMap<>(fields);
        if (de38 != null && !de38.isEmpty()) {
            accepted.put(38, de38);
        }
        long bits = 0L;
        int bodyLength = 20;
        for (Map.Entry<Integer, String> field : accepted.entrySet()) {
            bits |= 1L << (64 - field.getKey());
            bodyLength += field.getValue().length();
        }
        return new Iso8583Message(
                "0110", PrimaryBitmap.of(bits), 20, bodyLength, accepted);
    }

    private static void assertMetadataFails(String key, String value) {
        Map<String, String> response = echo();
        response.put(key, value);
        assertFails(Rule.INSTRUCTION_FIELD_MISMATCH, key, response, message(null));
    }

    private static void assertPhysicalFails(int dataElement, String value) {
        Map<Integer, String> response = physical();
        response.put(dataElement, value);
        assertFails(
                Rule.INSTRUCTION_FIELD_MISMATCH,
                "de" + dataElement,
                echo(),
                message(response, null));
    }

    private static void assertFails(
            Rule expectedRule, String expectedComponent, Map<String, String> response,
            Iso8583Message message) {
        assertFailsWith(
                expectedRule,
                expectedComponent,
                ORIGINAL,
                new Iso8583TrustedEvidenceRecord(WIRE_HASH, null),
                response,
                message);
    }

    private static void assertFailsWith(
            Rule expectedRule,
            String expectedComponent,
            Iso8583TrustedEvidenceRecord trusted,
            Map<String, String> response,
            Iso8583Message message) {
        assertFailsWith(
                expectedRule, expectedComponent, ORIGINAL, trusted, response, message);
    }

    private static void assertFailsWith(
            Rule expectedRule,
            String expectedComponent,
            Iso8583OriginalInstruction original,
            Iso8583TrustedEvidenceRecord trusted,
            Map<String, String> response,
            Iso8583Message message) {
        assertThatThrownBy(() -> Iso8583InstructionBindingValidator.validate(
                original, trusted, message, new Iso8583De48Response(response)))
                .isInstanceOf(Iso8583InstructionBindingException.class)
                .satisfies(thrown -> {
                    Iso8583InstructionBindingException failure =
                            (Iso8583InstructionBindingException) thrown;
                    assertThat(failure.rule()).isEqualTo(expectedRule);
                    assertThat(failure.component()).isEqualTo(expectedComponent);
                    assertThat(failure.getMessage())
                            .doesNotContain("CORR-X")
                            .doesNotContain("ACQ-B")
                            .doesNotContain("ZZ9999")
                            .doesNotContain("AB1234");
                });
    }
}
