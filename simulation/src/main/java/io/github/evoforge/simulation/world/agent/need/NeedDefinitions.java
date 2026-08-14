package io.github.evoforge.simulation.world.agent.need;

import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Definition-owned declaration of needs present on each object definition. */
public final class NeedDefinitions {

    private static final int DEFAULT_CAPACITY = 16;

    @SuppressWarnings("unchecked")
    private List<NeedSpec>[] values = new List[DEFAULT_CAPACITY];
    private boolean frozen;

    public void add(ObjectDefinitionId definitionId, NeedSpec spec) {
        if (frozen) {
            throw new IllegalStateException("need definitions are frozen");
        }
        if (definitionId == null) {
            throw new IllegalArgumentException("definitionId must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }

        int index = definitionId.asInt();
        ensureCapacity(index + 1);
        List<NeedSpec> list = values[index];
        if (list == null) {
            list = new ArrayList<>();
            values[index] = list;
        }
        for (NeedSpec existing : list) {
            if (existing.id().equals(spec.id())) {
                throw new IllegalStateException(
                        "need already declared for definition " + definitionId + ": " + spec.id().value());
            }
        }
        list.add(spec);
    }

    public boolean has(ObjectDefinitionId definitionId) {
        if (definitionId == null) {
            return false;
        }
        int index = definitionId.asInt();
        return index < values.length && values[index] != null && !values[index].isEmpty();
    }

    public int count(ObjectDefinitionId definitionId) {
        return has(definitionId) ? values[definitionId.asInt()].size() : 0;
    }

    public NeedSpec specAt(ObjectDefinitionId definitionId, int index) {
        if (!has(definitionId)) {
            throw new IllegalArgumentException("need definition not found: " + definitionId);
        }
        return values[definitionId.asInt()].get(index);
    }

    public void freeze() {
        if (frozen) {
            return;
        }
        for (int index = 0; index < values.length; index++) {
            if (values[index] != null) {
                values[index] = List.copyOf(values[index]);
            }
        }
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    private void ensureCapacity(int requiredCapacity) {
        if (requiredCapacity <= values.length) {
            return;
        }
        values = Arrays.copyOf(values, Math.max(requiredCapacity, values.length * 2));
    }
}
