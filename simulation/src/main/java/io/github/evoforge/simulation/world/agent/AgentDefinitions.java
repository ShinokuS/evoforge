package io.github.evoforge.simulation.world.agent;

import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import java.util.Arrays;

/** Immutable-at-runtime agent composition keyed by object definition. */
public final class AgentDefinitions {

    private static final int DEFAULT_CAPACITY = 16;

    private AgentDefinition[] values = new AgentDefinition[DEFAULT_CAPACITY];
    private boolean frozen;

    public void put(ObjectDefinitionId id, AgentDefinition definition) {
        if (frozen) {
            throw new IllegalStateException("agent definitions are frozen");
        }
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }

        int index = id.asInt();
        ensureCapacity(index + 1);
        if (values[index] != null) {
            throw new IllegalStateException("agent definition already exists: " + id);
        }
        values[index] = definition;
    }

    public boolean has(ObjectDefinitionId id) {
        if (id == null) {
            return false;
        }
        int index = id.asInt();
        return index < values.length && values[index] != null;
    }

    public AgentDefinition get(ObjectDefinitionId id) {
        if (!has(id)) {
            throw new IllegalArgumentException("agent definition not found: " + id);
        }
        return values[id.asInt()];
    }

    public void freeze() {
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
