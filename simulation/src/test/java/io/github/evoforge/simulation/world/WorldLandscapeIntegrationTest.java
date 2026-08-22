package io.github.evoforge.simulation.world;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionBootstrap;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.terrain.TerrainSystem;
import io.github.evoforge.simulation.world.terrain.storage.SparseTerrainStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorldLandscapeIntegrationTest {

    @TempDir
    Path directory;

    @Test
    void loadsLandscapeAndExposesPlacedTerrainThroughLookup()
            throws IOException {

        Path landscapeDirectory = Files.createDirectory(directory.resolve("landscape"));
        Files.writeString(
                landscapeDirectory.resolve("granite.json"),
                """
                        {
                            "key": "core:granite",
                            "aspects": {}
                        }
                        """,
                UTF_8);

        LandscapeDefinitionBootstrap landscapeBootstrap = new LandscapeDefinitionBootstrap();
        DefinitionRegistry<LandscapeDefinitionId> landscapeDefinitions = landscapeBootstrap.load(landscapeDirectory);
        LandscapeDefinitionId granite = landscapeDefinitions.resolve("core:granite");

        TerrainSystem terrainSystem = new TerrainSystem(
                new SparseTerrainStorage(),
                landscapeDefinitions);
        TerrainLookup terrain = terrainSystem.lookup();

        terrainSystem.place(10, 20, 30, granite);

        assertEquals(granite, terrain.find(10, 20, 30));
    }
}
