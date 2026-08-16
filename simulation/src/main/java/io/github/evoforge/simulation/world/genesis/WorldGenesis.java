package io.github.evoforge.simulation.world.genesis;

/** Immutable provenance describing how one generated world came into existence. */
public record WorldGenesis(
        WorldSpec spec,
        long masterSeed,
        GenerationRevision generationRevision,
        RngRevision rngRevision) {

    public WorldGenesis {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (generationRevision == null) {
            throw new IllegalArgumentException("generationRevision must not be null");
        }
        if (rngRevision == null) {
            throw new IllegalArgumentException("rngRevision must not be null");
        }
    }

    public static WorldGenesis current(WorldSpec spec, long masterSeed) {
        return new WorldGenesis(
                spec,
                masterSeed,
                GenerationRevision.V4,
                RngRevision.V1);
    }
}
