package io.github.evoforge.simulation.world.continuum.query;

import io.github.evoforge.simulation.world.continuum.page.ContinuumPageKey;
import java.util.List;
import java.util.Set;

/** Immutable result of one batched local-query pass. */
public record ContinuumLocalQueryBatch(
        long revision,
        List<ContinuumLocalScalarView> views,
        Set<ContinuumPageKey> sharedRegions,
        ContinuumLocalQueryMetrics metrics) {
    public ContinuumLocalQueryBatch {
        views = List.copyOf(views);
        sharedRegions = Set.copyOf(sharedRegions);
        if (metrics == null) {
            throw new IllegalArgumentException("metrics must not be null");
        }
    }
}
