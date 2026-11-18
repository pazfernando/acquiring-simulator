package com.acquiring.simulator.adapter.aws;

import com.amazonaws.services.lambda.runtime.*;
import com.acquiring.simulator.domain.model.SimulatedDecisionRequest;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Only Processor IAM may invoke this function. Fault selection is operator-controlled. */
public final class MastercardHandler implements RequestStreamHandler {
    private final MastercardNetworkSimulator network;
    public MastercardHandler() {
        network = new MastercardNetworkSimulator(new LambdaDownstream(AwsRuntime.lambda(), AwsRuntime.env("ISSUER_FUNCTION")),
                MastercardNetworkSimulator.Fault.valueOf(System.getenv().getOrDefault("NETWORK_FAULT", "NONE")));
    }
    @Override public void handleRequest(InputStream in, OutputStream out, Context ctx) throws IOException {
        var request = WireJson.read(new String(in.readNBytes(65537), StandardCharsets.UTF_8), SimulatedDecisionRequest.class);
        out.write(WireJson.write(network.decide(request)).getBytes(StandardCharsets.UTF_8));
    }
}
