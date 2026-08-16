package io.github.evoforge.simulation.world.terrain.generation;

import io.github.evoforge.simulation.world.atlas.DrainageField;
import io.github.evoforge.simulation.world.atlas.ElevationField;

/** Replaceable deterministic algorithm that derives material strata from generated causal facts. */
@FunctionalInterface
public interface TerrainMaterialGenerator {
    TerrainMaterialField generate(
            ElevationField elevation,
            DrainageField drainage,
            CompiledTerrainProfile profile);
}
