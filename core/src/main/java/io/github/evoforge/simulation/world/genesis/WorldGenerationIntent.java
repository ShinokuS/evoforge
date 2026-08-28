package io.github.evoforge.simulation.world.genesis;

import io.github.evoforge.simulation.definition.NormalizedValue;

/**
 * High-level authored intent for world generation.
 *
 * <p>The values describe desired outcomes and spatial character, not implementation thresholds or
 * physical constants. Generation stages are responsible for calibrating their algorithms to these
 * coordinates. {@code relief} controls large ordinary vertical structure, {@code localRelief}
 * controls rolling hills and depressions, {@code landformScale} controls their typical horizontal
 * size, {@code ruggedness} controls ridge prominence and tolerated local slope, and
 * {@code mountains} describes the separate dedicated mountain stage introduced after the accepted
 * V12 base morphology.</p>
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

    private static final NormalizedValue DEFAULT_LANDFORM_SCALE =
            NormalizedValue.ofPartsPerMillion(500_000);
    private static final NormalizedValue DEFAULT_RUGGEDNESS =
            NormalizedValue.ofPartsPerMillion(350_000);

    public WorldGenerationIntent {
        if (landCoverage == null
                || landmassScale == null
                || fragmentation == null
                || relief == null
                || localRelief == null
                || landformScale == null
                || ruggedness == null
                || mountains == null) {
            throw new IllegalArgumentException("world generation intent values must not be null");
        }
    }

    /** Compatibility constructor for callers that predate the dedicated V13 mountain stage. */
    public WorldGenerationIntent(
            NormalizedValue landCoverage,
            NormalizedValue landmassScale,
            NormalizedValue fragmentation,
            NormalizedValue relief,
            NormalizedValue localRelief,
            NormalizedValue landformScale,
            NormalizedValue ruggedness) {
        this(
                landCoverage,
                landmassScale,
                fragmentation,
                relief,
                localRelief,
                landformScale,
                ruggedness,
                MountainIntent.balanced());
    }

    /** Compatibility constructor for the first V12 drafts that predate terrain character controls. */
    public WorldGenerationIntent(
            NormalizedValue landCoverage,
            NormalizedValue landmassScale,
            NormalizedValue fragmentation,
            NormalizedValue relief,
            NormalizedValue localRelief) {
        this(
                landCoverage,
                landmassScale,
                fragmentation,
                relief,
                localRelief,
                DEFAULT_LANDFORM_SCALE,
                DEFAULT_RUGGEDNESS,
                MountainIntent.balanced());
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
                NormalizedValue.ofPartsPerMillion(0),
                DEFAULT_LANDFORM_SCALE,
                DEFAULT_RUGGEDNESS,
                MountainIntent.balanced());
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
                NormalizedValue.ofPartsPerMillion(0),
                DEFAULT_LANDFORM_SCALE,
                DEFAULT_RUGGEDNESS,
                MountainIntent.balanced());
    }

    /** Neutral intent used by compatibility constructors and simple tooling. */
    public static WorldGenerationIntent balanced() {
        return new WorldGenerationIntent(
                NormalizedValue.ofPartsPerMillion(500_000),
                NormalizedValue.ofPartsPerMillion(500_000),
                NormalizedValue.ofPartsPerMillion(500_000),
                NormalizedValue.ofPartsPerMillion(600_000),
                NormalizedValue.ofPartsPerMillion(450_000),
                DEFAULT_LANDFORM_SCALE,
                DEFAULT_RUGGEDNESS,
                MountainIntent.balanced());
    }
}
