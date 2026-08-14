package io.github.evoforge.simulation.world.agent.need.progression;

import io.github.evoforge.simulation.world.agent.need.NeedId;

/** Immutable intrinsic progression of one existing Need over simulation time. */
public record NeedProgressionDefinition(NeedId needId, long baseAmount, long intervalTicks) {
    public NeedProgressionDefinition {
        if (needId == null) throw new IllegalArgumentException("needId must not be null");
        if (baseAmount <= 0) throw new IllegalArgumentException("baseAmount must be > 0");
        if (intervalTicks <= 0) throw new IllegalArgumentException("intervalTicks must be > 0");
    }
}
