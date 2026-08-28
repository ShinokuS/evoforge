package io.github.evoforge.simulation.world.terrain.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import org.junit.jupiter.api.Test;

final class V15NativeReliefPageSourceTest {

    @Test
    void largeWorldAddsCellScaleReliefInsteadOfStretchingOneMacroHeight() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(10_000, 10_000);
        ContinuumScalarPageSource flatLand = constant(domain, 4_000_000d);
        V15NativeReliefPageSource source = new V15NativeReliefPageSource(
                domain,
                71_337L,
                V15TerrainDefinition.balanced(),
                flatLand,
                12,
                96);

        ContinuumSampleWindow window = new ContinuumSampleWindow(4_900, 4_900, 96, 96, 1);
        ContinuumScalarPage first = source.materialize(window);
        ContinuumScalarPage second = source.materialize(window);

        long minimum = Long.MAX_VALUE;
        long maximum = Long.MIN_VALUE;
        int horizontalChanges = 0;
        for (int y = 0; y < window.height(); y++) {
            for (int x = 0; x < window.width(); x++) {
                long value = Math.round(first.sample(x, y));
                assertEquals(value, Math.round(second.sample(x, y)));
                minimum = Math.min(minimum, value);
                maximum = Math.max(maximum, value);
                if (x > 0 && value != Math.round(first.sample(x - 1, y))) horizontalChanges++;
            }
        }

        assertNotEquals(minimum, maximum);
        assertTrue(horizontalChanges > 1_000,
                "native V12 detail must vary throughout a large-world local window");
    }

    private static ContinuumScalarPageSource constant(ContinuumWorldDomain domain, double value) {
        return new ContinuumScalarPageSource() {
            @Override
            public ContinuumWorldDomain domain() {
                return domain;
            }

            @Override
            public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
                double[] samples = new double[Math.multiplyExact(window.width(), window.height())];
                java.util.Arrays.fill(samples, value);
                return new ContinuumScalarPage(window, samples);
            }
        };
    }
}
