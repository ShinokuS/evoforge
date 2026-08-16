package io.github.evoforge.simulation.world.materialization;

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

    /** Resolves generated material keys through explicit content-composition bindings. */
    static TerrainMaterialResolver resolved(
            TerrainMaterialField field,
            TerrainMaterialBindings bindings) {
        if (field == null || bindings == null) {
            throw new IllegalArgumentException(
                    "terrain material field/bindings must not be null");
        }
        return (x, y, z) -> {
            TerrainMaterialKey key = field.materialAt(x, y, z);
            if (key == null) {
                return null;
            }
            LandscapeDefinitionId id = bindings.resolve(key);
            if (id == null) {
                throw new IllegalStateException(
                        "generated terrain material is not bound at ("
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
