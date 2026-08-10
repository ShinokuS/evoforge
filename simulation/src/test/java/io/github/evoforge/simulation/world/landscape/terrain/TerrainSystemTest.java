package io.github.evoforge.simulation.world.landscape.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;

final class TerrainSystemTest {

    private static final LandscapeDefinitionId GRANITE =
            LandscapeDefinitionId.of(0);
    private static final LandscapeDefinitionId SOIL =
            LandscapeDefinitionId.of(1);
    private static final LandscapeDefinitionId UNKNOWN =
            LandscapeDefinitionId.of(2);

    @Test
    void lookupReturnsTerrainAndReportsEmptyPositions() {
        TerrainSystem terrain = createTerrain();

        terrain.place(10, 20, -3, GRANITE);

        assertEquals(GRANITE, terrain.lookup().find(10, 20, -3));
        assertTrue(terrain.lookup().contains(10, 20, -3));
        assertNull(terrain.lookup().find(0, 0, 0));
        assertFalse(terrain.lookup().contains(0, 0, 0));
    }

    @Test
    void lookupIsStable() {
        TerrainSystem terrain = createTerrain();
        assertSame(terrain.lookup(), terrain.lookup());
    }

    @Test
    void placeRejectsOccupiedPositionWithoutChangingIt() {
        TerrainSystem terrain = createTerrain();
        terrain.place(1, 2, 3, GRANITE);

        assertThrows(
                IllegalStateException.class,
                () -> terrain.place(1, 2, 3, SOIL));
        assertEquals(GRANITE, terrain.lookup().find(1, 2, 3));
    }

    @Test
    void replaceChangesExistingTerrain() {
        TerrainSystem terrain = createTerrain();
        terrain.place(1, 2, 3, GRANITE);
        terrain.replace(1, 2, 3, SOIL);
        assertEquals(SOIL, terrain.lookup().find(1, 2, 3));
    }

    @Test
    void replaceRejectsEmptyPosition() {
        TerrainSystem terrain = createTerrain();
        assertThrows(
                IllegalStateException.class,
                () -> terrain.replace(1, 2, 3, SOIL));
        assertNull(terrain.lookup().find(1, 2, 3));
    }

    @Test
    void removeRemovesExistingTerrain() {
        TerrainSystem terrain = createTerrain();
        terrain.place(1, 2, 3, GRANITE);
        terrain.remove(1, 2, 3);
        assertNull(terrain.lookup().find(1, 2, 3));
    }

    @Test
    void removeRejectsEmptyPosition() {
        TerrainSystem terrain = createTerrain();
        assertThrows(
                IllegalStateException.class,
                () -> terrain.remove(1, 2, 3));
    }

    @Test
    void mutationsRejectNullAndUnknownDefinitions() {
        TerrainSystem terrain = createTerrain();

        assertThrows(
                IllegalArgumentException.class,
                () -> terrain.place(1, 2, 3, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> terrain.place(1, 2, 3, UNKNOWN));

        terrain.place(1, 2, 3, GRANITE);
        assertThrows(
                IllegalArgumentException.class,
                () -> terrain.replace(1, 2, 3, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> terrain.replace(1, 2, 3, UNKNOWN));
        assertEquals(GRANITE, terrain.lookup().find(1, 2, 3));
    }

    @Test
    void supportsFullIntCoordinateRange() {
        TerrainSystem terrain = createTerrain();
        terrain.place(
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                Integer.MIN_VALUE,
                GRANITE);
        assertEquals(
                GRANITE,
                terrain.lookup().find(
                        Integer.MIN_VALUE,
                        Integer.MAX_VALUE,
                        Integer.MIN_VALUE));
    }

    @Test
    void constructorRejectsNullDependencies() {
        TestDefinitionCatalog definitions = new TestDefinitionCatalog();
        assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainSystem(null, definitions));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainSystem(new TestTerrainStorage(), null));
    }

    private static TerrainSystem createTerrain() {
        TestDefinitionCatalog definitions = new TestDefinitionCatalog();
        definitions.add("core:granite", GRANITE);
        definitions.add("core:soil", SOIL);
        return new TerrainSystem(new TestTerrainStorage(), definitions);
    }

    private record Cell(int x, int y, int z) {
    }

    private static final class TestTerrainStorage implements TerrainStorage {
        private final Map<Cell, LandscapeDefinitionId> terrain = new HashMap<>();

        @Override
        public LandscapeDefinitionId find(int x, int y, int z) {
            return terrain.get(new Cell(x, y, z));
        }

        @Override
        public void put(
                int x,
                int y,
                int z,
                LandscapeDefinitionId definitionId) {
            terrain.put(new Cell(x, y, z), definitionId);
        }

        @Override
        public void remove(int x, int y, int z) {
            terrain.remove(new Cell(x, y, z));
        }
    }

    private static final class TestDefinitionCatalog
            implements DefinitionCatalog<LandscapeDefinitionId> {
        private final Map<String, LandscapeDefinitionId> byKey = new HashMap<>();
        private final Map<LandscapeDefinitionId, String> byId = new HashMap<>();

        void add(String key, LandscapeDefinitionId id) {
            byKey.put(key, id);
            byId.put(id, key);
        }

        @Override
        public LandscapeDefinitionId resolve(String key) {
            return byKey.get(key);
        }

        @Override
        public String keyOf(LandscapeDefinitionId id) {
            return byId.get(id);
        }

        @Override
        public boolean contains(LandscapeDefinitionId id) {
            return byId.containsKey(id);
        }
    }
}
