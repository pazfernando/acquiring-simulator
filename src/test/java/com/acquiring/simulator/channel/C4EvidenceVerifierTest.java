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
import static com.acquiring.simulator.channel.C4EvidenceFixtures.candidate;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.context;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.direct;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.emptyRegistry;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.keyPair;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.registry;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.relay;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.relayAttestation;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.sign;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.signRelay;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.trusted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.acquiring.simulator.channel.C4CarrierEnvelope.Mode;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Real Ed25519 DIRECT verification plus fail-closed key, role, sender, environment,
 * mandate, hash and RELAY tests. Private keys are ephemeral and never rendered.
 */
class C4EvidenceVerifierTest {

    @Test
    void directIssuerApprovalVerifiesWithRealEd25519() throws Exception {
        KeyPair issuer = keyPair();
        C4EvidenceAttestation attestation = C4EvidenceFixtures.attestation(
                ISSUER, ISSUER_KEY, "ISSUER", "ISSUER_REPLIED", "00",
                "APPROVED", "APPROVED", bodySha256());
        C4CarrierEnvelope envelope = direct(attestation, issuer.getPrivate());
        C4CarrierCandidate candidate = candidate(envelope, body(), ISSUER);

        C4VerifiedAttestation result = C4EvidenceVerifier.verify(
                candidate,
                context(ISSUER),
                registry(trusted(ISSUER, ISSUER_KEY, C4PrincipalRole.ISSUER_SIM,
                        issuer.getPublic(), ACQUIRER)));

        assertThat(result.attestationType())
                .isEqualTo(C4AttestationType.ISSUER_APPROVAL);
        assertThat(result.role()).isEqualTo(C4PrincipalRole.ISSUER_SIM);
        assertThat(result.verificationScope()).isEqualTo(
                C4VerifiedAttestation.VerificationScope
                        .DIRECT_ED25519_AND_TRUST_BINDINGS_VERIFIED);
        assertThat(result.environmentId()).isEqualTo(ENV);
        assertThat(result.evidenceId()).isEqualTo("EVID-001");
        assertThat(result.signingPrincipal()).isEqualTo(ISSUER);
        assertThat(result.keyId()).isEqualTo(ISSUER_KEY);
        assertThat(result.authorizationId()).isEqualTo("AUTH-001");
        assertThat(result.transactionId()).isEqualTo("TX-001");
        assertThat(result.responseBodySha256()).isEqualTo(bodySha256());
        assertThat(result.canonicalAttestationSha256())
                .isEqualTo(attestation.canonicalSha256());
    }

    @Test
    void directIssuerDeclineVerifies() throws Exception {
        KeyPair issuer = keyPair();
        C4VerifiedAttestation result = verifyDirect(
                ISSUER, ISSUER_KEY, C4PrincipalRole.ISSUER_SIM, issuer,
                "ISSUER", "ISSUER_REPLIED", "05", "DECLINED", "DELIBERATE_DECLINE");

        assertThat(result.attestationType())
                .isEqualTo(C4AttestationType.ISSUER_DECLINE);
    }

    @Test
    void directProcessorRejectionVerifies() throws Exception {
        KeyPair processor = keyPair();
        C4VerifiedAttestation result = verifyDirect(
                PROCESSOR, PROCESSOR_KEY, C4PrincipalRole.PROCESSOR_AUTH, processor,
                "PROCESSOR", "BEFORE_NETWORK", "30", "PROCESSOR_REJECTED", "DISALLOWED");

        assertThat(result.attestationType())
                .isEqualTo(C4AttestationType.PROCESSOR_REJECTION_BEFORE_NETWORK);
        assertThat(result.role()).isEqualTo(C4PrincipalRole.PROCESSOR_AUTH);
    }

    @Test
    void directNetworkUncertaintyVerifies() throws Exception {
        KeyPair network = keyPair();
        C4VerifiedAttestation result = verifyDirect(
                NETWORK, NETWORK_KEY, C4PrincipalRole.NETWORK_SIM, network,
                "NETWORK", "POSSIBLE_ISSUER", "91", "NOT_CONFIRMED", "UNAVAILABLE");

        assertThat(result.attestationType())
                .isEqualTo(C4AttestationType.POSSIBLE_ISSUER_UNCERTAINTY);
        assertThat(result.role()).isEqualTo(C4PrincipalRole.NETWORK_SIM);
    }

