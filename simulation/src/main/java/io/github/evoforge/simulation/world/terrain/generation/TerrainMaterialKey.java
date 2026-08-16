package io.github.evoforge.simulation.world.terrain.generation;

import java.util.regex.Pattern;

/** Stable semantic Landscape definition key used by generated terrain facts. */
public record TerrainMaterialKey(String value) {

    private static final Pattern PATTERN = Pattern.compile(
            "[a-z0-9][a-z0-9_.-]*:[a-z0-9][a-z0-9_.-]*");

    public TerrainMaterialKey {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "invalid terrain material definition key: " + value);
        }
    }

    public static TerrainMaterialKey of(String value) {
        return new TerrainMaterialKey(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
