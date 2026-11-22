package com.acquiring.simulator.channel;

import com.acquiring.simulator.domain.model.CvmIndicator;
import com.acquiring.simulator.domain.model.EntryMode;
import com.acquiring.simulator.domain.model.RiskIndicator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure C1 ingress validator for one Operation A channel submission.
 *
 * <p>It accepts one UTF-8 JSON document of at most 4096 bytes and returns a
 * typed {@link ChannelAuthorizationRequest}. The closed schema is exactly the
 * C1 field list: no unknown, duplicate or null keys; no floats, numbers for
 * strings, or string coercion for the boolean; no non-ASCII values; and the
 * approved literals, reference bounds/alphabet and amount profile. Caller
 * identity, decision, route, generation, actor, fault and arbitrary ISO input
 * are simply not part of the declared key set, so any such key is rejected.</p>
 *
 * <p>This validator does not authenticate, authorize context, resolve a
 * registry fixture/mandate/route, allocate identifiers or aliases, read or write
 * history, claim, dispatch, build an ISO instruction, duplicate any SIM-ISO87A
 * validation, or decide a financial outcome. A successful result is typed input
 * only: later seams own all of those decisions.</p>
 *
 * <p>Checks run in one fixed deterministic order: body size, strict UTF-8/ASCII,
 * JSON structure with duplicate/unknown-key rejection, missing keys, then the
 * declared fields in the C1 order. The first failure is reported once.</p>
 */
public final class ChannelRequestValidator {

    /** Approved C1 body limit in bytes. */
    public static final int MAX_BODY_BYTES = 4096;

    /** Profile-alphabet reference characters shared with the approved profile. */
    public static final String REFERENCE_ALPHABET = "[A-Za-z0-9._:-]";

    /** Inclusive {@code correlationId} bound. */
    public static final int CORRELATION_ID_MAX_LENGTH = 64;

    /** Inclusive acquirer/merchant/terminal/payment reference bound. */
    public static final int CONTEXT_REF_MAX_LENGTH = 128;

    /** Inclusive credential fixture reference bound. */
    public static final int CREDENTIAL_FIXTURE_REF_MAX_LENGTH = 64;

    /** Inclusive prior authorization reference bound. */
    public static final int PRIOR_AUTHORIZATION_ID_MAX_LENGTH = 64;

    private static final List<String> REQUIRED_FIELDS = List.of(
            "contractVersion",
            "operation",
            "correlationId",
            "acquirerRef",
            "merchantRef",
            "terminalRef",
            "paymentRef",
            "credentialFixtureRef",
            "amount",
            "currency",
            "entryMode",
            "cvm",
            "cryptogramPresent");

    private static final Set<String> DECLARED_FIELDS = Set.of(
            "contractVersion",
            "operation",
            "correlationId",
            "acquirerRef",
            "merchantRef",
            "terminalRef",
            "paymentRef",
            "credentialFixtureRef",
            "amount",
            "currency",
            "entryMode",
            "cvm",
            "cryptogramPresent",
            "risk",
            "priorAuthorizationId");

    private static final ObjectMapper JSON = new ObjectMapper();

    private ChannelRequestValidator() {
    }

    /**
     * Validates one raw channel body.
     *
     * @param body UTF-8 JSON bytes; {@code null} or empty is invalid input
     * @return typed request built only from validated channel fields
     * @throws ChannelRequestValidationException on the first deterministic failure
     */
    public static ChannelAuthorizationRequest validate(byte[] body) {
        if (body == null || body.length == 0) {
            throw invalid("EMPTY_BODY", null);
        }
        if (body.length > MAX_BODY_BYTES) {
            throw new ChannelRequestValidationException(
                    ChannelErrorCode.BODY_TOO_LARGE, "BODY_TOO_LARGE", null);
        }
        String text = decodeAscii(body);
        Map<String, JsonNode> fields = parseObject(text);

        for (String required : REQUIRED_FIELDS) {
            if (!fields.containsKey(required)) {
                throw invalid("MISSING_FIELD", required);
            }
        }

        String contractVersion = textField(fields, "contractVersion");
        requireLiteral(contractVersion, "1", "contractVersion");
        String operation = textField(fields, "operation");
        requireLiteral(operation, "A", "operation");
        String correlationId = referenceField(
                fields, "correlationId", CORRELATION_ID_MAX_LENGTH);
        String acquirerRef = referenceField(fields, "acquirerRef", CONTEXT_REF_MAX_LENGTH);
        String merchantRef = referenceField(fields, "merchantRef", CONTEXT_REF_MAX_LENGTH);
        String terminalRef = referenceField(fields, "terminalRef", CONTEXT_REF_MAX_LENGTH);
        String paymentRef = referenceField(fields, "paymentRef", CONTEXT_REF_MAX_LENGTH);
        String credentialFixtureRef = referenceField(
                fields, "credentialFixtureRef", CREDENTIAL_FIXTURE_REF_MAX_LENGTH);
        String amount = textField(fields, "amount");
        long amountMinor = ChannelAmount.toMinorUnits(amount);
        String currency = textField(fields, "currency");
        requireLiteral(currency, "USD", "currency");
        EntryMode entryMode = entryMode(textField(fields, "entryMode"));
        CvmIndicator cvm = cvm(textField(fields, "cvm"));
        boolean cryptogramPresent = booleanField(fields, "cryptogramPresent");
        RiskIndicator risk = risk(optionalTextField(fields, "risk"));
        String priorAuthorizationId = optionalReferenceField(
                fields, "priorAuthorizationId", PRIOR_AUTHORIZATION_ID_MAX_LENGTH);

        return new ChannelAuthorizationRequest(
                contractVersion,
                operation,
                correlationId,
                acquirerRef,
                merchantRef,
                terminalRef,
                paymentRef,
                credentialFixtureRef,
                amountMinor,
                currency,
                entryMode,
                cvm,
                cryptogramPresent,
                risk == null ? RiskIndicator.PASS : risk,
                priorAuthorizationId);
    }

