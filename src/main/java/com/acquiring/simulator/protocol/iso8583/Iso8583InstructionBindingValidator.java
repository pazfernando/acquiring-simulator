package com.acquiring.simulator.protocol.iso8583;

import java.util.regex.Pattern;

/**
 * Pure equality check between a received {@code 0110} echo, the already-verified
 * persisted original {@code 0100} instruction and a trusted-evidence record.
 *
 * <p>This validator compares values only. It does not authenticate a producer,
 * verify an attestation signature or evidence identifier, prove durable
 * processor receipt, or decide payment admission or a financial outcome. The
 * trusted record is a seam supplied by the caller; a successful call produces no
 * "verified" flag and never mints an authorization code.</p>
 *
 * <p>The compared echo is the complete business/context/metadata set the
 * profile requires a response to repeat: DE48 identity and context
 * ({@code cid, aid, tid, att, acq, mid, term, pay, gen}), the remaining DE48
 * metadata ({@code fixture, cvm, crypto, risk, sent, prior}) and the physical
 * elements DE3/4/7/11/22/25/32/37/41/42/49. {@code v} and {@code op} are fixed
 * literals already enforced by both canonical parsers. The original-wire hash
 * is supplied by the caller and is never recomputed from re-encoded bytes.</p>
 *
 * <p>Evaluation order is deliberate: the trusted instruction hash is checked
 * first, then every instruction echo in a fixed order, then DE38. Callers still
 * run the P4 actor/stage/DE39/result/reason matrix before this binding layer, so
 * the first failure across the layers stays P4, then instruction, then DE38.
 * Anything after the first failure is not evaluated. Only equality and the
 * profile's zero-formatted DE4 projection are applied: no amount range, USD
 * policy, calendar/DE7 consistency, registry/mandate check, freshness, capacity
 * or admission rule participates.</p>
 *
 * <p>This is not the full P5 envelope. Environment and producer/relay
 * principals, evidence identifier, attestation signature, {@code responseSha256},
 * signing principal/key, transport receipt and business admission remain a
 * separate future seam and are not declared covered by these comparisons.</p>
 */
public final class Iso8583InstructionBindingValidator {

    /** ISO 8583 DE38 is fixed six alphanumeric characters. */
    public static final Pattern DE38 = Pattern.compile("[A-Z0-9]{6}");

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    private Iso8583InstructionBindingValidator() {
    }

    /**
     * Bind one response echo to the persisted original instruction and the
     * trusted DE38 assertion.
     *
     * @param original        already-verified persisted original instruction
     * @param trusted         trusted-evidence projection; must not be synthesized
     *                        from the response or a caller boolean
     * @param responseMessage scanned physical {@code 0110} message
     * @param response        canonical parsed DE48 response echo
     * @return equality-only decision; never trust, admission or a minted code
     * @throws Iso8583InstructionBindingException on the first failing check
     */
    public static Iso8583InstructionBindingDecision validate(
            Iso8583OriginalInstruction original,
            Iso8583TrustedEvidenceRecord trusted,
            Iso8583Message responseMessage,
            Iso8583De48Response response) {
        requirePresent(original, "originalInstruction");
        requirePresent(trusted, "trustedRecord");
        requirePresent(responseMessage, "responseMessage");
        requirePresent(response, "response");
        requireCanonicalTrustedRecord(trusted);

        if (!trusted.instructionSha256().equals(original.originalWireSha256())) {
            throw new Iso8583InstructionBindingException(
                    Iso8583InstructionBindingException.Rule.INSTRUCTION_HASH_MISMATCH,
                    "originalWireSha256",
                    "trusted instruction hash does not equal the original-wire hash");
        }

        // Identity and business context.
        requireEcho(response, "cid", original.correlationId());
        requireEcho(response, "aid", original.authorizationId());
        requireEcho(response, "tid", original.transactionId());
        requireEcho(response, "att", original.instructionAttemptId());
        requireEcho(response, "acq", original.acquirerRef());
        requireEcho(response, "mid", original.merchantRef());
        requireEcho(response, "term", original.terminalRef());
        requireEcho(response, "pay", original.paymentRef());
        requireEcho(response, "gen", Integer.toString(original.generation()));

        // Remaining canonical DE48 metadata echoes.
        requireEcho(response, "fixture", original.fixture());
        requireEcho(response, "cvm", original.cvm());
        requireEcho(response, "crypto", original.crypto());
        requireEcho(response, "risk", original.risk());
        requireEcho(response, "sent", original.sent());
        requirePriorEcho(response, original.prior());

        // Physical elements echoed on the response wire.
        Iso8583PhysicalInstruction physical = original.physical();
        requirePhysicalEcho(responseMessage, 3, physical.de3());
        requirePhysicalEcho(responseMessage, 4, physical.de4());
        requirePhysicalEcho(responseMessage, 7, physical.de7());
        requirePhysicalEcho(responseMessage, 11, physical.stan());
        requirePhysicalEcho(responseMessage, 22, physical.de22());
        requirePhysicalEcho(responseMessage, 25, physical.de25());
        requirePhysicalEcho(responseMessage, 32, physical.de32());
        requirePhysicalEcho(responseMessage, 37, physical.rrn());
        requirePhysicalEcho(responseMessage, 41, physical.de41());
        requirePhysicalEcho(responseMessage, 42, physical.de42());
        requirePhysicalEcho(responseMessage, 49, physical.currency());

        requireAuthorizationReference(trusted, responseMessage.field(38));

        return new Iso8583InstructionBindingDecision(
                original.authorizationId(), trusted.authorizationReference());
    }

