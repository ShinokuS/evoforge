package io.github.evoforge.simulation.world.definition.physical;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.evoforge.simulation.world.definition.DefinitionAspectCompiler;
import io.github.evoforge.simulation.world.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.definition.DefinitionId;

public final class PhysicalDefinitionCompiler
        implements DefinitionAspectCompiler {

    private final PhysicalDefinitions definitions;

    public PhysicalDefinitionCompiler(
            PhysicalDefinitions definitions) {

        if (definitions == null) {
            throw new IllegalArgumentException(
                    "definitions must not be null");
        }

        this.definitions = definitions;
    }

    @Override
    public String key() {
        return "physical";
    }

    @Override
    public void compile(
            DefinitionId definitionId,
            JsonObject data,
            DefinitionCatalog catalog) {

        if (data == null) {
            throw new IllegalArgumentException(
                    "data must not be null");
        }

        JsonElement massElement = data.get("mass");

        if (massElement == null
                || !massElement.isJsonPrimitive()) {

            throw new IllegalArgumentException(
                    "physical.mass is required");
        }

        JsonPrimitive massPrimitive = massElement.getAsJsonPrimitive();

        if (!massPrimitive.isNumber()) {
            throw new IllegalArgumentException(
                    "physical.mass must be a number");
        }

        definitions.put(
                definitionId,
                massPrimitive.getAsDouble());
    }

    @Override
    public void finish() {
        definitions.freeze();
    }
}