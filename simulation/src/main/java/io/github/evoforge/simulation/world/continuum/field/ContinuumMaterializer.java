package io.github.evoforge.simulation.world.continuum.field;

import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;

/** Materializes only the requested bounded samples from an authoritative point-addressable field. */
public final class ContinuumMaterializer implements ContinuumScalarPageSource {
    private final ContinuumWorldDomain domain;
    private final ContinuumScalarField field;

    public ContinuumMaterializer(ContinuumWorldDomain domain, ContinuumScalarField field) {
        if (domain == null || field == null) {
            throw new IllegalArgumentException("domain and field must not be null");
        }
        this.domain = domain;
        this.field = field;
    }

    @Override
    public ContinuumWorldDomain domain() {
        return domain;
    }

    @Override
    public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
        if (window == null) {
            throw new IllegalArgumentException("window must not be null");
        }
        long maxX = window.xAt(window.width() - 1);
        long maxY = window.yAt(window.height() - 1);
        if (!domain.contains(window.minX(), window.minY()) || !domain.contains(maxX, maxY)) {
            throw new IllegalArgumentException("window lies outside the logical world domain");
        }
        double[] samples = new double[Math.multiplyExact(window.width(), window.height())];
        for (int y = 0; y < window.height(); y++) {
            long worldY = window.yAt(y);
            for (int x = 0; x < window.width(); x++) {
                samples[y * window.width() + x] = field.sample(window.xAt(x), worldY);
            }
        }
        return new ContinuumScalarPage(window, samples);
    }
}
