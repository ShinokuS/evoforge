package io.github.evoforge.simulation.world.agent.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.spatial.orientation.FacingDirection;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CorrelatedRandomWalkExplorationPolicyTest {

    @Test
    void standardExplorationSamplesDifferentDirectionsNearVisibleHorizon() {
        CorrelatedRandomWalkExplorationPolicy policy = CorrelatedRandomWalkExplorationPolicy.standard();
        ObjectId agent = ObjectId.of(7, 0);
        FacingDirection east = FacingDirection.of(1, 0);
        Set<FacingDirection> headings = new HashSet<>();

        for (long ordinal = 0; ordinal < 10; ordinal++) {
            SearchRelocationRequest request = policy.nextRelocation(agent, east, ordinal, 8);
            headings.add(request.heading());
            if (request.heading().x() != 0 && request.heading().y() != 0) {
                assertTrue(request.distance() >= 4 && request.distance() <= 5);
            } else {
                assertEquals(7, request.distance());
            }
        }

        assertTrue(headings.size() >= 3, "unguided exploration should not collapse to one straight ray");
        assertTrue(headings.stream().anyMatch(heading -> !heading.equals(east)));
    }

    @Test
    void diagonalLegUsesShorterGridDistanceToStayNearCircularVisionFrontier() {
        CorrelatedRandomWalkExplorationPolicy policy = CorrelatedRandomWalkExplorationPolicy.standard();
        ObjectId agent = ObjectId.of(3, 1);
        SearchRelocationRequest request = policy.nextRelocation(
                agent,
                FacingDirection.of(1, 1),
                0,
                7);

        assertTrue(request.distance() <= 6);
        if (request.heading().x() != 0 && request.heading().y() != 0) {
            assertNotEquals(6, request.distance());
        }
    }

    @Test
    void sameIdentityOrdinalAndVisionProduceSameLeg() {
        CorrelatedRandomWalkExplorationPolicy policy = CorrelatedRandomWalkExplorationPolicy.standard();
        ObjectId agent = ObjectId.of(11, 2);
        FacingDirection northEast = FacingDirection.of(1, 1);

        SearchRelocationRequest first = policy.nextRelocation(agent, northEast, 37, 9);
        SearchRelocationRequest second = policy.nextRelocation(agent, northEast, 37, 9);

        assertEquals(first, second);
    }
}
