package io.github.evoforge.visualizer.continuum;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/** Small LRU cache for disposable presentation resources such as map textures. */
public final class BoundedRenderCache<K, V> implements AutoCloseable {
    private final int maxEntries;
    private final Function<K, V> factory;
    private final Consumer<V> disposer;
    private final LinkedHashMap<K, V> entries = new LinkedHashMap<>(16, 0.75f, true);

    public BoundedRenderCache(int maxEntries, Function<K, V> factory, Consumer<V> disposer) {
        if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be > 0");
        if (factory == null || disposer == null) throw new IllegalArgumentException("factory/disposer must not be null");
        this.maxEntries = maxEntries;
        this.factory = factory;
        this.disposer = disposer;
    }

    public V get(K key) {
        V existing = entries.get(key);
        if (existing != null) return existing;
        V created = factory.apply(key);
        entries.put(key, created);
        evictOverflow();
        return created;
    }

    public int size() {
        return entries.size();
    }

    public int maxEntries() {
        return maxEntries;
    }

    public boolean contains(K key) {
        return entries.containsKey(key);
    }

    public void clear() {
        for (V value : entries.values()) disposer.accept(value);
        entries.clear();
    }

    @Override
    public void close() {
        clear();
    }

    private void evictOverflow() {
        while (entries.size() > maxEntries) {
            Map.Entry<K, V> eldest = entries.entrySet().iterator().next();
            entries.remove(eldest.getKey());
            disposer.accept(eldest.getValue());
        }
    }
}
