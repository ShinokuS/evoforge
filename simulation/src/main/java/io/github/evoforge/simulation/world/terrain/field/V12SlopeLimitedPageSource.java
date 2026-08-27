package io.github.evoforge.simulation.world.terrain.field;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.genesis.V12ContinuumSlopeCalibration;
import java.util.Arrays;

/**
 * Bounded Continuum slope projection for accepted V12 terrestrial relief.
 *
 * <p>The historical four directional in-place sweeps are intentionally not reproduced: their
 * traversal can propagate a correction across an arbitrarily large connected component and is
 * therefore incompatible with bounded local materialization. Instead this source computes the
 * symmetric mean of the two extremal Lipschitz envelopes over the cardinal land graph:</p>
 *
 * <pre>
 * lower(x) = sup_y (h(y) - S d(x,y))
 * upper(x) = inf_y (h(y) + S d(x,y))
 * result(x) = floor((lower(x) + upper(x)) / 2)
 * </pre>
 *
 * <p>{@code S} is the accepted V12 maximum readable cardinal step and {@code d} is cardinal path
 * distance through land only. Already slope-compliant terrain is unchanged. Wet/dry membership is
 * never changed. Because V12 land height is bounded, sources farther than the calibrated halo cannot
 * improve either envelope, so every returned page is exact for this projection without materializing
 * the logical world.</p>
 */
public final class V12SlopeLimitedPageSource implements ContinuumScalarPageSource {
    private final ContinuumWorldDomain domain;
    private final TerrainElevationField source;
    private final V12ContinuumSlopeCalibration calibration;

    public V12SlopeLimitedPageSource(
            ContinuumWorldDomain domain,
            TerrainElevationField source,
            V12ContinuumSlopeCalibration calibration) {
        if (domain == null || source == null || calibration == null) {
            throw new IllegalArgumentException("V12 slope page-source inputs must not be null");
        }
        this.domain = domain;
        this.source = source;
        this.calibration = calibration;
    }

    @Override
    public ContinuumWorldDomain domain() {
        return domain;
    }

    public int exactHaloCells() {
        return calibration.exactHaloCells();
    }

    public long maximumStepSubunits() {
        return calibration.maximumStepSubunits();
    }

    @Override
    public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
        validateWindow(window);

        long requestedMaxX = window.xAt(window.width() - 1);
        long requestedMaxY = window.yAt(window.height() - 1);
        int halo = calibration.exactHaloCells();
        long haloMinX = Math.max(0L, window.minX() - halo);
        long haloMinY = Math.max(0L, window.minY() - halo);
        long haloMaxX = Math.min(domain.width() - 1L, requestedMaxX + halo);
        long haloMaxY = Math.min(domain.height() - 1L, requestedMaxY + halo);
        int haloWidth = Math.toIntExact(haloMaxX - haloMinX + 1L);
        int haloHeight = Math.toIntExact(haloMaxY - haloMinY + 1L);
        int haloArea = Math.multiplyExact(haloWidth, haloHeight);

        long[] base = new long[haloArea];
        boolean hasLand = false;
        int cursor = 0;
        for (int y = 0; y < haloHeight; y++) {
            long worldY = haloMinY + y;
            for (int x = 0; x < haloWidth; x++) {
                long value = source.elevationSubunitsAt(haloMinX + x, worldY);
                if (value > 0L) {
                    if (value > calibration.maximumLandHeightSubunits()) {
                        throw new IllegalStateException(
                                "V12 source height exceeds calibrated land-height bound");
                    }
                    hasLand = true;
                }
                base[cursor++] = value;
            }
        }

        int outputArea = Math.multiplyExact(window.width(), window.height());
        double[] output = new double[outputArea];
        if (!hasLand || halo == 0) {
            copyRequested(window, haloMinX, haloMinY, haloWidth, base, output);
            return new ContinuumScalarPage(window, output);
        }

        IndexedMinHeap transform = new IndexedMinHeap(base.length);
        transform.build(base, false);
        runCardinalTransform(transform, base, haloWidth, haloHeight);
        long[] upper = new long[outputArea];
        copyRequestedLong(window, haloMinX, haloMinY, haloWidth, base, transform.distance, upper);

        transform.build(base, true);
        runCardinalTransform(transform, base, haloWidth, haloHeight);

