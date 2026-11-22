package com.acquiring.simulator.channel;

import static com.acquiring.simulator.channel.C4EvidenceFixtures.ACQUIRER;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.ENV;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.ISSUER;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.ISSUER_KEY;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.NETWORK;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.NETWORK_KEY;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.PROCESSOR;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.PROCESSOR_KEY;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.body;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.bodySha256;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.context;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.direct;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.keyPair;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.relay;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.registry;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.trusted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Closed C4 late-delivery envelope parsing composed with the existing real DIRECT/RELAY
 * Ed25519 verifier. Keys are ephemeral in-memory test pairs; the approved carrier vector
 * signature bytes are explicitly non-cryptographic and are used for shape only.
 *
 * <p>No payment, send/resend, storage, admission, worker, HTTP or runtime path is
 * exercised.</p>
 */
class C4LateDeliveryTest {

    private static final Path VECTORS = Path.of(
            "docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/carrier-vectors.json");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SCHEMA_ISSUER_APPROVAL = "LATE-001";

    @Test
    void approvedLateShapeFixtureParsesAsUnverifiedCandidate() throws Exception {
        JsonNode testCase = caseById("K03-late");
        byte[] raw = testCase.get("lateEnvelopeJson").asText()
                .getBytes(StandardCharsets.UTF_8);

        C4LateDeliveryCandidate candidate = C4LateDeliveryParser.parse(raw);

        assertThat(candidate.parseState())
                .isEqualTo(C4LateDeliveryCandidate.ParseState.UNVERIFIED_CANDIDATE);
        assertThat(candidate.lateEnvelope().deliveryReceiptId()).isEqualTo(SCHEMA_ISSUER_APPROVAL);
        assertThat(candidate.lateEnvelope().evidence().mode())
                .isEqualTo(C4CarrierEnvelope.Mode.DIRECT);
        assertThat(candidate.responseBodySha256())
                .isEqualTo(testCase.get("responseBodySha256").asText());
        assertThat(candidate.canonicalAttestationSha256())
                .isEqualTo(candidate.lateEnvelope().evidence().attestation().canonicalSha256());
        assertThat(candidate.canonicalLateEnvelopeSha256()).hasSize(64);
        assertThat(candidate.toString())
                .doesNotContain(testCase.get("lateEnvelopeJson").asText())
                .doesNotContain(testCase.get("responseBodyHex").asText())
                .doesNotContain(candidate.lateEnvelope().evidence().attestation()
                        .instructionSha256());
    }

    @Test
    void approvedLateMissingEvidenceFixtureIsRejected() throws Exception {
        JsonNode testCase = caseById("K11-late-missing-proof");
        byte[] raw = testCase.get("lateEnvelopeJson").asText()
                .getBytes(StandardCharsets.UTF_8);

        C4LateDeliveryException error = catchThrowableOfType(
                () -> C4LateDeliveryParser.parse(raw), C4LateDeliveryException.class);

        assertThat(error.violation())
                .isEqualTo(C4LateDeliveryViolation.LATE_ENVELOPE_SCHEMA);
        assertThat(error.field()).isEqualTo("evidence");
    }

    @Test
    void validDirectLateEnvelopeVerifiesWithRealEd25519() throws Exception {
        KeyPair issuer = keyPair();
        C4CarrierEnvelope evidence = direct(issuerApproval(), issuer.getPrivate());
        C4LateDeliveryCandidate candidate = parse(lateBytes(evidence, body(), "LATE-R1"));

        C4VerifiedLateDelivery result = C4LateDeliveryVerifier.verify(
                candidate,
                context(ISSUER),
                registry(trusted(ISSUER, ISSUER_KEY, C4PrincipalRole.ISSUER_SIM,
                        issuer.getPublic(), ACQUIRER)));

        assertThat(result.deliveryReceiptId()).isEqualTo("LATE-R1");
        assertThat(result.senderBinding())
                .isEqualTo(C4CarrierCandidate.SenderBinding.DIRECT_SENDER_MATCHED);
        assertThat(result.attestation().attestationType())
                .isEqualTo(C4AttestationType.ISSUER_APPROVAL);
        assertThat(result.attestation().signingPrincipal()).isEqualTo(ISSUER);
        assertThat(result.attestation().verificationScope()).isEqualTo(
                C4VerifiedAttestation.VerificationScope
                        .DIRECT_ED25519_AND_TRUST_BINDINGS_VERIFIED);
        assertThat(result.attestation().relayBinding()).isNull();
        assertThat(result.canonicalLateEnvelopeSha256())
                .isEqualTo(candidate.canonicalLateEnvelopeSha256());
    }

