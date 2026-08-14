package io.github.evoforge.simulation.world.agent.search;

import io.github.evoforge.simulation.world.spatial.orientation.FacingDirection;

/** Egocentric request to expand a search into one adjacent locally traversable position. */
public record SearchRelocationRequest(FacingDirection heading) {
    public SearchRelocationRequest {
        if (heading == null) throw new IllegalArgumentException("heading must not be null");
    }
}
