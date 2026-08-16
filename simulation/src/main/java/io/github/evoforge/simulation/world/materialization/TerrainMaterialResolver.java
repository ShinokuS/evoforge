package io.github.evoforge.simulation.world.materialization;

import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;

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
     * Resolves stable generated material keys into runtime Landscape ids at the
     * materialization boundary. Generated fields therefore never depend on registry ids.
     */
    static TerrainMaterialResolver resolved(
            TerrainMaterialField field,
            DefinitionCatalog<LandscapeDefinitionId> definitions) {
        if (field == null || definitions == null) {
            throw new IllegalArgumentException(
                    "terrain material field/catalog must not be null");
        }
        return (x, y, z) -> {
            TerrainMaterialKey key = field.materialAt(x, y, z);
            if (key == null) {
                return null;
            }
            LandscapeDefinitionId id = definitions.resolve(key.value());
            if (id == null) {
                throw new IllegalStateException(
                        "generated terrain material is not registered at ("
                                + x + ", " + y + ", " + z + "): " + key.value());
            }
            return id;
        };
    }

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
