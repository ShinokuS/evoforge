package io.github.evoforge.simulation.world.geology;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compiles authored geology content into deterministic key-sorted generation input. */
public final class GeologyProfileCompiler {
    public CompiledGeologyProfile compile(GeologyProfileDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("geology profile definition must not be null");
        }

        Map<GeologyUnitKey, GeologyMaterialKey> materials = new LinkedHashMap<>();
        for (GeologyProfileDefinition.UnitDefinition unit : definition.units()) {
            GeologyMaterialKey previous = materials.putIfAbsent(unit.key(), unit.material());
            if (previous != null) {
                throw new IllegalArgumentException(
                        "duplicate geology unit in profile " + definition.key() + ": " + unit.key());
            }
        }
        if (materials.size() > Character.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "geology profile exceeds supported unit count: " + materials.size());
        }

        List<GeologyUnitKey> units = new ArrayList<>(materials.keySet());
        units.sort(GeologyUnitKey::compareTo);
        return new CompiledGeologyProfile(definition.key(), units, materials);
    }
}
