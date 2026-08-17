package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.climate.ClimateNormalsField;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/** Replaceable deterministic algorithm for generated finite surface-water initial conditions. */
@FunctionalInterface
public interface SurfaceHydrologyGenerator {
    /** Legacy/custom seam retained as the single abstract method for lambda compatibility. */
    SurfaceHydrologyField generate(
            WorldGenesis genesis,
            ElevationField elevation,
            DrainageField drainage);

    /** Production composition path with an explicit durable hydrography dependency. */
    default SurfaceHydrologyField generate(
            WorldGenesis genesis,
            ElevationField elevation,
            DrainageField drainage,
            HydrographyField hydrography) {
        return generate(genesis, elevation, drainage);
    }

    /**
     * Production composition path with durable hydrography and climate dependencies.
     *
     * <p>The default preserves source compatibility for custom generators. The canonical stage
     * consumes both facts explicitly for V7+ climate-aware initial Water.</p>
     */
    default SurfaceHydrologyField generate(
            WorldGenesis genesis,
            ElevationField elevation,
            DrainageField drainage,
            HydrographyField hydrography,
            ClimateNormalsField climate) {
        return generate(genesis, elevation, drainage, hydrography);
    }
}
