package io.github.evoforge.simulation.world.agent.opportunity;

import io.github.evoforge.simulation.world.object.ObjectId;
import java.util.List;

/** One mechanic-owned bridge from perceived world objects into autonomous opportunities. */
public interface AgentOpportunityProvider {
    String id();
    OpportunityEvaluation evaluate(ObjectId agentId, ObjectId sourceId, int distance);

    /** Starts one provider-owned use lifecycle after the agent reached the source. */
    OpportunityUseStartAttempt startUse(ObjectId agentId, ObjectId sourceId);

    /** Whether this provider still owns an active use for the agent. */
    boolean isUseActive(ObjectId agentId);

    /** Last terminal use completion for this agent, or null when none completed yet. */
    OpportunityUseCompletion lastUseCompletion(ObjectId agentId);

    /** Unresolved motivations the agent semantically knows can be satisfied by finding a concrete source. */
    default List<OpportunitySearchDemand> searchDemands(ObjectId agentId) {
        return List.of();
    }
}
