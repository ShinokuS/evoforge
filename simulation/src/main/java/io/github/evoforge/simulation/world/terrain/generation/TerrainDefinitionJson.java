package io.github.evoforge.simulation.world.terrain.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;
import java.util.regex.Pattern;

/** Small strict-JSON helpers shared by authored terrain definition loaders. */
final class TerrainDefinitionJson {
    private static final Pattern KEY_PATTERN = Pattern.compile(
            "[a-z0-9][a-z0-9_.-]*:[a-z0-9][a-z0-9_.-]*");

    private TerrainDefinitionJson() { }

    static void rejectUnknown(
            JsonObject object,
            Set<String> allowed,
            String source,
            String context) {
        for (String field : object.keySet()) {
            if (!allowed.contains(field)) {
                throw invalid(source, "unknown " + context + " field: " + field);
            }
        }
    }

    static JsonArray requireArray(JsonObject object, String field, String source) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonArray()) {
            throw invalid(source, field + " must be an array");
        }
        return value.getAsJsonArray();
    }

    static JsonObject requireObject(JsonObject object, String field, String source) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonObject()) {
            throw invalid(source, field + " must be an object");
        }
        return value.getAsJsonObject();
    }

    static String requireKey(JsonObject object, String field, String source) {
        JsonElement value = object.get(field);
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw invalid(source, field + " must be a definition-key string");
        }
        return requireKeyValue(value.getAsString(), field, source);
    }

    static String requireKeyValue(String key, String context, String source) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw invalid(source, "invalid definition key for " + context + ": " + key);
        }
        return key;
    }

    static IllegalArgumentException invalid(String source, String message) {
        return new IllegalArgumentException(source + ": " + message);
    }

    static IllegalArgumentException invalid(String source, String message, Throwable cause) {
        return new IllegalArgumentException(source + ": " + message, cause);
    }
}
