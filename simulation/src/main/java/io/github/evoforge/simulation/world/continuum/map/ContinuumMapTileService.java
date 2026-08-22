package io.github.evoforge.simulation.world.continuum.map;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Bounded asynchronous cache for derived map tiles.
 *
 * <p>Visible work always outranks speculative prefetch. Identical concurrent requests share one
 * generation job, stale pending work can be discarded when the camera moves, and ready CPU tiles
 * use bounded LRU retention.</p>
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
    private final LinkedHashMap<ContinuumMapTileKey, PendingJob> visiblePending = new LinkedHashMap<>();
    private final LinkedHashMap<ContinuumMapTileKey, PendingJob> prefetchPending = new LinkedHashMap<>();
    private final Map<ContinuumMapTileKey, CompletableFuture<ContinuumMapTile>> inFlight = new LinkedHashMap<>();

    private long activeRevision;
    private ContinuumMapTile rootFallback;
    private int runningJobs;
    private long requests;
    private long readyHits;
    private long singleFlightJoins;
    private long generatedTiles;
    private long droppedPendingJobs;
    private long cancelledObsoleteJobs;
    private long promotedVisibleJobs;

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
        cancelAllPending("map source revision changed");
        rootFallback = generator.generate(new ContinuumMapTileKey(maxLevel, 0L, 0L, revision));
    }

    public synchronized long activeRevision() {
        return activeRevision;
    }

    /** Compatibility path: an explicit request is considered visible/high priority. */
    public CompletableFuture<ContinuumMapTile> request(ContinuumMapTileKey key) {
        return requestVisible(key);
    }

    /** Requests a tile required by the current viewport. Visible requests always run first. */
    public synchronized CompletableFuture<ContinuumMapTile> requestVisible(ContinuumMapTileKey key) {
        return request(key, Priority.VISIBLE);
    }

    /** Requests speculative work which may be dropped before any visible request. */
    public synchronized CompletableFuture<ContinuumMapTile> requestPrefetch(ContinuumMapTileKey key) {
        return request(key, Priority.PREFETCH);
    }

    /**
     * Cancels queued work which is no longer useful to the current camera demand.
     *
     * <p>Already-running work is allowed to finish because the generic Executor contract does not
     * expose safe interruption handles. It remains bounded by {@code maxWorkers}.</p>
     */
    public synchronized void retainPendingDemand(Set<ContinuumMapTileKey> demanded) {
        if (demanded == null) throw new IllegalArgumentException("demanded must not be null");
        cancelOutside(visiblePending, demanded);
        cancelOutside(prefetchPending, demanded);
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
                cancelledObsoleteJobs,
                promotedVisibleJobs,
                ready.size() + rootTiles,
                residentBytes + rootBytes,
                visiblePending.size() + prefetchPending.size(),
                visiblePending.size(),
                prefetchPending.size(),
                runningJobs,
                maxReadyTiles + 1,
                maxOutstandingJobs);
    }

    public synchronized List<ContinuumMapTileKey> residentKeys() {
        List<ContinuumMapTileKey> keys = new ArrayList<>(ready.keySet());
        if (rootFallback != null) keys.add(rootFallback.key());
        return List.copyOf(keys);
    }

    private CompletableFuture<ContinuumMapTile> request(ContinuumMapTileKey key, Priority priority) {
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
            if (priority == Priority.VISIBLE) promoteToVisible(key);
            return existing;
        }

        while (inFlight.size() >= maxOutstandingJobs) {
            PendingJob dropped = removeEldest(prefetchPending);
            if (dropped == null) dropped = removeEldest(visiblePending);
            if (dropped == null) {
                return CompletableFuture.failedFuture(new RejectedExecutionException("map tile job budget exhausted"));
            }
            inFlight.remove(dropped.key());
            droppedPendingJobs++;
            dropped.future().completeExceptionally(new CancellationException("superseded by newer map demand"));
        }

        CompletableFuture<ContinuumMapTile> future = new CompletableFuture<>();
        PendingJob job = new PendingJob(key, future);
        inFlight.put(key, future);
        queueFor(priority).put(key, job);
        pump();
        return future;
    }

    private void promoteToVisible(ContinuumMapTileKey key) {
        PendingJob pending = prefetchPending.remove(key);
        if (pending != null) {
            visiblePending.put(key, pending);
            promotedVisibleJobs++;
        }
    }

    private void cancelOutside(
            LinkedHashMap<ContinuumMapTileKey, PendingJob> queue,
            Set<ContinuumMapTileKey> demanded) {
        List<ContinuumMapTileKey> obsolete = new ArrayList<>();
        for (ContinuumMapTileKey key : queue.keySet()) {
            if (!demanded.contains(key)) obsolete.add(key);
        }
        for (ContinuumMapTileKey key : obsolete) {
            PendingJob job = queue.remove(key);
            if (job == null) continue;
            inFlight.remove(key);
            cancelledObsoleteJobs++;
            job.future().completeExceptionally(new CancellationException("map demand moved elsewhere"));
        }
    }

    private void cancelAllPending(String reason) {
        List<PendingJob> cancelled = new ArrayList<>(visiblePending.values());
        cancelled.addAll(prefetchPending.values());
        visiblePending.clear();
        prefetchPending.clear();
        for (PendingJob job : cancelled) {
            inFlight.remove(job.key());
            job.future().completeExceptionally(new CancellationException(reason));
        }
    }

    private LinkedHashMap<ContinuumMapTileKey, PendingJob> queueFor(Priority priority) {
        return priority == Priority.VISIBLE ? visiblePending : prefetchPending;
    }

    private static PendingJob removeEldest(LinkedHashMap<ContinuumMapTileKey, PendingJob> queue) {
        if (queue.isEmpty()) return null;
        Map.Entry<ContinuumMapTileKey, PendingJob> eldest = queue.entrySet().iterator().next();
        queue.remove(eldest.getKey());
        return eldest.getValue();
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
        while (runningJobs < maxWorkers && (!visiblePending.isEmpty() || !prefetchPending.isEmpty())) {
            PendingJob job = removeEldest(!visiblePending.isEmpty() ? visiblePending : prefetchPending);
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

        boolean accepted;
        synchronized (this) {
            runningJobs--;
            inFlight.remove(job.key());
            accepted = failure == null && job.key().sourceRevision() == activeRevision;
            if (accepted) {
                putReady(tile);
                generatedTiles++;
            }
            pump();
        }

        if (accepted) {
            job.future().complete(tile);
        } else if (failure != null) {
            job.future().completeExceptionally(failure);
        } else {
            job.future().completeExceptionally(new CancellationException("map source revision changed while generating"));
        }
    }

    private void putReady(ContinuumMapTile tile) {
        ready.put(tile.key(), tile);
        while (ready.size() > maxReadyTiles) {
            ContinuumMapTileKey eldest = ready.entrySet().iterator().next().getKey();
            ready.remove(eldest);
        }
    }

    private enum Priority {
        VISIBLE,
        PREFETCH
    }

    private record PendingJob(ContinuumMapTileKey key, CompletableFuture<ContinuumMapTile> future) {}

    public record Metrics(
            long requests,
            long readyHits,
            long singleFlightJoins,
            long generatedTiles,
            long droppedPendingJobs,
            long cancelledObsoleteJobs,
            long promotedVisibleJobs,
            int residentTiles,
            long residentPayloadBytes,
            int pendingJobs,
            int visiblePendingJobs,
            int prefetchPendingJobs,
            int runningJobs,
            int maxResidentTiles,
            int maxOutstandingJobs) {}
}
