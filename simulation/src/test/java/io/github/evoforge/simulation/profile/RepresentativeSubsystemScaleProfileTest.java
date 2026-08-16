package io.github.evoforge.simulation.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

final class RepresentativeSubsystemScaleProfileTest {

    @Test
    @Tag("scale-profile")
    void profileRepresentativeHydrology() {
        var profile = HydrologyScaleWorkload.profile(
                System.getProperty(LivingWorldScaleWorkload.PROFILE_PROPERTY, "medium"));
        var result = HydrologyScaleWorkload.run(profile);

        System.out.println(result.report());

        assertEquals(profile.ticks(), result.snapshot().tick());
        assertFalse(result.snapshot().fingerprint().isBlank());
        assertTrue(result.snapshot().wetColumns() > 0);
    }

    @Test
    @Tag("scale-profile")
    void profileRepresentativeNavigation() {
        var profile = NavigationScaleWorkload.profile(
                System.getProperty(LivingWorldScaleWorkload.PROFILE_PROPERTY, "medium"));
        var result = NavigationScaleWorkload.run(profile);

        System.out.println(result.report());

        assertEquals(profile.queries(), result.snapshot().foundQueries());
        assertFalse(result.snapshot().fingerprint().isBlank());
    }
}
