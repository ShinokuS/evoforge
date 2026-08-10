package io.github.evoforge.simulation.world;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionBootstrap;
import io.github.evoforge.simulation.world.mechanics.physical.PhysicalDefinitionCompiler;
import io.github.evoforge.simulation.world.mechanics.physical.PhysicalDefinitions;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainSystem;
import io.github.evoforge.simulation.world.landscape.terrain.storage.SparseTerrainStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldDefinitionIntegrationTest {

    @TempDir
    Path directory;

    @Test
    void createsWorldObjectFromLoadedDefinition()
            throws IOException {

        Files.writeString(
                directory.resolve("apple.json"),
                """
                        {
                            "key": "core:apple",
                            "aspects": {
                                "physical": {
                                    "mass": 0.18
                                }
                            }
                        }
                        """,
                UTF_8);

        PhysicalDefinitions physical = new PhysicalDefinitions();

        ObjectDefinitionBootstrap bootstrap = new ObjectDefinitionBootstrap(
                new PhysicalDefinitionCompiler(
                        physical));

        DefinitionRegistry<ObjectDefinitionId> definitions = bootstrap.load(directory);

        DefinitionRegistry<LandscapeDefinitionId> landscapeDefinitions =
                new DefinitionRegistry<>(
                        LandscapeDefinitionId::of,
                        LandscapeDefinitionId::asInt);

        World world = new World(
                definitions,
                new TerrainSystem(
                        new SparseTerrainStorage(),
                        landscapeDefinitions));

        WorldObject apple = world.objectFactory().create(
                "core:apple");

        assertEquals(
                definitions.resolve("core:apple"),
                apple.definitionId());

        assertEquals(
                0.18,
                physical.mass(
                        apple.definitionId()));

        assertTrue(
                world.objects().isAlive(
                        apple.id()));

        assertSame(
                apple,
                world.objects().get(
                        apple.id()));
    }
}
