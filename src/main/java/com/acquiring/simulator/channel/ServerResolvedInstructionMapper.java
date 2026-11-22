package com.acquiring.simulator.channel;

import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.model.CvmIndicator;
import com.acquiring.simulator.domain.model.EntryMode;
import com.acquiring.simulator.domain.model.RiskIndicator;
import com.acquiring.simulator.protocol.iso8583.Iso8583AdmissionMode;
import com.acquiring.simulator.protocol.iso8583.Iso8583De48RequestParser;
import com.acquiring.simulator.protocol.iso8583.Iso8583PackException;
import com.acquiring.simulator.protocol.iso8583.Iso8583Packer;
import com.acquiring.simulator.protocol.iso8583.Iso8583RequestValidation;
import com.acquiring.simulator.protocol.iso8583.Iso8583RequestValidationException;
import com.acquiring.simulator.protocol.iso8583.Iso8583RequestValidationPipeline;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Pure mapper from one validated C1 channel request plus one trusted server
 * resolution to the canonical SIM-ISO87A-v1 {@code 0100} instruction.
 *
 * <p>This is a mapping seam only. It does not allocate an authorization,
 * transaction or attempt identifier, allocate or hash an alias, increment a
 * counter, resolve a fixture/mandate/route, choose an outcome, authenticate,
 * authorize, claim, dispatch, persist or send. Every server value comes from
 * {@link ServerInstructionResolution}; the only channel-owned instruction value
 * is {@code cid}, which is the validated caller correlation echoed unchanged.</p>
 *
 * <p><strong>Value ownership.</strong></p>
 * <ul>
 *   <li>{@code v=1}, {@code op=A}, DE3 {@code 000000}, DE25 {@code 00} and
 *       DE49 {@code 840} are approved profile constants.</li>
 *   <li>{@code aid}, {@code tid}, {@code att}, {@code gen}, DE32/DE41/DE42,
 *       DE11 STAN, DE37 RRN and {@code sent} are taken only from the trusted
 *       server resolution.</li>
 *   <li>{@code cid} is taken only from the validated channel request.</li>
 *   <li>{@code acq}, {@code mid}, {@code term}, {@code pay}, {@code fixture},
 *       entry mode, CVM, cryptogram flag, risk, amount and currency are
 *       restated by the resolution and must equal the validated channel
 *       request; a mismatch is refused, never silently replaced.</li>
 *   <li>DE4 is the exact 12-digit zero-format of the resolved minor units; DE7
 *       is the {@code MMDDhhmmss} projection of the resolved {@code sent}; DE22
 *       is {@code 051}/{@code 071} from the resolved entry mode; DE48
 *       {@code cvm}/{@code crypto}/{@code risk} are the approved enum
 *       projections.</li>
 * </ul>
 *
 * <p>The result is packed by the existing {@link Iso8583Packer} and then checked
 * by the existing ordered {@link Iso8583RequestValidationPipeline} in
 * {@link Iso8583AdmissionMode#NEW_FIRST_DISPATCH} mode, so the canonical
 * schema, domain, 300-second freshness and 826-byte budget rules are applied
 * exactly once by their approved owners. A structural packing failure or an
 * ordered validation failure is surfaced as a safe
 * {@link InstructionMappingException}; no partially mapped body is returned.</p>
 *
 * <p><strong>Replay boundary.</strong> This mapper is NEW-only. An authorized
 * historical replay or late-evidence operation must return the persisted
 * original instruction from its separate policy seam; it is not remapped,
 * re-encoded or redispatched here. A non-NEW mode and a linked prior reference
 * are refused rather than ignored.</p>
 */
public final class ServerResolvedInstructionMapper {

    /** The only MTI this mapper produces. */
    public static final String REQUEST_MTI = "0100";

    /** Approved private purchase processing code. */
    public static final String PROCESSING_CODE = "000000";

    /** Approved POS condition code. */
    public static final String POS_CONDITION = "00";

    /** Approved USD currency code. */
    public static final String CURRENCY_CODE = "840";

    /** The single approved currency token. */
    public static final String USD = "USD";

    /** Inclusive DE4 minor-unit lower bound. */
    public static final long MIN_AMOUNT_MINOR = 100L;

    /** Inclusive DE4 minor-unit upper bound. */
    public static final long MAX_AMOUNT_MINOR = 999_999L;

    private static final int SENT_WIDTH = 14;

    private ServerResolvedInstructionMapper() {
    }

    /**
     * Map one validated channel request and one trusted server resolution to a
     * canonical {@code 0100} instruction.
     *
     * @param request    validated typed C1 request
     * @param resolution trusted immutable server resolution for this new work
     * @param mode       trusted server-side admission mode; only
     *                   {@link Iso8583AdmissionMode#NEW_FIRST_DISPATCH} is mapped
     * @param receivedAt trusted server receipt instant for the NEW freshness check
     * @return exact packed instruction, canonical fields/DE48 and pipeline envelope
     * @throws InstructionMappingException on the first mapping refusal
     */
    public static ServerResolvedInstruction map(
            ChannelAuthorizationRequest request,
            ServerInstructionResolution resolution,
            Iso8583AdmissionMode mode,
            Instant receivedAt) {
        if (request == null) {
            throw InstructionMappingException.inputRequired("request");
        }
        if (mode == null) {
            throw InstructionMappingException.inputRequired("mode");
        }
        if (mode != Iso8583AdmissionMode.NEW_FIRST_DISPATCH) {
            throw InstructionMappingException.newDispatchOnly(mode);
        }
        if (resolution == null) {
            throw InstructionMappingException.inputRequired("resolution");
        }
        if (receivedAt == null) {
            throw InstructionMappingException.inputRequired("receivedAt");
        }
        if (request.correlationId() == null) {
            throw InstructionMappingException.inputRequired("correlationId");
        }
        if (request.priorAuthorizationId() != null) {
            throw InstructionMappingException.linkedRetryNotNew();
        }

        requireContextAndReferences(request, resolution);
        requireEqual(InstructionMappingException.Rule.FIXTURE_MISMATCH,
                "fixture", request.credentialFixtureRef(), resolution.fixture());
        requireEqual(InstructionMappingException.Rule.PAYMENT_MISMATCH,
                "amount", request.amountMinor(), resolution.amountMinor());
        requireEqual(InstructionMappingException.Rule.PAYMENT_MISMATCH,
                "currency", request.currency(), resolution.currency());
        if (!USD.equals(resolution.currency())) {
            throw InstructionMappingException.serverValueInvalid("currency");
        }
        requireEqual(InstructionMappingException.Rule.ENTRY_MODE_MISMATCH,
                "entryMode", request.entryMode(), resolution.entryMode());
        requireEqual(InstructionMappingException.Rule.CVM_MISMATCH,
                "cvm", request.cvm(), resolution.cvm());
        requireEqual(InstructionMappingException.Rule.CRYPTOGRAM_MISMATCH,
                "cryptogramPresent", request.cryptogramPresent(),
                resolution.cryptogramPresent());
        requireEqual(InstructionMappingException.Rule.RISK_MISMATCH,
                "risk", request.risk(), resolution.risk());

        if (!resolution.authorizedContext().permittedRoutes().contains(resolution.route())) {
            throw InstructionMappingException.routeNotPermitted();
        }
        requireSent(resolution.sent());

        String de48 = buildDe48(request, resolution);
        Map<Integer, String> fields = buildFields(resolution, de48);

        String body;
        try {
            body = Iso8583Packer.pack(REQUEST_MTI, fields);
        } catch (Iso8583PackException packFailure) {
            throw InstructionMappingException.packRejected(
                    componentOf(packFailure), packFailure.rule(), packFailure);
        }

        Iso8583RequestValidation validation;
        try {
            validation = Iso8583RequestValidationPipeline.evaluate(
                    body, receivedAt, Iso8583AdmissionMode.NEW_FIRST_DISPATCH);
        } catch (Iso8583RequestValidationException validationFailure) {
            throw InstructionMappingException.validationRejected(
                    validationFailure.stage(),
                    validationFailure.failureLabel(),
                    validationFailure);
        }

        return new ServerResolvedInstruction(
                body,
                validation.message().fields(),
                de48,
                resolution.route(),
                validation);
    }

    private static void requireContextAndReferences(
            ChannelAuthorizationRequest request, ServerInstructionResolution resolution) {
        AuthorizedContext context = resolution.authorizedContext();
        if (!Objects.equals(context.acquirerRef(), request.acquirerRef())
                || !Objects.equals(context.acquirerRef(), resolution.acquirerRef())
                || !Objects.equals(context.merchantRef(), request.merchantRef())
                || !Objects.equals(context.merchantRef(), resolution.merchantRef())) {
            throw InstructionMappingException.mismatch(
                    InstructionMappingException.Rule.CONTEXT_MISMATCH,
                    "authorizedContext");
        }
        requireEqual(InstructionMappingException.Rule.REFERENCE_MISMATCH,
                "acquirerRef", request.acquirerRef(), resolution.acquirerRef());
        requireEqual(InstructionMappingException.Rule.REFERENCE_MISMATCH,
                "merchantRef", request.merchantRef(), resolution.merchantRef());
        requireEqual(InstructionMappingException.Rule.REFERENCE_MISMATCH,
                "terminalRef", request.terminalRef(), resolution.terminalRef());
        requireEqual(InstructionMappingException.Rule.REFERENCE_MISMATCH,
                "paymentRef", request.paymentRef(), resolution.paymentRef());
    }

    private static void requireEqual(
            InstructionMappingException.Rule rule,
            String component,
            Object requestValue,
            Object resolutionValue) {
        if (!Objects.equals(requestValue, resolutionValue)) {
            throw InstructionMappingException.mismatch(rule, component);
        }
    }

    private static void requireSent(String sent) {
        if (sent.length() != SENT_WIDTH) {
            throw InstructionMappingException.serverValueInvalid("sent");
        }
        for (int i = 0; i < SENT_WIDTH; i++) {
            char c = sent.charAt(i);
            if (c < '0' || c > '9') {
                throw InstructionMappingException.serverValueInvalid("sent");
            }
        }
    }

    private static String buildDe48(
            ChannelAuthorizationRequest request, ServerInstructionResolution resolution) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("v", "1");
        values.put("op", "A");
        values.put("aid", resolution.authorizationId());
        values.put("cid", request.correlationId());
        values.put("tid", resolution.transactionId());
        values.put("att", resolution.instructionAttemptId());
        values.put("acq", resolution.acquirerRef());
        values.put("mid", resolution.merchantRef());
        values.put("term", resolution.terminalRef());
        values.put("pay", resolution.paymentRef());
        values.put("gen", Integer.toString(resolution.generation()));
        values.put("fixture", resolution.fixture());
        values.put("cvm", cvmValue(resolution.cvm()));
        values.put("crypto", resolution.cryptogramPresent() ? "1" : "0");
        values.put("risk", riskValue(resolution.risk()));
        values.put("sent", resolution.sent());

        StringBuilder de48 = new StringBuilder(192);
        for (String key : Iso8583De48RequestParser.REQUEST_KEYS) {
            if ("prior".equals(key)) {
                // A new unlinked instruction never carries a prior link.
                continue;
            }
            String value = values.get(key);
            if (value == null) {
                throw new IllegalStateException(
                        "mapping is missing a canonical DE48 value for " + key);
            }
            if (!de48.isEmpty()) {
                de48.append(';');
            }
            de48.append(key).append('=').append(value);
        }
        return de48.toString();
    }

    private static Map<Integer, String> buildFields(
            ServerInstructionResolution resolution, String de48) {
        Map<Integer, String> fields = new LinkedHashMap<>();
        fields.put(3, PROCESSING_CODE);
        fields.put(4, amountValue(resolution.amountMinor()));
        fields.put(7, resolution.sent().substring(4));
        fields.put(11, resolution.stan());
        fields.put(22, entryModeValue(resolution.entryMode()));
        fields.put(25, POS_CONDITION);
        fields.put(32, resolution.acquirerAlias());
        fields.put(37, resolution.rrn());
        fields.put(41, resolution.terminalAlias());
        fields.put(42, resolution.merchantAlias());
        fields.put(48, de48);
        fields.put(49, CURRENCY_CODE);
        return fields;
    }

    /** Exact 12-digit zero-format; the packer never pads or rounds. */
    private static String amountValue(long amountMinor) {
        return String.format(Locale.ROOT, "%012d", amountMinor);
    }

    private static String entryModeValue(EntryMode entryMode) {
        return switch (entryMode) {
            case SIMULATED_CONTACT -> "051";
            case SIMULATED_CONTACTLESS -> "071";
        };
    }

    private static String cvmValue(CvmIndicator cvm) {
        return switch (cvm) {
            case SIMULATED_VERIFIED -> "VERIFIED";
            case SIMULATED_NOT_REQUIRED -> "NOT_REQUIRED";
        };
    }

    private static String riskValue(RiskIndicator risk) {
        return switch (risk) {
            case PASS -> "PASS";
            case REJECT -> "REJECT";
        };
    }

    private static String componentOf(Iso8583PackException failure) {
        return failure.dataElement() > 0 ? "de" + failure.dataElement() : "header";
    }
}
