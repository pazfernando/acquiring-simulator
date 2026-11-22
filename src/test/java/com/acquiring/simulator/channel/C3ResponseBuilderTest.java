package com.acquiring.simulator.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.channel.C3Response.HttpStatus;
import com.acquiring.simulator.channel.C3ResponseBuilder.AsyncHealth;
import com.acquiring.simulator.channel.C3ResponseBuilder.AttemptIdentifiers;
import com.acquiring.simulator.channel.C3ResponseBuilder.Denial;
import com.acquiring.simulator.channel.C3ResponseBuilder.Evidence;
import com.acquiring.simulator.channel.C3ResponseBuilder.Facts;
import com.acquiring.simulator.channel.C3ResponseBuilder.Identifiers;
import com.acquiring.simulator.channel.C3ResponseBuilder.InvocationIdentifiers;
import com.acquiring.simulator.channel.C3ResponseBuilder.Origin;
import com.acquiring.simulator.channel.C3ResponseBuilder.PreAdmissionFailure;
import com.acquiring.simulator.channel.C3ResponseBuilder.UnsentCause;
import com.acquiring.simulator.channel.C3ResponseEnvelope.Disposition;
import com.acquiring.simulator.channel.C3ResponseEnvelope.ErrorCode;
import com.acquiring.simulator.channel.C3ResponseEnvelope.Observation;
import com.acquiring.simulator.channel.C3ResponseEnvelope.Outcome;
import com.acquiring.simulator.channel.C3ResponseEnvelope.ServerFreshness;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Oracles for the pure C3 response precedence builder.
 *
 * <p>The 26 approved {@code response-vectors.json} cases are the authority. Each case is
 * translated into trusted internal facts (durable state, restart knowledge, this-exchange
 * observation and the authentication decision); the expected JSON/status is never passed
 * into the builder. The asserted canonical body must match the fixture's {@code bodyUtf8}
 * byte for byte, and the status must match the fixture's numeric status.</p>
 *
 * <p>No HTTP, storage, notification, authentication or cryptographic seam is exercised.
 * All times are the fixed fixture instant; no wall clock is read.</p>
 */
class C3ResponseBuilderTest {

    private static final Path VECTORS = Path.of(
            "docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/response-vectors.json");

    private static final Instant AS_OF = Instant.parse("2026-11-18T12:00:05Z");

    private static final AttemptIdentifiers ATTEMPT =
            new AttemptIdentifiers("ATT-002", "CORR-002");

    private static final Identifiers INITIAL = new Identifiers(
            "AUTH-001", "TX-001", "ATT-001", "CORR-001", "CORR-001");

    private static final Identifiers DUPLICATE = new Identifiers(
            "AUTH-001", "TX-001", "ATT-002", "CORR-002", "CORR-001");

    @Test
    void matchesAllApprovedResponseVectorOracles() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(Files.readString(VECTORS));
        JsonNode cases = root.get("cases");
        assertThat(cases).as("approved cases").isNotNull();
        assertThat(cases.size()).as("approved case count").isEqualTo(26);

