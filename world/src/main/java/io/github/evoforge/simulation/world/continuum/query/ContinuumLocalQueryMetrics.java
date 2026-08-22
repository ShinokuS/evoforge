package io.github.evoforge.simulation.world.continuum.query;

/** Small, human-readable summary of one local-query batch. */
public record ContinuumLocalQueryMetrics(
        int consumerRequests,
        int totalRegionUses,
        int uniqueRegions,
        int reusedRegionUses,
        long cacheHits,
        long cacheMisses,
        long pageLoads,
        long sharedWaits,
        int residentPages,
        long residentPayloadBytes,
        long elapsedNanos) {
}
