package io.github.evoforge.simulation.world.calibration.soil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class SoilCompositionProfileTest {

    @Test
    void storesMineralTextureAndOrganicMatterAsIndependentPhysicalFractions() {
        SoilCompositionProfile composition = SoilCompositionProfile.ofPercent(85, 10, 5, 2);

        assertEquals(850_000, composition.sandPartsPerMillion());
        assertEquals(100_000, composition.siltPartsPerMillion());
        assertEquals(50_000, composition.clayPartsPerMillion());
        assertEquals(20_000, composition.organicMatterPartsPerMillion());
    }

    @Test
    void requiresTheMineralTextureTriangleToCloseExactly() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SoilCompositionProfile(500_000, 300_000, 199_999, 20_000));
        assertThrows(
                IllegalArgumentException.class,
                () -> SoilCompositionProfile.ofPercent(70, 20, 20, 2));
    }

    @Test
    void validatesEveryPhysicalFraction() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SoilCompositionProfile(-1, 500_001, 500_000, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SoilCompositionProfile(500_000, 500_000, 0, 1_000_001));
    }
}
