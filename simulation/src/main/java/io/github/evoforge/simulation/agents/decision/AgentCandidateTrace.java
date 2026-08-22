package io.github.evoforge.simulation.agents.decision;

/** One perceived possibility recorded on the common deterministic Utility scale. */
public record AgentCandidateTrace(
        String providerId,
        String targetKey,
        int x,
        int y,
        int z,
        int distance,
        long expectedBenefit,
        long pressure,
        long relief,
        long travel,
        long utility,
        String motivation) {

    public AgentCandidateTrace {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        if (targetKey == null || targetKey.isBlank()) {
            throw new IllegalArgumentException("targetKey must not be blank");
        }
        if (distance < 0) {
            throw new IllegalArgumentException("distance must be >= 0");
        }
        if (expectedBenefit <= 0L || utility <= 0L) {
            throw new IllegalArgumentException("benefit and utility must be > 0");
        }
        if (motivation == null || motivation.isBlank()) {
            throw new IllegalArgumentException("motivation must not be blank");
        }
    }
}
