package io.github.evoforge.simulation.world.terrain.generation;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Strict loader/compiler for the deliberately small authored terrain-palette schema. */
public final class TerrainPaletteLoader {

    private static final Pattern KEY_PATTERN = Pattern.compile(
            "[a-z0-9][a-z0-9_.-]*:[a-z0-9][a-z0-9_.-]*");
    private static final Set<String> ROOT_FIELDS = Set.of(
            "key", "presets", "materials");
    private static final Set<String> MATERIAL_FIELDS = Set.of(
            "topsoil", "soil", "sand", "rock");

    private final TerrainPresetCatalog presets;

    public TerrainPaletteLoader() {
        this(TerrainPresetCatalog.standard());
    }

    public TerrainPaletteLoader(TerrainPresetCatalog presets) {
        if (presets == null) {
            throw new IllegalArgumentException("terrain preset catalog must not be null");
        }
        this.presets = presets;
    }

    public TerrainPalette load(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("terrain palette path must not be null");
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            return load(reader, path.toString());
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "failed to read terrain palette: " + path,
                    exception);
        }
    }

    public TerrainPalette load(Reader reader, String source) {
        if (reader == null) {
            throw new IllegalArgumentException("terrain palette reader must not be null");
        }
        String label = source == null ? "<terrain-palette>" : source;
        JsonElement rootElement;
        try {
            rootElement = JsonParser.parseReader(reader);
        } catch (RuntimeException exception) {
            throw invalid(label, "invalid JSON", exception);
        }
        if (!rootElement.isJsonObject()) {
            throw invalid(label, "root must be an object");
        }

        JsonObject root = rootElement.getAsJsonObject();
        rejectUnknown(root, ROOT_FIELDS, label, "root");
        String key = requireKey(root, "key", label);
        List<TerrainPreset> resolvedPresets = resolvePresets(
                requireArray(root, "presets", label),
                label);

        JsonElement materialsElement = root.get("materials");
        if (materialsElement == null || !materialsElement.isJsonObject()) {
            throw invalid(label, "materials must be an object");
        }
        JsonObject materials = materialsElement.getAsJsonObject();
        rejectUnknown(materials, MATERIAL_FIELDS, label, "materials");

        TerrainPaletteMaterials resolvedMaterials = new TerrainPaletteMaterials(
                TerrainMaterialKey.of(requireKey(materials, "topsoil", label)),
                TerrainMaterialKey.of(requireKey(materials, "soil", label)),
                TerrainMaterialKey.of(requireKey(materials, "sand", label)),
                TerrainMaterialKey.of(requireKey(materials, "rock", label)));

        return new TerrainPalette(key, resolvedPresets, resolvedMaterials);
    }

    private List<TerrainPreset> resolvePresets(JsonArray values, String source) {
        List<TerrainPreset> resolved = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        EnumMap<TerrainPresetCapability, String> capabilityOwners =
                new EnumMap<>(TerrainPresetCapability.class);

        for (JsonElement value : values) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw invalid(source, "preset entries must be strings");
            }
            String key = value.getAsString();
            TerrainPreset preset = presets.resolve(key);
            if (preset == null) {
                throw invalid(source, "unknown terrain preset: " + key);
            }
            if (!seen.add(key)) {
                throw invalid(source, "duplicate terrain preset: " + key);
            }
            String previous = capabilityOwners.putIfAbsent(
                    preset.capability(),
                    preset.key());
            if (previous != null) {
                throw invalid(
                        source,
                        "terrain preset capability conflict for "
                                + preset.capability()
                                + ": " + previous + " and " + preset.key());
            }
            resolved.add(preset);
        }
        return List.copyOf(resolved);
    }

    private static JsonArray requireArray(
            JsonObject object,
            String field,
            String source) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonArray()) {
            throw invalid(source, field + " must be an array");
        }
        return value.getAsJsonArray();
    }

    private static String requireKey(
            JsonObject object,
            String field,
            String source) {
        JsonElement value = object.get(field);
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw invalid(source, field + " must be a definition-key string");
        }
        String key = value.getAsString();
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw invalid(source, "invalid definition key for " + field + ": " + key);
        }
        return key;
    }

    private static void rejectUnknown(
            JsonObject object,
            Set<String> allowed,
            String source,
            String context) {
        for (String field : object.keySet()) {
            if (!allowed.contains(field)) {
                throw invalid(
                        source,
                        "unknown " + context + " field: " + field);
            }
        }
    }

    private static IllegalArgumentException invalid(
            String source,
            String message) {
        return new IllegalArgumentException(source + ": " + message);
    }

    private static IllegalArgumentException invalid(
            String source,
            String message,
            Throwable cause) {
        return new IllegalArgumentException(source + ": " + message, cause);
    }
}
