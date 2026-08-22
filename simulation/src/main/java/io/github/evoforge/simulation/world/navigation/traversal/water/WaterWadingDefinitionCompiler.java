package io.github.evoforge.simulation.world.navigation.traversal.water;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.evoforge.simulation.definition.DefinitionAspectCompiler;
import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

import java.math.BigDecimal;

/** Compiles the optional {@code waterWading} object-definition aspect. */
public final class WaterWadingDefinitionCompiler
        implements DefinitionAspectCompiler<ObjectDefinitionId> {

    private final WaterWadingDefinitions definitions;

    public WaterWadingDefinitionCompiler(
            WaterWadingDefinitions definitions) {

        if (definitions == null) {
            throw new IllegalArgumentException(
                    "definitions must not be null");
        }
        this.definitions = definitions;
    }

    @Override
    public String key() {
        return "waterWading";
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

        JsonElement maxDepthElement = data.get("maxDepth");
        if (maxDepthElement == null
                || !maxDepthElement.isJsonPrimitive()) {
            throw new IllegalArgumentException(
                    "waterWading.maxDepth is required");
        }

        JsonPrimitive primitive = maxDepthElement.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            throw new IllegalArgumentException(
                    "waterWading.maxDepth must be an integer");
        }

        int maxDepth;
        try {
            maxDepth = new BigDecimal(
                    primitive.getAsString())
                    .intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "waterWading.maxDepth must be an integer",
                    exception);
        }

        definitions.put(
                definitionId,
                new WaterWadingProfile(maxDepth));
    }

    @Override
    public void finish() {
        definitions.freeze();
    }
}
