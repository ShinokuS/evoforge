package io.github.evoforge.simulation.world.terrain;

import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Mutation-maintained derived index of occupied terrain Z values per XY column. */
final class TerrainSurfaceIndex {

    private final TreeMap<Column, TreeSet<Integer>> zByColumn =
            new TreeMap<>();
    private final TerrainSurfaceLookup lookup =
            new TerrainSurfaceLookup() {
                @Override
                public boolean hasColumn(int x, int y) {
                    return zByColumn.containsKey(new Column(x, y));
                }

                @Override
                public int topZ(int x, int y) {
                    TreeSet<Integer> zs = zByColumn.get(new Column(x, y));
                    if (zs == null || zs.isEmpty()) {
                        throw new IllegalArgumentException(
                                "terrain column is empty: " + x + ", " + y);
                    }
                    return zs.last();
                }

                @Override
                public int columnCount() {
                    return zByColumn.size();
                }

                @Override
                public void forEach(TerrainSurfaceConsumer consumer) {
                    if (consumer == null) {
                        throw new IllegalArgumentException(
                                "consumer must not be null");
                    }

                    for (Map.Entry<Column, TreeSet<Integer>> entry
                            : zByColumn.entrySet()) {

                        Column column = entry.getKey();
                        consumer.accept(
                                column.x(),
                                column.y(),
                                entry.getValue().last());
                    }
                }
            };

    TerrainSurfaceLookup lookup() {
        return lookup;
    }

    void add(int x, int y, int z) {
        boolean added = zByColumn
                .computeIfAbsent(
                        new Column(x, y),
                        ignored -> new TreeSet<>())
                .add(z);
        if (!added) {
            throw new IllegalStateException(
                    "terrain surface index already contains cell: "
                            + x + ", " + y + ", " + z);
        }
    }

    void remove(int x, int y, int z) {
        Column column = new Column(x, y);
        TreeSet<Integer> zs = zByColumn.get(column);
        if (zs == null || !zs.remove(z)) {
            throw new IllegalStateException(
                    "terrain surface index is missing cell: "
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
