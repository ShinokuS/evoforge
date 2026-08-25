package io.github.evoforge.simulation.world.geophysics;

import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarField;

/** Read-only Continuum scalar view of the macro-geophysical elevation authority. */
public final class MacroGeophysicalContinuumField implements ContinuumScalarField {
    private final MacroGeophysicalField geophysics;

    public MacroGeophysicalContinuumField(MacroGeophysicalField geophysics) {
        if (geophysics == null) {
            throw new IllegalArgumentException("geophysics must not be null");
        }
        this.geophysics = geophysics;
    }

    @Override
    public double sample(long x, long y) {
        return (geophysics.elevationAt(x, y) + 1.0d) * 0.5d;
    }
}
