package io.github.evoforge.simulation.world.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.calibration.soil.SoilCompositionCompiler;
import io.github.evoforge.simulation.world.calibration.soil.SoilCompositionProfile;
import io.github.evoforge.simulation.world.calibration.soil.SoilFormationCalibration;
import io.github.evoforge.simulation.world.calibration.soil.SoilFormationGenerationStage;
import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicCalibrator;
import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicProfile;
import io.github.evoforge.simulation.world.calibration.soil.SoilSemanticProfile;
import io.github.evoforge.simulation.world.calibration.soil.SoilSemanticProfileBindings;
import io.github.evoforge.simulation.world.genesis.ClimateSpec;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialBindings;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.preparation.GeneratedWorldPreparation;
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.surface.SurfaceMorphologyField;
import io.github.evoforge.simulation.world.surface.SurfaceMorphologyGenerator;
import io.github.evoforge.simulation.world.terrain.generation.CompiledTerrainProfile;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialGenerator;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialRole;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialSetDefinition;
import io.github.evoforge.simulation.world.terrain.generation.TerrainPresetCatalog;
import io.github.evoforge.simulation.world.terrain.generation.TerrainProfileCompiler;
import io.github.evoforge.simulation.world.terrain.generation.TerrainProfileDefinition;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class CausalSoilFormationBootstrapIntegrationTest {
    private static final TerrainMaterialKey GROUND = TerrainMaterialKey.of("test:ground");
    private static final WorldBounds BOUNDS = new WorldBounds(0, 2, 0, 0, -4, 4);

    @Test
    void oneMaterialDevelopsDifferentRuntimeSoilFromPreparedWorldCauses() {
        WorldGenesis genesis = WorldGenesis.current(
                new WorldSpec(
                        BOUNDS,
                        ClimateSpec.STANDARD,
                        PhysicalSpaceScale.cubicMillimeters(1_000L)),
                42L);

        SurfaceMorphologyGenerator morphology = elevation -> new SurfaceMorphologyField() {
            @Override public WorldBounds bounds() { return BOUNDS; }
            @Override public long maximumNeighborSlopeSubunitsAt(int x, int y) {
                return x == 0 ? 1_000_000L : 0L;
            }
            @Override public long concavitySubunitsAt(int x, int y) {
                return x == 2 ? 1_000_000L : 0L;
            }
        };
        TerrainMaterialGenerator terrain = (elevation, drainage, profile) ->
                new TerrainMaterialField() {
                    @Override public WorldBounds bounds() { return BOUNDS; }
                    @Override public TerrainMaterialKey materialAt(int x, int y, int z) {
                        return GROUND;
                    }
                };

        SoilCompositionCompiler composition = semantic -> {
            int fine = semantic.mineralFineness().partsPerMillion();
            return new SoilCompositionProfile(
                    NormalizedValue.SCALE - fine,
                    0,
                    fine,
                    semantic.organicMatter().partsPerMillion());
        };
        SoilHydraulicCalibrator hydraulics = profile -> new SoilHydraulicProfile(
                profile.clayPartsPerMillion(),
                0,
                0,
                WaterDepthRate.ZERO);
        SoilFormationGenerationStage formation = new SoilFormationGenerationStage(
                new SoilFormationCalibration(
                        1_000_000L,
                        1_000_000L,
                        NormalizedValue.ofPartsPerMillion(200_000)),
                composition,
                hydraulics);

        GeneratedWorldPreparation preparation = new GeneratedWorldPreparation(
                new WorldAtlasGenerator(),
                morphology,
                terrain,
                formation);
        GeneratedWorldBootstrap bootstrap = new GeneratedWorldBootstrap(
                preparation,
                GeneratedWorldRuntimeBootstrap.withTimeScale(
                        AtmosphericRuntimePlans.disabled(),
                        SimulationTimeScale.of(Duration.ofHours(1))));

        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition(GROUND.value());
        SoilSemanticProfileBindings semantics = SoilSemanticProfileBindings.of(Map.of(
                GROUND,
                new SoilSemanticProfile(
                        NormalizedValue.ofPartsPerMillion(500_000),
                        NormalizedValue.ofPartsPerMillion(700_000))));

        GeneratedWorldRuntime world = bootstrap.create(
                genesis,
                assembly,
                terrainProfile(),
                semantics,
                TerrainMaterialBindings.of(Map.of(GROUND, ground)));

        for (int x = 0; x <= 2; x++) {
            int surfaceZ = world.atlas().elevation().elevationAt(x, 0);
            int expectedCapacity = switch (x) {
                case 0 -> 400_000;
                case 1 -> 500_000;
                case 2 -> 600_000;
                default -> throw new AssertionError();
            };
            assertEquals(
                    expectedCapacity,
                    world.runtime().view().soilProperties().find(x, 0, surfaceZ).capacity());
        }
    }

    private static CompiledTerrainProfile terrainProfile() {
        return new TerrainProfileCompiler().compile(
                new TerrainProfileDefinition(
                        "test:terrain",
                        List.of(TerrainPresetCatalog.NATURAL_GROUND),
                        "test:materials"),
                new TerrainMaterialSetDefinition(
                        "test:materials",
                        Map.of(
                                TerrainMaterialRole.SURFACE, GROUND,
                                TerrainMaterialRole.SUBSURFACE, GROUND,
                                TerrainMaterialRole.SEDIMENT, GROUND,
                                TerrainMaterialRole.BEDROCK, GROUND)));
    }
}
