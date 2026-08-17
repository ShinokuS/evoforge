package io.github.evoforge.simulation.world.diagnostics;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.generation.CompiledTerrainProfile;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
import java.util.HashMap;
import java.util.Map;

/** On-demand exact audit of generated terrain material composition. */
public final class GeneratedTerrainMaterialDiagnosticsProbe {
    public GeneratedTerrainMaterialDiagnostics snapshot(
            WorldAtlas atlas,
            TerrainMaterialField materials,
            CompiledTerrainProfile profile) {
        if (atlas == null || materials == null || profile == null) {
            throw new IllegalArgumentException(
                    "generated terrain diagnostic dependencies must not be null");
        }
        ElevationField elevation = atlas.elevation();
        WorldBounds bounds = elevation.bounds();
        if (!bounds.equals(materials.bounds())) {
            throw new IllegalArgumentException(
                    "generated material field bounds must match Atlas elevation bounds");
        }

        Map<String, Long> surfaceCounts = new HashMap<>();
        Map<String, Long> volumeCounts = new HashMap<>();
        long terrainCells = 0L;
        int terrainColumns = 0;

        for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
            int worldY = (int) y;
            for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
                int worldX = (int) x;
                int surfaceZ = elevation.elevationAt(worldX, worldY);
                TerrainMaterialKey surface = materials.materialAt(worldX, worldY, surfaceZ);
                add(surfaceCounts, surface);
                terrainColumns++;

                for (long z = bounds.minZ(); z <= (long) surfaceZ; z++) {
                    TerrainMaterialKey material = materials.materialAt(worldX, worldY, (int) z);
                    add(volumeCounts, material);
                    terrainCells = Math.addExact(terrainCells, 1L);
                }
            }
        }

        return new GeneratedTerrainMaterialDiagnostics(
                atlas.genesis().masterSeed(),
                atlas.genesis().generationRevision(),
                atlas.genesis().rngRevision(),
                profile.key(),
                bounds,
                terrainCells,
                terrainColumns,
                surfaceCounts,
                volumeCounts);
    }

    private static void add(Map<String, Long> counts, TerrainMaterialKey material) {
        if (material == null) {
            throw new IllegalStateException("generated terrain material field returned null");
        }
        counts.merge(material.value(), 1L, Math::addExact);
    }
}
