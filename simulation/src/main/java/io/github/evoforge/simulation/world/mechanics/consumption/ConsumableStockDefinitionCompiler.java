package io.github.evoforge.simulation.world.mechanics.consumption;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.evoforge.simulation.definition.DefinitionAspectCompiler;
import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import java.math.BigDecimal;

/** Compiles the independent `consumableStock` definition aspect. */
public final class ConsumableStockDefinitionCompiler
        implements DefinitionAspectCompiler<ObjectDefinitionId> {
    private final ConsumableStockDefinitions definitions;

    public ConsumableStockDefinitionCompiler(ConsumableStockDefinitions definitions) {
        if (definitions == null) throw new IllegalArgumentException("definitions must not be null");
        this.definitions = definitions;
    }

    @Override
    public String key() { return "consumableStock"; }

    @Override
    public void compile(
            ObjectDefinitionId definitionId,
            JsonObject data,
            DefinitionCatalog<ObjectDefinitionId> catalog) {
        if (data == null) throw new IllegalArgumentException("data must not be null");
        long capacity = integer(data.get("capacity"), "consumableStock.capacity");
        JsonElement initialElement = data.get("initial");
        long initial = initialElement == null
                ? capacity
                : integer(initialElement, "consumableStock.initial");
        definitions.put(definitionId, new ConsumableStockDefinition(capacity, initial));
    }

    @Override
    public void finish() { definitions.freeze(); }

    private static long integer(JsonElement element, String path) {
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(path + " is required");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) throw new IllegalArgumentException(path + " must be an integer");
        try {
            return new BigDecimal(primitive.getAsString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException(path + " must be an integer", exception);
        }
    }
}
