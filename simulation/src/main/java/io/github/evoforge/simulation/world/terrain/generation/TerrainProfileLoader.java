package io.github.evoforge.simulation.world.terrain.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Strict parser for the deliberately small authored terrain-profile schema. */
public final class TerrainProfileLoader {
    private static final Set<String> ROOT_FIELDS = Set.of(
            "key", "presets", "materialSet");

    public TerrainProfileDefinition load(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("terrain profile path must not be null");
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            return load(reader, path.toString());
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "failed to read terrain profile: " + path,
                    exception);
        }
    }

    public TerrainProfileDefinition load(Reader reader, String source) {
        JsonObject root = parseRoot(reader, source);
        String label = source == null ? "<terrain-profile>" : source;
        TerrainDefinitionJson.rejectUnknown(root, ROOT_FIELDS, label, "root");

        String key = TerrainDefinitionJson.requireKey(root, "key", label);
        String materialSet = TerrainDefinitionJson.requireKey(root, "materialSet", label);
        JsonArray presetValues = TerrainDefinitionJson.requireArray(root, "presets", label);
        List<String> presetKeys = new ArrayList<>(presetValues.size());
        for (JsonElement value : presetValues) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw TerrainDefinitionJson.invalid(label, "preset entries must be strings");
            }
            presetKeys.add(TerrainDefinitionJson.requireKeyValue(
                    value.getAsString(),
                    "preset",
                    label));
        }
        return new TerrainProfileDefinition(key, presetKeys, materialSet);
    }

    private static JsonObject parseRoot(Reader reader, String source) {
        if (reader == null) {
            throw new IllegalArgumentException("terrain profile reader must not be null");
        }
        String label = source == null ? "<terrain-profile>" : source;
        JsonElement rootElement;
        try {
            rootElement = JsonParser.parseReader(reader);
        } catch (RuntimeException exception) {
            throw TerrainDefinitionJson.invalid(label, "invalid JSON", exception);
        }
        if (!rootElement.isJsonObject()) {
            throw TerrainDefinitionJson.invalid(label, "root must be an object");
        }
        return rootElement.getAsJsonObject();
    }
}
