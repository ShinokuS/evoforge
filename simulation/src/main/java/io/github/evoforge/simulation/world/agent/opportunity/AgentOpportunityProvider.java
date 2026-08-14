package io.github.evoforge.simulation.world.agent.opportunity;

import io.github.evoforge.simulation.world.object.ObjectId;
import java.util.List;

/** One mechanic-owned bridge from perceived world objects into autonomous opportunities. */
public interface AgentOpportunityProvider {
    String id();
    OpportunityEvaluation evaluate(ObjectId agentId, ObjectId sourceId, int distance);
    OpportunityUseResult use(ObjectId agentId, ObjectId sourceId);

    /** Unresolved motivations the agent semantically knows can be satisfied by finding a concrete source. */
    default List<OpportunitySearchDemand> searchDemands(ObjectId agentId) {
        return List.of();
    }
}