    @Test
    void relayIssuerApprovalVerifiesBothEd25519Signatures() throws Exception {
        KeyPair issuer = keyPair();
        KeyPair network = keyPair();
        C4EvidenceAttestation attestation = issuerApproval();
        C4CarrierEnvelope envelope = relay(
                attestation, issuer.getPrivate(), network.getPrivate());

        C4VerifiedAttestation result = C4EvidenceVerifier.verify(
                candidate(envelope, body(), NETWORK),
                context(NETWORK),
                relayRegistry(issuer, network));

        assertThat(result.attestationType()).isEqualTo(C4AttestationType.ISSUER_APPROVAL);
        assertThat(result.role()).isEqualTo(C4PrincipalRole.ISSUER_SIM);
        assertThat(result.verificationScope()).isEqualTo(
                C4VerifiedAttestation.VerificationScope
                        .RELAY_ED25519_AND_TRUST_BINDINGS_VERIFIED);
        assertThat(result.signingPrincipal()).isEqualTo(ISSUER);
        assertThat(result.keyId()).isEqualTo(ISSUER_KEY);
        assertThat(result.relayBinding()).isNotNull();
        assertThat(result.relayBinding().principal()).isEqualTo(NETWORK);
        assertThat(result.relayBinding().keyId()).isEqualTo(NETWORK_KEY);
        assertThat(result.relayBinding().receiptId()).isEqualTo("RELAY-001");
        assertThat(result.relayBinding().deliveredAt())
                .isEqualTo("2026-11-18T12:00:02Z");
        assertThat(result.responseBodySha256()).isEqualTo(bodySha256());
        assertThat(result.canonicalAttestationSha256())
                .isEqualTo(attestation.canonicalSha256());
    }

    @Test
    void relayIssuerDeclineVerifies() throws Exception {
        KeyPair issuer = keyPair();
        KeyPair network = keyPair();
        C4EvidenceAttestation attestation = C4EvidenceFixtures.attestation(
                ISSUER, ISSUER_KEY, "ISSUER", "ISSUER_REPLIED", "05",
                "DECLINED", "DELIBERATE_DECLINE", bodySha256());
        C4CarrierEnvelope envelope = relay(
                attestation, issuer.getPrivate(), network.getPrivate());

        C4VerifiedAttestation result = C4EvidenceVerifier.verify(
                candidate(envelope, body(), NETWORK),
                context(NETWORK),
                relayRegistry(issuer, network));

        assertThat(result.attestationType()).isEqualTo(C4AttestationType.ISSUER_DECLINE);
        assertThat(result.verificationScope()).isEqualTo(
                C4VerifiedAttestation.VerificationScope
                        .RELAY_ED25519_AND_TRUST_BINDINGS_VERIFIED);
    }

    @Test
    void relayInnerIssuerSignatureAlterationFailsClosed() throws Exception {
        KeyPair issuer = keyPair();
        KeyPair network = keyPair();
        C4CarrierEnvelope good = relay(
                issuerApproval(), issuer.getPrivate(), network.getPrivate());
        byte[] altered = good.signature();
        altered[0] ^= 0x01;
        C4CarrierEnvelope envelope = new C4CarrierEnvelope(
                C4CarrierEnvelope.VERSION, Mode.RELAY, good.attestation(),
                good.keyId(), altered, good.relay());

        C4VerificationException error = catchThrowableOfType(
                () -> C4EvidenceVerifier.verify(
                        candidate(envelope, body(), NETWORK),
                        context(NETWORK),
                        relayRegistry(issuer, network)),
                C4VerificationException.class);

        assertThat(error.violation()).isEqualTo(C4VerificationViolation.BAD_SIGNATURE);
        assertThat(error.field()).isEqualTo("issuerSignature");
    }

    @Test
    void relayCountersignatureAlterationFailsClosed() throws Exception {
        KeyPair issuer = keyPair();
        KeyPair network = keyPair();
        C4CarrierEnvelope good = relay(
                issuerApproval(), issuer.getPrivate(), network.getPrivate());
        byte[] altered = good.relay().signature();
        altered[0] ^= 0x01;
        C4RelayAttestation relay = new C4RelayAttestation(
                good.relay().principal(), good.relay().receiptId(),
                good.relay().deliveredAt(), good.relay().keyId(),
                good.relay().responseSha256(), good.relay().attestationSha256(), altered);
        C4CarrierEnvelope envelope = new C4CarrierEnvelope(
                C4CarrierEnvelope.VERSION, Mode.RELAY, good.attestation(),
                good.keyId(), good.signature(), relay);

        C4VerificationException error = catchThrowableOfType(
                () -> C4EvidenceVerifier.verify(
                        candidate(envelope, body(), NETWORK),
                        context(NETWORK),
                        relayRegistry(issuer, network)),
                C4VerificationException.class);

        assertThat(error.violation()).isEqualTo(C4VerificationViolation.BAD_SIGNATURE);
        assertThat(error.field()).isEqualTo("relaySignature");
    }

