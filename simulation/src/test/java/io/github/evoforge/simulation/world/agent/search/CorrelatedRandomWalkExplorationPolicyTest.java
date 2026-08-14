package io.github.evoforge.simulation.world.agent.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.spatial.orientation.FacingDirection;
import org.junit.jupiter.api.Test;

final class CorrelatedRandomWalkExplorationPolicyTest {

    @Test
    void initialExplorationPreservesHeading() {
        CorrelatedRandomWalkExplorationPolicy policy =
                new CorrelatedRandomWalkExplorationPolicy(3, 6, 2, 2);
        ObjectId agent = ObjectId.of(7, 0);
        FacingDirection east = FacingDirection.of(1, 0);

        assertEquals(east, policy.nextHeading(agent, east, 0));
        assertEquals(east, policy.nextHeading(agent, east, 1));
        assertEquals(east, policy.nextHeading(agent, east, 2));
    }

    @Test
    void sameIdentityAndOrdinalProduceSameHeading() {
        CorrelatedRandomWalkExplorationPolicy policy =
                CorrelatedRandomWalkExplorationPolicy.standard();
        ObjectId agent = ObjectId.of(11, 2);
        FacingDirection northEast = FacingDirection.of(1, 1);

        FacingDirection first = policy.nextHeading(agent, northEast, 37);
        FacingDirection second = policy.nextHeading(agent, northEast, 37);

        assertEquals(first, second);
    }
}
