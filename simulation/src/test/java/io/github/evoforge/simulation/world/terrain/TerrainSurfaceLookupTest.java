package io.github.evoforge.simulation.world.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;

final class TerrainSurfaceLookupTest {

    private static final LandscapeDefinitionId TERRAIN =
            LandscapeDefinitionId.of(0);

    @Test
    void tracksTopTerrainPerColumnThroughPlacementAndRemoval() {
        TerrainSystem terrain = terrain();
        TerrainSurfaceLookup surfaces = terrain.surfaces();

        terrain.place(4, 5, -2, TERRAIN);
        terrain.place(4, 5, 7, TERRAIN);
        terrain.place(4, 5, 3, TERRAIN);

        assertTrue(surfaces.hasColumn(4, 5));
        assertEquals(7, surfaces.topZ(4, 5));
        assertEquals(1, surfaces.columnCount());

        terrain.remove(4, 5, 7);
        assertEquals(3, surfaces.topZ(4, 5));

        terrain.remove(4, 5, 3);
        terrain.remove(4, 5, -2);
        assertFalse(surfaces.hasColumn(4, 5));
        assertEquals(0, surfaces.columnCount());
        assertThrows(
                IllegalArgumentException.class,
                () -> surfaces.topZ(4, 5));
    }

    @Test
    void iteratesColumnsInStableCoordinateOrder() {
        TerrainSystem terrain = terrain();
        TerrainSurfaceLookup surfaces = terrain.surfaces();

        terrain.place(2, -1, 8, TERRAIN);
        terrain.place(-3, 9, 1, TERRAIN);
        terrain.place(2, -1, 11, TERRAIN);
        terrain.place(2, 4, -7, TERRAIN);

        List<String> visited = new ArrayList<>();
        surfaces.forEach((x, y, z) ->
                visited.add(x + ":" + y + ":" + z));

        assertEquals(
                List.of(
                        "-3:9:1",
                        "2:-1:11",
                        "2:4:-7"),
                visited);
    }

    private static TerrainSystem terrain() {
        return new TerrainSystem(
                new TestStorage(),
                new TestDefinitions());
    }

    private record Cell(int x, int y, int z) {
    }

    private static final class TestStorage implements TerrainStorage {
        private final Map<Cell, LandscapeDefinitionId> values =
                new HashMap<>();

        @Override
        public LandscapeDefinitionId find(int x, int y, int z) {
            return values.get(new Cell(x, y, z));
        }

        @Override
        public void put(
                int x,
                int y,
                int z,
                LandscapeDefinitionId definitionId) {
            values.put(new Cell(x, y, z), definitionId);
        }

        @Override
        public void remove(int x, int y, int z) {
            values.remove(new Cell(x, y, z));
        }
    }

    private static final class TestDefinitions
            implements DefinitionCatalog<LandscapeDefinitionId> {

        @Override
        public LandscapeDefinitionId resolve(String key) {
            return "core:terrain".equals(key) ? TERRAIN : null;
        }

        @Override
        public String keyOf(LandscapeDefinitionId id) {
            return TERRAIN.equals(id) ? "core:terrain" : null;
        }

        @Override
        public boolean contains(LandscapeDefinitionId id) {
            return TERRAIN.equals(id);
        }
    }
}
