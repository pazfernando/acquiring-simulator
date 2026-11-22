package com.acquiring.simulator.channel;

import static com.acquiring.simulator.channel.C4EvidenceFixtures.NETWORK;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.keyPair;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.registry;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.sign;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.signRelay;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.trusted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pure signed-P5/P6 field binding over the approved byte corpus.
 *
 * <p>Each positive case builds the real persisted {@code 0100} octets and the real received
 * {@code 0110} octets from their authoritative hex, constructs the signed attestation from
 * the fixture's exact thirty-one attested fields, signs it with an ephemeral in-memory
 * Ed25519 key, runs the real DIRECT or RELAY verifier, and then runs the binder. Negative
 * cases alter exactly one boundary after a successful verification: the exact response
 * bytes, the exact original bytes, the trusted context, the signed content, or one signed
 * business field.</p>
 *
 * <p>No storage, receipt, duplicate/conflict, HTTP, key custody or runtime path exists
 * here. Ephemeral test keys are never written or logged.</p>
 */
class C4P6SignedFieldBinderTest {

    private static final Path PACKED =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/packed-vectors.json");
    private static final Path EVIDENCE =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/evidence-fixtures.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String NETWORK_KEY = "SPEC-KEY-NETWORK-SIM";
    private static final String ALTERNATE_ENV = "OTHER-ENV";
    private static final HexFormat HEX = HexFormat.of();

    @Test
    void directPositiveBindsTheCompleteSignedFieldSetToExactBytes() throws Exception {
        JsonNode evidence = evidence("EF:V02-approved-response");
        byte[] original = originalBytes(evidence);
        byte[] response = responseBytes("V02-approved-response");
        C4EvidenceAttestation signed = attestation(evidence.get("attestedFields"));

        assertThat(signed.canonicalSha256())
                .as("the constructed attestation is the fixture's exact canonical content")
                .isEqualTo(evidence.get("attestedContentSha256").asText());

        Verified verified = verifyDirect(evidence, signed, response);
        C4P6BindingDecision decision = C4P6SignedFieldBinder.bind(
                verified.attestation(),
                signed,
                verified.context(),
                original,
                response);

        assertThat(decision.scope()).isEqualTo(C4P6BindingDecision.BindingScope
                .SIGNED_P5_FIELDS_BOUND_TO_PERSISTED_INSTRUCTION_AND_RESPONSE);
        assertThat(decision.evidenceId()).isEqualTo("EVID-001");
        assertThat(decision.authorizationId()).isEqualTo("AUTH-001");
        assertThat(decision.transactionId()).isEqualTo("TX-001");
        assertThat(decision.amountMinor()).isEqualTo(1000L);
        assertThat(decision.currency()).isEqualTo("840");
        assertThat(decision.de39()).isEqualTo("00");
        assertThat(decision.result()).isEqualTo("APPROVED");
        assertThat(decision.reason()).isEqualTo("APPROVED");
        assertThat(decision.hasAuthorizationReference()).isFalse();
    }

    @Test
    void directPositiveBindsAnActualDe38Reference() throws Exception {
        JsonNode evidence = evidence("EF:V10-actual-DE38");
        C4EvidenceAttestation signed = attestation(evidence.get("attestedFields"));
        byte[] response = responseBytes("V10-actual-DE38");
        Verified verified = verifyDirect(evidence, signed, response);

        C4P6BindingDecision decision = C4P6SignedFieldBinder.bind(
                verified.attestation(), signed, verified.context(),
                originalBytes(evidence), response);

        assertThat(decision.authorizationReference()).isEqualTo("AB1234");
        assertThat(decision.hasAuthorizationReference()).isTrue();
    }

