package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.domain.model.*;
import com.acquiring.simulator.domain.port.IssuerSimulator;
import java.time.Duration;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.*;

/** No automatic retries of financial instructions; ambiguous invocation failures stay unknown. */
public final class LambdaDownstream implements IssuerSimulator {
    private final LambdaClient client;
    private final String function;
    public LambdaDownstream(LambdaClient client, String function) { this.client = client; this.function = function; }
    @Override public DownstreamReply decide(SimulatedDecisionRequest request) {
        try {
            var response = client.invoke(InvokeRequest.builder().functionName(function)
                    .invocationType(InvocationType.REQUEST_RESPONSE)
                    .overrideConfiguration(c -> c.apiCallTimeout(request.responseTimeout()))
                    .payload(SdkBytes.fromUtf8String(WireJson.write(request))).build());
            if (response.statusCode() != 200 || response.functionError() != null || response.payload() == null) return unknown(request);
            return WireJson.read(response.payload().asUtf8String(), DownstreamReply.class);
        } catch (SdkException | IllegalArgumentException e) { return unknown(request); }
    }
    public static DownstreamReply unknown(SimulatedDecisionRequest r) {
        return new DownstreamReply(r.correlationId(), IssuerDecision.NO_RESPONSE,
                r.dispatchedAt().plus(r.responseTimeout()).plus(Duration.ofMillis(1)));
    }
}
