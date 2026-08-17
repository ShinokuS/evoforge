package io.github.evoforge.simulation.world.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.world.atlas.DrainageGenerationStage;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.SurfaceHydrologyField;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.climate.ClimateHydroForcingView;
import io.github.evoforge.simulation.world.climate.ClimateTemperature;
import io.github.evoforge.simulation.world.diagnostics.GeneratedWorldDiagnostics;
import io.github.evoforge.simulation.world.diagnostics.GeneratedWorldDiagnosticsProbe;
import io.github.evoforge.simulation.world.genesis.ClimateSpec;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialResolver;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class GeneratedWorldBootstrapIntegrationTest {

    @Test
    void legacyV2UnforcedGeneratedWorldStillStartsWithoutGeneratedWater() {
        WorldBounds bounds = bounds();
        WorldGenesis legacy = new WorldGenesis(
                new WorldSpec(bounds, ClimateSpec.STANDARD_UNFORCED),
                71L,
                GenerationRevision.V2,
                RngRevision.V1);
        GeneratedWorldRuntime world = create(legacy, new WorldAtlasGenerator());

        advance(world, 24L);
        GeneratedWorldDiagnostics diagnostics = audit(world);

        assertEquals(24L, diagnostics.tick());
        assertTrue(diagnostics.surfaceMatchesAtlas());
        assertEquals(0L, diagnostics.generatedInitialWaterVolume());
        assertEquals(0, diagnostics.generatedInitialWaterColumns());
        assertEquals(0L, diagnostics.totalWaterVolume());
    }

    @Test
    void currentGeneratedSurfaceWaterMaterializesBeforeRuntimeAndRemainsFinite() {
        WorldBounds bounds = bounds();
        WorldGenesis genesis = WorldGenesis.current(
                new WorldSpec(bounds, ClimateSpec.STANDARD_UNFORCED),
                71L);
        WorldAtlasGenerator atlasGenerator = new WorldAtlasGenerator(
                ignored -> constantElevation(bounds, 0),
                new DrainageGenerationStage(),
                (requestedGenesis, elevation, drainage) -> oneWetColumn(bounds));
        GeneratedWorldRuntime world = create(genesis, atlasGenerator);

        GeneratedWorldDiagnostics initial = audit(world);
        assertEquals(0L, initial.tick());
        assertEquals(500_000L, initial.generatedInitialWaterVolume());
        assertEquals(1, initial.generatedInitialWaterColumns());
        assertEquals(8, initial.generatedShorelineColumns());
        assertEquals(initial.generatedInitialWaterVolume(), initial.totalWaterVolume());

        advance(world, 24L);
        GeneratedWorldDiagnostics after = audit(world);
        assertTrue(after.surfaceMatchesAtlas());
        assertEquals(initial.totalWaterVolume(), after.totalWaterVolume());
    }

    @Test
    void generatedClimateHydroProjectionRunsInsideProductionSchedulerAndReplaysExactly() {
        ClimateSpec climate = ClimateSpec.of(
                ClimateTemperature.ofMilliCelsius(12_000),
                250,
                CellVolumeRate.of(80_000L, 1L),
                CellVolumeRate.ZERO);

        GeneratedWorldRuntime first = create(
                WorldGenesis.current(new WorldSpec(bounds(), climate), 991L),
                new WorldAtlasGenerator());
        GeneratedWorldRuntime replay = create(
                WorldGenesis.current(new WorldSpec(bounds(), climate), 991L),
                new WorldAtlasGenerator());

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
                                ClimateSpec.of(
                                        ClimateTemperature.ofMilliCelsius(12_000),
                                        250,
                                        CellVolumeRate.of(1L, 2L),
                                        CellVolumeRate.ZERO)),
                        5L));
        ClimateHydroForcingView forcing = new ClimateHydroForcingView(atlas.climateNormals());

        SimulationAssembly generatedFirst = SimulationAssembly.create()
                .worldBounds(
                        bounds.minX(), bounds.maxX(),
                        bounds.minY(), bounds.maxY(),
                        bounds.minZ(), bounds.maxZ())
                .generatedHydroClimate(forcing);
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
                () -> legacyFirst.generatedHydroClimate(forcing));
    }

    private static GeneratedWorldRuntime create(
            WorldGenesis genesis,
            WorldAtlasGenerator atlasGenerator) {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition(
                "test:generated-porous-ground");
        assembly.soilProperties(ground, 550_000, 100_000);

        return new GeneratedWorldBootstrap(atlasGenerator).create(
                genesis,
                assembly,
                TerrainMaterialResolver.uniform(ground));
    }

    private static GeneratedWorldDiagnostics audit(GeneratedWorldRuntime world) {
        return new GeneratedWorldDiagnosticsProbe().snapshot(
                world.atlas(),
                world.runtime());
    }

    private static void advance(GeneratedWorldRuntime world, long ticks) {
        for (long tick = 0L; tick < ticks; tick++) {
            world.runtime().stepper().advance();
        }
    }

    private static ElevationField constantElevation(WorldBounds bounds, int value) {
        return new ElevationField() {
            @Override
            public WorldBounds bounds() {
                return bounds;
            }

            @Override
            public int elevationAt(int x, int y) {
                if (!contains(x, y)) throw new IllegalArgumentException("outside test elevation");
                return value;
            }
        };
    }

    private static SurfaceHydrologyField oneWetColumn(WorldBounds bounds) {
        return new SurfaceHydrologyField() {
            @Override
            public WorldBounds bounds() {
                return bounds;
            }

            @Override
            public int initialWaterVolumeAt(int x, int y) {
                requireContains(x, y);
                return x == 1 && y == 1 ? 500_000 : 0;
            }

            @Override
            public boolean isShoreline(int x, int y) {
                requireContains(x, y);
                if (x == 1 && y == 1) return false;
                return Math.abs(x - 1) <= 1 && Math.abs(y - 1) <= 1;
            }

            private void requireContains(int x, int y) {
                if (!contains(x, y)) throw new IllegalArgumentException("outside test hydrology");
            }
        };
    }

    private static WorldBounds bounds() {
        return new WorldBounds(0, 3, 0, 3, -4, 4);
    }
}
