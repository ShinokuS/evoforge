package io.github.evoforge.simulation.world.terrain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.geophysics.MacroGeophysicalField;
import org.junit.jupiter.api.Test;

final class TerrainSurfaceMapObservationTest {

    @Test
    void finerMapObservationConvergesTowardExactSurfaceInsteadOfStretchingOneCoarseTexture() {
        MacroGeophysicalField inland = (x, y) -> 0.34d;
        ContinuousTerrainSurface exact = TerrainSurfaceEvolution.create(
                0x45A10F0E2026L,
                5L,
                inland,
                TerrainSurfaceDefinition.balanced());
        TerrainSurfaceMapObservation map = (TerrainSurfaceMapObservation) exact;

        double coarseError = 0.0d;
        double fineError = 0.0d;
        int changedByLod = 0;
        for (int y = 0; y < 18; y++) {
            for (int x = 0; x < 18; x++) {
                long worldX = 900_000L + x * 4_096L;
                long worldY = 1_200_000L + y * 4_096L;
                double truth = exact.surfaceZAt(worldX, worldY);
                double coarse = map.surfaceZForMapAt(worldX, worldY, 32_768L);
                double fine = map.surfaceZForMapAt(worldX, worldY, 512L);
                coarseError += Math.abs(truth - coarse);
                fineError += Math.abs(truth - fine);
                if (Math.abs(coarse - fine) > 0.25d) changedByLod++;
            }
        }

        assertTrue(changedByLod > 100,
                "zooming to a resolvable sampling interval must reveal additional terrain structure");
        assertTrue(fineError < coarseError * 0.35d,
                "fine map observation must converge toward exact Terrain instead of magnifying coarse samples");
    }
}
