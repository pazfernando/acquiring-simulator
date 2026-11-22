package com.acquiring.simulator.channel;

/**
 * Trusted transport facts supplied by the internal service boundary.
 *
 * <p>The adapter constructs this after the transport has authenticated the peer and has
 * selected the configured environment. It must never be built from an {@code X-*} header,
 * a request or response body field, a query parameter, a JWT claim asserted by the client
 * or any client-controlled flag. Possession of the configured signing key and this
 * authenticated sender are separate checks.</p>
 */
public record C4AuthenticatedTransportContext(
        String environmentId,
        String authenticatedSender) {

    public C4AuthenticatedTransportContext {
        C4CarrierSchema.requireId("environmentId", environmentId, 64);
        C4CarrierSchema.requireId("authenticatedSender", authenticatedSender, 64);
    }

    @Override
    public String toString() {
        return "C4AuthenticatedTransportContext[environmentId=" + environmentId
                + ", senderPresent=true]";
    }
}
