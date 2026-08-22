package io.github.evoforge.simulation.world.landscape;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.terrain.TerrainPlacementResult;
import io.github.evoforge.simulation.world.terrain.TerrainRemovalResult;
import io.github.evoforge.simulation.world.terrain.TerrainReplacementResult;

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
