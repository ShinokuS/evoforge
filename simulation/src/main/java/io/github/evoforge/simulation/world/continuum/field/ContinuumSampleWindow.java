package io.github.evoforge.simulation.world.continuum.field;

/** Bounded request in global world coordinates. */
public record ContinuumSampleWindow(long minX, long minY, int width, int height, long step) {
    public ContinuumSampleWindow {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("sample dimensions must be > 0");
        }
        if (step <= 0L) {
            throw new IllegalArgumentException("step must be > 0");
        }
        Math.addExact(minX, Math.multiplyExact((long) width - 1L, step));
        Math.addExact(minY, Math.multiplyExact((long) height - 1L, step));
    }

    public long xAt(int sampleX) {
        if (sampleX < 0 || sampleX >= width) {
            throw new IndexOutOfBoundsException(sampleX);
        }
        return minX + (long) sampleX * step;
    }

    public long yAt(int sampleY) {
        if (sampleY < 0 || sampleY >= height) {
            throw new IndexOutOfBoundsException(sampleY);
        }
        return minY + (long) sampleY * step;
    }
}
