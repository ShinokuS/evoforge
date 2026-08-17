package io.github.evoforge.simulation.world.calibration.soil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.NormalizedValue;
import org.junit.jupiter.api.Test;

final class ContinuousSoilCompositionCompilerTest {

    private final ContinuousSoilCompositionCompiler compiler =
            new ContinuousSoilCompositionCompiler(SoilCompositionCalibration.representative());

    @Test
    void derivesPhysicalCompositionContinuouslyFromNormalizedCharacter() {
        SoilCompositionProfile composition = compiler.compile(profile(0.4, 0.4));

        assertEquals(360_000, composition.sandPartsPerMillion());
        assertEquals(480_000, composition.siltPartsPerMillion());
        assertEquals(160_000, composition.clayPartsPerMillion());
        assertEquals(20_000, composition.organicMatterPartsPerMillion());
    }

    @Test
    void nearbyAuthoredCoordinatesProduceNearbyPhysicalCompositionWithoutClassThresholds() {
        SoilCompositionProfile left = compiler.compile(profile(0.499, 0.4));
        SoilCompositionProfile right = compiler.compile(profile(0.501, 0.4));

        assertTrue(Math.abs(left.sandPartsPerMillion() - right.sandPartsPerMillion()) < 3_000);
        assertTrue(Math.abs(left.clayPartsPerMillion() - right.clayPartsPerMillion()) < 3_000);
        assertTrue(Math.abs(left.siltPartsPerMillion() - right.siltPartsPerMillion()) < 10);
    }

    @Test
    void rejectsMissingSemanticProfile() {
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(null));
    }

    private static SoilSemanticProfile profile(double fineness, double organicMatter) {
        return new SoilSemanticProfile(
                NormalizedValue.parse(Double.toString(fineness)),
                NormalizedValue.parse(Double.toString(organicMatter)));
    }
}
