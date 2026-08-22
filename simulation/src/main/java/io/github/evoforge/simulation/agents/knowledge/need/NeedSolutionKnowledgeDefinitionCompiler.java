package io.github.evoforge.simulation.agents.knowledge.need;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.evoforge.simulation.definition.DefinitionAspectCompiler;
import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.agents.need.NeedId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

/** Compiles semantic knowledge of solvable needs without encoding any concrete source. */
public final class NeedSolutionKnowledgeDefinitionCompiler implements DefinitionAspectCompiler<ObjectDefinitionId> {
    private final NeedSolutionKnowledgeDefinitions definitions;

    public NeedSolutionKnowledgeDefinitionCompiler(NeedSolutionKnowledgeDefinitions definitions) {
        if (definitions == null) throw new IllegalArgumentException("definitions must not be null");
        this.definitions = definitions;
    }

    @Override public String key() { return "needSolutionKnowledge"; }

    @Override
    public void compile(ObjectDefinitionId definitionId, JsonObject data, DefinitionCatalog<ObjectDefinitionId> catalog) {
        if (data == null) throw new IllegalArgumentException("data must not be null");
        JsonElement element = data.get("needs");
        if (element == null || !element.isJsonArray()) throw new IllegalArgumentException("needSolutionKnowledge.needs must be an array");
        JsonArray array = element.getAsJsonArray();
        for (JsonElement entry : array) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("needSolutionKnowledge.needs entries must be strings");
            }
            definitions.add(definitionId, NeedId.of(entry.getAsString()));
        }
    }

    @Override public void finish() { definitions.freeze(); }
}
