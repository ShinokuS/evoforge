package io.github.evoforge.simulation.world.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import io.github.evoforge.simulation.world.weather.WeatherCellState;
import io.github.evoforge.simulation.world.weather.WeatherState;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class GeneratedWorldWeatherBootstrapIntegrationTest {

    @Test
    void climateNormalDoesNotRainUntilCurrentWeatherContainsRain() {
        WorldGenesis genesis = physicalGenesis();
        SimulationAssembly assembly = SimulationAssembly.create();
        GeneratedWorldRuntime world = GeneratedWorldBootstrap.withTimeScale(
                        new WorldAtlasGenerator(),
                        AtmosphericForcingPolicy.WEATHER_STATE,
                        SimulationTimeScale.of(Duration.ofSeconds(1L)))
                .create(genesis, assembly, TerrainMaterialResolver.uniform(ground(assembly)));
        WeatherState weather = world.weatherState().orElseThrow();
        GeneratedWorldDiagnostics initial = diagnostics(world);

        assertTrue(world.atlas().climateNormals().precipitationDepthNormalAt(0, 0)
                .depthNanometersNumerator().signum() > 0);
        assertEquals(
                world.atlas().climateNormals().meanTemperatureAt(1, 1).milliCelsius(),
                weather.at(1, 1).airTemperature().milliCelsius());

        world.runtime().stepper().advance();
        GeneratedWorldDiagnostics calmAfter = diagnostics(world);
        assertEquals(initial.totalWaterVolume(), calmAfter.totalWaterVolume());

        WeatherCellState prior = weather.at(1, 1);
        weather.setAt(
                1,
                1,
                new WeatherCellState(
                        prior.airTemperature(),
                        WaterDepthRate.ofMillimeters(1L, Duration.ofSeconds(1L)),
                        WaterDepthRate.ZERO));
        world.runtime().stepper().advance();
        GeneratedWorldDiagnostics rainyAfter = diagnostics(world);
        assertEquals(calmAfter.totalWaterVolume() + 1_000L, rainyAfter.totalWaterVolume());

        weather.setAt(1, 1, WeatherCellState.calm(prior.airTemperature()));
        world.runtime().stepper().advance();
        assertEquals(rainyAfter.totalWaterVolume(), diagnostics(world).totalWaterVolume());
    }

    private static WorldGenesis physicalGenesis() {
        WorldBounds bounds = new WorldBounds(0, 3, 0, 3, -4, 4);
        WorldSpec spec = new WorldSpec(
                bounds,
                ClimateSpec.physical(
                        ClimateTemperature.ofMilliCelsius(12_000),
                        250,
                        WaterDepthRate.ofMillimeters(16L, Duration.ofSeconds(1L)),
                        WaterDepthRate.ZERO),
                PhysicalSpaceScale.cubicMillimeters(1_000L));
        return new WorldGenesis(spec, 991L, GenerationRevision.V8, RngRevision.V1);
    }

    private static LandscapeDefinitionId ground(SimulationAssembly assembly) {
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:weather-ground");
        assembly.soilProperties(ground, 550_000, 100_000);
        return ground;
    }

    private static GeneratedWorldDiagnostics diagnostics(GeneratedWorldRuntime world) {
        return new GeneratedWorldDiagnosticsProbe().snapshot(world.atlas(), world.runtime());
    }
}
