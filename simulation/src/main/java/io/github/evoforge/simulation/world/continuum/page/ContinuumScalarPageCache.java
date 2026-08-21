package io.github.evoforge.simulation.world.continuum.page;

import io.github.evoforge.simulation.world.continuum.field.ContinuumMaterializer;
import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded LRU cache for scalar proof pages.
 *
 * <p>The cache is never authoritative: eviction changes only resident representation. A later request
 * rematerializes the page from the authoritative field. The byte budget counts scalar payload bytes,
 * not JVM object overhead; full heap profiling remains a separate performance gate.</p>
 */
public final class ContinuumScalarPageCache {
    private final ContinuumPageLayout layout;
    private final ContinuumMaterializer materializer;
    private final int maxResidentPages;
    private final long maxResidentPayloadBytes;
    private final LinkedHashMap<ContinuumPageKey, ContinuumScalarPage> pages =
            new LinkedHashMap<>(16, 0.75f, true);

    private long hits;
    private long misses;
    private long loads;
    private long evictions;
    private long residentPayloadBytes;

    public ContinuumScalarPageCache(
            ContinuumPageLayout layout,
            ContinuumMaterializer materializer,
            int maxResidentPages,
            long maxResidentPayloadBytes) {
        if (layout == null || materializer == null) {
            throw new IllegalArgumentException("layout and materializer must not be null");
        }
        if (!layout.domain().equals(materializer.domain())) {
            throw new IllegalArgumentException("layout and materializer must use the same logical domain");
        }
        if (maxResidentPages <= 0) {
            throw new IllegalArgumentException("maxResidentPages must be > 0");
        }
        if (maxResidentPayloadBytes <= 0L) {
            throw new IllegalArgumentException("maxResidentPayloadBytes must be > 0");
        }
        this.layout = layout;
        this.materializer = materializer;
        this.maxResidentPages = maxResidentPages;
        this.maxResidentPayloadBytes = maxResidentPayloadBytes;
    }

    public ContinuumScalarPage page(ContinuumPageKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }

        ContinuumScalarPage cached = pages.get(key);
        if (cached != null) {
            hits++;
            return cached;
        }

        ContinuumSampleWindow window = layout.windowFor(key);
        long payloadBytes = layout.payloadBytesFor(key);
        if (payloadBytes > maxResidentPayloadBytes) {
            throw new IllegalArgumentException("one page exceeds the cache payload-byte budget");
        }

        misses++;
        ContinuumScalarPage loaded = materializer.materialize(window);
        loads++;

        evictUntilFits(payloadBytes);
        pages.put(key, loaded);
        residentPayloadBytes = Math.addExact(residentPayloadBytes, payloadBytes);
        return loaded;
    }

    public boolean isResident(ContinuumPageKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        return pages.containsKey(key);
    }

    /** Resident keys from least-recently used to most-recently used. */
    public List<ContinuumPageKey> residentKeys() {
        return List.copyOf(pages.keySet());
    }

    public ContinuumPageCacheMetrics metrics() {
        return new ContinuumPageCacheMetrics(
                hits,
                misses,
                loads,
                evictions,
                pages.size(),
                residentPayloadBytes,
                maxResidentPages,
                maxResidentPayloadBytes);
    }

    private void evictUntilFits(long incomingPayloadBytes) {
        while (!pages.isEmpty()
                && (pages.size() >= maxResidentPages
                        || residentPayloadBytes > maxResidentPayloadBytes - incomingPayloadBytes)) {
            Map.Entry<ContinuumPageKey, ContinuumScalarPage> eldest = pages.entrySet().iterator().next();
            long removedBytes = layout.payloadBytesFor(eldest.getKey());
            pages.remove(eldest.getKey());
            residentPayloadBytes -= removedBytes;
            evictions++;
        }
    }
}
