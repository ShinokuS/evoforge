package io.github.evoforge.simulation.world.genesis;

import io.github.evoforge.simulation.definition.NormalizedValue;

/**
 * High-level authored intent for world generation.
 *
 * <p>The values describe desired outcomes and spatial character, not implementation thresholds or
 * physical constants. Generation stages are responsible for calibrating their algorithms to these
 * coordinates. {@code relief} controls macro elevation structure, while {@code localRelief}
 * controls the strength of smaller regional hills and depressions without directly selecting a
 * noise frequency or vertical amplitude.</p>
 */
public record WorldGenerationIntent(
        NormalizedValue landCoverage,
        NormalizedValue landmassScale,
        NormalizedValue fragmentation,
        NormalizedValue relief,
        NormalizedValue localRelief) {

    public WorldGenerationIntent {
        if (landCoverage == null
                || landmassScale == null
                || fragmentation == null
                || relief == null
                || localRelief == null) {
            throw new IllegalArgumentException("world generation intent values must not be null");
        }
    }

    /** Compatibility constructor for V11 and older callers that predate local relief. */
    public WorldGenerationIntent(
            NormalizedValue landCoverage,
            NormalizedValue landmassScale,
            NormalizedValue fragmentation,
            NormalizedValue relief) {
        this(
                landCoverage,
                landmassScale,
                fragmentation,
                relief,
                NormalizedValue.ofPartsPerMillion(0));
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
                NormalizedValue.ofPartsPerMillion(500_000),
                NormalizedValue.ofPartsPerMillion(0));
    }

    /** Neutral intent used by compatibility constructors and simple tooling. */
    public static WorldGenerationIntent balanced() {
        return new WorldGenerationIntent(
                NormalizedValue.ofPartsPerMillion(500_000),
                NormalizedValue.ofPartsPerMillion(500_000),
                NormalizedValue.ofPartsPerMillion(500_000),
                NormalizedValue.ofPartsPerMillion(500_000),
                NormalizedValue.ofPartsPerMillion(350_000));
    }
}
