package io.github.evoforge.simulation.world.terrain;

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
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;

final class TerrainSystemTest {

    private static final MaterialDefinitionId GRANITE =
            MaterialDefinitionId.of(0);
    private static final MaterialDefinitionId SOIL =
            MaterialDefinitionId.of(1);
    private static final MaterialDefinitionId UNKNOWN =
            MaterialDefinitionId.of(2);

    @Test
    void lookupReturnsTerrainAndReportsEmptyPositions() {
        TerrainSystem terrain = createTerrain();

        assertEquals(
                TerrainPlacementResult.PLACED,
                terrain.place(10, 20, -3, GRANITE));

        assertEquals(GRANITE, terrain.lookup().find(10, 20, -3));
        assertTrue(terrain.lookup().contains(10, 20, -3));
        assertNull(terrain.lookup().find(0, 0, 0));
        assertFalse(terrain.lookup().contains(0, 0, 0));
    }

    @Test
    void lookupIsStable() {
        TerrainSystem terrain = createTerrain();
        assertSame(terrain.lookup(), terrain.lookup());
        assertSame(terrain.extents(), terrain.extents());
        assertSame(terrain.revisions(), terrain.revisions());
    }

    @Test
    void revisionAdvancesOnlyForAcceptedMutations() {
        TerrainSystem terrain = createTerrain();
        TerrainRevisionLookup revisions = terrain.revisions();

        assertEquals(0L, revisions.revision());
        assertEquals(
                TerrainPlacementResult.PLACED,
                terrain.place(1, 2, 3, GRANITE));
        assertEquals(1L, revisions.revision());

        assertEquals(
                TerrainPlacementResult.POSITION_OCCUPIED,
                terrain.place(1, 2, 3, SOIL));
        assertEquals(1L, revisions.revision());

        assertEquals(
                TerrainReplacementResult.REPLACED,
                terrain.replace(1, 2, 3, SOIL));
        assertEquals(2L, revisions.revision());

        assertEquals(
                TerrainReplacementResult.TERRAIN_ABSENT,
                terrain.replace(8, 8, 8, SOIL));
        assertEquals(2L, revisions.revision());

        assertEquals(
                TerrainRemovalResult.REMOVED,
                terrain.remove(1, 2, 3));
        assertEquals(3L, revisions.revision());

        assertEquals(
                TerrainRemovalResult.TERRAIN_ABSENT,
                terrain.remove(1, 2, 3));
        assertEquals(3L, revisions.revision());
    }

    @Test
    void extentsTrackPlacementAndRemovalWithoutScanningStorage() {
        TerrainSystem terrain = createTerrain();
        TerrainExtentLookup extents = terrain.extents();

        assertTrue(extents.empty());
        assertThrows(IllegalStateException.class, extents::minZ);
        assertThrows(IllegalStateException.class, extents::maxZ);

        assertEquals(
                TerrainPlacementResult.PLACED,
                terrain.place(0, 0, 3, GRANITE));
        assertEquals(
                TerrainPlacementResult.PLACED,
                terrain.place(1, 0, -4, GRANITE));
        assertEquals(
                TerrainPlacementResult.PLACED,
                terrain.place(2, 0, 3, SOIL));

        assertFalse(extents.empty());
        assertEquals(-4, extents.minZ());
        assertEquals(3, extents.maxZ());

        assertEquals(
                TerrainRemovalResult.REMOVED,
                terrain.remove(0, 0, 3));
        assertEquals(3, extents.maxZ());

        assertEquals(
                TerrainRemovalResult.REMOVED,
                terrain.remove(2, 0, 3));
        assertEquals(-4, extents.maxZ());

        assertEquals(
                TerrainRemovalResult.REMOVED,
                terrain.remove(1, 0, -4));
        assertTrue(extents.empty());
    }

    @Test
    void placeRejectsOccupiedPositionWithoutChangingIt() {
        TerrainSystem terrain = createTerrain();
        assertEquals(
                TerrainPlacementResult.PLACED,
                terrain.place(1, 2, 3, GRANITE));

        assertEquals(
                TerrainPlacementResult.POSITION_OCCUPIED,
                terrain.place(1, 2, 3, SOIL));
        assertEquals(GRANITE, terrain.lookup().find(1, 2, 3));
    }

    @Test
    void replaceChangesExistingTerrain() {
        TerrainSystem terrain = createTerrain();
        assertEquals(
                TerrainPlacementResult.PLACED,
                terrain.place(1, 2, 3, GRANITE));
        assertEquals(
                TerrainReplacementResult.REPLACED,
                terrain.replace(1, 2, 3, SOIL));
        assertEquals(SOIL, terrain.lookup().find(1, 2, 3));
    }

    @Test
    void replaceRejectsEmptyPosition() {
        TerrainSystem terrain = createTerrain();
        assertEquals(
                TerrainReplacementResult.TERRAIN_ABSENT,
                terrain.replace(1, 2, 3, SOIL));
        assertNull(terrain.lookup().find(1, 2, 3));
    }

    @Test
    void removeRemovesExistingTerrain() {
        TerrainSystem terrain = createTerrain();
        assertEquals(
                TerrainPlacementResult.PLACED,
                terrain.place(1, 2, 3, GRANITE));
        assertEquals(
                TerrainRemovalResult.REMOVED,
                terrain.remove(1, 2, 3));
        assertNull(terrain.lookup().find(1, 2, 3));
    }

    @Test
    void removeRejectsEmptyPosition() {
        TerrainSystem terrain = createTerrain();
        assertEquals(
                TerrainRemovalResult.TERRAIN_ABSENT,
                terrain.remove(1, 2, 3));
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

        assertEquals(
                TerrainPlacementResult.PLACED,
                terrain.place(1, 2, 3, GRANITE));
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
        assertEquals(
                TerrainPlacementResult.PLACED,
                terrain.place(
                        Integer.MIN_VALUE,
                        Integer.MAX_VALUE,
                        Integer.MIN_VALUE,
                        GRANITE));
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
        private final Map<Cell, MaterialDefinitionId> terrain = new HashMap<>();

        @Override
        public MaterialDefinitionId find(int x, int y, int z) {
            return terrain.get(new Cell(x, y, z));
        }

        @Override
        public void put(
                int x,
                int y,
                int z,
                MaterialDefinitionId definitionId) {
            terrain.put(new Cell(x, y, z), definitionId);
        }

        @Override
        public void remove(int x, int y, int z) {
            terrain.remove(new Cell(x, y, z));
        }
    }

    private static final class TestDefinitionCatalog
            implements DefinitionCatalog<MaterialDefinitionId> {
        private final Map<String, MaterialDefinitionId> byKey = new HashMap<>();
        private final Map<MaterialDefinitionId, String> byId = new HashMap<>();

        void add(String key, MaterialDefinitionId id) {
            byKey.put(key, id);
            byId.put(id, key);
        }

        @Override
        public MaterialDefinitionId resolve(String key) {
            return byKey.get(key);
        }

        @Override
        public String keyOf(MaterialDefinitionId id) {
            return byId.get(id);
        }

        @Override
        public boolean contains(MaterialDefinitionId id) {
            return byId.containsKey(id);
        }
    }
}
