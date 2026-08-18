package io.github.evoforge.visualizer.screen;

/** Pure sampling policy that caps far-zoom 2D preview work while keeping close inspection exact. */
final class WorldGeneration2DLod {
    /**
     * CPU-side SpriteBatch submission is the limiting cost in the generated-world overview.
     * Keep the far view near a few tens of thousands of submitted blocks rather than attempting
     * to draw one sprite for every visible world column.
     */
    static final long MAX_SAMPLES = 20_000L;

    private WorldGeneration2DLod() {
    }

    static int stride(int widthCells, int lengthCells) {
        if (widthCells <= 0 || lengthCells <= 0) return 1;
        long cells = Math.multiplyExact((long) widthCells, (long) lengthCells);
        if (cells <= MAX_SAMPLES) return 1;
        return Math.max(1, (int) Math.ceil(Math.sqrt(cells / (double) MAX_SAMPLES)));
    }

    static long sampledCells(int widthCells, int lengthCells, int stride) {
        if (widthCells <= 0 || lengthCells <= 0) return 0L;
        int safeStride = Math.max(1, stride);
        long x = (widthCells + (long) safeStride - 1L) / safeStride;
        long y = (lengthCells + (long) safeStride - 1L) / safeStride;
        return Math.multiplyExact(x, y);
    }
}
