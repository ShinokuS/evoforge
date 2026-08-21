package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/**
 * V15 terrain pipeline: accepted continent geometry, terrain-derived Z=0 inland lakes, accepted
 * structural mountains, accepted standing-water bathymetry, then inland-only depth refinement.
 *
 * <p>The class owns composition only. Lake placement, shoreline membership, mountain synthesis,
 * universal bathymetry and inland-lake depth remain independently replaceable responsibilities.</p>
 */
public final class V15InlandLakeTerrainGenerator implements ElevationGenerator {
    private final ElevationGenerator delegate;
    private final InlandLakeBathymetryAlgorithm inlandLakeBathymetry;
    private final InlandLakeBathymetryRecipe inlandLakeBathymetryRecipe;

    public V15InlandLakeTerrainGenerator(ElevationGenerator delegate) {
        this(delegate, InlandLakeBathymetryAlgorithm.standard(), InlandLakeBathymetryRecipe.balanced());
    }

    public V15InlandLakeTerrainGenerator(
            ElevationGenerator delegate,
            InlandLakeBathymetryAlgorithm inlandLakeBathymetry,
            InlandLakeBathymetryRecipe inlandLakeBathymetryRecipe) {
        if (delegate == null || inlandLakeBathymetry == null || inlandLakeBathymetryRecipe == null) {
            throw new IllegalArgumentException("V15 terrain dependencies must not be null");
        }
        this.delegate = delegate;
        this.inlandLakeBathymetry = inlandLakeBathymetry;
        this.inlandLakeBathymetryRecipe = inlandLakeBathymetryRecipe;
    }

    public static V15InlandLakeTerrainGenerator standard() {
        ElevationGenerator lakeAwareMountains = new V13MountainTerrainGenerator(
                V15InlandLakeBaseTerrainGenerator.standard(),
                MountainCalibrator.standard(),
                MountainRecipe.balanced());
        ElevationGenerator lakeAwareBathymetry = new V14BathymetryTerrainGenerator(
                lakeAwareMountains,
                BathymetryCalibrator.standard(),
                BathymetryRecipe.balanced());
        return new V15InlandLakeTerrainGenerator(
                lakeAwareBathymetry,
                InlandLakeBathymetryAlgorithm.standard(),
                InlandLakeBathymetryRecipe.balanced());
    }

    @Override
    public ElevationField generate(WorldGenesis genesis) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");
        ElevationField bathymetricTerrain = delegate.generate(genesis);
        if (bathymetricTerrain == null || !genesis.spec().bounds().equals(bathymetricTerrain.bounds())) {
            throw new IllegalStateException("V15 inland lake terrain delegate returned invalid elevation");
        }
        ElevationField result = inlandLakeBathymetry.generate(
                genesis,
                bathymetricTerrain,
                inlandLakeBathymetryRecipe);
        if (result == null || !genesis.spec().bounds().equals(result.bounds())) {
            throw new IllegalStateException("V15 inland lake bathymetry returned invalid elevation");
        }
        return result;
    }
}
