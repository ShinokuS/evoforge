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

    private static void set(double[] field, int width, int x, int y, double value) {
        field[index(width, x, y)] = value;
    }

    private static int index(int width, int x, int y) {
        return y * width + x;
    }
}
