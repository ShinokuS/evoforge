package io.github.evoforge.simulation.agents.search;

/** Local lifecycle of the current epistemic search strategy. */
public enum AgentSearchStatus {
    SWEEPING,
    EXPLORING,
    RELOCATION_BLOCKED
}
