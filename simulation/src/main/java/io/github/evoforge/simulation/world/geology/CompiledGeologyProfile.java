package io.github.evoforge.simulation.world.geology;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validated deterministic geology content used by generation and later materialization. */
public final class CompiledGeologyProfile {
    private final String key;
    private final List<GeologyUnitKey> units;
    private final Map<GeologyUnitKey, GeologyMaterialKey> materials;

    CompiledGeologyProfile(
            String key,
            List<GeologyUnitKey> units,
            Map<GeologyUnitKey, GeologyMaterialKey> materials) {
        this.key = key;
        this.units = List.copyOf(units);
        this.materials = Map.copyOf(new LinkedHashMap<>(materials));
    }

    public String key() {
        return key;
    }

    /** Stable key-sorted unit order used by deterministic generation. */
    public List<GeologyUnitKey> units() {
        return units;
    }

    public GeologyMaterialKey materialFor(GeologyUnitKey unit) {
        GeologyMaterialKey material = materials.get(unit);
        if (material == null) {
            throw new IllegalArgumentException("unit is not part of geology profile: " + unit);
        }
        return material;
    }

    public Map<GeologyUnitKey, GeologyMaterialKey> materials() {
        return materials;
    }
}
