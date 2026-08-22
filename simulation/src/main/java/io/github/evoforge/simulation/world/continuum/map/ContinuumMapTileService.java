package io.github.evoforge.simulation.world.continuum.map;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Bounded asynchronous cache for derived map tiles.
 *
 * <p>Identical concurrent requests share one generation job. Ready CPU tiles use LRU eviction.
 * Pending work is also bounded, so rapid camera movement cannot grow an infinite job backlog.</p>
 */
public final class ContinuumMapTileService {
    private final ContinuumMapTileGenerator generator;
    private final Executor executor;
    private final int maxLevel;
    private final int maxReadyTiles;
    private final int maxOutstandingJobs;
    private final int maxWorkers;

    private final LinkedHashMap<ContinuumMapTileKey, ContinuumMapTile> ready =
            new LinkedHashMap<>(16, 0.75f, true);
    private final LinkedHashMap<ContinuumMapTileKey, PendingJob> pending = new LinkedHashMap<>();
    private final Map<ContinuumMapTileKey, CompletableFuture<ContinuumMapTile>> inFlight = new LinkedHashMap<>();

    private long activeRevision;
    private ContinuumMapTile rootFallback;
    private int runningJobs;
    private long requests;
    private long readyHits;
    private long singleFlightJoins;
    private long generatedTiles;
    private long droppedPendingJobs;

    public ContinuumMapTileService(
            ContinuumMapTileGenerator generator,
            Executor executor,
            int maxLevel,
            int maxReadyTiles,
            int maxOutstandingJobs,
            int maxWorkers) {
        if (generator == null) throw new IllegalArgumentException("generator must not be null");
        if (executor == null) throw new IllegalArgumentException("executor must not be null");
        if (maxLevel < 0) throw new IllegalArgumentException("maxLevel must be >= 0");
        if (maxReadyTiles <= 0) throw new IllegalArgumentException("maxReadyTiles must be > 0");
        if (maxOutstandingJobs <= 0) throw new IllegalArgumentException("maxOutstandingJobs must be > 0");
        if (maxWorkers <= 0 || maxWorkers > maxOutstandingJobs) {
            throw new IllegalArgumentException("maxWorkers must be in 1..maxOutstandingJobs");
        }
        this.generator = generator;
        this.executor = executor;
        this.maxLevel = maxLevel;
        this.maxReadyTiles = maxReadyTiles;
        this.maxOutstandingJobs = maxOutstandingJobs;
        this.maxWorkers = maxWorkers;
        setRevision(0L);
    }

    /** Switches to a new authoritative source revision. Old ready/pending tiles cannot be reused. */
    public synchronized void setRevision(long revision) {
        if (rootFallback != null && activeRevision == revision) return;
        activeRevision = revision;
        ready.clear();

        List<PendingJob> cancelled = new ArrayList<>(pending.values());
        pending.clear();
        for (PendingJob job : cancelled) {
            inFlight.remove(job.key());
            job.future().completeExceptionally(new CancellationException("map source revision changed"));
        }

        rootFallback = generator.generate(new ContinuumMapTileKey(maxLevel, 0L, 0L, revision));
    }

    public synchronized long activeRevision() {
        return activeRevision;
    }

