package io.github.evoforge.simulation.world.object.stock.growth;

import io.github.evoforge.simulation.world.object.ObjectId;

/** Baseline resolver used while no environmental growth conditions exist. */
public final class IntrinsicGrowthRateResolver implements GrowthRateResolver {
    @Override
    public long resolve(ObjectId objectId, GrowthDefinition definition) {
        if (objectId == null || definition == null) {
            throw new IllegalArgumentException("growth rate inputs must not be null");
        }
        return definition.baseAmount();
    }
}
