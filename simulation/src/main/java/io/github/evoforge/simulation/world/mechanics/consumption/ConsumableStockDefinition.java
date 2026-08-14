package io.github.evoforge.simulation.world.mechanics.consumption;

/** Immutable bounded quantity carried by a consumable object definition. */
public record ConsumableStockDefinition(long capacity, long initialQuantity) {
    public ConsumableStockDefinition {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        if (initialQuantity < 0 || initialQuantity > capacity) {
            throw new IllegalArgumentException("initialQuantity must be in [0, capacity]");
        }
    }
}
