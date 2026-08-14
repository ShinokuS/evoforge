package io.github.evoforge.simulation.world.agent.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.spatial.orientation.FacingDirection;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CorrelatedRandomWalkExplorationPolicyTest {

    @Test
    void standardExplorationSamplesArbitraryPointsOnOuterVisionRing() {
        CorrelatedRandomWalkExplorationPolicy policy = CorrelatedRandomWalkExplorationPolicy.standard();
        ObjectId agent = ObjectId.of(7, 0);
        FacingDirection east = FacingDirection.of(1, 0);
        Set<String> points = new HashSet<>();
        boolean sawPointBetweenExactGridRays = false;

        int range = 8;
        long outerSquared = (long) range * range;
        long innerSquared = (long) (range - 1) * (range - 1);
        for (long ordinal = 0; ordinal < 24; ordinal++) {
            SearchRelocationRequest request = policy.nextRelocation(agent, east, ordinal, range);
            points.add(request.offsetX() + "," + request.offsetY());
            long distanceSquared = (long) request.offsetX() * request.offsetX()
                    + (long) request.offsetY() * request.offsetY();
            assertTrue(distanceSquared <= outerSquared);
            assertTrue(distanceSquared >= innerSquared);
            if (request.offsetX() != 0
                    && request.offsetY() != 0
                    && Math.abs(request.offsetX()) != Math.abs(request.offsetY())) {
                sawPointBetweenExactGridRays = true;
            }
        }

        assertTrue(points.size() >= 8, "unguided exploration should sample many frontier cells");
        assertTrue(sawPointBetweenExactGridRays,
                "frontier selection must not collapse to the eight cardinal/diagonal rays");
    }

    @Test
    void selectedPointKeepsCoarseFacingTowardItsQuadrant() {
        SearchRelocationRequest request = new SearchRelocationRequest(6, 3);

        assertEquals(FacingDirection.of(1, 1), request.heading());
        assertEquals(6, request.distance());
    }

    @Test
    void sameIdentityOrdinalAndVisionProduceSamePoint() {
        CorrelatedRandomWalkExplorationPolicy policy = CorrelatedRandomWalkExplorationPolicy.standard();
        ObjectId agent = ObjectId.of(11, 2);
        FacingDirection northEast = FacingDirection.of(1, 1);

        SearchRelocationRequest first = policy.nextRelocation(agent, northEast, 37, 9);
        SearchRelocationRequest second = policy.nextRelocation(agent, northEast, 37, 9);

        assertEquals(first, second);
    }
}
