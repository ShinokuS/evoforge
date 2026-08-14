package io.github.evoforge.simulation.world.agent.affordance;

import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Definition data for simple persistent sources that advertise need satisfaction. */
public final class NeedSatisfactionDefinitions {

    private static final int DEFAULT_CAPACITY = 16;

    @SuppressWarnings("unchecked")
    private List<NeedSatisfaction>[] values = new List[DEFAULT_CAPACITY];
    private boolean frozen;

    public void add(ObjectDefinitionId definitionId, NeedSatisfaction satisfaction) {
        if (frozen) {
            throw new IllegalStateException("need-satisfaction definitions are frozen");
        }
        if (definitionId == null) {
            throw new IllegalArgumentException("definitionId must not be null");
        }
        if (satisfaction == null) {
            throw new IllegalArgumentException("satisfaction must not be null");
        }

        int index = definitionId.asInt();
        ensureCapacity(index + 1);
        List<NeedSatisfaction> list = values[index];
        if (list == null) {
            list = new ArrayList<>();
            values[index] = list;
        }
        list.add(satisfaction);
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

    public NeedSatisfaction satisfactionAt(ObjectDefinitionId definitionId, int index) {
        if (!has(definitionId)) {
            throw new IllegalArgumentException("need-satisfaction definition not found: " + definitionId);
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