    @Test
    void validRelayLateEnvelopeVerifiesBothRealSignatures() throws Exception {
        KeyPair issuer = keyPair();
        KeyPair network = keyPair();
        C4CarrierEnvelope evidence = relay(
                issuerApproval(), issuer.getPrivate(), network.getPrivate());
        C4LateDeliveryCandidate candidate = parse(lateBytes(evidence, body(), "LATE-R2"));

        C4VerifiedLateDelivery result = C4LateDeliveryVerifier.verify(
                candidate, context(NETWORK), relayRegistry(issuer, network));

        assertThat(result.senderBinding())
                .isEqualTo(C4CarrierCandidate.SenderBinding.RELAY_SENDER_MATCHED);
        assertThat(result.attestation().verificationScope()).isEqualTo(
                C4VerifiedAttestation.VerificationScope
                        .RELAY_ED25519_AND_TRUST_BINDINGS_VERIFIED);
        assertThat(result.attestation().relayBinding()).isNotNull();
        assertThat(result.attestation().relayBinding().principal()).isEqualTo(NETWORK);
        assertThat(result.attestation().attestationType())
                .isEqualTo(C4AttestationType.ISSUER_APPROVAL);
    }

    @Test
    void responseBodyHashMismatchIsRejectedBeforeVerification() throws Exception {
        KeyPair issuer = keyPair();
        C4CarrierEnvelope evidence = direct(issuerApproval(), issuer.getPrivate());
        byte[] tamperedBody = "0110SIMULATED-TAMPERED-SECRET-MARKER"
                .getBytes(StandardCharsets.US_ASCII);
        byte[] raw = lateBytes(evidence, tamperedBody, "LATE-R3");

        C4LateDeliveryException error = catchThrowableOfType(
                () -> C4LateDeliveryParser.parse(raw), C4LateDeliveryException.class);

        assertThat(error.violation()).isEqualTo(C4LateDeliveryViolation.BODY_HASH_BINDING);
        assertThat(error.field()).isEqualTo("responseSha256");
        assertThat(error.getMessage())
                .doesNotContain("SECRET-MARKER")
                .doesNotContain(Base64.getEncoder().encodeToString(tamperedBody));
    }

    @Test
    void nonCanonicalResponseBodyBase64IsRejected() throws Exception {
        KeyPair issuer = keyPair();
        C4CarrierEnvelope evidence = direct(issuerApproval(), issuer.getPrivate());
        String canonicalB64 = Base64.getEncoder().encodeToString(body());
        String unpadded = canonicalB64.substring(0, canonicalB64.length() - 1);
        C4LateDeliveryEnvelope late = new C4LateDeliveryEnvelope(
                1, "LATE-R4", unpadded, evidence);

        C4LateDeliveryException error = catchThrowableOfType(
                () -> C4LateDeliveryParser.parse(late.toCanonicalBytes()),
                C4LateDeliveryException.class);

        assertThat(error.violation())
                .isEqualTo(C4LateDeliveryViolation.RESPONSE_BODY_ENCODING);
        assertThat(error.field()).isEqualTo("responseBodyBase64");
    }

    @Test
    void oversizedOriginalBodyIsRejected() throws Exception {
        KeyPair issuer = keyPair();
        byte[] tooLarge = new byte[C4LateDeliveryParser.MAX_RESPONSE_BODY_BYTES + 1];
        C4EvidenceAttestation attestation = C4EvidenceFixtures.attestation(
                ISSUER, ISSUER_KEY, "ISSUER", "ISSUER_REPLIED", "00",
                "APPROVED", "APPROVED", C4CarrierSchema.sha256Hex(tooLarge));
        C4CarrierEnvelope evidence = direct(attestation, issuer.getPrivate());
        byte[] raw = lateBytes(evidence, tooLarge, "LATE-R5");

        C4LateDeliveryException error = catchThrowableOfType(
                () -> C4LateDeliveryParser.parse(raw), C4LateDeliveryException.class);

        assertThat(error.violation()).isEqualTo(C4LateDeliveryViolation.RESPONSE_BODY_SIZE);
    }

