package io.github.evoforge.simulation.world.object.stock.growth;

import io.github.evoforge.simulation.world.object.ObjectId;

/** Resolves effective growth for one scheduled interval from intrinsic growth and current world conditions. */
@FunctionalInterface
public interface GrowthRateResolver {
    long resolve(ObjectId objectId, GrowthDefinition definition);
}
