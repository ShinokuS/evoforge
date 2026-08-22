package io.github.evoforge.simulation.world.continuum.query;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import java.util.Arrays;

/** Immutable consumer-local result. It exposes only the requested window, never whole shared pages. */
public final class ContinuumLocalScalarView {
    private final String consumerId;
    private final long revision;
    private final ContinuumSampleWindow window;
    private final double[] samples;

    ContinuumLocalScalarView(
            String consumerId,
            long revision,
            ContinuumSampleWindow window,
            double[] samples) {
        this.consumerId = consumerId;
        this.revision = revision;
        this.window = window;
        this.samples = samples.clone();
    }

    public String consumerId() {
        return consumerId;
    }

    public long revision() {
        return revision;
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
