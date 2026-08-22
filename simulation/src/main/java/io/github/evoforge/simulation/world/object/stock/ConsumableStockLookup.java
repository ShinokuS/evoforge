package io.github.evoforge.simulation.world.object.stock;

import io.github.evoforge.simulation.world.object.ObjectId;

/** Read-only observation of authoritative consumable quantity. */
public interface ConsumableStockLookup {
    boolean has(ObjectId objectId);
    long quantity(ObjectId objectId);
    long capacity(ObjectId objectId);
}
