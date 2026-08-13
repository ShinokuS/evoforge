package io.github.evoforge.simulation.world.mechanics.occupancy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.evoforge.simulation.definition.DefinitionAspectCompiler;
import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

/** Compiles the object-definition {@code occupancy} aspect. */
public final class OccupancyDefinitionCompiler
        implements DefinitionAspectCompiler<ObjectDefinitionId> {

    private final OccupancyDefinitions definitions;

    public OccupancyDefinitionCompiler(
            OccupancyDefinitions definitions) {

        if (definitions == null) {
            throw new IllegalArgumentException(
                    "definitions must not be null");
        }

        this.definitions = definitions;
    }

    @Override
    public String key() {
        return "occupancy";
    }

    @Override
    public void compile(
            ObjectDefinitionId definitionId,
            JsonObject data,
            DefinitionCatalog<ObjectDefinitionId> catalog) {

        if (data == null) {
            throw new IllegalArgumentException(
                    "data must not be null");
        }

        JsonElement exclusiveElement = data.get("exclusive");
        if (exclusiveElement == null
                || !exclusiveElement.isJsonPrimitive()) {
            throw new IllegalArgumentException(
                    "occupancy.exclusive is required");
        }

        JsonPrimitive exclusivePrimitive =
                exclusiveElement.getAsJsonPrimitive();
        if (!exclusivePrimitive.isBoolean()) {
            throw new IllegalArgumentException(
                    "occupancy.exclusive must be a boolean");
        }

        definitions.put(
                definitionId,
                exclusivePrimitive.getAsBoolean());
    }

    @Override
    public void finish() {
        definitions.freeze();
    }
}
