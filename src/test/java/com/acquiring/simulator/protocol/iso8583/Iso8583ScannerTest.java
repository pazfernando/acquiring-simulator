package com.acquiring.simulator.protocol.iso8583;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Drives {@link Iso8583Scanner} with the approved packed-fixture corpus.
 *
 * <p>The oracle is the immutable reviewed vector file; each fixture body is
 * rebuilt from its authoritative {@code hex} octets, not from the escaped
 * {@code ascii} rendering. Only the scanner-scope structural verdict is
 * asserted. Fixtures whose expected label belongs to the DE48 canonical schema,
 * domain validation, admission or trusted binding layers are recorded as
 * deferred and asserted to be structurally parseable, never as accepted
 * business outcomes.</p>
 */
class Iso8583ScannerTest {

    private static final Path PACKED =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/packed-vectors.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final int EXPECTED_CORPUS_SIZE = 85;

    /** Expected structural failure per scanner-scope negative fixture. */
    private enum Phase { HEADER, PRESENCE, FIELD }

    private record ExpectedFailure(Phase phase, String rule, int dataElement) {
        ExpectedFailure(Phase phase, String rule) {
            this(phase, rule, -1);
        }
    }

    private static final Map<String, ExpectedFailure> STRUCTURAL_NEGATIVES = Map.ofEntries(
            Map.entry("N-MTI", new ExpectedFailure(Phase.HEADER, "UNSUPPORTED_MTI")),
            Map.entry("N-bitmap", new ExpectedFailure(Phase.HEADER, "INVALID_BITMAP")),
            Map.entry("N-secondary", new ExpectedFailure(Phase.HEADER, "SECONDARY_BITMAP_FORBIDDEN")),
            Map.entry("B-DE2", new ExpectedFailure(Phase.HEADER, "UNSUPPORTED_FIELD")),
            Map.entry("B-DE55", new ExpectedFailure(Phase.HEADER, "UNSUPPORTED_FIELD")),
            Map.entry("N-missing-DE3",
                    new ExpectedFailure(Phase.PRESENCE, "MISSING_REQUIRED_FIELD", 3)),
            Map.entry("B-alias-12", new ExpectedFailure(Phase.FIELD, "LENGTH_OUT_OF_RANGE")),
            Map.entry("B-padded-terminal", new ExpectedFailure(Phase.FIELD, "INVALID_UPPER_ALNUM")),
            Map.entry("N-DE48-1000", new ExpectedFailure(Phase.FIELD, "INVALID_NUMERIC")),
            Map.entry("N-truncated", new ExpectedFailure(Phase.FIELD, "BODY_TOO_SHORT")),
            Map.entry("N-trailing", new ExpectedFailure(Phase.FIELD, "TRAILING_BYTES")),
            Map.entry("N-nonascii", new ExpectedFailure(Phase.FIELD, "NON_PRINTABLE_ASCII")));

    /**
     * Expected labels owned by later layers. A structural scan must neither
     * claim nor reject these; the table records where each verdict belongs.
     */
    private static final Map<String, String> DEFERRED_LABELS = new LinkedHashMap<>();

    static {
        DEFERRED_LABELS.put("SCHEMA_CANONICAL", "DE48 canonical key=value schema");
        DEFERRED_LABELS.put("SCHEMA_ENUM", "DE48 canonical schema/enum");
        DEFERRED_LABELS.put("SCHEMA_LENGTH", "DE48 canonical per-key bounds");
        DEFERRED_LABELS.put("DOMAIN_AMOUNT", "domain amount profile");
        DEFERRED_LABELS.put("DOMAIN_CURRENCY", "domain currency profile");
        DEFERRED_LABELS.put("DOMAIN_DATE", "domain Gregorian date");
        DEFERRED_LABELS.put("DOMAIN_DE7", "domain DE7/sent consistency");
        DEFERRED_LABELS.put("ADMISSION_FRESHNESS", "admission freshness");
        DEFERRED_LABELS.put("ADMISSION_RESPONSE_CAPACITY", "exchange admission budget");
        DEFERRED_LABELS.put("EVIDENCE_MATRIX", "P4 actor/stage/result matrix");
        DEFERRED_LABELS.put("BINDING_INSTRUCTION", "instruction binding");
        DEFERRED_LABELS.put("BINDING_AUTH_REFERENCE", "DE38 trusted-evidence binding");
        DEFERRED_LABELS.put("AUTH_ATTESTATION", "attestation and principal binding");
        DEFERRED_LABELS.put("BUSINESS_CONFLICT_QUARANTINE",
                "conditional business admission/quarantine");
    }

