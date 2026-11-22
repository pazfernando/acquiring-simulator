package com.acquiring.simulator.protocol.iso8583;

/**
 * Failure of the pure original-instruction/context and exact DE38
 * authorization-reference equality check.
 *
 * <p>Rules describe only whether supplied trusted-record fields and response
 * echoes match. They do not authenticate a sender, verify an attestation
 * signature, resolve an evidence identifier, prove durable processor receipt or
 * decide any financial outcome. Messages are fixed diagnostic text and never
 * echo the supplied values.</p>
 */
public final class Iso8583InstructionBindingException extends RuntimeException {

    public enum Rule {
        /** A required trusted-record or response input was absent. */
        MISSING_TRUSTED_RECORD,
        /** A trusted hash or authorization reference was not in canonical form. */
        MALFORMED_TRUSTED_RECORD,
        /** The trusted evidence hash does not equal the original-wire hash. */
        INSTRUCTION_HASH_MISMATCH,
        /** A required echoed response field is absent. */
        REQUIRED_ECHO_MISSING,
        /** An echoed value does not equal the persisted original instruction. */
        INSTRUCTION_FIELD_MISMATCH,
        /** The trusted record asserts a DE38 reference the response omits. */
        AUTHORIZATION_REFERENCE_MISSING,
        /** The response carries a DE38 reference the trusted record does not assert. */
        AUTHORIZATION_REFERENCE_UNSOLICITED,
        /** The response DE38 differs from the trusted asserted reference. */
        AUTHORIZATION_REFERENCE_MISMATCH
    }

    private final Rule rule;
    private final String component;

    public Iso8583InstructionBindingException(Rule rule, String component, String message) {
        super(message);
        this.rule = rule;
        this.component = component;
    }

    public Rule rule() {
        return rule;
    }

    /** Failing field or component name; never its raw value. */
    public String component() {
        return component;
    }
}
