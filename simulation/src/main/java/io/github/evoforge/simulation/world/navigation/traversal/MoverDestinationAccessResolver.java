package io.github.evoforge.simulation.world.navigation.traversal;

import io.github.evoforge.simulation.world.geometry.TransitionDirections;
import io.github.evoforge.simulation.world.navigation.NavigationLookup;
import io.github.evoforge.simulation.world.object.ObjectId;

/**
 * Cheap necessary-condition check for entering one destination cell with a mover.
 *
 * <p>This is deliberately not a pathfinder and makes no global reachability claim.
 * A destination is locally enterable only when at least one structurally valid
 * incoming Navigation edge also passes the current mover-specific traversal policy.
 * Any real MoveTo still owns global route search and authoritative revalidation.
 */
public final class MoverDestinationAccessResolver {
    private final NavigationLookup navigation;
    private final MoverTraversalConstraint traversal;

    public MoverDestinationAccessResolver(
            NavigationLookup navigation,
            MoverTraversalConstraint traversal) {
        if (navigation == null || traversal == null) {
            throw new IllegalArgumentException("destination access dependencies must not be null");
        }
        this.navigation = navigation;
        this.traversal = traversal;
    }

    public boolean canEnter(
            ObjectId moverId,
            int destinationX,
            int destinationY,
            int destinationZ) {
        if (moverId == null) {
            throw new IllegalArgumentException("moverId must not be null");
        }

        for (int direction = 0; direction < TransitionDirections.COUNT; direction++) {
            int dx = TransitionDirections.dx(direction);
            int dy = TransitionDirections.dy(direction);
            int dz = TransitionDirections.dz(direction);
            long fromX = (long) destinationX - dx;
            long fromY = (long) destinationY - dy;
            long fromZ = (long) destinationZ - dz;
            if (fromX < Integer.MIN_VALUE || fromX > Integer.MAX_VALUE
                    || fromY < Integer.MIN_VALUE || fromY > Integer.MAX_VALUE
                    || fromZ < Integer.MIN_VALUE || fromZ > Integer.MAX_VALUE) {
                continue;
            }

            int sourceX = (int) fromX;
            int sourceY = (int) fromY;
            int sourceZ = (int) fromZ;
            if ((navigation.transitions(sourceX, sourceY, sourceZ)
                    & TransitionDirections.mask(direction)) == 0) {
                continue;
            }
            if (traversal.allows(
                    moverId,
                    sourceX,
                    sourceY,
                    sourceZ,
                    destinationX,
                    destinationY,
                    destinationZ)) {
                return true;
            }
        }
        return false;
    }
}
