package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/**
 * V14-specific base-terrain composition that reserves a genuine oceanic domain during landmass
 * selection rather than repainting already-generated terrain after the fact.
 */
public final class V14OceanicBaseTerrainGenerator implements ElevationGenerator {
    private final V12LandformCalibrator terrainCalibrator;
    private final V12LandformRecipe terrainRecipe;
    private final LandmassBoundaryCalibrator boundaryCalibrator;
    private final LandmassBoundaryRecipe boundaryRecipe;
    private final V12LandformElevationAlgorithm algorithm;

    public V14OceanicBaseTerrainGenerator(
            V12LandformCalibrator terrainCalibrator,
            V12LandformRecipe terrainRecipe,
            LandmassBoundaryCalibrator boundaryCalibrator,
            LandmassBoundaryRecipe boundaryRecipe) {
        this(
                terrainCalibrator,
                terrainRecipe,
                boundaryCalibrator,
                boundaryRecipe,
                new V12LandformElevationAlgorithm());
    }

    V14OceanicBaseTerrainGenerator(
            V12LandformCalibrator terrainCalibrator,
            V12LandformRecipe terrainRecipe,
            LandmassBoundaryCalibrator boundaryCalibrator,
            LandmassBoundaryRecipe boundaryRecipe,
            V12LandformElevationAlgorithm algorithm) {
        if (terrainCalibrator == null
                || terrainRecipe == null
                || boundaryCalibrator == null
                || boundaryRecipe == null
                || algorithm == null) {
            throw new IllegalArgumentException("V14 oceanic base-terrain dependencies must not be null");
        }
        this.terrainCalibrator = terrainCalibrator;
        this.terrainRecipe = terrainRecipe;
        this.boundaryCalibrator = boundaryCalibrator;
        this.boundaryRecipe = boundaryRecipe;
        this.algorithm = algorithm;
    }

    public static V14OceanicBaseTerrainGenerator standard() {
        return new V14OceanicBaseTerrainGenerator(
                V12LandformCalibrator.standard(),
                V12LandformRecipe.balanced(),
                LandmassBoundaryCalibrator.standard(),
                LandmassBoundaryRecipe.balanced());
    }

    @Override
    public ElevationField generate(WorldGenesis genesis) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");
        V12LandformCalibration terrain = terrainCalibrator.calibrate(genesis, terrainRecipe);
        LandmassBoundaryCalibration boundary = boundaryCalibrator.calibrate(genesis, boundaryRecipe);
        if (terrain == null || boundary == null) {
            throw new IllegalStateException("V14 oceanic base-terrain calibrator returned null");
        }
        return algorithm.generate(genesis, terrain, terrainRecipe, boundary);
    }
}
