package io.github.evoforge.simulation.agents.decision;

import io.github.evoforge.simulation.world.object.ObjectId;
import java.util.List;

/** Immutable explanation of one autonomous decision pass. */
public record AgentDecisionTrace(
        long tick,
        ObjectId agentId,
        List<AgentCandidateTrace> candidates,
        AgentCandidateTrace selected) {

    public AgentDecisionTrace {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must be >= 0");
        }
        if (agentId == null) {
            throw new IllegalArgumentException("agentId must not be null");
        }
        if (candidates == null) {
            throw new IllegalArgumentException("candidates must not be null");
        }
        candidates = List.copyOf(candidates);
        for (AgentCandidateTrace candidate : candidates) {
            if (candidate == null) {
                throw new IllegalArgumentException("candidate must not be null");
            }
        }
        if (selected != null && !candidates.contains(selected)) {
            throw new IllegalArgumentException("selected candidate must belong to candidates");
        }
    }
}
