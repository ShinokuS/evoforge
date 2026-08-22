package io.github.evoforge.simulation.agents.need.progression;

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

/** Compiles independent `needProgression` definitions keyed by open NeedId. */
public final class NeedProgressionDefinitionCompiler
        implements DefinitionAspectCompiler<ObjectDefinitionId> {
    private final NeedProgressionDefinitions definitions;

    public NeedProgressionDefinitionCompiler(NeedProgressionDefinitions definitions) {
        if (definitions == null) throw new IllegalArgumentException("definitions must not be null");
        this.definitions = definitions;
    }

    @Override public String key() { return "needProgression"; }

    @Override
    public void compile(ObjectDefinitionId definitionId, JsonObject data, DefinitionCatalog<ObjectDefinitionId> catalog) {
        if (data == null) throw new IllegalArgumentException("data must not be null");
        List<String> keys = new ArrayList<>(data.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            JsonElement element = data.get(key);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("needProgression." + key + " must be an object");
            }
            JsonObject entry = element.getAsJsonObject();
            long baseAmount = integer(entry.get("baseAmount"), "needProgression." + key + ".baseAmount");
            long intervalTicks = integer(entry.get("intervalTicks"), "needProgression." + key + ".intervalTicks");
            definitions.add(
                    definitionId,
                    new NeedProgressionDefinition(NeedId.of(key), baseAmount, intervalTicks));
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
