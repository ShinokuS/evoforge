package io.github.evoforge.simulation.world.agent.decision;

import io.github.evoforge.simulation.world.object.ObjectId;

/** One scored, perceived possibility recorded for deterministic AI diagnostics. */
public record AgentCandidateTrace(
        String providerId,
        ObjectId sourceId,
        int x,
        int y,
        int z,
        int distance,
        long expectedBenefit,
        long score,
        String motivation) {

    public AgentCandidateTrace {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        if (sourceId == null) {
            throw new IllegalArgumentException("sourceId must not be null");
        }
        if (distance < 0) {
            throw new IllegalArgumentException("distance must be >= 0");
        }
        if (expectedBenefit <= 0 || score <= 0) {
            throw new IllegalArgumentException("benefit and score must be > 0");
        }
        if (motivation == null || motivation.isBlank()) {
            throw new IllegalArgumentException("motivation must not be blank");
        }
    }
}
