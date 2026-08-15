package io.github.evoforge.simulation.world.landscape.liquid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.liquid.storage.SparseLiquidStorage;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;
import io.github.evoforge.simulation.world.mechanics.geometry.FullShape;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;

/** Regression contract for the accepted deterministic hydraulic solver. */
final class LiquidFlowRegressionTest {

    private static final LiquidTypeId LIQUID = LiquidTypeId.of("test-liquid");
    private static final LiquidTransportLookup REFERENCE_TRANSPORT =
            type -> LiquidTransportProperties.reference();

    @Test
    void horizontalHeadDifferenceProducesFiniteConservedFlux() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0)
                .open(1, 0, 0);
        Fixture fixture = fixture(geometry);
        fixture.liquids.addAtMost(LIQUID, 0, 0, 0, 800_000);

        assertEquals(200_000, fixture.flow.update());
        assertEquals(600_000, amount(fixture, 0, 0, 0));
        assertEquals(200_000, amount(fixture, 1, 0, 0));
        assertEquals(800_000, total(fixture, cell(0, 0, 0), cell(1, 0, 0)));
    }

    @Test
    void verticalFlowUsesSameHeadLawAndCannotCrossTwoCellsInOneUpdate() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0)
                .open(0, 0, 1)
                .open(0, 0, 2);
        Fixture fixture = fixture(geometry);
        fixture.liquids.addAtMost(LIQUID, 0, 0, 2, 400_000);

        assertTrue(fixture.flow.update() > 0);
        assertTrue(amount(fixture, 0, 0, 1) > 0);
        assertEquals(CellVolume.EMPTY, amount(fixture, 0, 0, 0));
        assertEquals(400_000, total(
                fixture,
                cell(0, 0, 0),
                cell(0, 0, 1),
                cell(0, 0, 2)));

        assertTrue(fixture.flow.update() > 0);
        assertTrue(amount(fixture, 0, 0, 0) > 0);
        assertEquals(400_000, total(
                fixture,
                cell(0, 0, 0),
                cell(0, 0, 1),
                cell(0, 0, 2)));
    }

    @Test
    void multipleEqualExitsAreBoundedAgainstOneSnapshotSource() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0)
                .open(-1, 0, 0)
                .open(1, 0, 0)
                .open(0, -1, 0)
                .open(0, 1, 0);
        Fixture fixture = fixture(geometry);
        fixture.liquids.addAtMost(LIQUID, 0, 0, 0, CellVolume.FULL);

        assertEquals(CellVolume.FULL / 2, fixture.flow.update());
        assertEquals(CellVolume.FULL / 2, amount(fixture, 0, 0, 0));
        assertEquals(125_000, amount(fixture, -1, 0, 0));
        assertEquals(125_000, amount(fixture, 1, 0, 0));
        assertEquals(125_000, amount(fixture, 0, -1, 0));
        assertEquals(125_000, amount(fixture, 0, 1, 0));
        assertEquals(CellVolume.FULL, total(
                fixture,
                cell(0, 0, 0),
                cell(-1, 0, 0),
                cell(1, 0, 0),
                cell(0, -1, 0),
                cell(0, 1, 0)));
    }

    @Test
    void rampFlowsThroughLowFaceButNotThroughHighFace() {
        TestGeometry geometry = new TestGeometry()
                .shape(0, 0, 0, RampShape.POSITIVE_X)
                .open(-1, 0, 0)
                .open(1, 0, 0);
        Fixture fixture = fixture(geometry);
        fixture.liquids.addAtMost(LIQUID, 0, 0, 0, 400_000);

        assertTrue(fixture.flow.update() > 0);
        assertTrue(amount(fixture, -1, 0, 0) > 0);
        assertEquals(CellVolume.EMPTY, amount(fixture, 1, 0, 0));
        assertEquals(400_000, total(
                fixture,
                cell(-1, 0, 0),
                cell(0, 0, 0),
                cell(1, 0, 0)));
    }

    @Test
    void equalizedRegionBecomesDormantAndExternalMutationWakesIt() {
        TestGeometry geometry = new TestGeometry().open(0, 0, 0);
        Fixture fixture = fixture(geometry);
        fixture.liquids.addAtMost(LIQUID, 0, 0, 0, 100_000);
        assertEquals(1, fixture.flow.activeCellCount());

        assertEquals(CellVolume.EMPTY, fixture.flow.update());
        assertEquals(0, fixture.flow.activeCellCount());
        assertEquals(CellVolume.EMPTY, fixture.flow.update());

        fixture.liquids.removeAtMost(LIQUID, 0, 0, 0, 1);
        assertEquals(1, fixture.flow.activeCellCount());
        assertEquals(CellVolume.EMPTY, fixture.flow.update());
        assertEquals(0, fixture.flow.activeCellCount());
    }

    @Test
    void geometryWakeCanDisplaceExcessWithoutDeletingMass() {
        TestGeometry geometry = new TestGeometry().open(0, 0, 0);
        Fixture fixture = fixture(geometry);
        fixture.liquids.addAtMost(LIQUID, 0, 0, 0, 600_000);
        assertEquals(CellVolume.EMPTY, fixture.flow.update());
        assertEquals(0, fixture.flow.activeCellCount());

        geometry.shape(0, 0, 0, RampShape.POSITIVE_X)
                .open(-1, 0, 0);
        fixture.flow.activateAt(0, 0, 0);
        runUntilDormant(fixture.flow);

        assertTrue(amount(fixture, 0, 0, 0) <= CellVolume.FULL / 2);
        assertTrue(amount(fixture, -1, 0, 0) > 0);
        assertEquals(600_000, total(
                fixture,
                cell(-1, 0, 0),
                cell(0, 0, 0)));
    }

    @Test
    void mutationOrderDoesNotAffectDeterministicFlowResult() {
        TestGeometry firstGeometry = lineGeometry();
        Fixture first = fixture(firstGeometry);
        first.liquids.addAtMost(LIQUID, -1, 0, 0, 600_000);
        first.liquids.addAtMost(LIQUID, 1, 0, 0, 200_000);

        TestGeometry secondGeometry = lineGeometry();
        Fixture second = fixture(secondGeometry);
        second.liquids.addAtMost(LIQUID, 1, 0, 0, 200_000);
        second.liquids.addAtMost(LIQUID, -1, 0, 0, 600_000);

        for (int update = 0; update < 12; update++) {
            first.flow.update();
            second.flow.update();
        }

        for (int x = -1; x <= 1; x++) {
            assertEquals(
                    amount(first, x, 0, 0),
                    amount(second, x, 0, 0));
        }
        assertEquals(800_000, total(
                first,
                cell(-1, 0, 0),
                cell(0, 0, 0),
                cell(1, 0, 0)));
    }

    @Test
    void stableTwoCellPoolConvergesToIntegerDeadbandAndSleeps() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0)
                .open(1, 0, 0);
        Fixture fixture = fixture(geometry);
        fixture.liquids.addAtMost(LIQUID, 0, 0, 0, 800_000);
        runUntilDormant(fixture.flow);

        int difference = Math.abs(
                amount(fixture, 0, 0, 0)
                        - amount(fixture, 1, 0, 0));
        assertTrue(difference <= 2);
        assertEquals(0, fixture.flow.activeCellCount());
        assertEquals(800_000, total(
                fixture,
                cell(0, 0, 0),
                cell(1, 0, 0)));
    }

    private static Fixture fixture(GeometryLookup geometry) {
        LiquidSystem liquids = new LiquidSystem(new SparseLiquidStorage(), geometry);
        return new Fixture(
                liquids,
                new LiquidFlowSystem(liquids, geometry, REFERENCE_TRANSPORT));
    }

    private static TestGeometry lineGeometry() {
        return new TestGeometry()
                .open(-1, 0, 0)
                .open(0, 0, 0)
                .open(1, 0, 0);
    }

    private static void runUntilDormant(LiquidFlowSystem flow) {
        for (int update = 0;
                update < 64 && flow.activeCellCount() > 0;
                update++) {
            flow.update();
        }
        assertEquals(
                0,
                flow.activeCellCount(),
                "flow did not converge to dormancy within the bounded test window");
    }

    private static int amount(Fixture fixture, int x, int y, int z) {
        return fixture.liquids.lookup().amountOf(LIQUID, x, y, z);
    }

    private static int total(Fixture fixture, Cell... cells) {
        int total = 0;
        for (Cell cell : cells) {
            total += amount(fixture, cell.x, cell.y, cell.z);
        }
        return total;
    }

    private static Cell cell(int x, int y, int z) {
        return new Cell(x, y, z);
    }

    private record Fixture(LiquidSystem liquids, LiquidFlowSystem flow) {
    }

    private record Cell(int x, int y, int z) {
    }

    private static final class TestGeometry implements GeometryLookup {
        private final Set<Cell> open = new HashSet<>();
        private final Map<Cell, Shape> shapes = new HashMap<>();

        private TestGeometry open(int x, int y, int z) {
            Cell cell = cell(x, y, z);
            shapes.remove(cell);
            open.add(cell);
            return this;
        }

        private TestGeometry shape(int x, int y, int z, Shape shape) {
            Cell cell = cell(x, y, z);
            open.remove(cell);
            shapes.put(cell, shape);
            return this;
        }

        @Override
        public Shape find(int x, int y, int z) {
            Cell cell = cell(x, y, z);
            Shape shape = shapes.get(cell);
            if (shape != null) return shape;
            return open.contains(cell) ? null : FullShape.INSTANCE;
        }
    }
}
