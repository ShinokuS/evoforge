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
            MacroGeophysicsDefinition.of(0.05d, 0.70d, 0.95d, 0.05d, 0.25d)),
    BALANCED(
            "balanced",
            MacroGeophysicsDefinition.of(0.65d, 0.65d, 0.60d, 0.35d, 0.55d)),
    ARCHIPELAGO(
            "archipelago",
            MacroGeophysicsDefinition.of(0.72d, 0.35d, 0.15d, 0.90d, 0.75d)),
    OCEANIC(
            "oceanic",
            MacroGeophysicsDefinition.of(0.88d, 0.45d, 0.25d, 0.75d, 0.65d));

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
