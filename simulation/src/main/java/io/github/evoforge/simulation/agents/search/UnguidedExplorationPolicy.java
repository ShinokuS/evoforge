package io.github.evoforge.simulation.agents.search;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.space.orientation.FacingDirection;

/**
 * Chooses an egocentric exploration leg when search has no landmark, map or other spatial guidance.
 * Implementations receive no world coordinates; visualRange is only the observer-relative sensory horizon.
 */
public interface UnguidedExplorationPolicy {
    SearchRelocationRequest nextRelocation(
            ObjectId agentId,
            FacingDirection previousHeading,
            long legOrdinal,
            int visualRange);
}
