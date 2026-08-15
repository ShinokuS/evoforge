package io.github.evoforge.simulation.world.landscape.water;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.water.storage.SparseWaterStorage;
import io.github.evoforge.simulation.world.mechanics.geometry.FullShape;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;

final class WaterFlowLookupTest {

    @Test
    void lookupReportsActualTransferThenClearsWhenPoolSettles() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0)
                .open(1, 0, 0);
        WaterSystem water = new WaterSystem(
                new SparseWaterStorage(),
                geometry);
        WaterFlowSystem flow = new WaterFlowSystem(water, geometry);

        water.addAtMost(0, 0, 0, 100_000);
        flow.update();

        WaterFlowSample source = flow.flowLookup().find(0, 0, 0);
        WaterFlowSample destination = flow.flowLookup().find(1, 0, 0);
        assertNotNull(source);
        assertNotNull(destination);
        assertEquals(1, source.dx());
        assertEquals(0, source.dy());
        assertEquals(0, source.dz());
        assertEquals(source, destination);

        for (int step = 0; step < 64 && flow.activeCellCount() > 0; step++) {
            flow.update();
        }

        assertEquals(0, flow.activeCellCount());
        assertNull(flow.flowLookup().find(0, 0, 0));
        assertNull(flow.flowLookup().find(1, 0, 0));
    }

    @Test
    void verticalSampleReportsDownwardActualTransfer() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 1)
                .open(0, 0, 0);
        WaterSystem water = new WaterSystem(
                new SparseWaterStorage(),
                geometry);
        WaterFlowSystem flow = new WaterFlowSystem(water, geometry);

        water.addAtMost(0, 0, 1, 20_000);
        flow.update();

        WaterFlowSample sample = flow.flowLookup().find(0, 0, 1);
        assertNotNull(sample);
        assertEquals(0, sample.dx());
        assertEquals(0, sample.dy());
        assertEquals(-1, sample.dz());
    }

    private record Cell(int x, int y, int z) {
    }

    private static final class TestGeometry implements GeometryLookup {
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