    @Test
    void relayCannotSubstituteNetworkSignatureForIssuerSignature() throws Exception {
        KeyPair issuer = keyPair();
        KeyPair network = keyPair();
        C4CarrierEnvelope envelope = relay(
                issuerApproval(), network.getPrivate(), network.getPrivate());

        C4VerificationException error = catchThrowableOfType(
                () -> C4EvidenceVerifier.verify(
                        candidate(envelope, body(), NETWORK),
                        context(NETWORK),
                        relayRegistry(issuer, network)),
                C4VerificationException.class);

        assertThat(error.violation()).isEqualTo(C4VerificationViolation.BAD_SIGNATURE);
        assertThat(error.field()).isEqualTo("issuerSignature");
    }

    @Test
    void relayInnerSignerMustHoldIssuerRole() throws Exception {
        KeyPair issuer = keyPair();
        KeyPair network = keyPair();
        C4CarrierEnvelope envelope = relay(
                issuerApproval(), network.getPrivate(), network.getPrivate());
        C4KeyRegistry registry = registry(
                trusted(ISSUER, ISSUER_KEY, C4PrincipalRole.NETWORK_SIM,
                        network.getPublic(), ACQUIRER),
                trusted(NETWORK, NETWORK_KEY, C4PrincipalRole.NETWORK_SIM,
                        network.getPublic(), ACQUIRER));

        C4VerificationException error = catchThrowableOfType(
                () -> C4EvidenceVerifier.verify(
                        candidate(envelope, body(), NETWORK), context(NETWORK), registry),
                C4VerificationException.class);

        assertThat(error.violation())
                .isEqualTo(C4VerificationViolation.ROLE_NOT_PERMITTED);
        assertThat(error.field()).isEqualTo("issuerRole");
    }

    @Test
    void relaySignerMustHoldNetworkRole() throws Exception {
        KeyPair issuer = keyPair();
        KeyPair network = keyPair();
        C4CarrierEnvelope envelope = relay(
                issuerApproval(), issuer.getPrivate(), network.getPrivate());
        C4KeyRegistry registry = registry(
                trusted(ISSUER, ISSUER_KEY, C4PrincipalRole.ISSUER_SIM,
                        issuer.getPublic(), ACQUIRER),
                trusted(NETWORK, NETWORK_KEY, C4PrincipalRole.ISSUER_SIM,
                        network.getPublic(), ACQUIRER));

        C4VerificationException error = catchThrowableOfType(
                () -> C4EvidenceVerifier.verify(
                        candidate(envelope, body(), NETWORK), context(NETWORK), registry),
                C4VerificationException.class);

        assertThat(error.violation())
                .isEqualTo(C4VerificationViolation.ROLE_NOT_PERMITTED);
        assertThat(error.field()).isEqualTo("relayRole");
    }

    @Test
    void relayResponseHashMismatchFailsClosed() throws Exception {
        KeyPair issuer = keyPair();
        KeyPair network = keyPair();
        C4EvidenceAttestation attestation = issuerApproval();
        C4RelayAttestation relay = signedRelay(
                "RELAY-001", "2026-11-18T12:00:02Z",
                "0".repeat(64), attestation.canonicalSha256(), network);
        C4CarrierEnvelope envelope = relayEnvelope(
                attestation, sign(attestation, issuer.getPrivate()), relay);
        C4CarrierCandidate candidate = relayCandidate(
                envelope, bodySha256(), attestation.canonicalSha256());

        C4VerificationException error = catchThrowableOfType(
                () -> C4EvidenceVerifier.verify(
                        candidate, context(NETWORK), relayRegistry(issuer, network)),
                C4VerificationException.class);

        assertThat(error.violation()).isEqualTo(C4VerificationViolation.RELAY_BINDING);
        assertThat(error.field()).isEqualTo("relayResponseSha256");
    }

    @Test
    void relayAttestationHashMismatchFailsClosed() throws Exception {
        KeyPair issuer = keyPair();
        KeyPair network = keyPair();
        C4EvidenceAttestation attestation = issuerApproval();
        C4RelayAttestation relay = signedRelay(
                "RELAY-001", "2026-11-18T12:00:02Z",
                bodySha256(), "0".repeat(64), network);
        C4CarrierEnvelope envelope = relayEnvelope(
                attestation, sign(attestation, issuer.getPrivate()), relay);
        C4CarrierCandidate candidate = relayCandidate(
                envelope, bodySha256(), attestation.canonicalSha256());

        C4VerificationException error = catchThrowableOfType(
                () -> C4EvidenceVerifier.verify(
                        candidate, context(NETWORK), relayRegistry(issuer, network)),
                C4VerificationException.class);

        assertThat(error.violation()).isEqualTo(C4VerificationViolation.RELAY_BINDING);
        assertThat(error.field()).isEqualTo("relayAttestationSha256");
    }

