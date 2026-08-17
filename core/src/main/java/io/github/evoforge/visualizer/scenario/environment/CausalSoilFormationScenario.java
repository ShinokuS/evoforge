package io.github.evoforge.visualizer.scenario.environment;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.SurfaceHydrologyField;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.atlas.WorldGenerationAlgorithms;
import io.github.evoforge.simulation.world.bootstrap.AtmosphericRuntimePlans;
import io.github.evoforge.simulation.world.bootstrap.GeneratedWorldBootstrap;
import io.github.evoforge.simulation.world.bootstrap.GeneratedWorldRuntime;
import io.github.evoforge.simulation.world.bootstrap.GeneratedWorldRuntimeBootstrap;
import io.github.evoforge.simulation.world.calibration.soil.SoilFormationGenerationStage;
import io.github.evoforge.simulation.world.calibration.soil.SoilSemanticProfile;
import io.github.evoforge.simulation.world.calibration.soil.SoilSemanticProfileBindings;
import io.github.evoforge.simulation.world.environment.precipitation.PrecipitationSchedule;
import io.github.evoforge.simulation.world.genesis.ClimateSpec;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.soil.SoilProperties;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialBindings;
import io.github.evoforge.simulation.world.preparation.GeneratedWorldPreparation;
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.surface.SurfaceMorphologyGenerationStage;
import io.github.evoforge.simulation.world.terrain.generation.CompiledTerrainProfile;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialGenerator;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialRole;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialSetDefinition;
import io.github.evoforge.simulation.world.terrain.generation.TerrainPresetCatalog;
import io.github.evoforge.simulation.world.terrain.generation.TerrainProfileCompiler;
import io.github.evoforge.simulation.world.terrain.generation.TerrainProfileDefinition;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentationBindings;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentation;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationLookup;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarker;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarkerStyle;
import io.github.evoforge.visualizer.scenario.ScenarioController;
import io.github.evoforge.visualizer.scenario.ScenarioDiagnostics;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Visual proof that one authored Soil material develops different hydraulics from world causes.
 *
 * <p>Both acceptance sites materialize as the same discrete flat Terrain and share exactly one
 * semantic Definition. Only precise generated elevation differs: the left center is convex and the
 * right center is concave. Normal drainage and Soil formation turn those facts into different
 * hydraulics before the same rain reaches Simulation. Terrain material identity is intentionally
 * uniform so unrelated geology materialization cannot become a second experimental variable.</p>
 */
public final class CausalSoilFormationScenario implements VisualizerScenario {
    static final WorldBounds BOUNDS = new WorldBounds(-8, 8, -5, 5, -3, 4);
    static final int RIDGE_X = -4;
    static final int BASIN_X = 4;
    static final int CENTER_Y = 0;
    static final int SURFACE_Z = 1;
    static final int WATER_Z = 2;

    private static final TerrainMaterialKey GROUND =
            TerrainMaterialKey.of("scenario:causal-soil");
    private static final PhysicalSpaceScale SPACE =
            PhysicalSpaceScale.cubicMillimeters(1_000L);
    private static final SimulationTimeScale TIME =
            SimulationTimeScale.of(Duration.ofHours(1));
    private static final PrecipitationSchedule RAIN =
            PrecipitationSchedule.cyclic(10_000, 1L, 8L, 40L);

    @Override public String id() { return "causal-soil-formation"; }
    @Override public String title() { return "Causal Soil Formation"; }
    @Override public String description() {
        return "One Soil Definition and one Terrain material develop different local hydraulics from generated convex/concave morphology and drainage; identical rain then produces different retained and free Water.";
    }

    @Override
    public ScenarioSession create() {
        WorldGenesis genesis = WorldGenesis.current(
                new WorldSpec(BOUNDS, ClimateSpec.STANDARD, SPACE),
                0x50A1F04DL);
        WorldGenerationAlgorithms algorithms = WorldGenerationAlgorithms.standard()
                .withElevation(ignored -> acceptanceElevation())
                .withSurfaceHydrology((g, e, d) -> drySurface());

        GeneratedWorldPreparation preparation = new GeneratedWorldPreparation(
                new WorldAtlasGenerator(algorithms),
                new SurfaceMorphologyGenerationStage(),
                uniformTerrainGenerator(),
                SoilFormationGenerationStage.standard());
        GeneratedWorldBootstrap bootstrap = new GeneratedWorldBootstrap(
                preparation,
                GeneratedWorldRuntimeBootstrap.withTimeScale(
                        AtmosphericRuntimePlans.disabled(),
                        TIME));

        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition(GROUND.value());
        assembly.surfaceRetention(ground, 10_000);
        assembly.precipitation(RAIN);

        SoilSemanticProfileBindings semantics = SoilSemanticProfileBindings.of(Map.of(
                GROUND,
                new SoilSemanticProfile(
                        NormalizedValue.ofPartsPerMillion(500_000),
                        NormalizedValue.ofPartsPerMillion(400_000))));
        GeneratedWorldRuntime generated = bootstrap.create(
                genesis,
                assembly,
                terrainProfile(),
                semantics,
                TerrainMaterialBindings.of(Map.of(GROUND, ground)));

        SimulationRuntime runtime = generated.runtime();
        WeatherPresentationLookup weather = () -> RAIN.activeAt(runtime.time().tick())
                ? WeatherPresentation.rain(0.65f)
                : WeatherPresentation.CLEAR;
        return new ScenarioSession(
                runtime,
                new ScenarioView(SURFACE_Z, 0f, 0f, 0.9f),
                diagnostics(runtime),
                ObjectPresentationBindings.empty(),
                weather);
    }

