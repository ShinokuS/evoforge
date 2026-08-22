package io.github.evoforge.simulation.world.liquid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.liquid.storage.SparseLiquidStorage;
import io.github.evoforge.simulation.world.space.measurement.CellVolume;
import io.github.evoforge.simulation.world.geometry.FullShape;
import io.github.evoforge.simulation.world.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.geometry.Shape;
import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** Deterministic generated invariants for the generic free-liquid solver. */
final class LiquidFlowPropertyTest {
    private static final LiquidTypeId LIQUID = LiquidTypeId.of("property-liquid");
    private static final LiquidTransportLookup REFERENCE_TRANSPORT =
            type -> LiquidTransportProperties.reference();
    private static final long SEED = 0x4C4951554944L;
    private static final int GENERATED_CASES = 32;
    private static final int HALF = 2;
    private static final int UPDATE_BUDGET = 512;

    @Test
    void generatedOpenPoolsConserveMassStayBoundedAndBecomeIdempotentlyDormant() {
        SplittableRandom random = new SplittableRandom(SEED);
        for (int sample = 0; sample < GENERATED_CASES; sample++) {
            Fixture fixture = squareFixture();
            for (int x = -HALF; x <= HALF; x++) {
                for (int y = -HALF; y <= HALF; y++) {
                    int amount = random.nextInt(CellVolume.FULL + 1);
                    fixture.liquids.addAtMost(LIQUID, x, y, 0, amount);
                }
            }
            long expectedMass = total(fixture);

            int updates = 0;
            while (fixture.flow.activeCellCount() > 0 && updates < UPDATE_BUDGET) {
                fixture.flow.update();
                assertEquals(expectedMass, total(fixture), context(sample, updates) + " mass");
                assertAllAmountsBounded(fixture, sample, updates);
                updates++;
            }

            assertEquals(0, fixture.flow.activeCellCount(), context(sample, updates) + " dormancy");
            int[][] stable = snapshot(fixture);
            assertEquals(0, fixture.flow.update(), context(sample, updates) + " dormant transfer");
            assertSnapshotEquals(stable, fixture, sample, updates);
        }
    }

    @Test
    void identicalGeneratedInitialStatesProduceIdenticalFlowTraces() {
        SplittableRandom random = new SplittableRandom(SEED ^ 0x445445524D494EL);
        for (int sample = 0; sample < GENERATED_CASES; sample++) {
            int[][] initial = new int[HALF * 2 + 1][HALF * 2 + 1];
            for (int x = 0; x < initial.length; x++) {
                for (int y = 0; y < initial[x].length; y++) {
                    initial[x][y] = random.nextInt(CellVolume.FULL + 1);
                }
            }
            Fixture first = squareFixture();
            Fixture second = squareFixture();
            seed(first, initial);
            seed(second, initial);

            for (int update = 0; update < UPDATE_BUDGET; update++) {
                assertEquals(
                        first.flow.activeCellCount(),
                        second.flow.activeCellCount(),
                        context(sample, update) + " active frontier");
                long firstTransferred = first.flow.update();
                long secondTransferred = second.flow.update();
                assertEquals(firstTransferred, secondTransferred, context(sample, update) + " transfer");
                assertSameState(first, second, sample, update);
                if (first.flow.activeCellCount() == 0 && second.flow.activeCellCount() == 0) break;
            }
            assertEquals(0, first.flow.activeCellCount(), context(sample, UPDATE_BUDGET) + " first dormancy");
            assertEquals(0, second.flow.activeCellCount(), context(sample, UPDATE_BUDGET) + " second dormancy");
        }
    }

    @Test
    void mirroredLineStatesRemainMirroredThroughEveryUpdate() {
        SplittableRandom random = new SplittableRandom(SEED ^ 0x4D4952524F52L);
        for (int sample = 0; sample < GENERATED_CASES; sample++) {
            Fixture first = lineFixture();
            Fixture mirrored = lineFixture();
            for (int x = -HALF; x <= HALF; x++) {
                int amount = random.nextInt(CellVolume.FULL + 1);
                first.liquids.addAtMost(LIQUID, x, 0, 0, amount);
                mirrored.liquids.addAtMost(LIQUID, -x, 0, 0, amount);
            }

            for (int update = 0; update < UPDATE_BUDGET; update++) {
                first.flow.update();
                mirrored.flow.update();
                for (int x = -HALF; x <= HALF; x++) {
                    assertEquals(
                            amount(first, x, 0),
                            amount(mirrored, -x, 0),
                            context(sample, update) + " mirror x=" + x);
                }
                if (first.flow.activeCellCount() == 0 && mirrored.flow.activeCellCount() == 0) break;
            }
            assertEquals(0, first.flow.activeCellCount(), context(sample, UPDATE_BUDGET) + " first dormancy");
            assertEquals(0, mirrored.flow.activeCellCount(), context(sample, UPDATE_BUDGET) + " mirror dormancy");
        }
    }

