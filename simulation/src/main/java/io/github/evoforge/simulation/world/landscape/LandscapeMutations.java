package io.github.evoforge.simulation.world.landscape;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainPlacementResult;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainRemovalResult;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainReplacementResult;

public interface LandscapeMutations {

    TerrainPlacementResult placeTerrain(
            int x,
            int y,
            int z,
            LandscapeDefinitionId definitionId);

    TerrainReplacementResult replaceTerrain(
            int x,
            int y,
            int z,
            LandscapeDefinitionId definitionId);

    TerrainRemovalResult removeTerrain(
            int x,
            int y,
            int z);
}
