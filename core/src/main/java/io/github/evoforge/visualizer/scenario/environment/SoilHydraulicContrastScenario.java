package io.github.evoforge.visualizer.scenario.environment;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
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
import io.github.evoforge.simulation.world.calibration.soil.RepresentativeSoilCompositionCompiler;
import io.github.evoforge.simulation.world.calibration.soil.SaxtonRawls2006SoilHydraulicCalibrator;
import io.github.evoforge.simulation.world.calibration.soil.SoilCompositionCompiler;
import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicCalibrator;
import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicProfile;
import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicProfileBindings;
import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicRuntimeBinder;
import io.github.evoforge.simulation.world.calibration.soil.SoilSemanticProfile;
import io.github.evoforge.simulation.world.climate.ClimateTemperature;
import io.github.evoforge.simulation.world.genesis.ClimateSpec;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialBindings;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialResolver;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.preparation.GeneratedWorldPreparation;
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
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
import java.math.BigInteger;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * Acceptance scene comparing two points on one continuous authored soil scale under identical rain.
 * Exact composition and hydraulics are derived before runtime through replaceable preparation seams.
 */
public final class SoilHydraulicContrastScenario implements VisualizerScenario {
    static final WorldBounds BOUNDS = new WorldBounds(-8, 8, -5, 5, -4, 4);
    static final PhysicalSpaceScale SPACE = PhysicalSpaceScale.cubicMillimeters(1_000L);
    static final SimulationTimeScale TIME = SimulationTimeScale.of(Duration.ofMinutes(30));
    static final long WEATHER_SEED = 42L;

    private static final RainfallOccurrenceNormal OCCURRENCE = new RainfallOccurrenceNormal(
            Duration.ofHours(11),
            Duration.ofHours(1));
    private static final long WORLD_SEED = 4_204_212L;
    private static final TerrainMaterialKey COARSE_KEY = TerrainMaterialKey.of("scenario:soil-coarse");
    private static final TerrainMaterialKey FINE_KEY = TerrainMaterialKey.of("scenario:soil-fine");
    private static final TerrainMaterialKey DIVIDER_KEY = TerrainMaterialKey.of("scenario:soil-divider");
    private static final SoilCompositionCompiler COMPOSITION_COMPILER =
            new RepresentativeSoilCompositionCompiler();
    private static final SoilHydraulicCalibrator HYDRAULIC_CALIBRATOR =
            new SaxtonRawls2006SoilHydraulicCalibrator();
    private static final SoilHydraulicProfile COARSE_PROFILE = hydraulic(profile(100_000, 400_000));
    private static final SoilHydraulicProfile FINE_PROFILE = hydraulic(profile(800_000, 400_000));
    private static final SoilHydraulicProfileBindings HYDRAULICS =
            SoilHydraulicProfileBindings.of(Map.of(
                    COARSE_KEY, COARSE_PROFILE,
                    FINE_KEY, FINE_PROFILE));

    @Override public String id() { return "soil-hydraulic-contrast"; }
    @Override public String title() { return "Soil Hydraulic Contrast"; }
    @Override public String description() {
        return "One generated rain event falls on coarse and fine points of a continuous soil scale; exact hydraulics are derived before runtime.";
    }

