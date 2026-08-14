package io.github.evoforge.simulation.world.agent.affordance;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.evoforge.simulation.definition.DefinitionAspectCompiler;
import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.agent.CapabilityId;
import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Compiles the `needSatisfaction` aspect without a closed affordance-type switch. */
public final class NeedSatisfactionDefinitionCompiler
        implements DefinitionAspectCompiler<ObjectDefinitionId> {

    private final NeedSatisfactionDefinitions definitions;

    public NeedSatisfactionDefinitionCompiler(NeedSatisfactionDefinitions definitions) {
        if (definitions == null) throw new IllegalArgumentException("definitions must not be null");
        this.definitions = definitions;
    }

    @Override
    public String key() { return "needSatisfaction"; }

    @Override
    public void compile(
            ObjectDefinitionId definitionId,
            JsonObject data,
            DefinitionCatalog<ObjectDefinitionId> catalog) {
        if (data == null) throw new IllegalArgumentException("data must not be null");
        List<String> keys = new ArrayList<>(data.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            JsonElement element = data.get(key);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("needSatisfaction." + key + " must be an object");
            }
            JsonObject entry = element.getAsJsonObject();
            long amount = integer(entry.get("amount"), "needSatisfaction." + key + ".amount");
            JsonElement consumed = entry.get("consumesQuantity");
            long consumedQuantity = consumed == null
                    ? 0L
                    : integer(consumed, "needSatisfaction." + key + ".consumesQuantity");
            CapabilityId required = null;
            JsonElement capability = entry.get("requiresCapability");
            if (capability != null) {
                if (!capability.isJsonPrimitive() || !capability.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException(
                            "needSatisfaction." + key + ".requiresCapability must be a string");
                }
                required = CapabilityId.of(capability.getAsString());
            }
            definitions.add(
                    definitionId,
                    new NeedSatisfaction(NeedId.of(key), amount, consumedQuantity, required));
        }
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
