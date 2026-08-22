package io.github.evoforge.simulation.world.genesis;

import io.github.evoforge.simulation.definition.NormalizedValue;

/**
 * Human-authored definition of the desired large-scale terrain character.
 *
 * <p>Every numeric coordinate is semantic and normalized to {@code 0..1}. None of these values is
 * a cell count, height, slope, wavelength, probability threshold or physical constant. The world
 * generator owns the separate compilation of this definition into exact world-scale metrics.</p>
 */
public record WorldGenerationIntent(
        NormalizedValue landCoverage,
        NormalizedValue landmassScale,
        NormalizedValue fragmentation,
        NormalizedValue relief,
        NormalizedValue localRelief,
        NormalizedValue landformScale,
        NormalizedValue ruggedness,
        MountainIntent mountains) {

    public WorldGenerationIntent {
        if (landCoverage == null
                || landmassScale == null
                || fragmentation == null
                || relief == null
                || localRelief == null
                || landformScale == null
                || ruggedness == null
                || mountains == null) {
            throw new IllegalArgumentException("world generation definition values must not be null");
        }
    }
}
