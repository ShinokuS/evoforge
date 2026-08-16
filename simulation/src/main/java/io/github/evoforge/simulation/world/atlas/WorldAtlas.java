package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.geology.GeologyField;

/** Durable generated world facts that exist before detailed world materialization. */
public final class WorldAtlas {
    private final WorldGenesis genesis;
    private final ElevationField elevation;
    private final GeologyField geology;
    private final DrainageField drainage;
    private final SurfaceHydrologyField surfaceHydrology;
    private final HydroClimateField hydroClimate;

    WorldAtlas(
            WorldGenesis genesis,
            ElevationField elevation,
            GeologyField geology,
            DrainageField drainage,
            SurfaceHydrologyField surfaceHydrology,
            HydroClimateField hydroClimate) {
        if (genesis == null
                || elevation == null
                || geology == null
                || drainage == null
                || surfaceHydrology == null
                || hydroClimate == null) {
            throw new IllegalArgumentException("Atlas generated facts must not be null");
        }
        if (!genesis.spec().bounds().equals(elevation.bounds())) {
            throw new IllegalArgumentException("elevation bounds must match world genesis bounds");
        }
        if (!genesis.spec().bounds().equals(geology.bounds())) {
            throw new IllegalArgumentException("geology bounds must match world genesis bounds");
        }
        if (!genesis.spec().bounds().equals(drainage.bounds())) {
            throw new IllegalArgumentException("drainage bounds must match world genesis bounds");
        }
        if (!genesis.spec().bounds().equals(surfaceHydrology.bounds())) {
            throw new IllegalArgumentException(
                    "surface hydrology bounds must match world genesis bounds");
        }
        if (!genesis.spec().bounds().equals(hydroClimate.bounds())) {
            throw new IllegalArgumentException("hydroClimate bounds must match world genesis bounds");
        }
        this.genesis = genesis;
        this.elevation = elevation;
        this.geology = geology;
        this.drainage = drainage;
        this.surfaceHydrology = surfaceHydrology;
        this.hydroClimate = hydroClimate;
    }

    public WorldGenesis genesis() {
        return genesis;
    }

    public ElevationField elevation() {
        return elevation;
    }

    public GeologyField geology() {
        return geology;
    }

    public DrainageField drainage() {
        return drainage;
    }

    public SurfaceHydrologyField surfaceHydrology() {
        return surfaceHydrology;
    }

    public HydroClimateField hydroClimate() {
        return hydroClimate;
    }
}
