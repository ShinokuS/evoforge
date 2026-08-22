package io.github.evoforge.simulation.world.navigation.pathfinding;

/** Read-only diagnostic counters for the derived cluster connectivity cache. */
public record PathHierarchyIndexMetrics(
        int cachedClusters,
        long cacheHits,
        long cacheMisses,
        long rebuiltClusters,
        long navigationQueries,
        long localInvalidations,
        long globalInvalidations) {
}
