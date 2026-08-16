package io.github.evoforge.simulation.world.agent.decision;

import io.github.evoforge.simulation.world.agent.opportunity.OpportunityEvaluation;

/** Shared fixed-point Utility calculations; providers contribute evidence, not private final scores. */
public final class UtilityMath {
    private UtilityMath() { }

    public static long score(OpportunityEvaluation evaluation) {
        if (evaluation == null) throw new IllegalArgumentException("evaluation must not be null");
        long sum = Math.addExact(
                Math.addExact(evaluation.pressure(), evaluation.relief()),
                evaluation.travel());
        return Math.max(1L, sum / 3L);
    }

    public static long ratio(long numerator, long denominator) {
        if (numerator <= 0L || denominator <= 0L) {
            throw new IllegalArgumentException("ratio values must be > 0");
        }
        if (numerator >= denominator) return OpportunityEvaluation.SCALE;
        long scaled;
        try {
            scaled = Math.multiplyExact(numerator, OpportunityEvaluation.SCALE);
        } catch (ArithmeticException exception) {
            // Division first preserves a deterministic bounded approximation for unusually large domains.
            long whole = numerator / denominator;
            long remainder = numerator % denominator;
            scaled = Math.addExact(
                    Math.multiplyExact(whole, OpportunityEvaluation.SCALE),
                    Math.multiplyExact(remainder, OpportunityEvaluation.SCALE) / denominator);
            return Math.max(1L, Math.min(OpportunityEvaluation.SCALE, scaled));
        }
        return Math.max(1L, Math.min(OpportunityEvaluation.SCALE, scaled / denominator));
    }

    public static long travelFromDistance(int distance) {
        if (distance < 0) throw new IllegalArgumentException("distance must be >= 0");
        return Math.max(1L, OpportunityEvaluation.SCALE / ((long) distance + 1L));
    }
}
