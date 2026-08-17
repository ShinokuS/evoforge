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

    /**
     * Production composition path with an explicit durable hydrography dependency.
     *
     * <p>Custom legacy generators may intentionally ignore the new fact; the production stage
     * overrides this method and consumes it directly.</p>
     */
    default SurfaceHydrologyField generate(
            WorldGenesis genesis,
            ElevationField elevation,
            DrainageField drainage,
            HydrographyField hydrography) {
        return generate(genesis, elevation, drainage);
    }

    /**
     * Production composition path with the generated climate fact that constrains initial Water.
     *
     * <p>The default preserves source compatibility for custom generators. The canonical production
     * stage consumes both hydrography and climate explicitly.</p>
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
