package io.github.evoforge.simulation.world.agent.decision;

import io.github.evoforge.simulation.world.object.ObjectId;

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

    /** Temporary presentation bridge; remove once the HUD uses source-neutral targetKey directly. */
    @Deprecated(forRemoval = true)
    public ObjectId sourceId() {
        if (!targetKey.startsWith("object:")) return null;
        long value = Long.parseLong(targetKey.substring("object:".length()));
        return ObjectId.of((int) (value & 0xFFFF_FFFFL), (int) (value >>> 32));
    }

    /** Temporary presentation bridge; final score is the common Utility value. */
    @Deprecated(forRemoval = true)
    public long score() {
        return utility;
    }
}
