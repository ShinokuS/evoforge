package io.github.evoforge.simulation.world.landscape.soil;

import java.math.BigDecimal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.github.evoforge.simulation.definition.DefinitionAspectCompiler;
import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;

public final class SoilHydrologyDefinitionCompiler
        implements DefinitionAspectCompiler<LandscapeDefinitionId> {

    private final SoilHydrologyDefinitions definitions;

    public SoilHydrologyDefinitionCompiler(
            SoilHydrologyDefinitions definitions) {

        if (definitions == null) {
            throw new IllegalArgumentException(
                    "definitions must not be null");
        }

        this.definitions = definitions;
    }

    @Override
    public String key() {
        return "soil";
    }

    @Override
    public void compile(
            LandscapeDefinitionId definitionId,
            JsonObject data,
            DefinitionCatalog<LandscapeDefinitionId> catalog) {

        if (data == null) {
            throw new IllegalArgumentException(
                    "data must not be null");
        }

        definitions.put(
                definitionId,
                new SoilHydrology(
                        integer(data, "capacity"),
                        integer(data, "infiltrationLimit")));
    }

    @Override
    public void finish() {
        definitions.freeze();
    }

    private static int integer(
            JsonObject data,
            String field) {

        JsonElement element = data.get(field);
        String qualified = "soil." + field;

        if (element == null
                || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(
                    qualified + " is required");
        }

        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            throw new IllegalArgumentException(
                    qualified + " must be an integer");
        }

        try {
            return new BigDecimal(
                    primitive.getAsString())
                    .intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    qualified + " must be an integer",
                    exception);
        }
    }
}
