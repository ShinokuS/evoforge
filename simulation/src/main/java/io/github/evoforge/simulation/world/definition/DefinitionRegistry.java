package io.github.evoforge.simulation.world.definition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DefinitionRegistry
        implements DefinitionResolver {

    private final List<String> keys = new ArrayList<>();
    private final Map<String, DefinitionId> idsByKey = new HashMap<>();

    private boolean frozen;

    public DefinitionId register(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }

        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }

        if (frozen) {
            throw new IllegalStateException("registry is frozen");
        }

        if (idsByKey.containsKey(key)) {
            throw new IllegalArgumentException(
                    "definition already registered: " + key);
        }

        DefinitionId id = DefinitionId.of(keys.size());

        keys.add(key);
        idsByKey.put(key, id);

        return id;
    }

    public DefinitionId idOf(String key) {
        if (key == null) {
            return null;
        }

        return idsByKey.get(key);
    }

    @Override
    public DefinitionId resolve(String key) {
        return idOf(key);
    }

    public String keyOf(DefinitionId id) {
        if (id == null) {
            return null;
        }

        int index = id.asInt();

        if (index >= keys.size()) {
            return null;
        }

        return keys.get(index);
    }

    public int size() {
        return keys.size();
    }

    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }
}