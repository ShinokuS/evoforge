package io.github.evoforge.simulation.world.agent.search;

/** Result of one deterministic local information-seeking step. */
public record SearchAdvanceResult(boolean continueSoon, AgentSearchTrace trace) {
    public SearchAdvanceResult {
        if (trace == null) throw new IllegalArgumentException("trace must not be null");
    }
}
