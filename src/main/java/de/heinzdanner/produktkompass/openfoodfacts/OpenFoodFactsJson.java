package de.heinzdanner.produktkompass.openfoodfacts;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OpenFoodFactsJson {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<LinkedHashMap<String, String>> ROW_TYPE = new TypeReference<>() {};

    private OpenFoodFactsJson() {}

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    public static Map<String, String> readRow(String json) {
        return json == null ? new LinkedHashMap<>() : MAPPER.readValue(json, ROW_TYPE);
    }

    public static OpenFoodFactsMetadata readMetadata(String json) {
        return MAPPER.readValue(json, OpenFoodFactsMetadata.class);
    }
}
