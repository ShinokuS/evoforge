package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/**
 * Temporary V15 revision-router facade retained while the rejected post-terrain basin experiment is
 * replaced inside the open Stage 2B draft.
 *
 * <p>The actual V15 owner is now {@link V15InlandLakeTerrainGenerator}: inland standing water is
 * authored as a Z=0 terrain domain before mountains and bathymetry. This facade exists only to keep
 * the small stable revision router unchanged during manual visual acceptance.</p>
 */
public final class V15InlandBasinTerrainGenerator implements ElevationGenerator {
    private final ElevationGenerator delegate;

    public V15InlandBasinTerrainGenerator(ElevationGenerator delegate) {
        if (delegate == null) throw new IllegalArgumentException("V15 compatibility delegate must not be null");
        this.delegate = delegate;
    }

    public static V15InlandBasinTerrainGenerator standard() {
        return new V15InlandBasinTerrainGenerator(V15InlandLakeTerrainGenerator.standard());
    }

    @Override
    public ElevationField generate(WorldGenesis genesis) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");
        return delegate.generate(genesis);
    }
}
