package io.github.evoforge.simulation.agents.opportunity;

import io.github.evoforge.simulation.world.object.ObjectId;

/** Terminal provider-owned completion of one accepted opportunity use. */
public record OpportunityUseCompletion(
        OpportunityUseActionId actionId,
        ObjectId agentId,
        OpportunityTarget target,
        InteractionSite site,
        long startedTick,
        long completedTick,
        OpportunityUseResult result) {

    public OpportunityUseCompletion {
        if (actionId == null || agentId == null || target == null || site == null || result == null) {
            throw new IllegalArgumentException("opportunity use completion values must not be null");
        }
        if (startedTick < 0 || completedTick < startedTick) {
            throw new IllegalArgumentException("invalid opportunity use completion ticks");
        }
    }
}
