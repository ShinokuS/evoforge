package io.github.evoforge.simulation.world.calibration.rainfall;

import java.time.Duration;

/**
 * Algorithm-independent climatological statistics for alternating wet and dry spells.
 *
 * <p>These are observable climate characteristics, not parameters of a particular weather driver.
 * A runtime model may compile them into its own process parameters.</p>
 */
public record RainfallOccurrenceNormal(
        Duration meanDrySpellDuration,
        Duration meanWetSpellDuration) {

    public RainfallOccurrenceNormal {
        requirePositive(meanDrySpellDuration, "mean dry-spell duration");
        requirePositive(meanWetSpellDuration, "mean wet-spell duration");
    }

    private static void requirePositive(Duration value, String label) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }
}
