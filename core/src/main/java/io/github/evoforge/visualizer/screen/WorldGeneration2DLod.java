package io.github.evoforge.visualizer.screen;

/**
 * Pure sampling policy that caps 2D preview work while keeping close inspection exact.
 *
 * <p>Detailed LOD x1 is substantially more expensive than overview sampling because each visible
 * cell resolves Shape presentation, neighbour topology and relief edges. Overview mode is also
 * intentionally capped well below the old 20k-sample budget because terrain, water and contour
 * passes compound the actual GPU submissions.</p>
 */
final class WorldGeneration2DLod {
    static final long MAX_DETAILED_CELLS = 9_000L;
    static final long MAX_SAMPLES = 6_000L;

    private WorldGeneration2DLod() {
    }

    static int stride(int widthCells, int lengthCells) {
        if (widthCells <= 0 || lengthCells <= 0) return 1;
        long cells = Math.multiplyExact((long) widthCells, (long) lengthCells);
        if (cells <= MAX_DETAILED_CELLS) return 1;

        int overviewStride = Math.max(
                1,
                (int) Math.ceil(Math.sqrt(cells / (double) MAX_SAMPLES)));
        return Math.max(2, overviewStride);
    }

    static long sampledCells(int widthCells, int lengthCells, int stride) {
        if (widthCells <= 0 || lengthCells <= 0) return 0L;
        int safeStride = Math.max(1, stride);
        long x = (widthCells + (long) safeStride - 1L) / safeStride;
        long y = (lengthCells + (long) safeStride - 1L) / safeStride;
        return Math.multiplyExact(x, y);
    }
}
