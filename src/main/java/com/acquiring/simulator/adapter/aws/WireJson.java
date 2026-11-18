package com.acquiring.simulator.adapter.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Explicit, versioned JSON transport; never Java object deserialization. */
public final class WireJson {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private WireJson() {}
    public static String write(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("cannot encode simulator contract", e); }
    }
    public static <T> T read(String json, Class<T> type) {
        try { return MAPPER.readValue(json, type); }
        catch (Exception e) { throw new IllegalArgumentException("invalid simulator contract", e); }
    }
    public static com.fasterxml.jackson.databind.JsonNode tree(String json) {
        try { return MAPPER.readTree(json); }
        catch (Exception e) { throw new IllegalArgumentException("invalid JSON", e); }
    }
}
