package io.github.evoforge.simulation.world.genesis;

/** Immutable provenance describing how one generated world came into existence. */
public record WorldGenesis(
        WorldSpec spec,
        long masterSeed,
        GenerationRevision generationRevision,
        RngRevision rngRevision,
        WorldGenerationIntent generationIntent) {

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
        if (generationIntent == null) {
            throw new IllegalArgumentException("generationIntent must not be null");
        }
    }

    /** Compatibility constructor. V1-V8 ignore macro generation intent. */
    public WorldGenesis(
            WorldSpec spec,
            long masterSeed,
            GenerationRevision generationRevision,
            RngRevision rngRevision) {
        this(spec, masterSeed, generationRevision, rngRevision, WorldGenerationIntent.balanced());
    }

    /**
     * Current production revision remains V7 until semantic world projection can supply physical
     * climate and runtime time scales automatically. Newer revisions remain explicit validation
     * targets until their complete generation contracts are promoted together.
     */
    public static WorldGenesis current(WorldSpec spec, long masterSeed) {
        return new WorldGenesis(
                spec,
                masterSeed,
                GenerationRevision.V7,
                RngRevision.V1,
                WorldGenerationIntent.balanced());
    }
}
