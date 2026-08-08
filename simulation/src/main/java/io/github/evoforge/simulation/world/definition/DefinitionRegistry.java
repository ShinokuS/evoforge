package io.github.evoforge.simulation.world.definition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DefinitionRegistry {

    private final List<ObjectDefinition> definitions = new ArrayList<>();
    private final Map<String, DefinitionId> idsByKey = new HashMap<>();

    private boolean frozen;

    public DefinitionId register(ObjectDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }

        if (frozen) {
            throw new IllegalStateException("registry is frozen");
        }

        if (idsByKey.containsKey(definition.key())) {
            throw new IllegalArgumentException(
                    "definition already registered: " + definition.key());
        }

        DefinitionId id = DefinitionId.of(definitions.size());

        definitions.add(definition);
        idsByKey.put(definition.key(), id);

        return id;
    }

    public ObjectDefinition get(DefinitionId id) {
        if (id == null) {
            return null;
        }

        int index = id.asInt();

        if (index >= definitions.size()) {
            return null;
        }

        return definitions.get(index);
    }

    public ObjectDefinition get(String key) {
        if (key == null) {
            return null;
        }

        DefinitionId id = idsByKey.get(key);

        if (id == null) {
            return null;
        }

        return definitions.get(id.asInt());
    }

    public DefinitionId idOf(String key) {
        if (key == null) {
            return null;
        }

        return idsByKey.get(key);
    }

    public int size() {
        return definitions.size();
    }

    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }
}