package com.acquiring.simulator.channel;

import com.acquiring.simulator.domain.model.Route;
import com.acquiring.simulator.protocol.iso8583.Iso8583RequestValidation;
import com.acquiring.simulator.protocol.iso8583.Iso8583RequestValidationPipeline;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * One canonical {@code 0100} instruction produced by the server-resolved
 * mapper.
 *
 * <p>{@code body} is the exact packed ASCII request, {@code fields} is the
 * canonical ascending field view, {@code de48} is the exact canonical extension
 * and {@code validation} is the ordered pipeline envelope that accepted the
 * packed result. {@code resolvedRoute} is the server-resolved non-wire routing
 * decision retained for the later claim/dispatch seam; it is deliberately not
 * encoded in DE48 or any other data element.</p>
 *
 * <p>This record is a mapping artifact only. It is not an admission,
 * authorization, authentication, claim, dispatch, registry outcome or financial
 * decision, and it carries no client-overridable server value. It contains no
 * response, evidence, DE38, DE39, C4 carriage or HTTP material.</p>
 *
 * @param body          exact packed {@code 0100} ASCII octets
 * @param fields        ascending immutable canonical data elements
 * @param de48          exact canonical DE48 request extension
 * @param resolvedRoute server-resolved route, not wire-encoded
 * @param validation    ordered pipeline envelope for the packed body
 */
public record ServerResolvedInstruction(
        String body,
        Map<Integer, String> fields,
        String de48,
        Route resolvedRoute,
        Iso8583RequestValidation validation) {

    public ServerResolvedInstruction {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(de48, "de48");
        Objects.requireNonNull(resolvedRoute, "resolvedRoute");
        Objects.requireNonNull(validation, "validation");
        if (!body.startsWith(Iso8583RequestValidationPipeline.REQUEST_MTI)) {
            throw new IllegalArgumentException("mapped body must start with the request MTI");
        }
        if (!Iso8583RequestValidationPipeline.REQUEST_MTI.equals(validation.message().mti())) {
            throw new IllegalArgumentException("validation envelope must hold a 0100 request");
        }
        fields = Collections.unmodifiableMap(new TreeMap<>(fields));
    }

    /** @return exact packed body length in ASCII octets */
    public int length() {
        return body.length();
    }
}
