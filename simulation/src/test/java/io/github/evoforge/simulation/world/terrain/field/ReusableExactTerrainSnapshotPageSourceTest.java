package io.github.evoforge.simulation.world.terrain.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import org.junit.jupiter.api.Test;

final class ReusableExactTerrainSnapshotPageSourceTest {

    @Test
    void moderateExactStageMaterializesWholeSourceOnceAndReusesItAcrossWindows() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(600, 600);
        TrackingSource source = new TrackingSource(domain);
        ContinuumScalarPageSource reusable =
                ReusableExactTerrainSnapshotPageSource.captureIfPractical("test-stage", source);

        assertNotSame(source, reusable);

        ContinuumSampleWindow firstWindow = new ContinuumSampleWindow(211, 307, 32, 24, 1);
        ContinuumSampleWindow sparseWindow = new ContinuumSampleWindow(17, 29, 12, 9, 7);
        assertMatches(firstWindow, reusable.materialize(firstWindow));
        assertMatches(sparseWindow, reusable.materialize(sparseWindow));
        assertMatches(firstWindow, reusable.materialize(firstWindow));

        assertEquals(1, source.materializations);
        assertEquals(new ContinuumSampleWindow(0, 0, 600, 600, 1), source.lastWindow);
    }

    @Test
    void oversizedDomainKeepsOriginalSourceWithoutEagerMaterialization() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(5_000, 5_000);
        TrackingSource source = new TrackingSource(domain);

        ContinuumScalarPageSource result =
                ReusableExactTerrainSnapshotPageSource.captureIfPractical("too-large", source);

        assertSame(source, result);
        assertEquals(0, source.materializations);
    }

    private static void assertMatches(ContinuumSampleWindow window, ContinuumScalarPage page) {
        assertEquals(window, page.window());
        for (int y = 0; y < window.height(); y++) {
            for (int x = 0; x < window.width(); x++) {
                assertEquals(expected(window.xAt(x), window.yAt(y)), page.sample(x, y), 0.0);
            }
        }
    }

    private static double expected(long x, long y) {
        return x * 1_000_000d + y;
    }

    private static final class TrackingSource implements ContinuumScalarPageSource {
        private final ContinuumWorldDomain domain;
        private int materializations;
        private ContinuumSampleWindow lastWindow;

        private TrackingSource(ContinuumWorldDomain domain) {
            this.domain = domain;
        }

        @Override
        public ContinuumWorldDomain domain() {
            return domain;
        }

        @Override
        public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
            materializations++;
            lastWindow = window;
            double[] samples = new double[Math.multiplyExact(window.width(), window.height())];
            int cursor = 0;
            for (int y = 0; y < window.height(); y++) {
                long worldY = window.yAt(y);
                for (int x = 0; x < window.width(); x++) {
                    samples[cursor++] = expected(window.xAt(x), worldY);
                }
            }
            return new ContinuumScalarPage(window, samples);
        }
    }
}
