package io.github.evoforge.simulation.world.calibration.soil;

import io.github.evoforge.simulation.world.atlas.DrainageField;
import io.github.evoforge.simulation.world.terrain.surface.SurfaceMorphologyField;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;

/** Replaceable pre-runtime model that develops spatial Soil hydraulics from generated world facts. */
@FunctionalInterface
public interface SoilFormationGenerator {
    SoilHydraulicProfileField generate(
            TerrainMaterialField materials,
            SurfaceMorphologyField morphology,
            DrainageField drainage,
            SoilSemanticProfileBindings semantics);
}