    @Test
    void oversizedLateEnvelopeIsRejectedBeforeParsing() {
        C4LateDeliveryException error = catchThrowableOfType(
                () -> C4LateDeliveryParser.parse(new byte[
                        C4LateDeliveryParser.MAX_LATE_ENVELOPE_BYTES + 1]),
                C4LateDeliveryException.class);

        assertThat(error.violation()).isEqualTo(C4LateDeliveryViolation.LATE_ENVELOPE_SIZE);
    }

    @Test
    void compressedPayloadIsRejected() {
        byte[] gzip = {0x1f, (byte) 0x8b, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00};

        C4LateDeliveryException error = catchThrowableOfType(
                () -> C4LateDeliveryParser.parse(gzip), C4LateDeliveryException.class);

        assertThat(error.violation()).isEqualTo(C4LateDeliveryViolation.COMPRESSION);
    }

    @Test
    void foldedPayloadIsRejected() throws Exception {
        KeyPair issuer = keyPair();
        String canonical = new String(
                lateBytes(direct(issuerApproval(), issuer.getPrivate()), body(), "LATE-R6"),
                StandardCharsets.US_ASCII);
        byte[] folded = ("{\r\n" + canonical.substring(1))
                .getBytes(StandardCharsets.UTF_8);

        C4LateDeliveryException error = catchThrowableOfType(
                () -> C4LateDeliveryParser.parse(folded), C4LateDeliveryException.class);

        assertThat(error.violation())
                .isEqualTo(C4LateDeliveryViolation.LATE_ENVELOPE_ENCODING);
    }

    @Test
    void malformedJsonIsRejected() {
        C4LateDeliveryException error = catchThrowableOfType(
                () -> C4LateDeliveryParser.parse("{\"version\":1".getBytes(
                        StandardCharsets.UTF_8)),
                C4LateDeliveryException.class);

        assertThat(error.violation())
                .isEqualTo(C4LateDeliveryViolation.LATE_ENVELOPE_SCHEMA);
    }

    @Test
    void duplicateJsonKeyIsRejected() throws Exception {
        KeyPair issuer = keyPair();
        String canonical = new String(
                lateBytes(direct(issuerApproval(), issuer.getPrivate()), body(), "LATE-R7"),
                StandardCharsets.US_ASCII);
        String duplicated = "{\"deliveryReceiptId\":\"DUP\"," + canonical.substring(1);

        C4LateDeliveryException error = catchThrowableOfType(
                () -> C4LateDeliveryParser.parse(duplicated.getBytes(
                        StandardCharsets.UTF_8)),
                C4LateDeliveryException.class);

        assertThat(error.violation())
                .isEqualTo(C4LateDeliveryViolation.LATE_ENVELOPE_SCHEMA);
    }

    @Test
    void unknownExtraKeyIsRejected() throws Exception {
        KeyPair issuer = keyPair();
        byte[] canonical = lateBytes(
                direct(issuerApproval(), issuer.getPrivate()), body(), "LATE-R8");
        ObjectNode late = (ObjectNode) MAPPER.readTree(canonical);
        late.put("VALID", true);

        C4LateDeliveryException error = catchThrowableOfType(
                () -> C4LateDeliveryParser.parse(
                        MAPPER.writeValueAsString(late).getBytes(StandardCharsets.UTF_8)),
                C4LateDeliveryException.class);

        assertThat(error.violation())
                .isEqualTo(C4LateDeliveryViolation.LATE_ENVELOPE_SCHEMA);
        assertThat(error.field()).isEqualTo("VALID");
    }

