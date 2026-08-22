package io.github.evoforge.simulation.mechanics.terrainmutation;

import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.terrain.TerrainPlacementResult;
import io.github.evoforge.simulation.world.terrain.TerrainRemovalResult;
import io.github.evoforge.simulation.world.terrain.TerrainReplacementResult;

public interface TerrainMutations {

    TerrainPlacementResult placeTerrain(
            int x,
            int y,
            int z,
            MaterialDefinitionId definitionId);

    TerrainReplacementResult replaceTerrain(
            int x,
            int y,
            int z,
            MaterialDefinitionId definitionId);

    TerrainRemovalResult removeTerrain(
            int x,
            int y,
            int z);
}
