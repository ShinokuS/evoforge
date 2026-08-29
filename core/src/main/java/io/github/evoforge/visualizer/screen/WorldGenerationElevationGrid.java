package io.github.evoforge.visualizer.screen;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Bounded presentation grid sampled from one generated elevation field.
 *
 * <p>The regular interior uses one common world-coordinate step and therefore one bulk elevation
 * request. A final boundary column/row is appended when necessary so the preview mesh still reaches
 * the exact declared world bounds. At most {@code O(axisSamples)} point reads are needed for those
 * boundary strips; the interior is never expanded into per-column point queries.</p>
 */
final class WorldGenerationElevationGrid {
    private final int[] xCoordinates;
    private final int[] yCoordinates;
    private final long[] elevations;

    private WorldGenerationElevationGrid(
            int[] xCoordinates,
            int[] yCoordinates,
            long[] elevations) {
        this.xCoordinates = xCoordinates;
        this.yCoordinates = yCoordinates;
        this.elevations = elevations;
    }

    static WorldGenerationElevationGrid sample(ElevationField elevation, int maximumAxisSamples) {
        if (elevation == null) throw new IllegalArgumentException("elevation must not be null");
        if (maximumAxisSamples < 2) {
            throw new IllegalArgumentException("maximumAxisSamples must be >= 2");
        }
        WorldBounds bounds = elevation.bounds();
        long spanX = (long) bounds.maxX() - bounds.minX();
        long spanY = (long) bounds.maxY() - bounds.minY();
        long maximumSpan = Math.max(spanX, spanY);
        long step = maximumSpan == 0L
                ? 1L
                : divideCeil(maximumSpan, maximumAxisSamples - 1L);

        Axis xAxis = axis(bounds.minX(), bounds.maxX(), step);
        Axis yAxis = axis(bounds.minY(), bounds.maxY(), step);
        int width = xAxis.coordinates().length;
        int height = yAxis.coordinates().length;
        long[] values = new long[Math.multiplyExact(width, height)];

        int regularWidth = xAxis.regularCount();
        int regularHeight = yAxis.regularCount();
        long[] regular = new long[Math.multiplyExact(regularWidth, regularHeight)];
        elevation.fillElevationSubunits(
                bounds.minX(),
                bounds.minY(),
                regularWidth,
                regularHeight,
                step,
                regular);
        for (int y = 0; y < regularHeight; y++) {
            System.arraycopy(regular, y * regularWidth, values, y * width, regularWidth);
        }

        if (xAxis.hasBoundarySample()) {
            int boundaryX = width - 1;
            int worldX = xAxis.coordinates()[boundaryX];
            for (int y = 0; y < regularHeight; y++) {
                values[y * width + boundaryX] = elevation.elevationSubunitsAt(
                        worldX, yAxis.coordinates()[y]);
            }
        }
        if (yAxis.hasBoundarySample()) {
            int boundaryY = height - 1;
            int worldY = yAxis.coordinates()[boundaryY];
            for (int x = 0; x < regularWidth; x++) {
                values[boundaryY * width + x] = elevation.elevationSubunitsAt(
                        xAxis.coordinates()[x], worldY);
            }
        }
        if (xAxis.hasBoundarySample() && yAxis.hasBoundarySample()) {
            values[(height - 1) * width + width - 1] = elevation.elevationSubunitsAt(
                    xAxis.coordinates()[width - 1], yAxis.coordinates()[height - 1]);
        }

        return new WorldGenerationElevationGrid(
                xAxis.coordinates(), yAxis.coordinates(), values);
    }

    int width() {
        return xCoordinates.length;
    }

    int height() {
        return yCoordinates.length;
    }

    int xAt(int sampleX) {
        return xCoordinates[sampleX];
    }

    int yAt(int sampleY) {
        return yCoordinates[sampleY];
    }

    long elevationSubunitsAt(int sampleX, int sampleY) {
        return elevations[sampleY * width() + sampleX];
    }

    private static Axis axis(int minimum, int maximum, long step) {
        long span = (long) maximum - minimum;
        int regularCount = Math.toIntExact(span / step + 1L);
        long regularMaximum = (long) minimum + (regularCount - 1L) * step;
        boolean boundary = regularMaximum != maximum;
        int[] coordinates = new int[regularCount + (boundary ? 1 : 0)];
        for (int sample = 0; sample < regularCount; sample++) {
            coordinates[sample] = Math.toIntExact((long) minimum + sample * step);
        }
        if (boundary) coordinates[coordinates.length - 1] = maximum;
        return new Axis(coordinates, regularCount, boundary);
    }

    private static long divideCeil(long value, long divisor) {
        return Math.floorDiv(value - 1L, divisor) + 1L;
    }

    private record Axis(int[] coordinates, int regularCount, boolean hasBoundarySample) {}
}
