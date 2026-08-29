package io.github.evoforge.simulation.world.terrain.field;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;

/**
 * Bounded page adapter for the authored V12 elevation before directional slope relaxation.
 *
 * <p>Unit-resolution requests use {@link V12UnrelaxedLandElevationField#fillWindow} so land/coast
 * membership is shared across the whole local request. Sparse requests choose between one bounded
 * dense envelope and independent point evaluation by a fixed work estimate. The dense envelope is
 * capped at one million cells, so a coarse request spread over a 10k/100k world can never turn into
 * whole-world materialization merely because its samples are far apart.</p>
 */
public final class V12UnrelaxedElevationPageSource implements ContinuumScalarPageSource {
    private static final long MAX_DENSE_SAMPLE_CELLS = 1_048_576L;
    private static final long POINT_EQUIVALENT_DENSE_CELLS = 1_024L;

    private final ContinuumWorldDomain domain;
    private final V12UnrelaxedLandElevationField source;

    public V12UnrelaxedElevationPageSource(
            ContinuumWorldDomain domain,
            V12UnrelaxedLandElevationField source) {
        if (domain == null || source == null || !domain.equals(source.domain())) {
            throw new IllegalArgumentException("unrelaxed V12 page source must share one domain");
        }
        this.domain = domain;
        this.source = source;
    }

    @Override
    public ContinuumWorldDomain domain() {
        return domain;
    }

    @Override
    public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
        validateWindow(window);
        int outputArea = Math.multiplyExact(window.width(), window.height());
        double[] output = new double[outputArea];
        if (window.step() == 1L) {
            long[] dense = new long[outputArea];
            source.fillWindow(window.minX(), window.minY(), window.width(), window.height(), dense);
            copyToDouble(dense, output, outputArea);
            return new ContinuumScalarPage(window, output);
        }

        long maxX = window.xAt(window.width() - 1);
        long maxY = window.yAt(window.height() - 1);
        long denseWidth = maxX - window.minX() + 1L;
        long denseHeight = maxY - window.minY() + 1L;
        long denseCells = saturatedMultiply(denseWidth, denseHeight);
        long sparseEquivalent = saturatedMultiply(outputArea, POINT_EQUIVALENT_DENSE_CELLS);
        if (denseCells <= MAX_DENSE_SAMPLE_CELLS
                && denseCells <= sparseEquivalent
                && denseWidth <= Integer.MAX_VALUE
                && denseHeight <= Integer.MAX_VALUE) {
            int width = Math.toIntExact(denseWidth);
            int height = Math.toIntExact(denseHeight);
            long[] dense = new long[Math.multiplyExact(width, height)];
            source.fillWindow(window.minX(), window.minY(), width, height, dense);
            int cursor = 0;
            for (int sampleY = 0; sampleY < window.height(); sampleY++) {
                int localY = Math.toIntExact(window.yAt(sampleY) - window.minY());
                int row = Math.multiplyExact(localY, width);
                for (int sampleX = 0; sampleX < window.width(); sampleX++, cursor++) {
                    int localX = Math.toIntExact(window.xAt(sampleX) - window.minX());
                    output[cursor] = dense[row + localX];
                }
            }
            return new ContinuumScalarPage(window, output);
        }

        int cursor = 0;
        for (int sampleY = 0; sampleY < window.height(); sampleY++) {
            long y = window.yAt(sampleY);
            for (int sampleX = 0; sampleX < window.width(); sampleX++, cursor++) {
                output[cursor] = source.elevationSubunitsAt(window.xAt(sampleX), y);
            }
        }
        return new ContinuumScalarPage(window, output);
    }

    private static void copyToDouble(long[] source, double[] target, int area) {
        for (int cell = 0; cell < area; cell++) target[cell] = source[cell];
    }

    private static long saturatedMultiply(long first, long second) {
        if (first <= 0L || second <= 0L) return 0L;
        if (first > Long.MAX_VALUE / second) return Long.MAX_VALUE;
        return first * second;
    }

    private void validateWindow(ContinuumSampleWindow window) {
        if (window == null) throw new IllegalArgumentException("window must not be null");
        long maxX = window.xAt(window.width() - 1);
        long maxY = window.yAt(window.height() - 1);
        if (!domain.contains(window.minX(), window.minY()) || !domain.contains(maxX, maxY)) {
            throw new IllegalArgumentException("window lies outside the unrelaxed V12 domain");
        }
    }
}
