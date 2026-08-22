package io.github.evoforge.simulation.agents.affordance;

import io.github.evoforge.simulation.agents.CapabilityId;
import io.github.evoforge.simulation.agents.need.NeedId;

/** Immutable advertised effect with optional finite-stock cost and provider-owned use duration. */
public record NeedSatisfaction(
        NeedId needId,
        long amount,
        long consumedQuantity,
        long useDurationTicks,
        CapabilityId requiredCapability) {

    public NeedSatisfaction {
        if (needId == null) throw new IllegalArgumentException("needId must not be null");
        if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
        if (consumedQuantity < 0) throw new IllegalArgumentException("consumedQuantity must be >= 0");
        if (useDurationTicks < 0) throw new IllegalArgumentException("useDurationTicks must be >= 0");
    }

    public NeedSatisfaction(
            NeedId needId,
            long amount,
            long consumedQuantity,
            CapabilityId requiredCapability) {
        this(needId, amount, consumedQuantity, 0L, requiredCapability);
    }

    public NeedSatisfaction(NeedId needId, long amount, CapabilityId requiredCapability) {
        this(needId, amount, 0L, 0L, requiredCapability);
    }

    public boolean consumesStock() {
        return consumedQuantity > 0;
    }
}
