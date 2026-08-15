package io.github.evoforge.simulation.world.landscape.liquid;

import java.util.Map;
import java.util.TreeMap;

/** Mutation-maintained derived index of positive-liquid cells per XY column. */
final class LiquidSurfaceIndex {

    private final TreeMap<Column, TreeMap<Integer, LiquidTypeId>> cellsByColumn =
            new TreeMap<>();

    private final LiquidSurfaceLookup lookup = new LiquidSurfaceLookup() {
        @Override
        public boolean hasColumn(int x, int y) {
            return cellsByColumn.containsKey(new Column(x, y));
        }

        @Override
        public int topZ(int x, int y) {
            return requireColumn(x, y).lastKey();
        }

        @Override
        public LiquidTypeId topType(int x, int y) {
            return requireColumn(x, y).lastEntry().getValue();
        }

        @Override
        public int columnCount() {
            return cellsByColumn.size();
        }

        @Override
        public void forEach(LiquidSurfaceConsumer consumer) {
            requireConsumer(consumer);
            for (Map.Entry<Column, TreeMap<Integer, LiquidTypeId>> entry
                    : cellsByColumn.entrySet()) {
                Column column = entry.getKey();
                Map.Entry<Integer, LiquidTypeId> top = entry.getValue().lastEntry();
                consumer.accept(column.x(), column.y(), top.getKey(), top.getValue());
            }
        }

        @Override
        public boolean hasColumn(LiquidTypeId type, int x, int y) {
            requireType(type);
            TreeMap<Integer, LiquidTypeId> cells = cellsByColumn.get(new Column(x, y));
            return cells != null && topZOf(type, cells) != null;
        }

        @Override
        public int topZ(LiquidTypeId type, int x, int y) {
            requireType(type);
            TreeMap<Integer, LiquidTypeId> cells = cellsByColumn.get(new Column(x, y));
            Integer top = cells == null ? null : topZOf(type, cells);
            if (top == null) {
                throw new IllegalArgumentException(
                        "liquid column does not contain " + type + ": " + x + ", " + y);
            }
            return top;
        }

        @Override
        public int columnCount(LiquidTypeId type) {
            requireType(type);
            int count = 0;
            for (TreeMap<Integer, LiquidTypeId> cells : cellsByColumn.values()) {
                if (topZOf(type, cells) != null) count++;
            }
            return count;
        }

        @Override
        public void forEach(LiquidTypeId type, LiquidSurfaceConsumer consumer) {
            requireType(type);
            requireConsumer(consumer);
            for (Map.Entry<Column, TreeMap<Integer, LiquidTypeId>> entry
                    : cellsByColumn.entrySet()) {
                Integer top = topZOf(type, entry.getValue());
                if (top == null) continue;
                Column column = entry.getKey();
                consumer.accept(column.x(), column.y(), top, type);
            }
        }
    };

    LiquidSurfaceLookup lookup() {
        return lookup;
    }

    void becameWet(int x, int y, int z, LiquidTypeId type) {
        requireType(type);
        LiquidTypeId previous = cellsByColumn
                .computeIfAbsent(new Column(x, y), ignored -> new TreeMap<>())
                .putIfAbsent(z, type);
        if (previous != null) {
            throw new IllegalStateException(
                    "liquid surface index already contains wet cell: "
                            + x + ", " + y + ", " + z);
        }
    }

    void becameDry(int x, int y, int z) {
        Column column = new Column(x, y);
        TreeMap<Integer, LiquidTypeId> cells = cellsByColumn.get(column);
        if (cells == null || cells.remove(z) == null) {
            throw new IllegalStateException(
                    "liquid surface index is missing wet cell: "
                            + x + ", " + y + ", " + z);
        }
        if (cells.isEmpty()) cellsByColumn.remove(column);
    }

    private TreeMap<Integer, LiquidTypeId> requireColumn(int x, int y) {
        TreeMap<Integer, LiquidTypeId> cells = cellsByColumn.get(new Column(x, y));
        if (cells == null || cells.isEmpty()) {
            throw new IllegalArgumentException("liquid column is dry: " + x + ", " + y);
        }
        return cells;
    }

    private static Integer topZOf(
            LiquidTypeId type,
            TreeMap<Integer, LiquidTypeId> cells) {
        for (Map.Entry<Integer, LiquidTypeId> entry : cells.descendingMap().entrySet()) {
            if (type.equals(entry.getValue())) return entry.getKey();
        }
        return null;
    }

    private static void requireType(LiquidTypeId type) {
        if (type == null) throw new IllegalArgumentException("liquid type must not be null");
    }

    private static void requireConsumer(LiquidSurfaceConsumer consumer) {
        if (consumer == null) throw new IllegalArgumentException("consumer must not be null");
    }

    private record Column(int x, int y) implements Comparable<Column> {
        @Override
        public int compareTo(Column other) {
            int xOrder = Integer.compare(x, other.x);
            return xOrder != 0 ? xOrder : Integer.compare(y, other.y);
        }
    }
}
