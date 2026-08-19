package io.github.evoforge.simulation.world.atlas;

/** Exact world-specific operating parameters consumed by the V13 mountain algorithm. */
public record MountainCalibration(
        int width,
        int height,
        int area,
        int candidateSpacingCells,
        int candidateActivationPpm,
        int targetCoveragePpm,
        int typicalHalfWidthCells,
        int typicalLongAxisCells,
        long typicalUpliftSubunits,
        long worldHeightCapSubunits,
        long maximumCardinalRiseSubunits,
        int peakSharpnessPpm,
        int sharpnessMilli,
        boolean plateausEnabled,
        int plateauProbabilityPpm,
        int coastalTransitionCells,
        long shorelineUpliftSubunits,
        long baseTerrainCeilingSubunits,
        long mountainCeilingSubunits) {

    public MountainCalibration {
        if (width <= 0 || height <= 0 || area <= 0 || (long) width * height != area) {
            throw new IllegalArgumentException("mountain calibration dimensions must be positive and consistent");
        }
        if (candidateSpacingCells <= 0
                || typicalHalfWidthCells <= 0
                || typicalLongAxisCells < typicalHalfWidthCells
                || sharpnessMilli <= 0
                || coastalTransitionCells <= 0) {
            throw new IllegalArgumentException("mountain calibrated spatial values are invalid");
        }
        requireNormalized(candidateActivationPpm, "candidateActivationPpm");
        requireNormalized(targetCoveragePpm, "targetCoveragePpm");
        requireNormalized(peakSharpnessPpm, "peakSharpnessPpm");
        requireNormalized(plateauProbabilityPpm, "plateauProbabilityPpm");
        if (typicalUpliftSubunits < 0L
                || worldHeightCapSubunits < 0L
                || maximumCardinalRiseSubunits <= 0L
                || shorelineUpliftSubunits < 0L) {
            throw new IllegalArgumentException("mountain calibrated vertical values are invalid");
        }
        if (typicalUpliftSubunits > worldHeightCapSubunits) {
            throw new IllegalArgumentException("typical mountain uplift must not exceed the world-size height cap");
        }
        if (shorelineUpliftSubunits > typicalUpliftSubunits && typicalUpliftSubunits > 0L) {
            throw new IllegalArgumentException("shoreline mountain uplift must not exceed typical uplift");
        }
        if (baseTerrainCeilingSubunits <= 0L || mountainCeilingSubunits <= baseTerrainCeilingSubunits) {
            throw new IllegalArgumentException("mountain ceiling must leave positive headroom above V12 base terrain");
        }
    }

    private static void requireNormalized(int value, String name) {
        if (value < 0 || value > 1_000_000) {
            throw new IllegalArgumentException(name + " must be in [0, 1_000_000]");
        }
    }
}
