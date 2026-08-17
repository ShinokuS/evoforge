package io.github.evoforge.simulation.world.preparation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicProfile;
import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicProfileField;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class GeneratedLandscapePropertiesTest {
    @Test
    void spatialFieldCanCarryLocalPhysicalDifferencesIncludingAuthoritativeNull() {
        WorldBounds bounds = new WorldBounds(-1, 1, 0, 0, 0, 0);
        SoilHydraulicProfile coarse = SoilHydraulicProfile.ofPercent(
                45, 30, 12, WaterDepthRate.ZERO);
        SoilHydraulicProfile fine = SoilHydraulicProfile.ofPercent(
                50, 36, 18, WaterDepthRate.ZERO);
        SoilHydraulicProfileField field = new SoilHydraulicProfileField() {
            public WorldBounds bounds() { return bounds; }
            public SoilHydraulicProfile find(int x, int y, int z) {
                return x < 0 ? coarse : x > 0 ? fine : null;
            }
        };

        GeneratedLandscapeProperties properties = new GeneratedLandscapeProperties(field);
        assertTrue(properties.soilHydraulics().isPresent());
        SoilHydraulicProfileField generated = properties.soilHydraulics().orElseThrow();
        assertSame(coarse, generated.find(-1, 0, 0));
        assertNull(generated.find(0, 0, 0));
        assertSame(fine, generated.find(1, 0, 0));
    }

    @Test
    void emptyPropertiesMeanNoGeneratedSoilAuthority() {
        WorldBounds bounds = new WorldBounds(0, 0, 0, 0, 0, 0);
        GeneratedLandscapeProperties properties = GeneratedLandscapeProperties.empty(bounds);

        assertSame(bounds, properties.bounds());
        assertFalse(properties.soilHydraulics().isPresent());
    }
}
