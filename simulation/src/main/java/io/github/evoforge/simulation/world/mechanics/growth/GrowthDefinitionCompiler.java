package io.github.evoforge.simulation.world.mechanics.growth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.evoforge.simulation.definition.DefinitionAspectCompiler;
import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import java.math.BigDecimal;

/** Compiles the independent `growth` definition aspect. */
public final class GrowthDefinitionCompiler implements DefinitionAspectCompiler<ObjectDefinitionId> {
    private final GrowthDefinitions definitions;

    public GrowthDefinitionCompiler(GrowthDefinitions definitions) {
        if (definitions == null) throw new IllegalArgumentException("definitions must not be null");
        this.definitions = definitions;
    }

    @Override
    public String key() { return "growth"; }

    @Override
    public void compile(
            ObjectDefinitionId definitionId,
            JsonObject data,
            DefinitionCatalog<ObjectDefinitionId> catalog) {
        if (data == null) throw new IllegalArgumentException("data must not be null");
        long baseAmount = integer(data.get("baseAmount"), "growth.baseAmount");
        long intervalTicks = integer(data.get("intervalTicks"), "growth.intervalTicks");
        definitions.put(definitionId, new GrowthDefinition(baseAmount, intervalTicks));
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
