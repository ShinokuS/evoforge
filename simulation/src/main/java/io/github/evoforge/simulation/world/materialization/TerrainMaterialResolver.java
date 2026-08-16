package io.github.evoforge.simulation.world.materialization;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;

/**
 * Pure deterministic lookup for the Landscape material authored at one generated
 * solid terrain cell.
 *
 * <p>Implementations must not depend on invocation order or mutate world state.
 * Materialization may query the same coordinate during validation and placement.</p>
 */
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