        int checked = 0;
        for (JsonNode testCase : cases) {
            String id = testCase.get("id").asText();
            C3Response actual = C3ResponseBuilder.build(factsFor(id));

            assertThat(actual.httpStatus().code())
                    .as(id + " HTTP status")
                    .isEqualTo(testCase.get("httpStatus").asInt());
            assertThat(actual.envelope().toCanonicalJson())
                    .as(id + " canonical body")
                    .isEqualTo(testCase.get("bodyUtf8").asText());
            checked++;
        }
        assertThat(checked).as("oracle cases executed").isEqualTo(26);
    }

    @Test
    void envelopeIsExactlyTheClosedThirteenKeySet() throws IOException {
        C3Response response = C3ResponseBuilder.build(
                confirmed(DUPLICATE, AuthorizationState.APPROVED, Origin.DUPLICATE,
                        AsyncHealth.healthy()));

        Set<String> keys = new LinkedHashSet<>();
        new ObjectMapper().readTree(response.envelope().toCanonicalJson())
                .fieldNames().forEachRemaining(keys::add);

        assertThat(keys).hasSize(13).containsExactlyElementsOf(C3ResponseEnvelope.KEYS);
        assertThat(keys).doesNotContain("authenticated", "retryAfter", "retryable");
        assertThat(response.envelope().utf8Length())
                .isLessThanOrEqualTo(C3ResponseEnvelope.MAX_UTF8_BYTES);
    }

    @Test
    void denialWinsWithoutLeakingAPosteriorConfirmation() {
        Facts facts = new Facts(Denial.CONTEXT_DENIED, null, false,
                new Evidence.ConfirmedAuthorization(AuthorizationState.APPROVED, 2, DUPLICATE,
                        AsyncHealth.healthy()),
                Origin.DUPLICATE, ATTEMPT, AS_OF);

        C3Response response = C3ResponseBuilder.build(facts);

        assertThat(response.httpStatus().code()).isEqualTo(403);
        assertThat(response.envelope().authorizationId()).isNull();
        assertThat(response.envelope().transactionId()).isNull();
        assertThat(response.envelope().originalCorrelationId()).isNull();
        assertThat(response.envelope().businessVersion()).isNull();
        assertThat(response.envelope().outcome()).isNull();
        assertThat(response.envelope().disposition()).isEqualTo(Disposition.REJECTED);
        assertThat(response.envelope().error()).isEqualTo(ErrorCode.CONTEXT_DENIED);
        assertThat(response.envelope().observation()).isEqualTo(Observation.UNKNOWN);
        assertThat(response.envelope().serverFreshness()).isEqualTo(ServerFreshness.UNKNOWN);
    }

    @Test
    void unauthorizedStatusIsSelectedByTheDenialFactAlone() {
        C3Response response = C3ResponseBuilder.build(facts(new Evidence.UnreadableStore(
                InvocationIdentifiers.none("CORR-002")), Denial.UNAUTHENTICATED));

        assertThat(response.httpStatus().code()).isEqualTo(401);
        assertThat(response.envelope().error()).isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void unknownHistoryTargetIs404AndDisclosesNothing() {
        Facts facts = new Facts(Denial.NONE, null, true,
                new Evidence.UnreadableStore(InvocationIdentifiers.none("CORR-002")),
                Origin.HISTORY_READ, ATTEMPT, AS_OF);

        C3Response response = C3ResponseBuilder.build(facts);

        assertThat(response.httpStatus().code()).isEqualTo(404);
        assertThat(response.envelope().disposition()).isEqualTo(Disposition.UNAVAILABLE);
        assertThat(response.envelope().error()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(response.envelope().authorizationId()).isNull();
        assertThat(response.envelope().businessVersion()).isNull();
    }

    @Test
    void preAdmissionFailuresMapToTheirExactStatusAndCode() {
        record Expected(PreAdmissionFailure failure, int status,
                C3ResponseEnvelope.ErrorCode code) {
        }
        List<Expected> expected = List.of(
                new Expected(PreAdmissionFailure.INVALID_INPUT, 400, ErrorCode.INVALID_INPUT),
                new Expected(PreAdmissionFailure.INVALID_REFERENCE, 400,
                        ErrorCode.INVALID_REFERENCE),
                new Expected(PreAdmissionFailure.PAYLOAD_CONFLICT, 409,
                        ErrorCode.PAYLOAD_CONFLICT),
                new Expected(PreAdmissionFailure.BODY_TOO_LARGE, 413, ErrorCode.BODY_TOO_LARGE),
                new Expected(PreAdmissionFailure.UNSUPPORTED_MEDIA, 415,
                        ErrorCode.UNSUPPORTED_MEDIA));

        for (Expected e : expected) {
            Facts facts = new Facts(Denial.NONE, e.failure(), false,
                    new Evidence.UnreadableStore(InvocationIdentifiers.none("CORR-002")),
                    Origin.SUBMISSION, ATTEMPT, AS_OF);
            C3Response response = C3ResponseBuilder.build(facts);

            assertThat(response.httpStatus().code()).as(e.failure().name()).isEqualTo(e.status());
            assertThat(response.envelope().error()).as(e.failure().name()).isEqualTo(e.code());
            assertThat(response.envelope().outcome()).isNull();
            assertThat(response.envelope().disposition()).isEqualTo(Disposition.REJECTED);
        }
    }

    @Test
    void envelopeRejectsAPaymentOutcomeOnAnUnavailableRow() {
        assertThatThrownBy(() -> new C3ResponseEnvelope("1", null, null, null, "CORR-002", null,
                Outcome.APPROVED, Disposition.UNAVAILABLE, null, AS_OF,
                Observation.UNKNOWN, ServerFreshness.UNKNOWN,
                ErrorCode.BUSINESS_JOURNAL_UNAVAILABLE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void envelopeRejectsNonPositiveVersionAndUnknownProfileIdentifiers() {
        assertThatThrownBy(() -> new C3ResponseEnvelope("1", "AUTH-001", "TX-001", "ATT-002",
                "CORR-002", "CORR-001", Outcome.APPROVED, Disposition.REPLAY, 0L, AS_OF,
                Observation.COMPLETE, ServerFreshness.CURRENT, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new C3ResponseEnvelope("1", "AUTH 001", "TX-001", "ATT-002",
                "CORR-002", "CORR-001", Outcome.APPROVED, Disposition.REPLAY, 2L, AS_OF,
                Observation.COMPLETE, ServerFreshness.CURRENT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownObservationForcesUnknownFreshness() {
        assertThatThrownBy(() -> new C3ResponseEnvelope("1", null, null, null, "CORR-002", null,
                null, Disposition.UNAVAILABLE, null, AS_OF,
                Observation.UNKNOWN, ServerFreshness.CURRENT,
                ErrorCode.BUSINESS_JOURNAL_UNAVAILABLE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Facts factsFor(String id) {
        return switch (id) {
            case "H01" -> confirmed(DUPLICATE, AuthorizationState.APPROVED, Origin.DUPLICATE,
                    AsyncHealth.healthy());
            case "H02" -> confirmed(DUPLICATE, AuthorizationState.APPROVED, Origin.DUPLICATE,
                    AsyncHealth.healthy().withoutEventHistory());
            case "H03" -> confirmed(DUPLICATE, AuthorizationState.APPROVED, Origin.DUPLICATE,
                    AsyncHealth.healthy().lostStoreAfterRead());
            case "H04" -> confirmed(INITIAL, AuthorizationState.APPROVED, Origin.SUBMISSION,
                    AsyncHealth.healthy().withoutLiveNotifications());
            case "H05" -> unsentClaim(DUPLICATE, Origin.DUPLICATE);
            case "H06" -> noClaimUnsent(UnsentCause.ADMISSION_UNAVAILABLE);
            case "H07" -> facts(new Evidence.PossibleSendStoreUnavailable(1L, DUPLICATE),
                    Origin.DUPLICATE);
            case "H08" -> readablePossibleSend(INITIAL, Origin.SUBMISSION,
                    Evidence.Progress.ACTIVE, AsyncHealth.healthy());
            case "H09" -> readablePossibleSend(DUPLICATE, Origin.DUPLICATE,
                    Evidence.Progress.ACTIVE, AsyncHealth.healthy());
            case "H10" -> readablePossibleSend(INITIAL, Origin.SUBMISSION,
                    Evidence.Progress.DEADLINE, AsyncHealth.healthy());
            case "H11" -> readablePossibleSend(DUPLICATE, Origin.DUPLICATE,
                    Evidence.Progress.DEADLINE, AsyncHealth.healthy());
            case "H12" -> unreadable(InvocationIdentifiers.none("CORR-002"));
            case "H13" -> unreadable(new InvocationIdentifiers(
                    "AUTH-001", "TX-001", "ATT-002", "CORR-002"));
            case "H14" -> new Facts(Denial.CONTEXT_DENIED, null, false,
                    new Evidence.UnreadableStore(InvocationIdentifiers.none("CORR-002")),
                    Origin.DUPLICATE, ATTEMPT, AS_OF);
            case "H15" -> new Facts(Denial.NONE, PreAdmissionFailure.INVALID_INPUT, false,
                    new Evidence.UnreadableStore(InvocationIdentifiers.none("CORR-002")),
                    Origin.SUBMISSION, ATTEMPT, AS_OF);
            case "H16" -> unsentClaim(DUPLICATE, Origin.HISTORY_READ);
            case "H17" -> confirmed(DUPLICATE, AuthorizationState.DECLINED, Origin.SUBMISSION,
                    AsyncHealth.healthy());
            case "H18" -> confirmed(DUPLICATE, AuthorizationState.NETWORK_REJECTED,
                    Origin.SUBMISSION, AsyncHealth.healthy());
            case "H19" -> confirmed(INITIAL, AuthorizationState.APPROVED, Origin.SUBMISSION,
                    AsyncHealth.healthy());
            case "H20" -> confirmed(DUPLICATE, AuthorizationState.APPROVED, Origin.SUBMISSION,
                    AsyncHealth.healthy().withLiveDeliveryGap());
            case "H21" -> noClaimUnsent(UnsentCause.ADMISSION_UNAVAILABLE);
            case "H22" -> noClaimUnsent(UnsentCause.CAPACITY);
            case "H23" -> readablePossibleSend(INITIAL, Origin.SUBMISSION,
                    Evidence.Progress.DEADLINE,
                    AsyncHealth.healthy().withoutLiveNotifications());
            case "H24" -> confirmed(DUPLICATE, AuthorizationState.APPROVED, Origin.SUBMISSION,
                    AsyncHealth.healthy().withLiveDeliveryGap());
            case "H25" -> confirmed(DUPLICATE, AuthorizationState.APPROVED, Origin.DUPLICATE,
                    AsyncHealth.healthy().withoutLiveNotifications());
            case "H26" -> unreadable(InvocationIdentifiers.none("CORR-002"));
            default -> throw new IllegalArgumentException("unmapped approved vector " + id);
        };
    }

    private static Facts confirmed(Identifiers identifiers, AuthorizationState state,
            Origin origin, AsyncHealth health) {
        return new Facts(Denial.NONE, null, false,
                new Evidence.ConfirmedAuthorization(state, 2L, identifiers, health),
                origin, new AttemptIdentifiers(identifiers.attemptId(),
                        identifiers.correlationId()), AS_OF);
    }

    private static Facts unsentClaim(Identifiers identifiers, Origin origin) {
        return new Facts(Denial.NONE, null, false,
                new Evidence.UnsentClaim(1L, identifiers), origin,
                new AttemptIdentifiers(identifiers.attemptId(), identifiers.correlationId()),
                AS_OF);
    }

    private static Facts noClaimUnsent(UnsentCause cause) {
        return new Facts(Denial.NONE, null, false, new Evidence.NoClaimUnsent(cause),
                Origin.SUBMISSION, ATTEMPT, AS_OF);
    }

    private static Facts readablePossibleSend(Identifiers identifiers, Origin origin,
            Evidence.Progress progress, AsyncHealth health) {
        return new Facts(Denial.NONE, null, false,
                new Evidence.ReadablePossibleSend(1L, identifiers, progress, health), origin,
                new AttemptIdentifiers(identifiers.attemptId(), identifiers.correlationId()),
                AS_OF);
    }

    private static Facts unreadable(InvocationIdentifiers identifiers) {
        return new Facts(Denial.NONE, null, false, new Evidence.UnreadableStore(identifiers),
                Origin.SUBMISSION, ATTEMPT, AS_OF);
    }

    private static Facts facts(Evidence evidence, Origin origin) {
        return new Facts(Denial.NONE, null, false, evidence, origin, ATTEMPT, AS_OF);
    }

    private static Facts facts(Evidence evidence, Denial denial) {
        return new Facts(denial, null, false, evidence, Origin.DUPLICATE, ATTEMPT, AS_OF);
    }

    // Compile-time sanity: the builder's status enum stays the approved transport set.
    private static final Set<HttpStatus> APPROVED_STATUSES = Set.of(
            HttpStatus.OK, HttpStatus.ACCEPTED, HttpStatus.BAD_REQUEST,
            HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND,
            HttpStatus.CONFLICT, HttpStatus.PAYLOAD_TOO_LARGE,
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, HttpStatus.SERVICE_UNAVAILABLE);
}
