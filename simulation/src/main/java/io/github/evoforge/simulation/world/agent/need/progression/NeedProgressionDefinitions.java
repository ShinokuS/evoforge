package io.github.evoforge.simulation.world.agent.need.progression;

import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Immutable-at-runtime progression declarations keyed by object definition. */
public final class NeedProgressionDefinitions {
    @SuppressWarnings("unchecked")
    private List<NeedProgressionDefinition>[] values = new List[16];
    private boolean frozen;

    public void add(ObjectDefinitionId definitionId, NeedProgressionDefinition definition) {
        if (frozen) throw new IllegalStateException("need progression definitions are frozen");
        if (definitionId == null || definition == null) {
            throw new IllegalArgumentException("need progression definition values must not be null");
        }
        int index = definitionId.asInt();
        if (index >= values.length) values = Arrays.copyOf(values, Math.max(index + 1, values.length * 2));
        List<NeedProgressionDefinition> list = values[index];
        if (list == null) {
            list = new ArrayList<>();
            values[index] = list;
        }
        for (NeedProgressionDefinition existing : list) {
            if (existing.needId().equals(definition.needId())) {
                throw new IllegalStateException(
                        "need progression already declared for definition " + definitionId + ": " + definition.needId());
            }
        }
        list.add(definition);
    }

    public boolean has(ObjectDefinitionId definitionId) {
        if (definitionId == null) return false;
        int index = definitionId.asInt();
        return index < values.length && values[index] != null && !values[index].isEmpty();
    }

    public int count(ObjectDefinitionId definitionId) {
        return has(definitionId) ? values[definitionId.asInt()].size() : 0;
    }

    public NeedProgressionDefinition definitionAt(ObjectDefinitionId definitionId, int index) {
        if (!has(definitionId)) throw new IllegalArgumentException("need progression definition not found: " + definitionId);
        return values[definitionId.asInt()].get(index);
    }

    public NeedProgressionDefinition get(ObjectDefinitionId definitionId, NeedId needId) {
        if (!has(definitionId) || needId == null) return null;
        for (NeedProgressionDefinition definition : values[definitionId.asInt()]) {
            if (definition.needId().equals(needId)) return definition;
        }
        return null;
    }

    public void freeze() {
        if (frozen) return;
        for (int index = 0; index < values.length; index++) {
            if (values[index] != null) values[index] = List.copyOf(values[index]);
        }
        frozen = true;
    }

    public boolean isFrozen() { return frozen; }
}