    @Test
    void relayWrongAuthenticatedSenderFailsClosed() throws Exception {
        KeyPair issuer = keyPair();
        KeyPair network = keyPair();
        C4CarrierEnvelope envelope = relay(
                issuerApproval(), issuer.getPrivate(), network.getPrivate());

        C4VerificationException error = catchThrowableOfType(
                () -> C4EvidenceVerifier.verify(
                        candidate(envelope, body(), NETWORK),
                        context(ISSUER),
                        relayRegistry(issuer, network)),
                C4VerificationException.class);

        assertThat(error.violation())
                .isEqualTo(C4VerificationViolation.AUTHENTICATED_SENDER);
        assertThat(error.field()).isEqualTo("relayPrincipal");
    }

    @Test
    void relayUnknownRelayKeyFailsClosed() throws Exception {
        KeyPair issuer = keyPair();
        KeyPair network = keyPair();
        C4CarrierEnvelope envelope = relay(
                issuerApproval(), issuer.getPrivate(), network.getPrivate());
        C4KeyRegistry onlyIssuer = registry(trusted(
                ISSUER, ISSUER_KEY, C4PrincipalRole.ISSUER_SIM,
                issuer.getPublic(), ACQUIRER));

        C4VerificationException error = catchThrowableOfType(
                () -> C4EvidenceVerifier.verify(
                        candidate(envelope, body(), NETWORK),
                        context(NETWORK),
                        onlyIssuer),
                C4VerificationException.class);

        assertThat(error.violation()).isEqualTo(C4VerificationViolation.UNKNOWN_KEY);
        assertThat(error.field()).isEqualTo("relayKeyId");
    }

    @Test
    void relayRegistryKeyBindingMismatchFailsClosed() throws Exception {
        KeyPair issuer = keyPair();
        KeyPair network = keyPair();
        C4CarrierEnvelope envelope = relay(
                issuerApproval(), issuer.getPrivate(), network.getPrivate());
        C4KeyRegistry wrongRegistry = (environmentId, principal, keyId) -> {
            if (NETWORK.equals(principal)) {
                return Optional.of(new C4TrustedPrincipalKey(
                        ENV, NETWORK, "OTHER-KEY", C4PrincipalRole.NETWORK_SIM,
                        Set.of(ACQUIRER), network.getPublic()));
            }
            return Optional.of(trusted(
                    ISSUER, ISSUER_KEY, C4PrincipalRole.ISSUER_SIM,
                    issuer.getPublic(), ACQUIRER));
        };

        C4VerificationException error = catchThrowableOfType(
                () -> C4EvidenceVerifier.verify(
                        candidate(envelope, body(), NETWORK),
                        context(NETWORK),
                        wrongRegistry),
                C4VerificationException.class);

        assertThat(error.violation()).isEqualTo(C4VerificationViolation.KEY_BINDING);
        assertThat(error.field()).isEqualTo("relayKeyId");
    }

    @Test
    void relayWrongAuthenticatedEnvironmentFailsClosed() throws Exception {
        KeyPair issuer = keyPair();
        KeyPair network = keyPair();
        C4CarrierEnvelope envelope = relay(
                issuerApproval(), issuer.getPrivate(), network.getPrivate());

        C4VerificationException error = catchThrowableOfType(
                () -> C4EvidenceVerifier.verify(
                        candidate(envelope, body(), NETWORK),
                        new C4AuthenticatedTransportContext("OTHER-ENV", NETWORK),
                        relayRegistry(issuer, network)),
                C4VerificationException.class);

        assertThat(error.violation())
                .isEqualTo(C4VerificationViolation.ENVIRONMENT_BINDING);
        assertThat(error.field()).isEqualTo("environmentId");
    }

