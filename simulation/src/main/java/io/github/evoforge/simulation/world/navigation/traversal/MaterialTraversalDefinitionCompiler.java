package io.github.evoforge.simulation.world.navigation.traversal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.evoforge.simulation.definition.DefinitionAspectCompiler;
import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;

import java.math.BigDecimal;
import io.github.evoforge.simulation.world.navigation.traversal.SurfaceTraversalCost;

public final class MaterialTraversalDefinitionCompiler
        implements DefinitionAspectCompiler<MaterialDefinitionId> {

    private final MaterialTraversalDefinitions definitions;

    public MaterialTraversalDefinitionCompiler(
            MaterialTraversalDefinitions definitions) {

        if (definitions == null) {
            throw new IllegalArgumentException(
                    "definitions must not be null");
        }

        this.definitions = definitions;
    }

    @Override
    public String key() {
        return "traversal";
    }

    @Override
    public void compile(
            MaterialDefinitionId definitionId,
            JsonObject data,
            DefinitionCatalog<MaterialDefinitionId> catalog) {

        if (data == null) {
            throw new IllegalArgumentException(
                    "data must not be null");
        }

        JsonElement costElement = data.get("cost");

        if (costElement == null
                || !costElement.isJsonPrimitive()) {
            throw new IllegalArgumentException(
                    "traversal.cost is required");
        }

        JsonPrimitive costPrimitive =
                costElement.getAsJsonPrimitive();

        if (!costPrimitive.isNumber()) {
            throw new IllegalArgumentException(
                    "traversal.cost must be an integer");
        }

        long cost;

        try {
            cost = new BigDecimal(
                    costPrimitive.getAsString())
                    .longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "traversal.cost must be an integer",
                    exception);
        }

        definitions.put(
                definitionId,
                SurfaceTraversalCost.of(cost));
    }

    @Override
    public void finish() {
        definitions.freeze();
    }
}
