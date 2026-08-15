package io.github.evoforge.simulation.world.landscape.water;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.water.storage.SparseWaterStorage;
import io.github.evoforge.simulation.world.mechanics.geometry.FullShape;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;

final class SurfaceWaterStorageFlowTest {

    @Test
    void shallowRetainedFilmDoesNotSpreadHorizontally() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0)
                .open(1, 0, 0);
        WaterSystem water = water(geometry);
        WaterFlowSystem flow = new WaterFlowSystem(
                water,
                geometry,
                (x, y, z) -> 10_000);

        water.addAtMost(0, 0, 0, 8_000);

        assertEquals(0L, flow.update());
        assertEquals(8_000, water.lookup().amount(0, 0, 0));
        assertEquals(0, water.lookup().amount(1, 0, 0));
        assertEquals(0, flow.activeCellCount());
    }

    @Test
    void volumeAboveStorageProducesConservedRunoff() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0)
                .open(1, 0, 0);
        WaterSystem water = water(geometry);
        WaterFlowSystem flow = new WaterFlowSystem(
                water,
                geometry,
                (x, y, z) -> 10_000);

        water.addAtMost(0, 0, 0, 20_000);

        assertTrue(flow.update() > 0L);
        assertTrue(water.lookup().amount(1, 0, 0) > 0);
        assertTrue(water.lookup().amount(0, 0, 0) >= 10_000);
        assertEquals(
                20_000,
                water.lookup().amount(0, 0, 0)
                        + water.lookup().amount(1, 0, 0));
    }

    @Test
    void multipleHorizontalExitsCannotDrainSourceBelowStorage() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0)
                .open(-1, 0, 0)
                .open(1, 0, 0)
                .open(0, -1, 0)
                .open(0, 1, 0);
        WaterSystem water = water(geometry);
        WaterFlowSystem flow = new WaterFlowSystem(
                water,
                geometry,
                (x, y, z) -> 10_000);

        water.addAtMost(0, 0, 0, 12_000);

        assertTrue(flow.update() > 0L);
        assertTrue(
                water.lookup().amount(0, 0, 0) >= 10_000,
                "simultaneous exits must preserve the one shared surface-storage reserve");
        assertEquals(
                12_000,
                water.lookup().amount(0, 0, 0)
                        + water.lookup().amount(-1, 0, 0)
                        + water.lookup().amount(1, 0, 0)
                        + water.lookup().amount(0, -1, 0)
                        + water.lookup().amount(0, 1, 0));
    }

    @Test
    void retainedFilmStillFallsThroughOpenFloor() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 1)
                .open(0, 0, 0);
        WaterSystem water = water(geometry);
        WaterFlowSystem flow = new WaterFlowSystem(
                water,
                geometry,
                (x, y, z) -> 100_000);

        water.addAtMost(0, 0, 1, 5_000);

        assertTrue(flow.update() > 0L);
        assertTrue(water.lookup().amount(0, 0, 0) > 0);
        assertEquals(
                5_000,
                water.lookup().amount(0, 0, 0)
                        + water.lookup().amount(0, 0, 1));
    }

    private static WaterSystem water(GeometryLookup geometry) {
        return new WaterSystem(
                new SparseWaterStorage(),
                geometry);
    }

    private record Cell(int x, int y, int z) {
    }

    private static final class TestGeometry
            implements GeometryLookup {

        private final Set<Cell> open = new HashSet<>();

        private TestGeometry open(int x, int y, int z) {
            open.add(new Cell(x, y, z));
            return this;
        }

        @Override
        public Shape find(int x, int y, int z) {
            return open.contains(new Cell(x, y, z))
                    ? null
                    : FullShape.INSTANCE;
        }
    }
}
