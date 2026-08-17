package io.github.evoforge.simulation.world.terrain.generation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves authored terrain references and validates preset/material-role composition. */
public final class TerrainProfileCompiler {
    private final TerrainPresetCatalog presets;

    public TerrainProfileCompiler() {
        this(TerrainPresetCatalog.standard());
    }

    public TerrainProfileCompiler(TerrainPresetCatalog presets) {
        if (presets == null) {
            throw new IllegalArgumentException("terrain preset catalog must not be null");
        }
        this.presets = presets;
    }

    public CompiledTerrainProfile compile(
            TerrainProfileDefinition profile,
            TerrainMaterialSetDefinition materialSet) {
        if (profile == null || materialSet == null) {
            throw new IllegalArgumentException("terrain compilation inputs must not be null");
        }
        if (!profile.materialSetKey().equals(materialSet.key())) {
            throw invalid(
                    profile,
                    "material set reference " + profile.materialSetKey()
                            + " does not match supplied material set " + materialSet.key());
        }

        List<TerrainPreset> resolved = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        EnumMap<TerrainPresetCapability, String> capabilityOwners =
                new EnumMap<>(TerrainPresetCapability.class);
        EnumSet<TerrainMaterialRole> requiredRoles = EnumSet.noneOf(TerrainMaterialRole.class);

        for (String presetKey : profile.presetKeys()) {
            if (!seen.add(presetKey)) {
                throw invalid(profile, "duplicate terrain preset: " + presetKey);
            }
            TerrainPreset preset = presets.resolve(presetKey);
            if (preset == null) {
                throw invalid(profile, "unknown terrain preset: " + presetKey);
            }
            String previous = capabilityOwners.putIfAbsent(
                    preset.capability(),
                    preset.key());
            if (previous != null) {
                throw invalid(
                        profile,
                        "terrain preset capability conflict for " + preset.capability()
                                + ": " + previous + " and " + preset.key());
            }
            requiredRoles.addAll(preset.requiredRoles());
            resolved.add(preset);
        }

        for (TerrainMaterialRole role : requiredRoles) {
            if (!materialSet.bindings().containsKey(role)) {
                throw invalid(
                        profile,
                        "terrain preset composition requires material role: "
                                + role.authoredName());
            }
        }

        return new CompiledTerrainProfile(
                profile.key(),
                resolved,
                new TerrainMaterialSet(materialSet.key(), materialSet.bindings()));
    }

    private static IllegalArgumentException invalid(
            TerrainProfileDefinition profile,
            String message) {
        return new IllegalArgumentException(
                "cannot compile terrain profile " + profile.key() + ": " + message);
    }
}
