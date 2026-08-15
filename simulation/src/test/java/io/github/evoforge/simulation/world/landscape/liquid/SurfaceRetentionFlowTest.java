package io.github.evoforge.simulation.world.landscape.liquid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.liquid.storage.SparseLiquidStorage;
import io.github.evoforge.simulation.world.mechanics.geometry.FullShape;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;

final class SurfaceRetentionFlowTest {

    private static final LiquidTypeId LIQUID = LiquidTypeId.of("test-liquid");
    private static final LiquidTransportLookup REFERENCE_TRANSPORT =
            type -> LiquidTransportProperties.reference();

    @Test
    void shallowRetainedFilmDoesNotSpreadHorizontally() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0)
                .open(1, 0, 0);
        Fixture fixture = fixture(geometry, (x, y, z) -> 10_000);

        fixture.liquids.addAtMost(LIQUID, 0, 0, 0, 8_000);

        assertEquals(0L, fixture.flow.update());
        assertEquals(8_000, amount(fixture, 0, 0, 0));
        assertEquals(0, amount(fixture, 1, 0, 0));
        assertEquals(0, fixture.flow.activeCellCount());
    }

    @Test
    void volumeAboveRetentionProducesConservedRunoff() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0)
                .open(1, 0, 0);
        Fixture fixture = fixture(geometry, (x, y, z) -> 10_000);

        fixture.liquids.addAtMost(LIQUID, 0, 0, 0, 20_000);

        assertTrue(fixture.flow.update() > 0L);
        assertTrue(amount(fixture, 1, 0, 0) > 0);
        assertTrue(amount(fixture, 0, 0, 0) >= 10_000);
        assertEquals(
                20_000,
                amount(fixture, 0, 0, 0)
                        + amount(fixture, 1, 0, 0));
    }

    @Test
    void multipleHorizontalExitsCannotDrainSourceBelowOneSharedReserve() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0)
                .open(-1, 0, 0)
                .open(1, 0, 0)
                .open(0, -1, 0)
                .open(0, 1, 0);
        Fixture fixture = fixture(geometry, (x, y, z) -> 10_000);

        fixture.liquids.addAtMost(LIQUID, 0, 0, 0, 12_000);

        assertTrue(fixture.flow.update() > 0L);
        assertTrue(
                amount(fixture, 0, 0, 0) >= 10_000,
                "simultaneous exits must preserve the one shared surface-retention reserve");
        assertEquals(
                12_000,
                amount(fixture, 0, 0, 0)
                        + amount(fixture, -1, 0, 0)
                        + amount(fixture, 1, 0, 0)
                        + amount(fixture, 0, -1, 0)
                        + amount(fixture, 0, 1, 0));
    }

    @Test
    void retainedFilmStillFallsThroughOpenFloor() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 1)
                .open(0, 0, 0);
        Fixture fixture = fixture(geometry, (x, y, z) -> 100_000);

        fixture.liquids.addAtMost(LIQUID, 0, 0, 1, 5_000);

        assertTrue(fixture.flow.update() > 0L);
        assertTrue(amount(fixture, 0, 0, 0) > 0);
        assertEquals(
                5_000,
                amount(fixture, 0, 0, 0)
                        + amount(fixture, 0, 0, 1));
    }

    private static Fixture fixture(
            GeometryLookup geometry,
            LiquidSurfaceRetentionLookup retention) {
        LiquidSystem liquids = new LiquidSystem(new SparseLiquidStorage(), geometry);
        return new Fixture(
                liquids,
                new LiquidFlowSystem(
                        liquids,
                        geometry,
                        retention,
                        REFERENCE_TRANSPORT));
    }

    private static int amount(Fixture fixture, int x, int y, int z) {
        return fixture.liquids.lookup().amountOf(LIQUID, x, y, z);
    }

    private record Fixture(LiquidSystem liquids, LiquidFlowSystem flow) {
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
