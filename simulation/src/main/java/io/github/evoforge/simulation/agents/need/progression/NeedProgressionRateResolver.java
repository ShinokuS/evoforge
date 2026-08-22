package io.github.evoforge.simulation.agents.need.progression;

import io.github.evoforge.simulation.world.object.ObjectId;

/** Resolves the effective deficit increase for one scheduled progression interval. */
@FunctionalInterface
public interface NeedProgressionRateResolver {
    long resolve(ObjectId objectId, NeedProgressionDefinition definition);
}
