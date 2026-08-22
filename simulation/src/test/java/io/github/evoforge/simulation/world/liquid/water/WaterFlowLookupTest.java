package io.github.evoforge.simulation.world.liquid.water;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.liquid.LiquidFlowSystem;
import io.github.evoforge.simulation.world.liquid.LiquidSystem;
import io.github.evoforge.simulation.world.liquid.LiquidTransportProperties;
import io.github.evoforge.simulation.world.liquid.storage.SparseLiquidStorage;
import io.github.evoforge.simulation.world.geometry.FullShape;
import io.github.evoforge.simulation.world.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.geometry.Shape;

final class WaterFlowLookupTest {

    @Test
    void lookupReportsActualTransferThenClearsWhenPoolSettles() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0)
                .open(1, 0, 0);
        Fixture fixture = fixture(geometry);

        fixture.water.addAtMost(0, 0, 0, 100_000);
        fixture.flow.update();

        WaterFlowSample source = fixture.waterFlow.find(0, 0, 0);
        WaterFlowSample destination = fixture.waterFlow.find(1, 0, 0);
        assertNotNull(source);
        assertNotNull(destination);
        assertEquals(1, source.dx());
        assertEquals(0, source.dy());
        assertEquals(0, source.dz());
        assertEquals(source, destination);

        for (int step = 0; step < 64 && fixture.flow.activeCellCount() > 0; step++) {
            fixture.flow.update();
        }

        assertEquals(0, fixture.flow.activeCellCount());
        assertNull(fixture.waterFlow.find(0, 0, 0));
        assertNull(fixture.waterFlow.find(1, 0, 0));
    }

    @Test
    void verticalSampleReportsDownwardActualTransfer() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 1)
                .open(0, 0, 0);
        Fixture fixture = fixture(geometry);

        fixture.water.addAtMost(0, 0, 1, 20_000);
        fixture.flow.update();

        WaterFlowSample sample = fixture.waterFlow.find(0, 0, 1);
        assertNotNull(sample);
        assertEquals(0, sample.dx());
        assertEquals(0, sample.dy());
        assertEquals(-1, sample.dz());
    }

    private static Fixture fixture(GeometryLookup geometry) {
        LiquidSystem liquids = new LiquidSystem(new SparseLiquidStorage(), geometry);
        WaterSystem water = new WaterSystem(liquids);
        LiquidFlowSystem flow = new LiquidFlowSystem(
                liquids,
                geometry,
                type -> LiquidTransportProperties.reference());
        return new Fixture(
                water,
                flow,
                WaterFlowLookup.from(flow.flowLookup()));
    }

    private record Fixture(
            WaterSystem water,
            LiquidFlowSystem flow,
            WaterFlowLookup waterFlow) {
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
