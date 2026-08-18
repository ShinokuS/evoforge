package io.github.evoforge.simulation.world.atlas;

/**
 * Exact world-specific operating parameters consumed by the V12 base-terrain algorithm.
 *
 * <p>This is generated complexity. It is never authored as content: normalized semantic intent is
 * resolved into these values by a {@link V12LandformCalibrator} before spatial generation begins.</p>
 */
public record V12LandformCalibration(
        int width,
        int height,
        int area,
        int landCount,
        int coherentLandmassScale,
        int fragmentedLandmassScale,
        int fragmentationPpm,
        int landformSpacing,
        int upliftScale,
        int ridgeScale,
        int rollingScale,
        int rollingDetailScale,
        int reliefPpm,
        int localReliefPpm,
        int ruggednessPpm,
        long maximumReadableStepSubunits) {

    public V12LandformCalibration {
        if (width <= 0 || height <= 0 || area <= 0) {
            throw new IllegalArgumentException("calibrated world dimensions must be positive");
        }
        if ((long) width * height != area) {
            throw new IllegalArgumentException("calibrated area must match width * height");
        }
        if (landCount < 0 || landCount > area) {
            throw new IllegalArgumentException("calibrated land count must fit world area");
        }
        if (coherentLandmassScale <= 0 || fragmentedLandmassScale <= 0
                || landformSpacing <= 0 || upliftScale <= 0 || ridgeScale <= 0
                || rollingScale <= 0 || rollingDetailScale <= 0) {
            throw new IllegalArgumentException("calibrated spatial scales must be positive");
        }
        requireNormalized(fragmentationPpm, "fragmentationPpm");
        requireNormalized(reliefPpm, "reliefPpm");
        requireNormalized(localReliefPpm, "localReliefPpm");
        requireNormalized(ruggednessPpm, "ruggednessPpm");
        if (maximumReadableStepSubunits <= 0L) {
            throw new IllegalArgumentException("maximumReadableStepSubunits must be positive");
        }
    }

    private static void requireNormalized(int value, String name) {
        if (value < 0 || value > 1_000_000) {
            throw new IllegalArgumentException(name + " must be in [0, 1_000_000]");
        }
    }
}
