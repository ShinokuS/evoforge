package io.github.evoforge.simulation.agents.need.progression;

import io.github.evoforge.simulation.agents.need.NeedId;

/** Read-only diagnostic snapshot of one scheduled Need progression evaluation. */
public record NeedProgressionTrace(
        long tick,
        NeedId needId,
        long resolvedAmount,
        long appliedAmount,
        long levelAfter,
        long maxLevel) {

    public NeedProgressionTrace {
        if (tick < 0) throw new IllegalArgumentException("tick must be >= 0");
        if (needId == null) throw new IllegalArgumentException("needId must not be null");
        if (resolvedAmount < 0 || appliedAmount < 0) {
            throw new IllegalArgumentException("progression amounts must be >= 0");
        }
        if (levelAfter < 0 || maxLevel <= 0 || levelAfter > maxLevel) {
            throw new IllegalArgumentException("invalid Need levels in progression trace");
        }
    }
}
