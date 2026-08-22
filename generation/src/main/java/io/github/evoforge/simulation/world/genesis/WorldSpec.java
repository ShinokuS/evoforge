package io.github.evoforge.simulation.world.genesis;

import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;

/**
 * Logical horizontal address space requested for a generated world.
 *
 * <p>This is a generation/world description, not an active-simulation envelope and not a dense
 * allocation request. Vertical exact-XYZ materialization belongs to later refinement stages.</p>
 */
public record WorldSpec(ContinuumWorldDomain domain) {
    public WorldSpec {
        if (domain == null) {
            throw new IllegalArgumentException("domain must not be null");
        }
    }
}
