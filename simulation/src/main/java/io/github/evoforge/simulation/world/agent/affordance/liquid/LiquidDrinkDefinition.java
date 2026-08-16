package io.github.evoforge.simulation.world.agent.affordance.liquid;

import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidTypeId;
import io.github.evoforge.simulation.world.mechanics.interaction.InteractionReachProfile;

/** Immutable physiology/access data for satisfying one Need by drinking one free-liquid type. */
public record LiquidDrinkDefinition(
        NeedId needId,
        LiquidTypeId liquidType,
        long requestedMillilitersPerUse,
        long needReliefPerFullUse,
        long useDurationTicks,
        InteractionReachProfile reach) {

    public LiquidDrinkDefinition {
        if (needId == null || liquidType == null || reach == null) {
            throw new IllegalArgumentException("liquid drink definition identities must not be null");
        }
        if (requestedMillilitersPerUse <= 0L || needReliefPerFullUse <= 0L) {
            throw new IllegalArgumentException("drink amount and need relief must be > 0");
        }
        if (useDurationTicks < 0L) {
            throw new IllegalArgumentException("useDurationTicks must be >= 0");
        }
    }
}