    @Test
    void relayAcquirerMandateMismatchFailsClosed() throws Exception {
        KeyPair issuer = keyPair();
        KeyPair network = keyPair();
        C4CarrierEnvelope envelope = relay(
                issuerApproval(), issuer.getPrivate(), network.getPrivate());
        C4KeyRegistry registry = registry(
                trusted(ISSUER, ISSUER_KEY, C4PrincipalRole.ISSUER_SIM,
                        issuer.getPublic(), ACQUIRER),
                trusted(NETWORK, NETWORK_KEY, C4PrincipalRole.NETWORK_SIM,
                        network.getPublic(), "ACQ-B"));

        C4VerificationException error = catchThrowableOfType(
                () -> C4EvidenceVerifier.verify(
                        candidate(envelope, body(), NETWORK), context(NETWORK), registry),
                C4VerificationException.class);

        assertThat(error.violation()).isEqualTo(C4VerificationViolation.MANDATE_BINDING);
        assertThat(error.field()).isEqualTo("relayMandate");
    }

    @Test
    void relaySignedObjectExcludesSignatureAndBindsReceiptTimeAndHashes() {
        C4EvidenceAttestation attestation = issuerApproval();
        C4RelayAttestation base = relayAttestation(attestation, signatureOf((byte) 0));
        C4RelayAttestation otherSignature = new C4RelayAttestation(
                base.principal(), base.receiptId(), base.deliveredAt(), base.keyId(),
                base.responseSha256(), base.attestationSha256(), signatureOf((byte) 1));

        assertThat(otherSignature.signedBytes()).isEqualTo(base.signedBytes());
        assertThat(changedRelay(base, "RELAY-002", base.deliveredAt(),
                base.responseSha256(), base.attestationSha256()).signedBytes())
                .isNotEqualTo(base.signedBytes());
        assertThat(changedRelay(base, base.receiptId(), "2026-11-18T12:00:03Z",
                base.responseSha256(), base.attestationSha256()).signedBytes())
                .isNotEqualTo(base.signedBytes());
        assertThat(changedRelay(base, base.receiptId(), base.deliveredAt(),
                "0".repeat(64), base.attestationSha256()).signedBytes())
                .isNotEqualTo(base.signedBytes());
        assertThat(changedRelay(base, base.receiptId(), base.deliveredAt(),
                base.responseSha256(), "0".repeat(64)).signedBytes())
                .isNotEqualTo(base.signedBytes());
    }

    @Test
    void relayVerificationLeavesInnerAttestationAndBothSignaturesUnchanged()
            throws Exception {
        KeyPair issuer = keyPair();
        KeyPair network = keyPair();
        C4EvidenceAttestation attestation = issuerApproval();
        C4CarrierEnvelope envelope = relay(
                attestation, issuer.getPrivate(), network.getPrivate());
        byte[] canonicalBefore = attestation.toCanonicalBytes();
        byte[] envelopeBefore = envelope.toCanonicalBytes();
        byte[] innerSignature = envelope.signature();
        byte[] relaySignature = envelope.relay().signature();

        C4VerifiedAttestation result = C4EvidenceVerifier.verify(
                candidate(envelope, body(), NETWORK),
                context(NETWORK),
                relayRegistry(issuer, network));

        assertThat(attestation.toCanonicalBytes()).isEqualTo(canonicalBefore);
        assertThat(envelope.toCanonicalBytes()).isEqualTo(envelopeBefore);
        assertThat(envelope.signature()).isEqualTo(innerSignature);
        assertThat(envelope.relay().signature()).isEqualTo(relaySignature);
        assertThat(result.signingPrincipal()).isEqualTo(ISSUER);
        assertThat(result.relayBinding().principal()).isEqualTo(NETWORK);
        assertThat(result.toString())
                .contains("RELAY_ED25519_AND_TRUST_BINDINGS_VERIFIED")
                .doesNotContain(Base64.getEncoder().encodeToString(innerSignature))
                .doesNotContain(Base64.getEncoder().encodeToString(relaySignature));
    }

    @Test
    void unknownKeyFailsClosed() throws Exception {
        KeyPair issuer = keyPair();
        C4CarrierCandidate candidate = directCandidate(issuer);

        C4VerificationException error = catchThrowableOfType(
                () -> C4EvidenceVerifier.verify(
                        candidate, context(ISSUER), emptyRegistry()),
                C4VerificationException.class);

        assertThat(error.violation()).isEqualTo(C4VerificationViolation.UNKNOWN_KEY);
        assertThat(error.field()).isEqualTo("keyId");
    }

