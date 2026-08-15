package io.github.evoforge.visualizer.visual;

import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainSurfaceLookup;
import io.github.evoforge.simulation.world.landscape.water.WaterLookup;
import io.github.evoforge.simulation.world.landscape.water.WaterSurfaceLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.CellSpace;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;

/**
 * Presentation-only Water depth derived from authoritative XYZ quantity + geometry.
 * Work is capped once the surface is already optically deep, so deep lakes do not
 * make rendering cost proportional to their full vertical extent.
 */
public final class WaterOpticalDepthResolver {

    public static final int MAX_OPTICAL_DEPTH = CellVolume.FULL * 3;

    private final WaterLookup water;
    private final WaterSurfaceLookup waterSurfaces;
    private final TerrainSurfaceLookup terrainSurfaces;
    private final GeometryLookup geometry;

    public WaterOpticalDepthResolver(SimulationView view) {
        this(
                requireView(view).water(),
                view.waterSurfaces(),
                view.terrainSurfaces(),
                view.geometry());
    }

    public WaterOpticalDepthResolver(
            WaterLookup water,
            WaterSurfaceLookup waterSurfaces,
            TerrainSurfaceLookup terrainSurfaces,
            GeometryLookup geometry) {
        if (water == null || waterSurfaces == null || terrainSurfaces == null || geometry == null) {
            throw new IllegalArgumentException("Water optical depth dependencies must not be null");
        }
        this.water = water;
        this.waterSurfaces = waterSurfaces;
        this.terrainSurfaces = terrainSurfaces;
        this.geometry = geometry;
    }

    /** Returns normalized vertical depth, capped at three full cells, or zero when hidden from surface view. */
    public int visibleDepth(int x, int y) {
        if (!waterSurfaces.hasColumn(x, y)) return 0;

        int topWaterZ = waterSurfaces.topZ(x, y);
        if (terrainSurfaces.hasColumn(x, y)) {
            int terrainZ = terrainSurfaces.topZ(x, y);
            if (topWaterZ < terrainZ) return 0;
            if (topWaterZ == terrainZ
                    && CellSpace.capacity(geometry.find(x, y, terrainZ)) == 0) {
                return 0;
            }
        }

        long depth = 0L;
        int z = topWaterZ;
        while (depth < MAX_OPTICAL_DEPTH) {
            int amount = water.amount(x, y, z);
            if (amount <= 0) break;

            Shape shape = geometry.find(x, y, z);
            int capacity = CellSpace.capacity(shape);
            if (capacity <= 0) break;

            int localHeight = CellSpace.surfaceHeight(shape, amount);
            depth += Math.max(0, localHeight);
            if (z == Integer.MIN_VALUE) break;
            z--;
        }

        return (int) Math.min(MAX_OPTICAL_DEPTH, depth);
    }

    private static SimulationView requireView(SimulationView view) {
        if (view == null) throw new IllegalArgumentException("view must not be null");
        return view;
    }
}