    @Test
    void unknownNestedEvidenceKeyIsRejected() throws Exception {
        KeyPair issuer = keyPair();
        byte[] canonical = lateBytes(
                direct(issuerApproval(), issuer.getPrivate()), body(), "LATE-R9");
        ObjectNode late = (ObjectNode) MAPPER.readTree(canonical);
        ((ObjectNode) late.get("evidence")).put("P5_ATTESTATION_VALID", true);

        C4CarrierException error = catchThrowableOfType(
                () -> C4LateDeliveryParser.parse(
                        MAPPER.writeValueAsString(late).getBytes(StandardCharsets.UTF_8)),
                C4CarrierException.class);

        assertThat(error.violation()).isEqualTo(C4CarrierViolation.CARRIER_SCHEMA);
        assertThat(error.field()).isEqualTo("P5_ATTESTATION_VALID");
    }

    @Test
    void nonCanonicalSpacingIsRejected() throws Exception {
        KeyPair issuer = keyPair();
        byte[] canonical = lateBytes(
                direct(issuerApproval(), issuer.getPrivate()), body(), "LATE-R10");
        byte[] spaced = (" " + new String(canonical, StandardCharsets.US_ASCII))
                .getBytes(StandardCharsets.UTF_8);

        C4LateDeliveryException error = catchThrowableOfType(
                () -> C4LateDeliveryParser.parse(spaced), C4LateDeliveryException.class);

        assertThat(error.violation())
                .isEqualTo(C4LateDeliveryViolation.LATE_ENVELOPE_SCHEMA);
        assertThat(error.field()).isEqualTo("canonical");
    }

    @Test
    void tamperedDirectSignatureFailsRealEd25519() throws Exception {
        KeyPair issuer = keyPair();
        C4CarrierEnvelope good = direct(issuerApproval(), issuer.getPrivate());
        byte[] altered = good.signature();
        altered[0] ^= 0x01;
        C4CarrierEnvelope tampered = new C4CarrierEnvelope(
                C4CarrierEnvelope.VERSION, C4CarrierEnvelope.Mode.DIRECT,
                good.attestation(), good.keyId(), altered, null);
        C4LateDeliveryCandidate candidate = parse(lateBytes(tampered, body(), "LATE-R11"));

        C4VerificationException error = catchThrowableOfType(
                () -> C4LateDeliveryVerifier.verify(
                        candidate,
                        context(ISSUER),
                        registry(trusted(ISSUER, ISSUER_KEY, C4PrincipalRole.ISSUER_SIM,
                                issuer.getPublic(), ACQUIRER))),
                C4VerificationException.class);

        assertThat(error.violation()).isEqualTo(C4VerificationViolation.BAD_SIGNATURE);
        assertThat(error.field()).isEqualTo("signature");
    }

    @Test
    void tamperedRelaySignatureFailsRealEd25519() throws Exception {
        KeyPair issuer = keyPair();
        KeyPair network = keyPair();
        C4CarrierEnvelope good = relay(
                issuerApproval(), issuer.getPrivate(), network.getPrivate());
        C4RelayAttestation relay = good.relay();
        byte[] altered = relay.signature();
        altered[0] ^= 0x01;
        C4RelayAttestation tamperedRelay = new C4RelayAttestation(
                relay.principal(), relay.receiptId(), relay.deliveredAt(), relay.keyId(),
                relay.responseSha256(), relay.attestationSha256(), altered);
        C4CarrierEnvelope tampered = new C4CarrierEnvelope(
                C4CarrierEnvelope.VERSION, C4CarrierEnvelope.Mode.RELAY,
                good.attestation(), good.keyId(), good.signature(), tamperedRelay);
        C4LateDeliveryCandidate candidate = parse(lateBytes(tampered, body(), "LATE-R12"));

        C4VerificationException error = catchThrowableOfType(
                () -> C4LateDeliveryVerifier.verify(
                        candidate, context(NETWORK), relayRegistry(issuer, network)),
                C4VerificationException.class);

        assertThat(error.violation()).isEqualTo(C4VerificationViolation.BAD_SIGNATURE);
        assertThat(error.field()).isEqualTo("relaySignature");
    }