    @Test
    void signatureFromDifferentPrivateKeyFailsClosed() throws Exception {
        KeyPair registered = keyPair();
        KeyPair signer = keyPair();
        C4EvidenceAttestation attestation = C4EvidenceFixtures.attestation(
                ISSUER, ISSUER_KEY, "ISSUER", "ISSUER_REPLIED", "00",
                "APPROVED", "APPROVED", bodySha256());
        C4CarrierEnvelope envelope = direct(attestation, signer.getPrivate());
        C4CarrierCandidate candidate = candidate(envelope, body(), ISSUER);

        C4VerificationException error = catchThrowableOfType(
                () -> C4EvidenceVerifier.verify(
                        candidate,
                        context(ISSUER),
                        registry(trusted(ISSUER, ISSUER_KEY, C4PrincipalRole.ISSUER_SIM,
                                registered.getPublic(), ACQUIRER))),
                C4VerificationException.class);

        assertThat(error.violation()).isEqualTo(C4VerificationViolation.BAD_SIGNATURE);
        assertThat(error.field()).isEqualTo("signature");
    }

    @Test
    void tamperedCanonicalFieldFailsClosed() throws Exception {
        KeyPair issuer = keyPair();
        C4EvidenceAttestation original = C4EvidenceFixtures.attestation(
                ISSUER, ISSUER_KEY, "ISSUER", "ISSUER_REPLIED", "00",
                "APPROVED", "APPROVED", bodySha256());
        byte[] originalSignature = sign(original, issuer.getPrivate());
        C4EvidenceAttestation tampered = C4EvidenceFixtures.attestationWithBounds(
                "RRN123456789", "AUTH-001", "TX-001", "ATT-001", null, 2000,
                bodySha256());
        C4CarrierEnvelope envelope = new C4CarrierEnvelope(
                C4CarrierEnvelope.VERSION, Mode.DIRECT, tampered, tampered.keyId(),
                originalSignature, null);
        C4CarrierCandidate candidate = candidate(envelope, body(), ISSUER);

        C4VerificationException error = catchThrowableOfType(
                () -> C4EvidenceVerifier.verify(
                        candidate,
                        context(ISSUER),
                        registry(trusted(ISSUER, ISSUER_KEY, C4PrincipalRole.ISSUER_SIM,
                                issuer.getPublic(), ACQUIRER))),
                C4VerificationException.class);

        assertThat(error.violation()).isEqualTo(C4VerificationViolation.BAD_SIGNATURE);
    }

    @Test
    void outerKeyIdTamperFailsClosedAtEnvelopeConstruction() throws Exception {
        KeyPair issuer = keyPair();
        C4EvidenceAttestation attestation = C4EvidenceFixtures.attestation(
                ISSUER, ISSUER_KEY, "ISSUER", "ISSUER_REPLIED", "00",
                "APPROVED", "APPROVED", bodySha256());
        byte[] signature = sign(attestation, issuer.getPrivate());

        C4CarrierException error = catchThrowableOfType(
                () -> new C4CarrierEnvelope(
                        C4CarrierEnvelope.VERSION, Mode.DIRECT, attestation,
                        "OTHER-KEY", signature, null),
                C4CarrierException.class);

        assertThat(error.violation()).isEqualTo(C4CarrierViolation.KEY_BINDING);
    }

    @Test
    void registryReturningDifferentKeyIdFailsClosed() throws Exception {
        KeyPair issuer = keyPair();
        C4CarrierCandidate candidate = directCandidate(issuer);
        C4KeyRegistry wrongRegistry = (environmentId, principal, keyId) ->
                Optional.of(new C4TrustedPrincipalKey(
                        ENV, ISSUER, "OTHER-KEY", C4PrincipalRole.ISSUER_SIM,
                        Set.of(ACQUIRER), issuer.getPublic()));

        C4VerificationException error = catchThrowableOfType(
                () -> C4EvidenceVerifier.verify(
                        candidate, context(ISSUER), wrongRegistry),
                C4VerificationException.class);

        assertThat(error.violation()).isEqualTo(C4VerificationViolation.KEY_BINDING);
    }

    @Test
    void networkRoleCannotSignIssuerDecision() throws Exception {
        KeyPair issuer = keyPair();
        C4CarrierCandidate candidate = directCandidate(issuer);

        C4VerificationException error = catchThrowableOfType(
                () -> C4EvidenceVerifier.verify(
                        candidate,
                        context(ISSUER),
                        registry(trusted(ISSUER, ISSUER_KEY, C4PrincipalRole.NETWORK_SIM,
                                issuer.getPublic(), ACQUIRER))),
                C4VerificationException.class);

        assertThat(error.violation())
                .isEqualTo(C4VerificationViolation.ROLE_NOT_PERMITTED);
        assertThat(error.field()).isEqualTo("role");
    }

