package io.github.evoforge.simulation.world.mechanics.geometry;

/** Guaranteed minimum positive traversal factor among Shapes currently present in Geometry. */
public interface ShapeTraversalLowerBoundLookup {

    int minimumTraversalFactor();
}