    @Test
    void relayPositiveBindsBothRealSignaturesToExactBytes() throws Exception {
        JsonNode evidence = evidence("EF:V02-approved-response");
        byte[] original = originalBytes(evidence);
        byte[] response = responseBytes("V02-approved-response");
        C4EvidenceAttestation signed = attestation(evidence.get("attestedFields"));

        KeyPair issuer = keyPair();
        KeyPair network = keyPair();
        C4CarrierEnvelope envelope = relayEnvelope(signed, response, issuer, network);
        byte[] late = new C4LateDeliveryEnvelope(
                C4LateDeliveryEnvelope.VERSION,
                "LATE-R1",
                Base64.getEncoder().encodeToString(response),
                envelope).toCanonicalBytes();

        C4AuthenticatedTransportContext context =
                new C4AuthenticatedTransportContext(signed.environmentId(), NETWORK);
        C4VerifiedLateDelivery verifiedLate = C4LateDeliveryVerifier.verify(
                C4LateDeliveryParser.parse(late),
                context,
                relayRegistry(signed, issuer, network));

        C4P6BindingDecision decision = C4P6SignedFieldBinder.bindLate(
                verifiedLate, signed, context, original, response);

        assertThat(verifiedLate.senderBinding())
                .isEqualTo(C4CarrierCandidate.SenderBinding.RELAY_SENDER_MATCHED);
        assertThat(decision.result()).isEqualTo("APPROVED");
        assertThat(decision.transactionId()).isEqualTo("TX-001");
    }

    @Test
    void tamperedResponseBytesAreRejectedByTheExactHash() throws Exception {
        JsonNode evidence = evidence("EF:V02-approved-response");
        C4EvidenceAttestation signed = attestation(evidence.get("attestedFields"));
        byte[] response = responseBytes("V02-approved-response");
        Verified verified = verifyDirect(evidence, signed, response);

        byte[] otherResponse = responseBytes("V04-decline-response");
        assertViolation(
                () -> C4P6SignedFieldBinder.bind(
                        verified.attestation(), signed, verified.context(),
                        originalBytes(evidence), otherResponse),
                C4P6BindingViolation.RESPONSE_HASH_MISMATCH,
                "responseSha256");
    }

    @Test
    void tamperedPersistedInstructionBytesAreRejectedByTheExactHash() throws Exception {
        JsonNode evidence = evidence("EF:V02-approved-response");
        C4EvidenceAttestation signed = attestation(evidence.get("attestedFields"));
        byte[] response = responseBytes("V02-approved-response");
        Verified verified = verifyDirect(evidence, signed, response);

        byte[] original = originalBytes(evidence);
        original[3] = '1';
        assertViolation(
                () -> C4P6SignedFieldBinder.bind(
                        verified.attestation(), signed, verified.context(),
                        original, response),
                C4P6BindingViolation.INSTRUCTION_HASH_MISMATCH,
                "instructionSha256");
    }

    @Test
    void echoedCorrelationMismatchFailsInstructionBindingAfterP4() throws Exception {
        JsonNode evidence = evidence("EF:N-wrong-correlation");
        C4EvidenceAttestation signed = attestation(evidence.get("attestedFields"));
        byte[] response = responseBytes("N-wrong-correlation");
        Verified verified = verifyDirect(evidence, signed, response);

        C4P6BindingException error = catchThrowableOfType(
                () -> C4P6SignedFieldBinder.bind(
                        verified.attestation(), signed, verified.context(),
                        originalBytes(evidence), response),
                C4P6BindingException.class);

        assertThat(error.violation())
                .isEqualTo(C4P6BindingViolation.INSTRUCTION_BINDING);
        assertThat(error.component()).isEqualTo("cid");
        assertThat(error.sourceRule()).isEqualTo("INSTRUCTION_FIELD_MISMATCH");
    }

    @Test
    void unsolicitedDe38FailsExactAuthorizationReferenceBinding() throws Exception {
        JsonNode evidence = evidence("EF:N-unsolicited-DE38");
        C4EvidenceAttestation signed = attestation(evidence.get("attestedFields"));
        byte[] response = responseBytes("N-unsolicited-DE38");
        Verified verified = verifyDirect(evidence, signed, response);

        C4P6BindingException error = catchThrowableOfType(
                () -> C4P6SignedFieldBinder.bind(
                        verified.attestation(), signed, verified.context(),
                        originalBytes(evidence), response),
                C4P6BindingException.class);

        assertThat(error.violation())
                .isEqualTo(C4P6BindingViolation.INSTRUCTION_BINDING);
        assertThat(error.component()).isEqualTo("de38");
        assertThat(error.sourceRule()).isEqualTo("AUTHORIZATION_REFERENCE_UNSOLICITED");
    }

