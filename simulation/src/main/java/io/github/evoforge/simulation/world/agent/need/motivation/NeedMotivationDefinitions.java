package io.github.evoforge.simulation.world.agent.need.motivation;

import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Immutable-at-runtime autonomous motivation thresholds keyed by object definition and open NeedId. */
public final class NeedMotivationDefinitions {
    @SuppressWarnings("unchecked")
    private List<NeedMotivationDefinition>[] values = new List[16];
    private boolean frozen;

    public void add(ObjectDefinitionId definitionId, NeedMotivationDefinition definition) {
        if (frozen) throw new IllegalStateException("need motivation definitions are frozen");
        if (definitionId == null || definition == null) {
            throw new IllegalArgumentException("need motivation definition values must not be null");
        }
        int index = definitionId.asInt();
        if (index >= values.length) values = Arrays.copyOf(values, Math.max(index + 1, values.length * 2));
        List<NeedMotivationDefinition> list = values[index];
        if (list == null) {
            list = new ArrayList<>();
            values[index] = list;
        }
        for (NeedMotivationDefinition existing : list) {
            if (existing.needId().equals(definition.needId())) {
                throw new IllegalStateException(
                        "need motivation already declared for definition " + definitionId + ": " + definition.needId());
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

    public NeedMotivationDefinition definitionAt(ObjectDefinitionId definitionId, int index) {
        if (!has(definitionId)) throw new IllegalArgumentException("need motivation definition not found: " + definitionId);
        return values[definitionId.asInt()].get(index);
    }

    public NeedMotivationDefinition get(ObjectDefinitionId definitionId, NeedId needId) {
        if (!has(definitionId) || needId == null) return null;
        for (NeedMotivationDefinition definition : values[definitionId.asInt()]) {
            if (definition.needId().equals(needId)) return definition;
        }
        return null;
    }

    public long activationLevel(ObjectDefinitionId definitionId, NeedId needId) {
        NeedMotivationDefinition definition = get(definitionId, needId);
        return definition == null ? 1L : definition.activationLevel();
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
