package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/**
 * Replaceable structural unit for the accepted V12 base terrain.
 *
 * <p>The public generator consumes semantic {@link WorldGenesis}, delegates semantic-to-operating
 * conversion to a calibrator, then delegates spatial synthesis to the V12 algorithm. Neither the
 * algorithm nor downstream consumers need to read human-authored intent directly.</p>
 */
public final class V12BaseTerrainGenerator implements ElevationGenerator {
    private final V12LandformCalibrator calibrator;
    private final V12LandformRecipe recipe;
    private final V12LandformElevationAlgorithm algorithm;

    public V12BaseTerrainGenerator(
            V12LandformCalibrator calibrator,
            V12LandformRecipe recipe) {
        this(calibrator, recipe, new V12LandformElevationAlgorithm());
    }

    V12BaseTerrainGenerator(
            V12LandformCalibrator calibrator,
            V12LandformRecipe recipe,
            V12LandformElevationAlgorithm algorithm) {
        if (calibrator == null || recipe == null || algorithm == null) {
            throw new IllegalArgumentException("V12 base-terrain dependencies must not be null");
        }
        this.calibrator = calibrator;
        this.recipe = recipe;
        this.algorithm = algorithm;
    }

    public static V12BaseTerrainGenerator standard() {
        return new V12BaseTerrainGenerator(
                V12LandformCalibrator.standard(),
                V12LandformRecipe.balanced());
    }

    @Override
    public ElevationField generate(WorldGenesis genesis) {
        V12LandformCalibration calibration = calibrator.calibrate(genesis, recipe);
        if (calibration == null) {
            throw new IllegalStateException("V12 landform calibrator returned null");
        }
        return algorithm.generate(genesis, calibration, recipe);
    }
}
