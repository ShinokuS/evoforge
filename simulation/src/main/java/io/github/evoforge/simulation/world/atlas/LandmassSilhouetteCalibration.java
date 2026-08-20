package io.github.evoforge.simulation.world.atlas;

/** Exact world-specific operating values for geometric V14 landmass silhouettes. */
public record LandmassSilhouetteCalibration(
        int primaryBodyCount,
        int satelliteBodyCount,
        int primaryRadiusCells,
        int irregularityPpm,
        int silhouetteInfluencePpm) {

    public LandmassSilhouetteCalibration {
        if (primaryBodyCount <= 0 || satelliteBodyCount < 0 || primaryRadiusCells <= 0) {
            throw new IllegalArgumentException("landmass silhouette body calibration must be positive");
        }
        if (irregularityPpm < 0 || irregularityPpm > 1_000_000
                || silhouetteInfluencePpm < 0 || silhouetteInfluencePpm > 1_000_000) {
            throw new IllegalArgumentException("landmass silhouette calibration must be normalized");
        }
    }
}
