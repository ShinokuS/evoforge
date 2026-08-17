package io.github.evoforge.simulation.world.geology;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict JSON parser for compact authored geology profiles. */
public final class GeologyProfileLoader {
    private static final Set<String> PROFILE_FIELDS = Set.of("key", "units");
    private static final Set<String> UNIT_FIELDS = Set.of("key", "material");
    private static final Pattern KEY = Pattern.compile(
            "[a-z0-9][a-z0-9._-]*:[a-z0-9][a-z0-9._/-]*");

    public GeologyProfileDefinition load(Path path) {
        if (path == null) throw new IllegalArgumentException("path must not be null");
        try {
            return parse(Files.readString(path), path.toString());
        } catch (IOException ex) {
            throw new IllegalArgumentException("failed to read geology profile: " + path, ex);
        }
    }

    public GeologyProfileDefinition parse(String json, String source) {
        if (json == null) throw new IllegalArgumentException("json must not be null");
        String origin = source == null ? "<geology-profile>" : source;
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException ex) {
            throw invalid(origin, "invalid JSON", ex);
        }
        if (!root.isJsonObject()) throw invalid(origin, "root must be an object");
        JsonObject object = root.getAsJsonObject();
        rejectUnknown(object, PROFILE_FIELDS, origin, "profile");

        String key = requireKey(object, "key", origin);
        JsonElement unitsElement = object.get("units");
        if (unitsElement == null || !unitsElement.isJsonArray()) {
            throw invalid(origin, "units must be an array");
        }
        JsonArray unitsArray = unitsElement.getAsJsonArray();
        if (unitsArray.isEmpty()) throw invalid(origin, "units must not be empty");

        List<GeologyProfileDefinition.UnitDefinition> units = new ArrayList<>();
        for (int index = 0; index < unitsArray.size(); index++) {
            JsonElement unitElement = unitsArray.get(index);
            if (!unitElement.isJsonObject()) {
                throw invalid(origin, "units[" + index + "] must be an object");
            }
            JsonObject unit = unitElement.getAsJsonObject();
            rejectUnknown(unit, UNIT_FIELDS, origin, "unit");
            units.add(new GeologyProfileDefinition.UnitDefinition(
                    GeologyUnitKey.of(requireKey(unit, "key", origin)),
                    GeologyMaterialKey.of(requireKey(unit, "material", origin))));
        }
        return new GeologyProfileDefinition(key, units);
    }

    private static void rejectUnknown(
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

    private static String requireKey(JsonObject object, String field, String source) {
        JsonElement value = object.get(field);
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()
                || !KEY.matcher(value.getAsString()).matches()) {
            throw invalid(source, field + " must be a namespaced key string");
        }
        return value.getAsString();
    }

    private static IllegalArgumentException invalid(String source, String message) {
        return new IllegalArgumentException(source + ": " + message);
    }

    private static IllegalArgumentException invalid(
            String source,
            String message,
            Throwable cause) {
        return new IllegalArgumentException(source + ": " + message, cause);
    }
}
