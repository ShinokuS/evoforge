package io.github.evoforge.simulation.world.continuum.field;

import java.util.Arrays;

/** Immutable bounded materialization. The backing array is page-sized, never world-sized. */
public final class ContinuumScalarPage {
    private final ContinuumSampleWindow window;
    private final double[] samples;

    public ContinuumScalarPage(ContinuumSampleWindow window, double[] samples) {
        if (window == null || samples == null) {
            throw new IllegalArgumentException("window and samples must not be null");
        }
        int expected = Math.multiplyExact(window.width(), window.height());
        if (samples.length != expected) {
            throw new IllegalArgumentException("unexpected sample count");
        }
        this.window = window;
        this.samples = samples.clone();
    }

    public ContinuumSampleWindow window() {
        return window;
    }

    public double sample(int x, int y) {
        if (x < 0 || x >= window.width() || y < 0 || y >= window.height()) {
            throw new IndexOutOfBoundsException();
        }
        return samples[y * window.width() + x];
    }

    public double[] copySamples() {
        return Arrays.copyOf(samples, samples.length);
    }
}
