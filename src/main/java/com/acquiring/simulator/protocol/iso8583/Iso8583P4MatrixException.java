package com.acquiring.simulator.protocol.iso8583;

/**
 * Structural failure of the SIM-ISO87A-v1 P4 actor/dispatch-stage/result
 * matrix.
 *
 * <p>Rules describe only whether the five claimed values match an approved
 * matrix tuple. They do not authenticate the claimed actor, verify trust in an
 * evidence identifier, bind DE38, bind the original instruction/context, or
 * decide any financial outcome. Messages are fixed diagnostic text and never
 * echo the supplied values.</p>
 */
public final class Iso8583P4MatrixException extends RuntimeException {

    public enum Rule {
        /** One of the five matrix components was absent. */
        MISSING_COMPONENT,
        /** DE39 is not one of the four matrix decision codes 00/05/30/91. */
        DE39_UNSUPPORTED,
        /** The claimed actor/stage pair is not a stage the matrix allows. */
        ACTOR_STAGE_NOT_PERMITTED,
        /** DE39 is not permitted for this otherwise valid actor/stage pair. */
        DE39_NOT_PERMITTED_FOR_STAGE,
        /** The result is not the one the matrix requires for this tuple. */
        RESULT_NOT_PERMITTED,
        /** The reason is not one the matrix permits for this tuple. */
        REASON_NOT_PERMITTED
    }

    private final Rule rule;
    private final String component;

    public Iso8583P4MatrixException(Rule rule, String component, String message) {
        super(message);
        this.rule = rule;
        this.component = component;
    }

    public Rule rule() {
        return rule;
    }

    /** Failing matrix component name; never its raw value. */
    public String component() {
        return component;
    }
}
