package io.github.evoforge.simulation.world.surface;

import io.github.evoforge.simulation.world.atlas.ElevationField;

/** Replaceable deterministic algorithm that derives reusable local morphology from elevation. */
@FunctionalInterface
public interface SurfaceMorphologyGenerator {
    SurfaceMorphologyField generate(ElevationField elevation);
}
