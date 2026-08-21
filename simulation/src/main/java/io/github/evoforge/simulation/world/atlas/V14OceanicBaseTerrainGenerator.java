package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/**
 * V14-specific base-terrain composition that authors finite-world continent/island silhouettes
 * before the accepted V12 relief laws are synthesized.
 *
 * <p>Boundary policy owns only external-ocean safety and maximum land capacity. Silhouette
 * calibration and geometry are independently replaceable and own the actual geographic footprint.
 * The V12 elevation algorithm consumes that typed footprint but continues to own relief exactly as
 * before.</p>
 */
public final class V14OceanicBaseTerrainGenerator implements ElevationGenerator {
    private final V12LandformCalibrator terrainCalibrator;
    private final V12LandformRecipe terrainRecipe;
    private final LandmassBoundaryCalibrator boundaryCalibrator;
    private final LandmassBoundaryRecipe boundaryRecipe;
    private final LandmassSilhouetteCalibrator silhouetteCalibrator;
    private final LandmassSilhouetteRecipe silhouetteRecipe;
    private final LandmassSilhouetteAlgorithm silhouetteAlgorithm;
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
                LandmassSilhouetteCalibrator.standard(),
                LandmassSilhouetteRecipe.balanced(),
                LandmassSilhouetteAlgorithm.standard(),
                new V12LandformElevationAlgorithm());
    }

    V14OceanicBaseTerrainGenerator(
            V12LandformCalibrator terrainCalibrator,
            V12LandformRecipe terrainRecipe,
            LandmassBoundaryCalibrator boundaryCalibrator,
            LandmassBoundaryRecipe boundaryRecipe,
            V12LandformElevationAlgorithm algorithm) {
        this(
                terrainCalibrator,
                terrainRecipe,
                boundaryCalibrator,
                boundaryRecipe,
                LandmassSilhouetteCalibrator.standard(),
                LandmassSilhouetteRecipe.balanced(),
                LandmassSilhouetteAlgorithm.standard(),
                algorithm);
    }

    V14OceanicBaseTerrainGenerator(
            V12LandformCalibrator terrainCalibrator,
            V12LandformRecipe terrainRecipe,
            LandmassBoundaryCalibrator boundaryCalibrator,
            LandmassBoundaryRecipe boundaryRecipe,
            LandmassSilhouetteCalibrator silhouetteCalibrator,
            LandmassSilhouetteRecipe silhouetteRecipe,
            LandmassSilhouetteAlgorithm silhouetteAlgorithm,
            V12LandformElevationAlgorithm algorithm) {
        if (terrainCalibrator == null
                || terrainRecipe == null
                || boundaryCalibrator == null
                || boundaryRecipe == null
                || silhouetteCalibrator == null
                || silhouetteRecipe == null
                || silhouetteAlgorithm == null
                || algorithm == null) {
            throw new IllegalArgumentException("V14 oceanic base-terrain dependencies must not be null");
        }
        this.terrainCalibrator = terrainCalibrator;
        this.terrainRecipe = terrainRecipe;
        this.boundaryCalibrator = boundaryCalibrator;
        this.boundaryRecipe = boundaryRecipe;
        this.silhouetteCalibrator = silhouetteCalibrator;
        this.silhouetteRecipe = silhouetteRecipe;
        this.silhouetteAlgorithm = silhouetteAlgorithm;
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
        if (terrain == null) {
            throw new IllegalStateException("V14 terrain calibrator returned null");
        }
        LandmassBoundaryCalibration boundary = boundaryCalibrator.calibrate(
                genesis,
                terrain,
                boundaryRecipe);
        LandmassSilhouetteCalibration silhouetteCalibration = silhouetteCalibrator.calibrate(
                genesis,
                terrain,
                silhouetteRecipe);
        if (boundary == null || silhouetteCalibration == null) {
            throw new IllegalStateException("V14 landmass calibrator returned null");
        }
        LandmassSilhouette silhouette = silhouetteAlgorithm.generate(
                genesis,
                boundary,
                silhouetteCalibration,
                silhouetteRecipe);
        if (silhouette == null) {
            throw new IllegalStateException("V14 landmass silhouette algorithm returned null");
        }
        return algorithm.generate(genesis, terrain, terrainRecipe, silhouette);
    }
}
