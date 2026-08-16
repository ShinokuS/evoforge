package io.github.evoforge.simulation.world.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialBindings;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialGenerationStage;
import io.github.evoforge.simulation.world.terrain.generation.TerrainPalette;
import io.github.evoforge.simulation.world.terrain.generation.TerrainPaletteMaterials;
import io.github.evoforge.simulation.world.terrain.generation.TerrainPreset;
import io.github.evoforge.simulation.world.terrain.generation.TerrainPresetCapability;
import io.github.evoforge.simulation.world.terrain.generation.TerrainPresetCatalog;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;

final class GeneratedTerrainPaletteBootstrapTest {

    @Test
    void productionBootstrapMaterializesGeneratedSemanticMaterials() {
        WorldBounds bounds = new WorldBounds(-4, 3, -4, 3, -8, 8);
        WorldGenesis genesis = WorldGenesis.current(new WorldSpec(bounds), 42L);
        TerrainPalette palette = palette();

        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId topsoil = assembly.landscapeDefinition(
                "test:topsoil", 1_050);
        assembly.soilProperties(topsoil, 550_000, 100_000);
        LandscapeDefinitionId soil = assembly.landscapeDefinition(
                "test:soil", 1_100);
        assembly.soilProperties(soil, 450_000, 60_000);
        LandscapeDefinitionId sand = assembly.landscapeDefinition(
                "test:sand", 1_300);
        assembly.soilProperties(sand, 350_000, 250_000);
        LandscapeDefinitionId rock = assembly.landscapeDefinition("test:granite");

        TerrainMaterialBindings bindings = TerrainMaterialBindings.forPalette(
                palette,
                topsoil,
                soil,
                sand,
                rock);
        GeneratedWorldRuntime world = new GeneratedWorldBootstrap().create(
                genesis,
                assembly,
                palette,
                bindings);

        TerrainMaterialField expected = new TerrainMaterialGenerationStage().generate(
                world.atlas().elevation(),
                world.atlas().drainage(),
                palette);
        Set<LandscapeDefinitionId> observed = new HashSet<>();

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                int surface = world.atlas().elevation().elevationAt(x, y);
                for (int z = bounds.minZ(); z <= surface; z++) {
                    LandscapeDefinitionId expectedId = bindings.resolve(
                            expected.materialAt(x, y, z));
                    LandscapeDefinitionId actual = world.runtime().view().terrain().find(
                            x, y, z);
                    assertEquals(expectedId, actual);
                    observed.add(actual);
                }
            }
        }

        assertTrue(observed.contains(rock));
        assertTrue(observed.contains(topsoil) || observed.contains(sand));
        assertEquals(
                world.materialization().terrainCells(),
                observedCellCount(world, bounds));
    }

    private static long observedCellCount(
            GeneratedWorldRuntime world,
            WorldBounds bounds) {
        long count = 0L;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    if (world.runtime().view().terrain().contains(x, y, z)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static TerrainPalette palette() {
        return new TerrainPalette(
                "test:temperate",
                java.util.List.of(
                        new TerrainPreset(
                                TerrainPresetCatalog.NATURAL_GROUND,
                                TerrainPresetCapability.GROUND_PROFILE),
                        new TerrainPreset(
                                TerrainPresetCatalog.DEPOSITIONAL_SAND,
                                TerrainPresetCapability.SURFACE_DEPOSITION)),
                new TerrainPaletteMaterials(
                        TerrainMaterialKey.of("test:topsoil"),
                        TerrainMaterialKey.of("test:soil"),
                        TerrainMaterialKey.of("test:sand"),
                        TerrainMaterialKey.of("test:granite")));
    }
}