        int sample = 0;
        for (int y = 0; y < window.height(); y++) {
            int localY = Math.toIntExact(window.yAt(y) - haloMinY);
            for (int x = 0; x < window.width(); x++, sample++) {
                int localX = Math.toIntExact(window.xAt(x) - haloMinX);
                int index = localY * haloWidth + localX;
                long baseHeight = base[index];
                if (baseHeight <= 0L) {
                    output[sample] = baseHeight;
                    continue;
                }
                long upperHeight = upper[sample];
                long lowerHeight = -transform.distance[index];
                if (upperHeight > baseHeight || lowerHeight < baseHeight || lowerHeight < upperHeight) {
                    throw new IllegalStateException("invalid V12 Lipschitz envelope ordering");
                }
                output[sample] = upperHeight + (lowerHeight - upperHeight) / 2L;
            }
        }
        return new ContinuumScalarPage(window, output);
    }

    private void runCardinalTransform(
            IndexedMinHeap transform,
            long[] base,
            int width,
            int height) {
        long step = calibration.maximumStepSubunits();
        while (!transform.isEmpty()) {
            int current = transform.popMinimum();
            int x = current % width;
            int y = current / width;
            if (x > 0) relax(transform, base, current, current - 1, step);
            if (x + 1 < width) relax(transform, base, current, current + 1, step);
            if (y > 0) relax(transform, base, current, current - width, step);
            if (y + 1 < height) relax(transform, base, current, current + width, step);
        }
    }

    private static void relax(
            IndexedMinHeap transform,
            long[] base,
            int from,
            int to,
            long step) {
        if (base[to] <= 0L || !transform.isActive(to)) return;
        long candidate = Math.addExact(transform.distance[from], step);
        if (candidate < transform.distance[to]) {
            transform.decreaseKey(to, candidate);
        }
    }

    private void validateWindow(ContinuumSampleWindow window) {
        if (window == null) {
            throw new IllegalArgumentException("window must not be null");
        }
        long maxX = window.xAt(window.width() - 1);
        long maxY = window.yAt(window.height() - 1);
        if (!domain.contains(window.minX(), window.minY()) || !domain.contains(maxX, maxY)) {
            throw new IllegalArgumentException("window lies outside the V12 slope domain");
        }
    }

    private static void copyRequested(
            ContinuumSampleWindow window,
            long haloMinX,
            long haloMinY,
            int haloWidth,
            long[] base,
            double[] output) {
        int sample = 0;
        for (int y = 0; y < window.height(); y++) {
            int localY = Math.toIntExact(window.yAt(y) - haloMinY);
            for (int x = 0; x < window.width(); x++, sample++) {
                int localX = Math.toIntExact(window.xAt(x) - haloMinX);
                output[sample] = base[localY * haloWidth + localX];
            }
        }
    }

    private static void copyRequestedLong(
            ContinuumSampleWindow window,
            long haloMinX,
            long haloMinY,
            int haloWidth,
            long[] base,
            long[] transformed,
            long[] output) {
        int sample = 0;
        for (int y = 0; y < window.height(); y++) {
            int localY = Math.toIntExact(window.yAt(y) - haloMinY);
            for (int x = 0; x < window.width(); x++, sample++) {
                int localX = Math.toIntExact(window.xAt(x) - haloMinX);
                int index = localY * haloWidth + localX;
                output[sample] = base[index] > 0L ? transformed[index] : base[index];
            }
        }
    }

    /** Primitive indexed binary heap; every land cell is an initial source and heap size never exceeds halo area. */
    private static final class IndexedMinHeap {
        private final int[] heap;
        private final int[] position;
        private final long[] distance;
        private int size;

        private IndexedMinHeap(int capacity) {
            heap = new int[capacity];
            position = new int[capacity];
            distance = new long[capacity];
        }

        private void build(long[] base, boolean negate) {
            Arrays.fill(position, -2);
            size = 0;
            for (int index = 0; index < base.length; index++) {
                long value = base[index];
                if (value <= 0L) {
                    distance[index] = Long.MAX_VALUE;
                    continue;
                }
                distance[index] = negate ? -value : value;
                heap[size] = index;
                position[index] = size;
                size++;
            }
            for (int parent = size / 2 - 1; parent >= 0; parent--) {
                siftDown(parent);
            }
        }

        private boolean isEmpty() {
            return size == 0;
        }

        private boolean isActive(int index) {
            return position[index] >= 0;
        }

        private int popMinimum() {
            if (size == 0) throw new IllegalStateException("V12 slope heap is empty");
            int minimum = heap[0];
            int last = heap[--size];
            position[minimum] = -1;
            if (size > 0) {
                heap[0] = last;
                position[last] = 0;
                siftDown(0);
            }
            return minimum;
        }

        private void decreaseKey(int index, long value) {
            int at = position[index];
            if (at < 0) throw new IllegalStateException("cannot decrease finalized V12 slope node");
            if (value >= distance[index]) return;
            distance[index] = value;
            siftUp(at);
        }

        private void siftUp(int start) {
            int child = start;
            while (child > 0) {
                int parent = (child - 1) >>> 1;
                if (!less(heap[child], heap[parent])) return;
                swap(child, parent);
                child = parent;
            }
        }

        private void siftDown(int start) {
            int parent = start;
            while (true) {
                int left = parent * 2 + 1;
                if (left >= size) return;
                int right = left + 1;
                int smallest = right < size && less(heap[right], heap[left]) ? right : left;
                if (!less(heap[smallest], heap[parent])) return;
                swap(parent, smallest);
                parent = smallest;
            }
        }

        private boolean less(int first, int second) {
            long firstDistance = distance[first];
            long secondDistance = distance[second];
            return firstDistance < secondDistance
                    || firstDistance == secondDistance && first < second;
        }

        private void swap(int firstPosition, int secondPosition) {
            int first = heap[firstPosition];
            int second = heap[secondPosition];
            heap[firstPosition] = second;
            heap[secondPosition] = first;
            position[first] = secondPosition;
            position[second] = firstPosition;
        }
    }
}
