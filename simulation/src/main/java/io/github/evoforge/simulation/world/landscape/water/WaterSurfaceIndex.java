package io.github.evoforge.simulation.world.landscape.water;

import java.util.TreeMap;
import java.util.TreeSet;

/** Mutation-maintained derived index of positive-water Z values per XY column. */
final class WaterSurfaceIndex {

    private final TreeMap<Column, TreeSet<Integer>> zByColumn =
            new TreeMap<>();
    private final WaterSurfaceLookup lookup =
            new WaterSurfaceLookup() {
                @Override
                public boolean hasColumn(int x, int y) {
                    return zByColumn.containsKey(new Column(x, y));
                }

                @Override
                public int topZ(int x, int y) {
                    TreeSet<Integer> zs = zByColumn.get(new Column(x, y));
                    if (zs == null || zs.isEmpty()) {
                        throw new IllegalArgumentException(
                                "water column is dry: " + x + ", " + y);
                    }
                    return zs.last();
                }

                @Override
                public int columnCount() {
                    return zByColumn.size();
                }
            };

    WaterSurfaceLookup lookup() {
        return lookup;
    }

    void becameWet(int x, int y, int z) {
        boolean added = zByColumn
                .computeIfAbsent(
                        new Column(x, y),
                        ignored -> new TreeSet<>())
                .add(z);
        if (!added) {
            throw new IllegalStateException(
                    "water surface index already contains wet cell: "
                            + x + ", " + y + ", " + z);
        }
    }

    void becameDry(int x, int y, int z) {
        Column column = new Column(x, y);
        TreeSet<Integer> zs = zByColumn.get(column);
        if (zs == null || !zs.remove(z)) {
            throw new IllegalStateException(
                    "water surface index is missing wet cell: "
                            + x + ", " + y + ", " + z);
        }
        if (zs.isEmpty()) {
            zByColumn.remove(column);
        }
    }

    private record Column(int x, int y)
            implements Comparable<Column> {

        @Override
        public int compareTo(Column other) {
            int xOrder = Integer.compare(x, other.x);
            return xOrder != 0
                    ? xOrder
                    : Integer.compare(y, other.y);
        }
    }
}
