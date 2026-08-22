package io.github.evoforge.visualizer.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.terrain.TerrainSurfaceConsumer;
import io.github.evoforge.simulation.world.terrain.TerrainSurfaceLookup;
import io.github.evoforge.simulation.world.liquid.water.WaterSurfaceConsumer;
import io.github.evoforge.simulation.world.liquid.water.WaterSurfaceLookup;
import io.github.evoforge.simulation.world.space.measurement.CellVolume;
import io.github.evoforge.simulation.world.geometry.FullShape;
import io.github.evoforge.simulation.world.geometry.GeometryLookup;

final class WaterOpticalDepthResolverTest {

    @Test
    void contiguousWaterColumnAddsVerticalDepth() {
        Fixture fixture = new Fixture()
                .water(0, 0, 0, CellVolume.FULL)
                .water(0, 0, 1, CellVolume.FULL / 2);

        assertEquals(
                CellVolume.FULL + CellVolume.FULL / 2,
                fixture.resolver().visibleDepth(0, 0));
    }

    @Test
    void deepColumnStopsAtOpticalCap() {
        Fixture fixture = new Fixture();
        for (int z = 0; z <= 7; z++) {
            fixture.water(0, 0, z, CellVolume.FULL);
        }

        assertEquals(
                WaterOpticalDepthResolver.MAX_OPTICAL_DEPTH,
                fixture.resolver().visibleDepth(0, 0));
    }

    @Test
    void waterBelowHighestTerrainIsHiddenFromSurfaceProjection() {
        Fixture fixture = new Fixture()
                .terrainTop(0, 0, 2)
                .water(0, 0, 1, CellVolume.FULL);

        assertEquals(0, fixture.resolver().visibleDepth(0, 0));
    }

    @Test
    void sameZWaterInsideSolidTerrainDoesNotLeakThroughSurface() {
        Fixture fixture = new Fixture()
                .terrainTop(0, 0, 0)
                .solid(0, 0, 0)
                .water(0, 0, 0, CellVolume.FULL / 2);

        assertEquals(0, fixture.resolver().visibleDepth(0, 0));
    }

    private record Cell(int x, int y, int z) { }
    private record Column(int x, int y) { }

    private static final class Fixture {
        private final Map<Cell, Integer> water = new HashMap<>();
        private final Map<Column, Integer> waterTop = new HashMap<>();
        private final Map<Column, Integer> terrainTop = new HashMap<>();
        private final Map<Cell, Boolean> solid = new HashMap<>();

        private Fixture water(int x, int y, int z, int amount) {
            water.put(new Cell(x, y, z), amount);
            waterTop.merge(new Column(x, y), z, Math::max);
            return this;
        }

        private Fixture terrainTop(int x, int y, int z) {
            terrainTop.put(new Column(x, y), z);
            return this;
        }

        private Fixture solid(int x, int y, int z) {
            solid.put(new Cell(x, y, z), true);
            return this;
        }

        private WaterOpticalDepthResolver resolver() {
            WaterSurfaceLookup wet = new WaterSurfaceLookup() {
                @Override public boolean hasColumn(int x, int y) {
                    return waterTop.containsKey(new Column(x, y));
                }
                @Override public int topZ(int x, int y) {
                    return waterTop.get(new Column(x, y));
                }
                @Override public int columnCount() { return waterTop.size(); }
                @Override public void forEach(WaterSurfaceConsumer consumer) { }
            };
            TerrainSurfaceLookup terrain = new TerrainSurfaceLookup() {
                @Override public boolean hasColumn(int x, int y) {
                    return terrainTop.containsKey(new Column(x, y));
                }
                @Override public int topZ(int x, int y) {
                    return terrainTop.get(new Column(x, y));
                }
                @Override public int columnCount() { return terrainTop.size(); }
                @Override public void forEach(TerrainSurfaceConsumer consumer) { }
            };
            GeometryLookup geometry = (x, y, z) ->
                    solid.containsKey(new Cell(x, y, z)) ? FullShape.INSTANCE : null;
            return new WaterOpticalDepthResolver(
                    (x, y, z) -> water.getOrDefault(new Cell(x, y, z), 0),
                    wet,
                    terrain,
                    geometry);
        }
    }
}