    @Test
    void missingAssertedDe38FailsExactAuthorizationReferenceBinding() throws Exception {
        JsonNode evidence = evidence("EF:N-missing-DE38-asserted");
        C4EvidenceAttestation signed = attestation(evidence.get("attestedFields"));
        byte[] response = responseBytes("N-missing-DE38-asserted");
        Verified verified = verifyDirect(evidence, signed, response);

        C4P6BindingException error = catchThrowableOfType(
                () -> C4P6SignedFieldBinder.bind(
                        verified.attestation(), signed, verified.context(),
                        originalBytes(evidence), response),
                C4P6BindingException.class);

        assertThat(error.violation())
                .isEqualTo(C4P6BindingViolation.INSTRUCTION_BINDING);
        assertThat(error.component()).isEqualTo("de38");
        assertThat(error.sourceRule()).isEqualTo("AUTHORIZATION_REFERENCE_MISSING");
    }

    @Test
    void domainFailureWinsBeforeP4AndInstructionBinding() throws Exception {
        JsonNode evidence = evidence("EF:N-response-invalid-date");
        C4EvidenceAttestation signed = attestation(evidence.get("attestedFields"));
        byte[] response = responseBytes("N-response-invalid-date");
        Verified verified = verifyDirect(evidence, signed, response);

        C4P6BindingException error = catchThrowableOfType(
                () -> C4P6SignedFieldBinder.bind(
                        verified.attestation(), signed, verified.context(),
                        originalBytes(evidence), response),
                C4P6BindingException.class);

        assertThat(error.violation()).isEqualTo(C4P6BindingViolation.DOMAIN);
        assertThat(error.component()).isEqualTo("sent");
        assertThat(error.sourceRule()).isEqualTo("INVALID_GREGORIAN_DATE");
    }

    @Test
    void signedDeclineCannotBindToAnApprovedResponse() throws Exception {
        JsonNode evidence = evidence("EF:V02-approved-response");
        C4EvidenceAttestation approved = attestation(evidence.get("attestedFields"));
        byte[] response = responseBytes("V02-approved-response");

        C4EvidenceAttestation declined = copy(
                approved, "05", "DECLINED", "DELIBERATE_DECLINE",
                approved.amountMinor(), approved.authorizationReference(),
                approved.availableAt());
        Verified verified = verifyDirect(evidence, declined, response);

        assertThat(verified.attestation().attestationType())
                .isEqualTo(C4AttestationType.ISSUER_DECLINE);
        assertViolation(
                () -> C4P6SignedFieldBinder.bind(
                        verified.attestation(), declined, verified.context(),
                        originalBytes(evidence), response),
                C4P6BindingViolation.SIGNED_RESULT_MISMATCH,
                "DE39");
    }

    @Test
    void signedAmountCannotBindToADifferentOriginalAmount() throws Exception {
        JsonNode evidence = evidence("EF:V02-approved-response");
        C4EvidenceAttestation approved = attestation(evidence.get("attestedFields"));
        byte[] response = responseBytes("V02-approved-response");

        C4EvidenceAttestation altered = copy(
                approved, approved.DE39(), approved.result(), approved.reason(),
                1001, approved.authorizationReference(), approved.availableAt());
        Verified verified = verifyDirect(evidence, altered, response);

        assertViolation(
                () -> C4P6SignedFieldBinder.bind(
                        verified.attestation(), altered, verified.context(),
                        originalBytes(evidence), response),
                C4P6BindingViolation.SIGNED_INSTRUCTION_MISMATCH,
                "de4");
    }

    @Test
    void signedAvailabilityMustEqualTheResponseAtSeconds() throws Exception {
        JsonNode evidence = evidence("EF:V02-approved-response");
        C4EvidenceAttestation approved = attestation(evidence.get("attestedFields"));
        byte[] response = responseBytes("V02-approved-response");

        C4EvidenceAttestation altered = copy(
                approved, approved.DE39(), approved.result(), approved.reason(),
                approved.amountMinor(), approved.authorizationReference(),
                "2026-11-18T12:00:02Z");
        Verified verified = verifyDirect(evidence, altered, response);

        assertViolation(
                () -> C4P6SignedFieldBinder.bind(
                        verified.attestation(), altered, verified.context(),
                        originalBytes(evidence), response),
                C4P6BindingViolation.SIGNED_AVAILABILITY_MISMATCH,
                "availableAt");
    }

