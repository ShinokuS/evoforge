package io.github.evoforge.simulation.world.agent.decision;

import io.github.evoforge.simulation.world.agent.opportunity.InteractionSite;

/** Read-only developer snapshot of the agent's currently committed continuing intent. */
public record AgentIntentTrace(
        AgentIntentPhase phase,
        String providerId,
        String targetKey,
        InteractionSite site,
        long startedTick,
        long expectedCompletionTick) {

    public AgentIntentTrace {
        if (phase == null) throw new IllegalArgumentException("phase must not be null");
        if (startedTick < 0) throw new IllegalArgumentException("startedTick must be >= 0");
        if (expectedCompletionTick < -1L) {
            throw new IllegalArgumentException("expectedCompletionTick must be -1 or >= startedTick");
        }
        if (expectedCompletionTick >= 0L && expectedCompletionTick < startedTick) {
            throw new IllegalArgumentException("expectedCompletionTick must be >= startedTick");
        }
    }
}
