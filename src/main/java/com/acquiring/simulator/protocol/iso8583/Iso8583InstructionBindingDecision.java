package com.acquiring.simulator.protocol.iso8583;

import java.util.Objects;

/**
 * Result of one pure instruction/context/authorization-reference equality check.
 *
 * <p>Construction means only that every echoed DE48 metadata value and every
 * physical DE3/4/7/11/22/25/32/37/41/42/49 value equalled the supplied
 * persisted original instruction and that the response DE38 presence and value
 * matched the trusted assertion. It is <strong>not</strong> authentication,
 * issuer truth, evidence-signature verification, full P5 identity, payment
 * admission or financial outcome. The record carries no verified, trusted or
 * admitted flag by design.</p>
 *
 * @param authorizationId bound original authorization ID
 * @param authorizationReference exactly the trusted asserted DE38 value, or
 *                             {@code null} when the trusted record asserted no
 *                             reference and the response carried none. The
 *                             validator never mints a value here.
 */
public record Iso8583InstructionBindingDecision(
        String authorizationId, String authorizationReference) {

    public Iso8583InstructionBindingDecision {
        Objects.requireNonNull(authorizationId, "authorizationId");
    }

    /** @return {@code true} when an exact DE38 reference was bound */
    public boolean hasAuthorizationReference() {
        return authorizationReference != null;
    }
}
