package com.amazon.tdm.s3a.service.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Utility class for JSON mapper.
 */
public final class JsonMapperUtil {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonMapperUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static String writeValueAsString(final Object value) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(value);
    }

    public static ObjectNode createObjectNode() {
        return OBJECT_MAPPER.createObjectNode();
    }

    public static JsonNode readTree(final String value) throws JsonProcessingException {
        return OBJECT_MAPPER.readTree(value);
    }

    public static JsonNode valueToTree(final Object value) {
        return OBJECT_MAPPER.valueToTree(value);
    }

    public static <T> T readValue(final String value, final Class<T> valueType) throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(value, valueType);
    }

}