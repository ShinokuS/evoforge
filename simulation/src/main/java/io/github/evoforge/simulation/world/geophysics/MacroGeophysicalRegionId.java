package io.github.evoforge.simulation.world.geophysics;

/**
 * Stable identity of one deterministic macro-geophysical structural region.
 *
 * <p>The value is opaque to consumers. It identifies natural geophysical structure, not a
 * Continuum page/tile/cache region, and may change only when the compatible geophysical model
 * revision changes.</p>
 */
public record MacroGeophysicalRegionId(long value) {}
