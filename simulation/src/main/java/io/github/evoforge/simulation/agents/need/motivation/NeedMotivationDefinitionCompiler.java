package io.github.evoforge.simulation.agents.need.motivation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.evoforge.simulation.definition.DefinitionAspectCompiler;
import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.agents.need.NeedId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Compiles independent `needMotivation` thresholds keyed by open NeedId. */
public final class NeedMotivationDefinitionCompiler
        implements DefinitionAspectCompiler<ObjectDefinitionId> {
    private final NeedMotivationDefinitions definitions;

    public NeedMotivationDefinitionCompiler(NeedMotivationDefinitions definitions) {
        if (definitions == null) throw new IllegalArgumentException("definitions must not be null");
        this.definitions = definitions;
    }

    @Override public String key() { return "needMotivation"; }

    @Override
    public void compile(ObjectDefinitionId definitionId, JsonObject data, DefinitionCatalog<ObjectDefinitionId> catalog) {
        if (data == null) throw new IllegalArgumentException("data must not be null");
        List<String> keys = new ArrayList<>(data.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            JsonElement element = data.get(key);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("needMotivation." + key + " must be an object");
            }
            JsonObject entry = element.getAsJsonObject();
            long activationLevel = integer(
                    entry.get("activationLevel"),
                    "needMotivation." + key + ".activationLevel");
            definitions.add(
                    definitionId,
                    new NeedMotivationDefinition(NeedId.of(key), activationLevel));
        }
    }

    @Override public void finish() { definitions.freeze(); }

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
