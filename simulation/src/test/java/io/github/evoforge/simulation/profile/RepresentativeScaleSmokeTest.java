package io.github.evoforge.simulation.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class RepresentativeScaleSmokeTest {

    @Test
    void representativeLivingWorldProducesSameAuthoritativeSnapshotTwice() {
        var first = LivingWorldScaleWorkload.run("smoke", 4, 80);
        var second = LivingWorldScaleWorkload.run("smoke", 4, 80);

        assertEquals(80, first.snapshot().tick());
        assertEquals(first.snapshot(), second.snapshot());
    }

    @Test
    void representativeHydrologyProducesSameAuthoritativeSnapshotTwice() {
        var first = HydrologyScaleWorkload.run("smoke", 6, 60);
        var second = HydrologyScaleWorkload.run("smoke", 6, 60);

        assertEquals(60, first.snapshot().tick());
        assertEquals(first.snapshot(), second.snapshot());
    }

    @Test
    void representativeNavigationProducesSamePathResultsTwice() {
        var first = NavigationScaleWorkload.run("smoke", 12, 4);
        var second = NavigationScaleWorkload.run("smoke", 12, 4);

        assertEquals(4, first.snapshot().foundQueries());
        assertEquals(first.snapshot(), second.snapshot());
    }
}
