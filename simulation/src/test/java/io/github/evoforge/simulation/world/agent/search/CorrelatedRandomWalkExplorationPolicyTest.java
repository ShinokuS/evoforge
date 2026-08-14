package io.github.evoforge.simulation.world.agent.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.spatial.orientation.FacingDirection;
import org.junit.jupiter.api.Test;

final class CorrelatedRandomWalkExplorationPolicyTest {

    @Test
    void initialExplorationPreservesHeadingAndUsesMultiCellVisibleLegs() {
        CorrelatedRandomWalkExplorationPolicy policy =
                new CorrelatedRandomWalkExplorationPolicy(3, 6, 2, 2);
        ObjectId agent = ObjectId.of(7, 0);
        FacingDirection east = FacingDirection.of(1, 0);

        for (long ordinal = 0; ordinal < 3; ordinal++) {
            SearchRelocationRequest request = policy.nextRelocation(agent, east, ordinal, 8);
            assertEquals(east, request.heading());
            assertTrue(request.distance() >= 4 && request.distance() <= 7);
        }
    }

    @Test
    void sameIdentityOrdinalAndVisionProduceSameLeg() {
        CorrelatedRandomWalkExplorationPolicy policy =
                CorrelatedRandomWalkExplorationPolicy.standard();
        ObjectId agent = ObjectId.of(11, 2);
        FacingDirection northEast = FacingDirection.of(1, 1);

        SearchRelocationRequest first = policy.nextRelocation(agent, northEast, 37, 9);
        SearchRelocationRequest second = policy.nextRelocation(agent, northEast, 37, 9);

        assertEquals(first, second);
    }
}
