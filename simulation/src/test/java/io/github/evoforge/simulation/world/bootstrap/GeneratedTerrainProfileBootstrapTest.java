package io.github.evoforge.simulation.world.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.geology.CompiledGeologyProfile;
import io.github.evoforge.simulation.world.geology.GeologyGenerationStage;
import io.github.evoforge.simulation.world.geology.GeologyMaterialKey;
import io.github.evoforge.simulation.world.geology.GeologyProfileCompiler;
import io.github.evoforge.simulation.world.geology.GeologyProfileDefinition;
import io.github.evoforge.simulation.world.geology.GeologyUnitKey;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class GeneratedTerrainProfileBootstrapTest {
    @Test
    void productionBootstrapMaterializesGeneratedSurfaceAndGeologyMaterials() {
        WorldBounds bounds = new WorldBounds(-12, 11, -12, 11, -12, 12);
        WorldGenesis genesis = WorldGenesis.current(new WorldSpec(bounds), 42L);
        CompiledTerrainProfile terrainProfile = terrainProfile();
        CompiledGeologyProfile geologyProfile = geologyProfile();

        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId topsoil = assembly.landscapeDefinition("test:topsoil", 1_050);
        assembly.soilProperties(topsoil, 550_000, 100_000);
        LandscapeDefinitionId soil = assembly.landscapeDefinition("test:soil", 1_100);
        assembly.soilProperties(soil, 450_000, 60_000);
        LandscapeDefinitionId sand = assembly.landscapeDefinition("test:sand", 1_300);
        assembly.soilProperties(sand, 350_000, 250_000);
        LandscapeDefinitionId legacyRock = assembly.landscapeDefinition("test:granite");

        TerrainMaterialBindings bindings = TerrainMaterialBindings.forProfile(
                terrainProfile,
                Map.of(
                        TerrainMaterialRole.SURFACE, topsoil,
                        TerrainMaterialRole.SUBSURFACE, soil,
                        TerrainMaterialRole.SEDIMENT, sand,
                        TerrainMaterialRole.BEDROCK, legacyRock));

        Map<TerrainMaterialKey, LandscapeDefinitionId> geologyBindings = new LinkedHashMap<>();
        Set<LandscapeDefinitionId> geologyIds = new HashSet<>();
        for (GeologyMaterialKey material : geologyProfile.materials().values()) {
            TerrainMaterialKey key = TerrainMaterialKey.of(material.value());
            LandscapeDefinitionId id = key.value().equals("test:granite")
                    ? legacyRock
                    : assembly.landscapeDefinition(key.value());
            geologyBindings.put(key, id);
            geologyIds.add(id);
        }
        bindings = bindings.withMaterials(geologyBindings);

        GeneratedWorldRuntime world = new GeneratedWorldBootstrap(
                WorldAtlasGenerator.withGeology(new GeologyGenerationStage(geologyProfile)))
                .create(genesis, assembly, terrainProfile, bindings);

        TerrainMaterialField expected = new TerrainMaterialGenerationStage().generate(
                world.atlas().elevation(),
                world.atlas().geology(),
                world.atlas().drainage(),
                world.atlas().surfaceHydrology(),
                terrainProfile);
        Set<LandscapeDefinitionId> observed = new HashSet<>();
        Set<LandscapeDefinitionId> observedGeology = new HashSet<>();

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                int surface = world.atlas().elevation().elevationAt(x, y);
                for (int z = bounds.minZ(); z <= surface; z++) {
                    LandscapeDefinitionId expectedId = bindings.resolve(expected.materialAt(x, y, z));
                    LandscapeDefinitionId actual = world.runtime().view().terrain().find(x, y, z);
                    assertEquals(expectedId, actual);
                    observed.add(actual);
                    if (geologyIds.contains(actual)) observedGeology.add(actual);
                }
            }
        }

        assertTrue(observedGeology.size() > 1, "materialized terrain exposes only one geology unit");
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

    private static CompiledTerrainProfile terrainProfile() {
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
                                TerrainMaterialRole.SURFACE, TerrainMaterialKey.of("test:topsoil"),
                                TerrainMaterialRole.SUBSURFACE, TerrainMaterialKey.of("test:soil"),
                                TerrainMaterialRole.SEDIMENT, TerrainMaterialKey.of("test:sand"),
                                TerrainMaterialRole.BEDROCK, TerrainMaterialKey.of("test:granite"))));
    }

    private static CompiledGeologyProfile geologyProfile() {
        return new GeologyProfileCompiler().compile(new GeologyProfileDefinition(
                "test:crust",
                List.of(
                        unit("test:granite"),
                        unit("test:basalt"),
                        unit("test:limestone"),
                        unit("test:shale"))));
    }

    private static GeologyProfileDefinition.UnitDefinition unit(String key) {
        return new GeologyProfileDefinition.UnitDefinition(
                GeologyUnitKey.of(key),
                GeologyMaterialKey.of(key));
    }
}
