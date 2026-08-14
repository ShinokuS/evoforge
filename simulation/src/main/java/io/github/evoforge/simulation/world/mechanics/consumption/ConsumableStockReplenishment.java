package io.github.evoforge.simulation.world.mechanics.consumption;

import io.github.evoforge.simulation.world.object.ObjectId;

/** Narrow mutation capability for systems that legitimately restore consumable stock. */
@FunctionalInterface
public interface ConsumableStockReplenishment {
    long replenish(ObjectId objectId, long requestedQuantity);
}