    @Test
    void wrongSenderWithoutDiagnosticPermissionFailsClosed() throws Exception {
        KeyPair issuer = keyPair();
        C4LateDeliveryCandidate candidate = parse(lateBytes(
                direct(issuerApproval(), issuer.getPrivate()), body(), "LATE-R13"));

        C4VerificationException error = catchThrowableOfType(
                () -> C4LateDeliveryVerifier.verify(
                        candidate,
                        context(PROCESSOR),
                        registry(
                                trusted(ISSUER, ISSUER_KEY, C4PrincipalRole.ISSUER_SIM,
                                        issuer.getPublic(), ACQUIRER),
                                trusted(PROCESSOR, PROCESSOR_KEY,
                                        C4PrincipalRole.PROCESSOR_AUTH,
                                        keyPair().getPublic(), ACQUIRER))),
                C4VerificationException.class);

        assertThat(error.violation())
                .isEqualTo(C4VerificationViolation.DIAGNOSTIC_FORWARD_PERMISSION);
        assertThat(error.field()).isEqualTo("diagnosticForwardPermission");
    }

    @Test
    void diagnosticForwardWithExplicitScopedPermissionIsAcceptedWithoutOutcomeAuthority()
            throws Exception {
        KeyPair issuer = keyPair();
        C4CarrierEnvelope evidence = direct(issuerApproval(), issuer.getPrivate());
        C4LateDeliveryCandidate candidate = parse(lateBytes(evidence, body(), "LATE-R14"));
        C4KeyRegistry base = registry(
                trusted(ISSUER, ISSUER_KEY, C4PrincipalRole.ISSUER_SIM,
                        issuer.getPublic(), ACQUIRER));

        C4VerifiedLateDelivery result = C4LateDeliveryVerifier.verify(
                candidate,
                context("DIAG-FORWARD"),
                forwardingRegistry(base, "DIAG-FORWARD", ACQUIRER));

        assertThat(result.senderBinding())
                .isEqualTo(C4CarrierCandidate.SenderBinding.DIAGNOSTIC_FORWARD_PERMITTED);
        assertThat(result.attestation().signingPrincipal()).isEqualTo(ISSUER);
        assertThat(result.attestation().verificationScope()).isEqualTo(
                C4VerifiedAttestation.VerificationScope
                        .DIRECT_ED25519_AND_TRUST_BINDINGS_VERIFIED_VIA_AUTHORIZED_FORWARD);
        assertThat(result.attestation().relayBinding()).isNull();
    }

    @Test
    void diagnosticForwardOutsideConfiguredScopeIsRefused() throws Exception {
        KeyPair issuer = keyPair();
        C4LateDeliveryCandidate candidate = parse(lateBytes(
                direct(issuerApproval(), issuer.getPrivate()), body(), "LATE-R15"));
        C4KeyRegistry base = registry(
                trusted(ISSUER, ISSUER_KEY, C4PrincipalRole.ISSUER_SIM,
                        issuer.getPublic(), ACQUIRER));

        C4VerificationException error = catchThrowableOfType(
                () -> C4LateDeliveryVerifier.verify(
                        candidate,
                        context("DIAG-FORWARD"),
                        forwardingRegistry(base, "DIAG-FORWARD", "OTHER-ACQUIRER")),
                C4VerificationException.class);

        assertThat(error.violation())
                .isEqualTo(C4VerificationViolation.DIAGNOSTIC_FORWARD_PERMISSION);
    }

    @Test
    void relayDiagnosticForwardStillVerifiesBothSignatures() throws Exception {
        KeyPair issuer = keyPair();
        KeyPair network = keyPair();
        C4CarrierEnvelope evidence = relay(
                issuerApproval(), issuer.getPrivate(), network.getPrivate());
        C4LateDeliveryCandidate candidate = parse(lateBytes(evidence, body(), "LATE-R16"));

        C4VerifiedLateDelivery result = C4LateDeliveryVerifier.verify(
                candidate,
                context("DIAG-FORWARD"),
                forwardingRegistry(relayRegistry(issuer, network), "DIAG-FORWARD", ACQUIRER));

        assertThat(result.senderBinding())
                .isEqualTo(C4CarrierCandidate.SenderBinding.DIAGNOSTIC_FORWARD_PERMITTED);
        assertThat(result.attestation().verificationScope()).isEqualTo(
                C4VerifiedAttestation.VerificationScope
                        .RELAY_ED25519_AND_TRUST_BINDINGS_VERIFIED_VIA_AUTHORIZED_FORWARD);
        assertThat(result.attestation().relayBinding()).isNotNull();
        assertThat(result.attestation().attestationType())
                .isEqualTo(C4AttestationType.ISSUER_APPROVAL);
    }

