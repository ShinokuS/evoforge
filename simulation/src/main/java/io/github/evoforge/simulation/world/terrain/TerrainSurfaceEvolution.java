package io.github.evoforge.simulation.world.terrain;

import io.github.evoforge.simulation.world.geophysics.MacroGeophysicalField;

/** Public creation boundary for the replaceable Stage 6 continuous-surface model. */
public final class TerrainSurfaceEvolution {
    private TerrainSurfaceEvolution() {}

    public static ContinuousTerrainSurface create(
            long worldSeed,
            long surfaceRevision,
            MacroGeophysicalField macroGeophysics,
            TerrainSurfaceDefinition definition) {
        if (macroGeophysics == null || definition == null) {
            throw new IllegalArgumentException("macroGeophysics and definition must not be null");
        }
        return new DeterministicContinuousTerrainSurface(
                worldSeed,
                surfaceRevision,
                macroGeophysics,
                definition);
    }
}
