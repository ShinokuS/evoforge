package io.github.evoforge.simulation.world.terrain.field;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;

/**
 * Eager exact snapshot for genuinely small finite terrain domains.
 *
 * <p>The cache is deliberately hard-bounded to 512 x 512 samples (2 MiB of doubles) and therefore
 * cannot grow with a large Continuum address space. It exists to avoid re-running historical global
 * parity passes when a small finite world is requested repeatedly (tests, inspection and other
 * bounded consumers). Domains above the fixed payload budget keep the original page source and its
 * bounded materialization behavior unchanged.</p>
 */
public final class BoundedExactTerrainSnapshotPageSource implements ContinuumScalarPageSource {
    public static final int MAX_SNAPSHOT_CELLS = 512 * 512;

    private final ContinuumWorldDomain domain;
    private final int width;
    private final double[] samples;

    private BoundedExactTerrainSnapshotPageSource(ContinuumScalarPageSource source) {
        this.domain = source.domain();
        this.width = Math.toIntExact(domain.width());
        int height = Math.toIntExact(domain.height());
        ContinuumSampleWindow wholeDomain = new ContinuumSampleWindow(0L, 0L, width, height, 1L);
        this.samples = source.materialize(wholeDomain).copySamples();
    }

    /**
     * Captures the complete exact result only when it fits the fixed small-domain payload budget.
     * Larger domains are returned unchanged and never allocate a whole-domain snapshot.
     */
    public static ContinuumScalarPageSource captureIfBounded(ContinuumScalarPageSource source) {
        if (source == null) throw new IllegalArgumentException("source must not be null");
        ContinuumWorldDomain domain = source.domain();
        long width = domain.width();
        long height = domain.height();
        if (width > MAX_SNAPSHOT_CELLS || height > MAX_SNAPSHOT_CELLS) return source;
        if (Math.multiplyExact(width, height) > MAX_SNAPSHOT_CELLS) return source;
        return new BoundedExactTerrainSnapshotPageSource(source);
    }

    @Override
    public ContinuumWorldDomain domain() {
        return domain;
    }

    @Override
    public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
        validateWindow(window);
        double[] result = new double[Math.multiplyExact(window.width(), window.height())];
        int cursor = 0;
        for (int sampleY = 0; sampleY < window.height(); sampleY++) {
            int y = Math.toIntExact(window.yAt(sampleY));
            for (int sampleX = 0; sampleX < window.width(); sampleX++) {
                int x = Math.toIntExact(window.xAt(sampleX));
                result[cursor++] = samples[y * width + x];
            }
        }
        return new ContinuumScalarPage(window, result);
    }

    private void validateWindow(ContinuumSampleWindow window) {
        if (window == null) throw new IllegalArgumentException("window must not be null");
        long maxX = window.xAt(window.width() - 1);
        long maxY = window.yAt(window.height() - 1);
        if (!domain.contains(window.minX(), window.minY()) || !domain.contains(maxX, maxY)) {
            throw new IllegalArgumentException("window lies outside bounded terrain snapshot");
        }
    }
}
