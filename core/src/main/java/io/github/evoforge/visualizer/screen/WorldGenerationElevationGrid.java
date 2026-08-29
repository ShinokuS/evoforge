package io.github.evoforge.visualizer.screen;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Arrays;

/**
 * Bounded presentation grid sampled from one generated elevation field.
 *
 * <p>The regular interior uses one common world-coordinate step and therefore one bulk elevation
 * request. A final boundary column/row is appended when necessary so the preview mesh still reaches
 * the exact declared world bounds. At most {@code O(axisSamples)} point reads are needed for those
 * boundary strips; the interior is never expanded into per-column point queries.
 *
 * <p>Large-world grids also register a read-only interpolated adapter as the immediate 2D overview
 * fallback for the exact elevation field they were sampled from. Interpolation is presentation-only:
 * it prevents the coarse grid from turning a shoreline into large nearest-neighbour rectangles while
 * an authoritative viewport refinement is prepared in the background. It is never fed back into
 * simulation state and detailed authoritative sampling remains unchanged.</p>
 */
final class WorldGenerationElevationGrid {
    private final WorldBounds bounds;
    private final int[] xCoordinates;
    private final int[] yCoordinates;
    private final long[] elevations;

    private WorldGenerationElevationGrid(
            WorldBounds bounds,
            int[] xCoordinates,
            int[] yCoordinates,
            long[] elevations) {
        this.bounds = bounds;
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

        WorldGenerationElevationGrid grid = new WorldGenerationElevationGrid(
                bounds, xAxis.coordinates(), yAxis.coordinates(), values);
        WorldGenerationOverviewElevationField.registerFallback(
                elevation, grid.presentationFallback());
        return grid;
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

    ElevationField presentationFallback() {
        return new ElevationField() {
            @Override
            public WorldBounds bounds() {
                return bounds;
            }

            @Override
            public int elevationAt(int x, int y) {
                return Math.toIntExact(Math.floorDiv(elevationSubunitsAt(x, y), SUBUNITS_PER_CELL));
            }

            @Override
            public long elevationSubunitsAt(int x, int y) {
                requireCoordinate(x, y);
                Bracket xBracket = bracket(xCoordinates, x);
                Bracket yBracket = bracket(yCoordinates, y);
                return bilinear(xBracket, yBracket, x, y);
            }

            @Override
            public void fillElevationSubunits(
                    int minX,
                    int minY,
                    int sampleWidth,
                    int sampleHeight,
                    long step,
                    long[] target) {
                if (sampleWidth <= 0 || sampleHeight <= 0 || step <= 0L || target == null
                        || target.length < Math.multiplyExact(sampleWidth, sampleHeight)) {
                    throw new IllegalArgumentException(
                            "preview elevation-grid sample request is invalid");
                }
                long maxX = minX + Math.multiplyExact(sampleWidth - 1L, step);
                long maxY = minY + Math.multiplyExact(sampleHeight - 1L, step);
                if (minX < bounds.minX() || maxX > bounds.maxX()
                        || minY < bounds.minY() || maxY > bounds.maxY()) {
                    throw new IllegalArgumentException(
                            "preview elevation-grid sample lies outside bounds");
                }
                int cursor = 0;
                for (int sampleY = 0; sampleY < sampleHeight; sampleY++) {
                    int worldY = Math.toIntExact(minY + sampleY * step);
                    Bracket yBracket = bracket(yCoordinates, worldY);
                    for (int sampleX = 0; sampleX < sampleWidth; sampleX++, cursor++) {
                        int worldX = Math.toIntExact(minX + sampleX * step);
                        Bracket xBracket = bracket(xCoordinates, worldX);
                        target[cursor] = bilinear(xBracket, yBracket, worldX, worldY);
                    }
                }
            }

            private void requireCoordinate(int x, int y) {
                if (x < bounds.minX() || x > bounds.maxX()
                        || y < bounds.minY() || y > bounds.maxY()) {
                    throw new IllegalArgumentException(
                            "coordinate lies outside preview elevation grid");
                }
            }

            private long bilinear(
                    Bracket xBracket,
                    Bracket yBracket,
                    int x,
                    int y) {
                int x0 = xBracket.lower();
                int x1 = xBracket.upper();
                int y0 = yBracket.lower();
                int y1 = yBracket.upper();
                long v00 = elevations[y0 * width() + x0];
                if (x0 == x1 && y0 == y1) return v00;
                long v10 = elevations[y0 * width() + x1];
                long v01 = elevations[y1 * width() + x0];
                long v11 = elevations[y1 * width() + x1];
                double tx = interpolationFraction(xCoordinates[x0], xCoordinates[x1], x);
                double ty = interpolationFraction(yCoordinates[y0], yCoordinates[y1], y);
                double top = v00 + (v10 - (double) v00) * tx;
                double bottom = v01 + (v11 - (double) v01) * tx;
                return Math.round(top + (bottom - top) * ty);
            }
        };
    }

    private static Bracket bracket(int[] coordinates, int coordinate) {
        int exact = Arrays.binarySearch(coordinates, coordinate);
        if (exact >= 0) return new Bracket(exact, exact);
        int insertion = -exact - 1;
        if (insertion <= 0) return new Bracket(0, 0);
        if (insertion >= coordinates.length) {
            int last = coordinates.length - 1;
            return new Bracket(last, last);
        }
        return new Bracket(insertion - 1, insertion);
    }

    private static double interpolationFraction(int lower, int upper, int coordinate) {
        if (lower == upper) return 0d;
        return (coordinate - (double) lower) / (upper - (double) lower);
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
    private record Bracket(int lower, int upper) {}
}
