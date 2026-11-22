package com.acquiring.simulator.channel;

/**
 * Deterministic RFC8785-shaped JSON for the closed C4 carrier objects.
 *
 * <p>Every emitted object uses the exact declared key set in canonical (UTF-16 code-unit)
 * order, compact separators and no trailing bytes. Because the contract restricts all
 * string values to printable ASCII and all numeric values to integers, RFC8785
 * serialization reduces to this explicit emitter: no generic maps, no key discovery and
 * no P5 attestation field can appear unless it is declared in the model. The parser
 * compares received envelope bytes against this output, so non-canonical spacing, key
 * order, escaping or duplicate keys are rejected rather than normalized.</p>
 */
final class C4CarrierCanonicalJson {

    private C4CarrierCanonicalJson() {
    }

    static String lateEnvelope(C4LateDeliveryEnvelope late) {
        StringBuilder out = new StringBuilder(2048);
        out.append('{');
        field(out, "deliveryReceiptId", quote(late.deliveryReceiptId())).append(',');
        field(out, "evidence", envelope(late.evidence())).append(',');
        field(out, "responseBodyBase64", quote(late.responseBodyBase64())).append(',');
        field(out, "version", Integer.toString(late.version()));
        return out.append('}').toString();
    }

    static String envelope(C4CarrierEnvelope envelope) {
        StringBuilder out = new StringBuilder(512);
        out.append('{');
        field(out, "attestation", attestation(envelope.attestation())).append(',');
        field(out, "keyId", quote(envelope.keyId())).append(',');
        field(out, "mode", quote(envelope.mode().name())).append(',');
        field(out, "relay", envelope.relay() == null ? "null" : relay(envelope.relay())).append(',');
        field(out, "signature", quote(C4CarrierSchema.canonicalBase64(envelope.signature())))
                .append(',');
        field(out, "version", Integer.toString(envelope.version()));
        return out.append('}').toString();
    }

    static String attestation(C4EvidenceAttestation a) {
        StringBuilder out = new StringBuilder(384);
        out.append('{');
        field(out, "DE39", quote(a.DE39())).append(',');
        field(out, "DE7", quote(a.DE7())).append(',');
        field(out, "RRN", quote(a.RRN())).append(',');
        field(out, "STAN", quote(a.STAN())).append(',');
        field(out, "acquirerRef", quote(a.acquirerRef())).append(',');
        field(out, "actor", quote(a.actor())).append(',');
        field(out, "amountMinor", Integer.toString(a.amountMinor())).append(',');
        field(out, "authorizationId", quote(a.authorizationId())).append(',');
        field(out, "authorizationReference", quoteOrNull(a.authorizationReference())).append(',');
        field(out, "availableAt", quote(a.availableAt())).append(',');
        field(out, "correlationId", quote(a.correlationId())).append(',');
        field(out, "currency", quote(a.currency())).append(',');
        field(out, "environmentId", quote(a.environmentId())).append(',');
        field(out, "evidenceId", quote(a.evidenceId())).append(',');
        field(out, "generation", Integer.toString(a.generation())).append(',');
        field(out, "instructionAttemptId", quote(a.instructionAttemptId())).append(',');
        field(out, "instructionSha256", quote(a.instructionSha256())).append(',');
        field(out, "keyId", quote(a.keyId())).append(',');
        field(out, "merchantRef", quote(a.merchantRef())).append(',');
        field(out, "paymentRef", quote(a.paymentRef())).append(',');
        field(out, "producerPrincipal", quote(a.producerPrincipal())).append(',');
        field(out, "profile", quote(a.profile())).append(',');
        field(out, "reason", quote(a.reason())).append(',');
        field(out, "responseSha256", quote(a.responseSha256())).append(',');
        field(out, "result", quote(a.result())).append(',');
        field(out, "schemaVersion", Integer.toString(a.schemaVersion())).append(',');
        field(out, "sent", quote(a.sent())).append(',');
        field(out, "signingPrincipal", quote(a.signingPrincipal())).append(',');
        field(out, "stage", quote(a.stage())).append(',');
        field(out, "terminalRef", quote(a.terminalRef())).append(',');
        field(out, "transactionId", quote(a.transactionId()));
        return out.append('}').toString();
    }

    static String relay(C4RelayAttestation relay) {
        StringBuilder out = new StringBuilder(256);
        out.append('{');
        relayFields(out, relay);
        out.append(',');
        field(out, "signature", quote(C4CarrierSchema.canonicalBase64(relay.signature())));
        return out.append('}').toString();
    }

    /** Relay object without {@code signature}; this is what the relay signature covers. */
    static String relaySigned(C4RelayAttestation relay) {
        StringBuilder out = new StringBuilder(224);
        out.append('{');
        relayFields(out, relay);
        return out.append('}').toString();
    }

    private static void relayFields(StringBuilder out, C4RelayAttestation relay) {
        field(out, "attestationSha256", quote(relay.attestationSha256())).append(',');
        field(out, "deliveredAt", quote(relay.deliveredAt())).append(',');
        field(out, "keyId", quote(relay.keyId())).append(',');
        field(out, "principal", quote(relay.principal())).append(',');
        field(out, "receiptId", quote(relay.receiptId())).append(',');
        field(out, "responseSha256", quote(relay.responseSha256()));
    }

    private static StringBuilder field(StringBuilder out, String name, String jsonValue) {
        out.append(quote(name)).append(':').append(jsonValue);
        return out;
    }

    private static String quoteOrNull(String value) {
        return value == null ? "null" : quote(value);
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
