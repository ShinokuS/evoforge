package io.github.evoforge.simulation.world.mechanics.traversal;

/** Monotonic version of topology/cost facts consumed by traversal searches. */
public interface TraversalRevisionLookup {

    long revision();
}
