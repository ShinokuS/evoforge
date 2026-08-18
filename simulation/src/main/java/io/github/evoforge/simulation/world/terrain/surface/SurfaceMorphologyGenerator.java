package io.github.evoforge.simulation.world.terrain.surface;

import io.github.evoforge.simulation.world.atlas.ElevationField;

/** Replaceable deterministic algorithm that derives reusable local morphology from elevation. */
@FunctionalInterface
public interface SurfaceMorphologyGenerator {
    SurfaceMorphologyField generate(ElevationField elevation);
}
