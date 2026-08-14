package io.github.evoforge.simulation.world.agent.search;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.spatial.orientation.FacingDirection;

/**
 * Deterministic correlated-random-walk fallback for unguided exploration.
 * Direction tends to persist; turns are derived from stable agent/search identity rather than wall-clock randomness.
 */
public final class CorrelatedRandomWalkExplorationPolicy implements UnguidedExplorationPolicy {
    private final int initialStraightSteps;
    private final int straightWeight;
    private final int leftWeight;
    private final int rightWeight;
    private final int totalWeight;

    public CorrelatedRandomWalkExplorationPolicy(
            int initialStraightSteps,
            int straightWeight,
            int leftWeight,
            int rightWeight) {
        if (initialStraightSteps < 0 || straightWeight <= 0 || leftWeight < 0 || rightWeight < 0) {
            throw new IllegalArgumentException("exploration weights/initial steps are invalid");
        }
        long total = (long) straightWeight + leftWeight + rightWeight;
        if (total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("exploration weight sum is too large");
        }
        this.initialStraightSteps = initialStraightSteps;
        this.straightWeight = straightWeight;
        this.leftWeight = leftWeight;
        this.rightWeight = rightWeight;
        this.totalWeight = (int) total;
    }

    public static CorrelatedRandomWalkExplorationPolicy standard() {
        return new CorrelatedRandomWalkExplorationPolicy(3, 6, 2, 2);
    }

    @Override
    public FacingDirection nextHeading(
            ObjectId agentId,
            FacingDirection previousHeading,
            long stepOrdinal) {
        if (agentId == null || previousHeading == null) {
            throw new IllegalArgumentException("exploration identity/heading must not be null");
        }
        if (stepOrdinal < 0) {
            throw new IllegalArgumentException("stepOrdinal must be >= 0");
        }
        if (stepOrdinal < initialStraightSteps) {
            return previousHeading;
        }

        long mixed = mix64(agentId.asLong() ^ ((stepOrdinal + 1L) * 0x9E3779B97F4A7C15L));
        int bucket = (int) Long.remainderUnsigned(mixed, totalWeight);
        if (bucket < straightWeight) {
            return previousHeading;
        }
        if (bucket < straightWeight + leftWeight) {
            return left(previousHeading);
        }
        return right(previousHeading);
    }

    static FacingDirection right(FacingDirection heading) {
        return FacingDirection.of(heading.y(), -heading.x());
    }

    private static FacingDirection left(FacingDirection heading) {
        return FacingDirection.of(-heading.y(), heading.x());
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
