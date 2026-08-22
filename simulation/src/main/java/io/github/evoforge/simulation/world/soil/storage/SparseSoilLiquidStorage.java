package io.github.evoforge.simulation.world.soil.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import io.github.evoforge.simulation.world.liquid.LiquidTypeId;
import io.github.evoforge.simulation.world.soil.SoilLiquidStorage;
import io.github.evoforge.simulation.world.space.measurement.CellVolume;

/** Sparse multi-constituent retained-liquid storage for Soil cells. */
public final class SparseSoilLiquidStorage implements SoilLiquidStorage {

    private final Map<Cell, Composition> cells = new HashMap<>();
    private final CellProbe lookupProbe = new CellProbe();

    @Override
    public int amountOf(
            LiquidTypeId type,
            int x,
            int y,
            int z) {

        requireType(type);
        Composition composition = composition(x, y, z);
        return composition == null
                ? CellVolume.EMPTY
                : composition.amountOf(type);
    }

    @Override
    public int totalAmount(int x, int y, int z) {
        Composition composition = composition(x, y, z);
        return composition == null
                ? CellVolume.EMPTY
                : composition.totalAmount();
    }

    @Override
    public void put(
            int x,
            int y,
            int z,
            LiquidTypeId type,
            int amount) {

        requireType(type);
        CellVolume.requireValid(amount);
        if (amount == CellVolume.EMPTY) {
            throw new IllegalArgumentException(
                    "stored Soil liquid amount must be positive");
        }

        Composition composition = cells.computeIfAbsent(
                new Cell(x, y, z), ignored -> new Composition());
        composition.put(type, amount);
    }

    @Override
    public void remove(
            int x,
            int y,
            int z,
            LiquidTypeId type) {

        requireType(type);
        lookupProbe.set(x, y, z);
        Composition composition = cells.get(lookupProbe);
        if (composition == null) return;

        composition.remove(type);
        if (composition.isEmpty()) {
            cells.remove(lookupProbe);
        }
    }

    private Composition composition(int x, int y, int z) {
        lookupProbe.set(x, y, z);
        return cells.get(lookupProbe);
    }

    private static void requireType(LiquidTypeId type) {
        if (type == null) {
            throw new IllegalArgumentException("liquid type must not be null");
        }
    }

    private static int hash(int x, int y, int z) {
        int result = Integer.hashCode(x);
        result = 31 * result + Integer.hashCode(y);
        result = 31 * result + Integer.hashCode(z);
        return result;
    }

    private static final class Composition {
        private final TreeMap<LiquidTypeId, Integer> amounts = new TreeMap<>();
        private int totalAmount;

        int amountOf(LiquidTypeId type) {
            return amounts.getOrDefault(type, CellVolume.EMPTY);
        }

        int totalAmount() {
            return totalAmount;
        }

        void put(LiquidTypeId type, int amount) {
            int previous = amounts.getOrDefault(type, CellVolume.EMPTY);
            totalAmount = Math.addExact(totalAmount, amount - previous);
            CellVolume.requireValid(totalAmount);
            amounts.put(type, amount);
        }

        void remove(LiquidTypeId type) {
            Integer previous = amounts.remove(type);
            if (previous != null) {
                totalAmount -= previous;
            }
        }

        boolean isEmpty() {
            return amounts.isEmpty();
        }
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

        void set(int x, int y, int z) {
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
