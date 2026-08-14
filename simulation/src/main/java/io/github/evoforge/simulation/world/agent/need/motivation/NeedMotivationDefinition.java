package io.github.evoforge.simulation.world.agent.need.motivation;

import io.github.evoforge.simulation.world.agent.need.NeedId;

/** Independent threshold at which one need becomes worth autonomous environmental action. */
public record NeedMotivationDefinition(NeedId needId, long activationLevel) {
    public NeedMotivationDefinition {
        if (needId == null) throw new IllegalArgumentException("needId must not be null");
        if (activationLevel <= 0L) throw new IllegalArgumentException("activationLevel must be > 0");
    }
}
