package io.github.evoforge.simulation.world.terrain.storage;

import java.util.HashMap;
import java.util.Map;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.terrain.TerrainStorage;

public final class SparseTerrainStorage implements TerrainStorage {

    private final Map<Cell, LandscapeDefinitionId> terrain =
            new HashMap<>();
    private final CellProbe lookupProbe = new CellProbe();

    @Override
    public LandscapeDefinitionId find(
            int x,
            int y,
            int z) {

        lookupProbe.set(x, y, z);
        return terrain.get(lookupProbe);
    }

    @Override
    public void put(
            int x,
            int y,
            int z,
            LandscapeDefinitionId definitionId) {

        if (definitionId == null) {
            throw new IllegalArgumentException(
                    "definitionId must not be null");
        }

        terrain.put(
                new Cell(x, y, z),
                definitionId);
    }

    @Override
    public void remove(
            int x,
            int y,
            int z) {

        lookupProbe.set(x, y, z);
        terrain.remove(lookupProbe);
    }

    private static int hash(
            int x,
            int y,
            int z) {

        int result = Integer.hashCode(x);
        result = 31 * result + Integer.hashCode(y);
        result = 31 * result + Integer.hashCode(z);
        return result;
    }

    private record Cell(
            int x,
            int y,
            int z) {

        @Override
        public int hashCode() {
            return hash(x, y, z);
        }
    }

    /**
     * Mutable lookup-only key. The storage is already intentionally
     * single-owner/non-thread-safe; reusing this probe removes one allocation
     * from every read without changing the immutable keys stored in the map.
     */
    private static final class CellProbe {
        private int x;
        private int y;
        private int z;

        private void set(
                int x,
                int y,
                int z) {

            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int hashCode() {
            return hash(x, y, z);
        }

        @Override
        public boolean equals(
                Object other) {

            if (other instanceof Cell cell) {
                return x == cell.x()
                        && y == cell.y()
                        && z == cell.z();
            }
            if (other instanceof CellProbe probe) {
                return x == probe.x
                        && y == probe.y
                        && z == probe.z;
            }
            return false;
        }
    }
}
