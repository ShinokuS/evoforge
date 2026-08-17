package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.climate.ClimateNormalsField;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.geology.GeologyField;

/** Durable generated world facts that exist before detailed world materialization. */
public final class WorldAtlas {
    private final WorldGenesis genesis;
    private final ElevationField elevation;
    private final GeologyField geology;
    private final ClimateNormalsField climateNormals;
    private final DrainageField drainage;
    private final SurfaceHydrologyField surfaceHydrology;

    WorldAtlas(
            WorldGenesis genesis,
            ElevationField elevation,
            GeologyField geology,
            ClimateNormalsField climateNormals,
            DrainageField drainage,
            SurfaceHydrologyField surfaceHydrology) {
        if (genesis == null
                || elevation == null
                || geology == null
                || climateNormals == null
                || drainage == null
                || surfaceHydrology == null) {
            throw new IllegalArgumentException("Atlas generated facts must not be null");
        }
        if (!genesis.spec().bounds().equals(elevation.bounds())) {
            throw new IllegalArgumentException("elevation bounds must match world genesis bounds");
        }
        if (!genesis.spec().bounds().equals(geology.bounds())) {
            throw new IllegalArgumentException("geology bounds must match world genesis bounds");
        }
        if (!genesis.spec().bounds().equals(climateNormals.bounds())) {
            throw new IllegalArgumentException("climate normals bounds must match world genesis bounds");
        }
        if (!genesis.spec().bounds().equals(drainage.bounds())) {
            throw new IllegalArgumentException("drainage bounds must match world genesis bounds");
        }
        if (!genesis.spec().bounds().equals(surfaceHydrology.bounds())) {
            throw new IllegalArgumentException(
                    "surface hydrology bounds must match world genesis bounds");
        }
        this.genesis = genesis;
        this.elevation = elevation;
        this.geology = geology;
        this.climateNormals = climateNormals;
        this.drainage = drainage;
        this.surfaceHydrology = surfaceHydrology;
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

    public ClimateNormalsField climateNormals() {
        return climateNormals;
    }

    public DrainageField drainage() {
        return drainage;
    }

    public SurfaceHydrologyField surfaceHydrology() {
        return surfaceHydrology;
    }
}
