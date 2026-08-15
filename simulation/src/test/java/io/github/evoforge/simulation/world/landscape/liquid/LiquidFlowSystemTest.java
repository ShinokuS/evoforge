package io.github.evoforge.simulation.world.landscape.liquid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.liquid.storage.SparseLiquidStorage;
import io.github.evoforge.simulation.world.mechanics.geometry.FullShape;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;

final class LiquidFlowSystemTest {

    private static final LiquidTypeId WINE = LiquidTypeId.of("wine");
    private static final LiquidTypeId BLOOD = LiquidTypeId.of("blood");

    @Test
    void nonWaterLiquidUsesTheSharedHydraulicSolverAndPreservesIdentity() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0)
                .open(1, 0, 0);
        LiquidSystem liquids = liquids(geometry);
        LiquidFlowSystem flow = new LiquidFlowSystem(liquids, geometry);
        liquids.addAtMost(WINE, 0, 0, 0, 400_000);

        long moved = flow.update();

        assertTrue(moved > 0L);
        assertEquals(WINE, liquids.lookup().typeAt(1, 0, 0));
        assertTrue(liquids.lookup().amountOf(WINE, 1, 0, 0) > 0);
        assertEquals(
                400_000,
                liquids.lookup().amount(0, 0, 0)
                        + liquids.lookup().amount(1, 0, 0));
        LiquidFlowSample sample = flow.flowLookup().find(1, 0, 0);
        assertEquals(WINE, sample.type());
    }

    @Test
    void separatedLiquidTypesAdvanceInOneSharedSolveWithoutCrossContamination() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0)
                .open(1, 0, 0)
                .open(10, 0, 0)
                .open(11, 0, 0);
        LiquidSystem liquids = liquids(geometry);
        LiquidFlowSystem flow = new LiquidFlowSystem(liquids, geometry);
        liquids.addAtMost(WINE, 0, 0, 0, 400_000);
        liquids.addAtMost(BLOOD, 10, 0, 0, 600_000);

        assertTrue(flow.update() > 0L);

        assertEquals(WINE, liquids.lookup().typeAt(1, 0, 0));
        assertEquals(BLOOD, liquids.lookup().typeAt(11, 0, 0));
        assertEquals(
                400_000,
                liquids.lookup().amountOf(WINE, 0, 0, 0)
                        + liquids.lookup().amountOf(WINE, 1, 0, 0));
        assertEquals(
                600_000,
                liquids.lookup().amountOf(BLOOD, 10, 0, 0)
                        + liquids.lookup().amountOf(BLOOD, 11, 0, 0));
        assertEquals(0, liquids.lookup().amountOf(BLOOD, 1, 0, 0));
        assertEquals(0, liquids.lookup().amountOf(WINE, 11, 0, 0));
    }

    @Test
    void unlikeOccupiedLiquidsMeetAtExplicitNoMixBoundary() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0)
                .open(1, 0, 0);
        LiquidSystem liquids = liquids(geometry);
        LiquidFlowSystem flow = new LiquidFlowSystem(liquids, geometry);
        liquids.addAtMost(WINE, 0, 0, 0, 400_000);
        liquids.addAtMost(BLOOD, 1, 0, 0, 100_000);

        assertEquals(0L, flow.update());
        assertEquals(WINE, liquids.lookup().typeAt(0, 0, 0));
        assertEquals(BLOOD, liquids.lookup().typeAt(1, 0, 0));
        assertEquals(400_000, liquids.lookup().amount(0, 0, 0));
        assertEquals(100_000, liquids.lookup().amount(1, 0, 0));
    }

    @Test
    void simultaneousUnlikeInflowsDoNotUseOrderingAsAccidentalMixingRule() {
        TestGeometry geometry = new TestGeometry()
                .open(-1, 0, 0)
                .open(0, 0, 0)
                .open(1, 0, 0);
        LiquidSystem liquids = liquids(geometry);
        LiquidFlowSystem flow = new LiquidFlowSystem(liquids, geometry);
        liquids.addAtMost(WINE, -1, 0, 0, 400_000);
        liquids.addAtMost(BLOOD, 1, 0, 0, 400_000);

        assertEquals(0L, flow.update());
        assertNull(liquids.lookup().typeAt(0, 0, 0));
        assertEquals(400_000, liquids.lookup().amount(-1, 0, 0));
        assertEquals(400_000, liquids.lookup().amount(1, 0, 0));
    }

    @Test
    void retentionCapabilityCanVaryByLiquidType() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0)
                .open(1, 0, 0);
        LiquidSystem liquids = liquids(geometry);
        LiquidFlowSystem flow = new LiquidFlowSystem(
                liquids,
                geometry,
                (type, x, y, z) -> WINE.equals(type) ? 20_000 : 0);
        liquids.addAtMost(WINE, 0, 0, 0, 15_000);

        assertEquals(0L, flow.update());
        assertEquals(15_000, liquids.lookup().amount(0, 0, 0));
        assertEquals(0, liquids.lookup().amount(1, 0, 0));
    }

    private static LiquidSystem liquids(GeometryLookup geometry) {
        return new LiquidSystem(new SparseLiquidStorage(), geometry);
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
