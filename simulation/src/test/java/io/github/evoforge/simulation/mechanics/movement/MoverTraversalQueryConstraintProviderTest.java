package io.github.evoforge.simulation.mechanics.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathTransitionConstraint;
import io.github.evoforge.simulation.world.navigation.traversal.MoverTraversalConstraint;

final class MoverTraversalQueryConstraintProviderTest {

    @Test
    void composesCallerAndMoverPermissionWithoutInventingRevision() {
        ObjectId mover = ObjectId.of(3, 0);
        MoverTraversalConstraint traversal =
                (moverId, fromX, fromY, fromZ, toX, toY, toZ) -> toX != 2;
        MoverTraversalQueryConstraintProvider provider =
                new MoverTraversalQueryConstraintProvider(traversal);
        PathTransitionConstraint caller = new PathTransitionConstraint() {
            @Override
            public boolean allows(
                    int fromX,
                    int fromY,
                    int fromZ,
                    int toX,
                    int toY,
                    int toZ) {
                return toY != 4;
            }

            @Override
            public long revision() {
                return 17L;
            }
        };

        PathTransitionConstraint combined =
                provider.constraintFor(mover, caller);

        assertTrue(combined.allows(0, 0, 0, 1, 1, 0));
        assertFalse(combined.allows(0, 0, 0, 2, 1, 0));
        assertFalse(combined.allows(0, 0, 0, 1, 4, 0));
        assertEquals(17L, combined.revision());
    }

    @Test
    void allowAllReturnsOriginalConstraintIdentity() {
        MoverTraversalQueryConstraintProvider provider =
                new MoverTraversalQueryConstraintProvider(
                        MoverTraversalConstraint.ALLOW_ALL);
        PathTransitionConstraint caller = PathTransitionConstraint.ALLOW_ALL;

        assertSame(
                caller,
                provider.constraintFor(ObjectId.of(0, 0), caller));
    }
}
