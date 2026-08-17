package io.github.evoforge.simulation.world.terrain.generation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Strict parser for authored semantic terrain material-set bindings. */
public final class TerrainMaterialSetLoader {
    private static final Set<String> ROOT_FIELDS = Set.of("key", "bindings");
    private static final Set<String> BINDING_FIELDS = Set.of(
            "surface", "subsurface", "sediment", "bedrock");

    public TerrainMaterialSetDefinition load(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("terrain material-set path must not be null");
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            return load(reader, path.toString());
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "failed to read terrain material set: " + path,
                    exception);
        }
    }

    public TerrainMaterialSetDefinition load(Reader reader, String source) {
        JsonObject root = parseRoot(reader, source);
        String label = source == null ? "<terrain-material-set>" : source;
        TerrainDefinitionJson.rejectUnknown(root, ROOT_FIELDS, label, "root");

        String key = TerrainDefinitionJson.requireKey(root, "key", label);
        JsonObject bindingsObject = TerrainDefinitionJson.requireObject(
                root, "bindings", label);
        TerrainDefinitionJson.rejectUnknown(
                bindingsObject, BINDING_FIELDS, label, "bindings");

        Map<TerrainMaterialRole, TerrainMaterialKey> bindings =
                new EnumMap<>(TerrainMaterialRole.class);
        for (String field : bindingsObject.keySet()) {
            TerrainMaterialRole role = TerrainMaterialRole.fromAuthoredName(field);
            if (role == null) {
                throw TerrainDefinitionJson.invalid(
                        label, "unknown terrain material role: " + field);
            }
            String materialKey = TerrainDefinitionJson.requireKey(
                    bindingsObject, field, label);
            bindings.put(role, TerrainMaterialKey.of(materialKey));
        }
        return new TerrainMaterialSetDefinition(key, bindings);
    }

    private static JsonObject parseRoot(Reader reader, String source) {
        if (reader == null) {
            throw new IllegalArgumentException("terrain material-set reader must not be null");
        }
        String label = source == null ? "<terrain-material-set>" : source;
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
