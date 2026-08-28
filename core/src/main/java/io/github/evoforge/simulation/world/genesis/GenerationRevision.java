package io.github.evoforge.simulation.world.genesis;

/** Temporary visualizer compatibility identifier; production preview supports only accepted V15. */
public record GenerationRevision(String value) {
    public static final GenerationRevision V11 = new GenerationRevision("evoforge:worldgen-v11");
    public static final GenerationRevision V12 = new GenerationRevision("evoforge:worldgen-v12");
    public static final GenerationRevision V13 = new GenerationRevision("evoforge:worldgen-v13");
    public static final GenerationRevision V14 = new GenerationRevision("evoforge:worldgen-v14");
    public static final GenerationRevision V15 = new GenerationRevision("evoforge:worldgen-v15");

    public GenerationRevision {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("generation revision must not be blank");
        }
    }
}