    private static void requirePresent(Object value, String component) {
        if (value == null) {
            throw new Iso8583InstructionBindingException(
                    Iso8583InstructionBindingException.Rule.MISSING_TRUSTED_RECORD,
                    component,
                    "required binding input is absent");
        }
    }

    private static void requireCanonicalTrustedRecord(Iso8583TrustedEvidenceRecord trusted) {
        if (!SHA256_HEX.matcher(trusted.instructionSha256()).matches()) {
            throw new Iso8583InstructionBindingException(
                    Iso8583InstructionBindingException.Rule.MALFORMED_TRUSTED_RECORD,
                    "trustedRecord.instructionSha256",
                    "trusted instruction hash is not canonical lowercase SHA-256 hex");
        }
        if (trusted.hasAuthorizationReference()
                && !DE38.matcher(trusted.authorizationReference()).matches()) {
            throw new Iso8583InstructionBindingException(
                    Iso8583InstructionBindingException.Rule.MALFORMED_TRUSTED_RECORD,
                    "trustedRecord.authorizationReference",
                    "trusted authorization reference is not canonical DE38");
        }
    }

    private static void requireEcho(
            Iso8583De48Response response, String key, String expected) {
        String echoed = response.value(key);
        if (echoed == null || echoed.isEmpty()) {
            throw new Iso8583InstructionBindingException(
                    Iso8583InstructionBindingException.Rule.REQUIRED_ECHO_MISSING,
                    key,
                    "required response echo is absent");
        }
        if (!echoed.equals(expected)) {
            throw new Iso8583InstructionBindingException(
                    Iso8583InstructionBindingException.Rule.INSTRUCTION_FIELD_MISMATCH,
                    key,
                    "response echo does not equal the persisted original instruction");
        }
    }

    /**
     * {@code prior} is the only optional request key: its presence must mirror
     * the original exactly. An omitted original forbids an echoed prior; a
     * supplied original requires the identical value.
     */
    private static void requirePriorEcho(Iso8583De48Response response, String expectedPrior) {
        String echoed = response.value("prior");
        boolean responseHasPrior = echoed != null && !echoed.isEmpty();
        if (expectedPrior == null) {
            if (responseHasPrior) {
                throw new Iso8583InstructionBindingException(
                        Iso8583InstructionBindingException.Rule.INSTRUCTION_FIELD_MISMATCH,
                        "prior",
                        "response echoes a prior reference the original did not supply");
            }
            return;
        }
        if (!responseHasPrior) {
            throw new Iso8583InstructionBindingException(
                    Iso8583InstructionBindingException.Rule.REQUIRED_ECHO_MISSING,
                    "prior",
                    "required response echo is absent");
        }
        if (!expectedPrior.equals(echoed)) {
            throw new Iso8583InstructionBindingException(
                    Iso8583InstructionBindingException.Rule.INSTRUCTION_FIELD_MISMATCH,
                    "prior",
                    "response echo does not equal the persisted original instruction");
        }
    }

    private static void requirePhysicalEcho(
            Iso8583Message response, int dataElement, String expected) {
        String component = "de" + dataElement;
        String echoed = response.field(dataElement);
        if (echoed == null || echoed.isEmpty()) {
            throw new Iso8583InstructionBindingException(
                    Iso8583InstructionBindingException.Rule.REQUIRED_ECHO_MISSING,
                    component,
                    "required response echo is absent");
        }
        if (!echoed.equals(expected)) {
            throw new Iso8583InstructionBindingException(
                    Iso8583InstructionBindingException.Rule.INSTRUCTION_FIELD_MISMATCH,
                    component,
                    "response echo does not equal the persisted original instruction");
        }
    }

    private static void requireAuthorizationReference(
            Iso8583TrustedEvidenceRecord trusted, String responseDe38) {
        boolean responseHasReference = responseDe38 != null && !responseDe38.isEmpty();
        if (!trusted.hasAuthorizationReference()) {
            if (responseHasReference) {
                throw new Iso8583InstructionBindingException(
                        Iso8583InstructionBindingException.Rule.AUTHORIZATION_REFERENCE_UNSOLICITED,
                        "de38",
                        "response carries DE38 but the trusted record asserts no reference");
            }
            return;
        }
        if (!responseHasReference) {
            throw new Iso8583InstructionBindingException(
                    Iso8583InstructionBindingException.Rule.AUTHORIZATION_REFERENCE_MISSING,
                    "de38",
                    "trusted record asserts a DE38 reference the response omits");
        }
        if (!trusted.authorizationReference().equals(responseDe38)) {
            throw new Iso8583InstructionBindingException(
                    Iso8583InstructionBindingException.Rule.AUTHORIZATION_REFERENCE_MISMATCH,
                    "de38",
                    "response DE38 differs from the trusted asserted reference");
        }
    }
}
