package io.github.evoforge.simulation.world.continuum.page;

import io.github.evoforge.simulation.world.continuum.field.ContinuumMaterializer;
import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded LRU cache for scalar proof pages.
 *
 * <p>The cache is never authoritative: eviction changes only resident representation. A later request
 * rematerializes the page from the authoritative field. Concurrent requests for the same missing page
 * share one in-progress materialization instead of starting duplicate work.</p>
 */
public final class ContinuumScalarPageCache {
    private final ContinuumPageLayout layout;
    private final ContinuumMaterializer materializer;
    private final int maxResidentPages;
    private final long maxResidentPayloadBytes;
    private final Object residencyLock = new Object();
    private final LinkedHashMap<ContinuumPageKey, ContinuumScalarPage> pages =
            new LinkedHashMap<>(16, 0.75f, true);
    private final ConcurrentHashMap<ContinuumPageKey, CompletableFuture<ContinuumScalarPage>> inFlight =
            new ConcurrentHashMap<>();

    private long hits;
    private long misses;
    private long loads;
    private long sharedWaits;
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

        synchronized (residencyLock) {
            ContinuumScalarPage cached = pages.get(key);
            if (cached != null) {
                hits++;
                return cached;
            }
            misses++;
        }

        ContinuumSampleWindow window = layout.windowFor(key);
        long payloadBytes = layout.payloadBytesFor(key);
        if (payloadBytes > maxResidentPayloadBytes) {
            throw new IllegalArgumentException("one page exceeds the cache payload-byte budget");
        }

        CompletableFuture<ContinuumScalarPage> mine = new CompletableFuture<>();
        CompletableFuture<ContinuumScalarPage> existing = inFlight.putIfAbsent(key, mine);
        if (existing != null) {
            synchronized (residencyLock) {
                sharedWaits++;
            }
            return join(existing);
        }

        try {
            ContinuumScalarPage loaded = materializer.materialize(window);
            synchronized (residencyLock) {
                ContinuumScalarPage alreadyResident = pages.get(key);
                if (alreadyResident != null) {
                    loaded = alreadyResident;
                } else {
                    evictUntilFits(payloadBytes);
                    pages.put(key, loaded);
                    residentPayloadBytes = Math.addExact(residentPayloadBytes, payloadBytes);
                    loads++;
                }
            }
            mine.complete(loaded);
            return loaded;
        } catch (Throwable failure) {
            mine.completeExceptionally(failure);
            if (failure instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("page materialization failed", failure);
        } finally {
            inFlight.remove(key, mine);
        }
    }

    public boolean isResident(ContinuumPageKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        synchronized (residencyLock) {
            return pages.containsKey(key);
        }
    }

    /** Resident keys from least-recently used to most-recently used. */
    public List<ContinuumPageKey> residentKeys() {
        synchronized (residencyLock) {
            return List.copyOf(pages.keySet());
        }
    }

    public ContinuumPageCacheMetrics metrics() {
        synchronized (residencyLock) {
            return new ContinuumPageCacheMetrics(
                    hits,
                    misses,
                    loads,
                    sharedWaits,
                    evictions,
                    pages.size(),
                    residentPayloadBytes,
                    maxResidentPages,
                    maxResidentPayloadBytes);
        }
    }

    private static ContinuumScalarPage join(CompletableFuture<ContinuumScalarPage> future) {
        try {
            return future.join();
        } catch (CompletionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw failure;
        }
    }

    /** Must be called while {@link #residencyLock} is held. */
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
