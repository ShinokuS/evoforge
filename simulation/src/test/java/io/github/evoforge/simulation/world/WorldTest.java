package io.github.evoforge.simulation.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainSystem;
import io.github.evoforge.simulation.world.landscape.terrain.storage.SparseTerrainStorage;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

final class WorldTest {

    private static final LandscapeDefinitionId GRANITE =
            LandscapeDefinitionId.of(0);

    @Test
    void ownsWorldInfrastructure() {
        DefinitionRegistry<ObjectDefinitionId> objectDefinitions =
                createObjectDefinitions();

        objectDefinitions.register("core:test");

        World world = new World(
                objectDefinitions,
                createTerrainSystem());

        assertNotNull(world.objects());
        assertNotNull(world.objectFactory());
        assertNotNull(world.terrain());
    }

    @Test
    void createsObjectsFromObjectDefinitions() {
        DefinitionRegistry<ObjectDefinitionId> objectDefinitions =
                createObjectDefinitions();

        ObjectDefinitionId definitionId =
                objectDefinitions.register("core:test");

        World world = new World(
                objectDefinitions,
                createTerrainSystem());

        WorldObject object =
                world.objectFactory().create("core:test");

        assertEquals(
                definitionId,
                object.definitionId());
        assertEquals(
                object,
                world.objects().get(object.id()));
    }

    @Test
    void exposesTerrainLookup() {
        TerrainSystem terrainSystem = createTerrainSystem();

        terrainSystem.place(
                10,
                20,
                30,
                GRANITE);

        World world = new World(
                createObjectDefinitions(),
                terrainSystem);

        assertEquals(
                GRANITE,
                world.terrain().find(10, 20, 30));
    }

    @Test
    void rejectsNullObjectDefinitions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new World(
                        null,
                        createTerrainSystem()));
    }

    @Test
    void rejectsNullTerrainSystem() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new World(
                        createObjectDefinitions(),
                        null));
    }

    private static DefinitionRegistry<ObjectDefinitionId>
            createObjectDefinitions() {

        return new DefinitionRegistry<>(
                ObjectDefinitionId::of,
                ObjectDefinitionId::asInt);
    }

    private static TerrainSystem createTerrainSystem() {
        DefinitionRegistry<LandscapeDefinitionId> definitions =
                new DefinitionRegistry<>(
                        LandscapeDefinitionId::of,
                        LandscapeDefinitionId::asInt);

        definitions.register("core:granite");

        return new TerrainSystem(
                new SparseTerrainStorage(),
                definitions);
    }
}
