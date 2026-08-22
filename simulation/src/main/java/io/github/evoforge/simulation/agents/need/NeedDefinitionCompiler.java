package io.github.evoforge.simulation.agents.need;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.evoforge.simulation.definition.DefinitionAspectCompiler;
import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Compiles the object-definition `needs` aspect keyed by open NeedId strings. */
public final class NeedDefinitionCompiler implements DefinitionAspectCompiler<ObjectDefinitionId> {

    private final NeedDefinitions definitions;

    public NeedDefinitionCompiler(NeedDefinitions definitions) {
        if (definitions == null) {
            throw new IllegalArgumentException("definitions must not be null");
        }
        this.definitions = definitions;
    }

    @Override
    public String key() {
        return "needs";
    }

    @Override
    public void compile(
            ObjectDefinitionId definitionId,
            JsonObject data,
            DefinitionCatalog<ObjectDefinitionId> catalog) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        List<String> keys = new ArrayList<>(data.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            JsonElement element = data.get(key);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("needs." + key + " must be an object");
            }
            JsonObject need = element.getAsJsonObject();
            long max = integer(need.get("max"), "needs." + key + ".max");
            long initial = integer(need.get("initial"), "needs." + key + ".initial");
            definitions.add(definitionId, new NeedSpec(NeedId.of(key), max, initial));
        }
    }

    @Override
    public void finish() {
        definitions.freeze();
    }

    private static long integer(JsonElement element, String path) {
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(path + " is required");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            throw new IllegalArgumentException(path + " must be an integer");
        }
        try {
            return new BigDecimal(primitive.getAsString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException(path + " must be an integer", exception);
        }
    }
}
