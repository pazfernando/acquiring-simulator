package com.acquiring.simulator.protocol.iso8583;

import java.util.Set;

/**
 * Pure check of the approved SIM-ISO87A-v1 P4 actor/dispatch-stage/DE39/result/
 * reason matrix.
 *
 * <p>The eleven approved tuples are the two issuer rows ({@code 00} approval and
 * {@code 05} deliberate decline), the processor and network pre-issuer
 * rejections ({@code 30} with {@code FORMAT} or {@code DISALLOWED}), the network
 * positive non-forwarding evidence ({@code 91}/{@code UNAVAILABLE}), and the
 * network or processor possible-issuer uncertainty ({@code 91}/
 * {@code NOT_CONFIRMED} with {@code UNCERTAIN} or {@code UNAVAILABLE}). The
 * "no response at all" row is not a tuple: absence has no representation here
 * and this API never manufactures a response on timeout.</p>
 *
 * <p>A successful call returns only syntactic matrix eligibility. The actor and
 * every other value remain caller claims. This validator does not authenticate
 * the producer principal, verify an evidence identifier, resolve DE38
 * presence/absence, bind the original instruction or context, prove durable
 * processor receipt, or decide a financial outcome. In particular, {@code 00}/
 * {@code 05} are eligible only under a claimed {@code ISSUER} at
 * {@code ISSUER_REPLIED}; whether that issuer decision is real belongs to the
 * separate attestation/trusted-evidence slice, and a network or processor claim
 * of an issuer code is rejected here.</p>
 */
public final class Iso8583P4MatrixValidator {

    /** DE39 codes that participate in any approved matrix row. */
    public static final Set<String> SUPPORTED_DE39 = Set.of("00", "05", "30", "91");

    private Iso8583P4MatrixValidator() {
    }

    /**
     * Check one claimed response tuple.
     *
     * @param actor claimed producing actor text
     * @param stage claimed dispatch stage
     * @param de39 physical DE39 value
     * @param result claimed result
     * @param reason claimed reason
     * @return matrix eligibility only; never financial admission or trust
     * @throws Iso8583P4MatrixException when the tuple is not an approved row
     */
    public static Iso8583P4MatrixDecision validate(
            String actor, String stage, String de39, String result, String reason) {
        requireComponent("actor", actor);
        requireComponent("stage", stage);
        requireComponent("de39", de39);
        requireComponent("result", result);
        requireComponent("reason", reason);

        if (!SUPPORTED_DE39.contains(de39)) {
            throw new Iso8583P4MatrixException(
                    Iso8583P4MatrixException.Rule.DE39_UNSUPPORTED,
                    "de39",
                    "DE39 is not one of the supported decision codes");
        }
        if (!isPermittedActorStage(actor, stage)) {
            throw new Iso8583P4MatrixException(
                    Iso8583P4MatrixException.Rule.ACTOR_STAGE_NOT_PERMITTED,
                    "stage",
                    "dispatch stage is not a permitted actor/stage pair");
        }
        if (!isPermittedDe39(actor, stage, de39)) {
            throw new Iso8583P4MatrixException(
                    Iso8583P4MatrixException.Rule.DE39_NOT_PERMITTED_FOR_STAGE,
                    "de39",
                    "DE39 is not permitted for this dispatch stage");
        }
        if (!isPermittedResult(stage, de39, result)) {
            throw new Iso8583P4MatrixException(
                    Iso8583P4MatrixException.Rule.RESULT_NOT_PERMITTED,
                    "result",
                    "result is not permitted for this dispatch stage and DE39");
        }
        if (!isPermittedReason(actor, stage, de39, reason)) {
            throw new Iso8583P4MatrixException(
                    Iso8583P4MatrixException.Rule.REASON_NOT_PERMITTED,
                    "reason",
                    "reason is not permitted for this dispatch stage, DE39 and result");
        }
        return new Iso8583P4MatrixDecision(actor, stage, de39, result, reason);
    }

    private static void requireComponent(String component, String value) {
        if (value == null || value.isEmpty()) {
            throw new Iso8583P4MatrixException(
                    Iso8583P4MatrixException.Rule.MISSING_COMPONENT,
                    component,
                    "matrix component is required");
        }
    }

    private static boolean isPermittedActorStage(String actor, String stage) {
        return switch (stage) {
            case "ISSUER_REPLIED" -> actor.equals("ISSUER");
            case "BEFORE_NETWORK" -> actor.equals("PROCESSOR");
            case "BEFORE_ISSUER" -> actor.equals("NETWORK");
            case "POSSIBLE_ISSUER" ->
                    actor.equals("NETWORK") || actor.equals("PROCESSOR");
            default -> false;
        };
    }

    private static boolean isPermittedDe39(String actor, String stage, String de39) {
        return switch (actor + "/" + stage) {
            case "ISSUER/ISSUER_REPLIED" -> de39.equals("00") || de39.equals("05");
            case "PROCESSOR/BEFORE_NETWORK" -> de39.equals("30");
            case "NETWORK/BEFORE_ISSUER" -> de39.equals("30") || de39.equals("91");
            case "NETWORK/POSSIBLE_ISSUER", "PROCESSOR/POSSIBLE_ISSUER" ->
                    de39.equals("91");
            default -> false;
        };
    }

    private static boolean isPermittedResult(String stage, String de39, String result) {
        return switch (de39) {
            case "00" -> result.equals("APPROVED");
            case "05" -> result.equals("DECLINED");
            case "30" -> result.equals("PROCESSOR_REJECTED");
            case "91" -> stage.equals("POSSIBLE_ISSUER")
                    ? result.equals("NOT_CONFIRMED")
                    : result.equals("PROCESSOR_REJECTED");
            default -> false;
        };
    }

    private static boolean isPermittedReason(
            String actor, String stage, String de39, String reason) {
        return switch (de39) {
            case "00" -> reason.equals("APPROVED");
            case "05" -> reason.equals("DELIBERATE_DECLINE");
            case "30" -> (actor.equals("PROCESSOR") || actor.equals("NETWORK"))
                    && (reason.equals("FORMAT") || reason.equals("DISALLOWED"));
            case "91" -> {
                if (stage.equals("BEFORE_ISSUER")) {
                    yield reason.equals("UNAVAILABLE");
                }
                if (stage.equals("POSSIBLE_ISSUER")) {
                    yield reason.equals("UNCERTAIN") || reason.equals("UNAVAILABLE");
                }
                yield false;
            }
            default -> false;
        };
    }
}
