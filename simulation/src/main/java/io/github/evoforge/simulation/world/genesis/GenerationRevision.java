package io.github.evoforge.simulation.world.genesis;

/** Stable identifier for the algorithm revision that authored world facts. */
public record GenerationRevision(String value) {
    public static final GenerationRevision V1 = new GenerationRevision("evoforge:worldgen-v1");
    public static final GenerationRevision V2 = new GenerationRevision("evoforge:worldgen-v2");

    public GenerationRevision {
        value = GenesisKeyFormat.requireKey(value, "generation revision");
    }

    public static GenerationRevision of(String value) {
        return new GenerationRevision(value);
    }
}
