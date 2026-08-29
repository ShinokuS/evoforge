package io.github.evoforge.simulation.world.terrain.field;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.genesis.V15ContinuumLakeDomainPlan;

/** Request-local V15 inland-lake carving for the large-domain Continuum path. */
public final class V15ContinuumLakeBasePageSource implements ContinuumScalarPageSource {
    private final ContinuumWorldDomain domain;
    private final ContinuumScalarPageSource continental;
    private final V15ContinuumLakeDomainPlan lakes;

    public V15ContinuumLakeBasePageSource(
            ContinuumWorldDomain domain,
            ContinuumScalarPageSource continental,
            V15ContinuumLakeDomainPlan lakes) {
        if (domain == null || continental == null || lakes == null
                || !domain.equals(continental.domain())
                || !domain.equals(lakes.domain())) {
            throw new IllegalArgumentException("Continuum lake-base dependencies must share one domain");
        }
        this.domain = domain;
        this.continental = continental;
        this.lakes = lakes;
    }

    @Override
    public ContinuumWorldDomain domain() {
        return domain;
    }

    @Override
    public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
        ContinuumScalarPage base = continental.materialize(window);
        double[] output = new double[Math.multiplyExact(window.width(), window.height())];
        int cursor = 0;
        for (int sampleY = 0; sampleY < window.height(); sampleY++) {
            long y = window.yAt(sampleY);
            for (int sampleX = 0; sampleX < window.width(); sampleX++, cursor++) {
                long x = window.xAt(sampleX);
                double value = base.sample(sampleX, sampleY);
                output[cursor] = value > 0d && lakes.isLake(x, y, Math.round(value)) ? 0d : value;
            }
        }
        return new ContinuumScalarPage(window, output);
    }
}
