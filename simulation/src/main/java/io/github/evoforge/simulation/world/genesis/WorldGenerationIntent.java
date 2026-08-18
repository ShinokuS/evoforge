package io.github.evoforge.simulation.world.genesis;

import io.github.evoforge.simulation.definition.NormalizedValue;

/**
 * High-level authored intent for macro world generation.
 *
 * <p>The values describe desired outcomes and spatial character, not implementation thresholds or
 * physical constants. Generation stages are responsible for calibrating their algorithms to these
 * coordinates.</p>
 */
public record WorldGenerationIntent(
        NormalizedValue landCoverage,
        NormalizedValue landmassScale,
        NormalizedValue fragmentation,
        NormalizedValue relief) {

    public WorldGenerationIntent {
        if (landCoverage == null
                || landmassScale == null
                || fragmentation == null
                || relief == null) {
            throw new IllegalArgumentException("world generation intent values must not be null");
        }
    }

    /** Compatibility constructor for V9 and older callers that do not author relief explicitly. */
    public WorldGenerationIntent(
            NormalizedValue landCoverage,
            NormalizedValue landmassScale,
            NormalizedValue fragmentation) {
        this(
                landCoverage,
                landmassScale,
                fragmentation,
                NormalizedValue.ofPartsPerMillion(500_000));
    }

    /** Neutral intent used by compatibility constructors and simple tooling. */
    public static WorldGenerationIntent balanced() {
        return new WorldGenerationIntent(
                NormalizedValue.ofPartsPerMillion(500_000),
                NormalizedValue.ofPartsPerMillion(500_000),
                NormalizedValue.ofPartsPerMillion(500_000),
                NormalizedValue.ofPartsPerMillion(500_000));
    }
}
