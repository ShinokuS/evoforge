package io.github.evoforge.simulation.world.mechanics.growth;

import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import java.util.Arrays;

/** Immutable-at-runtime growth composition keyed by object definition. */
public final class GrowthDefinitions {
    private GrowthDefinition[] values = new GrowthDefinition[16];
    private boolean frozen;

    public void put(ObjectDefinitionId id, GrowthDefinition definition) {
        if (frozen) throw new IllegalStateException("growth definitions are frozen");
        if (id == null || definition == null) throw new IllegalArgumentException("growth definition values must not be null");
        int index = id.asInt();
        if (index >= values.length) values = Arrays.copyOf(values, Math.max(index + 1, values.length * 2));
        if (values[index] != null) throw new IllegalStateException("growth definition already exists: " + id);
        values[index] = definition;
    }

    public boolean has(ObjectDefinitionId id) {
        return id != null && id.asInt() < values.length && values[id.asInt()] != null;
    }

    public GrowthDefinition get(ObjectDefinitionId id) {
        if (!has(id)) throw new IllegalArgumentException("growth definition not found: " + id);
        return values[id.asInt()];
    }

    public void freeze() { frozen = true; }
    public boolean isFrozen() { return frozen; }
}
