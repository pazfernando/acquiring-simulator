package com.acquiring.simulator.presenter;

import com.acquiring.simulator.adapter.aws.DynamoAuthorizationRepository;
import com.acquiring.simulator.adapter.aws.DynamoEventStore;
import com.acquiring.simulator.channel.C4AuthenticatedTransportContext;
import com.acquiring.simulator.channel.C4CarrierCandidate;
import com.acquiring.simulator.channel.C4CarrierEnvelope;
import com.acquiring.simulator.channel.C4CarrierException;
import com.acquiring.simulator.channel.C4CarrierParser;
import com.acquiring.simulator.channel.C4EvidenceAttestation;
import com.acquiring.simulator.channel.C4KeyRegistry;
import com.acquiring.simulator.channel.C4PrincipalRole;
import com.acquiring.simulator.channel.C4TransportResponse;
import com.acquiring.simulator.channel.C4TrustedPrincipalKey;
import com.acquiring.simulator.channel.SyntheticInstructionMaterializer;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.DownstreamReply;
import com.acquiring.simulator.domain.model.IssuerDecision;
import com.acquiring.simulator.domain.model.SimulatedDecisionRequest;
import com.acquiring.simulator.domain.port.IssuerSimulator;
import com.acquiring.simulator.domain.port.OriginalInstructionReader;
import com.acquiring.simulator.protocol.iso8583.Iso8583De48Request;
import com.acquiring.simulator.protocol.iso8583.Iso8583De48RequestParser;
import com.acquiring.simulator.protocol.iso8583.Iso8583De48ResponseParser;
import com.acquiring.simulator.protocol.iso8583.Iso8583Message;
import com.acquiring.simulator.protocol.iso8583.Iso8583Packer;
import com.acquiring.simulator.protocol.iso8583.Iso8583Scanner;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local-demo simulated bank for the presenter relay.
 *
 * <p>This is the non-test port of the verified T3 composition: it receives no pre-seeded
 * instruction, reads the exact {@code 0100} that the atomic dispatch-intent adapter already
 * committed, independently re-materializes it, echoes it into one correlated {@code 0110} and signs
 * a P5 attestation with an ephemeral in-memory Ed25519 key. It never approves without a durable
 * original, never answers with an unsigned result and never writes a receipt itself — the real
 * {@code SignedEvidenceReceiptGateway} (through {@link LocalSignedResultGateway}) verifies and
 * commits.</p>
 *
 * <p>This is a local simulation fixture, not a real scheme, issuer or key-custody implementation.
 * The private key exists only in relay memory, is never persisted or logged, and dies with the
 * process.</p>
 */
public final class LocalSignedIssuerSimulator implements IssuerSimulator {

    private static final DateTimeFormatter AT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final OriginalInstructionReader originals;
    private final DynamoAuthorizationRepository repository;
    private final String environmentId;
    private final String issuerPrincipal;
    private final String keyId;
    private final String evidenceIdPrefix;
    private final KeyPair key;
    private final AtomicInteger sends = new AtomicInteger();

    private volatile Chain chain;
    private volatile String lastFailure;

