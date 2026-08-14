package io.github.evoforge.simulation.world.agent.search;

import io.github.evoforge.simulation.world.spatial.orientation.FacingDirection;

/** Egocentric request to expand a search along one currently visible local leg. */
public record SearchRelocationRequest(FacingDirection heading, int distance) {
    public SearchRelocationRequest {
        if (heading == null) throw new IllegalArgumentException("heading must not be null");
        if (distance <= 0) throw new IllegalArgumentException("distance must be > 0");
    }
}
