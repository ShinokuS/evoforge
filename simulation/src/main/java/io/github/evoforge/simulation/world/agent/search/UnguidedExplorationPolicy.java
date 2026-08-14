package io.github.evoforge.simulation.world.agent.search;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.spatial.orientation.FacingDirection;

/**
 * Chooses an egocentric heading when search has no landmark, map or other spatial guidance.
 * Implementations receive no world coordinates.
 */
public interface UnguidedExplorationPolicy {
    FacingDirection nextHeading(ObjectId agentId, FacingDirection previousHeading, long stepOrdinal);
}