    private static Fixture squareFixture() {
        TestGeometry geometry = new TestGeometry();
        for (int x = -HALF; x <= HALF; x++) {
            for (int y = -HALF; y <= HALF; y++) geometry.open(x, y, 0);
        }
        return fixture(geometry);
    }

    private static Fixture lineFixture() {
        TestGeometry geometry = new TestGeometry();
        for (int x = -HALF; x <= HALF; x++) geometry.open(x, 0, 0);
        return fixture(geometry);
    }

    private static Fixture fixture(GeometryLookup geometry) {
        LiquidSystem liquids = new LiquidSystem(new SparseLiquidStorage(), geometry);
        return new Fixture(liquids, new LiquidFlowSystem(liquids, geometry, REFERENCE_TRANSPORT));
    }

    private static void seed(Fixture fixture, int[][] initial) {
        for (int x = 0; x < initial.length; x++) {
            for (int y = 0; y < initial[x].length; y++) {
                fixture.liquids.addAtMost(LIQUID, x - HALF, y - HALF, 0, initial[x][y]);
            }
        }
    }

    private static long total(Fixture fixture) {
        long total = 0L;
        for (int x = -HALF; x <= HALF; x++) {
            for (int y = -HALF; y <= HALF; y++) total += amount(fixture, x, y);
        }
        return total;
    }

    private static void assertAllAmountsBounded(Fixture fixture, int sample, int update) {
        for (int x = -HALF; x <= HALF; x++) {
            for (int y = -HALF; y <= HALF; y++) {
                int amount = amount(fixture, x, y);
                assertTrue(
                        amount >= CellVolume.EMPTY && amount <= CellVolume.FULL,
                        context(sample, update) + " bounds at " + x + "," + y + ": " + amount);
            }
        }
    }

    private static int[][] snapshot(Fixture fixture) {
        int size = HALF * 2 + 1;
        int[][] result = new int[size][size];
        for (int x = -HALF; x <= HALF; x++) {
            for (int y = -HALF; y <= HALF; y++) result[x + HALF][y + HALF] = amount(fixture, x, y);
        }
        return result;
    }

    private static void assertSnapshotEquals(
            int[][] expected,
            Fixture fixture,
            int sample,
            int update) {
        for (int x = -HALF; x <= HALF; x++) {
            for (int y = -HALF; y <= HALF; y++) {
                assertEquals(
                        expected[x + HALF][y + HALF],
                        amount(fixture, x, y),
                        context(sample, update) + " idempotence at " + x + "," + y);
            }
        }
    }

    private static void assertSameState(Fixture first, Fixture second, int sample, int update) {
        for (int x = -HALF; x <= HALF; x++) {
            for (int y = -HALF; y <= HALF; y++) {
                assertEquals(
                        amount(first, x, y),
                        amount(second, x, y),
                        context(sample, update) + " state at " + x + "," + y);
            }
        }
    }

    private static int amount(Fixture fixture, int x, int y) {
        return fixture.liquids.lookup().amountOf(LIQUID, x, y, 0);
    }

    private static String context(int sample, int update) {
        return "seed=" + SEED + ", sample=" + sample + ", update=" + update;
    }

    private record Fixture(LiquidSystem liquids, LiquidFlowSystem flow) { }

    private record Cell(int x, int y, int z) { }

    private static final class TestGeometry implements GeometryLookup {
        private final Set<Cell> open = new HashSet<>();

        private TestGeometry open(int x, int y, int z) {
            open.add(new Cell(x, y, z));
            return this;
        }

        @Override
        public Shape find(int x, int y, int z) {
            return open.contains(new Cell(x, y, z)) ? null : FullShape.INSTANCE;
        }
    }
}
