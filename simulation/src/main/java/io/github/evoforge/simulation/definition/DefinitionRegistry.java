package io.github.evoforge.simulation.definition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

public final class DefinitionRegistry<I>
        implements DefinitionCatalog<I> {

    private static final Pattern KEY_PATTERN = Pattern.compile(
            "[a-z0-9][a-z0-9_.-]*:[a-z0-9][a-z0-9_.-]*");

    private final List<String> keys = new ArrayList<>();
    private final List<I> ids = new ArrayList<>();
    private final Map<String, I> idsByKey = new HashMap<>();
    private final IntFunction<I> idFactory;
    private final ToIntFunction<I> idIndexer;

    private boolean frozen;

    public DefinitionRegistry(
            IntFunction<I> idFactory,
            ToIntFunction<I> idIndexer) {
        if (idFactory == null) {
            throw new IllegalArgumentException("idFactory must not be null");
        }
        if (idIndexer == null) {
            throw new IllegalArgumentException("idIndexer must not be null");
        }
        this.idFactory = idFactory;
        this.idIndexer = idIndexer;
    }

    public I register(String key) {
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

        int index = keys.size();
        I id = idFactory.apply(index);

        if (id == null) {
            throw new IllegalStateException(
                    "idFactory returned null for index " + index);
        }

        int actualIndex = idIndexer.applyAsInt(id);

        if (actualIndex != index) {
            throw new IllegalStateException(
                    "idFactory/idIndexer mismatch: expected "
                            + index
                            + ", got "
                            + actualIndex);
        }

        keys.add(key);
        ids.add(id);
        idsByKey.put(key, id);

        return id;
    }

    public I idOf(String key) {
        if (key == null) {
            return null;
        }

        return idsByKey.get(key);
    }

    @Override
    public I resolve(String key) {
        return idOf(key);
    }

    public String keyOf(I id) {
        int index = registeredIndexOf(id);

        if (index < 0) {
            return null;
        }

        return keys.get(index);
    }

    @Override
    public boolean contains(I id) {
        return registeredIndexOf(id) >= 0;
    }

    private int registeredIndexOf(I id) {
        if (id == null) {
            return -1;
        }

        int index = idIndexer.applyAsInt(id);

        if (index < 0 || index >= ids.size()) {
            return -1;
        }

        if (!ids.get(index).equals(id)) {
            return -1;
        }

        return index;
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
