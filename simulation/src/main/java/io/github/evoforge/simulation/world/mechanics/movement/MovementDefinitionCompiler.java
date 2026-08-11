package io.github.evoforge.simulation.world.mechanics.movement;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.evoforge.simulation.definition.DefinitionAspectCompiler;
import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

import java.math.BigDecimal;

public final class MovementDefinitionCompiler
        implements DefinitionAspectCompiler<ObjectDefinitionId> {

    private final MovementDefinitions definitions;

    public MovementDefinitionCompiler(
            MovementDefinitions definitions) {

        if (definitions == null) {
            throw new IllegalArgumentException(
                    "definitions must not be null");
        }

        this.definitions = definitions;
    }

    @Override
    public String key() {
        return "movement";
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

        JsonElement rateElement = data.get("rate");

        if (rateElement == null
                || !rateElement.isJsonPrimitive()) {
            throw new IllegalArgumentException(
                    "movement.rate is required");
        }

        JsonPrimitive ratePrimitive =
                rateElement.getAsJsonPrimitive();

        if (!ratePrimitive.isNumber()) {
            throw new IllegalArgumentException(
                    "movement.rate must be an integer");
        }

        long rate;

        try {
            rate = new BigDecimal(
                    ratePrimitive.getAsString())
                    .longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "movement.rate must be an integer",
                    exception);
        }

        definitions.put(
                definitionId,
                MovementRate.of(rate));
    }

    @Override
    public void finish() {
        definitions.freeze();
    }
}
