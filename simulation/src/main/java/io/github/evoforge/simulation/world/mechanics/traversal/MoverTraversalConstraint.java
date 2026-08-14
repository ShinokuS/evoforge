package io.github.evoforge.simulation.world.mechanics.traversal;

import io.github.evoforge.simulation.world.object.ObjectId;

/**
 * Dynamic mover-specific permission layered on top of authoritative Navigation.
 *
 * <p>Navigation answers whether a geometric edge exists. Implementations of this
 * constraint answer whether one particular mover may use that edge under current
 * environmental facts. Real Movement must revalidate this permission when an edge
 * starts and again before it commits.
 */
@FunctionalInterface
public interface MoverTraversalConstraint {

    MoverTraversalConstraint ALLOW_ALL =
            (moverId, fromX, fromY, fromZ, toX, toY, toZ) -> true;

    boolean allows(
            ObjectId moverId,
            int fromX,
            int fromY,
            int fromZ,
            int toX,
            int toY,
            int toZ);
}
