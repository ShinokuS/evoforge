package io.github.evoforge.simulation.world.agent.affordance;

import io.github.evoforge.simulation.world.agent.CapabilityId;
import io.github.evoforge.simulation.world.agent.need.NeedId;

/** Immutable advertised effect: using this source may reduce one need deficit. */
public record NeedSatisfaction(NeedId needId, long amount, CapabilityId requiredCapability) {

    public NeedSatisfaction {
        if (needId == null) {
            throw new IllegalArgumentException("needId must not be null");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
    }
}
