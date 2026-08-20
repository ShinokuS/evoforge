package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/**
 * V15 terrain pipeline: accepted continent geometry, terrain-derived Z=0 inland lakes, accepted
 * structural mountains, then accepted standing-water bathymetry.
 *
 * <p>The class owns composition only. Lake placement, shoreline conditioning, mountain synthesis
 * and bathymetry remain independently replaceable algorithms.</p>
 */
public final class V15InlandLakeTerrainGenerator implements ElevationGenerator {
    private final ElevationGenerator delegate;

    public V15InlandLakeTerrainGenerator(ElevationGenerator delegate) {
        if (delegate == null) throw new IllegalArgumentException("V15 terrain delegate must not be null");
        this.delegate = delegate;
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
        return new V15InlandLakeTerrainGenerator(lakeAwareBathymetry);
    }

    @Override
    public ElevationField generate(WorldGenesis genesis) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");
        ElevationField result = delegate.generate(genesis);
        if (result == null || !genesis.spec().bounds().equals(result.bounds())) {
            throw new IllegalStateException("V15 inland lake terrain generator returned invalid elevation");
        }
        return result;
    }
}
