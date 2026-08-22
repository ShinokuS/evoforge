package io.github.evoforge.simulation.agents.decision;

import io.github.evoforge.simulation.world.object.ObjectId;

/** Read-only developer-facing observation of autonomous decision state. */
public interface AgentDecisionLookup {

    AgentDecisionTrace lastDecision(ObjectId agentId);

    String currentTargetKey(ObjectId agentId);

    AgentIntentTrace currentIntent(ObjectId agentId);
}
