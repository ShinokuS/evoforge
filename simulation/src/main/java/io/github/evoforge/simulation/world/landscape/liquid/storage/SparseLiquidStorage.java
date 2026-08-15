package io.github.evoforge.simulation.world.landscape.liquid.storage;

import java.util.HashMap;
import java.util.Map;

import io.github.evoforge.simulation.world.landscape.liquid.LiquidStorage;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidTypeId;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/** Sparse single-component liquid storage. Dry cells allocate no entry. */
public final class SparseLiquidStorage implements LiquidStorage {

    private final Map<Cell, Entry> liquids = new HashMap<>();
    private final CellProbe lookupProbe = new CellProbe();

    @Override
    public LiquidTypeId typeAt(int x, int y, int z) {
        Entry entry = entry(x, y, z);
        return entry == null ? null : entry.type();
    }

    @Override
    public int amount(int x, int y, int z) {
        Entry entry = entry(x, y, z);
        return entry == null ? CellVolume.EMPTY : entry.amount();
    }

    @Override
    public void put(
            int x,
            int y,
            int z,
            LiquidTypeId type,
            int amount) {

        if (type == null) {
            throw new IllegalArgumentException("liquid type must not be null");
        }
        CellVolume.requireValid(amount);
        if (amount == CellVolume.EMPTY) {
            throw new IllegalArgumentException("stored liquid amount must be positive");
        }
        liquids.put(new Cell(x, y, z), new Entry(type, amount));
    }

    @Override
    public void remove(int x, int y, int z) {
        lookupProbe.set(x, y, z);
        liquids.remove(lookupProbe);
    }

    private Entry entry(int x, int y, int z) {
        lookupProbe.set(x, y, z);
        return liquids.get(lookupProbe);
    }

    private static int hash(int x, int y, int z) {
        int result = Integer.hashCode(x);
        result = 31 * result + Integer.hashCode(y);
        result = 31 * result + Integer.hashCode(z);
        return result;
    }

    private record Entry(LiquidTypeId type, int amount) {
    }

    private record Cell(int x, int y, int z) {
        @Override
        public int hashCode() {
            return hash(x, y, z);
        }
    }

    private static final class CellProbe {
        private int x;
        private int y;
        private int z;

        private void set(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int hashCode() {
            return hash(x, y, z);
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof Cell cell) {
                return x == cell.x() && y == cell.y() && z == cell.z();
            }
            if (other instanceof CellProbe probe) {
                return x == probe.x && y == probe.y && z == probe.z;
            }
            return false;
        }
    }
}
