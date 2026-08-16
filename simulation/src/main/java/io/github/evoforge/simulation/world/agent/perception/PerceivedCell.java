package io.github.evoforge.simulation.world.agent.perception;

/** One cell currently observed by the agent through a sensory mechanic. */
public record PerceivedCell(int x, int y, int z, int distance) {
    public PerceivedCell {
        if (distance < 0) {
            throw new IllegalArgumentException("distance must be >= 0");
        }
    }
}