    /**
     * Convenience overload for an already-decoded body, encoded back to UTF-8
     * for the approved byte bound.
     */
    public static ChannelAuthorizationRequest validate(String body) {
        return validate(body == null ? null : body.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeAscii(byte[] body) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        String text;
        try {
            text = decoder.decode(ByteBuffer.wrap(body)).toString();
        } catch (CharacterCodingException e) {
            throw invalid("NON_ASCII_BODY", null);
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 0x7F) {
                throw invalid("NON_ASCII_BODY", null);
            }
        }
        return text;
    }

    /**
     * Reads the single root object with explicit duplicate and unknown-key
     * rejection, so the first-failure label does not depend on a parser message.
     * Any declared key whose value is an object or array is kept as a node and
     * rejected later by the type check for that field.
     */
    private static Map<String, JsonNode> parseObject(String text) {
        try (JsonParser parser = JSON.createParser(text)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw invalid("INVALID_JSON", null);
            }
            Map<String, JsonNode> fields = new LinkedHashMap<>();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String name = parser.currentName();
                if (!DECLARED_FIELDS.contains(name)) {
                    // Never echo the caller-supplied key.
                    throw invalid("UNKNOWN_FIELD", null);
                }
                if (fields.containsKey(name)) {
                    throw invalid("DUPLICATE_FIELD", name);
                }
                if (parser.nextToken() == null) {
                    throw invalid("INVALID_JSON", null);
                }
                fields.put(name, JSON.readTree(parser));
            }
            if (parser.currentToken() != JsonToken.END_OBJECT) {
                throw invalid("INVALID_JSON", null);
            }
            if (parser.nextToken() != null) {
                throw invalid("TRAILING_CONTENT", null);
            }
            return fields;
        } catch (IOException e) {
            throw invalid("INVALID_JSON", null);
        }
    }

    private static String textField(Map<String, JsonNode> fields, String field) {
        JsonNode value = requirePresent(fields, field);
        if (!value.isTextual()) {
            String rule = "amount".equals(field) ? "CHANNEL_TYPE_AMOUNT" : "WRONG_TYPE";
            throw invalid(rule, field);
        }
        return value.textValue();
    }

    private static String optionalTextField(Map<String, JsonNode> fields, String field) {
        JsonNode value = fields.get(field);
        if (value == null) {
            return null;
        }
        if (value.isNull()) {
            throw invalid("NULL_FIELD", field);
        }
        if (!value.isTextual()) {
            throw invalid("WRONG_TYPE", field);
        }
        return value.textValue();
    }

    private static boolean booleanField(Map<String, JsonNode> fields, String field) {
        JsonNode value = requirePresent(fields, field);
        if (!value.isBoolean()) {
            throw invalid("WRONG_TYPE", field);
        }
        return value.booleanValue();
    }

    private static JsonNode requirePresent(Map<String, JsonNode> fields, String field) {
        JsonNode value = fields.get(field);
        if (value == null) {
            throw invalid("MISSING_FIELD", field);
        }
        if (value.isNull()) {
            throw invalid("NULL_FIELD", field);
        }
        return value;
    }

    private static String referenceField(
            Map<String, JsonNode> fields, String field, int maxLength) {
        return requireReference(textField(fields, field), field, maxLength);
    }

    private static String optionalReferenceField(
            Map<String, JsonNode> fields, String field, int maxLength) {
        String value = optionalTextField(fields, field);
        return value == null ? null : requireReference(value, field, maxLength);
    }

    private static String requireReference(String value, String field, int maxLength) {
        if (value.isEmpty() || value.length() > maxLength) {
            throw invalid("INVALID_REFERENCE", field);
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == ':' || c == '-';
            if (!allowed) {
                throw invalid("INVALID_REFERENCE", field);
            }
        }
        return value;
    }

    private static void requireLiteral(String value, String literal, String field) {
        if (!literal.equals(value)) {
            throw invalid("INVALID_LITERAL", field);
        }
    }

    private static EntryMode entryMode(String value) {
        return switch (value) {
            case "simulated-contact" -> EntryMode.SIMULATED_CONTACT;
            case "simulated-contactless" -> EntryMode.SIMULATED_CONTACTLESS;
            default -> throw invalid("INVALID_LITERAL", "entryMode");
        };
    }

    private static CvmIndicator cvm(String value) {
        return switch (value) {
            case "simulated-verified" -> CvmIndicator.SIMULATED_VERIFIED;
            case "simulated-not-required" -> CvmIndicator.SIMULATED_NOT_REQUIRED;
            default -> throw invalid("INVALID_LITERAL", "cvm");
        };
    }

    private static RiskIndicator risk(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "pass" -> RiskIndicator.PASS;
            case "reject" -> RiskIndicator.REJECT;
            default -> throw invalid("INVALID_LITERAL", "risk");
        };
    }

    private static ChannelRequestValidationException invalid(String rule, String field) {
        return new ChannelRequestValidationException(ChannelErrorCode.INVALID_INPUT, rule, field);
    }
}
