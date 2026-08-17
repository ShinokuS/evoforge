package io.github.evoforge.visualizer.scenario.environment;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.atlas.SurfaceHydrologyField;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.atlas.WorldGenerationAlgorithms;
import io.github.evoforge.simulation.world.bootstrap.GeneratedWorldRuntime;
import io.github.evoforge.simulation.world.bootstrap.GeneratedWorldRuntimeBootstrap;
import io.github.evoforge.simulation.world.calibration.rainfall.MeanPreservingRainfallRegimeCalibrator;
import io.github.evoforge.simulation.world.calibration.rainfall.RainfallOccurrenceField;
import io.github.evoforge.simulation.world.calibration.rainfall.RainfallOccurrenceNormal;
import io.github.evoforge.simulation.world.calibration.rainfall.RainfallRegimeField;
import io.github.evoforge.simulation.world.climate.ClimateTemperature;
import io.github.evoforge.simulation.world.genesis.ClimateSpec;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialResolver;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.preparation.GeneratedWorldPreparation;
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.weather.WeatherLookup;
import io.github.evoforge.simulation.world.weather.model.AlternatingRainfallPulseAtmosphericPlan;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentationBindings;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentation;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationLookup;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarker;
import io.github.evoforge.visualizer.scenario.ScenarioController;
import io.github.evoforge.visualizer.scenario.ScenarioDiagnostics;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;
import io.github.evoforge.visualizer.scenario.water.WaterScenarioDiagnostics;
import java.time.Duration;

/**
 * Acceptance scene for the full generated climate -> rainfall calibration -> runtime Weather path.
 * Generated initial surface water is deliberately suppressed so every new wet surface in the scene
 * has an unambiguous runtime atmospheric cause.
 */
public final class GeneratedRainfallRegimeScenario implements VisualizerScenario {
    private static final WorldBounds BOUNDS = new WorldBounds(-7, 7, -5, 5, -4, 4);
    private static final PhysicalSpaceScale SPACE = PhysicalSpaceScale.cubicMillimeters(1_000L);
    private static final SimulationTimeScale TIME = SimulationTimeScale.of(Duration.ofMinutes(30));
    private static final RainfallOccurrenceNormal OCCURRENCE = new RainfallOccurrenceNormal(
            Duration.ofHours(11),
            Duration.ofHours(1));
    private static final long WORLD_SEED = 4_204_211L;
    private static final long WEATHER_SEED = 42L;

    @Override public String id() { return "generated-rainfall-regime"; }
    @Override public String title() { return "Generated Rainfall Regime"; }
    @Override public String description() {
        return "Dry-start generated climate is calibrated into an eventful rainfall regime, then ordinary runtime Weather drives Soil and Water.";
    }

    @Override
    public ScenarioSession create() {
        ClimateSpec climate = ClimateSpec.physical(
                ClimateTemperature.ofMilliCelsius(12_000),
                250,
                WaterDepthRate.ofMillimeters(1_200L, Duration.ofDays(365L)),
                WaterDepthRate.ZERO);
        WorldGenesis genesis = new WorldGenesis(
                new WorldSpec(BOUNDS, climate, SPACE),
                WORLD_SEED,
                GenerationRevision.V8,
                RngRevision.V1);

        WorldGenerationAlgorithms algorithms = WorldGenerationAlgorithms.standard()
                .withSurfaceHydrology((ignoredGenesis, ignoredElevation, ignoredDrainage) -> dryHydrology());
        WorldAtlas atlas = new GeneratedWorldPreparation(new WorldAtlasGenerator(algorithms))
                .generateFacts(genesis);

        RainfallOccurrenceField occurrence = RainfallOccurrenceField.uniform(BOUNDS, OCCURRENCE);
        RainfallRegimeField rainfall = new MeanPreservingRainfallRegimeCalibrator()
                .calibrate(atlas.climateNormals(), occurrence);

        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("scenario:generated_rain_ground");
        // Acceptance substrate only: rainfall calibration is under test here; Soil calibration is not.
        assembly.soilProperties(ground, 1_200, 3_000);
        assembly.surfaceRetention(ground, 400);

        GeneratedWorldRuntime generated = GeneratedWorldRuntimeBootstrap.withTimeScale(
                        new AlternatingRainfallPulseAtmosphericPlan(rainfall, WEATHER_SEED),
                        TIME)
                .start(atlas, assembly, TerrainMaterialResolver.uniform(ground));

        WeatherLookup weather = generated.weather().orElseThrow();
        WaterScenarioDiagnostics water = new WaterScenarioDiagnostics(
                generated.runtime(),
                BOUNDS.minX(), BOUNDS.maxX(),
                BOUNDS.minY(), BOUNDS.maxY(),
                BOUNDS.minZ(), BOUNDS.maxZ());
        ScenarioController diagnostics = diagnostics(water, weather);
        WeatherPresentationLookup presentation = () -> raining(weather)
                ? WeatherPresentation.rain(0.65f)
                : WeatherPresentation.CLEAR;

        int focusZ = atlas.elevation().elevationAt(0, 0);
        return new ScenarioSession(
                generated.runtime(),
                new ScenarioView(focusZ, 0f, 0f, 1f),
                diagnostics,
                ObjectPresentationBindings.empty(),
                presentation);
    }

    private static ScenarioController diagnostics(
            WaterScenarioDiagnostics water,
            WeatherLookup weather) {
        return new ScenarioController() {
            private ScenarioDiagnostics current = ScenarioDiagnostics.NONE;

            @Override
            public void update(long tick) {
                water.update(tick);
                String phase = raining(weather) ? "RAIN" : "DRY";
                current = new ScenarioDiagnostics(
                        new ScenarioCellMarker[0],
                        "physicalTick=30min · climatePrecip=1200mm/year"
                                + " · calibratedDryMean=11h · calibratedWetMean=1h"
                                + " · phase=" + phase
                                + " · " + water.diagnostics().summary());
            }

            @Override public ScenarioDiagnostics diagnostics() { return current; }
        };
    }

    private static boolean raining(WeatherLookup weather) {
        return weather.at(0, 0).precipitationRate()
                .depthNanometersNumerator().signum() > 0;
    }

    private static SurfaceHydrologyField dryHydrology() {
        return new SurfaceHydrologyField() {
            @Override public WorldBounds bounds() { return BOUNDS; }
            @Override public int initialWaterVolumeAt(int x, int y) {
                requireInside(x, y);
                return 0;
            }
            @Override public boolean isShoreline(int x, int y) {
                requireInside(x, y);
                return false;
            }
            private void requireInside(int x, int y) {
                if (!contains(x, y)) {
                    throw new IllegalArgumentException("outside dry acceptance hydrology");
                }
            }
        };
    }
}
