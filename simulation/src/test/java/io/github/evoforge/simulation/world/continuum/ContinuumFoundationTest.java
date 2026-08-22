package io.github.evoforge.simulation.world.continuum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.world.continuum.field.ContinuumMaterializer;
import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarField;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumRandom;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import org.junit.jupiter.api.Test;

final class ContinuumFoundationTest {
    @Test
    void millionScaleDomainIsOnlyAnAddressSpace() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(1_000_000L, 1_000_000L);
        assertEquals(1_000_000L, domain.width());
        assertEquals(1_000_000L, domain.height());
    }

    @Test
    void materializationIsBoundedAndUsesGlobalCoordinates() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(1_000_000L, 1_000_000L);
        ContinuumScalarField field = (x, y) -> x * 0.25d + y * 0.5d;
        ContinuumMaterializer materializer = new ContinuumMaterializer(domain, field);
        ContinuumSampleWindow window = new ContinuumSampleWindow(900_000L, 700_000L, 4, 3, 11L);
        ContinuumScalarPage page = materializer.materialize(window);

        assertEquals(field.sample(900_022L, 700_011L), page.sample(2, 1));
        assertEquals(12, page.copySamples().length);
    }

    @Test
    void adjacentPagesAgreeAtSharedBoundary() {
        ContinuumScalarField field = (x, y) -> x * 31d - y * 17d;
        ContinuumMaterializer materializer = new ContinuumMaterializer(
                new ContinuumWorldDomain(100_000L, 100_000L), field);
        ContinuumScalarPage left = materializer.materialize(new ContinuumSampleWindow(100L, 200L, 5, 4, 3L));
        ContinuumScalarPage right = materializer.materialize(new ContinuumSampleWindow(112L, 200L, 5, 4, 3L));

        for (int y = 0; y < 4; y++) {
            assertEquals(left.sample(4, y), right.sample(0, y));
        }
    }

    @Test
    void continuumRandomIsOrderIndependentAndPurposeScoped() {
        ContinuumRandom random = new ContinuumRandom(42L);
        long first = random.sampleLong("continuum:proof", 120L, 240L, 0L);
        random.sampleLong("continuum:other", 999L, 1L, 5L);
        assertEquals(first, random.sampleLong("continuum:proof", 120L, 240L, 0L));
        assertNotEquals(first, random.sampleLong("continuum:other", 120L, 240L, 0L));
    }

    @Test
    void materializerRejectsRequestsOutsideLogicalWorld() {
        ContinuumMaterializer materializer = new ContinuumMaterializer(
                new ContinuumWorldDomain(100L, 100L), (x, y) -> 0d);
        assertThrows(IllegalArgumentException.class,
                () -> materializer.materialize(new ContinuumSampleWindow(99L, 99L, 2, 2, 1L)));
    }
}
