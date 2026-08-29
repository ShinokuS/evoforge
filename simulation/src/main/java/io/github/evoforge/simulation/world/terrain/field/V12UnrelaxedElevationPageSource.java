package io.github.evoforge.simulation.world.terrain.field;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;

/** Bounded page adapter for the authored V12 elevation before directional slope relaxation. */
public final class V12UnrelaxedElevationPageSource implements ContinuumScalarPageSource {
    private final ContinuumWorldDomain domain;
    private final V12UnrelaxedLandElevationField source;

    public V12UnrelaxedElevationPageSource(
            ContinuumWorldDomain domain,
            V12UnrelaxedLandElevationField source) {
        if (domain == null || source == null) {
            throw new IllegalArgumentException("unrelaxed V12 page-source inputs must not be null");
        }
        this.domain = domain;
        this.source = source;
    }

    @Override
    public ContinuumWorldDomain domain() {
        return domain;
    }

    @Override
    public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
        validateWindow(window);
        int area = Math.multiplyExact(window.width(), window.height());
        long[] sampled = new long[area];
        if (window.step() == 1L) {
            source.fillWindow(window.minX(), window.minY(), window.width(), window.height(), sampled);
        } else {
            source.fillSampleWindow(window, sampled);
        }
        double[] output = new double[area];
        for (int cell = 0; cell < area; cell++) output[cell] = sampled[cell];
        return new ContinuumScalarPage(window, output);
    }

    private void validateWindow(ContinuumSampleWindow window) {
        if (window == null) throw new IllegalArgumentException("window must not be null");
        long maxX = window.xAt(window.width() - 1);
        long maxY = window.yAt(window.height() - 1);
        if (!domain.contains(window.minX(), window.minY()) || !domain.contains(maxX, maxY)) {
            throw new IllegalArgumentException("window lies outside the unrelaxed V12 domain");
        }
    }
}
