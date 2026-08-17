package io.github.evoforge.simulation.world.genesis;

/** Stable identifier for the algorithm revision that authored world facts. */
public record GenerationRevision(String value) {
    public static final GenerationRevision V1 = new GenerationRevision("evoforge:worldgen-v1");
    public static final GenerationRevision V2 = new GenerationRevision("evoforge:worldgen-v2");
    public static final GenerationRevision V3 = new GenerationRevision("evoforge:worldgen-v3");
    public static final GenerationRevision V4 = new GenerationRevision("evoforge:worldgen-v4");
    public static final GenerationRevision V5 = new GenerationRevision("evoforge:worldgen-v5");
    public static final GenerationRevision V6 = new GenerationRevision("evoforge:worldgen-v6");
    public static final GenerationRevision V7 = new GenerationRevision("evoforge:worldgen-v7");
    public static final GenerationRevision V8 = new GenerationRevision("evoforge:worldgen-v8");
    public static final GenerationRevision V9 = new GenerationRevision("evoforge:worldgen-v9");

    public GenerationRevision {
        value = GenesisKeyFormat.requireKey(value, "generation revision");
    }

    public static GenerationRevision of(String value) {
        return new GenerationRevision(value);
    }
}
