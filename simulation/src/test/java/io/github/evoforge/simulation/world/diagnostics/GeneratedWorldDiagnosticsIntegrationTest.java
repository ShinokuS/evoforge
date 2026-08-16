package io.github.evoforge.simulation.world.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

final class GeneratedWorldDiagnosticsIntegrationTest {

    @Test
    void generatedWorldSurvivesProductionRuntimeWithoutInventingWater() {
        GeneratedWorldDiagnostics diagnostics = run(71L, false, 24);

        assertEquals(24L, diagnostics.tick());
        assertTrue(diagnostics.surfaceMatchesAtlas());
        assertEquals(16, diagnostics.terrainColumns());
        assertTrue(diagnostics.terrainCells() >= diagnostics.terrainColumns());
        assertTrue(diagnostics.terminalBasins() >= 1L);
        assertTrue(diagnostics.maximumContributingArea() >= 1L);
        assertEquals(0L, diagnostics.totalWaterVolume());
        assertEquals(0L, diagnostics.wetWaterCells());
        assertEquals(0L, diagnostics.wetSoilCells());

        GeneratedWorldDiagnosticsLog.info(diagnostics);
    }

    @Test
    void existingHydrologyRunsOnGeneratedTerrainAndRemainsDeterministic() {
        GeneratedWorldDiagnostics first = run(991L, true, 12);
        GeneratedWorldDiagnostics replay = run(991L, true, 12);

        assertEquals(first, replay);
        assertTrue(first.surfaceMatchesAtlas());
        assertTrue(first.totalWaterVolume() > 0L);
        assertTrue(first.retainedWaterVolume() > 0L);
        assertTrue(first.wetSoilCells() > 0L);
    }

    private static GeneratedWorldDiagnostics run(
            long seed,
            boolean rain,
            int ticks) {
        WorldBounds bounds = new WorldBounds(0, 3, 0, 3, -4, 4);
        WorldAtlas atlas = new WorldAtlasGenerator().generate(
                WorldGenesis.current(new WorldSpec(bounds), seed));

        SimulationAssembly assembly = SimulationAssembly.create()
                .worldBounds(
                        bounds.minX(), bounds.maxX(),
                        bounds.minY(), bounds.maxY(),
                        bounds.minZ(), bounds.maxZ());

        LandscapeDefinitionId ground = assembly.landscapeDefinition(
                "test:generated-porous-ground");
        assembly.soilProperties(ground, 550_000, 100_000);
        if (rain) {
            assembly.periodicPrecipitation(80_000, 1L);
        }

        for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
            int worldX = (int) x;
            for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
                int worldY = (int) y;
                int surfaceZ = atlas.elevation().elevationAt(worldX, worldY);
                for (long z = bounds.minZ(); z <= (long) surfaceZ; z++) {
                    assembly.placeTerrain(worldX, worldY, (int) z, ground);
                }
            }
        }

        SimulationRuntime runtime = assembly.start();
        for (int tick = 0; tick < ticks; tick++) {
            runtime.stepper().advance();
        }

        return new GeneratedWorldDiagnosticsProbe().snapshot(atlas, runtime);
    }
}
