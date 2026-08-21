package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class CoastFieldRelaxationAlgorithmTest {

    @Test
    void balancedRelaxationRoundsOnlyTheNearCoastFieldAndLocksWorldEdge() {
        int width = 7;
        int height = 7;
        double[] source = new double[width * height];
        Arrays.fill(source, -4d);

        set(source, width, 2, 2, 0.15d);
        set(source, width, 3, 2, 0.50d);
        set(source, width, 4, 2, 0.15d);
        set(source, width, 2, 3, 0.50d);
        set(source, width, 3, 3, 3.00d);
        set(source, width, 4, 3, 0.50d);
        set(source, width, 2, 4, 0.15d);
        set(source, width, 3, 4, 0.50d);
        set(source, width, 4, 4, 0.15d);

        set(source, width, 1, 2, -1d);
        set(source, width, 2, 1, -1d);
        set(source, width, 5, 2, -1d);
        set(source, width, 4, 1, -1d);
        set(source, width, 1, 4, -1d);
        set(source, width, 2, 5, -1d);
        set(source, width, 5, 4, -1d);
        set(source, width, 4, 5, -1d);
        set(source, width, 0, 3, -0.10d);

        LandmassSilhouetteRecipe.CoastRelaxationPolicy policy =
                LandmassSilhouetteRecipe.balanced().relaxation();
        double[] relaxed = CoastFieldRelaxationAlgorithm.standard().relax(
                source,
                width,
                height,
                1,
                10,
                policy);

        assertTrue(relaxed[index(width, 2, 2)] < source[index(width, 2, 2)],
                "convex raster corner should move toward the surrounding ocean field");
        assertEquals(3d, relaxed[index(width, 3, 3)], 0d,
                "deep land outside the relaxation band must remain exact");
        assertEquals(-4d, relaxed[index(width, 1, 1)], 0d,
                "deep ocean outside the relaxation band must remain exact");
        assertEquals(-0.10d, relaxed[index(width, 0, 3)], 0d,
                "guaranteed external-ocean edge must remain locked even inside the band");
    }

    @Test
    void relaxationIsDeterministicAndEachPassHasAStrictSubCellShiftBound() {
        int width = 5;
        int height = 5;
        double[] source = new double[width * height];
        Arrays.fill(source, -10d);
        set(source, width, 2, 2, 0.10d);

        LandmassSilhouetteRecipe.CoastRelaxationPolicy onePass =
                new LandmassSilhouetteRecipe.CoastRelaxationPolicy(
                        1,
                        200_000,
                        500_000,
                        90_000,
                        35_000,
                        450_000);
        CoastFieldRelaxationAlgorithm algorithm = CoastFieldRelaxationAlgorithm.standard();
        double[] first = algorithm.relax(source, width, height, 1, 10, onePass);
        double[] second = algorithm.relax(source, width, height, 1, 10, onePass);

        assertArrayEquals(first, second, 0d, "coast relaxation must remain deterministic");
        double displacement = StrictMath.abs(first[index(width, 2, 2)] - source[index(width, 2, 2)]);
        assertTrue(displacement <= 0.450_000_1d,
                "one relaxation pass must not displace the signed coast by more than the configured sub-cell bound");
    }

    @Test
    void ownedRelaxationMatchesFormerClonePerPassFormulaBitForBit() {
        int width = 17;
        int height = 13;
        double[] source = irregularField(width, height);

        for (int passCount = 0; passCount <= 4; passCount++) {
            LandmassSilhouetteRecipe.CoastRelaxationPolicy policy = policy(passCount);
            double[] expected = formerClonePerPassFormula(source, width, height, 2, 9, policy);
            double[] actual = WeightedCoastFieldRelaxationAlgorithm.INSTANCE.relaxOwned(
                    source.clone(),
                    width,
                    height,
                    2,
                    9,
                    policy);

            assertRawBitsEqual(expected, actual, "owned relaxation changed pass=" + passCount);
        }
    }

    @Test
    void copySafeRelaxationPreservesCallerStorageAndMatchesFormerFormula() {
        int width = 11;
        int height = 9;
        double[] source = irregularField(width, height);
        double[] before = source.clone();
        LandmassSilhouetteRecipe.CoastRelaxationPolicy policy = policy(2);

        double[] actual = WeightedCoastFieldRelaxationAlgorithm.INSTANCE.relax(
                source,
                width,
                height,
                1,
                7,
                policy);
        double[] expected = formerClonePerPassFormula(before, width, height, 1, 7, policy);

        assertRawBitsEqual(expected, actual, "copy-safe relaxation changed result");
        assertRawBitsEqual(before, source, "copy-safe relaxation mutated caller storage");
    }

    private static double[] formerClonePerPassFormula(
            double[] source,
            int width,
            int height,
            int lockedOceanEdgeCells,
            int plateSpacingCells,
            LandmassSilhouetteRecipe.CoastRelaxationPolicy policy) {
        final int ppm = 1_000_000;
        double bandWidth = Math.max(
                1.5d,
                plateSpacingCells * policy.bandWidthSpacingPpm() / (double) ppm);
        double maximumShift = policy.maximumShiftPpmOfCell() / (double) ppm;
        double[] current = source.clone();

        for (int pass = 0; pass < policy.passes(); pass++) {
            double[] next = current.clone();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (edgeDistance(x, y, width, height) < lockedOceanEdgeCells) continue;
                    int index = y * width + x;
                    double center = current[index];
                    if (!Double.isFinite(center) || StrictMath.abs(center) > bandWidth) continue;

                    double weighted = center * policy.selfWeightPpm();
                    long totalWeight = policy.selfWeightPpm();
                    for (int oy = -1; oy <= 1; oy++) {
                        for (int ox = -1; ox <= 1; ox++) {
                            if (ox == 0 && oy == 0) continue;
                            int nx = x + ox;
                            int ny = y + oy;
                            if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                            double neighbor = current[ny * width + nx];
                            if (!Double.isFinite(neighbor)) continue;
                            int weight = ox == 0 || oy == 0
                                    ? policy.orthogonalNeighborWeightPpm()
                                    : policy.diagonalNeighborWeightPpm();
                            weighted += neighbor * weight;
                            totalWeight += weight;
                        }
                    }
                    double target = weighted / totalWeight;
                    double shift = Math.max(-maximumShift, Math.min(maximumShift, target - center));
                    next[index] = center + shift;
                }
            }
            current = next;
        }
        return current;
    }

    private static double[] irregularField(int width, int height) {
        double[] field = new double[Math.multiplyExact(width, height)];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                field[index] = StrictMath.sin(x * 0.71d)
                        + StrictMath.cos(y * 0.43d)
                        + (x - y) * 0.03125d;
            }
        }
        field[width + 2] = Double.NaN;
        field[2 * width + 5] = Double.POSITIVE_INFINITY;
        field[3 * width + 7] = Double.NEGATIVE_INFINITY;
        field[4 * width + 3] = -0.0d;
        return field;
    }

    private static LandmassSilhouetteRecipe.CoastRelaxationPolicy policy(int passes) {
        return new LandmassSilhouetteRecipe.CoastRelaxationPolicy(
                passes,
                160_000,
                500_000,
                90_000,
                35_000,
                450_000);
    }

    private static void assertRawBitsEqual(double[] expected, double[] actual, String message) {
        assertEquals(expected.length, actual.length, message + " length");
        for (int index = 0; index < expected.length; index++) {
            assertEquals(
                    Double.doubleToRawLongBits(expected[index]),
                    Double.doubleToRawLongBits(actual[index]),
                    message + " index=" + index);
        }
    }

    private static int edgeDistance(int x, int y, int width, int height) {
        return Math.min(Math.min(x, width - 1 - x), Math.min(y, height - 1 - y));
    }

    private static void set(double[] field, int width, int x, int y, double value) {
        field[index(width, x, y)] = value;
    }

    private static int index(int width, int x, int y) {
        return y * width + x;
    }
}
