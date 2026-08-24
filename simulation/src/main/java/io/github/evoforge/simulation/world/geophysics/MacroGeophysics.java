package io.github.evoforge.simulation.world.geophysics;

/** Public creation boundary for the replaceable macro-geophysical algorithm. */
public final class MacroGeophysics {
    private MacroGeophysics() {}

    public static MacroGeophysicalField create(
            long seed,
            long revision,
            MacroGeophysicsDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("definition must not be null");
        return new DeterministicMacroGeophysicalField(seed, revision, definition);
    }
}
