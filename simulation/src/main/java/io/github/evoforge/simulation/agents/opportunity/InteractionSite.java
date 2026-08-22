package io.github.evoforge.simulation.agents.opportunity;

/** Physical standing cell from which an agent can use one concrete opportunity. */
public record InteractionSite(int x, int y, int z, int distance) {
    public InteractionSite {
        if (distance < 0) {
            throw new IllegalArgumentException("distance must be >= 0");
        }
    }
}
