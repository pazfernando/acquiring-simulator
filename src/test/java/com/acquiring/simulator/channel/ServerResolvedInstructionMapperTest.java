package com.acquiring.simulator.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.model.CvmIndicator;
import com.acquiring.simulator.domain.model.EntryMode;
import com.acquiring.simulator.domain.model.RiskIndicator;
import com.acquiring.simulator.domain.model.Route;
import com.acquiring.simulator.protocol.iso8583.Iso8583AdmissionMode;
import com.acquiring.simulator.protocol.iso8583.Iso8583RequestValidationException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Deterministic tests for the pure server-resolved instruction mapper.
 *
 * <p>Three bounded cases only: one exact-byte oracle for a new first dispatch,
 * one authorized-context mismatch refusal and one derived exchange-budget
 * refusal. The oracle case asserts the complete packed ASCII body against a
 * literal byte string composed independently from the approved profile, not
 * against a re-pack or a roundtrip through the packer. Counts: 1 exact-byte
 * oracle assertion, 0 roundtrip-body assertions.</p>
 *
 * <p>No test allocates an identifier, resolves a fixture/mandate/route,
 * authenticates, authorizes context, claims, persists, dispatches, sends, or
 * decides a financial outcome.</p>
 */
class ServerResolvedInstructionMapperTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-11-20T15:30:00Z");

    private static final String SENT = "20261120153000";

    /** Literal canonical body for the oracle case; independent of the packer. */
    private static final String EXPECTED_BODY =
            "0100"
                    + "3220048108C18000"
                    + "000000"
                    + "000000012345"
                    + "1120153000"
                    + "000123"
                    + "051"
                    + "00"
                    + "11"
                    + "12345678901"
                    + "123456789012"
                    + "TERM0001"
                    + "MERCHANT0000001"
                    + "206"
                    + "v=1;op=A;aid=AUTH-0001;cid=CORR-0001;tid=TXN-0001;att=ATT-0001"
                    + ";acq=ACQ-REF-001;mid=MID-REF-001;term=TERM-REF-001;pay=PAY-REF-001"
                    + ";gen=1;fixture=FIXTURE-001;cvm=VERIFIED;crypto=1;risk=PASS"
                    + ";sent=20261120153000"
                    + "840";

    @Test
    void mapsNewDispatchToTheExactByteOracle() {
        ChannelAuthorizationRequest request = request(
                "CORR-0001", "ACQ-REF-001", "MID-REF-001", "TERM-REF-001",
                "PAY-REF-001", "FIXTURE-001");
        ServerInstructionResolution resolution = resolution(
                context("ACQ-REF-001", "MID-REF-001"),
                "AUTH-0001", "TXN-0001", "ATT-0001", 1,
                "ACQ-REF-001", "MID-REF-001", "TERM-REF-001", "PAY-REF-001",
                "12345678901", "MERCHANT0000001", "TERM0001",
                "000123", "123456789012", "FIXTURE-001");

        ServerResolvedInstruction mapped = ServerResolvedInstructionMapper.map(
                request, resolution, Iso8583AdmissionMode.NEW_FIRST_DISPATCH, RECEIVED_AT);

        // Exact-byte oracle: the complete body equals the literal profile string.
        assertThat(mapped.body()).isEqualTo(EXPECTED_BODY);
        assertThat(mapped.length()).isEqualTo(EXPECTED_BODY.length());

        assertThat(mapped.de48()).isEqualTo(
                "v=1;op=A;aid=AUTH-0001;cid=CORR-0001;tid=TXN-0001;att=ATT-0001"
                        + ";acq=ACQ-REF-001;mid=MID-REF-001;term=TERM-REF-001;pay=PAY-REF-001"
                        + ";gen=1;fixture=FIXTURE-001;cvm=VERIFIED;crypto=1;risk=PASS"
                        + ";sent=20261120153000");
        assertThat(mapped.resolvedRoute()).isEqualTo(Route.DOMESTIC);
        assertThat(mapped.validation().mode())
                .isEqualTo(Iso8583AdmissionMode.NEW_FIRST_DISPATCH);

        Map<Integer, String> fields = mapped.fields();
        assertThat(fields).containsEntry(3, "000000");
        assertThat(fields).containsEntry(4, "000000012345");
        assertThat(fields).containsEntry(7, "1120153000");
        assertThat(fields).containsEntry(11, "000123");
        assertThat(fields).containsEntry(22, "051");
        assertThat(fields).containsEntry(25, "00");
        assertThat(fields).containsEntry(32, "12345678901");
        assertThat(fields).containsEntry(37, "123456789012");
        assertThat(fields).containsEntry(41, "TERM0001");
        assertThat(fields).containsEntry(42, "MERCHANT0000001");
        assertThat(fields).containsEntry(49, "840");
    }

    @Test
    void refusesAuthorizedContextThatDoesNotMatchTheRequest() {
        ChannelAuthorizationRequest request = request(
                "CORR-0001", "ACQ-REF-001", "MID-REF-001", "TERM-REF-001",
                "PAY-REF-001", "FIXTURE-001");
        ServerInstructionResolution resolution = resolution(
                context("ACQ-REF-001", "OTHER-MERCHANT-REF"),
                "AUTH-0001", "TXN-0001", "ATT-0001", 1,
                "ACQ-REF-001", "MID-REF-001", "TERM-REF-001", "PAY-REF-001",
                "12345678901", "MERCHANT0000001", "TERM0001",
                "000123", "123456789012", "FIXTURE-001");

        assertThatThrownBy(() -> ServerResolvedInstructionMapper.map(
                request, resolution, Iso8583AdmissionMode.NEW_FIRST_DISPATCH, RECEIVED_AT))
                .isInstanceOf(InstructionMappingException.class)
                .satisfies(failure -> {
                    InstructionMappingException rejection =
                            (InstructionMappingException) failure;
                    assertThat(rejection.rule())
                            .isEqualTo(InstructionMappingException.Rule.CONTEXT_MISMATCH);
                    assertThat(rejection.component()).isEqualTo("authorizedContext");
                    assertThat(rejection.getMessage())
                            .doesNotContain("OTHER-MERCHANT-REF")
                            .doesNotContain("MID-REF-001");
                });
    }

    @Test
    void refusesNewDispatchThatExceedsTheDerivedExchangeBudget() {
        String sixtyFour = "A".repeat(64);
        String oneTwentyEight = "A".repeat(128);
        ChannelAuthorizationRequest request = request(
                sixtyFour, oneTwentyEight, oneTwentyEight, oneTwentyEight,
                oneTwentyEight, sixtyFour);
        ServerInstructionResolution resolution = resolution(
                context(oneTwentyEight, oneTwentyEight),
                sixtyFour, sixtyFour, sixtyFour, 1,
                oneTwentyEight, oneTwentyEight, oneTwentyEight, oneTwentyEight,
                "12345678901", "MERCHANT0000001", "TERM0001",
                "000123", "123456789012", sixtyFour);

        assertThatThrownBy(() -> ServerResolvedInstructionMapper.map(
                request, resolution, Iso8583AdmissionMode.NEW_FIRST_DISPATCH, RECEIVED_AT))
                .isInstanceOf(InstructionMappingException.class)
                .satisfies(failure -> {
                    InstructionMappingException rejection =
                            (InstructionMappingException) failure;
                    assertThat(rejection.rule())
                            .isEqualTo(InstructionMappingException.Rule.VALIDATION_REJECTED);
                    assertThat(rejection.stage())
                            .isEqualTo(Iso8583RequestValidationException.Stage.BUDGET);
                    assertThat(rejection.failureLabel())
                            .isEqualTo("ADMISSION_RESPONSE_CAPACITY");
                });
    }

    private static ChannelAuthorizationRequest request(
            String correlationId,
            String acquirerRef,
            String merchantRef,
            String terminalRef,
            String paymentRef,
            String fixture) {
        return new ChannelAuthorizationRequest(
                "1",
                "A",
                correlationId,
                acquirerRef,
                merchantRef,
                terminalRef,
                paymentRef,
                fixture,
                12_345L,
                "USD",
                EntryMode.SIMULATED_CONTACT,
                CvmIndicator.SIMULATED_VERIFIED,
                true,
                RiskIndicator.PASS,
                null);
    }

    private static AuthorizedContext context(String acquirerRef, String merchantRef) {
        return new AuthorizedContext(
                acquirerRef, merchantRef, "SIMULATED_CALLER", Set.of(Route.DOMESTIC));
    }

    private static ServerInstructionResolution resolution(
            AuthorizedContext authorizedContext,
            String authorizationId,
            String transactionId,
            String instructionAttemptId,
            int generation,
            String acquirerRef,
            String merchantRef,
            String terminalRef,
            String paymentRef,
            String acquirerAlias,
            String merchantAlias,
            String terminalAlias,
            String stan,
            String rrn,
            String fixture) {
        return new ServerInstructionResolution(
                authorizedContext,
                authorizationId,
                transactionId,
                instructionAttemptId,
                generation,
                acquirerRef,
                merchantRef,
                terminalRef,
                paymentRef,
                acquirerAlias,
                merchantAlias,
                terminalAlias,
                stan,
                rrn,
                SENT,
                fixture,
                EntryMode.SIMULATED_CONTACT,
                CvmIndicator.SIMULATED_VERIFIED,
                true,
                RiskIndicator.PASS,
                12_345L,
                "USD",
                Route.DOMESTIC);
    }
}
