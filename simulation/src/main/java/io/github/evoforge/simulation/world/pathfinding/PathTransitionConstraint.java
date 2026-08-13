package io.github.evoforge.simulation.world.pathfinding;

/** Query-local advisory filter layered on top of authoritative Navigation edges. */
@FunctionalInterface
public interface PathTransitionConstraint {

    PathTransitionConstraint ALLOW_ALL =
            (fromX, fromY, fromZ, toX, toY, toZ) -> true;

    boolean allows(
            int fromX,
            int fromY,
            int fromZ,
            int toX,
            int toY,
            int toZ);

    /**
     * Version of dynamic facts read by this constraint. Immutable constraints may keep zero.
     * A resumable search becomes STALE when this value changes.
     */
    default long revision() {
        return 0L;
    }
}
