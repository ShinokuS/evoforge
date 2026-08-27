package io.github.evoforge.simulation.world.terrain.field;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;

/** Exact Continuum materialization of the accepted V15 Z=0 inland-lake shoreline contract. */
public final class V15ExactInlandLakeBasePageSource implements ContinuumScalarPageSource {
    private final ContinuumWorldDomain domain;
    private final ContinuumScalarPageSource continentalBase;
    private final V15InlandLakeDomainPlan lakeDomain;

    public V15ExactInlandLakeBasePageSource(
            ContinuumWorldDomain domain,
            ContinuumScalarPageSource continentalBase,
            V15InlandLakeDomainPlan lakeDomain) {
        if (domain == null || continentalBase == null || lakeDomain == null) {
            throw new IllegalArgumentException("V15 inland-lake base inputs must not be null");
        }
        if (!domain.equals(continentalBase.domain()) || !domain.equals(lakeDomain.domain())) {
            throw new IllegalArgumentException("V15 inland-lake base dependencies must share one domain");
        }
        this.domain = domain;
        this.continentalBase = continentalBase;
        this.lakeDomain = lakeDomain;
        lakeDomain.verifyDrySupport(continentalBase);
    }

    @Override
    public ContinuumWorldDomain domain() {
        return domain;
    }

    @Override
    public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
        validateWindow(window);
        ContinuumScalarPage basePage = continentalBase.materialize(window);
        if (lakeDomain.lakeCellCount() == 0) return basePage;

        double[] samples = new double[Math.multiplyExact(window.width(), window.height())];
        int cursor = 0;
        for (int sampleY = 0; sampleY < window.height(); sampleY++) {
            long y = window.yAt(sampleY);
            for (int sampleX = 0; sampleX < window.width(); sampleX++) {
                long x = window.xAt(sampleX);
                double original = basePage.sample(sampleX, sampleY);
                if (lakeDomain.isLake(x, y)) {
                    if (Math.round(original) <= 0L) {
                        throw new IllegalStateException(
                                "inland lake domain overlapped existing standing water at " + x + "," + y);
                    }
                    samples[cursor++] = -1L;
                } else {
                    samples[cursor++] = original;
                }
            }
        }
        return new ContinuumScalarPage(window, samples);
    }

    private void validateWindow(ContinuumSampleWindow window) {
        if (window == null) throw new IllegalArgumentException("window must not be null");
        long maxX = window.xAt(window.width() - 1);
        long maxY = window.yAt(window.height() - 1);
        if (!domain.contains(window.minX(), window.minY()) || !domain.contains(maxX, maxY)) {
            throw new IllegalArgumentException("window lies outside V15 inland-lake base domain");
        }
    }
}
