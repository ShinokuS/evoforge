package io.github.evoforge.simulation.world.landscape.soil.storage;

import java.util.HashMap;
import java.util.Map;

import io.github.evoforge.simulation.world.landscape.soil.SoilMoistureStorage;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

public final class SparseSoilMoistureStorage implements SoilMoistureStorage {

    private final Map<Cell, Integer> moisture = new HashMap<>();
    private final CellProbe lookupProbe = new CellProbe();

    @Override
    public int amount(
            int x,
            int y,
            int z) {

        lookupProbe.set(x, y, z);
        Integer amount = moisture.get(lookupProbe);
        return amount == null ? CellVolume.EMPTY : amount;
    }

    @Override
    public void put(
            int x,
            int y,
            int z,
            int amount) {

        CellVolume.requireValid(amount);
        if (amount == CellVolume.EMPTY) {
            throw new IllegalArgumentException(
                    "stored soil moisture must be positive");
        }

        moisture.put(new Cell(x, y, z), amount);
    }

    @Override
    public void remove(
            int x,
            int y,
            int z) {

        lookupProbe.set(x, y, z);
        moisture.remove(lookupProbe);
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