    // --- Corpus classification -------------------------------------------------

    static Stream<Arguments> acceptedFixtures() throws IOException {
        return vectors("ACCEPT").map(v -> Arguments.of(v.get("id").asText(), body(v)));
    }

    @ParameterizedTest(name = "{0} is structurally accepted")
    @MethodSource("acceptedFixtures")
    void acceptsEveryApprovedStructuralFixture(String id, String body) {
        Iso8583Message message = Iso8583Scanner.scan(body);

        assertThat(message.mti()).isIn("0100", "0110");
        assertThat(message.headerLength()).isEqualTo(Iso8583HeaderParser.HEADER_LENGTH);
        assertThat(message.bodyLength()).isEqualTo(body.length());
        assertThat(message.fields().keySet()).containsExactlyElementsOf(
                message.bitmap().setFields());
    }

    static Stream<Arguments> structuralNegativeFixtures() throws IOException {
        return vectors(null)
                .filter(v -> STRUCTURAL_NEGATIVES.containsKey(v.get("id").asText()))
                .map(v -> Arguments.of(
                        v.get("id").asText(),
                        body(v),
                        STRUCTURAL_NEGATIVES.get(v.get("id").asText())));
    }

    @ParameterizedTest(name = "{0} is rejected as {2}")
    @MethodSource("structuralNegativeFixtures")
    void rejectsScannerScopeNegativesWithTheirStructuralRule(
            String id, String body, ExpectedFailure expected) {
        Throwable thrown = catchThrowable(() -> Iso8583Scanner.scan(body));

        assertThat(thrown).as("%s must fail structurally", id).isNotNull();
        switch (expected.phase()) {
            case HEADER -> assertThat(thrown).isInstanceOfSatisfying(
                    Iso8583HeaderParseException.class,
                    e -> assertThat(e.rule().name()).isEqualTo(expected.rule()));
            case PRESENCE -> assertThat(thrown).isInstanceOfSatisfying(
                    Iso8583PresenceException.class,
                    e -> {
                        assertThat(e.rule().name()).isEqualTo(expected.rule());
                        assertThat(e.dataElement()).isEqualTo(expected.dataElement());
                    });
            case FIELD -> assertThat(thrown).isInstanceOfSatisfying(
                    Iso8583FieldException.class,
                    e -> assertThat(e.rule().name()).isEqualTo(expected.rule()));
        }
    }

    static Stream<Arguments> deferredFixtures() throws IOException {
        return vectors(null)
                .filter(v -> !"ACCEPT".equals(v.get("expected").asText()))
                .filter(v -> !STRUCTURAL_NEGATIVES.containsKey(v.get("id").asText()))
                .map(v -> Arguments.of(
                        v.get("id").asText(),
                        v.get("expected").asText(),
                        body(v)));
    }

    @ParameterizedTest(name = "{0} ({1}) is structurally parseable and deferred")
    @MethodSource("deferredFixtures")
    void defersNonStructuralNegativesAtTheStructuralLayer(
            String id, String expectedLabel, String body) {
        assertThat(DEFERRED_LABELS)
                .as("new expected label without a recorded owner layer: %s", expectedLabel)
                .containsKey(expectedLabel);

        Iso8583Message message = Iso8583Scanner.scan(body);
        assertThat(message.bodyLength()).isEqualTo(body.length());
        assertThat(message.fields().keySet()).containsExactlyElementsOf(
                message.bitmap().setFields());
    }

