package io.github.evoforge.simulation.world.geophysics;

/** Consumer-neutral read capability for deterministic Stage 5 structural geophysical context. */
public interface MacroGeophysicalStructureField {
    MacroGeophysicalStructure structureAt(long x, long y);
}