    @Test
    void aclRoleHasNoEvidenceSigningAuthority() throws Exception {
        KeyPair issuer = keyPair();
        C4CarrierCandidate candidate = directCandidate(issuer);

        C4VerificationException error = catchThrowableOfType(
                () -> C4EvidenceVerifier.verify(
                        candidate,
                        context(ISSUER),
                        registry(trusted(ISSUER, ISSUER_KEY, C4PrincipalRole.ACL_AUTH,
                                issuer.getPublic(), ACQUIRER))),
                C4VerificationException.class);

        assertThat(error.violation())
                .isEqualTo(C4VerificationViolation.ROLE_NOT_PERMITTED);
    }

    @Test
    void wrongAuthenticatedSenderFailsClosed() throws Exception {
        KeyPair issuer = keyPair();
        C4CarrierCandidate candidate = directCandidate(issuer);

        C4VerificationException error = catchThrowableOfType(
                () -> C4EvidenceVerifier.verify(
                        candidate,
                        context(NETWORK),
                        registry(trusted(ISSUER, ISSUER_KEY, C4PrincipalRole.ISSUER_SIM,
                                issuer.getPublic(), ACQUIRER))),
                C4VerificationException.class);

        assertThat(error.violation())
                .isEqualTo(C4VerificationViolation.AUTHENTICATED_SENDER);
        assertThat(error.field()).isEqualTo("signingPrincipal");
    }

    @Test
    void wrongAuthenticatedEnvironmentFailsClosed() throws Exception {
        KeyPair issuer = keyPair();
        C4CarrierCandidate candidate = directCandidate(issuer);

        C4VerificationException error = catchThrowableOfType(
                () -> C4EvidenceVerifier.verify(
                        candidate,
                        new C4AuthenticatedTransportContext("OTHER-ENV", ISSUER),
                        registry(trusted(ISSUER, ISSUER_KEY, C4PrincipalRole.ISSUER_SIM,
                                issuer.getPublic(), ACQUIRER))),
                C4VerificationException.class);

        assertThat(error.violation())
                .isEqualTo(C4VerificationViolation.ENVIRONMENT_BINDING);
        assertThat(error.field()).isEqualTo("environmentId");
    }

    @Test
    void acquirerMandateMismatchFailsClosed() throws Exception {
        KeyPair issuer = keyPair();
        C4CarrierCandidate candidate = directCandidate(issuer);

        C4VerificationException error = catchThrowableOfType(
                () -> C4EvidenceVerifier.verify(
                        candidate,
                        context(ISSUER),
                        registry(trusted(ISSUER, ISSUER_KEY, C4PrincipalRole.ISSUER_SIM,
                                issuer.getPublic(), "ACQ-B"))),
                C4VerificationException.class);

        assertThat(error.violation())
                .isEqualTo(C4VerificationViolation.MANDATE_BINDING);
        assertThat(error.field()).isEqualTo("acquirerRef");
    }

    @Test
    void candidateBodyHashTamperFailsClosed() throws Exception {
        KeyPair issuer = keyPair();
        C4CarrierCandidate good = directCandidate(issuer);
        C4CarrierCandidate tampered = new C4CarrierCandidate(
                good.envelope(),
                "0".repeat(64),
                good.canonicalAttestationSha256(),
                good.senderBinding(),
                good.attestationState(),
                good.keyResolution());

        C4VerificationException error = catchThrowableOfType(
                () -> C4EvidenceVerifier.verify(
                        tampered,
                        context(ISSUER),
                        registry(trusted(ISSUER, ISSUER_KEY, C4PrincipalRole.ISSUER_SIM,
                                issuer.getPublic(), ACQUIRER))),
                C4VerificationException.class);

        assertThat(error.violation()).isEqualTo(C4VerificationViolation.HASH_BINDING);
    }

    @Test
    void candidateCanonicalAttestationHashTamperFailsClosed() throws Exception {
        KeyPair issuer = keyPair();
        C4CarrierCandidate good = directCandidate(issuer);
        C4CarrierCandidate tampered = new C4CarrierCandidate(
                good.envelope(),
                good.responseBodySha256(),
                "0".repeat(64),
                good.senderBinding(),
                good.attestationState(),
                good.keyResolution());

        C4VerificationException error = catchThrowableOfType(
                () -> C4EvidenceVerifier.verify(
                        tampered,
                        context(ISSUER),
                        registry(trusted(ISSUER, ISSUER_KEY, C4PrincipalRole.ISSUER_SIM,
                                issuer.getPublic(), ACQUIRER))),
                C4VerificationException.class);

        assertThat(error.violation())
                .isEqualTo(C4VerificationViolation.ATTESTATION_HASH_BINDING);
    }

