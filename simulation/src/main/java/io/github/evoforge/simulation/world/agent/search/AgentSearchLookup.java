package io.github.evoforge.simulation.world.agent.search;

import io.github.evoforge.simulation.world.object.ObjectId;

public interface AgentSearchLookup {
    AgentSearchTrace currentSearch(ObjectId agentId);
    AgentSearchTrace lastSearch(ObjectId agentId);
}
