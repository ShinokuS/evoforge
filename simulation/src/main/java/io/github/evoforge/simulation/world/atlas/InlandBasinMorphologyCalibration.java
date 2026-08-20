package io.github.evoforge.simulation.world.atlas;

/** Exact scale-aware operating values for inland-basin morphology in one generated world. */
public record InlandBasinMorphologyCalibration(
        int minimumRadiusCells,
        int maximumRadiusCells,
        int targetBasinCount,
        long minimumDepthSubunits,
        long maximumDepthSubunits) {

    public InlandBasinMorphologyCalibration {
        if (minimumRadiusCells < 2 || maximumRadiusCells < minimumRadiusCells) {
            throw new IllegalArgumentException("inland basin radius calibration is invalid");
        }
        if (targetBasinCount < 0) {
            throw new IllegalArgumentException("inland basin target count must be non-negative");
        }
        if (minimumDepthSubunits <= 0L || maximumDepthSubunits < minimumDepthSubunits) {
            throw new IllegalArgumentException("inland basin depth calibration is invalid");
        }
    }
}
