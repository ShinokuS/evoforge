package io.github.evoforge.simulation.agents.opportunity;

/** Provider-local identity of one accepted opportunity-use lifecycle. */
public record OpportunityUseActionId(long value) {
    public OpportunityUseActionId {
        if (value < 0) throw new IllegalArgumentException("value must be >= 0");
    }
}
