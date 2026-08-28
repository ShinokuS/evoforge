package io.github.evoforge.simulation.world.genesis;

import io.github.evoforge.simulation.definition.NormalizedValue;

/**
 * High-level authored intent for dedicated mountain morphology.
 *
 * <p>These values describe desired mountain character, not implementation thresholds. The mountain
 * stage calibrates them into world-specific coverage, widths, heights, elongation, slope character
 * and plateau policy before spatial synthesis begins.</p>
 */
public record MountainIntent(
        NormalizedValue abundance,
        NormalizedValue height,
        NormalizedValue scale,
        NormalizedValue chaininess,
        NormalizedValue peakSharpness,
        boolean plateausEnabled,
        NormalizedValue plateauProbability) {

    private static final NormalizedValue ZERO = NormalizedValue.ofPartsPerMillion(0);

    public MountainIntent {
        if (abundance == null
                || height == null
                || scale == null
                || chaininess == null
                || peakSharpness == null
                || plateauProbability == null) {
            throw new IllegalArgumentException("mountain intent values must not be null");
        }
    }

    /** Neutral mixed mountain character used by V13 tooling unless content says otherwise. */
    public static MountainIntent balanced() {
        return new MountainIntent(
                NormalizedValue.ofPartsPerMillion(350_000),
                NormalizedValue.ofPartsPerMillion(520_000),
                NormalizedValue.ofPartsPerMillion(500_000),
                NormalizedValue.ofPartsPerMillion(550_000),
                NormalizedValue.ofPartsPerMillion(600_000),
                true,
                NormalizedValue.ofPartsPerMillion(180_000));
    }

    /** Explicitly disables dedicated mountains while leaving V12 base morphology untouched. */
    public static MountainIntent none() {
        return new MountainIntent(
                ZERO,
                NormalizedValue.ofPartsPerMillion(520_000),
                NormalizedValue.ofPartsPerMillion(500_000),
                NormalizedValue.ofPartsPerMillion(550_000),
                NormalizedValue.ofPartsPerMillion(600_000),
                false,
                ZERO);
    }
}
