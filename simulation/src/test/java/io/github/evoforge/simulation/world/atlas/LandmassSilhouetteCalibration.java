package io.github.evoforge.simulation.world.atlas;

/** Exact world-specific operating values for compact-region V14 landmass synthesis. */
public record LandmassSilhouetteCalibration(
        int scaffoldSpacingCells,
        int landClusterCount,
        int fragmentationPpm,
        int silhouetteInfluencePpm) {

    public LandmassSilhouetteCalibration {
        if (scaffoldSpacingCells < 3) {
            throw new IllegalArgumentException("landmass scaffold spacing must be at least three cells");
        }
        if (landClusterCount < 1) {
            throw new IllegalArgumentException("landmass cluster count must be positive");
        }
        if (fragmentationPpm < 0 || fragmentationPpm > 1_000_000
                || silhouetteInfluencePpm < 0 || silhouetteInfluencePpm > 1_000_000) {
            throw new IllegalArgumentException("landmass silhouette calibration must be normalized");
        }
    }
}
