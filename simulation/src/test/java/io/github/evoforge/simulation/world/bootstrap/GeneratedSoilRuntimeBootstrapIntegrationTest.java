package io.github.evoforge.simulation.world.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicProfile;
import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicProfileField;
import io.github.evoforge.simulation.world.genesis.ClimateSpec;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialBindings;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.preparation.GeneratedLandscapeProperties;
import io.github.evoforge.simulation.world.preparation.PreparedGeneratedWorld;
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class GeneratedSoilRuntimeBootstrapIntegrationTest {
    private static final SimulationTimeScale ONE_HOUR =
            SimulationTimeScale.of(Duration.ofHours(1));
    private static final TerrainMaterialKey GROUND = TerrainMaterialKey.of("test:ground");

    @Test
    void generatedSpatialSoilOverridesMaterialFallbackPerCoordinate() {
        PreparedGeneratedWorld prepared = prepared(true);
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition(GROUND.value());
        assembly.soilProperties(ground, 999_000, 999_000);

        GeneratedWorldRuntime world = GeneratedWorldRuntimeBootstrap.withTimeScale(
                        AtmosphericRuntimePlans.disabled(), ONE_HOUR)
                .start(prepared, assembly, TerrainMaterialBindings.of(Map.of(GROUND, ground)));

        int leftZ = prepared.atlas().elevation().elevationAt(0, 0);
        int centerZ = prepared.atlas().elevation().elevationAt(1, 0);
        int rightZ = prepared.atlas().elevation().elevationAt(2, 0);

        assertEquals(450_000, world.runtime().view().soilProperties().find(0, 0, leftZ).capacity());
        assertEquals(8_000, world.runtime().view().soilProperties().find(0, 0, leftZ).permeability());
        assertNull(world.runtime().view().soilProperties().find(1, 0, centerZ));
        assertEquals(500_000, world.runtime().view().soilProperties().find(2, 0, rightZ).capacity());
        assertEquals(1_000, world.runtime().view().soilProperties().find(2, 0, rightZ).permeability());
    }

    @Test
    void absentGeneratedSoilPreservesDefinitionBackedRuntime() {
        PreparedGeneratedWorld prepared = prepared(false);
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition(GROUND.value());
        assembly.soilProperties(ground, 321_000, 7_000);

        GeneratedWorldRuntime world = GeneratedWorldRuntimeBootstrap.withTimeScale(
                        AtmosphericRuntimePlans.disabled(), ONE_HOUR)
                .start(prepared, assembly, TerrainMaterialBindings.of(Map.of(GROUND, ground)));

        int z = prepared.atlas().elevation().elevationAt(1, 0);
        assertEquals(321_000, world.runtime().view().soilProperties().find(1, 0, z).capacity());
        assertEquals(7_000, world.runtime().view().soilProperties().find(1, 0, z).permeability());
    }

    @Test
    void generatedSoilRequiresExplicitPhysicalRuntimeTime() {
        PreparedGeneratedWorld prepared = prepared(true);
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition(GROUND.value());

        assertThrows(
                IllegalStateException.class,
                () -> new GeneratedWorldRuntimeBootstrap(AtmosphericRuntimePlans.disabled())
                        .start(
                                prepared,
                                assembly,
                                TerrainMaterialBindings.of(Map.of(GROUND, ground))));
    }

    private static PreparedGeneratedWorld prepared(boolean withGeneratedSoil) {
        WorldBounds bounds = new WorldBounds(0, 2, 0, 0, -4, 4);
        WorldGenesis genesis = WorldGenesis.current(
                new WorldSpec(
                        bounds,
                        ClimateSpec.STANDARD,
                        PhysicalSpaceScale.cubicMillimeters(1_000L)),
                42L);
        WorldAtlas atlas = new WorldAtlasGenerator().generate(genesis);
        TerrainMaterialField materials = new TerrainMaterialField() {
            public WorldBounds bounds() { return bounds; }
            public TerrainMaterialKey materialAt(int x, int y, int z) { return GROUND; }
        };
        if (!withGeneratedSoil) {
            return new PreparedGeneratedWorld(atlas, materials);
        }

        SoilHydraulicProfile fast = profile(450_000, 8L);
        SoilHydraulicProfile slow = profile(500_000, 1L);
        SoilHydraulicProfileField hydraulics = new SoilHydraulicProfileField() {
            public WorldBounds bounds() { return bounds; }
            public SoilHydraulicProfile find(int x, int y, int z) {
                return x == 0 ? fast : x == 2 ? slow : null;
            }
        };
        return new PreparedGeneratedWorld(
                atlas,
                materials,
                new GeneratedLandscapeProperties(hydraulics));
    }

    private static SoilHydraulicProfile profile(int porosity, long millimetersPerHour) {
        return new SoilHydraulicProfile(
                porosity,
                porosity / 2,
                porosity / 4,
                WaterDepthRate.ofMillimeters(millimetersPerHour, Duration.ofHours(1)));
    }
}
