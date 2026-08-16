package io.github.evoforge.simulation.world.agent.opportunity;

/**
 * Provider-owned opaque identity of one concrete opportunity target.
 *
 * <p>Agent orchestration may compare and retain the target, but it must not inspect
 * provider-specific target semantics.
 */
public interface OpportunityTarget {
    /** Stable developer-facing identity used only for diagnostics and deterministic tie-breaking. */
    String debugKey();
}
