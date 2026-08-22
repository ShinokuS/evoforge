package io.github.evoforge.simulation.agents.search;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.space.orientation.FacingDirection;

/** Developer-readable current/last local visual search step. */
public record AgentSearchTrace(ObjectId agentId, String providerId, String motivation,
        AgentSearchStatus status, int headingsObserved, FacingDirection facing) {
    public AgentSearchTrace {
        if (agentId == null || providerId == null || providerId.isBlank() || motivation == null || motivation.isBlank()
                || status == null || facing == null) throw new IllegalArgumentException("search trace values must not be null/blank");
        if (headingsObserved < 1) throw new IllegalArgumentException("headingsObserved must be >= 1");
    }
}
