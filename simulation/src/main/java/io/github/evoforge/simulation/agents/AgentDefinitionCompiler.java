package io.github.evoforge.simulation.agents;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.evoforge.simulation.definition.DefinitionAspectCompiler;
import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import java.util.ArrayList;
import java.util.List;

/** Compiles the object-definition `agent` aspect. Sensory mechanics use independent aspects. */
public final class AgentDefinitionCompiler implements DefinitionAspectCompiler<ObjectDefinitionId> {
    private final AgentDefinitions definitions;

    public AgentDefinitionCompiler(AgentDefinitions definitions) {
        if (definitions == null) throw new IllegalArgumentException("definitions must not be null");
        this.definitions = definitions;
    }

    @Override public String key() { return "agent"; }

    @Override
    public void compile(ObjectDefinitionId definitionId, JsonObject data, DefinitionCatalog<ObjectDefinitionId> catalog) {
        if (data == null) throw new IllegalArgumentException("data must not be null");
        List<CapabilityId> capabilities = new ArrayList<>();
        JsonElement element = data.get("capabilities");
        if (element != null) {
            if (!element.isJsonArray()) throw new IllegalArgumentException("agent.capabilities must be an array");
            JsonArray array = element.getAsJsonArray();
            for (JsonElement capabilityElement : array) {
                if (!capabilityElement.isJsonPrimitive() || !capabilityElement.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("agent.capabilities entries must be strings");
                }
                capabilities.add(CapabilityId.of(capabilityElement.getAsString()));
            }
        }
        definitions.put(definitionId, new AgentDefinition(capabilities.toArray(CapabilityId[]::new)));
    }

    @Override public void finish() { definitions.freeze(); }
}
