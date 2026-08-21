package io.github.evoforge.simulation.world.continuum.page;

/** Immutable observer snapshot of technical page-cache activity. */
public record ContinuumPageCacheMetrics(
        long hits,
        long misses,
        long loads,
        long evictions,
        int residentPages,
        long residentPayloadBytes,
        int maxResidentPages,
        long maxResidentPayloadBytes) {
}
