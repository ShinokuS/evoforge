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
    void arbitraryLiquidUsesTheSharedHydraulicSolverAndPreservesIdentity() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0)
                .open(1, 0, 0);
        LiquidSystem liquids = liquids(geometry);
        LiquidFlowSystem flow = flow(liquids, geometry, referenceTransport());
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
    void higherKinematicViscosityMovesLessVolumeUnderIdenticalHydraulicConditions() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0)
                .open(1, 0, 0);

        LiquidSystem referenceLiquids = liquids(geometry);
        LiquidFlowSystem referenceFlow = flow(
                referenceLiquids,
                geometry,
                type -> LiquidTransportProperties.reference());
        referenceLiquids.addAtMost(WINE, 0, 0, 0, 400_000);

        LiquidSystem viscousLiquids = liquids(geometry);
        LiquidFlowSystem viscousFlow = flow(
                viscousLiquids,
                geometry,
                type -> LiquidTransportProperties.ofKinematicViscosity(
                        LiquidTransportProperties.REFERENCE_KINEMATIC_VISCOSITY * 4L));
        viscousLiquids.addAtMost(BLOOD, 0, 0, 0, 400_000);

        referenceFlow.update();
        viscousFlow.update();

        int referenceMoved = referenceLiquids.lookup().amount(1, 0, 0);
        int viscousMoved = viscousLiquids.lookup().amount(1, 0, 0);
        assertTrue(referenceMoved > viscousMoved);
        assertTrue(viscousMoved > 0);
    }

    @Test
    void separatedLiquidTypesAdvanceInOneSharedSolveWithoutCrossContamination() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0)
                .open(1, 0, 0)
                .open(10, 0, 0)
                .open(11, 0, 0);
        LiquidSystem liquids = liquids(geometry);
        LiquidFlowSystem flow = flow(liquids, geometry, referenceTransport());
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
        LiquidFlowSystem flow = flow(liquids, geometry, referenceTransport());
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
        LiquidFlowSystem flow = flow(liquids, geometry, referenceTransport());
        liquids.addAtMost(WINE, -1, 0, 0, 400_000);
        liquids.addAtMost(BLOOD, 1, 0, 0, 400_000);

        assertEquals(0L, flow.update());
        assertNull(liquids.lookup().typeAt(0, 0, 0));
        assertEquals(400_000, liquids.lookup().amount(-1, 0, 0));
        assertEquals(400_000, liquids.lookup().amount(1, 0, 0));
    }

    @Test
    void symmetricSplitPublishesNoArtificialDirectionAtTheSource() {
        TestGeometry geometry = new TestGeometry()
                .open(-1, 0, 0)
                .open(0, 0, 0)
                .open(1, 0, 0);
        LiquidSystem liquids = liquids(geometry);
        LiquidFlowSystem flow = flow(liquids, geometry, referenceTransport());
        liquids.addAtMost(WINE, 0, 0, 0, 400_000);

        assertTrue(flow.update() > 0L);

        assertNull(flow.flowLookup().find(0, 0, 0),
                "equal opposite transfers must cancel instead of choosing one presentation direction");
        LiquidFlowSample west = flow.flowLookup().find(-1, 0, 0);
        LiquidFlowSample east = flow.flowLookup().find(1, 0, 0);
        assertEquals(-1, west.dx());
        assertEquals(1, east.dx());
        assertEquals(west.amount(), east.amount());
    }

    @Test
    void materialSurfaceRetentionBlocksHorizontalRunoffBelowItsCapacity() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0)
                .open(1, 0, 0);
        LiquidSystem liquids = liquids(geometry);
        LiquidFlowSystem flow = new LiquidFlowSystem(
                liquids,
                geometry,
                (x, y, z) -> x == 0 ? 20_000 : 0,
                referenceTransport());
        liquids.addAtMost(WINE, 0, 0, 0, 15_000);

        assertEquals(0L, flow.update());
        assertEquals(15_000, liquids.lookup().amount(0, 0, 0));
        assertEquals(0, liquids.lookup().amount(1, 0, 0));
    }

    private static LiquidFlowSystem flow(
            LiquidSystem liquids,
            GeometryLookup geometry,
            LiquidTransportLookup transport) {
        return new LiquidFlowSystem(liquids, geometry, transport);
    }

    private static LiquidTransportLookup referenceTransport() {
        return type -> LiquidTransportProperties.reference();
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
