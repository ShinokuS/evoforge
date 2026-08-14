package io.github.evoforge.simulation.world.agent.search;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.spatial.orientation.FacingDirection;

/**
 * Deterministic correlated-random-walk fallback for unguided exploration.
 * Direction tends to persist and each leg spans several observer-visible cells when Vision permits it.
 * Variation is derived from stable agent/search identity rather than wall-clock randomness.
 */
public final class CorrelatedRandomWalkExplorationPolicy implements UnguidedExplorationPolicy {
    private static final long DIRECTION_SALT = 0x9E3779B97F4A7C15L;
    private static final long DISTANCE_SALT = 0xD1B54A32D192ED03L;

    private final int initialStraightLegs;
    private final int straightWeight;
    private final int leftWeight;
    private final int rightWeight;
    private final int totalWeight;

    public CorrelatedRandomWalkExplorationPolicy(
            int initialStraightLegs,
            int straightWeight,
            int leftWeight,
            int rightWeight) {
        if (initialStraightLegs < 0 || straightWeight <= 0 || leftWeight < 0 || rightWeight < 0) {
            throw new IllegalArgumentException("exploration weights/initial legs are invalid");
        }
        long total = (long) straightWeight + leftWeight + rightWeight;
        if (total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("exploration weight sum is too large");
        }
        this.initialStraightLegs = initialStraightLegs;
        this.straightWeight = straightWeight;
        this.leftWeight = leftWeight;
        this.rightWeight = rightWeight;
        this.totalWeight = (int) total;
    }

    public static CorrelatedRandomWalkExplorationPolicy standard() {
        return new CorrelatedRandomWalkExplorationPolicy(3, 6, 2, 2);
    }

    @Override
    public SearchRelocationRequest nextRelocation(
            ObjectId agentId,
            FacingDirection previousHeading,
            long legOrdinal,
            int visualRange) {
        if (agentId == null || previousHeading == null) {
            throw new IllegalArgumentException("exploration identity/heading must not be null");
        }
        if (legOrdinal < 0) throw new IllegalArgumentException("legOrdinal must be >= 0");
        if (visualRange <= 0) throw new IllegalArgumentException("visualRange must be > 0");

        FacingDirection heading = nextHeading(agentId, previousHeading, legOrdinal);
        int maxDistance = Math.max(1, visualRange - 1);
        int minDistance = Math.min(maxDistance, Math.max(2, (visualRange + 1) / 2));
        int span = maxDistance - minDistance + 1;
        long mixed = mix64(agentId.asLong() ^ ((legOrdinal + 1L) * DISTANCE_SALT));
        int distance = minDistance + (int) Long.remainderUnsigned(mixed, span);
        return new SearchRelocationRequest(heading, distance);
    }

    private FacingDirection nextHeading(
            ObjectId agentId,
            FacingDirection previousHeading,
            long legOrdinal) {
        if (legOrdinal < initialStraightLegs) return previousHeading;
        long mixed = mix64(agentId.asLong() ^ ((legOrdinal + 1L) * DIRECTION_SALT));
        int bucket = (int) Long.remainderUnsigned(mixed, totalWeight);
        if (bucket < straightWeight) return previousHeading;
        if (bucket < straightWeight + leftWeight) return left(previousHeading);
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