    @Test
    void deliveryReceiptIdAloneGrantsNoDecisionOrDurabilityAuthority() throws Exception {
        KeyPair issuer = keyPair();
        C4CarrierEnvelope evidence = direct(issuerApproval(), issuer.getPrivate());
        C4KeyRegistry registry = registry(
                trusted(ISSUER, ISSUER_KEY, C4PrincipalRole.ISSUER_SIM,
                        issuer.getPublic(), ACQUIRER));

        C4VerifiedLateDelivery first = C4LateDeliveryVerifier.verify(
                parse(lateBytes(evidence, body(), "RECEIPT-A")), context(ISSUER), registry);
        C4VerifiedLateDelivery second = C4LateDeliveryVerifier.verify(
                parse(lateBytes(evidence, body(), "RECEIPT-B")), context(ISSUER), registry);

        assertThat(first.deliveryReceiptId()).isEqualTo("RECEIPT-A");
        assertThat(second.deliveryReceiptId()).isEqualTo("RECEIPT-B");
        assertThat(first.attestation()).isEqualTo(second.attestation());

        assertThat(C4VerifiedLateDelivery.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("paymentConfirmed", "receiptDurable", "isValid",
                        "isVerified", "isTrusted", "admit", "commit", "persist");
        assertThat(C4LateDeliveryVerifier.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.toLowerCase().matches(
                        ".*(send|resend|retry|persist|admit|commit|durable).*"));
    }

    @Test
    void parserAndVerifierExposeNoPaymentOrWorkerSurface() {
        assertThat(C4LateDeliveryParser.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.toLowerCase().matches(
                        ".*(send|resend|retry|persist|admit|commit|durable|worker).*"));
        assertThat(C4LateDeliveryCandidate.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("paymentConfirmed", "receiptDurable", "isValid",
                        "isVerified", "isTrusted");
    }

    private static C4LateDeliveryCandidate parse(byte[] raw) {
        return C4LateDeliveryParser.parse(raw);
    }

    private static byte[] lateBytes(
            C4CarrierEnvelope evidence, byte[] responseBody, String receiptId) {
        return new C4LateDeliveryEnvelope(
                C4LateDeliveryEnvelope.VERSION,
                receiptId,
                Base64.getEncoder().encodeToString(responseBody),
                evidence).toCanonicalBytes();
    }

    private static C4EvidenceAttestation issuerApproval() {
        return C4EvidenceFixtures.attestation(
                ISSUER, ISSUER_KEY, "ISSUER", "ISSUER_REPLIED", "00",
                "APPROVED", "APPROVED", bodySha256());
    }

    private static C4KeyRegistry relayRegistry(KeyPair issuer, KeyPair network) {
        return registry(
                trusted(ISSUER, ISSUER_KEY, C4PrincipalRole.ISSUER_SIM,
                        issuer.getPublic(), ACQUIRER),
                trusted(NETWORK, NETWORK_KEY, C4PrincipalRole.NETWORK_SIM,
                        network.getPublic(), ACQUIRER));
    }

    private static C4KeyRegistry forwardingRegistry(
            C4KeyRegistry base, String permittedPrincipal, String permittedAcquirer) {
        return new C4KeyRegistry() {
            @Override
            public Optional<C4TrustedPrincipalKey> resolve(
                    String environmentId, String principal, String keyId) {
                return base.resolve(environmentId, principal, keyId);
            }

            @Override
            public boolean diagnosticForwardPermitted(
                    String environmentId, String principal, String acquirerRef) {
                return ENV.equals(environmentId)
                        && permittedPrincipal.equals(principal)
                        && permittedAcquirer.equals(acquirerRef);
            }
        };
    }

    private static JsonNode caseById(String id) throws Exception {
        for (JsonNode testCase : MAPPER.readTree(Files.readString(VECTORS)).get("cases")) {
            if (id.equals(testCase.get("id").asText())) {
                return testCase;
            }
        }
        throw new IllegalArgumentException("unknown carrier case " + id);
    }
}
