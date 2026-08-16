package io.github.evoforge.simulation.world.agent.opportunity;

/** One currently perceived concrete opportunity and one physical site from which it can be used. */
public record AgentOpportunity(
        OpportunityTarget target,
        InteractionSite site,
        OpportunityEvaluation evaluation) {

    public AgentOpportunity {
        if (target == null || site == null || evaluation == null) {
            throw new IllegalArgumentException("opportunity values must not be null");
        }
        if (target.debugKey() == null || target.debugKey().isBlank()) {
            throw new IllegalArgumentException("opportunity target debug key must not be blank");
        }
    }
}