    private static ScenarioController diagnostics(SimulationRuntime runtime) {
        ScenarioCellMarker[] markers = {
                new ScenarioCellMarker(RIDGE_X, CENTER_Y, WATER_Z, ScenarioCellMarkerStyle.START),
                new ScenarioCellMarker(BASIN_X, CENTER_Y, WATER_Z, ScenarioCellMarkerStyle.GOAL)
        };
        return new ScenarioController() {
            private ScenarioDiagnostics current = ScenarioDiagnostics.NONE;

            @Override
            public void update(long tick) {
                SoilProperties ridge = runtime.view().soilProperties()
                        .find(RIDGE_X, CENTER_Y, SURFACE_Z);
                SoilProperties basin = runtime.view().soilProperties()
                        .find(BASIN_X, CENTER_Y, SURFACE_Z);
                CellWater ridgeWater = waterAt(runtime, RIDGE_X, CENTER_Y);
                CellWater basinWater = waterAt(runtime, BASIN_X, CENTER_Y);
                current = new ScenarioDiagnostics(
                        markers,
                        "same Definition fineness=0.50 organic=0.40 · physicalTick=1h"
                                + " · ridge K=" + ridge.permeability()
                                + " cap=" + ridge.capacity()
                                + " retained=" + ridgeWater.retained()
                                + " free=" + ridgeWater.free()
                                + " · basin K=" + basin.permeability()
                                + " cap=" + basin.capacity()
                                + " retained=" + basinWater.retained()
                                + " free=" + basinWater.free()
                                + " · phase=" + (RAIN.activeAt(tick) ? "RAIN" : "DRY"));
            }

            @Override public ScenarioDiagnostics diagnostics() { return current; }
        };
    }

    static CellWater waterAt(SimulationRuntime runtime, int x, int y) {
        long retained = runtime.view().soilLiquids()
                .amountOf(WaterSystem.TYPE, x, y, SURFACE_Z);
        long free = 0L;
        for (int z = BOUNDS.minZ(); z <= BOUNDS.maxZ(); z++) {
            free += runtime.view().water().amount(x, y, z);
        }
        return new CellWater(retained, free);
    }

    private static TerrainMaterialGenerator uniformTerrainGenerator() {
        return (elevation, drainage, profile) -> new TerrainMaterialField() {
            @Override public WorldBounds bounds() { return BOUNDS; }

            @Override
            public TerrainMaterialKey materialAt(int x, int y, int z) {
                requireColumn(x, y);
                if (z < BOUNDS.minZ() || z > SURFACE_Z) {
                    throw new IllegalArgumentException(
                            "acceptance material lookup outside generated solid column");
                }
                return GROUND;
            }
        };
    }

    private static ElevationField acceptanceElevation() {
        return new ElevationField() {
            @Override public WorldBounds bounds() { return BOUNDS; }

            @Override public int elevationAt(int x, int y) {
                requireColumn(x, y);
                return SURFACE_Z;
            }

            @Override public long elevationSubunitsAt(int x, int y) {
                requireColumn(x, y);
                if (x == RIDGE_X && y == CENTER_Y) return 1_900_000L;
                if (insideRing(x, y, RIDGE_X)) return 1_100_000L;
                if (x == BASIN_X && y == CENTER_Y) return 1_100_000L;
                if (insideRing(x, y, BASIN_X)) return 1_900_000L;
                return 1_500_000L;
            }
        };
    }

    private static boolean insideRing(int x, int y, int centerX) {
        return Math.abs(x - centerX) <= 1
                && Math.abs(y - CENTER_Y) <= 1
                && (x != centerX || y != CENTER_Y);
    }

    private static void requireColumn(int x, int y) {
        if (x < BOUNDS.minX() || x > BOUNDS.maxX()
                || y < BOUNDS.minY() || y > BOUNDS.maxY()) {
            throw new IllegalArgumentException("acceptance coordinate outside world bounds");
        }
    }

    private static SurfaceHydrologyField drySurface() {
        return new SurfaceHydrologyField() {
            @Override public WorldBounds bounds() { return BOUNDS; }
            @Override public int initialWaterVolumeAt(int x, int y) {
                requireColumn(x, y);
                return 0;
            }
            @Override public boolean isShoreline(int x, int y) {
                requireColumn(x, y);
                return false;
            }
        };
    }

    private static CompiledTerrainProfile terrainProfile() {
        return new TerrainProfileCompiler().compile(
                new TerrainProfileDefinition(
                        "scenario:causal-soil-profile",
                        List.of(TerrainPresetCatalog.NATURAL_GROUND),
                        "scenario:causal-soil-materials"),
                new TerrainMaterialSetDefinition(
                        "scenario:causal-soil-materials",
                        Map.of(
                                TerrainMaterialRole.SURFACE, GROUND,
                                TerrainMaterialRole.SUBSURFACE, GROUND,
                                TerrainMaterialRole.SEDIMENT, GROUND,
                                TerrainMaterialRole.BEDROCK, GROUND)));
    }

    record CellWater(long retained, long free) { }
}
