package io.github.evoforge.simulation.world.geology;

import java.util.List;

/** Strict authored geology content: identity plus eligible units and their material identities. */
public record GeologyProfileDefinition(
        String key,
        List<UnitDefinition> units) {

    public GeologyProfileDefinition {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("geology profile key must not be blank");
        }
        if (units == null || units.isEmpty()) {
            throw new IllegalArgumentException("geology profile must contain at least one unit");
        }
        units = List.copyOf(units);
    }

    public record UnitDefinition(
            GeologyUnitKey key,
            GeologyMaterialKey material) {
        public UnitDefinition {
            if (key == null || material == null) {
                throw new IllegalArgumentException("geology unit definition must not contain nulls");
            }
        }
    }
}
