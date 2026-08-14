package io.github.evoforge.simulation.world.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.evoforge.simulation.definition.DefinitionAspectCompiler;
import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Compiles the object-definition `agent` aspect. */
public final class AgentDefinitionCompiler implements DefinitionAspectCompiler<ObjectDefinitionId> {

    private final AgentDefinitions definitions;

    public AgentDefinitionCompiler(AgentDefinitions definitions) {
        if (definitions == null) {
            throw new IllegalArgumentException("definitions must not be null");
        }
        this.definitions = definitions;
    }

    @Override
    public String key() {
        return "agent";
    }

    @Override
    public void compile(
            ObjectDefinitionId definitionId,
            JsonObject data,
            DefinitionCatalog<ObjectDefinitionId> catalog) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        int radius = integer(data.get("perceptionRadius"), "agent.perceptionRadius");
        if (radius < 0) {
            throw new IllegalArgumentException("agent.perceptionRadius must be >= 0");
        }

        List<CapabilityId> capabilities = new ArrayList<>();
        JsonElement element = data.get("capabilities");
        if (element != null) {
            if (!element.isJsonArray()) {
                throw new IllegalArgumentException("agent.capabilities must be an array");
            }
            JsonArray array = element.getAsJsonArray();
            for (JsonElement capabilityElement : array) {
                if (!capabilityElement.isJsonPrimitive()
                        || !capabilityElement.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("agent.capabilities entries must be strings");
                }
                capabilities.add(CapabilityId.of(capabilityElement.getAsString()));
            }
        }
        definitions.put(
                definitionId,
                new AgentDefinition(radius, capabilities.toArray(CapabilityId[]::new)));
    }

    @Override
    public void finish() {
        definitions.freeze();
    }

    private static int integer(JsonElement element, String path) {
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(path + " is required");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            throw new IllegalArgumentException(path + " must be an integer");
        }
        try {
            return new BigDecimal(primitive.getAsString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException(path + " must be an integer", exception);
        }
    }
}
