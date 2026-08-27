package io.github.evoforge.simulation.world.terrain.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import org.junit.jupiter.api.Test;

final class V12TerrainCalibrationTest {

    @Test
    void balancedDefinitionReproducesLegacyV12OperatingScales() {
        V12TerrainCalibration calibration = V12TerrainCalibration.compile(
                new ContinuumWorldDomain(512, 384),
                V15TerrainDefinition.balanced(),
                V12TerrainRecipe.balanced());

        assertEquals(512, calibration.width());
        assertEquals(384, calibration.height());
        assertEquals(196_608L, calibration.area());
        assertEquals(98_304L, calibration.landCount());
        assertEquals(258, calibration.coherentLandmassScale());
        assertEquals(64, calibration.fragmentedLandmassScale());
        assertEquals(500_000, calibration.fragmentationPpm());
        assertEquals(42, calibration.landformSpacing());
        assertEquals(84, calibration.upliftScale());
        assertEquals(63, calibration.ridgeScale());
        assertEquals(21, calibration.rollingScale());
        assertEquals(14, calibration.rollingDetailScale());
        assertEquals(600_000, calibration.reliefPpm());
        assertEquals(450_000, calibration.localReliefPpm());
        assertEquals(350_000, calibration.ruggednessPpm());
    }
}
