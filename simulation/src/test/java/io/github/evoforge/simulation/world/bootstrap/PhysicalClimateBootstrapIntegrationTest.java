package io.github.evoforge.simulation.world.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
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
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class PhysicalClimateBootstrapIntegrationTest {

    @Test
    void oneMillimeterPerSecondAddsPhysicalWaterThroughOrdinaryRuntime() {
        WorldGenesis genesis = physicalGenesis(
                WaterDepthRate.ofMillimeters(1L, Duration.ofSeconds(1L)),
                WaterDepthRate.ZERO);
        SimulationAssembly assembly = assembly();
        GeneratedWorldRuntime world = GeneratedWorldBootstrap.withTimeScale(
                        new WorldAtlasGenerator(),
                        AtmosphericForcingPolicy.CLIMATE_NORMALS,
                        SimulationTimeScale.of(Duration.ofSeconds(1L)))
                .create(genesis, assembly, TerrainMaterialResolver.uniform(ground(assembly)));

        GeneratedWorldDiagnostics initial = diagnostics(world);
        world.runtime().stepper().advance();
        world.runtime().stepper().advance();
        GeneratedWorldDiagnostics after = diagnostics(world);

        long columns = 16L;
        long expectedAtmosphericAddition = columns * 1_000L * 2L;
        assertEquals(initial.totalWaterVolume() + expectedAtmosphericAddition, after.totalWaterVolume());
        assertEquals(2L, world.runtime().time().tick());
        assertEquals(
                SimulationTimeScale.of(Duration.ofSeconds(1L)).elapsedNanoseconds(2L),
                world.elapsedPhysicalNanoseconds().orElseThrow());
    }

    @Test
    void physicalClimateRequiresTimeScaleOnlyWhenAtmosphereIsAttached() {
        WorldGenesis genesis = physicalGenesis(
                WaterDepthRate.ofMillimeters(1L, Duration.ofSeconds(1L)),
                WaterDepthRate.ZERO);

        SimulationAssembly activeAssembly = assembly();
        assertThrows(
                IllegalStateException.class,
                () -> new GeneratedWorldBootstrap(
                                new WorldAtlasGenerator(),
                                AtmosphericForcingPolicy.CLIMATE_NORMALS)
                        .create(
                                genesis,
                                activeAssembly,
                                TerrainMaterialResolver.uniform(ground(activeAssembly))));

        SimulationAssembly isolatedAssembly = assembly();
        GeneratedWorldRuntime isolated = new GeneratedWorldBootstrap(
                        new WorldAtlasGenerator(),
                        AtmosphericForcingPolicy.DISABLED)
                .create(
                        genesis,
                        isolatedAssembly,
                        TerrainMaterialResolver.uniform(ground(isolatedAssembly)));
        GeneratedWorldDiagnostics before = diagnostics(isolated);
        isolated.runtime().stepper().advance();
        GeneratedWorldDiagnostics after = diagnostics(isolated);
        assertEquals(before.totalWaterVolume(), after.totalWaterVolume());
        assertTrue(isolated.atlas().climateNormals().precipitationDepthNormalAt(0, 0)
                .depthNanometersNumerator().signum() > 0);
    }

    private static WorldGenesis physicalGenesis(
            WaterDepthRate precipitation,
            WaterDepthRate evaporation) {
        WorldBounds bounds = new WorldBounds(0, 3, 0, 3, -4, 4);
        WorldSpec spec = new WorldSpec(
                bounds,
                ClimateSpec.physical(
                        ClimateTemperature.ofMilliCelsius(12_000),
                        250,
                        precipitation,
                        evaporation),
                PhysicalSpaceScale.cubicMillimeters(1_000L));
        return new WorldGenesis(spec, 991L, GenerationRevision.V8, RngRevision.V1);
    }

    private static SimulationAssembly assembly() {
        return SimulationAssembly.create();
    }

    private static LandscapeDefinitionId ground(SimulationAssembly assembly) {
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:physical-climate-ground");
        assembly.soilProperties(ground, 550_000, 100_000);
        return ground;
    }

    private static GeneratedWorldDiagnostics diagnostics(GeneratedWorldRuntime world) {
        return new GeneratedWorldDiagnosticsProbe().snapshot(world.atlas(), world.runtime());
    }
}
