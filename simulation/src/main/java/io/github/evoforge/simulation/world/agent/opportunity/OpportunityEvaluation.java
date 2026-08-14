package io.github.evoforge.simulation.world.agent.opportunity;

/** Provider-owned utility evidence for one currently perceived opportunity. */
public record OpportunityEvaluation(
        long score,
        long expectedBenefit,
        String motivation) {

    public OpportunityEvaluation {
        if (score <= 0) {
            throw new IllegalArgumentException("score must be > 0");
        }
        if (expectedBenefit <= 0) {
            throw new IllegalArgumentException("expectedBenefit must be > 0");
        }
        if (motivation == null || motivation.isBlank()) {
            throw new IllegalArgumentException("motivation must not be blank");
        }
    }
}
