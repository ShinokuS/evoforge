package io.github.evoforge.simulation.world.geophysics;

import io.github.evoforge.simulation.definition.NormalizedValue;

/**
 * Human-authored semantic controls for the Stage 5 macro-geophysical character of a world.
 *
 * <p>These values describe intent rather than exposing solver spans, interpolation constants or
 * noise weights. The concrete geophysical algorithm translates the normalized semantics into its
 * own internal parameters.</p>
 */
public record MacroGeophysicsDefinition(
        NormalizedValue oceanPrevalence,
        NormalizedValue continentalScale,
        NormalizedValue landmassCohesion,
        NormalizedValue fragmentation,
        NormalizedValue macroVariation) {

    public MacroGeophysicsDefinition {
        if (oceanPrevalence == null
                || continentalScale == null
                || landmassCohesion == null
                || fragmentation == null
                || macroVariation == null) {
            throw new IllegalArgumentException("macro-geophysics controls must not be null");
        }
    }

    public static MacroGeophysicsDefinition of(
            double oceanPrevalence,
            double continentalScale,
            double landmassCohesion,
            double fragmentation,
            double macroVariation) {
        return new MacroGeophysicsDefinition(
                NormalizedValue.of(oceanPrevalence),
                NormalizedValue.of(continentalScale),
                NormalizedValue.of(landmassCohesion),
                NormalizedValue.of(fragmentation),
                NormalizedValue.of(macroVariation));
    }
}
