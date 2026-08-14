package io.github.evoforge.simulation.world.agent.decision;

import io.github.evoforge.simulation.world.object.ObjectId;

/** Read-only developer-facing observation of autonomous decision state. */
public interface AgentDecisionLookup {

    AgentDecisionTrace lastDecision(ObjectId agentId);

    ObjectId currentTarget(ObjectId agentId);
}
