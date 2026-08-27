package io.github.evoforge.simulation.world.genesis;

/** Stable identifier for a deterministic world-generation random algorithm. */
public record RngRevision(String value) {
    public static final RngRevision V1 = new RngRevision("evoforge:rng-v1");

    public RngRevision {
        value = GenesisKeyFormat.requireKey(value, "RNG revision");
    }

    public static RngRevision of(String value) {
        return new RngRevision(value);
    }
}
