package io.github.evoforge.simulation.world.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.diagnostics.GeneratedWorldDiagnostics;
import io.github.evoforge.simulation.world.diagnostics.GeneratedWorldDiagnosticsProbe;
import io.github.evoforge.simulation.world.genesis.HydroClimateSpec;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialResolver;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

final class GeneratedWorldBootstrapIntegrationTest {

    @Test
    void unforcedGeneratedWorldStartsThroughOneProductionPathWithoutInventingWater() {
        GeneratedWorldRuntime world = create(71L, HydroClimateSpec.UNFORCED);

        assertEquals(0L, world.runtime().time().tick());
        assertEquals(16L, world.materialization().columns());
        assertTrue(world.materialization().terrainCells() >= 16L);

        advance(world, 24L);
        GeneratedWorldDiagnostics diagnostics = audit(world);

        assertEquals(24L, diagnostics.tick());
        assertTrue(diagnostics.surfaceMatchesAtlas());
        assertEquals(0L, diagnostics.totalWaterVolume());
        assertEquals(0L, diagnostics.wetWaterCells());
        assertEquals(0L, diagnostics.wetSoilCells());
    }

    @Test
    void generatedHydroClimateRunsInsideProductionSchedulerAndReplaysExactly() {
        HydroClimateSpec climate = HydroClimateSpec.of(
                CellVolumeRate.of(80_000L, 1L),
                CellVolumeRate.ZERO);

        GeneratedWorldRuntime first = create(991L, climate);
        GeneratedWorldRuntime replay = create(991L, climate);

        advance(first, 12L);
        advance(replay, 12L);

        GeneratedWorldDiagnostics firstDiagnostics = audit(first);
        GeneratedWorldDiagnostics replayDiagnostics = audit(replay);

        assertEquals(firstDiagnostics, replayDiagnostics);
        assertTrue(firstDiagnostics.surfaceMatchesAtlas());
        assertTrue(firstDiagnostics.totalWaterVolume() > 0L);
        assertTrue(firstDiagnostics.retainedWaterVolume() > 0L);
        assertTrue(firstDiagnostics.wetSoilCells() > 0L);
    }

    @Test
    void generatedAndLegacyAtmosphericForcingCannotBeCombined() {
        WorldBounds bounds = bounds();
        WorldAtlas atlas = new WorldAtlasGenerator().generate(
                WorldGenesis.current(
                        new WorldSpec(
                                bounds,
                                HydroClimateSpec.of(
                                        CellVolumeRate.of(1L, 2L),
                                        CellVolumeRate.ZERO)),
                        5L));

        SimulationAssembly generatedFirst = SimulationAssembly.create()
                .worldBounds(
                        bounds.minX(), bounds.maxX(),
                        bounds.minY(), bounds.maxY(),
                        bounds.minZ(), bounds.maxZ())
                .generatedHydroClimate(atlas.hydroClimate());
        assertThrows(
                IllegalStateException.class,
                () -> generatedFirst.periodicPrecipitation(1, 1L));
        assertThrows(
                IllegalStateException.class,
                () -> generatedFirst.periodicEvaporation(1, 1L));

        SimulationAssembly legacyFirst = SimulationAssembly.create()
                .worldBounds(
                        bounds.minX(), bounds.maxX(),
                        bounds.minY(), bounds.maxY(),
                        bounds.minZ(), bounds.maxZ())
                .periodicPrecipitation(1, 1L);
        assertThrows(
                IllegalStateException.class,
                () -> legacyFirst.generatedHydroClimate(atlas.hydroClimate()));
    }

    private static GeneratedWorldRuntime create(
            long seed,
            HydroClimateSpec climate) {
        WorldBounds bounds = bounds();
        WorldGenesis genesis = WorldGenesis.current(
                new WorldSpec(bounds, climate),
                seed);

        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition(
                "test:generated-porous-ground");
        assembly.soilProperties(ground, 550_000, 100_000);

        return new GeneratedWorldBootstrap().create(
                genesis,
                assembly,
                TerrainMaterialResolver.uniform(ground));
    }

    private static GeneratedWorldDiagnostics audit(
            GeneratedWorldRuntime world) {
        return new GeneratedWorldDiagnosticsProbe().snapshot(
                world.atlas(),
                world.runtime());
    }

    private static void advance(
            GeneratedWorldRuntime world,
            long ticks) {
        for (long tick = 0L; tick < ticks; tick++) {
            world.runtime().stepper().advance();
        }
    }

    private static WorldBounds bounds() {
        return new WorldBounds(0, 3, 0, 3, -4, 4);
    }
}
