package io.github.evoforge.simulation.world.terrain.definition;

import io.github.evoforge.simulation.definition.NormalizedValue;

/**
 * Semantic terrain controls preserved from the manually accepted V12-V15 generator.
 *
 * <p>This definition deliberately contains only authored meaning. Legacy fixed-point conversion and
 * algorithm constants live in the terrain genesis implementation, not in the public definition.</p>
 */
public record V15TerrainDefinition(
        NormalizedValue landCoverage,
        NormalizedValue landmassScale,
        NormalizedValue fragmentation,
        NormalizedValue relief,
        NormalizedValue localRelief,
        NormalizedValue landformScale,
        NormalizedValue ruggedness) {

    public V15TerrainDefinition {
        if (landCoverage == null
                || landmassScale == null
                || fragmentation == null
                || relief == null
                || localRelief == null
                || landformScale == null
                || ruggedness == null) {
            throw new IllegalArgumentException("terrain definition values must not be null");
        }
    }

    /** Exact semantic defaults used by the accepted legacy balanced V12-V15 intent. */
    public static V15TerrainDefinition balanced() {
        return new V15TerrainDefinition(
                NormalizedValue.of(0.50),
                NormalizedValue.of(0.50),
                NormalizedValue.of(0.50),
                NormalizedValue.of(0.60),
                NormalizedValue.of(0.45),
                NormalizedValue.of(0.50),
                NormalizedValue.of(0.35));
    }
}
