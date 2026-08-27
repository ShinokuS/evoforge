package io.github.evoforge.simulation.world.terrain.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.genesis.V15InlandLakeBathymetryRecipe;
import org.junit.jupiter.api.Test;

/** Final V15 result must be independent of bounded request shape and request order. */
final class V15ContinuumWindowParityTest {
    private static final int SIDE = 41;
    private static final long SEED = 71_337L;
    private static final int MIN_Z_CELLS = -16;

    @Test
    void overlappingActiveLakeWindowsAreSeamlessAndRequestOrderIndependent() {
        ContinuumSampleWindow firstWindow = new ContinuumSampleWindow(4L, 4L, 24, 24, 1L);
        ContinuumSampleWindow secondWindow = new ContinuumSampleWindow(16L, 12L, 21, 24, 1L);

        ContinuumScalarPageSource forward = source();
        ContinuumScalarPage firstThen = forward.materialize(firstWindow);
        ContinuumScalarPage secondThen = forward.materialize(secondWindow);

        ContinuumScalarPageSource reverse = source();
        ContinuumScalarPage secondFirst = reverse.materialize(secondWindow);
        ContinuumScalarPage firstSecond = reverse.materialize(firstWindow);

        assertSamePage(firstThen, firstSecond);
        assertSamePage(secondThen, secondFirst);
        assertOverlapEqual(firstThen, secondThen);
        assertOverlapEqual(firstSecond, secondFirst);
        assertTrue(
                containsRefinedLakeDepth(firstThen) || containsRefinedLakeDepth(secondThen),
                "window parity fixture must exercise active V15 inland depth refinement");
    }

    private static ContinuumScalarPageSource source() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(SIDE, SIDE);
        return new V15ExactInlandLakeBathymetryPageSource(
                domain,
                SEED,
                new ArrayPageSource(domain, enclosedLakeBase()),
                MIN_Z_CELLS,
                V15InlandLakeBathymetryRecipe.balanced());
    }

    private static long[] enclosedLakeBase() {
        long[] values = new long[SIDE * SIDE];
        long land = 8L * TerrainElevationField.SUBUNITS_PER_CELL;
        long water = -TerrainElevationField.SUBUNITS_PER_CELL;
        for (int y = 0; y < SIDE; y++) {
            for (int x = 0; x < SIDE; x++) {
                values[y * SIDE + x] = x >= 8 && x <= 32 && y >= 8 && y <= 32 ? water : land;
            }
        }
        return values;
    }

    private static boolean containsRefinedLakeDepth(ContinuumScalarPage page) {
        long originalDepth = -TerrainElevationField.SUBUNITS_PER_CELL;
        for (int y = 0; y < page.window().height(); y++) {
            for (int x = 0; x < page.window().width(); x++) {
                if (Math.round(page.sample(x, y)) < originalDepth) return true;
            }
        }
        return false;
    }

    private static void assertSamePage(ContinuumScalarPage expected, ContinuumScalarPage actual) {
        assertEquals(expected.window(), actual.window());
        for (int y = 0; y < expected.window().height(); y++) {
            for (int x = 0; x < expected.window().width(); x++) {
                assertEquals(
                        Math.round(expected.sample(x, y)),
                        Math.round(actual.sample(x, y)),
                        "request-order parity failed at local x=" + x + " y=" + y);
            }
        }
    }

    private static void assertOverlapEqual(ContinuumScalarPage first, ContinuumScalarPage second) {
        long minX = Math.max(first.window().minX(), second.window().minX());
        long minY = Math.max(first.window().minY(), second.window().minY());
        long maxX = Math.min(
                first.window().xAt(first.window().width() - 1),
                second.window().xAt(second.window().width() - 1));
        long maxY = Math.min(
                first.window().yAt(first.window().height() - 1),
                second.window().yAt(second.window().height() - 1));

        for (long y = minY; y <= maxY; y++) {
            for (long x = minX; x <= maxX; x++) {
                int firstX = Math.toIntExact(x - first.window().minX());
                int firstY = Math.toIntExact(y - first.window().minY());
                int secondX = Math.toIntExact(x - second.window().minX());
                int secondY = Math.toIntExact(y - second.window().minY());
                assertEquals(
                        Math.round(first.sample(firstX, firstY)),
                        Math.round(second.sample(secondX, secondY)),
                        "overlapping V15 windows disagree at x=" + x + " y=" + y);
            }
        }
    }

    private static final class ArrayPageSource implements ContinuumScalarPageSource {
        private final ContinuumWorldDomain domain;
        private final long[] values;

        private ArrayPageSource(ContinuumWorldDomain domain, long[] values) {
            this.domain = domain;
            this.values = values;
        }

        @Override
        public ContinuumWorldDomain domain() {
            return domain;
        }

        @Override
        public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
            double[] samples = new double[Math.multiplyExact(window.width(), window.height())];
            int cursor = 0;
            for (int sampleY = 0; sampleY < window.height(); sampleY++) {
                int y = Math.toIntExact(window.yAt(sampleY));
                for (int sampleX = 0; sampleX < window.width(); sampleX++) {
                    int x = Math.toIntExact(window.xAt(sampleX));
                    samples[cursor++] = values[y * SIDE + x];
                }
            }
            return new ContinuumScalarPage(window, samples);
        }
    }
}