    @Test
    void subSecondAvailabilityIsRejectedRatherThanTruncated() throws Exception {
        JsonNode evidence = evidence("EF:V02-approved-response");
        C4EvidenceAttestation approved = attestation(evidence.get("attestedFields"));
        byte[] response = responseBytes("V02-approved-response");

        C4EvidenceAttestation altered = copy(
                approved, approved.DE39(), approved.result(), approved.reason(),
                approved.amountMinor(), approved.authorizationReference(),
                "2026-11-18T12:00:01.500Z");
        Verified verified = verifyDirect(evidence, altered, response);

        assertViolation(
                () -> C4P6SignedFieldBinder.bind(
                        verified.attestation(), altered, verified.context(),
                        originalBytes(evidence), response),
                C4P6BindingViolation.SIGNED_AVAILABILITY_MISMATCH,
                "availableAt");
    }

    @Test
    void signedReferenceCannotBindToDifferentDe38Bytes() throws Exception {
        JsonNode evidence = evidence("EF:V10-actual-DE38");
        C4EvidenceAttestation actual = attestation(evidence.get("attestedFields"));
        byte[] response = responseBytes("V10-actual-DE38");

        C4EvidenceAttestation altered = copy(
                actual, actual.DE39(), actual.result(), actual.reason(),
                actual.amountMinor(), "ZZ9999", actual.availableAt());
        Verified verified = verifyDirect(evidence, altered, response);

        C4P6BindingException error = catchThrowableOfType(
                () -> C4P6SignedFieldBinder.bind(
                        verified.attestation(), altered, verified.context(),
                        originalBytes(evidence), response),
                C4P6BindingException.class);

        assertThat(error.violation())
                .isEqualTo(C4P6BindingViolation.INSTRUCTION_BINDING);
        assertThat(error.component()).isEqualTo("de38");
        assertThat(error.sourceRule()).isEqualTo("AUTHORIZATION_REFERENCE_MISMATCH");
    }

    @Test
    void currentEnvironmentSubstitutionIsRejected() throws Exception {
        JsonNode evidence = evidence("EF:V02-approved-response");
        C4EvidenceAttestation signed = attestation(evidence.get("attestedFields"));
        byte[] response = responseBytes("V02-approved-response");
        Verified verified = verifyDirect(evidence, signed, response);

        C4AuthenticatedTransportContext other = new C4AuthenticatedTransportContext(
                ALTERNATE_ENV, signed.signingPrincipal());
        assertViolation(
                () -> C4P6SignedFieldBinder.bind(
                        verified.attestation(), signed, other,
                        originalBytes(evidence), response),
                C4P6BindingViolation.ENVIRONMENT_BINDING,
                "environmentId");
    }

    @Test
    void currentPrincipalSubstitutionIsRejected() throws Exception {
        JsonNode evidence = evidence("EF:V02-approved-response");
        C4EvidenceAttestation signed = attestation(evidence.get("attestedFields"));
        byte[] response = responseBytes("V02-approved-response");
        Verified verified = verifyDirect(evidence, signed, response);

        C4AuthenticatedTransportContext other = new C4AuthenticatedTransportContext(
                signed.environmentId(), "ACL-AUTH");
        assertViolation(
                () -> C4P6SignedFieldBinder.bind(
                        verified.attestation(), signed, other,
                        originalBytes(evidence), response),
                C4P6BindingViolation.PRINCIPAL_BINDING,
                "signingPrincipal");
    }

    @Test
    void verifiedResultCannotBePairedWithDifferentSignedContent() throws Exception {
        JsonNode evidence = evidence("EF:V02-approved-response");
        C4EvidenceAttestation approved = attestation(evidence.get("attestedFields"));
        byte[] response = responseBytes("V02-approved-response");
        Verified verified = verifyDirect(evidence, approved, response);

        C4EvidenceAttestation different = copy(
                approved, approved.DE39(), approved.result(), approved.reason(),
                1001, approved.authorizationReference(), approved.availableAt());
        assertViolation(
                () -> C4P6SignedFieldBinder.bind(
                        verified.attestation(), different, verified.context(),
                        originalBytes(evidence), response),
                C4P6BindingViolation.VERIFIED_BINDING,
                "canonicalAttestationSha256");
    }

