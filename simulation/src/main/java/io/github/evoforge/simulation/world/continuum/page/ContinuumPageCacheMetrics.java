package io.github.evoforge.simulation.world.continuum.page;

import java.util.Objects;

/** Immutable observer snapshot of technical page-cache activity. */
public final class ContinuumPageCacheMetrics {
    private final long hits;
    private final long misses;
    private final long loads;
    private final long sharedWaits;
    private final long evictions;
    private final int residentPages;
    private final long residentPayloadBytes;
    private final int maxResidentPages;
    private final long maxResidentPayloadBytes;

    /** Compatibility constructor for callers that predate Stage 1 single-flight metrics. */
    public ContinuumPageCacheMetrics(
            long hits,
            long misses,
            long loads,
            long evictions,
            int residentPages,
            long residentPayloadBytes,
            int maxResidentPages,
            long maxResidentPayloadBytes) {
        this(hits, misses, loads, 0L, evictions, residentPages, residentPayloadBytes,
                maxResidentPages, maxResidentPayloadBytes);
    }

    public ContinuumPageCacheMetrics(
            long hits,
            long misses,
            long loads,
            long sharedWaits,
            long evictions,
            int residentPages,
            long residentPayloadBytes,
            int maxResidentPages,
            long maxResidentPayloadBytes) {
        this.hits = hits;
        this.misses = misses;
        this.loads = loads;
        this.sharedWaits = sharedWaits;
        this.evictions = evictions;
        this.residentPages = residentPages;
        this.residentPayloadBytes = residentPayloadBytes;
        this.maxResidentPages = maxResidentPages;
        this.maxResidentPayloadBytes = maxResidentPayloadBytes;
    }

    public long hits() { return hits; }
    public long misses() { return misses; }
    public long loads() { return loads; }
    public long sharedWaits() { return sharedWaits; }
    public long evictions() { return evictions; }
    public int residentPages() { return residentPages; }
    public long residentPayloadBytes() { return residentPayloadBytes; }
    public int maxResidentPages() { return maxResidentPages; }
    public long maxResidentPayloadBytes() { return maxResidentPayloadBytes; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ContinuumPageCacheMetrics that)) return false;
        return hits == that.hits
                && misses == that.misses
                && loads == that.loads
                && sharedWaits == that.sharedWaits
                && evictions == that.evictions
                && residentPages == that.residentPages
                && residentPayloadBytes == that.residentPayloadBytes
                && maxResidentPages == that.maxResidentPages
                && maxResidentPayloadBytes == that.maxResidentPayloadBytes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hits, misses, loads, sharedWaits, evictions, residentPages,
                residentPayloadBytes, maxResidentPages, maxResidentPayloadBytes);
    }

    @Override
    public String toString() {
        return "ContinuumPageCacheMetrics["
                + "hits=" + hits
                + ", misses=" + misses
                + ", loads=" + loads
                + ", sharedWaits=" + sharedWaits
                + ", evictions=" + evictions
                + ", residentPages=" + residentPages
                + ", residentPayloadBytes=" + residentPayloadBytes
                + ", maxResidentPages=" + maxResidentPages
                + ", maxResidentPayloadBytes=" + maxResidentPayloadBytes
                + ']';
    }
}
