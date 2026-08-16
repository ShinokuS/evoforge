package io.github.evoforge.simulation.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

final class RepresentativeScaleProfileTest {

    @Test
    @Tag("scale-profile")
    void profileRepresentativeLivingWorld() {
        var profile = LivingWorldScaleWorkload.profile(
                System.getProperty(LivingWorldScaleWorkload.PROFILE_PROPERTY, "medium"));
        var result = LivingWorldScaleWorkload.run(profile);

        System.out.println(result.report());

        assertEquals(profile.ticks(), result.snapshot().tick());
        assertFalse(result.snapshot().fingerprint().isBlank());
    }
}
