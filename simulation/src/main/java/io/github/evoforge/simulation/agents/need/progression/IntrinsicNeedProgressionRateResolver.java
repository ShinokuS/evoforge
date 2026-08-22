package io.github.evoforge.simulation.agents.need.progression;

import io.github.evoforge.simulation.world.object.ObjectId;

/** Current resolver: intrinsic definition amount is the effective progression amount. */
public final class IntrinsicNeedProgressionRateResolver implements NeedProgressionRateResolver {
    @Override
    public long resolve(ObjectId objectId, NeedProgressionDefinition definition) {
        if (objectId == null || definition == null) {
            throw new IllegalArgumentException("need progression resolver arguments must not be null");
        }
        return definition.baseAmount();
    }
}
