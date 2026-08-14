package io.github.evoforge.simulation.world.agent.opportunity;

import io.github.evoforge.simulation.world.object.ObjectId;

/**
 * One mechanic-owned bridge from perceived world objects into autonomous opportunities.
 * Generic decision code knows providers only through this contract.
 */
public interface AgentOpportunityProvider {

    String id();

    /** Returns null when this source currently offers nothing to this agent. */
    OpportunityEvaluation evaluate(ObjectId agentId, ObjectId sourceId, int distance);

    /** Revalidates and applies the selected interaction after the agent reaches the source. */
    OpportunityUseResult use(ObjectId agentId, ObjectId sourceId);
}
