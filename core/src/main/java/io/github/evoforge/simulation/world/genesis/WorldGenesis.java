package io.github.evoforge.simulation.world.genesis;

/** Minimal compatibility shell used only by the historical world-generation preview UI. */
public record WorldGenesis(
        WorldSpec spec,
        long masterSeed,
        GenerationRevision generationRevision,
        RngRevision rngRevision,
        WorldGenerationIntent generationIntent) {

    public WorldGenesis {
        if (spec == null
                || generationRevision == null
                || rngRevision == null
                || generationIntent == null) {
            throw new IllegalArgumentException("world preview genesis values must not be null");
        }
    }
}
