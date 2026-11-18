package com.acquiring.simulator.aws;

import com.acquiring.simulator.adapter.aws.*;
import com.acquiring.simulator.domain.AuthorizationService;
import com.acquiring.simulator.domain.model.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AwsTransportTest {
    final SimulatedDecisionRequest request = new SimulatedDecisionRequest("AUTH-1", "CORR-1", CredentialDecision.APPROVE,
            Route.DOMESTIC, Instant.parse("2026-11-18T12:00:00Z"), Duration.ofSeconds(2));
    @Test void lambdaInvocationIsSynchronousBoundedAndWireContractRoundTrips() {
        var client = mock(LambdaClient.class);
        var reply = new DownstreamReply("CORR-1", IssuerDecision.APPROVED, request.dispatchedAt());
        when(client.invoke(any(InvokeRequest.class))).thenReturn(InvokeResponse.builder().statusCode(200)
                .payload(SdkBytes.fromUtf8String(WireJson.write(reply))).build());
        assertThat(new LambdaDownstream(client, "mastercard").decide(request)).isEqualTo(reply);
        var capture = ArgumentCaptor.forClass(InvokeRequest.class);
        verify(client, times(1)).invoke(capture.capture());
        assertThat(capture.getValue().invocationType()).isEqualTo(InvocationType.REQUEST_RESPONSE);
        assertThat(WireJson.read(capture.getValue().payload().asUtf8String(), SimulatedDecisionRequest.class)).isEqualTo(request);
        assertThat(capture.getValue().overrideConfiguration().orElseThrow().apiCallTimeout()).contains(Duration.ofSeconds(2));
    }
    @Test void ambiguousTransportFailureNeverRetriesOrFabricatesDecline() {
        var client = mock(LambdaClient.class);
        when(client.invoke(any(InvokeRequest.class))).thenThrow(SdkClientException.create("lost reply"));
        assertThat(new LambdaDownstream(client, "network").decide(request).decision()).isEqualTo(IssuerDecision.NO_RESPONSE);
        verify(client, times(1)).invoke(any(InvokeRequest.class));
    }
    @Test void functionErrorsAndInvalidPayloadsRemainUnconfirmed() {
        var client = mock(LambdaClient.class);
        for (var response : List.of(InvokeResponse.builder().statusCode(200).functionError("Unhandled").build(),
                InvokeResponse.builder().statusCode(200).payload(SdkBytes.fromUtf8String("not JSON")).build())) {
            when(client.invoke(any(InvokeRequest.class))).thenReturn(response);
            assertThat(new LambdaDownstream(client, "network").decide(request).decision()).isEqualTo(IssuerDecision.NO_RESPONSE);
        }
    }
    Map<String, Object> event(String subject, String body) {
        var claims = subject == null ? Map.of() : Map.of("sub", subject);
        return Map.of("version", "2.0", "rawPath", "/authorizations", "body", body,
                "requestContext", Map.of("http", Map.of("method", "POST"), "authorizer", Map.of("jwt", Map.of("claims", claims))));
    }
    String call(ProcessorHandler handler, Object event) throws IOException {
        var output = new ByteArrayOutputStream();
        handler.handleRequest(new ByteArrayInputStream(WireJson.write(event).getBytes(StandardCharsets.UTF_8)), output, null);
        return output.toString(StandardCharsets.UTF_8);
    }
    @Test void unauthenticatedOrUnregisteredCallerCannotSupplyTheirOwnContext() throws Exception {
        var handler = new ProcessorHandler(() -> { throw new AssertionError("must not initialize"); }, sub -> null);
        assertThat(WireJson.tree(call(handler, event(null, "{}"))).path("statusCode").asInt()).isEqualTo(403);
        assertThat(WireJson.tree(call(handler, event("unregistered", "{\"acquirerRef\":\"ACQ\"}"))).path("statusCode").asInt()).isEqualTo(403);
    }
    @Test void authenticatedInputUsesTrustedRegistryAndReturnsOnlyOutcomeAndReference() throws Exception {
        var context = new AuthorizedContext("A", "M", "SUB", Set.of(Route.DOMESTIC));
        var core = mock(AuthorizationService.class);
        when(core.authorize(eq(context), any())).thenReturn(new AuthorizationResult(Outcome.APPROVED, "AUTH-1"));
        var handler = new ProcessorHandler(() -> core, sub -> context);
        var purchase = new PurchaseRequest("M", "T", "P", new BigDecimal("10"), "USD", "SIM-APP-001",
                EntryMode.SIMULATED_CONTACT, CvmIndicator.SIMULATED_VERIFIED, true, null, null);
        var response = WireJson.tree(call(handler, event("SUB", WireJson.write(purchase))));
        assertThat(response.path("statusCode").asInt()).isEqualTo(200);
        assertThat(response.path("body").asText()).isEqualTo("{\"outcome\":\"APPROVED\",\"reference\":\"AUTH-1\"}");
        verify(core).authorize(context, purchase);
    }
    @Test void bodyCannotInjectFaultSelectionOrCallerPermissions() throws Exception {
        var core = mock(AuthorizationService.class);
        var handler = new ProcessorHandler(() -> core, sub -> new AuthorizedContext("A", "M", sub, Set.of()));
        var response = WireJson.tree(call(handler, event("SUB", "{\"networkFault\":\"DROP_RESPONSE\"}")));
        assertThat(response.path("statusCode").asInt()).isEqualTo(400);
        verifyNoInteractions(core);
    }
}
