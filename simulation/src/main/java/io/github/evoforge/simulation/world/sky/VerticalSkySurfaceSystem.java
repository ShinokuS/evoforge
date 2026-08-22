package io.github.evoforge.simulation.world.sky;

import java.util.TreeSet;

import io.github.evoforge.simulation.world.terrain.TerrainSurfaceLookup;
import io.github.evoforge.simulation.world.liquid.water.WaterSurfaceLookup;

/**
 * Resolves one vertical sky-exposed surface for an XY column from cached Terrain and
 * Water surfaces. Water strictly above terrain wins; a shared anchor Z remains
 * terrain-first because the coarse cell model does not resolve exposed sub-cell area.
 */
public final class VerticalSkySurfaceSystem implements SkySurfaceLookup {

    private final TerrainSurfaceLookup terrain;
    private final WaterSurfaceLookup water;

    public VerticalSkySurfaceSystem(
            TerrainSurfaceLookup terrain,
            WaterSurfaceLookup water) {

        if (terrain == null || water == null) {
            throw new IllegalArgumentException(
                    "sky surface dependencies must not be null");
        }
        this.terrain = terrain;
        this.water = water;
    }

    @Override
    public SkySurface find(int x, int y) {
        boolean hasTerrain = terrain.hasColumn(x, y);
        boolean hasWater = water.hasColumn(x, y);

        if (!hasTerrain && !hasWater) {
            return null;
        }
        if (!hasTerrain) {
            return new SkySurface(
                    x,
                    y,
                    water.topZ(x, y),
                    SkySurface.Kind.WATER);
        }
        if (!hasWater) {
            return new SkySurface(
                    x,
                    y,
                    terrain.topZ(x, y),
                    SkySurface.Kind.TERRAIN);
        }

        int terrainZ = terrain.topZ(x, y);
        int waterZ = water.topZ(x, y);
        return waterZ > terrainZ
                ? new SkySurface(
                        x,
                        y,
                        waterZ,
                        SkySurface.Kind.WATER)
                : new SkySurface(
                        x,
                        y,
                        terrainZ,
                        SkySurface.Kind.TERRAIN);
    }

    @Override
    public void forEach(SkySurfaceConsumer consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException(
                    "consumer must not be null");
        }

        TreeSet<Column> columns = new TreeSet<>();
        terrain.forEach((x, y, z) -> columns.add(new Column(x, y)));
        water.forEach((x, y, z) -> columns.add(new Column(x, y)));

        for (Column column : columns) {
            SkySurface surface = find(column.x(), column.y());
            if (surface != null) {
                consumer.accept(surface);
            }
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
