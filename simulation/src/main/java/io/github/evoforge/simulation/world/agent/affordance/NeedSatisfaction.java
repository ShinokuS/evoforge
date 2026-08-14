package io.github.evoforge.simulation.world.agent.affordance;

import io.github.evoforge.simulation.world.agent.CapabilityId;
import io.github.evoforge.simulation.world.agent.need.NeedId;

/** Immutable advertised effect with an optional finite consumable-stock cost. */
public record NeedSatisfaction(
        NeedId needId,
        long amount,
        long consumedQuantity,
        CapabilityId requiredCapability) {

    public NeedSatisfaction {
        if (needId == null) throw new IllegalArgumentException("needId must not be null");
        if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
        if (consumedQuantity < 0) throw new IllegalArgumentException("consumedQuantity must be >= 0");
    }

    public NeedSatisfaction(NeedId needId, long amount, CapabilityId requiredCapability) {
        this(needId, amount, 0L, requiredCapability);
    }

    public boolean consumesStock() {
        return consumedQuantity > 0;
    }
}
