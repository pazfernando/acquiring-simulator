package com.acquiring.simulator.channel;

import com.acquiring.simulator.protocol.iso8583.Iso8583AdmissionMode;
import com.acquiring.simulator.protocol.iso8583.Iso8583PackException;
import com.acquiring.simulator.protocol.iso8583.Iso8583RequestValidationException;

/**
 * Safe, first-failure rejection from the pure server-resolved instruction
 * mapper.
 *
 * <p>The type names the mapping rule and, where applicable, a fixed local
 * component, the ordered validation stage and the stable first-failure label.
 * It never carries caller values, resolved values, the raw request, the DE48
 * extension or any packed bytes. A rejection therefore cannot be mistaken for a
 * produced instruction and cannot leak the values that caused it.</p>
 *
 * <p>This is not an admission, authorization, authentication or financial
 * outcome. It says only that no instruction was mapped.</p>
 */
public final class InstructionMappingException extends RuntimeException {

    /** Stable mapping refusal reasons. */
    public enum Rule {
        /** A required mapper input was absent. */
        INPUT_REQUIRED,
        /** The trusted mode was not a new first dispatch. */
        NEW_DISPATCH_ONLY,
        /** A linked retry was supplied to the new-dispatch mapper. */
        LINKED_RETRY_NOT_NEW,
        /** The authorized acquirer/merchant context did not match. */
        CONTEXT_MISMATCH,
        /** A full business reference did not match the channel request. */
        REFERENCE_MISMATCH,
        /** The resolved fixture did not match the channel request. */
        FIXTURE_MISMATCH,
        /** Amount or currency did not match the channel request. */
        PAYMENT_MISMATCH,
        /** Entry mode did not match the channel request. */
        ENTRY_MODE_MISMATCH,
        /** CVM indicator did not match the channel request. */
        CVM_MISMATCH,
        /** Cryptogram-present flag did not match the channel request. */
        CRYPTOGRAM_MISMATCH,
        /** Risk outcome did not match the channel request. */
        RISK_MISMATCH,
        /** The resolved route was absent from the authorized route set. */
        ROUTE_NOT_PERMITTED,
        /** A server value could not be represented in the approved profile. */
        SERVER_VALUE_INVALID,
        /** Structural packing rejected the resolved field values. */
        PACK_REJECTED,
        /** The ordered schema/domain/freshness/budget pipeline rejected the result. */
        VALIDATION_REJECTED
    }

    private final Rule rule;
    private final String component;
    private final Iso8583RequestValidationException.Stage stage;
    private final String failureLabel;

    private InstructionMappingException(
            Rule rule,
            String component,
            Iso8583RequestValidationException.Stage stage,
            String failureLabel,
            String message,
            Throwable cause) {
        super(message, cause);
        this.rule = rule;
        this.component = component;
        this.stage = stage;
        this.failureLabel = failureLabel;
    }

    public static InstructionMappingException inputRequired(String component) {
        return new InstructionMappingException(
                Rule.INPUT_REQUIRED,
                component,
                null,
                null,
                "required mapper input is absent: " + component,
                null);
    }

    public static InstructionMappingException newDispatchOnly(Iso8583AdmissionMode mode) {
        return new InstructionMappingException(
                Rule.NEW_DISPATCH_ONLY,
                "mode",
                null,
                null,
                "the server-resolved mapper accepts only a new first dispatch",
                null);
    }

    public static InstructionMappingException linkedRetryNotNew() {
        return new InstructionMappingException(
                Rule.LINKED_RETRY_NOT_NEW,
                "priorAuthorizationId",
                null,
                null,
                "a linked retry belongs to the replay seam, not the new-dispatch mapper",
                null);
    }

    public static InstructionMappingException mismatch(Rule rule, String component) {
        return new InstructionMappingException(
                rule,
                component,
                null,
                null,
                "server resolution does not match the validated channel request at "
                        + component,
                null);
    }

    public static InstructionMappingException routeNotPermitted() {
        return new InstructionMappingException(
                Rule.ROUTE_NOT_PERMITTED,
                "route",
                null,
                null,
                "the resolved route is not permitted by the authorized context",
                null);
    }

    public static InstructionMappingException serverValueInvalid(String component) {
        return new InstructionMappingException(
                Rule.SERVER_VALUE_INVALID,
                component,
                null,
                null,
                "server resolution value cannot be represented in the approved profile at "
                        + component,
                null);
    }

    public static InstructionMappingException packRejected(
            String component, Iso8583PackException.Rule packRule, Iso8583PackException cause) {
        return new InstructionMappingException(
                Rule.PACK_REJECTED,
                component,
                null,
                packRule.name(),
                "structural packing rejected the resolved instruction at " + component
                        + ": " + packRule.name(),
                cause);
    }

    public static InstructionMappingException validationRejected(
            Iso8583RequestValidationException.Stage stage,
            String failureLabel,
            Iso8583RequestValidationException cause) {
        return new InstructionMappingException(
                Rule.VALIDATION_REJECTED,
                stage.name(),
                stage,
                failureLabel,
                "ordered request validation rejected the resolved instruction at "
                        + stage + ": " + failureLabel,
                cause);
    }

    public Rule rule() {
        return rule;
    }

    /** Fixed local component name, or the validation stage name; never a value. */
    public String component() {
        return component;
    }

    /** Ordered pipeline stage for {@link Rule#VALIDATION_REJECTED}, else {@code null}. */
    public Iso8583RequestValidationException.Stage stage() {
        return stage;
    }

    /** Packer rule or pipeline first-failure label, else {@code null}. */
    public String failureLabel() {
        return failureLabel;
    }
}
