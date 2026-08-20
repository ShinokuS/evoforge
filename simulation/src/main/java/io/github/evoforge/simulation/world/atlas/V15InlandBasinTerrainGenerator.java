package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/**
 * V15 terrain composition: accepted V14 landmass, relief, mountains and bathymetry followed by
 * independent broad continental lowland/basin morphology.
 */
public final class V15InlandBasinTerrainGenerator implements ElevationGenerator {
    private final ElevationGenerator baseGenerator;
    private final InlandBasinMorphologyCalibrator calibrator;
    private final InlandBasinMorphologyRecipe recipe;
    private final InlandBasinMorphologyAlgorithm algorithm;

    public V15InlandBasinTerrainGenerator(
            ElevationGenerator baseGenerator,
            InlandBasinMorphologyCalibrator calibrator,
            InlandBasinMorphologyRecipe recipe,
            InlandBasinMorphologyAlgorithm algorithm) {
        if (baseGenerator == null || calibrator == null || recipe == null || algorithm == null) {
            throw new IllegalArgumentException("V15 inland basin terrain dependencies must not be null");
        }
        this.baseGenerator = baseGenerator;
        this.calibrator = calibrator;
        this.recipe = recipe;
        this.algorithm = algorithm;
    }

    public static V15InlandBasinTerrainGenerator standard() {
        return new V15InlandBasinTerrainGenerator(
                V14BathymetryTerrainGenerator.standard(),
                InlandBasinMorphologyCalibrator.standard(),
                InlandBasinMorphologyRecipe.balanced(),
                InlandBasinMorphologyAlgorithm.standard());
    }

    @Override
    public ElevationField generate(WorldGenesis genesis) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");
        ElevationField base = baseGenerator.generate(genesis);
        if (base == null || !genesis.spec().bounds().equals(base.bounds())) {
            throw new IllegalStateException("V15 base generator returned invalid elevation");
        }
        InlandBasinMorphologyCalibration calibration = calibrator.calibrate(base, recipe);
        if (calibration == null) {
            throw new IllegalStateException("V15 inland basin calibrator returned null");
        }
        ElevationField result = algorithm.generate(genesis, base, calibration, recipe);
        if (result == null || !base.bounds().equals(result.bounds())) {
            throw new IllegalStateException("V15 inland basin algorithm returned invalid elevation");
        }
        return result;
    }
}