    public LocalSignedIssuerSimulator(OriginalInstructionReader originals,
            DynamoAuthorizationRepository repository, String environmentId,
            String issuerPrincipal, String keyId, String evidenceIdPrefix) {
        this.originals = originals;
        this.repository = repository;
        this.environmentId = environmentId;
        this.issuerPrincipal = issuerPrincipal;
        this.keyId = keyId;
        this.evidenceIdPrefix = evidenceIdPrefix;
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
            this.key = generator.generateKeyPair();
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Ed25519 is not available in this runtime", failure);
        }
    }

    public String issuerPrincipal() {
        return issuerPrincipal;
    }

    public String environmentId() {
        return environmentId;
    }

    public int sends() {
        return sends.get();
    }

    /** Last correlation of the signed chain, or {@code null} before the first decision. */
    public Chain chain() {
        return chain;
    }

    public String lastFailure() {
        return lastFailure;
    }

    @Override
    public DownstreamReply decide(SimulatedDecisionRequest request) {
        sends.incrementAndGet();
        try {
            // The financial instruction must already be durable and readable when the bank is
            // invoked. A missing original is a failure, never an unsigned or seeded fallback.
            byte[] persisted = originals.readPersistedOriginal0100(request.authorizationId())
                    .orElseThrow(() -> new IllegalStateException(
                            "persisted original is absent when the local bank is invoked"));
            AuthorizationRecord intent = repository.findById(request.authorizationId())
                    .orElseThrow(() -> new IllegalStateException("durable intent is absent"));
            byte[] rematerialized = new SyntheticInstructionMaterializer().materialize(
                    intent, request.correlationId(), request.dispatchedAt());
            if (!Arrays.equals(persisted, rematerialized)) {
                throw new IllegalStateException(
                        "persisted original differs from the independent materialization");
            }
            if (!request.correlationId().equals(intent.dispatchCorrelationId())) {
                throw new IllegalStateException(
                        "bank correlation differs from the durable intent");
            }

            Iso8583Message original =
                    Iso8583Scanner.scan(new String(persisted, StandardCharsets.US_ASCII));
            Iso8583De48Request extension =
                    Iso8583De48RequestParser.parse(original.field(48));

            // One receipt identity per operation: the same evidence id must never describe two
            // different payments.
            String evidenceId = evidenceIdPrefix + "-" + request.authorizationId();
            Instant availableAt = secondPrecision(request.dispatchedAt());
            byte[] response = approvalResponse(original, extension, availableAt, evidenceId);
            C4EvidenceAttestation signed = attestation(original, extension, response,
                    availableAt, persisted, evidenceId);
            C4CarrierEnvelope envelope = direct(signed);
            C4AuthenticatedTransportContext context =
                    new C4AuthenticatedTransportContext(environmentId, issuerPrincipal);
            C4CarrierCandidate candidate = candidate(envelope, response);
            C4KeyRegistry registry = registry(extension.value("acq"));

            chain = new Chain(signed, candidate, context, registry, response);
            lastFailure = null;
            return new DownstreamReply(request.correlationId(), IssuerDecision.APPROVED,
                    availableAt);
        } catch (RuntimeException failure) {
            lastFailure = failure.getMessage();
            throw failure;
        } catch (Exception failure) {
            lastFailure = failure.getMessage();
            throw new IllegalStateException("local bank could not return an approval", failure);
        }
    }

    /**
     * Canonical whole-second availability instant for the signed evidence.
     *
     * <p>P5/P6 bind the signed {@code availableAt} to the second-precision {@code at} projection
     * and refuse a sub-second value instead of silently truncating it. The simulated bank must
     * therefore sign exactly the instant it writes on the wire. The local demo composition
     * advances its clock on whole seconds, so the dispatch instant is already canonical; a
     * finer-grained composition fails here instead of emitting evidence the verifier must
     * reject.</p>
     */
    private static Instant secondPrecision(Instant dispatchedAt) {
        Instant truncated = dispatchedAt.truncatedTo(ChronoUnit.SECONDS);
        if (truncated.isBefore(dispatchedAt)) {
            throw new IllegalStateException(
                    "the local demo clock must advance on whole seconds: P5 availability is "
                            + "second-granular and cannot predate the dispatch it answers");
        }
        return truncated;
    }

    private byte[] approvalResponse(Iso8583Message original, Iso8583De48Request extension,
            Instant availableAt, String evidenceId) {
        Map<Integer, String> fields = new LinkedHashMap<>();
        for (int dataElement : List.of(3, 4, 7, 11, 22, 25, 32, 37, 41, 42, 49)) {
            fields.put(dataElement, original.field(dataElement));
        }
        fields.put(39, "00");
        fields.put(48, responseExtension(extension, availableAt, evidenceId));
        return Iso8583Packer.pack("0110", fields).getBytes(StandardCharsets.US_ASCII);
    }

    private String responseExtension(Iso8583De48Request extension, Instant availableAt,
            String evidenceId) {
        StringBuilder de48 = new StringBuilder(256);
        for (String key : Iso8583De48ResponseParser.REQUEST_ECHO_KEYS) {
            append(de48, key, extension.value(key));
        }
        if (extension.has(Iso8583De48ResponseParser.PRIOR_KEY)) {
            append(de48, Iso8583De48ResponseParser.PRIOR_KEY,
                    extension.value(Iso8583De48ResponseParser.PRIOR_KEY));
        }
        append(de48, "actor", "ISSUER");
        append(de48, "stage", "ISSUER_REPLIED");
        append(de48, "result", "APPROVED");
        append(de48, "reason", "APPROVED");
        append(de48, "evidence", evidenceId);
        append(de48, "at", AT.format(availableAt));
        return de48.toString();
    }

    private static void append(StringBuilder de48, String key, String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("missing approved DE48 echo value for " + key);
        }
        if (!de48.isEmpty()) {
            de48.append(';');
        }
        de48.append(key).append('=').append(value);
    }

    private C4EvidenceAttestation attestation(Iso8583Message original,
            Iso8583De48Request extension, byte[] response, Instant availableAt, byte[] originalBytes,
            String evidenceId) {
        return new C4EvidenceAttestation(
                1,
                environmentId,
                issuerPrincipal,
                evidenceId,
                sha256Hex(originalBytes),
                "SIM-ISO87A-v1",
                extension.value("aid"),
                extension.value("tid"),
                extension.value("cid"),
                extension.value("att"),
                extension.value("acq"),
                extension.value("mid"),
                extension.value("term"),
                extension.value("pay"),
                Integer.parseInt(extension.value("gen")),
                Integer.parseInt(original.field(4)),
                original.field(49),
                original.field(11),
                original.field(37),
                original.field(7),
                extension.value("sent"),
                "ISSUER",
                "ISSUER_REPLIED",
                "00",
                "APPROVED",
                "APPROVED",
                null,
                DateTimeFormatter.ISO_INSTANT.format(availableAt),
                issuerPrincipal,
                keyId,
                sha256Hex(response));
    }

    private C4CarrierEnvelope direct(C4EvidenceAttestation attestation) throws GeneralSecurityException {
        return new C4CarrierEnvelope(
                C4CarrierEnvelope.VERSION,
                C4CarrierEnvelope.Mode.DIRECT,
                attestation,
                attestation.keyId(),
                sign(attestation),
                null);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private byte[] sign(C4EvidenceAttestation attestation) throws GeneralSecurityException {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(key.getPrivate());
        signer.update(attestation.toCanonicalBytes());
        return signer.sign();
    }

    private C4CarrierCandidate candidate(C4CarrierEnvelope envelope, byte[] body) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", List.of("application/octet-stream"));
        headers.put(C4CarrierParser.HEADER_NAME, List.of(
                Base64.getEncoder().encodeToString(envelope.toCanonicalBytes())));
        int total = headers.entrySet().stream()
                .mapToInt(entry -> entry.getKey().length() + 2
                        + entry.getValue().get(0).length() + 2)
                .sum();
        try {
            return C4CarrierParser.parse(new C4TransportResponse(
                    200, headers, body, issuerPrincipal, total));
        } catch (C4CarrierException violation) {
            throw new IllegalStateException("local carrier envelope is not parseable", violation);
        }
    }

    private C4KeyRegistry registry(String acquirerRef) {
        C4TrustedPrincipalKey trusted = new C4TrustedPrincipalKey(
                environmentId, issuerPrincipal, keyId, C4PrincipalRole.ISSUER_SIM,
                Set.of(acquirerRef), key.getPublic());
        return (environment, principal, requestedKeyId) ->
                environmentId.equals(environment) && issuerPrincipal.equals(principal)
                        && keyId.equals(requestedKeyId)
                        ? Optional.of(trusted) : Optional.empty();
    }

    /** The verified chain the result gateway may submit; never persisted or logged. */
    public record Chain(C4EvidenceAttestation signed, C4CarrierCandidate candidate,
            C4AuthenticatedTransportContext context, C4KeyRegistry registry, byte[] response) {
    }
}