    @Test
    void corpusPartitionsIntoAcceptedStructuralNegativesAndDeferredFixtures() throws IOException {
        long accepted = count("ACCEPT");
        long structuralNegatives = vectors(null)
                .filter(v -> STRUCTURAL_NEGATIVES.containsKey(v.get("id").asText()))
                .count();
        long deferred = count(null) - accepted - structuralNegatives;

        assertThat(count(null)).as("approved packed corpus size").isEqualTo(EXPECTED_CORPUS_SIZE);
        assertThat(accepted).as("31 ACCEPT fixtures").isEqualTo(31);
        assertThat(structuralNegatives).as("scanner-scope structural negatives")
                .isEqualTo(STRUCTURAL_NEGATIVES.size());
        assertThat(accepted + structuralNegatives + deferred).isEqualTo(EXPECTED_CORPUS_SIZE);
        // Every fixture the scanner does not own is recorded, never skipped silently.
        assertThat(deferred).isEqualTo(EXPECTED_CORPUS_SIZE - 31 - STRUCTURAL_NEGATIVES.size());
    }

    // --- Decoded values ---------------------------------------------------------

    @Test
    void decodesRequestFieldsInAscendingBitmapOrder() throws IOException {
        Iso8583Message message = Iso8583Scanner.scan(body(fixture("V01-approved-request")));

        assertThat(message.mti()).isEqualTo("0100");
        assertThat(message.fields().keySet())
                .containsExactly(3, 4, 7, 11, 22, 25, 32, 37, 41, 42, 48, 49);
        assertThat(message.field(3)).isEqualTo("000000");
        assertThat(message.field(4)).isEqualTo("000000001000");
        assertThat(message.field(7)).isEqualTo("1118120000");
        assertThat(message.field(11)).isEqualTo("000001");
        assertThat(message.field(22)).isEqualTo("051");
        assertThat(message.field(25)).isEqualTo("00");
        assertThat(message.field(32)).isEqualTo("1");
        assertThat(message.field(37)).isEqualTo("000000000001");
        assertThat(message.field(41)).isEqualTo("TERM0001");
        assertThat(message.field(42)).isEqualTo("MERCH0000000001");
        assertThat(message.field(48)).startsWith("v=1;op=A;aid=AUTH-001;");
        assertThat(message.field(48)).endsWith("sent=20261118120000");
        assertThat(message.field(49)).isEqualTo("840");
        assertThat(message.field(38)).isNull();
        assertThat(message.field(39)).isNull();
    }

    @Test
    void decodesResponseFieldsIncludingTheConditionalDe38() throws IOException {
        Iso8583Message withReference =
                Iso8583Scanner.scan(body(fixture("V10-actual-DE38")));

        assertThat(withReference.mti()).isEqualTo("0110");
        assertThat(withReference.field(38)).isEqualTo("AB1234");
        assertThat(withReference.field(39)).isEqualTo("00");

        Iso8583Message withoutReference =
                Iso8583Scanner.scan(body(fixture("V02-approved-response")));
        assertThat(withoutReference.field(38)).isNull();
        assertThat(withoutReference.field(39)).isEqualTo("00");
    }

