package io.github.evoforge.simulation.genesis;

/**
 * Immutable input provenance for one generated world.
 *
 * <p>The seed and structural envelope are exact inputs. Terrain character remains human-authored
 * normalized intent; generator-specific metrics are deliberately absent from this record.</p>
 */
public record WorldGenesis(
        WorldSpec spec,
        long masterSeed,
        WorldGenerationIntent generationIntent) {

    public WorldGenesis {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (generationIntent == null) {
            throw new IllegalArgumentException("generationIntent must not be null");
        }
    }
}
