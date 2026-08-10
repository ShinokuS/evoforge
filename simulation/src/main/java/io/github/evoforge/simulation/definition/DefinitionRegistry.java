package io.github.evoforge.simulation.definition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class DefinitionRegistry
        implements DefinitionCatalog {

    private static final Pattern KEY_PATTERN = Pattern.compile(
            "[a-z0-9][a-z0-9_.-]*:[a-z0-9][a-z0-9_.-]*");

    private final List<String> keys = new ArrayList<>();
    private final Map<String, DefinitionId> idsByKey = new HashMap<>();

    private boolean frozen;

    public DefinitionId register(String key) {
        if (key == null) {
            throw new IllegalArgumentException(
                    "key must not be null");
        }

        if (key.isBlank()) {
            throw new IllegalArgumentException(
                    "key must not be blank");
        }

        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException(
                    "invalid definition key: " + key);
        }

        if (frozen) {
            throw new IllegalStateException(
                    "registry is frozen");
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

    @Override
    public boolean contains(DefinitionId id) {
        if (id == null) {
            return false;
        }

        int index = id.asInt();

        return index >= 0 && index < keys.size();
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
