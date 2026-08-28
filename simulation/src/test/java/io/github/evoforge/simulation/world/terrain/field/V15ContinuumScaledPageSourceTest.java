package io.github.evoforge.simulation.world.terrain.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import org.junit.jupiter.api.Test;

final class V15ContinuumScaledPageSourceTest {

    @Test
    void largeDomainRequestOnlyTouchesBoundedPlanningWindow() {
        ContinuumWorldDomain planningDomain = new ContinuumWorldDomain(300, 300);
        TrackingPlanningSource planning = new TrackingPlanningSource(planningDomain);
        V15ContinuumScaledPageSource source = new V15ContinuumScaledPageSource(
                new ContinuumWorldDomain(5_000, 5_000),
                planning);

        ContinuumSampleWindow request = new ContinuumSampleWindow(2_400, 2_500, 96, 80, 1);
        ContinuumScalarPage page = source.materialize(request);

        assertEquals(request, page.window());
        assertTrue(planning.materializations > 0);
        assertTrue(planning.maximumRequestedSamples < 1_000,
                "a local large-world request must not pull the whole 300x300 V15 plan");
    }

    @Test
    void overlappingRequestsAreOrderIndependentAndSeamFree() {
        ContinuumWorldDomain planningDomain = new ContinuumWorldDomain(300, 300);
        V15ContinuumScaledPageSource source = new V15ContinuumScaledPageSource(
                new ContinuumWorldDomain(5_000, 5_000),
                new TrackingPlanningSource(planningDomain));

        ContinuumSampleWindow firstWindow = new ContinuumSampleWindow(1_000, 1_200, 64, 64, 1);
        ContinuumSampleWindow secondWindow = new ContinuumSampleWindow(1_032, 1_200, 64, 64, 1);
        ContinuumScalarPage second = source.materialize(secondWindow);
        ContinuumScalarPage first = source.materialize(firstWindow);

        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 32; x++) {
                assertEquals(first.sample(x + 32, y), second.sample(x, y), 0.0);
            }
        }

        ContinuumScalarPage firstAgain = source.materialize(firstWindow);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                assertEquals(first.sample(x, y), firstAgain.sample(x, y), 0.0);
            }
        }
    }

    private static final class TrackingPlanningSource implements ContinuumScalarPageSource {
        private final ContinuumWorldDomain domain;
        private int materializations;
        private int maximumRequestedSamples;

        private TrackingPlanningSource(ContinuumWorldDomain domain) {
            this.domain = domain;
        }

        @Override
        public ContinuumWorldDomain domain() {
            return domain;
        }

        @Override
        public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
            materializations++;
            maximumRequestedSamples = Math.max(
                    maximumRequestedSamples,
                    Math.multiplyExact(window.width(), window.height()));
            double[] samples = new double[Math.multiplyExact(window.width(), window.height())];
            int cursor = 0;
            for (int y = 0; y < window.height(); y++) {
                long worldY = window.yAt(y);
                for (int x = 0; x < window.width(); x++) {
                    long worldX = window.xAt(x);
                    samples[cursor++] = worldX * 10_000d + worldY;
                }
            }
            return new ContinuumScalarPage(window, samples);
        }
    }
}
