package io.github.evoforge.simulation.world.agent.search;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.spatial.orientation.FacingDirection;

/**
 * Deterministic correlated-random-walk fallback for unguided exploration.
 * Each leg selects a pseudo-random direction around the current visible horizon while retaining
 * a mild bias toward the previous heading. Variation comes only from stable agent/search identity.
 */
public final class CorrelatedRandomWalkExplorationPolicy implements UnguidedExplorationPolicy {
    private static final long DIRECTION_SALT = 0x9E3779B97F4A7C15L;

    private static final FacingDirection[] CLOCKWISE = {
            FacingDirection.of(1, 0),
            FacingDirection.of(1, -1),
            FacingDirection.of(0, -1),
            FacingDirection.of(-1, -1),
            FacingDirection.of(-1, 0),
            FacingDirection.of(-1, 1),
            FacingDirection.of(0, 1),
            FacingDirection.of(1, 1)
    };

    private final int straightWeight;
    private final int adjacentWeight;
    private final int quarterTurnWeight;
    private final int broadTurnWeight;
    private final int reverseWeight;
    private final int totalWeight;

    public CorrelatedRandomWalkExplorationPolicy(
            int straightWeight,
            int adjacentWeight,
            int quarterTurnWeight,
            int broadTurnWeight,
            int reverseWeight) {
        if (straightWeight <= 0 || adjacentWeight < 0 || quarterTurnWeight < 0
                || broadTurnWeight < 0 || reverseWeight < 0) {
            throw new IllegalArgumentException("exploration direction weights are invalid");
        }
        long total = (long) straightWeight
                + adjacentWeight * 2L
                + quarterTurnWeight * 2L
                + broadTurnWeight * 2L
                + reverseWeight;
        if (total <= 0L || total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("exploration weight sum is invalid");
        }
        this.straightWeight = straightWeight;
        this.adjacentWeight = adjacentWeight;
        this.quarterTurnWeight = quarterTurnWeight;
        this.broadTurnWeight = broadTurnWeight;
        this.reverseWeight = reverseWeight;
        this.totalWeight = (int) total;
    }

    public static CorrelatedRandomWalkExplorationPolicy standard() {
        return new CorrelatedRandomWalkExplorationPolicy(4, 3, 2, 1, 1);
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
        return new SearchRelocationRequest(heading, horizonDistance(heading, visualRange));
    }

    private FacingDirection nextHeading(
            ObjectId agentId,
            FacingDirection previousHeading,
            long legOrdinal) {
        long mixed = mix64(agentId.asLong() ^ ((legOrdinal + 1L) * DIRECTION_SALT));
        int bucket = (int) Long.remainderUnsigned(mixed, totalWeight);

        if (bucket < straightWeight) return previousHeading;
        bucket -= straightWeight;
        if (bucket < adjacentWeight) return rotate(previousHeading, 1);
        bucket -= adjacentWeight;
        if (bucket < adjacentWeight) return rotate(previousHeading, -1);
        bucket -= adjacentWeight;
        if (bucket < quarterTurnWeight) return rotate(previousHeading, 2);
        bucket -= quarterTurnWeight;
        if (bucket < quarterTurnWeight) return rotate(previousHeading, -2);
        bucket -= quarterTurnWeight;
        if (bucket < broadTurnWeight) return rotate(previousHeading, 3);
        bucket -= broadTurnWeight;
        if (bucket < broadTurnWeight) return rotate(previousHeading, -3);
        if (reverseWeight > 0) return rotate(previousHeading, 4);
        return previousHeading;
    }

    /**
     * Chooses a grid-ray length near the circular Vision frontier. Diagonal grid steps are longer
     * in Euclidean space, so they need fewer cells than cardinal steps to reach the same horizon.
     */
    private static int horizonDistance(FacingDirection heading, int visualRange) {
        int cardinalRadius = Math.max(1, visualRange - 1);
        if (heading.x() == 0 || heading.y() == 0) return cardinalRadius;
        return Math.max(1, Math.round((float) (cardinalRadius / Math.sqrt(2.0))));
    }

    private static FacingDirection rotate(FacingDirection heading, int eighthTurnsClockwise) {
        int index = directionIndex(heading);
        return CLOCKWISE[Math.floorMod(index + eighthTurnsClockwise, CLOCKWISE.length)];
    }

    private static int directionIndex(FacingDirection heading) {
        for (int index = 0; index < CLOCKWISE.length; index++) {
            if (CLOCKWISE[index].equals(heading)) return index;
        }
        throw new IllegalArgumentException("unsupported facing direction: " + heading);
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
