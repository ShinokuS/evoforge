package io.github.evoforge.simulation.agents.opportunity;

import io.github.evoforge.simulation.agents.perception.PerceptionSnapshot;
import io.github.evoforge.simulation.world.object.ObjectId;
import java.util.List;

/** One mechanic-owned bridge from current perception into autonomous opportunities. */
public interface AgentOpportunityProvider {
    String id();

    /** Concrete opportunities currently discoverable from the supplied sensory snapshot. */
    List<AgentOpportunity> opportunities(ObjectId agentId, PerceptionSnapshot perception);

    /** Re-evaluates one already-known target/site without rediscovering it omnisciently. */
    OpportunityEvaluation evaluate(ObjectId agentId, OpportunityTarget target, InteractionSite site);

    /** Starts one provider-owned use lifecycle after the agent reached the interaction site. */
    OpportunityUseStartAttempt startUse(
            ObjectId agentId,
            OpportunityTarget target,
            InteractionSite site);

    /** Whether this provider still owns an active use for the agent. */
    boolean isUseActive(ObjectId agentId);

    /** Last terminal use completion for this agent, or null when none completed yet. */
    OpportunityUseCompletion lastUseCompletion(ObjectId agentId);

    /** Unresolved motivations the agent semantically knows can be satisfied by finding a concrete source. */
    default List<OpportunitySearchDemand> searchDemands(ObjectId agentId) {
        return List.of();
    }
}
