package io.github.evoforge.simulation.world.terrain.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import org.junit.jupiter.api.Test;

final class BoundedExactTerrainSnapshotPageSourceTest {
    @Test
    void smallDomainIsCapturedOnceAndLaterWindowsReadOnlyTheImmutableSnapshot() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(8L, 6L);
        CountingPageSource source = new CountingPageSource(domain);

        ContinuumScalarPageSource snapshot =
                BoundedExactTerrainSnapshotPageSource.captureIfBounded(source);

        assertEquals(1, source.materializations);
        ContinuumScalarPage first = snapshot.materialize(new ContinuumSampleWindow(1L, 1L, 3, 2, 2L));
        ContinuumScalarPage second = snapshot.materialize(new ContinuumSampleWindow(3L, 1L, 3, 3, 1L));
        assertEquals(1, source.materializations, "bounded requests must not re-run the captured source");

        assertEquals(valueAt(1L, 1L), first.sample(0, 0));
        assertEquals(valueAt(5L, 3L), first.sample(2, 1));
        assertEquals(valueAt(3L, 1L), second.sample(0, 0));
        assertEquals(valueAt(5L, 3L), second.sample(2, 2));
    }

    @Test
    void domainAboveFixedPayloadBudgetKeepsOriginalBoundedSource() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(513L, 512L);
        CountingPageSource source = new CountingPageSource(domain);

        assertSame(source, BoundedExactTerrainSnapshotPageSource.captureIfBounded(source));
        assertEquals(0, source.materializations);
    }

    private static double valueAt(long x, long y) {
        return y * 10_000d + x;
    }

    private static final class CountingPageSource implements ContinuumScalarPageSource {
        private final ContinuumWorldDomain domain;
        private int materializations;

        private CountingPageSource(ContinuumWorldDomain domain) {
            this.domain = domain;
        }

        @Override
        public ContinuumWorldDomain domain() {
            return domain;
        }

        @Override
        public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
            materializations++;
            double[] samples = new double[Math.multiplyExact(window.width(), window.height())];
            int cursor = 0;
            for (int y = 0; y < window.height(); y++) {
                for (int x = 0; x < window.width(); x++) {
                    samples[cursor++] = valueAt(window.xAt(x), window.yAt(y));
                }
            }
            return new ContinuumScalarPage(window, samples);
        }
    }
}
