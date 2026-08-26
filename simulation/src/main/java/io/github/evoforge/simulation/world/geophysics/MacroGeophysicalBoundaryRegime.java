package io.github.evoforge.simulation.world.geophysics;

/** Coarse relative-motion character of a macro-geophysical structural boundary. */
public enum MacroGeophysicalBoundaryRegime {
    /** The queried point is not meaningfully influenced by the nearest structural boundary. */
    INTERIOR,
    /** Neighboring structural regions move toward one another across the boundary normal. */
    CONVERGENT,
    /** Neighboring structural regions move away from one another across the boundary normal. */
    DIVERGENT,
    /** Relative motion is predominantly tangential to the shared boundary. */
    TRANSFORM
}
