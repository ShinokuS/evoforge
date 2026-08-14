package io.github.evoforge.simulation.world.agent.search;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.spatial.orientation.FacingDirection;

/**
 * Deterministic correlated-random-walk fallback for unguided exploration.
 * Each leg selects a pseudo-random relative point on the outer Vision ring rather than one of eight grid rays.
 * Variation comes only from stable agent/search identity, preserving replay determinism.
 */
public final class CorrelatedRandomWalkExplorationPolicy implements UnguidedExplorationPolicy {
    private static final long DIRECTION_SALT = 0x9E3779B97F4A7C15L;
    private static final long ANGLE_SALT = 0x94D049BB133111EBL;
    private static final double EIGHTH_TURN_RADIANS = StrictMath.PI / 4.0;

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

        int turnSector = nextTurnSector(agentId, legOrdinal);
        double jitter = signedUnit(agentId, legOrdinal, ANGLE_SALT) * 0.5;
        double baseAngle = StrictMath.atan2(previousHeading.y(), previousHeading.x());
        double targetAngle = baseAngle + (turnSector + jitter) * EIGHTH_TURN_RADIANS;
        return frontierPoint(targetAngle, visualRange);
    }

    /**
     * Selects one weighted 45-degree sector around the previous heading. The later intra-sector jitter
     * prevents the result from collapsing back to the eight exact grid rays.
     */
    private int nextTurnSector(ObjectId agentId, long legOrdinal) {
        long mixed = mix64(agentId.asLong() ^ ((legOrdinal + 1L) * DIRECTION_SALT));
        int bucket = (int) Long.remainderUnsigned(mixed, totalWeight);

        if (bucket < straightWeight) return 0;
        bucket -= straightWeight;
        if (bucket < adjacentWeight) return 1;
        bucket -= adjacentWeight;
        if (bucket < adjacentWeight) return -1;
        bucket -= adjacentWeight;
        if (bucket < quarterTurnWeight) return 2;
        bucket -= quarterTurnWeight;
        if (bucket < quarterTurnWeight) return -2;
        bucket -= quarterTurnWeight;
        if (bucket < broadTurnWeight) return 3;
        bucket -= broadTurnWeight;
        if (bucket < broadTurnWeight) return -3;
        return reverseWeight > 0 ? 4 : 0;
    }

    /** Returns a lattice cell on the outer one-cell-thick ring of the circular visual horizon. */
    private static SearchRelocationRequest frontierPoint(double angle, int visualRange) {
        int radius = visualRange;
        int x = (int) StrictMath.round(StrictMath.cos(angle) * radius);
        int y = (int) StrictMath.round(StrictMath.sin(angle) * radius);
        long radiusSquared = (long) radius * radius;

        while ((long) x * x + (long) y * y > radiusSquared) {
            if (StrictMath.abs(x) >= StrictMath.abs(y) && x != 0) {
                x -= Integer.signum(x);
            } else if (y != 0) {
                y -= Integer.signum(y);
            }
        }
        if (x == 0 && y == 0) {
            x = StrictMath.cos(angle) >= 0.0 ? 1 : -1;
        }
        return new SearchRelocationRequest(x, y);
    }

    private static double signedUnit(ObjectId agentId, long legOrdinal, long salt) {
        long mixed = mix64(agentId.asLong() ^ ((legOrdinal + 1L) * salt));
        double unit = (double) (mixed >>> 11) * 0x1.0p-53;
        return unit * 2.0 - 1.0;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
