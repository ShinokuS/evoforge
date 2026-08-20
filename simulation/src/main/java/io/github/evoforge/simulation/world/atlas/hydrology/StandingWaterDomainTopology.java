package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable per-body oceanic/inland domain roles for standing water. */
public interface StandingWaterDomainTopology {
    WorldBounds bounds();

    int bodyCount();

    StandingWaterDomainRole role(int bodyId);

    default boolean isOceanic(int bodyId) {
        return role(bodyId) == StandingWaterDomainRole.OCEANIC;
    }

    default int oceanicBodyCount() {
        int count = 0;
        for (int bodyId = 0; bodyId < bodyCount(); bodyId++) {
            if (isOceanic(bodyId)) count++;
        }
        return count;
    }

    default int inlandBodyCount() {
        return bodyCount() - oceanicBodyCount();
    }
}
