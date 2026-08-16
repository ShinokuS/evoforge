package io.github.evoforge.simulation.world.agent.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.agent.opportunity.OpportunityEvaluation;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** Deterministic generated checks for the common Utility math contract. */
final class UtilityMathPropertyTest {
    private static final long SEED = 0x45564F464F524745L;
    private static final int CASES = 10_000;

    @Test
    void generatedScoresStayBoundedDeterministicAndMonotone() {
        SplittableRandom random = new SplittableRandom(SEED);
        for (int sample = 0; sample < CASES; sample++) {
            long pressure = normalized(random);
            long relief = normalized(random);
            long travel = normalized(random);
            long expectedBenefit = 1L + random.nextLong(1_000_000L);
            OpportunityEvaluation baseline = evaluation(expectedBenefit, pressure, relief, travel);

            long score = UtilityMath.score(baseline);
            assertTrue(score >= 1L && score <= OpportunityEvaluation.SCALE, context(sample));
            assertEquals(score, UtilityMath.score(baseline), context(sample));

            long increasedPressure = increaseWithinScale(pressure, random);
            long increasedRelief = increaseWithinScale(relief, random);
            long increasedTravel = increaseWithinScale(travel, random);

            assertTrue(
                    UtilityMath.score(evaluation(expectedBenefit, increasedPressure, relief, travel)) >= score,
                    context(sample) + " pressure monotonicity");
            assertTrue(
                    UtilityMath.score(evaluation(expectedBenefit, pressure, increasedRelief, travel)) >= score,
                    context(sample) + " relief monotonicity");
            assertTrue(
                    UtilityMath.score(evaluation(expectedBenefit, pressure, relief, increasedTravel)) >= score,
                    context(sample) + " travel monotonicity");
        }
    }

    @Test
    void generatedRatiosAreBoundedAndMonotoneInNumerator() {
        SplittableRandom random = new SplittableRandom(SEED ^ 0x524154494FL);
        for (int sample = 0; sample < CASES; sample++) {
            long denominator = 1L + random.nextLong(1_000_000_000L);
            long firstNumerator = 1L + random.nextLong(denominator);
            long secondNumerator = firstNumerator + random.nextLong(denominator - firstNumerator + 1L);

            long first = UtilityMath.ratio(firstNumerator, denominator);
            long second = UtilityMath.ratio(secondNumerator, denominator);

            assertTrue(first >= 1L && first <= OpportunityEvaluation.SCALE, context(sample));
            assertTrue(second >= 1L && second <= OpportunityEvaluation.SCALE, context(sample));
            assertTrue(second >= first, context(sample) + " numerator monotonicity");
            assertEquals(first, UtilityMath.ratio(firstNumerator, denominator), context(sample));
        }
    }

    @Test
    void travelUtilityNeverImprovesWhenDistanceIncreases() {
        long previous = UtilityMath.travelFromDistance(0);
        assertEquals(OpportunityEvaluation.SCALE, previous);
        for (int distance = 1; distance <= 100_000; distance++) {
            long current = UtilityMath.travelFromDistance(distance);
            assertTrue(current >= 1L && current <= previous, "distance=" + distance);
            previous = current;
        }
    }

    private static OpportunityEvaluation evaluation(
            long expectedBenefit,
            long pressure,
            long relief,
            long travel) {
        return new OpportunityEvaluation(
                expectedBenefit,
                pressure,
                relief,
                travel,
                "test:generated");
    }

    private static long normalized(SplittableRandom random) {
        return 1L + random.nextLong(OpportunityEvaluation.SCALE);
    }

    private static long increaseWithinScale(long value, SplittableRandom random) {
        if (value == OpportunityEvaluation.SCALE) return value;
        return value + random.nextLong(OpportunityEvaluation.SCALE - value + 1L);
    }

    private static String context(int sample) {
        return "seed=" + SEED + ", sample=" + sample;
    }
}
