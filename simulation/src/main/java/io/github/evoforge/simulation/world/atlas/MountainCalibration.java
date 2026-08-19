package io.github.evoforge.simulation.world.atlas;

/** Exact world-specific operating parameters consumed by the V13 mountain algorithm. */
public record MountainCalibration(
        int width,
        int height,
        int area,
        int candidateSpacingCells,
        int candidateActivationPpm,
        int typicalHalfWidthCells,
        long typicalUpliftSubunits,
        int ridgeHalfLengthCells,
        int peakSpacingCells,
        int peakSharpnessPpm,
        int sharpnessMilli,
        int branchProbabilityPpm,
        boolean plateausEnabled,
        int plateauProbabilityPpm,
        long baseTerrainCeilingSubunits,
        long mountainCeilingSubunits) {

    public MountainCalibration {
        if (width <= 0 || height <= 0 || area <= 0 || (long) width * height != area) {
            throw new IllegalArgumentException("mountain calibration dimensions must be positive and consistent");
        }
        if (candidateSpacingCells <= 0 || typicalHalfWidthCells <= 0
                || ridgeHalfLengthCells < 0 || peakSpacingCells <= 0 || sharpnessMilli <= 0) {
            throw new IllegalArgumentException("mountain calibrated spatial values are invalid");
        }
        requireNormalized(candidateActivationPpm, "candidateActivationPpm");
        requireNormalized(peakSharpnessPpm, "peakSharpnessPpm");
        requireNormalized(branchProbabilityPpm, "branchProbabilityPpm");
        requireNormalized(plateauProbabilityPpm, "plateauProbabilityPpm");
        if (typicalUpliftSubunits < 0L) {
            throw new IllegalArgumentException("typicalUpliftSubunits must be non-negative");
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
