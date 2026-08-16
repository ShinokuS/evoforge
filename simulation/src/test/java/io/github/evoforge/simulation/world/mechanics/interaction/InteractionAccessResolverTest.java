package io.github.evoforge.simulation.world.mechanics.interaction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.mechanics.geometry.FullShape;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import org.junit.jupiter.api.Test;

final class InteractionAccessResolverTest {

    @Test
    void cardinalSameLevelAndOneBelowAreAllowedWithoutDiagonalReach() {
        InteractionAccessResolver resolver = new InteractionAccessResolver((x, y, z) -> null);
        InteractionReachProfile reach = InteractionReachProfiles.cardinalSameOrOneBelow();

        assertTrue(resolver.allows(0, 0, 1, 1, 0, 1, reach));
        assertTrue(resolver.allows(0, 0, 1, 1, 0, 0, reach));
        assertFalse(resolver.allows(0, 0, 1, 1, 1, 1, reach));
        assertFalse(resolver.allows(0, 0, 1, 1, 1, 0, reach));
    }

    @Test
    void occupiedStandingSiteIsNeverAnInteractionSite() {
        GeometryLookup occupiedSite = (x, y, z) -> x == 0 && y == 0 && z == 1
                ? FullShape.INSTANCE
                : null;
        InteractionAccessResolver resolver = new InteractionAccessResolver(occupiedSite);

        assertFalse(resolver.allows(
                0, 0, 1,
                1, 0, 1,
                InteractionReachProfiles.cardinalSameOrOneBelow()));
    }

    @Test
    void lowerTargetRequiresOpenSpaceAboveIt() {
        GeometryLookup blocked = (x, y, z) -> x == 1 && y == 0 && z == 1
                ? FullShape.INSTANCE
                : null;
        InteractionAccessResolver resolver = new InteractionAccessResolver(blocked);

        assertFalse(resolver.allows(
                0, 0, 1,
                1, 0, 0,
                InteractionReachProfiles.cardinalSameOrOneBelow()));
    }
}
