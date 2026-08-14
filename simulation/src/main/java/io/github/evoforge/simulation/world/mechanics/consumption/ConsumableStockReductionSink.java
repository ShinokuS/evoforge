package io.github.evoforge.simulation.world.mechanics.consumption;

import io.github.evoforge.simulation.world.object.ObjectId;

/** Narrow notification boundary for mechanics that react when authoritative consumable stock decreases. */
@FunctionalInterface
public interface ConsumableStockReductionSink {
    void stockReduced(ObjectId objectId);
}
