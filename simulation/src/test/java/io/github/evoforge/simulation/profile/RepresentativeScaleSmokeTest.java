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
}
