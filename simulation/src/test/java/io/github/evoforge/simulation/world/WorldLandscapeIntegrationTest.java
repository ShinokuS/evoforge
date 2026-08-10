package io.github.evoforge.simulation.world;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionBootstrap;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainSystem;
import io.github.evoforge.simulation.world.landscape.terrain.storage.SparseTerrainStorage;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

final class WorldLandscapeIntegrationTest {

    @TempDir
    Path directory;

    @Test
    void loadsLandscapeAndExposesPlacedTerrainThroughWorld()
            throws IOException {

        Path landscapeDirectory =
                Files.createDirectory(
                        directory.resolve("landscape"));

        Files.writeString(
                landscapeDirectory.resolve("granite.json"),
                """
                        {
                            "key": "core:granite",
                            "aspects": {}
                        }
                        """,
                UTF_8);

        LandscapeDefinitionBootstrap landscapeBootstrap =
                new LandscapeDefinitionBootstrap();

        DefinitionRegistry<LandscapeDefinitionId> landscapeDefinitions =
                landscapeBootstrap.load(landscapeDirectory);

        LandscapeDefinitionId granite =
                landscapeDefinitions.resolve("core:granite");

        TerrainSystem terrainSystem =
                new TerrainSystem(
                        new SparseTerrainStorage(),
                        landscapeDefinitions);

        DefinitionRegistry<ObjectDefinitionId> objectDefinitions =
                new DefinitionRegistry<>(
                        ObjectDefinitionId::of,
                        ObjectDefinitionId::asInt);

        World world =
                new World(
                        objectDefinitions,
                        terrainSystem);

        terrainSystem.place(
                10,
                20,
                30,
                granite);

        assertEquals(
                granite,
                world.terrain().find(
                        10,
                        20,
                        30));
    }
}
