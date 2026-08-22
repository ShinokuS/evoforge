package io.github.evoforge.simulation.world.navigation.traversal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.geometry.TransitionMask;
import io.github.evoforge.simulation.world.navigation.NavigationLookup;
import io.github.evoforge.simulation.world.object.ObjectId;
import org.junit.jupiter.api.Test;

final class MoverDestinationAccessResolverTest {

    @Test
    void destinationRequiresAtLeastOneAllowedIncomingEdge() {
        ObjectId mover = ObjectId.of(0, 0);
        NavigationLookup navigation = (x, y, z) -> {
            if (x == 0 && y == 0 && z == 0) return TransitionMask.of(1, 0, 0);
            return TransitionMask.NONE;
        };
        MoverDestinationAccessResolver access = new MoverDestinationAccessResolver(
                navigation,
                MoverTraversalConstraint.ALLOW_ALL);

        assertTrue(access.canEnter(mover, 1, 0, 0));
        assertFalse(access.canEnter(mover, 2, 0, 0));
    }

    @Test
    void moverTraversalCanRejectOtherwiseStructuralDestination() {
        ObjectId mover = ObjectId.of(0, 0);
        NavigationLookup navigation = (x, y, z) ->
                x == 0 && y == 0 && z == 0
                        ? TransitionMask.of(1, 0, 0)
                        : TransitionMask.NONE;
        MoverTraversalConstraint blockedDestination =
                (moverId, fromX, fromY, fromZ, toX, toY, toZ) -> toX != 1;
        MoverDestinationAccessResolver access = new MoverDestinationAccessResolver(
                navigation,
                blockedDestination);

        assertFalse(access.canEnter(mover, 1, 0, 0));
    }
}
