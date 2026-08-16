package io.github.evoforge.simulation.world.materialization;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;

/** Resolves the Landscape material authored for one generated solid terrain cell. */
@FunctionalInterface
public interface TerrainMaterialResolver {

    LandscapeDefinitionId materialAt(int x, int y, int z);

    /**
     * Creates an explicit single-material resolver for worlds that do not yet have
     * generated geology or another material-authoring fact.
     */
    static TerrainMaterialResolver uniform(
            LandscapeDefinitionId definitionId) {

        if (definitionId == null) {
            throw new IllegalArgumentException(
                    "definitionId must not be null");
        }
        return (x, y, z) -> definitionId;
    }
}
