package io.github.evoforge.simulation.world.landscape.terrain.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;

final class SparseTerrainStorageTest {

    private static final LandscapeDefinitionId GRANITE =
            LandscapeDefinitionId.of(0);

    private static final LandscapeDefinitionId SOIL =
            LandscapeDefinitionId.of(1);

    @Test
    void emptyPositionReturnsNull() {
        SparseTerrainStorage storage =
                new SparseTerrainStorage();

        assertNull(
                storage.find(1, 2, 3));
    }

    @Test
    void storesTerrainAtPosition() {
        SparseTerrainStorage storage =
                new SparseTerrainStorage();

        storage.put(1, 2, 3, GRANITE);

        assertEquals(
                GRANITE,
                storage.find(1, 2, 3));
    }

    @Test
    void differentCoordinatesAreIndependent() {
        SparseTerrainStorage storage =
                new SparseTerrainStorage();

        storage.put(1, 2, 3, GRANITE);
        storage.put(1, 2, 4, SOIL);

        assertEquals(
                GRANITE,
                storage.find(1, 2, 3));

        assertEquals(
                SOIL,
                storage.find(1, 2, 4));
    }

    @Test
    void putReplacesStoredValue() {
        SparseTerrainStorage storage =
                new SparseTerrainStorage();

        storage.put(1, 2, 3, GRANITE);
        storage.put(1, 2, 3, SOIL);

        assertEquals(
                SOIL,
                storage.find(1, 2, 3));
    }

    @Test
    void removesTerrain() {
        SparseTerrainStorage storage =
                new SparseTerrainStorage();

        storage.put(1, 2, 3, GRANITE);
        storage.remove(1, 2, 3);

        assertNull(
                storage.find(1, 2, 3));
    }

    @Test
    void removingMissingPositionDoesNothing() {
        SparseTerrainStorage storage =
                new SparseTerrainStorage();

        storage.remove(1, 2, 3);

        assertNull(
                storage.find(1, 2, 3));
    }

    @Test
    void rejectsNullDefinition() {
        SparseTerrainStorage storage =
                new SparseTerrainStorage();

        assertThrows(
                IllegalArgumentException.class,
                () -> storage.put(
                        1,
                        2,
                        3,
                        null));
    }

    @Test
    void supportsFullIntCoordinateRange() {
        SparseTerrainStorage storage =
                new SparseTerrainStorage();

        storage.put(
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                Integer.MIN_VALUE,
                GRANITE);

        assertEquals(
                GRANITE,
                storage.find(
                        Integer.MIN_VALUE,
                        Integer.MAX_VALUE,
                        Integer.MIN_VALUE));
    }
}
