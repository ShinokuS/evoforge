package io.github.evoforge.simulation.agents.opportunity;

/** Provider-owned evidence on the common deterministic Utility scale. */
public record OpportunityEvaluation(
        long expectedBenefit,
        long pressure,
        long relief,
        long travel,
        String motivation) {

    public static final long SCALE = 1_000_000L;

    public OpportunityEvaluation {
        if (expectedBenefit <= 0L) {
            throw new IllegalArgumentException("expectedBenefit must be > 0");
        }
        requireNormalized("pressure", pressure);
        requireNormalized("relief", relief);
        requireNormalized("travel", travel);
        if (motivation == null || motivation.isBlank()) {
            throw new IllegalArgumentException("motivation must not be blank");
        }
    }

    private static void requireNormalized(String name, long value) {
        if (value <= 0L || value > SCALE) {
            throw new IllegalArgumentException(name + " must be in [1," + SCALE + "]");
        }
    }
}
