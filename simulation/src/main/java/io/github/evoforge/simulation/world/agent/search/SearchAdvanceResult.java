package io.github.evoforge.simulation.world.agent.search;

/** Result of one deterministic information-seeking step. */
public record SearchAdvanceResult(
        boolean continueSoon,
        AgentSearchTrace trace,
        SearchRelocationRequest relocation) {
    public SearchAdvanceResult {
        if (trace == null) throw new IllegalArgumentException("trace must not be null");
    }
}
