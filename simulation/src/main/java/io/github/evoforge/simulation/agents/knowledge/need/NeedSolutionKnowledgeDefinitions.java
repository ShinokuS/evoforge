package io.github.evoforge.simulation.agents.knowledge.need;

import io.github.evoforge.simulation.agents.need.NeedId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** General semantic knowledge that a need has environmental solutions, without concrete source knowledge. */
public final class NeedSolutionKnowledgeDefinitions {
    @SuppressWarnings("unchecked")
    private List<NeedId>[] values = new List[16];
    private boolean frozen;

    public void add(ObjectDefinitionId definitionId, NeedId needId) {
        if (frozen) throw new IllegalStateException("need-solution knowledge definitions are frozen");
        if (definitionId == null || needId == null) throw new IllegalArgumentException("knowledge values must not be null");
        int index = definitionId.asInt();
        if (index >= values.length) values = Arrays.copyOf(values, Math.max(index + 1, values.length * 2));
        List<NeedId> list = values[index];
        if (list == null) { list = new ArrayList<>(); values[index] = list; }
        if (list.contains(needId)) throw new IllegalStateException("need solution already known for definition " + definitionId + ": " + needId.value());
        list.add(needId);
    }

    public boolean knows(ObjectDefinitionId definitionId, NeedId needId) {
        if (definitionId == null || needId == null || definitionId.asInt() >= values.length) return false;
        List<NeedId> list = values[definitionId.asInt()];
        return list != null && list.contains(needId);
    }

    public void freeze() {
        if (frozen) return;
        for (int index = 0; index < values.length; index++) if (values[index] != null) values[index] = List.copyOf(values[index]);
        frozen = true;
    }
    public boolean isFrozen() { return frozen; }
}
