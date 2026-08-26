package io.github.evoforge.simulation.world.geophysics;

/**
 * Complete Stage 5 macro-geophysical read surface for later Genesis consumers.
 *
 * <p>The elevation and structural capabilities describe the same deterministic geophysical model.
 * Neither capability is tied to Terrain, Drainage, rendering or another current consumer.</p>
 */
public interface MacroGeophysicalModel extends MacroGeophysicalField, MacroGeophysicalStructureField {}
