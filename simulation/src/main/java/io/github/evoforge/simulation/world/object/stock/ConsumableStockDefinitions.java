package io.github.evoforge.simulation.world.object.stock;

import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import java.util.Arrays;

/** Immutable-at-runtime consumable stock composition keyed by object definition. */
public final class ConsumableStockDefinitions {
    private ConsumableStockDefinition[] values = new ConsumableStockDefinition[16];
    private boolean frozen;

    public void put(ObjectDefinitionId id, ConsumableStockDefinition definition) {
        if (frozen) throw new IllegalStateException("consumable stock definitions are frozen");
        if (id == null || definition == null) {
            throw new IllegalArgumentException("consumable stock definition values must not be null");
        }
        int index = id.asInt();
        if (index >= values.length) values = Arrays.copyOf(values, Math.max(index + 1, values.length * 2));
        if (values[index] != null) {
            throw new IllegalStateException("consumable stock definition already exists: " + id);
        }
        values[index] = definition;
    }

    public boolean has(ObjectDefinitionId id) {
        return id != null && id.asInt() < values.length && values[id.asInt()] != null;
    }

    public ConsumableStockDefinition get(ObjectDefinitionId id) {
        if (!has(id)) throw new IllegalArgumentException("consumable stock definition not found: " + id);
        return values[id.asInt()];
    }

    public void freeze() { frozen = true; }
    public boolean isFrozen() { return frozen; }
}
