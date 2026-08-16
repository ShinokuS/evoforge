package io.github.evoforge.simulation.world.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialBindings;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.generation.CompiledTerrainProfile;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialGenerationStage;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialRole;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialSetDefinition;
import io.github.evoforge.simulation.world.terrain.generation.TerrainPresetCatalog;
import io.github.evoforge.simulation.world.terrain.generation.TerrainProfileCompiler;
import io.github.evoforge.simulation.world.terrain.generation.TerrainProfileDefinition;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class GeneratedTerrainProfileBootstrapTest {
    @Test
    void productionBootstrapMaterializesGeneratedSemanticMaterials() {
        WorldBounds bounds = new WorldBounds(-4, 3, -4, 3, -8, 8);
        WorldGenesis genesis = WorldGenesis.current(new WorldSpec(bounds), 42L);
        CompiledTerrainProfile profile = profile();

        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId topsoil = assembly.landscapeDefinition("test:topsoil", 1_050);
        assembly.soilProperties(topsoil, 550_000, 100_000);
        LandscapeDefinitionId soil = assembly.landscapeDefinition("test:soil", 1_100);
        assembly.soilProperties(soil, 450_000, 60_000);
        LandscapeDefinitionId sand = assembly.landscapeDefinition("test:sand", 1_300);
        assembly.soilProperties(sand, 350_000, 250_000);
        LandscapeDefinitionId rock = assembly.landscapeDefinition("test:granite");

        TerrainMaterialBindings bindings = TerrainMaterialBindings.forProfile(
                profile,
                Map.of(
                        TerrainMaterialRole.SURFACE, topsoil,
                        TerrainMaterialRole.SUBSURFACE, soil,
                        TerrainMaterialRole.SEDIMENT, sand,
                        TerrainMaterialRole.BEDROCK, rock));
        GeneratedWorldRuntime world = new GeneratedWorldBootstrap().create(
                genesis,
                assembly,
                profile,
                bindings);

        TerrainMaterialField expected = new TerrainMaterialGenerationStage().generate(
                world.atlas().elevation(),
                world.atlas().drainage(),
                profile);
        Set<LandscapeDefinitionId> observed = new HashSet<>();

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                int surface = world.atlas().elevation().elevationAt(x, y);
                for (int z = bounds.minZ(); z <= surface; z++) {
                    LandscapeDefinitionId expectedId = bindings.resolve(
                            expected.materialAt(x, y, z));
                    LandscapeDefinitionId actual = world.runtime().view().terrain().find(x, y, z);
                    assertEquals(expectedId, actual);
                    observed.add(actual);
                }
            }
        }

        assertTrue(observed.contains(rock));
        assertTrue(observed.contains(topsoil) || observed.contains(sand));
        assertEquals(world.materialization().terrainCells(), observedCellCount(world, bounds));
    }

    private static long observedCellCount(GeneratedWorldRuntime world, WorldBounds bounds) {
        long count = 0L;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    if (world.runtime().view().terrain().contains(x, y, z)) count++;
                }
            }
        }
        return count;
    }

    private static CompiledTerrainProfile profile() {
        return new TerrainProfileCompiler().compile(
                new TerrainProfileDefinition(
                        "test:temperate",
                        List.of(
                                TerrainPresetCatalog.NATURAL_GROUND,
                                TerrainPresetCatalog.DEPOSITIONAL_SAND),
                        "test:materials"),
                new TerrainMaterialSetDefinition(
                        "test:materials",
                        Map.of(
                                TerrainMaterialRole.SURFACE,
                                TerrainMaterialKey.of("test:topsoil"),
                                TerrainMaterialRole.SUBSURFACE,
                                TerrainMaterialKey.of("test:soil"),
                                TerrainMaterialRole.SEDIMENT,
                                TerrainMaterialKey.of("test:sand"),
                                TerrainMaterialRole.BEDROCK,
                                TerrainMaterialKey.of("test:granite"))));
    }
}