    @Test
    void bindingDecisionClaimsNoConfirmedResultBeforeAtomicReceipt() {
        assertThat(Arrays.stream(C4P6BindingDecision.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .doesNotContain("isConfirmed", "isAdmitted", "isCommitted", "isReceiptDurable",
                        "confirmed", "admitted", "committed", "receiptDurable",
                        "isValid", "isVerified", "isTrusted",
                        "valid", "verified", "trusted");

        C4P6BindingDecision decision = new C4P6BindingDecision(
                "EVID-001", "AUTH-001", "TX-001", 1000L, "840", "00",
                "APPROVED", "APPROVED", null,
                C4P6BindingDecision.BindingScope
                        .SIGNED_P5_FIELDS_BOUND_TO_PERSISTED_INSTRUCTION_AND_RESPONSE);
        assertThat(decision.toString())
                .doesNotContain("APPROVED")
                .doesNotContain("CONFIRMED")
                .contains("SIGNED_P5_FIELDS_BOUND_TO_PERSISTED_INSTRUCTION_AND_RESPONSE");
    }

    // --- Helpers ---------------------------------------------------------------

    private record Verified(
            C4VerifiedAttestation attestation,
            C4AuthenticatedTransportContext context) {
    }

    private static Verified verifyDirect(
            JsonNode evidence, C4EvidenceAttestation signed, byte[] response)
            throws Exception {
        KeyPair key = keyPair();
        C4CarrierEnvelope envelope = C4EvidenceFixtures.direct(signed, key.getPrivate());
        C4AuthenticatedTransportContext context = new C4AuthenticatedTransportContext(
                signed.environmentId(), signed.signingPrincipal());
        C4VerifiedAttestation verified = C4EvidenceVerifier.verify(
                C4EvidenceFixtures.candidate(envelope, response, signed.signingPrincipal()),
                context,
                registry(trusted(
                        signed.signingPrincipal(), signed.keyId(), roleFor(signed),
                        key.getPublic(), signed.acquirerRef())));
        assertThat(evidence.get("attestedFields").get("responseSha256").asText())
                .isEqualTo(C4CarrierSchema.sha256Hex(response));
        return new Verified(verified, context);
    }

    private static C4CarrierEnvelope relayEnvelope(
            C4EvidenceAttestation signed, byte[] response, KeyPair issuer, KeyPair network)
            throws Exception {
        String responseHash = C4CarrierSchema.sha256Hex(response);
        C4RelayAttestation unsigned = new C4RelayAttestation(
                NETWORK,
                "RELAY-R1",
                "2026-11-18T12:00:02Z",
                NETWORK_KEY,
                responseHash,
                signed.canonicalSha256(),
                new byte[C4CarrierParser.SIGNATURE_BYTES]);
        C4RelayAttestation relay = new C4RelayAttestation(
                unsigned.principal(),
                unsigned.receiptId(),
                unsigned.deliveredAt(),
                unsigned.keyId(),
                unsigned.responseSha256(),
                unsigned.attestationSha256(),
                signRelay(unsigned, network.getPrivate()));
        return new C4CarrierEnvelope(
                C4CarrierEnvelope.VERSION,
                C4CarrierEnvelope.Mode.RELAY,
                signed,
                signed.keyId(),
                sign(signed, issuer.getPrivate()),
                relay);
    }

    private static C4KeyRegistry relayRegistry(
            C4EvidenceAttestation signed, KeyPair issuer, KeyPair network) {
        return registry(
                trusted(signed.signingPrincipal(), signed.keyId(), roleFor(signed),
                        issuer.getPublic(), signed.acquirerRef()),
                trusted(NETWORK, NETWORK_KEY, C4PrincipalRole.NETWORK_SIM,
                        network.getPublic(), signed.acquirerRef()));
    }

    private static C4PrincipalRole roleFor(C4EvidenceAttestation signed) {
        return switch (signed.actor()) {
            case "ISSUER" -> C4PrincipalRole.ISSUER_SIM;
            case "NETWORK" -> C4PrincipalRole.NETWORK_SIM;
            case "PROCESSOR" -> C4PrincipalRole.PROCESSOR_AUTH;
            default -> throw new IllegalArgumentException("unsupported actor");
        };
    }

    private static C4EvidenceAttestation attestation(JsonNode fields) {
        JsonNode reference = fields.get("authorizationReference");
        return new C4EvidenceAttestation(
                fields.get("schemaVersion").asInt(),
                fields.get("environmentId").asText(),
                fields.get("producerPrincipal").asText(),
                fields.get("evidenceId").asText(),
                fields.get("instructionSha256").asText(),
                fields.get("profile").asText(),
                fields.get("authorizationId").asText(),
                fields.get("transactionId").asText(),
                fields.get("correlationId").asText(),
                fields.get("instructionAttemptId").asText(),
                fields.get("acquirerRef").asText(),
                fields.get("merchantRef").asText(),
                fields.get("terminalRef").asText(),
                fields.get("paymentRef").asText(),
                fields.get("generation").asInt(),
                fields.get("amountMinor").asInt(),
                fields.get("currency").asText(),
                fields.get("STAN").asText(),
                fields.get("RRN").asText(),
                fields.get("DE7").asText(),
                fields.get("sent").asText(),
                fields.get("actor").asText(),
                fields.get("stage").asText(),
                fields.get("DE39").asText(),
                fields.get("result").asText(),
                fields.get("reason").asText(),
                reference == null || reference.isNull() ? null : reference.asText(),
                fields.get("availableAt").asText(),
                fields.get("signingPrincipal").asText(),
                fields.get("keyId").asText(),
                fields.get("responseSha256").asText());
    }

    private static C4EvidenceAttestation copy(
            C4EvidenceAttestation a,
            String de39,
            String result,
            String reason,
            int amountMinor,
            String authorizationReference,
            String availableAt) {
        return new C4EvidenceAttestation(
                a.schemaVersion(),
                a.environmentId(),
                a.producerPrincipal(),
                a.evidenceId(),
                a.instructionSha256(),
                a.profile(),
                a.authorizationId(),
                a.transactionId(),
                a.correlationId(),
                a.instructionAttemptId(),
                a.acquirerRef(),
                a.merchantRef(),
                a.terminalRef(),
                a.paymentRef(),
                a.generation(),
                amountMinor,
                a.currency(),
                a.STAN(),
                a.RRN(),
                a.DE7(),
                a.sent(),
                a.actor(),
                a.stage(),
                de39,
                result,
                reason,
                authorizationReference,
                availableAt,
                a.signingPrincipal(),
                a.keyId(),
                a.responseSha256());
    }

    private static byte[] originalBytes(JsonNode evidence) {
        return HEX.parseHex(evidence.get("original0100Hex").asText());
    }

    private static byte[] responseBytes(String fixtureId) throws Exception {
        return HEX.parseHex(vector(fixtureId).get("hex").asText());
    }

    private static JsonNode vector(String id) throws Exception {
        Map<String, JsonNode> byId = new HashMap<>();
        for (JsonNode vector : JSON.readTree(Files.readString(PACKED)).get("vectors")) {
            byId.put(vector.get("id").asText(), vector);
        }
        JsonNode found = byId.get(id);
        if (found == null) {
            throw new IllegalArgumentException("unknown packed vector " + id);
        }
        return found;
    }

    private static JsonNode evidence(String id) throws Exception {
        for (JsonNode fixture
                : JSON.readTree(Files.readString(EVIDENCE)).get("fixtures")) {
            if (id.equals(fixture.get("id").asText())) {
                return fixture;
            }
        }
        throw new IllegalArgumentException("unknown evidence fixture " + id);
    }

    private static void assertViolation(
            Runnable call, C4P6BindingViolation violation, String component) {
        C4P6BindingException error =
                catchThrowableOfType(call::run, C4P6BindingException.class);
        assertThat(error).isNotNull();
        assertThat(error.violation()).isEqualTo(violation);
        assertThat(error.component()).isEqualTo(component);
    }
}
