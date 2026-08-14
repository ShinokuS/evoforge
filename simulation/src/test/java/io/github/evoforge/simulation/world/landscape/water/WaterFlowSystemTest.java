package io.github.evoforge.simulation.world.landscape.water;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.water.storage.SparseWaterStorage;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;
import io.github.evoforge.simulation.world.mechanics.geometry.FullShape;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;

final class WaterFlowSystemTest {

    @Test
    void horizontalHeadDifferenceProducesFiniteConservedFlux() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0)
                .open(1, 0, 0);
        WaterSystem water = water(geometry);
        WaterFlowSystem flow = new WaterFlowSystem(
                water,
                geometry);

        water.addAtMost(0, 0, 0, 800_000);

        assertEquals(
                200_000,
                flow.update());
        assertEquals(
                600_000,
                amount(water, 0, 0, 0));
        assertEquals(
                200_000,
                amount(water, 1, 0, 0));
        assertEquals(
                800_000,
                total(
                        water,
                        cell(0, 0, 0),
                        cell(1, 0, 0)));
    }

    @Test
    void verticalFlowUsesSameHeadLawAndCannotCrossTwoCellsInOneUpdate() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0)
                .open(0, 0, 1)
                .open(0, 0, 2);
        WaterSystem water = water(geometry);
        WaterFlowSystem flow = new WaterFlowSystem(
                water,
                geometry);

        water.addAtMost(0, 0, 2, 400_000);

        assertTrue(flow.update() > 0);
        assertTrue(amount(water, 0, 0, 1) > 0);
        assertEquals(
                CellVolume.EMPTY,
                amount(water, 0, 0, 0));
        assertEquals(
                400_000,
                total(
                        water,
                        cell(0, 0, 0),
                        cell(0, 0, 1),
                        cell(0, 0, 2)));

        assertTrue(flow.update() > 0);
        assertTrue(amount(water, 0, 0, 0) > 0);
        assertEquals(
                400_000,
                total(
                        water,
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
        WaterSystem water = water(geometry);
        WaterFlowSystem flow = new WaterFlowSystem(
                water,
                geometry);

        water.addAtMost(
                0,
                0,
                0,
                CellVolume.FULL);

        assertEquals(
                CellVolume.FULL / 2,
                flow.update());
        assertEquals(
                CellVolume.FULL / 2,
                amount(water, 0, 0, 0));
        assertEquals(125_000, amount(water, -1, 0, 0));
        assertEquals(125_000, amount(water, 1, 0, 0));
        assertEquals(125_000, amount(water, 0, -1, 0));
        assertEquals(125_000, amount(water, 0, 1, 0));
        assertEquals(
                CellVolume.FULL,
                total(
                        water,
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
        WaterSystem water = water(geometry);
        WaterFlowSystem flow = new WaterFlowSystem(
                water,
                geometry);

        water.addAtMost(0, 0, 0, 400_000);

        assertTrue(flow.update() > 0);
        assertTrue(amount(water, -1, 0, 0) > 0);
        assertEquals(
                CellVolume.EMPTY,
                amount(water, 1, 0, 0));
        assertEquals(
                400_000,
                total(
                        water,
                        cell(-1, 0, 0),
                        cell(0, 0, 0),
                        cell(1, 0, 0)));
    }

    @Test
    void equalizedRegionBecomesDormantAndExternalMutationWakesIt() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0);
        WaterSystem water = water(geometry);
        WaterFlowSystem flow = new WaterFlowSystem(
                water,
                geometry);

        water.addAtMost(0, 0, 0, 100_000);
        assertEquals(1, flow.activeCellCount());

        assertEquals(
                CellVolume.EMPTY,
                flow.update());
        assertEquals(0, flow.activeCellCount());
        assertEquals(
                CellVolume.EMPTY,
                flow.update());

        water.removeAtMost(0, 0, 0, 1);
        assertEquals(1, flow.activeCellCount());
        assertEquals(
                CellVolume.EMPTY,
                flow.update());
        assertEquals(0, flow.activeCellCount());
    }

    @Test
    void geometryWakeCanDisplaceExcessWithoutDeletingMass() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0);
        WaterSystem water = water(geometry);
        WaterFlowSystem flow = new WaterFlowSystem(
                water,
                geometry);

        water.addAtMost(0, 0, 0, 600_000);
        assertEquals(
                CellVolume.EMPTY,
                flow.update());
        assertEquals(0, flow.activeCellCount());

        geometry.shape(0, 0, 0, RampShape.POSITIVE_X)
                .open(-1, 0, 0);
        flow.activateAt(0, 0, 0);

        runUntilDormant(flow);

        assertTrue(
                amount(water, 0, 0, 0)
                        <= CellVolume.FULL / 2);
        assertTrue(amount(water, -1, 0, 0) > 0);
        assertEquals(
                600_000,
                total(
                        water,
                        cell(-1, 0, 0),
                        cell(0, 0, 0)));
    }

    @Test
    void mutationOrderDoesNotAffectDeterministicFlowResult() {
        TestGeometry firstGeometry = lineGeometry();
        WaterSystem firstWater = water(firstGeometry);
        WaterFlowSystem firstFlow = new WaterFlowSystem(
                firstWater,
                firstGeometry);

        firstWater.addAtMost(-1, 0, 0, 600_000);
        firstWater.addAtMost(1, 0, 0, 200_000);

        TestGeometry secondGeometry = lineGeometry();
        WaterSystem secondWater = water(secondGeometry);
        WaterFlowSystem secondFlow = new WaterFlowSystem(
                secondWater,
                secondGeometry);

        secondWater.addAtMost(1, 0, 0, 200_000);
        secondWater.addAtMost(-1, 0, 0, 600_000);

        for (int update = 0; update < 12; update++) {
            firstFlow.update();
            secondFlow.update();
        }

        for (int x = -1; x <= 1; x++) {
            assertEquals(
                    amount(firstWater, x, 0, 0),
                    amount(secondWater, x, 0, 0));
        }
        assertEquals(
                800_000,
                total(
                        firstWater,
                        cell(-1, 0, 0),
                        cell(0, 0, 0),
                        cell(1, 0, 0)));
    }

    @Test
    void stableTwoCellPoolConvergesToIntegerDeadbandAndSleeps() {
        TestGeometry geometry = new TestGeometry()
                .open(0, 0, 0)
                .open(1, 0, 0);
        WaterSystem water = water(geometry);
        WaterFlowSystem flow = new WaterFlowSystem(
                water,
                geometry);

        water.addAtMost(0, 0, 0, 800_000);
        runUntilDormant(flow);

        int difference = Math.abs(
                amount(water, 0, 0, 0)
                        - amount(water, 1, 0, 0));

        assertTrue(difference <= 2);
        assertEquals(0, flow.activeCellCount());
        assertEquals(
                800_000,
                total(
                        water,
                        cell(0, 0, 0),
                        cell(1, 0, 0)));
    }

    private static TestGeometry lineGeometry() {
        return new TestGeometry()
                .open(-1, 0, 0)
                .open(0, 0, 0)
                .open(1, 0, 0);
    }

    private static void runUntilDormant(
            WaterFlowSystem flow) {

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

    private static WaterSystem water(
            GeometryLookup geometry) {

        return new WaterSystem(
                new SparseWaterStorage(),
                geometry);
    }

    private static int amount(
            WaterSystem water,
            int x,
            int y,
            int z) {

        return water.lookup().amount(x, y, z);
    }

    private static int total(
            WaterSystem water,
            Cell... cells) {

        int total = 0;
        for (Cell cell : cells) {
            total += amount(
                    water,
                    cell.x,
                    cell.y,
                    cell.z);
        }
        return total;
    }

    private static Cell cell(
            int x,
            int y,
            int z) {

        return new Cell(x, y, z);
    }

    private record Cell(
            int x,
            int y,
            int z) {
    }

    private static final class TestGeometry
            implements GeometryLookup {

        private final Set<Cell> open = new HashSet<>();
        private final Map<Cell, Shape> shapes = new HashMap<>();

        private TestGeometry open(
                int x,
                int y,
                int z) {

            Cell cell = cell(x, y, z);
            shapes.remove(cell);
            open.add(cell);
            return this;
        }

        private TestGeometry shape(
                int x,
                int y,
                int z,
                Shape shape) {

            Cell cell = cell(x, y, z);
            open.remove(cell);
            shapes.put(cell, shape);
            return this;
        }

        @Override
        public Shape find(
                int x,
                int y,
                int z) {

            Cell cell = cell(x, y, z);
            Shape shape = shapes.get(cell);
            if (shape != null) {
                return shape;
            }
            return open.contains(cell)
                    ? null
                    : FullShape.INSTANCE;
        }
    }
}