    @Override
    public ScenarioSession create() {
        WorldAtlas atlas = generateDryPhysicalAtlas();
        RainfallRegimeField rainfall = new MeanPreservingRainfallRegimeCalibrator().calibrate(
                atlas.climateNormals(),
                RainfallOccurrenceField.uniform(BOUNDS, OCCURRENCE));

        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId coarseGround = assembly.landscapeDefinition("scenario:soil_coarse");
        LandscapeDefinitionId fineGround = assembly.landscapeDefinition("scenario:soil_fine");
        LandscapeDefinitionId divider = assembly.landscapeDefinition("scenario:soil_divider");
        assembly.surfaceRetention(coarseGround, 400);
        assembly.surfaceRetention(fineGround, 400);
        assembly.surfaceRetention(divider, 400);

        TerrainMaterialBindings materialBindings = TerrainMaterialBindings.of(Map.of(
                COARSE_KEY, coarseGround,
                FINE_KEY, fineGround,
                DIVIDER_KEY, divider));
        SoilHydraulicRuntimeBinder.bind(
                assembly,
                materialBindings,
                HYDRAULICS,
                SPACE,
                TIME);

        TerrainMaterialResolver materials = (x, y, z) -> materialBindings.resolve(
                x < 0 ? COARSE_KEY : x > 0 ? FINE_KEY : DIVIDER_KEY);
        GeneratedWorldRuntime generated = GeneratedWorldRuntimeBootstrap.withTimeScale(
                        new AlternatingRainfallPulseAtmosphericPlan(rainfall, WEATHER_SEED),
                        TIME)
                .start(atlas, assembly, materials);

        WeatherLookup weather = generated.weather().orElseThrow();
        ScenarioController diagnostics = diagnostics(generated.runtime(), weather);
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

    private static WorldAtlas generateDryPhysicalAtlas() {
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
        return new GeneratedWorldPreparation(new WorldAtlasGenerator(algorithms))
                .generateFacts(genesis);
    }

    private static ScenarioController diagnostics(
            SimulationRuntime runtime,
            WeatherLookup weather) {
        String coarseHydraulics = hydraulicSummary(COARSE_PROFILE);
        String fineHydraulics = hydraulicSummary(FINE_PROFILE);
        return new ScenarioController() {
            private ScenarioDiagnostics current = ScenarioDiagnostics.NONE;

            @Override
            public void update(long tick) {
                SideWater coarse = sideWater(runtime, BOUNDS.minX(), -1);
                SideWater fine = sideWater(runtime, 1, BOUNDS.maxX());
                current = new ScenarioDiagnostics(
                        new ScenarioCellMarker[0],
                        "physicalTick=30min"
                                + " · left=fineness 0.10 " + coarseHydraulics
                                + " · right=fineness 0.80 " + fineHydraulics
                                + " · phase=" + (raining(weather) ? "RAIN" : "DRY")
                                + " · left retained=" + coarse.retained()
                                + " free=" + coarse.free()
                                + " · right retained=" + fine.retained()
                                + " free=" + fine.free());
            }

            @Override public ScenarioDiagnostics diagnostics() { return current; }
        };
    }

    static SideWater sideWater(
            SimulationRuntime runtime,
            int minX,
            int maxX) {
        long retained = 0L;
        long free = 0L;
        for (int x = minX; x <= maxX; x++) {
            for (int y = BOUNDS.minY(); y <= BOUNDS.maxY(); y++) {
                for (int z = BOUNDS.minZ(); z <= BOUNDS.maxZ(); z++) {
                    retained += runtime.view().soilLiquids().amountOf(
                            WaterSystem.TYPE, x, y, z);
                    free += runtime.view().water().amount(x, y, z);
                }
            }
        }
        return new SideWater(retained, free);
    }

    private static SoilSemanticProfile profile(int mineralFineness, int organicMatter) {
        return new SoilSemanticProfile(
                NormalizedValue.ofPartsPerMillion(mineralFineness),
                NormalizedValue.ofPartsPerMillion(organicMatter));
    }

    private static SoilHydraulicProfile hydraulic(SoilSemanticProfile semantic) {
        return HYDRAULIC_CALIBRATOR.calibrate(COMPOSITION_COMPILER.compile(semantic));
    }

    private static String hydraulicSummary(SoilHydraulicProfile profile) {
        return String.format(
                Locale.ROOT,
                "porosity=%.2f%% FC=%.2f%% WP=%.2f%% Ks=%.3fmm/h",
                profile.porosityPartsPerMillion() / 10_000d,
                profile.fieldCapacityPartsPerMillion() / 10_000d,
                profile.permanentWiltingPointPartsPerMillion() / 10_000d,
                micrometersPerHour(profile.saturatedHydraulicConductivity()) / 1_000d);
    }

    private static long micrometersPerHour(WaterDepthRate rate) {
        BigInteger numerator = rate.depthNanometersNumerator()
                .multiply(BigInteger.valueOf(Duration.ofHours(1).toNanos()));
        BigInteger denominator = rate.durationNanosecondsDenominator()
                .multiply(BigInteger.valueOf(1_000L));
        BigInteger[] quotientAndRemainder = numerator.divideAndRemainder(denominator);
        if (quotientAndRemainder[1].signum() != 0) {
            throw new IllegalStateException("scenario hydraulic rate is not an exact micrometre/hour value");
        }
        return quotientAndRemainder[0].longValueExact();
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
                    throw new IllegalArgumentException("outside dry Soil hydraulic acceptance hydrology");
                }
            }
        };
    }

    record SideWater(long retained, long free) { }
}
