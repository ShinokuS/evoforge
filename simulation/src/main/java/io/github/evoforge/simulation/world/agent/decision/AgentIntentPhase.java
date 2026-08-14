package io.github.evoforge.simulation.world.agent.decision;

/** Structural phase of the currently committed autonomous intent, not a domain action type. */
public enum AgentIntentPhase {
    MOVING_TO_OPPORTUNITY,
    USING_OPPORTUNITY,
    SEARCH_RELOCATION
}
