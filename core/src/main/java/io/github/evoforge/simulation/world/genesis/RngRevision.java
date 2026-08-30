package io.github.evoforge.simulation.world.genesis;

/** Temporary visualizer compatibility identifier for the accepted deterministic RNG contract. */
public record RngRevision(String value) {
    public static final RngRevision V1 = new RngRevision("evoforge:rng-v1");

    public RngRevision {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("RNG revision must not be blank");
        }
    }
}
