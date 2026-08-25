package io.github.evoforge.simulation.world.geophysics;

/**
 * A few deliberately contrasting authored profiles used for inspection and quick world setup.
 *
 * <p>Presets are conveniences over {@link MacroGeophysicsDefinition}; custom definitions remain the
 * actual semantic contract.</p>
 */
public enum MacroGeophysicsPreset {
    SUPERCONTINENT(
            "supercontinent",
            MacroGeophysicsDefinition.of(0.18d, 0.78d, 0.92d, 0.08d, 0.25d)),
    BALANCED(
            "balanced",
            MacroGeophysicsDefinition.of(0.64d, 0.68d, 0.60d, 0.30d, 0.50d)),
    ARCHIPELAGO(
            "archipelago",
            MacroGeophysicsDefinition.of(0.74d, 0.50d, 0.30d, 0.88d, 0.72d)),
    OCEANIC(
            "oceanic",
            MacroGeophysicsDefinition.of(0.90d, 0.72d, 0.68d, 0.18d, 0.45d));

    private final String displayName;
    private final MacroGeophysicsDefinition definition;

    MacroGeophysicsPreset(String displayName, MacroGeophysicsDefinition definition) {
        this.displayName = displayName;
        this.definition = definition;
    }

    public String displayName() {
        return displayName;
    }

    public MacroGeophysicsDefinition definition() {
        return definition;
    }
}
