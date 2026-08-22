package io.github.evoforge.simulation.agents.perception.vision;

import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import java.util.Arrays;

/** Immutable-at-runtime visual-sense composition keyed by object definition. */
public final class VisionDefinitions {
    private VisionDefinition[] values = new VisionDefinition[16];
    private boolean frozen;

    public void put(ObjectDefinitionId id, VisionDefinition definition) {
        if (frozen) throw new IllegalStateException("vision definitions are frozen");
        if (id == null || definition == null) throw new IllegalArgumentException("vision definition values must not be null");
        int index = id.asInt();
        if (index >= values.length) values = Arrays.copyOf(values, Math.max(index + 1, values.length * 2));
        if (values[index] != null) throw new IllegalStateException("vision definition already exists: " + id);
        values[index] = definition;
    }

    public boolean has(ObjectDefinitionId id) {
        return id != null && id.asInt() < values.length && values[id.asInt()] != null;
    }

    public VisionDefinition get(ObjectDefinitionId id) {
        if (!has(id)) throw new IllegalArgumentException("vision definition not found: " + id);
        return values[id.asInt()];
    }

    public void freeze() { frozen = true; }
    public boolean isFrozen() { return frozen; }
}