    /** Requests a target tile. The returned future is shared for identical concurrent misses. */
    public synchronized CompletableFuture<ContinuumMapTile> request(ContinuumMapTileKey key) {
        requireCurrentRevision(key);
        requests++;

        if (rootFallback.key().equals(key)) {
            readyHits++;
            return CompletableFuture.completedFuture(rootFallback);
        }

        ContinuumMapTile cached = ready.get(key);
        if (cached != null) {
            readyHits++;
            return CompletableFuture.completedFuture(cached);
        }

        CompletableFuture<ContinuumMapTile> existing = inFlight.get(key);
        if (existing != null) {
            singleFlightJoins++;
            return existing;
        }

        while (inFlight.size() >= maxOutstandingJobs && !pending.isEmpty()) {
            Map.Entry<ContinuumMapTileKey, PendingJob> eldest = pending.entrySet().iterator().next();
            pending.remove(eldest.getKey());
            inFlight.remove(eldest.getKey());
            droppedPendingJobs++;
            eldest.getValue().future().completeExceptionally(new CancellationException("superseded by newer map demand"));
        }
        if (inFlight.size() >= maxOutstandingJobs) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("map tile job budget exhausted"));
        }

        CompletableFuture<ContinuumMapTile> future = new CompletableFuture<>();
        PendingJob job = new PendingJob(key, future);
        inFlight.put(key, future);
        pending.put(key, job);
        pump();
        return future;
    }

    /** Returns the requested tile if ready, otherwise the nearest ready ancestor for the same revision. */
    public synchronized Optional<ContinuumMapTile> bestAvailable(ContinuumMapTileKey desired) {
        requireCurrentRevision(desired);
        ContinuumMapTileKey candidate = desired;
        while (candidate.level() < maxLevel) {
            ContinuumMapTile tile = ready.get(candidate);
            if (tile != null) return Optional.of(tile);
            candidate = candidate.parent();
        }
        ContinuumMapTile tile = ready.get(candidate);
        if (tile != null) return Optional.of(tile);
        if (rootFallback != null && rootFallback.key().sourceRevision() == desired.sourceRevision()) {
            return Optional.of(rootFallback);
        }
        return Optional.empty();
    }

    public synchronized Metrics metrics() {
        long residentBytes = 0L;
        for (ContinuumMapTile tile : ready.values()) residentBytes += tile.payloadBytes();
        int rootTiles = rootFallback == null ? 0 : 1;
        long rootBytes = rootFallback == null ? 0L : rootFallback.payloadBytes();
        return new Metrics(
                requests,
                readyHits,
                singleFlightJoins,
                generatedTiles,
                droppedPendingJobs,
                ready.size() + rootTiles,
                residentBytes + rootBytes,
                pending.size(),
                runningJobs,
                maxReadyTiles + 1,
                maxOutstandingJobs);
    }

    public synchronized List<ContinuumMapTileKey> residentKeys() {
        List<ContinuumMapTileKey> keys = new ArrayList<>(ready.keySet());
        if (rootFallback != null) keys.add(rootFallback.key());
        return List.copyOf(keys);
    }

    private void requireCurrentRevision(ContinuumMapTileKey key) {
        if (key == null) throw new IllegalArgumentException("key must not be null");
        if (key.level() > maxLevel) throw new IllegalArgumentException("key level exceeds maxLevel");
        if (key.sourceRevision() != activeRevision) {
            throw new IllegalArgumentException("tile revision is stale for this service");
        }
    }

    /** Caller holds this object's monitor. */
    private void pump() {
        while (runningJobs < maxWorkers && !pending.isEmpty()) {
            Map.Entry<ContinuumMapTileKey, PendingJob> entry = pending.entrySet().iterator().next();
            pending.remove(entry.getKey());
            PendingJob job = entry.getValue();
            runningJobs++;
            try {
                executor.execute(() -> generate(job));
            } catch (RuntimeException failure) {
                runningJobs--;
                inFlight.remove(job.key());
                job.future().completeExceptionally(failure);
            }
        }
    }

    private void generate(PendingJob job) {
        ContinuumMapTile tile = null;
        Throwable failure = null;
        try {
            tile = generator.generate(job.key());
        } catch (Throwable thrown) {
            failure = thrown;
        }

        synchronized (this) {
            runningJobs--;
            inFlight.remove(job.key());
            if (failure == null && job.key().sourceRevision() == activeRevision) {
                putReady(tile);
                generatedTiles++;
            }
            pump();
        }

        if (failure == null) job.future().complete(tile);
        else job.future().completeExceptionally(failure);
    }

    private void putReady(ContinuumMapTile tile) {
        ready.put(tile.key(), tile);
        while (ready.size() > maxReadyTiles) {
            ContinuumMapTileKey eldest = ready.entrySet().iterator().next().getKey();
            ready.remove(eldest);
        }
    }

    private record PendingJob(ContinuumMapTileKey key, CompletableFuture<ContinuumMapTile> future) {}

    public record Metrics(
            long requests,
            long readyHits,
            long singleFlightJoins,
            long generatedTiles,
            long droppedPendingJobs,
            int residentTiles,
            long residentPayloadBytes,
            int pendingJobs,
            int runningJobs,
            int maxResidentTiles,
            int maxOutstandingJobs) {}
}
