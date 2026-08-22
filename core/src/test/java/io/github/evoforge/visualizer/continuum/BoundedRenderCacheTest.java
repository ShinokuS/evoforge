package io.github.evoforge.visualizer.continuum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class BoundedRenderCacheTest {

    @Test
    void evictsLeastRecentlyUsedResourcesAndDisposesThem() {
        AtomicInteger disposed = new AtomicInteger();
        BoundedRenderCache<Integer, Resource> cache = new BoundedRenderCache<>(
                2,
                Resource::new,
                resource -> disposed.incrementAndGet());

        cache.get(1);
        cache.get(2);
        cache.get(1); // 2 becomes least recently used
        cache.get(3);

        assertEquals(2, cache.size());
        assertTrue(cache.contains(1));
        assertTrue(cache.contains(3));
        assertFalse(cache.contains(2));
        assertEquals(1, disposed.get());

        cache.close();
        assertEquals(3, disposed.get());
        assertEquals(0, cache.size());
    }

    private record Resource(int id) {}
}