    @Test
    void verifiedResultIsNotPaymentOrReceiptAndDoesNotRenderMaterial() throws Exception {
        assertThat(Arrays.stream(C4VerifiedAttestation.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .doesNotContain("isValid", "isVerified", "isTrusted", "valid", "verified",
                        "trusted", "isPaymentConfirmed", "isProcessorReceipt",
                        "paymentConfirmed", "processorReceipt");

        KeyPair issuer = keyPair();
        C4EvidenceAttestation attestation = C4EvidenceFixtures.attestation(
                ISSUER, ISSUER_KEY, "ISSUER", "ISSUER_REPLIED", "00",
                "APPROVED", "APPROVED", bodySha256());
        C4CarrierEnvelope envelope = direct(attestation, issuer.getPrivate());
        C4VerifiedAttestation result = C4EvidenceVerifier.verify(
                candidate(envelope, body(), ISSUER),
                context(ISSUER),
                registry(trusted(ISSUER, ISSUER_KEY, C4PrincipalRole.ISSUER_SIM,
                        issuer.getPublic(), ACQUIRER)));

        String rendered = result.toString();
        assertThat(rendered)
                .doesNotContain(Base64.getEncoder().encodeToString(envelope.signature()))
                .doesNotContain(new String(body(), java.nio.charset.StandardCharsets.US_ASCII))
                .doesNotContain(issuer.getPublic().getEncoded().toString());
        assertThat(rendered).contains("DIRECT_ED25519_AND_TRUST_BINDINGS_VERIFIED");
        assertThat(result.relayBinding()).isNull();
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

    private static C4RelayAttestation signedRelay(
            String receiptId,
            String deliveredAt,
            String responseSha256,
            String attestationSha256,
            KeyPair network) throws Exception {
        C4RelayAttestation unsigned = new C4RelayAttestation(
                C4RelayAttestation.PRINCIPAL, receiptId, deliveredAt, NETWORK_KEY,
                responseSha256, attestationSha256, signatureOf((byte) 0));
        return new C4RelayAttestation(
                unsigned.principal(), unsigned.receiptId(), unsigned.deliveredAt(),
                unsigned.keyId(), unsigned.responseSha256(), unsigned.attestationSha256(),
                signRelay(unsigned, network.getPrivate()));
    }

    private static C4CarrierEnvelope relayEnvelope(
            C4EvidenceAttestation attestation, byte[] issuerSignature,
            C4RelayAttestation relay) {
        return new C4CarrierEnvelope(
                C4CarrierEnvelope.VERSION, Mode.RELAY, attestation,
                attestation.keyId(), issuerSignature, relay);
    }

    private static C4CarrierCandidate relayCandidate(
            C4CarrierEnvelope envelope, String bodySha256, String attestationSha256) {
        return new C4CarrierCandidate(
                envelope,
                bodySha256,
                attestationSha256,
                C4CarrierCandidate.SenderBinding.RELAY_SENDER_MATCHED,
                C4CarrierCandidate.AttestationState.UNVERIFIED_CANDIDATE,
                C4CarrierCandidate.KeyResolution.UNRESOLVED);
    }

    private static byte[] signatureOf(byte value) {
        byte[] signature = new byte[C4CarrierParser.SIGNATURE_BYTES];
        Arrays.fill(signature, value);
        return signature;
    }

    private static C4RelayAttestation changedRelay(
            C4RelayAttestation base,
            String receiptId,
            String deliveredAt,
            String responseSha256,
            String attestationSha256) {
        return new C4RelayAttestation(
                base.principal(), receiptId, deliveredAt, base.keyId(),
                responseSha256, attestationSha256, base.signature());
    }

    private static C4CarrierCandidate directCandidate(KeyPair issuer) throws Exception {
        C4EvidenceAttestation attestation = C4EvidenceFixtures.attestation(
                ISSUER, ISSUER_KEY, "ISSUER", "ISSUER_REPLIED", "00",
                "APPROVED", "APPROVED", bodySha256());
        return candidate(direct(attestation, issuer.getPrivate()), body(), ISSUER);
    }

    private static C4VerifiedAttestation verifyDirect(
            String principal,
            String keyId,
            C4PrincipalRole role,
            KeyPair keyPair,
            String actor,
            String stage,
            String de39,
            String result,
            String reason) throws Exception {
        C4EvidenceAttestation attestation = C4EvidenceFixtures.attestation(
                principal, keyId, actor, stage, de39, result, reason, bodySha256());
        return C4EvidenceVerifier.verify(
                candidate(direct(attestation, keyPair.getPrivate()), body(), principal),
                context(principal),
                registry(trusted(principal, keyId, role, keyPair.getPublic(), ACQUIRER)));
    }
}
