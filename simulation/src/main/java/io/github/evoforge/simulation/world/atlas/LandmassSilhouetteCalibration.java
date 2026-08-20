package io.github.evoforge.simulation.world.atlas;

/** Exact world-specific operating values for plate-scaffold V14 landmass synthesis. */
public record LandmassSilhouetteCalibration(
        int plateSpacingCells,
        int correlationPasses,
        int fragmentationPpm,
        int silhouetteInfluencePpm) {

    public LandmassSilhouetteCalibration {
        if (plateSpacingCells < 3) {
            throw new IllegalArgumentException("landmass plate spacing must be at least three cells");
        }
        if (correlationPasses < 0) {
            throw new IllegalArgumentException("landmass correlation passes must be non-negative");
        }
        if (fragmentationPpm < 0 || fragmentationPpm > 1_000_000
                || silhouetteInfluencePpm < 0 || silhouetteInfluencePpm > 1_000_000) {
            throw new IllegalArgumentException("landmass silhouette calibration must be normalized");
        }
    }
}
