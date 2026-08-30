package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Immutable unit-resolution elevation snapshot used by bounded generated-world presentations.
 *
 * <p>The source is bulk-read in bounded row bands during generation. Once construction finishes,
 * point and strided reads are plain array access and can never trigger Continuum page generation,
 * cache misses or background refinement while the user pans or zooms the preview.</p>
 */
public final class MaterializedElevationField implements ElevationField {
    private static final int TARGET_BULK_SAMPLES = 262_144;

    private final WorldBounds bounds;
    private final int width;
    private final int height;
    private final long[] elevations;

    private MaterializedElevationField(
            WorldBounds bounds,
            int width,
            int height,
            long[] elevations) {
        this.bounds = bounds;
        this.width = width;
        this.height = height;
        this.elevations = elevations;
    }

    public static MaterializedElevationField copyOf(ElevationField source) {
        if (source == null) throw new IllegalArgumentException("source elevation must not be null");
        WorldBounds bounds = source.bounds();
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.toIntExact(Math.multiplyExact((long) width, height));
        long[] elevations = new long[area];

        int rowsPerBatch = Math.max(1, Math.min(height, TARGET_BULK_SAMPLES / Math.max(1, width)));
        long[] batch = new long[Math.multiplyExact(width, rowsPerBatch)];
        for (int row = 0; row < height; row += rowsPerBatch) {
            int rows = Math.min(rowsPerBatch, height - row);
            int samples = Math.multiplyExact(width, rows);
            if (batch.length < samples) batch = new long[samples];
            source.fillElevationSubunits(
                    bounds.minX(),
                    Math.addExact(bounds.minY(), row),
                    width,
                    rows,
                    1L,
                    batch);
            System.arraycopy(batch, 0, elevations, row * width, samples);
        }
        return new MaterializedElevationField(bounds, width, height, elevations);
    }

    @Override
    public WorldBounds bounds() {
        return bounds;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public long cellCount() {
        return elevations.length;
    }

    @Override
    public int elevationAt(int x, int y) {
        return Math.toIntExact(Math.floorDiv(elevationSubunitsAt(x, y), SUBUNITS_PER_CELL));
    }

    @Override
    public long elevationSubunitsAt(int x, int y) {
        requireCoordinate(x, y);
        return elevations[(y - bounds.minY()) * width + (x - bounds.minX())];
    }

    @Override
    public void fillElevationSubunits(
            int minX,
            int minY,
            int sampleWidth,
            int sampleHeight,
            long step,
            long[] target) {
        int samples = validateSampleRequest(minX, minY, sampleWidth, sampleHeight, step, target);
        if (samples == 0) return;

        if (step == 1L) {
            int sourceX = minX - bounds.minX();
            int sourceY = minY - bounds.minY();
            for (int y = 0; y < sampleHeight; y++) {
                System.arraycopy(
                        elevations,
                        (sourceY + y) * width + sourceX,
                        target,
                        y * sampleWidth,
                        sampleWidth);
            }
            return;
        }

        int cursor = 0;
        for (int sampleY = 0; sampleY < sampleHeight; sampleY++) {
            int y = Math.toIntExact((long) minY + sampleY * step);
            for (int sampleX = 0; sampleX < sampleWidth; sampleX++, cursor++) {
                int x = Math.toIntExact((long) minX + sampleX * step);
                target[cursor] = elevationSubunitsAt(x, y);
            }
        }
    }

    private int validateSampleRequest(
            int minX,
            int minY,
            int sampleWidth,
            int sampleHeight,
            long step,
            long[] target) {
        if (sampleWidth <= 0 || sampleHeight <= 0 || step <= 0L || target == null) {
            throw new IllegalArgumentException("materialized elevation sample request is invalid");
        }
        int samples = Math.multiplyExact(sampleWidth, sampleHeight);
        if (target.length < samples) {
            throw new IllegalArgumentException("materialized elevation output is too small");
        }
        long maxX = Math.addExact((long) minX, Math.multiplyExact((long) sampleWidth - 1L, step));
        long maxY = Math.addExact((long) minY, Math.multiplyExact((long) sampleHeight - 1L, step));
        if (maxX < Integer.MIN_VALUE || maxX > Integer.MAX_VALUE
                || maxY < Integer.MIN_VALUE || maxY > Integer.MAX_VALUE
                || !contains(minX, minY)
                || !contains((int) maxX, (int) maxY)) {
            throw new IllegalArgumentException("materialized elevation sample lies outside bounds");
        }
        return samples;
    }

    private void requireCoordinate(int x, int y) {
        if (!contains(x, y)) {
            throw new IllegalArgumentException(
                    "position outside materialized elevation field: (" + x + ", " + y + ")");
        }
    }
}