    @Test
    void decodedMessageIsImmutableAndAscending() throws IOException {
        Iso8583Message message = Iso8583Scanner.scan(body(fixture("V01-approved-request")));

        assertThat(catchThrowable(() -> message.fields().put(3, "999999")))
                .as("the decoded field map is read-only")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // --- Presence matrix --------------------------------------------------------

    @Test
    void requestPresenceMatrixRequiresItsFieldsAndForbidsResponseFields() throws IOException {
        String request = body(fixture("V01-approved-request"));

        assertPresenceFailure(withoutBit(request, 48),
                Iso8583PresenceException.Rule.MISSING_REQUIRED_FIELD, 48);
        assertPresenceFailure(withoutBit(request, 4),
                Iso8583PresenceException.Rule.MISSING_REQUIRED_FIELD, 4);
        // Ascending rule: a request missing both 4 and 11 reports the lowest one.
        assertPresenceFailure(withoutBit(withoutBit(request, 11), 4),
                Iso8583PresenceException.Rule.MISSING_REQUIRED_FIELD, 4);
        assertPresenceFailure(withBit(request, 38),
                Iso8583PresenceException.Rule.FORBIDDEN_FIELD_PRESENT, 38);
        assertPresenceFailure(withBit(request, 39),
                Iso8583PresenceException.Rule.FORBIDDEN_FIELD_PRESENT, 39);
    }

    @Test
    void responsePresenceMatrixRequiresDe39ButLeavesDe38Conditional() throws IOException {
        String response = body(fixture("V02-approved-response"));

        assertPresenceFailure(withoutBit(response, 39),
                Iso8583PresenceException.Rule.MISSING_REQUIRED_FIELD, 39);

        // DE38 presence is decided by trusted issuer evidence, never by the scanner.
        assertThat(Iso8583Scanner.scan(body(fixture("V02-approved-response"))).field(38))
                .isNull();
        assertThat(Iso8583Scanner.scan(body(fixture("V10-actual-DE38"))).field(38))
                .isEqualTo("AB1234");
    }

    // --- Package consistency and hard bounds ------------------------------------

    @Test
    void scannerEnabledFieldsMatchTheHeaderParserProfile() {
        assertThat(Iso8583DataElement.enabledFields())
                .containsExactlyInAnyOrderElementsOf(Iso8583HeaderParser.PROFILE_FIELDS);
    }

    @Test
    void rejectsNullTruncatedAndHeaderOnlyBodies() {
        assertThat(catchThrowable(() -> Iso8583Scanner.scan(null)))
                .isInstanceOfSatisfying(Iso8583HeaderParseException.class,
                        e -> assertThat(e.rule())
                                .isEqualTo(Iso8583HeaderParseException.Rule.BODY_TOO_SHORT));

        assertThat(catchThrowable(() -> Iso8583Scanner.scan("01003220048108C1800")))
                .isInstanceOfSatisfying(Iso8583HeaderParseException.class,
                        e -> assertThat(e.rule())
                                .isEqualTo(Iso8583HeaderParseException.Rule.BODY_TOO_SHORT));

        // The bitmap announces every required field but no field octets follow,
        // so strict body bounds fail at the first field after presence passes.
        assertThat(catchThrowable(() -> Iso8583Scanner.scan("01003220048108C18000")))
                .isInstanceOfSatisfying(Iso8583FieldException.class,
                        e -> assertThat(e.rule())
                                .isEqualTo(Iso8583FieldException.Rule.BODY_TOO_SHORT));
    }

    @Test
    void rejectsAnUnknownDataElementAskedForDirectly() {
        assertThat(catchThrowable(() -> Iso8583DataElement.forDe(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DE2");
    }

    // --- Helpers ----------------------------------------------------------------

    private static void assertPresenceFailure(
            String body, Iso8583PresenceException.Rule rule, int dataElement) {
        assertThat(catchThrowable(() -> Iso8583Scanner.scan(body)))
                .isInstanceOfSatisfying(Iso8583PresenceException.class, e -> {
                    assertThat(e.rule()).isEqualTo(rule);
                    assertThat(e.dataElement()).isEqualTo(dataElement);
                });
    }

    /** Clear one bitmap bit without touching the body octets. */
    private static String withoutBit(String body, int de) {
        return withBit(body, de, false);
    }

    private static String withBit(String body, int de) {
        return withBit(body, de, true);
    }

    private static String withBit(String body, int de, boolean set) {
        long bits = Long.parseUnsignedLong(body.substring(4, 20), 16);
        long mask = 1L << (64 - de);
        long updated = set ? (bits | mask) : (bits & ~mask);
        return body.substring(0, 4) + String.format("%016X", updated) + body.substring(20);
    }

    private static JsonNode fixture(String id) throws IOException {
        return vectors(null)
                .filter(v -> id.equals(v.get("id").asText()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("fixture not found: " + id));
    }

    private static long count(String expected) throws IOException {
        return vectors(expected).count();
    }

    private static Stream<JsonNode> vectors(String expected) throws IOException {
        JsonNode vectors = JSON.readTree(PACKED.toFile()).get("vectors");
        Stream<JsonNode> stream = StreamSupport.stream(vectors.spliterator(), false);
        return expected == null
                ? stream
                : stream.filter(v -> expected.equals(v.get("expected").asText()));
    }

    /** Rebuild the body from its authoritative hex octets. */
    private static String body(JsonNode vector) {
        byte[] octets = HexFormat.of().parseHex(vector.get("hex").asText());
        return new String(octets, StandardCharsets.ISO_8859_1);
    }
}
