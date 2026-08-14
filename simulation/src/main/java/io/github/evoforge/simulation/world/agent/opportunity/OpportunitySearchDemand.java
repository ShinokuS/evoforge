package io.github.evoforge.simulation.world.agent.opportunity;

/** Provider-owned unresolved motivation for which the agent knows a concrete opportunity can be sought. */
public record OpportunitySearchDemand(String motivation, long urgency) {
    public OpportunitySearchDemand {
        if (motivation == null || motivation.isBlank()) throw new IllegalArgumentException("motivation must not be blank");
        if (urgency <= 0) throw new IllegalArgumentException("urgency must be > 0");
    }
}
