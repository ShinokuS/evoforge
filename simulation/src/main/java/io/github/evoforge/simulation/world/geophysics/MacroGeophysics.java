package io.github.evoforge.simulation.world.geophysics;

/** Public creation boundary for the replaceable macro-geophysical algorithm. */
public final class MacroGeophysics {
    private MacroGeophysics() {}

    public static MacroGeophysicalModel create(
            long seed,
            long revision,
            MacroGeophysicsDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("definition must not be null");
        return new DeterministicMacroGeophysicalField(seed, revision, definition);
    }

    /**
     * Creates a finite-world view without changing the accepted unbounded Stage 5 generator.
     *
     * <p>The finite wrapper owns only the land/ocean conflict at the physical world boundary. The
     * original model remains available through {@link #create(long, long, MacroGeophysicsDefinition)}
     * for regression tests and consumers that need the unbounded field.</p>
     */
    public static MacroGeophysicalModel createFinite(
            long seed,
            long revision,
            MacroGeophysicsDefinition definition,
            long width,
            long height) {
        MacroGeophysicalModel source = create(seed, revision, definition);
        return new FiniteMacroGeophysicalField(source, width, height, seed, revision);
    }
}
