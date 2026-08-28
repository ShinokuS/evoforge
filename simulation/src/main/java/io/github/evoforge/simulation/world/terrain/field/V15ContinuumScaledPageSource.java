package io.github.evoforge.simulation.world.terrain.field;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;

/**
 * Exposes a bounded V15 planning field over a larger Continuum domain.
 *
 * <p>The planning field contains the accepted V15 morphology (continents, mountains, bathymetry and
 * inland lakes) at a bounded control resolution. The declared simulation-world dimensions only
 * define the coordinate transform into that morphology; they do not trigger a dense world-sized
 * raster. Requested output samples are reconstructed by deterministic bilinear interpolation from
 * the bounded planning field.</p>
 *
 * <p>This class is deliberately representation-only. It does not author terrain features and does
 * not introduce another world generator.</p>
 */
public final class V15ContinuumScaledPageSource implements ContinuumScalarPageSource {
    private final ContinuumWorldDomain domain;
    private final ContinuumScalarPageSource planningSource;
    private final ContinuumWorldDomain planningDomain;

    public V15ContinuumScaledPageSource(
            ContinuumWorldDomain domain,
            ContinuumScalarPageSource planningSource) {
        if (domain == null || planningSource == null) {
            throw new IllegalArgumentException("V15 scaled source inputs must not be null");
        }
        this.domain = domain;
        this.planningSource = planningSource;
        this.planningDomain = planningSource.domain();
        if (planningDomain.width() < 2L || planningDomain.height() < 2L) {
            throw new IllegalArgumentException("V15 planning domain must be at least 2 x 2");
        }
    }

    @Override
    public ContinuumWorldDomain domain() {
        return domain;
    }

    public ContinuumWorldDomain planningDomain() {
        return planningDomain;
    }

    @Override
    public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
        validateWindow(window);

        double firstPlanningX = planningX(window.minX());
        double firstPlanningY = planningY(window.minY());
        double lastPlanningX = planningX(window.xAt(window.width() - 1));
        double lastPlanningY = planningY(window.yAt(window.height() - 1));
        int minimumPlanningX = clampPlanningX((int) StrictMath.floor(Math.min(firstPlanningX, lastPlanningX)));
        int maximumPlanningX = clampPlanningX((int) StrictMath.ceil(Math.max(firstPlanningX, lastPlanningX)));
        int minimumPlanningY = clampPlanningY((int) StrictMath.floor(Math.min(firstPlanningY, lastPlanningY)));
        int maximumPlanningY = clampPlanningY((int) StrictMath.ceil(Math.max(firstPlanningY, lastPlanningY)));

        ContinuumScalarPage planning = planningSource.materialize(new ContinuumSampleWindow(
                minimumPlanningX,
                minimumPlanningY,
                maximumPlanningX - minimumPlanningX + 1,
                maximumPlanningY - minimumPlanningY + 1,
                1L));

        double[] output = new double[Math.multiplyExact(window.width(), window.height())];
        int cursor = 0;
        for (int sampleY = 0; sampleY < window.height(); sampleY++) {
            double py = planningY(window.yAt(sampleY));
            int y0 = clampPlanningY((int) StrictMath.floor(py));
            int y1 = Math.min(y0 + 1, Math.toIntExact(planningDomain.height() - 1L));
            double ty = py - y0;
            for (int sampleX = 0; sampleX < window.width(); sampleX++) {
                double px = planningX(window.xAt(sampleX));
                int x0 = clampPlanningX((int) StrictMath.floor(px));
                int x1 = Math.min(x0 + 1, Math.toIntExact(planningDomain.width() - 1L));
                double tx = px - x0;

                double a = planning.sample(x0 - minimumPlanningX, y0 - minimumPlanningY);
                double b = planning.sample(x1 - minimumPlanningX, y0 - minimumPlanningY);
                double c = planning.sample(x0 - minimumPlanningX, y1 - minimumPlanningY);
                double d = planning.sample(x1 - minimumPlanningX, y1 - minimumPlanningY);
                double top = a + (b - a) * tx;
                double bottom = c + (d - c) * tx;
                output[cursor++] = top + (bottom - top) * ty;
            }
        }
        return new ContinuumScalarPage(window, output);
    }

    private double planningX(long worldX) {
        if (domain.width() <= 1L) return 0d;
        return worldX * (planningDomain.width() - 1d) / (domain.width() - 1d);
    }

    private double planningY(long worldY) {
        if (domain.height() <= 1L) return 0d;
        return worldY * (planningDomain.height() - 1d) / (domain.height() - 1d);
    }

    private int clampPlanningX(int value) {
        return Math.max(0, Math.min(Math.toIntExact(planningDomain.width() - 1L), value));
    }

    private int clampPlanningY(int value) {
        return Math.max(0, Math.min(Math.toIntExact(planningDomain.height() - 1L), value));
    }

    private void validateWindow(ContinuumSampleWindow window) {
        if (window == null) throw new IllegalArgumentException("window must not be null");
        long maximumX = window.xAt(window.width() - 1);
        long maximumY = window.yAt(window.height() - 1);
        if (!domain.contains(window.minX(), window.minY()) || !domain.contains(maximumX, maximumY)) {
            throw new IllegalArgumentException("window lies outside V15 Continuum domain");
        }
    }
}
