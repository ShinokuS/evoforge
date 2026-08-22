package io.github.evoforge.simulation.world.navigation.traversal;

/** Latest coordinated traversal mutation, used by derived caches for local invalidation. */
public interface TraversalChangeLookup extends TraversalRevisionLookup {

    int lastChangeX();

    int lastChangeY();

    int lastChangeZ();
}
