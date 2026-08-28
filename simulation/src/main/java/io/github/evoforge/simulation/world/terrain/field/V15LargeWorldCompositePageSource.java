package io.github.evoforge.simulation.world.terrain.field;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;

/**
 * Large-world V15 composition seam.
 *
 * <p>The scaled lake-base page is used only as the macro membership authority. Dry land comes from
 * native-scale V12/V13 detail evaluated in real world coordinates; standing water comes from the
 * bounded V15 lake/bathymetry planning field. This prevents already-finished mountains and local
 * relief from being geometrically stretched with world size.</p>
 */
public final class V15LargeWorldCompositePageSource implements ContinuumScalarPageSource {
    private final ContinuumWorldDomain domain;
    private final ContinuumScalarPageSource membership;
    private final ContinuumScalarPageSource land;
    private final ContinuumScalarPageSource water;

    public V15LargeWorldCompositePageSource(
            ContinuumWorldDomain domain,
            ContinuumScalarPageSource membership,
            ContinuumScalarPageSource land,
            ContinuumScalarPageSource water) {
        if (domain == null || membership == null || land == null || water == null) {
            throw new IllegalArgumentException("large-world V15 sources must not be null");
        }
        if (!domain.equals(membership.domain())
                || !domain.equals(land.domain())
                || !domain.equals(water.domain())) {
            throw new IllegalArgumentException("large-world V15 sources must share one domain");
        }
        this.domain = domain;
        this.membership = membership;
        this.land = land;
        this.water = water;
    }

    @Override
    public ContinuumWorldDomain domain() {
        return domain;
    }

    @Override
    public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
        ContinuumScalarPage membershipPage = membership.materialize(window);
        boolean hasLand = false;
        boolean hasWater = false;
        for (int y = 0; y < window.height() && !(hasLand && hasWater); y++) {
            for (int x = 0; x < window.width(); x++) {
                if (membershipPage.sample(x, y) > 0d) hasLand = true;
                else hasWater = true;
                if (hasLand && hasWater) break;
            }
        }
        if (hasLand && !hasWater) return land.materialize(window);
        if (!hasLand) return water.materialize(window);

        ContinuumScalarPage landPage = land.materialize(window);
        ContinuumScalarPage waterPage = water.materialize(window);
        double[] output = new double[Math.multiplyExact(window.width(), window.height())];
        int cursor = 0;
        for (int y = 0; y < window.height(); y++) {
            for (int x = 0; x < window.width(); x++) {
                output[cursor++] = membershipPage.sample(x, y) > 0d
                        ? landPage.sample(x, y)
                        : waterPage.sample(x, y);
            }
        }
        return new ContinuumScalarPage(window, output);
    }
}
